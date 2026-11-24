package com.forma.engine

import java.time.LocalDate

enum class Sex { MALE, FEMALE }

/** How the daily calorie budget is derived. */
enum class BudgetMode {
    /** Budget = estimated expenditure for the day − desired deficit. Recalculates as activity data arrives. */
    DYNAMIC,

    /** Budget = a fixed number chosen by the user (still subject to the minimum safeguard and carryover). */
    FIXED,
}

/**
 * Everything the engine needs to know about the person. Imperial units are used at the boundary
 * because that is what the app stores; conversion to metric happens inside [Metabolism].
 */
data class EnergyProfile(
    val sex: Sex,
    val ageYears: Int,
    val heightInches: Double,
    val weightLb: Double,
    /** Multiplier applied to BMR for a non-exercise day when no measured expenditure exists. 1.2 = sedentary. */
    val baselineActivityFactor: Double = 1.2,
    /** Multiplier used only to present an initial "maintenance" estimate including typical training. */
    val maintenanceActivityFactor: Double = 1.375,
    val desiredDeficitKcal: Int = 500,
    val minimumBudgetKcal: Int = 2000,
    val maxCarryoverRepaymentPerDay: Int = 200,
    val budgetMode: BudgetMode = BudgetMode.DYNAMIC,
    val fixedBudgetKcal: Int? = null,
    val proteinTargetG: Int = 180,
    /** Conservative adjustment to wearable-measured activity calories, in percent (e.g. -10). 0 = use as reported. */
    val wearableAdjustmentPct: Int = 0,
    /**
     * In DYNAMIC mode, activity may raise the budget at most this many kcal above the sedentary-baseline budget, so a
     * high wearable estimate can't inflate the food allowance. Null = no cap.
     */
    val maxActivityBudgetIncreaseKcal: Int? = 500,
)

enum class SessionSource {
    /** Read from Health Connect ExerciseSessionRecord. */
    HEALTH_CONNECT,

    /** Entered by hand in FORMA. */
    MANUAL,

    /** A FORMA program workout marked complete. */
    PROGRAM,
}

/**
 * A bout of exercise. [kcal] is the gross energy reported for the session (e.g. a treadmill display) when known;
 * otherwise the engine estimates from [met] and duration.
 */
data class ActivitySession(
    val id: String,
    val source: SessionSource,
    val title: String,
    val startEpochMs: Long? = null,
    val endEpochMs: Long? = null,
    val durationMinutes: Double,
    val kcal: Double? = null,
    val met: Double = 4.0,
    /**
     * True when a watch/phone that writes to Health Connect also recorded this session, so its energy is already
     * part of Health Connect's totals. HEALTH_CONNECT sessions are always true.
     */
    val recordedByDevice: Boolean,
)

/** Raw inputs for one calendar day. */
data class DayEnergyInputs(
    val date: LocalDate,
    /** Fraction of the day that has elapsed: 1.0 for any completed day, 0..1 for today. */
    val elapsedFraction: Double,
    val hcTotalKcal: Double? = null,
    val hcActiveKcal: Double? = null,
    /** Health Connect basal (resting) calories for the elapsed part of the day, when a device writes them. */
    val hcBasalKcal: Double? = null,
    val steps: Long? = null,
    val sessions: List<ActivitySession> = emptyList(),
)

data class Macros(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double? = null,
    val fatG: Double? = null,
) {
    operator fun plus(o: Macros) = Macros(
        kcal + o.kcal,
        proteinG + o.proteinG,
        sumNullable(carbsG, o.carbsG),
        sumNullable(fatG, o.fatG),
    )

    operator fun times(f: Double) = Macros(kcal * f, proteinG * f, carbsG?.times(f), fatG?.times(f))

    companion object {
        val ZERO = Macros(0.0, 0.0, 0.0, 0.0)
        private fun sumNullable(a: Double?, b: Double?): Double? =
            if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)
    }
}
