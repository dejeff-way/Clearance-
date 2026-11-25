package com.forma.app.data.repo

import com.forma.app.data.db.CarryoverEntity
import com.forma.app.data.db.DailyEnergyEntity
import com.forma.app.data.db.DailyFoodTotals
import com.forma.app.data.db.ExerciseSessionEntity
import com.forma.app.data.db.ProfileEntity
import com.forma.app.data.db.WeightEntity
import com.forma.app.data.db.WorkoutSessionEntity
import com.forma.engine.ActivitySession
import com.forma.engine.BudgetCalculator
import com.forma.engine.BudgetMode
import com.forma.engine.CarryoverItem
import com.forma.engine.CarryoverKind
import com.forma.engine.CarryoverLedger
import com.forma.engine.CarryoverState
import com.forma.engine.DayEnergyInputs
import com.forma.engine.DaySummary
import com.forma.engine.EnergyProfile
import com.forma.engine.ExpenditureEstimator
import com.forma.engine.Macros
import com.forma.engine.SessionSource
import com.forma.engine.Sex
import java.time.LocalDate
import kotlin.math.roundToInt

/** Immutable snapshot of everything the calorie engine reads. */
data class EnergySnapshot(
    val profile: ProfileEntity,
    val weights: List<WeightEntity>,
    val energy: Map<Long, DailyEnergyEntity>,
    val sessions: List<ExerciseSessionEntity>,
    val workouts: List<WorkoutSessionEntity>,
    val food: Map<Long, DailyFoodTotals>,
    val carryover: List<CarryoverEntity>,
    val today: LocalDate,
    val elapsedFractionToday: Double,
    val workoutDefs: Map<String, com.forma.app.data.db.WorkoutEntity> = emptyMap(),
)

/**
 * Adapts stored rows into engine inputs. Every number shown in the app comes through here, so the dashboard,
 * history, carryover and progress screens always agree.
 */
class EnergyModel(val s: EnergySnapshot) {
    private val sessionsByDay = s.sessions.groupBy { it.epochDay }
    private val workoutsByDay = s.workouts.filter { it.completedAt != null }.groupBy { it.epochDay }
    private val sortedWeights = s.weights.sortedBy { it.measuredAt }
    private val baseCache = HashMap<Long, Int>()

    fun weightOn(day: LocalDate): Double {
        val d = day.toEpochDay()
        return sortedWeights.lastOrNull { it.epochDay <= d }?.weightLb ?: s.profile.currentWeightLb
    }

    fun profileFor(day: LocalDate): EnergyProfile = s.profile.toEnergyProfile(weightOn(day))

    fun inputs(day: LocalDate): DayEnergyInputs {
        val d = day.toEpochDay()
        val e = s.energy[d]
        val f = when {
            day.isBefore(s.today) -> 1.0
            day == s.today -> s.elapsedFractionToday
            else -> 0.0
        }
        val manual = sessionsByDay[d].orEmpty().map { it.toActivity() }
        val program = workoutsByDay[d].orEmpty().map { w ->
            val t = s.workoutDefs[w.templateKey]
            val minutes = if (w.startedAt != null && w.completedAt != null)
                (w.activeMinutes(w.completedAt) ?: 50.0).coerceIn(15.0, 120.0)
            else (t?.durationMinutes ?: 50).toDouble()
            ActivitySession(
                id = "program-${w.id}", source = SessionSource.PROGRAM, title = "FORMA · ${w.title}",
                startEpochMs = w.completedAt?.let { it - (minutes * 60_000).toLong() } ?: w.startedAt, endEpochMs = w.completedAt, durationMinutes = minutes,
                met = t?.met ?: 4.5, recordedByDevice = true,
            )
        }
        return DayEnergyInputs(day, f, e?.hcTotalKcal, e?.hcActiveKcal, e?.hcBasalKcal, e?.steps, manual + program)
    }

    fun consumed(day: LocalDate): Macros {
        val t = s.food[day.toEpochDay()] ?: return Macros.ZERO
        return Macros(t.kcal, t.proteinG, t.carbsG, t.fatG)
    }

    fun hasFood(day: LocalDate) = (s.food[day.toEpochDay()]?.items ?: 0) > 0

    fun baseBudget(day: LocalDate): Int = baseCache.getOrPut(day.toEpochDay()) {
        val p = profileFor(day)
        BudgetCalculator.baseBudget(p, ExpenditureEstimator.estimate(p, inputs(day)).projectedDayKcal)
    }

    /** First day automatic carryover applies to, or null when it's turned off. */
    val autoCarryoverFrom: LocalDate? get() =
        if (s.profile.autoCarryover) s.profile.autoCarryoverFromEpochDay?.let(LocalDate::ofEpochDay) else null

    val carryover: CarryoverState by lazy {
        val autoFrom = autoCarryoverFrom
        val items = s.carryover.filter { !it.cancelled }
            // From the day automatic carryover starts, "ate more than I logged" estimates are already part of that
            // day's logged food, so they must not also count as a separate carryover item.
            .filterNot { autoFrom != null && it.epochDay >= autoFrom.toEpochDay() && it.kind != CarryoverKind.OVERAGE.name }
            .map {
                CarryoverItem(it.id, LocalDate.ofEpochDay(it.epochDay), it.kcal,
                    if (it.kind == CarryoverKind.UNLOGGED_ADJUSTMENT.name) CarryoverKind.UNLOGGED_ADJUSTMENT else CarryoverKind.OVERAGE)
            }
        CarryoverLedger.compute(items, s.profile.maxRepaymentPerDay, s.profile.minimumBudgetKcal, s.today, ::baseBudget, autoFrom) { d, repaid ->
            if (!hasFood(d)) null
            else (consumed(d).kcal - (baseBudget(d) - repaid)).roundToInt().coerceAtLeast(0)
        }
    }

    private fun scheduledRepayment(day: LocalDate): Int =
        if (day == s.today) carryover.scheduledToday
        else carryover.history.firstOrNull { it.date == day }?.scheduled ?: 0

    fun summary(day: LocalDate): DaySummary =
        DaySummary.compute(profileFor(day), inputs(day), consumed(day), scheduledRepayment(day))

    val today: DaySummary by lazy { summary(s.today) }

    /** Protein, carb and fat targets for [day], scaled to that day's calorie budget. */
    fun macroTargets(day: LocalDate, summary: DaySummary = summary(day)): com.forma.engine.MacroTargets =
        com.forma.engine.MacroCalculator.targets(
            summary.budget.finalBudget, s.profile.proteinTargetG, s.profile.fatPctOfBudget, s.profile.fatTargetG, s.profile.carbTargetG,
            summary.consumed,
        )

    /** Summaries for the [days] days ending today (oldest first). */
    fun history(days: Int): List<Pair<LocalDate, DaySummary>> =
        (days - 1 downTo 0).map { s.today.minusDays(it.toLong()) }.map { it to summary(it) }
}

fun ProfileEntity.toEnergyProfile(weightLb: Double = currentWeightLb) = EnergyProfile(
    sex = if (sex == "FEMALE") Sex.FEMALE else Sex.MALE,
    ageYears = ageYears, heightInches = heightInches, weightLb = weightLb,
    baselineActivityFactor = baselineActivityFactor, maintenanceActivityFactor = maintenanceActivityFactor,
    desiredDeficitKcal = desiredDeficitKcal, minimumBudgetKcal = minimumBudgetKcal,
    maxCarryoverRepaymentPerDay = maxRepaymentPerDay,
    budgetMode = if (budgetMode == BudgetMode.FIXED.name) BudgetMode.FIXED else BudgetMode.DYNAMIC,
    fixedBudgetKcal = fixedBudgetKcal, proteinTargetG = proteinTargetG,
    wearableAdjustmentPct = wearableAdjustmentPct,
    maxActivityBudgetIncreaseKcal = maxActivityBonusKcal.takeIf { it > 0 },
)

fun ExerciseSessionEntity.toActivity() = ActivitySession(
    id = "s-$id",
    source = if (source == "HEALTH_CONNECT") SessionSource.HEALTH_CONNECT else SessionSource.MANUAL,
    title = title, startEpochMs = startTime, endEpochMs = endTime, durationMinutes = durationMinutes,
    kcal = kcal, met = met, recordedByDevice = source == "HEALTH_CONNECT" || recordedByDevice,
)
