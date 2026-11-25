package com.forma.engine

import java.time.LocalDate
import kotlin.math.roundToInt

/** What's known about one day. Nulls mean "no data", never zero. */
data class WeekDayData(
    val date: LocalDate,
    /** Logged food; null when nothing was logged. */
    val kcal: Double? = null,
    /** Full-day deficit (expenditure − food); only for days with food logged. */
    val deficit: Int? = null,
    val steps: Long? = null,
    /** The night that ended on this morning. */
    val sleep: SleepNight? = null,
)

data class NightScore(val date: LocalDate, val score: Int, val asleepMin: Double)

data class WeekStats(
    val weekStart: LocalDate,
    val daysLogged: Int,
    val kcalTotal: Int,
    val kcalAvg: Int?,
    val deficitTotal: Int,
    val deficitAvg: Int?,
    /** 3,500 kcal ≈ 1 lb of fat. */
    val fatLb: Double,
    val stepsTotal: Long,
    val stepsAvg: Long?,
    val stepDays: Int,
    val stepsBest: Pair<LocalDate, Long>?,
    val stepGoalDays: Int,
    val sleepNights: Int,
    val sleepScoreAvg: Int?,
    val sleepAvgMin: Int?,
    val deepAvgMin: Int?,
    val remAvgMin: Int?,
    val lightAvgMin: Int?,
    val awakeAvgMin: Int?,
    val bestNight: NightScore?,
    val worstNight: NightScore?,
    val sleepFromStages: Boolean,
)

object WeeklyStats {
    fun compute(weekStart: LocalDate, days: List<WeekDayData>, stepGoal: Int): WeekStats {
        val week = days.filter { !it.date.isBefore(weekStart) && it.date.isBefore(weekStart.plusDays(7)) }
        val food = week.filter { it.kcal != null }
        val deficits = week.mapNotNull { it.deficit }
        val steps = week.filter { (it.steps ?: 0) > 0 }
        val nights = week.mapNotNull { d -> d.sleep?.takeIf { it.asleepMin > 0 }?.let { d.date to it } }
        val scored = nights.map { (d, n) -> NightScore(d, SleepScore.score(n).score, n.asleepMin) }
        fun avg(f: (SleepNight) -> Double?) = nights.mapNotNull { f(it.second) }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        val deficitTotal = deficits.sum()
        return WeekStats(
            weekStart = weekStart,
            daysLogged = food.size,
            kcalTotal = food.sumOf { it.kcal!! }.roundToInt(),
            kcalAvg = food.takeIf { it.isNotEmpty() }?.map { it.kcal!! }?.average()?.roundToInt(),
            deficitTotal = deficitTotal,
            deficitAvg = deficits.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            fatLb = deficitTotal / 3500.0,
            stepsTotal = steps.sumOf { it.steps!! },
            stepsAvg = steps.takeIf { it.isNotEmpty() }?.let { s -> s.sumOf { it.steps!! } / s.size },
            stepDays = steps.size,
            stepsBest = steps.maxByOrNull { it.steps!! }?.let { it.date to it.steps!! },
            stepGoalDays = steps.count { it.steps!! >= stepGoal },
            sleepNights = nights.size,
            sleepScoreAvg = scored.takeIf { it.isNotEmpty() }?.map { it.score }?.average()?.roundToInt(),
            sleepAvgMin = avg { it.asleepMin },
            deepAvgMin = avg { it.deepMin }, remAvgMin = avg { it.remMin }, lightAvgMin = avg { it.lightMin }, awakeAvgMin = avg { it.awakeMin },
            bestNight = scored.maxByOrNull { it.score },
            worstNight = scored.takeIf { it.size > 1 }?.minByOrNull { it.score }?.takeIf { w -> w.score < scored.maxOf { it.score } },
            sleepFromStages = nights.any { it.second.hasStages },
        )
    }
}
