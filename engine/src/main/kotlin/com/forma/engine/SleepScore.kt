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
    val hasStages get() = deepMin != null && remMin != null
}

data class SleepScoreResult(
    val score: Int,
    /** 0–50: time asleep. */
    val duration: Int,
    /** 0–25: deep and REM sleep. */
    val quality: Int,
    /** 0–25: sleep efficiency (time asleep ÷ time in bed). */
    val restoration: Int,
) {
    val label get() = SleepScore.label(score)
}

/**
 * FORMA sleep score, 0–100. Each part is anchored to a published adult norm; how the parts are weighted
 * (50 / 25 / 25, the same split wearables use) is a convention, not a research finding.
 *
 * - **Duration (50):** full marks at ≥ 7 h asleep — the AASM / Sleep Research Society recommendation for adults
 *   (Watson et al., 2015). Scaled down linearly to 0 at 3 h.
 * - **Quality (25):** deep (N3) sleep ≥ 13 % and REM ≥ 20 % of sleep each earn 12.5 — the low end of typical adult
 *   ranges (N3 ≈ 13–23 %, REM ≈ 20–25 %; Ohayon et al., 2004). Pro-rata below.
 * - **Restoration (25):** sleep efficiency ≥ 85 % earns 25, the usual clinical cut-off for normal sleep; 0 at 65 %.
 *
 * Nights without stage data get no score (only the hours are shown) rather than a guessed one.
 */
object SleepScore {
    const val FULL_DURATION_MIN = 420.0
    const val ZERO_DURATION_MIN = 180.0
    const val DEEP_NORM_PCT = 13.0
    const val REM_NORM_PCT = 20.0
    const val EFFICIENCY_NORM_PCT = 85.0
    const val EFFICIENCY_ZERO_PCT = 65.0

    /** Null when the night has no stage data. */
    fun score(n: SleepNight): SleepScoreResult? {
        if (!n.hasStages || n.asleepMin <= 0) return null
        val duration = 50 * ((n.asleepMin - ZERO_DURATION_MIN) / (FULL_DURATION_MIN - ZERO_DURATION_MIN)).coerceIn(0.0, 1.0)
        val deepPct = 100 * n.deepMin!! / n.asleepMin
        val remPct = 100 * n.remMin!! / n.asleepMin
        val quality = 12.5 * (deepPct / DEEP_NORM_PCT).coerceIn(0.0, 1.0) + 12.5 * (remPct / REM_NORM_PCT).coerceIn(0.0, 1.0)
        val awake = n.awakeMin ?: 0.0
        val efficiency = 100 * n.asleepMin / (n.asleepMin + awake)
        val restoration = 25 * ((efficiency - EFFICIENCY_ZERO_PCT) / (EFFICIENCY_NORM_PCT - EFFICIENCY_ZERO_PCT)).coerceIn(0.0, 1.0)
        val d = duration.roundToInt(); val q = quality.roundToInt(); val r = restoration.roundToInt()
        return SleepScoreResult((d + q + r).coerceIn(0, 100), d, q, r)
    }

    fun label(score: Int) = when {
        score >= 90 -> "Excellent"
        score >= 80 -> "Good"
        score >= 60 -> "Fair"
        else -> "Poor"
    }
}
