package com.forma.app.data.report

import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.db.WeeklyReportEntity
import com.forma.app.data.repo.EnergyModel
import com.forma.app.data.seed.ExerciseKind
import com.forma.app.data.workout.WorkoutCatalog
import com.forma.app.util.TimeProvider
import com.forma.engine.LoggedSet
import com.forma.engine.RecordMetric
import com.forma.engine.SleepNight
import com.forma.engine.StrengthRecords
import com.forma.engine.TrendAnalyzer
import com.forma.engine.WeekDayData
import com.forma.engine.WeeklyStats
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

@Serializable
data class PrEntry(val exercise: String, val metric: String, val now: String, val before: String)

/** Everything shown in one week's report. Saved as JSON so past weeks read exactly as they did that Sunday. */
@Serializable
data class WeeklyReport(
    val weekStart: Long,
    val generatedAt: Long,
    // Food & deficit
    val daysLogged: Int = 0,
    val kcalTotal: Int = 0,
    val kcalAvg: Int? = null,
    val budgetAvg: Int? = null,
    val deficitTotal: Int = 0,
    val deficitAvg: Int? = null,
    // Weight (weekly average of weigh-ins)
    val weightAvg: Double? = null,
    val weightChange: Double? = null,
    // Steps
    val stepsTotal: Long = 0,
    val stepsAvg: Long? = null,
    val stepDays: Int = 0,
    val stepsBestDay: Long? = null,
    val stepsBest: Long? = null,
    val stepGoal: Int = 10000,
    val stepGoalDays: Int = 0,
    // Sleep
    val sleepNights: Int = 0,
    val sleepScore: Int? = null,
    val sleepAvgMin: Int? = null,
    val deepAvgMin: Int? = null,
    val remAvgMin: Int? = null,
    val lightAvgMin: Int? = null,
    val awakeAvgMin: Int? = null,
    val bestNightDay: Long? = null,
    val bestNightScore: Int? = null,
    val worstNightDay: Long? = null,
    val worstNightScore: Int? = null,
    val sleepFromStages: Boolean = false,
    // Training
    val workoutsDone: Int = 0,
    val workoutsPlanned: Int = 0,
    val trainingMinutes: Int = 0,
    // Personal records
    val prsUnlocked: Boolean = false,
    val prsUnlockDay: Long? = null,
    val prs: List<PrEntry> = emptyList(),
) {
    val start: LocalDate get() = LocalDate.ofEpochDay(weekStart)
    val end: LocalDate get() = start.plusDays(6)
    val hasData get() = daysLogged > 0 || stepDays > 0 || sleepNights > 0 || workoutsDone > 0
}

/**
 * Builds the Sunday report for each finished week (Sunday–Saturday). A week's report comes out on the following
 * Sunday at 6 am. Missing weeks are filled in from the logs, so the history in Progress is complete.
 */
class WeeklyReportRepository(
    private val db: FormaDatabase,
    private val time: TimeProvider,
    private val model: suspend () -> EnergyModel,
    private val catalog: suspend () -> WorkoutCatalog,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val all = db.reports().observeAll()
    fun observe(week: LocalDate) = db.reports().observe(week.toEpochDay())

    fun decode(e: WeeklyReportEntity): WeeklyReport = json.decodeFromString(WeeklyReport.serializer(), e.json)

    suspend fun dismiss(week: LocalDate) = db.reports().dismiss(week.toEpochDay())

    /** The most recent week whose report is out (it comes out Sunday 6 am). */
    fun latestReadyWeek(): LocalDate {
        val now = time.now()
        val thisWeek = TrendAnalyzer.weekStart(now.toLocalDate())
        val releasedThisSunday = !now.toLocalDateTime().isBefore(thisWeek.atTime(RELEASE))
        return thisWeek.minusWeeks(if (releasedThisSunday) 1 else 2)
    }

    /** When the report for [week] comes out. */
    fun releaseTime(week: LocalDate) = week.plusDays(7).atTime(RELEASE)

    /**
     * Creates any missing reports up to the latest finished week. The latest one is rebuilt during its first two
     * days so watch data that syncs late on Sunday still makes it in. Returns the latest report if it was created now.
     */
    suspend fun refresh(): WeeklyReport? {
        val ready = latestReadyWeek()
        val first = firstDataDay() ?: return null
        var week = maxOf(TrendAnalyzer.weekStart(first), ready.minusWeeks(MAX_BACKFILL_WEEKS))
        var created: WeeklyReport? = null
        val m = model(); val cat = catalog()
        val sets = loggedSets(cat)
        while (!week.isAfter(ready)) {
            val existing = db.reports().get(week.toEpochDay())
            val fresh = existing != null && week == ready &&
                time.now().toLocalDateTime().isBefore(releaseTime(week).plusDays(2))
            if (existing == null || fresh) {
                val r = build(week, m, cat, sets)
                if (r.hasData) {
                    db.reports().upsert(WeeklyReportEntity(week.toEpochDay(), json.encodeToString(WeeklyReport.serializer(), r), r.generatedAt,
                        dismissed = existing?.dismissed ?: (week != ready)))
                    if (existing == null && week == ready) created = r
                }
            }
            week = week.plusWeeks(1)
        }
        return created
    }

    private suspend fun firstDataDay(): LocalDate? {
        val days = listOfNotNull(
            db.food().all().minOfOrNull { it.epochDay },
            db.energy().all().filter { (it.steps ?: 0) > 0 || (it.sleepMinutes ?: 0.0) > 0 }.minOfOrNull { it.epochDay },
            db.workouts().allSessions().minOfOrNull { it.epochDay },
        )
        return days.minOrNull()?.let(LocalDate::ofEpochDay)
    }

    /** Every logged set, for personal records. Cardio and mobility times aren't records. */
    private suspend fun loggedSets(cat: WorkoutCatalog): List<LoggedSet> {
        val sessions = db.workouts().allSessions().associateBy { it.id }
        val logs = db.workouts().allExerciseLogs().associateBy { it.id }
        return db.workouts().allSetLogs().mapNotNull { s ->
            val l = logs[s.exerciseLogId] ?: return@mapNotNull null
            val day = sessions[l.sessionId]?.epochDay ?: return@mapNotNull null
            val kind = runCatching { cat.kind(l.exerciseKey) }.getOrNull()
            val seconds = s.durationMin?.takeIf { kind == ExerciseKind.CORE || kind == ExerciseKind.BALANCE }?.let { (it * 60).roundToInt() }
            if (kind == ExerciseKind.CARDIO || kind == ExerciseKind.MOBILITY) return@mapNotNull null
            LoggedSet(LocalDate.ofEpochDay(day), l.exerciseKey, s.weightLb, s.reps, seconds)
        }
    }

    suspend fun build(week: LocalDate, m: EnergyModel, cat: WorkoutCatalog, sets: List<LoggedSet>): WeeklyReport {
        val days = (0L..6L).map { week.plusDays(it) }
        val energy = db.energy().all().associateBy { it.epochDay }
        val p = m.s.profile
        val dayData = days.map { d ->
            val e = energy[d.toEpochDay()]
            val food = m.hasFood(d)
            WeekDayData(
                d,
                kcal = if (food) m.consumed(d).kcal else null,
                deficit = if (food) m.summary(d).projectedDeficit else null,
                steps = e?.steps,
                sleep = e?.sleepMinutes?.takeIf { it > 0 }?.let { SleepNight(it, e.sleepDeepMin, e.sleepLightMin, e.sleepRemMin, e.sleepAwakeMin) },
            )
        }
        val st = WeeklyStats.compute(week, dayData, p.stepGoal)
        val logged = days.filter { m.hasFood(it) }
        val budgetAvg = logged.takeIf { it.isNotEmpty() }?.map { m.summary(it).budget.finalBudget }?.average()?.roundToInt()

        val weights = db.body().allWeights()
        fun avgWeight(from: LocalDate) = weights.filter { it.epochDay in from.toEpochDay()..from.plusDays(6).toEpochDay() }
            .takeIf { it.isNotEmpty() }?.map { it.weightLb }?.average()
        val wNow = avgWeight(week); val wBefore = avgWeight(week.minusWeeks(1))

        val sessions = db.workouts().allSessions().filter { it.completedAt != null && it.epochDay in days.first().toEpochDay()..days.last().toEpochDay() }
        val minutes = sessions.sumOf { (it.activeMinutes(it.completedAt!!) ?: 0.0).coerceIn(0.0, 300.0) }.roundToInt()

        val rec = StrengthRecords.forWeek(sets, week)
        val prs = rec.records.map { r ->
            val name = runCatching { cat.exercise(r.exerciseKey).name }.getOrDefault(r.exerciseKey)
            PrEntry(name, r.metric.name, setText(r.metric, r.best), setText(r.metric, r.previous))
        }
        return WeeklyReport(
            weekStart = week.toEpochDay(), generatedAt = time.nowMillis(),
            daysLogged = st.daysLogged, kcalTotal = st.kcalTotal, kcalAvg = st.kcalAvg, budgetAvg = budgetAvg,
            deficitTotal = st.deficitTotal, deficitAvg = st.deficitAvg,
            weightAvg = wNow, weightChange = if (wNow != null && wBefore != null) wNow - wBefore else null,
            stepsTotal = st.stepsTotal, stepsAvg = st.stepsAvg, stepDays = st.stepDays,
            stepsBestDay = st.stepsBest?.first?.toEpochDay(), stepsBest = st.stepsBest?.second, stepGoal = p.stepGoal, stepGoalDays = st.stepGoalDays,
            sleepNights = st.sleepNights, sleepScore = st.sleepScoreAvg, sleepAvgMin = st.sleepAvgMin,
            deepAvgMin = st.deepAvgMin, remAvgMin = st.remAvgMin, lightAvgMin = st.lightAvgMin, awakeAvgMin = st.awakeAvgMin,
            bestNightDay = st.bestNight?.date?.toEpochDay(), bestNightScore = st.bestNight?.score,
            worstNightDay = st.worstNight?.date?.toEpochDay(), worstNightScore = st.worstNight?.score, sleepFromStages = st.sleepFromStages,
            workoutsDone = sessions.size, workoutsPlanned = cat.requiredInWeekOf(week), trainingMinutes = minutes,
            prsUnlocked = rec.unlocked, prsUnlockDay = rec.unlocksOn?.toEpochDay(), prs = prs,
        )
    }

    private fun setText(m: RecordMetric, s: LoggedSet): String = when (m) {
        RecordMetric.STRENGTH -> "${fmtLb(s.weightLb!!)} lb × ${s.reps}"
        RecordMetric.REPS -> "${s.reps} reps"
        RecordMetric.HOLD -> s.seconds!!.let { if (it >= 60) "${it / 60}:${"%02d".format(it % 60)}" else "$it s" }
    }

    private fun fmtLb(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)

    companion object {
        val RELEASE: LocalTime = LocalTime.of(6, 0)
        const val MAX_BACKFILL_WEEKS = 26L
    }
}
