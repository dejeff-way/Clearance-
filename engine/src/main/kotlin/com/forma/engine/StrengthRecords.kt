package com.forma.engine

import java.time.LocalDate
import kotlin.math.abs

/** A logged set. Weighted sets have weight and reps; bodyweight sets have reps only; holds have seconds. */
data class LoggedSet(val date: LocalDate, val exerciseKey: String, val weightLb: Double? = null, val reps: Int? = null, val seconds: Int? = null)

enum class RecordMetric { STRENGTH, REPS, HOLD }

data class PersonalRecord(
    val exerciseKey: String,
    val metric: RecordMetric,
    val best: LoggedSet,
    val previous: LoggedSet,
    val bestValue: Double,
    val previousValue: Double,
)

data class WeekRecords(
    /** False while the first 4 weeks of workouts are being recorded as a baseline. */
    val unlocked: Boolean,
    /** First day PRs can be reported (null before any workout is logged). */
    val unlocksOn: LocalDate?,
    val records: List<PersonalRecord>,
)

/**
 * Personal records by **estimated strength**: weight and reps combine into one number (Epley estimated
 * one-rep max, `weight × (1 + reps / 30)`), so a heavier weight or more reps at the same weight both count.
 * Bodyweight exercises use reps; holds use seconds.
 */
object StrengthRecords {
    const val BASELINE_DAYS = 28L

    fun estimatedMax(weightLb: Double, reps: Int): Double = if (reps <= 1) weightLb else weightLb * (1 + reps / 30.0)

    fun value(s: LoggedSet): Pair<RecordMetric, Double>? {
        val w = s.weightLb; val r = s.reps; val sec = s.seconds
        return when {
            w != null && w > 0 && r != null && r > 0 -> RecordMetric.STRENGTH to estimatedMax(w, r)
            r != null && r > 0 -> RecordMetric.REPS to r.toDouble()
            sec != null && sec > 0 -> RecordMetric.HOLD to sec.toDouble()
            else -> null
        }
    }

    /** Records broken in the week starting [weekStart] (Sunday), compared with everything logged before that week. */
    fun forWeek(sets: List<LoggedSet>, weekStart: LocalDate): WeekRecords {
        val scored = sets.mapNotNull { s -> value(s)?.let { (m, v) -> Triple(s, m, v) } }
        val first = scored.minOfOrNull { it.first.date }
        val unlocksOn = first?.plusDays(BASELINE_DAYS)
        val weekEnd = weekStart.plusDays(6)
        if (unlocksOn == null || weekEnd.isBefore(unlocksOn)) return WeekRecords(false, unlocksOn, emptyList())
        val from = maxOf(weekStart, unlocksOn)
        val records = scored.groupBy { it.first.exerciseKey to it.second }.mapNotNull { (key, list) ->
            val before = list.filter { it.first.date.isBefore(weekStart) }.maxByOrNull { it.third } ?: return@mapNotNull null
            val best = list.filter { !it.first.date.isBefore(from) && !it.first.date.isAfter(weekEnd) }.maxByOrNull { it.third } ?: return@mapNotNull null
            if (best.third > before.third + 1e-6 && abs(best.third - before.third) >= 0.05)
                PersonalRecord(key.first, key.second, best.first, before.first, best.third, before.third) else null
        }.sortedByDescending { (it.bestValue - it.previousValue) / it.previousValue }
        return WeekRecords(true, unlocksOn, records)
    }
}
