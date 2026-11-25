package com.forma.app.ui.metrics

import com.forma.app.data.db.DailyEnergyEntity
import com.forma.app.data.prefs.AppSettings
import com.forma.app.data.prefs.LengthUnit
import com.forma.app.data.repo.EnergyModel
import com.forma.app.health.HcDataType
import com.forma.engine.ExpenditureSource
import com.forma.engine.TrendAnalyzer
import java.time.LocalDate

enum class ChartKind { BARS, LINE, BALANCE }

/** Every metric with a history screen. [hcType] is the Health Connect permission it depends on, if any. */
enum class Metric(val label: String, val chart: ChartKind, val hcType: HcDataType?, val higherIsBetter: Boolean? = null) {
    STEPS("Steps", ChartKind.BARS, HcDataType.STEPS, true),
    DISTANCE("Distance", ChartKind.BARS, HcDataType.DISTANCE, true),
    FLOORS("Floors climbed", ChartKind.BARS, HcDataType.FLOORS, true),
    EXERCISE("Exercise minutes", ChartKind.BARS, HcDataType.EXERCISE, true),
    ACTIVE_KCAL("Active calories", ChartKind.BARS, null, true),
    EXPENDITURE("Calories burned (est.)", ChartKind.BARS, null),
    INTAKE("Calories eaten", ChartKind.BARS, null),
    PROTEIN("Protein eaten", ChartKind.BARS, null),
    CARBS("Carbs eaten", ChartKind.BARS, null),
    FAT("Fat eaten", ChartKind.BARS, null),
    DEFICIT("Calorie deficit (est.)", ChartKind.BALANCE, null, true),
    HEART_RATE("Heart rate (daily avg)", ChartKind.LINE, HcDataType.HEART_RATE),
    RESTING_HR("Resting heart rate", ChartKind.LINE, HcDataType.RESTING_HEART_RATE, false),
    HRV("Heart-rate variability", ChartKind.LINE, HcDataType.HRV, true),
    SLEEP("Sleep", ChartKind.BARS, HcDataType.SLEEP, true),
    SPO2("Oxygen saturation", ChartKind.LINE, HcDataType.OXYGEN_SATURATION),
    RESP_RATE("Respiratory rate", ChartKind.LINE, HcDataType.RESPIRATORY_RATE),
    WEIGHT("Weight", ChartKind.LINE, null, false),
    WORKOUTS("Workouts per week", ChartKind.BARS, null, true);

    fun unit(s: AppSettings): String = when (this) {
        STEPS -> "steps"; DISTANCE -> if (s.lengthUnit == LengthUnit.CM) "km" else "mi"; FLOORS -> "floors"; EXERCISE -> "min"
        PROTEIN, CARBS, FAT -> "g"
        ACTIVE_KCAL, EXPENDITURE, INTAKE, DEFICIT -> "kcal"; HEART_RATE, RESTING_HR -> "bpm"; HRV -> "ms"; SLEEP -> "h"
        SPO2 -> "%"; RESP_RATE -> "br/min"; WEIGHT -> if (s.weightUnit.name == "KG") "kg" else "lb"; WORKOUTS -> "sessions"
    }

    fun format(v: Double): String = when (this) {
        DISTANCE, SLEEP, HRV, RESP_RATE, WEIGHT -> com.forma.app.util.Fmt.one(v)
        SPO2 -> com.forma.app.util.Fmt.one(v)
        else -> com.forma.app.util.Fmt.int(v)
    }

    val explanation: String get() = when (this) {
        STEPS -> "Steps from Health Connect. Health Connect merges overlapping sources (e.g. phone + band) using your app priority, so steps aren't counted twice."
        DISTANCE -> "Distance travelled, from Health Connect."
        FLOORS -> "Floors climbed, from Health Connect."
        EXERCISE -> "Minutes in exercise sessions recorded in Health Connect."
        ACTIVE_KCAL -> "Calories above resting energy, as reported by your wearable (imported active calories, or measured total minus resting). Estimates, not exact measurements."
        EXPENDITURE -> "FORMA's estimate of total energy burned — measured by your wearable when available, otherwise estimated from your profile."
        INTAKE -> "Calories you logged. Days without food logged are left out."
        PROTEIN -> "Protein you logged, against your daily target."
        CARBS -> "Carbs you logged. Your target is whatever your calorie budget has left after protein and fat (unless you fixed it), so it changes with your budget. Entries without carb info count as zero."
        FAT -> "Fat you logged. Your target is a share of the day's calorie budget (30% by default). Entries without fat info count as zero."
        DEFICIT -> "Estimated expenditure minus calories eaten, for days with food logged. Positive = deficit."
        HEART_RATE -> "Average of all heart-rate readings that day."
        RESTING_HR -> "Resting heart rate as reported by your device. A gradual decline usually reflects improving fitness."
        HRV -> "RMSSD heart-rate variability, when your device writes it to Health Connect. Compare against your own trend, not other people."
        SLEEP -> "Time asleep (awake periods excluded when your device records sleep stages)."
        SPO2 -> "Blood-oxygen readings, when available. Not a medical measurement."
        RESP_RATE -> "Breaths per minute, usually measured during sleep."
        WEIGHT -> "Weigh-ins (manual and Health Connect)."
        WORKOUTS -> "Completed FORMA workouts plus watch workouts that didn't overlap one."
    }
}

object MetricValues {
    private const val M_PER_MI = 1609.344

    /** The day's value, or null when there is no real data (never 0 as a placeholder). */
    fun value(m: Metric, model: EnergyModel, d: LocalDate, settings: AppSettings): Double? {
        val e: DailyEnergyEntity? = model.s.energy[d.toEpochDay()]
        return when (m) {
            Metric.STEPS -> e?.steps?.toDouble()
            Metric.DISTANCE -> e?.distanceM?.let { if (settings.lengthUnit == LengthUnit.CM) it / 1000 else it / M_PER_MI }
            Metric.FLOORS -> e?.floors
            Metric.EXERCISE -> e?.exerciseMinutes
            Metric.ACTIVE_KCAL -> e?.hcActiveKcal ?: model.summary(d).expenditure.takeIf { it.source == ExpenditureSource.HEALTH_CONNECT_TOTAL }?.let {
                if (it.dayComplete) it.activeKcal else (it.burnedSoFarKcal - (e?.hcBasalKcal ?: it.bmrKcal * model.s.elapsedFractionToday)).coerceAtLeast(0.0)
            }
            Metric.EXPENDITURE -> if (d.isAfter(model.s.today)) null else model.summary(d).expenditure.let { if (it.dayComplete) it.projectedDayKcal else it.burnedSoFarKcal }
            Metric.INTAKE -> if (model.hasFood(d)) model.consumed(d).kcal else null
            Metric.PROTEIN -> if (model.hasFood(d)) model.consumed(d).proteinG else null
            Metric.CARBS -> if (model.hasFood(d)) model.consumed(d).carbsG ?: 0.0 else null
            Metric.FAT -> if (model.hasFood(d)) model.consumed(d).fatG ?: 0.0 else null
            Metric.DEFICIT -> if (!model.hasFood(d)) null else model.summary(d).let { if (d == model.s.today) it.actualDeficitSoFar.toDouble() else it.projectedDeficit.toDouble() }
            Metric.HEART_RATE -> e?.avgHr
            Metric.RESTING_HR -> e?.restingHr
            Metric.HRV -> e?.hrvMs
            Metric.SLEEP -> e?.sleepMinutes?.let { it / 60.0 }
            Metric.SPO2 -> e?.spo2Pct
            Metric.RESP_RATE -> e?.respRate
            Metric.WEIGHT -> model.s.weights.filter { it.epochDay == d.toEpochDay() }.takeIf { it.isNotEmpty() }?.map { it.weightLb }?.average()
                ?.let { if (settings.weightUnit.name == "KG") it * 0.45359237 else it }
            Metric.WORKOUTS -> null
        }
    }

    /** Workouts completed in the week starting [weekStart] (FORMA sessions + non-overlapping watch sessions). */
    fun workoutsInWeek(model: EnergyModel, weekStart: LocalDate): Int {
        val days = weekStart.toEpochDay()..weekStart.plusDays(6).toEpochDay()
        val forma = model.s.workouts.filter { it.completedAt != null && it.epochDay in days }
        val watch = model.s.sessions.filter { it.source == "HEALTH_CONNECT" && it.epochDay in days }.filter { hc ->
            forma.none { f -> f.epochDay == hc.epochDay && overlaps(f.startedAt, f.completedAt, hc.startTime, hc.endTime) }
        }
        return forma.size + watch.size
    }

    fun overlaps(aS: Long?, aE: Long?, bS: Long?, bE: Long?): Boolean {
        if (aS == null || aE == null || bS == null || bE == null) return false
        return aS < bE && bS < aE
    }

    fun series(m: Metric, model: EnergyModel, days: Int, settings: AppSettings): List<Pair<LocalDate, Double?>> =
        (days - 1 downTo 0).map { model.s.today.minusDays(it.toLong()) }.map { it to value(m, model, it, settings) }

    fun weekly(m: Metric, model: EnergyModel, weeks: Int, settings: AppSettings): List<Pair<LocalDate, Double?>> {
        val thisWeek = TrendAnalyzer.weekStart(model.s.today)
        return (weeks - 1 downTo 0).map { thisWeek.minusWeeks(it.toLong()) }.map { ws ->
            if (m == Metric.WORKOUTS) ws to workoutsInWeek(model, ws).toDouble()
            else ws to (0..6).map { ws.plusDays(it.toLong()) }.filter { !it.isAfter(model.s.today) }
                .mapNotNull { value(m, model, it, settings) }.takeIf { it.isNotEmpty() }?.average()
        }
    }
}
