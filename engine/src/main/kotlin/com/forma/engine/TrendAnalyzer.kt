package com.forma.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

data class WeightPoint(val date: LocalDate, val lb: Double)

data class TrendPoint(val date: LocalDate, val raw: Double, val average7: Double)

enum class RecommendationKind { ON_TRACK, EAT_MORE, EAT_LESS, HOLD, CHECK_LOGGING }

data class Recommendation(
    val kind: RecommendationKind,
    /** Suggested change to the desired deficit (positive = larger deficit / fewer kcal). 0 = no change. */
    val deficitChangeKcal: Int,
    val observedLbPerWeek: Double,
    val expectedLbPerWeek: Double?,
    /** Maintenance implied by intake and weight trend; null if intake data is too sparse. */
    val observedMaintenanceKcal: Int?,
    val headline: String,
    val explanation: String,
)

/** Uses trends, not single days. */
object TrendAnalyzer {
    const val MIN_SPAN_DAYS = 14
    const val MAX_ADJUSTMENT = 200

    /** One value per day (mean of that day's weigh-ins). */
    fun daily(points: List<WeightPoint>): List<WeightPoint> =
        points.groupBy { it.date }.map { (d, v) -> WeightPoint(d, v.map { it.lb }.average()) }.sortedBy { it.date }

    /** For each weigh-in day, the mean of all daily values within the trailing 7 calendar days (inclusive). */
    fun rolling7(points: List<WeightPoint>): List<TrendPoint> {
        val d = daily(points)
        return d.map { p ->
            val window = d.filter { !it.date.isAfter(p.date) && it.date.isAfter(p.date.minusDays(7)) }
            TrendPoint(p.date, p.lb, window.map { it.lb }.average())
        }
    }

    /** Mean weight per Sunday-start week. */
    fun weeklyAverages(points: List<WeightPoint>): List<Pair<LocalDate, Double>> =
        daily(points).groupBy { weekStart(it.date) }.map { (w, v) -> w to v.map { it.lb }.average() }
            .sortedBy { it.first }

    fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value % 7).toLong())

    /** Least-squares slope in lb/day. Null with fewer than 2 points. */
    fun slopePerDay(points: List<WeightPoint>): Double? {
        val d = daily(points)
        if (d.size < 2) return null
        val x0 = d.first().date
        val xs = d.map { ChronoUnit.DAYS.between(x0, it.date).toDouble() }
        val ys = d.map { it.lb }
        val mx = xs.average(); val my = ys.average()
        val den = xs.sumOf { (it - mx) * (it - mx) }
        if (den == 0.0) return null
        return xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den
    }

    fun spanDays(points: List<WeightPoint>): Long {
        if (points.isEmpty()) return 0
        val s = points.minOf { it.date }; val e = points.maxOf { it.date }
        return ChronoUnit.DAYS.between(s, e)
    }

    /**
     * Optional recommendation once ≥14 days of weight data exist. Changes are small (≤200 kcal) and never applied
     * automatically.
     *
     * @param window weigh-ins to analyse (typically the last 28 days)
     * @param avgIntakeKcal mean logged intake over the same window (only days with food logged)
     * @param avgExpenditureKcal mean estimated expenditure over the same window
     * @param loggedDays number of days with food logged in the window
     * @param programStart the first weigh-in date overall (early water-weight caveat)
     */
    fun recommend(
        window: List<WeightPoint>,
        avgIntakeKcal: Double?,
        avgExpenditureKcal: Double?,
        loggedDays: Int,
        currentWeightLb: Double,
        programStart: LocalDate,
        today: LocalDate,
    ): Recommendation? {
        if (spanDays(window) < MIN_SPAN_DAYS - 1 || daily(window).size < 6) return null
        val slope = slopePerDay(window) ?: return null
        val lbWeek = slope * 7
        val lossPerWeek = -lbWeek
        val expected = if (avgIntakeKcal != null && avgExpenditureKcal != null)
            (avgExpenditureKcal - avgIntakeKcal) * 7 / Metabolism.KCAL_PER_LB else null
        val maintenance = if (avgIntakeKcal != null && loggedDays >= 10)
            (avgIntakeKcal + lossPerWeek / 7 * Metabolism.KCAL_PER_LB).roundToInt() else null
        val fastLimit = maxOf(2.0, currentWeightLb * 0.01)
        val earlyPhase = ChronoUnit.DAYS.between(programStart, today) < 21
        val obs = "%.1f".format(abs(lossPerWeek))

        return when {
            loggedDays < 10 -> Recommendation(
                RecommendationKind.CHECK_LOGGING, 0, lbWeek, expected, maintenance,
                "Log a few more days of food",
                "Your weight is trending ${dir(lbWeek)} $obs lb/week, but only $loggedDays of the last days have " +
                    "food logged. FORMA needs at least 10 logged days to compare intake with the trend.",
            )
            lossPerWeek > fastLimit && earlyPhase -> Recommendation(
                RecommendationKind.HOLD, 0, lbWeek, expected, maintenance,
                "Fast early loss — hold steady",
                "You're losing about $obs lb/week. In the first few weeks much of this is water and glycogen, " +
                    "so no change is suggested yet. Check again in a week.",
            )
            lossPerWeek > fastLimit -> Recommendation(
                RecommendationKind.EAT_MORE, -MAX_ADJUSTMENT, lbWeek, expected, maintenance,
                "Consider eating ~$MAX_ADJUSTMENT kcal more",
                "Your 7-day trend shows about $obs lb/week of loss, faster than ~${"%.1f".format(fastLimit)} " +
                    "lb/week. A slightly smaller deficit helps protect muscle and training quality.",
            )
            lossPerWeek < 0 -> Recommendation(
                RecommendationKind.EAT_LESS, 150, lbWeek, expected, maintenance,
                "Trend is rising — small adjustment",
                "Your trend is up about $obs lb/week despite a planned deficit. Before cutting further, check " +
                    "for unlogged items, oils and portion sizes. If logging is accurate, a 150 kcal larger deficit is a " +
                    "modest next step.",
            )
            lossPerWeek < 0.5 -> Recommendation(
                RecommendationKind.EAT_LESS, 150, lbWeek, expected, maintenance,
                "Progress has slowed — consider 150 kcal less",
                "You're losing about $obs lb/week. Expenditure estimates may be running high. A modest 150 kcal " +
                    "change (never below your minimum budget) is suggested — or keep going for another week.",
            )
            else -> Recommendation(
                RecommendationKind.ON_TRACK, 0, lbWeek, expected, maintenance,
                "On track",
                "You're losing about $obs lb/week — a sustainable rate. No change suggested.",
            )
        }
    }

    private fun dir(lbWeek: Double) = if (lbWeek < 0) "down" else "up"
}
