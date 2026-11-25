package com.forma.engine

import kotlin.math.roundToInt

/** One night of sleep (minutes). Stage minutes are null when the tracker didn't record stages. */
data class SleepNight(
    val asleepMin: Double,
    val deepMin: Double? = null,
    val lightMin: Double? = null,
    val remMin: Double? = null,
    val awakeMin: Double? = null,
) {
    val hasStages get() = deepMin != null || remMin != null || lightMin != null
}

data class SleepScoreResult(
    val score: Int,
    /** 0–50: time asleep. */
    val duration: Int,
    /** 0–25: share of deep + REM sleep. */
    val quality: Int,
    /** 0–25: how little time was spent awake. */
    val restoration: Int,
    val basedOnStages: Boolean,
) {
    val label get() = SleepScore.label(score)
}

/**
 * FORMA sleep score, 0–100. It is modelled on how wearables score sleep, but it is FORMA's own number and won't
 * match Fitbit's exactly (Fitbit's score isn't shared with Health Connect).
 *
 * - **Duration (50):** full marks from 7 h asleep, nothing under 3 h, linear between.
 * - **Quality (25):** deep sleep ≥ 15 % of sleep earns 12.5, REM ≥ 20 % earns 12.5, pro-rata below.
 * - **Restoration (25):** awake ≤ 5 % of time in bed earns 25, 25 % or more earns 0.
 *
 * Without stage data only duration is known, so quality and restoration are given a neutral half-score.
 */
object SleepScore {
    const val FULL_DURATION_MIN = 420.0
    const val ZERO_DURATION_MIN = 180.0

    fun score(n: SleepNight): SleepScoreResult {
        val duration = 50 * ((n.asleepMin - ZERO_DURATION_MIN) / (FULL_DURATION_MIN - ZERO_DURATION_MIN)).coerceIn(0.0, 1.0)
        if (!n.hasStages || n.asleepMin <= 0) {
            val d = duration.roundToInt()
            return SleepScoreResult(d + 25, d, 12, 13, basedOnStages = false)
        }
        val deepPct = 100 * (n.deepMin ?: 0.0) / n.asleepMin
        val remPct = 100 * (n.remMin ?: 0.0) / n.asleepMin
        val quality = 12.5 * (deepPct / 15).coerceIn(0.0, 1.0) + 12.5 * (remPct / 20).coerceIn(0.0, 1.0)
        val awake = n.awakeMin ?: 0.0
        val awakePct = 100 * awake / (n.asleepMin + awake)
        val restoration = 25 * (1 - ((awakePct - 5) / 20).coerceIn(0.0, 1.0))
        val d = duration.roundToInt(); val q = quality.roundToInt(); val r = restoration.roundToInt()
        return SleepScoreResult((d + q + r).coerceIn(0, 100), d, q, r, basedOnStages = true)
    }

    fun label(score: Int) = when {
        score >= 90 -> "Excellent"
        score >= 80 -> "Good"
        score >= 60 -> "Fair"
        else -> "Poor"
    }
}
