package com.forma.app.di

import android.content.Context
import com.forma.app.data.backup.BackupManager
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.prefs.SettingsStore
import com.forma.app.data.repo.ActivityRepository
import com.forma.app.data.repo.BodyRepository
import com.forma.app.data.repo.CarryoverRepository
import com.forma.app.data.repo.EnergyModel
import com.forma.app.data.repo.EnergySnapshot
import com.forma.app.data.repo.FoodRepository
import com.forma.app.data.repo.ProfileRepository
import com.forma.app.data.repo.RecipeRepository
import com.forma.app.data.repo.WorkoutRepository
import com.forma.app.health.HealthConnectSource
import com.forma.app.health.HealthDataSource
import com.forma.app.health.HealthSyncer
import com.forma.app.util.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Manual dependency container — small, explicit and easy to swap in tests. */
class AppContainer(
    val context: Context,
    val db: FormaDatabase = FormaDatabase.create(context),
    val time: TimeProvider = TimeProvider(),
    val healthSource: HealthDataSource = HealthConnectSource(context),
    /** Ticker interval for "today" recalculation (elapsed fraction of the day). */
    private val tickMillis: Long = 60_000,
) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsStore(context)
    val profile = ProfileRepository(db, time)
    val recipes = RecipeRepository(db)
    val food = FoodRepository(db, recipes, time)
    val body = BodyRepository(db, profile, time)
    val carryover = CarryoverRepository(db, time)
    val activity = ActivityRepository(db)
    val workouts = WorkoutRepository(db, time)
    val healthSyncer = HealthSyncer(db, healthSource, time)
    val backup = BackupManager(context, db)
    val recipeImporter = com.forma.app.data.importer.RecipeImporter(context, db, time)
    val library = com.forma.app.data.workout.WorkoutLibraryRepository(db, time)
    val mealPlan = com.forma.app.data.plan.MealPlanRepository(db, recipes, food, time)
    val workoutImporter = com.forma.app.data.workout.WorkoutImporter(db, time)

    private val timerAlerts = com.forma.app.util.TimerAlerts(context)
    /** Hold and rest timers for the workout in progress. */
    val setTimers = com.forma.app.data.workout.SetTimerController(time, workouts, appScope) { timerAlerts.play(it) }

    /** "Do this workout today" choice (date, workout id). Doesn't change the schedule. */
    val workoutOverride = kotlinx.coroutines.flow.MutableStateFlow<Pair<java.time.LocalDate, String>?>(null)

    /** Workout library + active schedule, shared by Home, Workout and Progress. */
    val catalog: StateFlow<com.forma.app.data.workout.WorkoutCatalog?> = library.catalog.stateIn(appScope, SharingStarted.Eagerly, null)

    private val ticker: Flow<Pair<java.time.LocalDate, Double>> = flow {
        while (true) {
            emit(time.today() to time.elapsedFractionToday())
            delay(tickMillis)
        }
    }

    /** Live energy model shared by every screen. Re-computes whenever any input table or the clock changes. */
    val energyModel: StateFlow<EnergyModel?> = combine(
        listOf(
            profile.profile, body.weights, db.energy().observeAll(), activity.sessions, workouts.sessions,
            food.dailyTotals, carryover.entries, ticker, db.library().observeWorkouts(),
        ),
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        val tick = arr[7] as Pair<java.time.LocalDate, Double>
        @Suppress("UNCHECKED_CAST")
        EnergyModel(
            EnergySnapshot(
                profile = arr[0] as com.forma.app.data.db.ProfileEntity,
                weights = arr[1] as List<com.forma.app.data.db.WeightEntity>,
                energy = (arr[2] as List<com.forma.app.data.db.DailyEnergyEntity>).associateBy { it.epochDay },
                sessions = arr[3] as List<com.forma.app.data.db.ExerciseSessionEntity>,
                workouts = arr[4] as List<com.forma.app.data.db.WorkoutSessionEntity>,
                food = (arr[5] as List<com.forma.app.data.db.DailyFoodTotals>).associateBy { it.epochDay },
                carryover = arr[6] as List<com.forma.app.data.db.CarryoverEntity>,
                today = tick.first,
                elapsedFractionToday = tick.second,
                workoutDefs = (arr[8] as List<com.forma.app.data.db.WorkoutEntity>).associateBy { it.id },
            ),
        )
    }.stateIn(appScope, SharingStarted.Eagerly, null)

    val syncMeta = db.syncMeta().observe().map { it }

    /** Sunday weekly reports (saved; never deleted automatically). */
    val reports = com.forma.app.data.report.WeeklyReportRepository(db, time,
        model = { energyModel.filterNotNull().first() }, catalog = { catalog.filterNotNull().first() })

    /** A screen to open, set when the app is launched from a notification. */
    val pendingRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    suspend fun initialize() {
        profile.ensure()
        // Automatic carryover counts from the first day it's available, never retroactively.
        if (profile.ensure().autoCarryoverFromEpochDay == null) profile.update { it.copy(autoCarryoverFromEpochDay = time.today().toEpochDay()) }
        recipes.seed()
        library.seed()
        runCatching { reports.refresh() }
    }
}
