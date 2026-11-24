package com.forma.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forma.app.data.db.ExerciseLogEntity
import com.forma.app.data.db.FoodLogEntity
import com.forma.app.data.db.ProfileEntity
import com.forma.app.data.db.SyncMetaEntity
import com.forma.app.data.db.WorkoutSessionEntity
import com.forma.app.data.prefs.AppSettings
import com.forma.app.data.repo.EnergyModel
import com.forma.app.di.AppContainer
import com.forma.engine.TrendAnalyzer
import com.forma.engine.WeightPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodayWorkout(
    val day: com.forma.app.data.workout.PlannedDay,
    val session: WorkoutSessionEntity?,
    val completedSlots: Int,
    val totalSlots: Int,
) {
    val done get() = session?.completedAt != null
}

data class HomeState(
    val model: EnergyModel,
    val profile: ProfileEntity,
    val meals: List<FoodLogEntity>,
    val workout: TodayWorkout,
    val otherSessionToday: WorkoutSessionEntity?,
    val weightTrend: List<Double>,
    val trendWeight: Double?,
    val weekChange: Double?,
    val sync: SyncMetaEntity?,
    val settings: AppSettings,
    val hasWeighIns: Boolean,
) {
    val today: LocalDate get() = model.s.today
    val summary get() = model.today
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val todayFlow = c.energyModel.filterNotNull().map { it.s.today }.distinctUntilChanged()
    private val meals = todayFlow.flatMapLatest { c.food.day(it.toEpochDay()) }

    private data class W(val sessions: List<WorkoutSessionEntity>, val logs: List<ExerciseLogEntity>)

    val state: StateFlow<HomeState?> = combine(
        c.energyModel.filterNotNull(), meals,
        combine(c.workouts.sessions, c.workouts.exerciseLogs) { s, l -> W(s, l) },
        c.syncMeta, combine(c.settings.settings, c.catalog.filterNotNull()) { a, b -> a to b },
    ) { model, meals, w, sync, sc ->
        val (settings, catalog) = sc
        val today = model.s.today
        val plan = catalog.planFor(today)
        val todays = w.sessions.filter { it.epochDay == today.toEpochDay() }
        val planned = todays.firstOrNull { it.templateKey == plan.workout?.id } ?: todays.firstOrNull { plan.workout == null }
        val other = todays.firstOrNull { it != planned }
        val logs = planned?.let { s -> w.logs.filter { it.sessionId == s.id } }.orEmpty()
        val pts = model.s.weights.map { WeightPoint(LocalDate.ofEpochDay(it.epochDay), it.weightLb) }
        val trend = TrendAnalyzer.rolling7(pts)
        val recent = trend.filter { !it.date.isBefore(today.minusDays(29)) }
        val weekAgo = trend.lastOrNull { !it.date.isAfter(today.minusDays(7)) }
        HomeState(
            model = model, profile = model.s.profile, meals = meals,
            workout = TodayWorkout(plan, planned, logs.count { it.completed }, plan.workout?.slots?.size ?: logs.size),
            otherSessionToday = other,
            weightTrend = recent.map { it.average7 },
            trendWeight = trend.lastOrNull()?.average7,
            weekChange = if (weekAgo != null && trend.isNotEmpty()) trend.last().average7 - weekAgo.average7 else null,
            sync = sync, settings = settings, hasWeighIns = pts.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun logWeight(lb: Double, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching { c.body.logWeight(lb) }.onFailure { onError(it.message ?: "Couldn't save") }
    }

    fun deleteMeal(id: Long) = viewModelScope.launch { c.food.delete(id) }

    fun sync() = viewModelScope.launch { runCatching { c.healthSyncer.sync() } }
}
