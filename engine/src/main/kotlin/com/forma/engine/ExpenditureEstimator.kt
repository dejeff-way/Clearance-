package com.forma.engine

import kotlin.math.max

enum class ExpenditureSource {
    /** Health Connect TotalCaloriesBurned (includes resting + active energy as measured by the user's devices). */
    HEALTH_CONNECT_TOTAL,

    /** BMR estimate + Health Connect ActiveCaloriesBurned. */
    HEALTH_CONNECT_ACTIVE,

    /** No usable measured data: BMR × baseline factor + separately identified activity. */
    ESTIMATED,
}

enum class LineKind { INCLUDED, EXCLUDED, INFO }

/** One row in the transparent "how we got this number" breakdown. */
data class ExpenditureLine(
    val label: String,
    val kcal: Double?,
    val kind: LineKind,
    val detail: String? = null,
)

data class Expenditure(
    val source: ExpenditureSource,
    /** Best estimate for the full calendar day. */
    val projectedDayKcal: Double,
    /** Estimated energy burned up to now (equals [projectedDayKcal] for completed days). */
    val burnedSoFarKcal: Double,
    /** True when the day is over and the figure covers the full day. */
    val dayComplete: Boolean,
    val bmrKcal: Double,
    val lines: List<ExpenditureLine>,
    val notes: List<String>,
    /** Health Connect provided a total, but it was too low to be a complete daily figure, so it was not used. */
    val healthConnectTotalRejected: Boolean = false,
    /** Resting/baseline part of [projectedDayKcal]. */
    val baselineKcal: Double = 0.0,
    /** Conservative adjustment applied to wearable activity (negative = reduced). */
    val wearableAdjustmentKcal: Double = 0.0,
) {
    /** Activity part of [projectedDayKcal] (imported active calories, or estimated activity). */
    val activeKcal: Double get() = (projectedDayKcal - baselineKcal).coerceAtLeast(0.0)
}

/**
 * Turns a day's raw inputs into an expenditure estimate, choosing ONE source of truth so energy is never counted twice.
 *
 * Priority:
 *  1. Health Connect total calories, when it looks complete. Steps, device-recorded sessions and FORMA program
 *     workouts are NOT added because the total already reflects that movement. Only sessions the user explicitly
 *     marked as "not recorded by a device" are added (as net calories above the resting baseline).
 *  2. BMR + Health Connect active calories (same exclusion rules).
 *  3. BMR × baseline factor + max(step estimate, net session estimate). The larger of the two is used rather than
 *     the sum, because walking or treadmill sessions also generate steps.
 */
object ExpenditureEstimator {
    /** Resting multiplier used to project the remaining hours of today when a measured total is available. */
    const val REMAINING_DAY_FACTOR = 1.1

    /** Daily steps assumed to be already covered by the sedentary baseline factor. */
    const val BASELINE_STEPS = 5000L

    /** Net kcal per step per kg of body weight (above resting). Rough population average. */
    const val NET_KCAL_PER_STEP_PER_KG = 0.0005

    /** Completed day: a Health Connect total below this fraction of BMR is treated as incomplete. */
    const val COMPLETE_DAY_MIN_BMR_FRACTION = 0.8

    /** Today: a running total below this fraction of BMR × elapsed fraction is treated as incomplete. */
    const val PARTIAL_DAY_MIN_BMR_FRACTION = 0.75

    fun estimate(p: EnergyProfile, input: DayEnergyInputs): Expenditure {
        val bmr = Metabolism.bmr(p)
        val f = input.elapsedFraction.coerceIn(0.0, 1.0)
        val complete = f >= 1.0
        val notes = mutableListOf<String>()
        val weightKg = Metabolism.lbToKg(p.weightLb)
        val baseline = bmr * p.baselineActivityFactor
        val baselineMetEquivalent = baseline / (weightKg * 24.0)

        val (sessions, duplicates) = dedupeSessions(input.sessions)

        fun net(s: ActivitySession): Double = netSessionKcal(s, baseline, baselineMetEquivalent, weightKg)

        val total = input.hcTotalKcal
        var rejected = false
        if (total != null && total > 0) {
            val threshold = if (complete) COMPLETE_DAY_MIN_BMR_FRACTION * bmr
            else PARTIAL_DAY_MIN_BMR_FRACTION * bmr * max(f, 0.05)
            if (total >= threshold) {
                val restingSoFar = input.hcBasalKcal?.takeIf { it > 0 } ?: (bmr * f)
                val activity = max(0.0, total - restingSoFar)
                val adj = activity * p.wearableAdjustmentPct / 100.0
                val extra = mutableListOf<ExpenditureLine>()
                if (adj != 0.0) extra += ExpenditureLine("Wearable adjustment (${p.wearableAdjustmentPct}% of activity)", adj, LineKind.INCLUDED,
                    "Your conservative setting, applied to the ${activity.toInt()} kcal above resting energy.")
                return measuredResult(
                    source = ExpenditureSource.HEALTH_CONNECT_TOTAL,
                    measuredSoFar = total + adj,
                    measuredLine = ExpenditureLine(
                        if (complete) "Health Connect total burned" else "Health Connect total burned so far",
                        total, LineKind.INCLUDED,
                        "Resting + active energy reported by your connected devices." +
                            (if (input.hcBasalKcal != null) " Includes ${input.hcBasalKcal.toInt()} kcal resting (basal)." else ""),
                    ),
                    restOfDay = if (complete) 0.0 else (1 - f) * bmr * REMAINING_DAY_FACTOR,
                    p = p, input = input, sessions = sessions, duplicates = duplicates, bmr = bmr,
                    complete = complete, net = ::net, notes = notes, extraLines = extra, restingSoFar = restingSoFar, adjustment = adj,
                )
            } else {
                rejected = true
                notes += "Health Connect reported ${total.toInt()} kcal total, which is too low to be a complete " +
                    "daily figure (some apps only write partial data). FORMA used another method instead."
            }
        }

        val active = input.hcActiveKcal
        if (active != null && active > 0) {
            val basal = input.hcBasalKcal?.takeIf { it > 0 }
            val restingSoFar = basal ?: (bmr * f)
            val adj = active * p.wearableAdjustmentPct / 100.0
            val extra = mutableListOf<ExpenditureLine>()
            if (adj != 0.0) extra += ExpenditureLine("Wearable adjustment (${p.wearableAdjustmentPct}% of activity)", adj, LineKind.INCLUDED,
                "Your conservative setting, applied to imported active calories.")
            val r = measuredResult(
                source = ExpenditureSource.HEALTH_CONNECT_ACTIVE,
                measuredSoFar = restingSoFar + active + adj,
                measuredLine = ExpenditureLine(
                    "Health Connect active calories", active, LineKind.INCLUDED,
                    if (basal != null) "Added to resting energy reported by your device." else "Added to an estimated resting expenditure (Mifflin–St Jeor BMR).",
                ),
                restOfDay = bmr * (1 - f),
                p = p, input = input, sessions = sessions, duplicates = duplicates, bmr = bmr,
                complete = complete, net = ::net, notes = notes,
                bmrLine = if (basal != null) ExpenditureLine(if (complete) "Resting energy (Health Connect basal)" else "Resting energy so far (Health Connect basal)",
                    basal, LineKind.INCLUDED, "Measured by your device rather than estimated.")
                else ExpenditureLine("Resting energy (BMR, estimated)", bmr * f, LineKind.INCLUDED,
                    if (complete) "Mifflin–St Jeor." else "Mifflin–St Jeor, for the ${pct(f)} of the day elapsed."),
                extraLines = extra, restingSoFar = restingSoFar, adjustment = adj,
            )
            return r.copy(healthConnectTotalRejected = rejected)
        }

        // 3. Pure estimate.
        val lines = mutableListOf<ExpenditureLine>()
        lines += ExpenditureLine(
            "Baseline (BMR × ${"%.2f".format(p.baselineActivityFactor)})", baseline, LineKind.INCLUDED,
            "Resting energy plus light daily living. BMR ${bmr.toInt()} kcal (Mifflin–St Jeor).",
        )
        val sessionNet = sessions.sumOf { net(it) }
        val stepsKcal = input.steps?.let { max(0L, it - BASELINE_STEPS) * NET_KCAL_PER_STEP_PER_KG * weightKg }
        val activity: Double
        if (stepsKcal != null && sessions.isNotEmpty()) {
            activity = max(stepsKcal, sessionNet)
            val stepsWins = stepsKcal >= sessionNet
            lines += ExpenditureLine(
                "Steps beyond ${BASELINE_STEPS / 1000}k (${input.steps})", stepsKcal,
                if (stepsWins) LineKind.INCLUDED else LineKind.EXCLUDED,
                if (stepsWins) "Larger than the session estimate, so used instead of it."
                else "Not added: sessions already account for this movement.",
            )
            sessions.forEach { s ->
                lines += ExpenditureLine(
                    "${s.title} (${s.durationMinutes.toInt()} min, net)", net(s),
                    if (stepsWins) LineKind.EXCLUDED else LineKind.INCLUDED,
                    if (stepsWins) "Not added: step estimate already covers it." else sessionDetail(s),
                )
            }
            notes += "Steps and exercise sessions overlap (walking sessions produce steps), so FORMA uses the larger " +
                "of the two instead of adding both."
        } else if (stepsKcal != null) {
            activity = stepsKcal
            lines += ExpenditureLine("Steps beyond ${BASELINE_STEPS / 1000}k (${input.steps})", stepsKcal,
                LineKind.INCLUDED, "Net energy above the baseline, estimated from steps and body weight.")
        } else {
            activity = sessionNet
            sessions.forEach { s ->
                lines += ExpenditureLine("${s.title} (${s.durationMinutes.toInt()} min, net)", net(s),
                    LineKind.INCLUDED, sessionDetail(s))
            }
        }
        duplicates.forEach { d ->
            lines += ExpenditureLine(d.title, null, LineKind.EXCLUDED,
                "Overlaps a Health Connect session — counted once.")
        }
        notes += "No measured expenditure for this day — values are estimates from your profile and logged activity."
        return Expenditure(
            source = ExpenditureSource.ESTIMATED,
            projectedDayKcal = baseline + activity,
            burnedSoFarKcal = baseline * f + activity,
            dayComplete = complete,
            bmrKcal = bmr,
            lines = lines,
            notes = notes,
            healthConnectTotalRejected = rejected,
            baselineKcal = baseline,
        )
    }

    private fun measuredResult(
        source: ExpenditureSource,
        measuredSoFar: Double,
        measuredLine: ExpenditureLine,
        restOfDay: Double,
        p: EnergyProfile,
        input: DayEnergyInputs,
        sessions: List<ActivitySession>,
        duplicates: List<ActivitySession>,
        bmr: Double,
        complete: Boolean,
        net: (ActivitySession) -> Double,
        notes: MutableList<String>,
        bmrLine: ExpenditureLine? = null,
        extraLines: List<ExpenditureLine> = emptyList(),
        restingSoFar: Double = 0.0,
        adjustment: Double = 0.0,
    ): Expenditure {
        val lines = mutableListOf<ExpenditureLine>()
        if (bmrLine != null) lines += bmrLine
        lines += measuredLine
        lines += extraLines
        var extra = 0.0
        sessions.forEach { s ->
            if (s.source != SessionSource.HEALTH_CONNECT && !s.recordedByDevice) {
                val n = net(s)
                extra += n
                lines += ExpenditureLine("${s.title} (${s.durationMinutes.toInt()} min, net)", n, LineKind.INCLUDED,
                    "Marked as not recorded by any device, so added on top.")
            } else {
                lines += ExpenditureLine(s.title, null, LineKind.EXCLUDED,
                    when (s.source) {
                        SessionSource.HEALTH_CONNECT -> "Already inside the Health Connect figure."
                        SessionSource.PROGRAM -> "FORMA workout — assumed captured by your device. Log it as " +
                            "'not recorded by a device' if your watch didn't track it."
                        SessionSource.MANUAL -> "Marked as recorded by a device — already included."
                    })
            }
        }
        duplicates.forEach { d ->
            lines += ExpenditureLine(d.title, null, LineKind.EXCLUDED, "Duplicate of a Health Connect session.")
        }
        if (input.steps != null) {
            lines += ExpenditureLine("Steps (${input.steps})", null, LineKind.EXCLUDED,
                "Already reflected in the measured figure — not added again.")
        }
        if (!complete && restOfDay > 0) {
            lines += ExpenditureLine("Projected rest of day (resting)", restOfDay, LineKind.INCLUDED,
                "Assumes ${"%.1f".format(REMAINING_DAY_FACTOR)} × BMR for the remaining hours. No future exercise assumed.")
        }
        val soFar = measuredSoFar + extra
        if (!complete) notes += "Today is in progress; the full-day figure will rise if you are more active."
        return Expenditure(
            source = source,
            projectedDayKcal = soFar + restOfDay,
            burnedSoFarKcal = soFar,
            dayComplete = complete,
            bmrKcal = bmr,
            lines = lines,
            notes = notes,
            baselineKcal = restingSoFar + restOfDay,
            wearableAdjustmentKcal = adjustment,
        )
    }

    /**
     * Removes MANUAL/PROGRAM sessions that were recorded by a device and overlap in time with a Health Connect
     * session — they are the same workout seen twice.
     */
    fun dedupeSessions(all: List<ActivitySession>): Pair<List<ActivitySession>, List<ActivitySession>> {
        val hc = all.filter { it.source == SessionSource.HEALTH_CONNECT }
        val kept = mutableListOf<ActivitySession>()
        val dupes = mutableListOf<ActivitySession>()
        for (s in all) {
            if (s.source != SessionSource.HEALTH_CONNECT && s.recordedByDevice && hc.any { overlaps(it, s) }) {
                dupes += s
            } else kept += s
        }
        return kept to dupes
    }

    private fun overlaps(a: ActivitySession, b: ActivitySession): Boolean {
        val aS = a.startEpochMs ?: return false
        val aE = a.endEpochMs ?: return false
        val bS = b.startEpochMs ?: return false
        val bE = b.endEpochMs ?: (bS + (b.durationMinutes * 60_000).toLong())
        return aS < bE && bS < aE
    }

    /** Energy above the resting baseline for the session's duration (never negative). */
    fun netSessionKcal(s: ActivitySession, baselinePerDay: Double, baselineMet: Double, weightKg: Double): Double {
        val hours = s.durationMinutes / 60.0
        return if (s.kcal != null) {
            max(0.0, s.kcal - baselinePerDay / 24.0 * hours)
        } else {
            max(0.0, (s.met - baselineMet) * weightKg * hours)
        }
    }

    private fun sessionDetail(s: ActivitySession) =
        if (s.kcal != null) "Reported ${s.kcal.toInt()} kcal gross; resting share removed."
        else "Estimated at ${"%.1f".format(s.met)} MET; resting share removed."

    private fun pct(f: Double) = "${(f * 100).toInt()}%"
}
