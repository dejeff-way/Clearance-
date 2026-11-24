package com.forma.engine

import kotlin.math.roundToInt

data class BudgetResult(
    /** Budget before the minimum safeguard and before carryover. */
    val rawBudget: Int,
    /** Budget after the minimum safeguard, before carryover repayment. */
    val baseBudget: Int,
    val heldAtMinimum: Boolean,
    val repaymentScheduled: Int,
    val repaymentApplied: Int,
    /** What the user can eat today. */
    val finalBudget: Int,
    /** The deficit the user asked for. */
    val plannedDeficit: Int,
    /** Expenditure − final budget: the deficit implied if the user eats exactly the budget. */
    val impliedDeficit: Int,
    val warnings: List<String>,
    /** Activity tried to raise the budget beyond the configured limit, so the budget was capped. */
    val cappedByActivityLimit: Boolean = false,
    /** Budget if activity weren't counted (sedentary baseline − deficit); the reference for the cap. */
    val sedentaryBudget: Int? = null,
) {}

object BudgetCalculator {
    /** Deficits above this get a caution message. */
    const val LARGE_DEFICIT_KCAL = 1000
    const val LARGE_DEFICIT_FRACTION = 0.25

    /** Budget before carryover, after the minimum safeguard. Used by the carryover ledger. */
    fun baseBudget(p: EnergyProfile, projectedExpenditure: Double): Int =
        maxOf(rawBudget(p, projectedExpenditure), p.minimumBudgetKcal)

    fun rawBudget(p: EnergyProfile, projectedExpenditure: Double): Int = when (p.budgetMode) {
        BudgetMode.DYNAMIC -> {
            val dynamic = (projectedExpenditure - p.desiredDeficitKcal).roundToInt()
            activityCap(p)?.let { minOf(dynamic, it) } ?: dynamic
        }
        BudgetMode.FIXED -> p.fixedBudgetKcal ?: (projectedExpenditure - p.desiredDeficitKcal).roundToInt()
    }

    /** Sedentary-baseline budget, i.e. what the budget would be with no measured activity at all. */
    fun sedentaryBudget(p: EnergyProfile): Int = (Metabolism.baseline(p) - p.desiredDeficitKcal).roundToInt()

    /** Highest budget activity may produce in DYNAMIC mode, or null when uncapped. */
    fun activityCap(p: EnergyProfile): Int? = p.maxActivityBudgetIncreaseKcal?.takeIf { it > 0 }?.let { sedentaryBudget(p) + it }

    fun compute(p: EnergyProfile, projectedExpenditure: Double, repaymentScheduled: Int): BudgetResult {
        val warnings = mutableListOf<String>()
        val raw = rawBudget(p, projectedExpenditure)
        val uncapped = (projectedExpenditure - p.desiredDeficitKcal).roundToInt()
        val capped = p.budgetMode == BudgetMode.DYNAMIC && raw < uncapped
        if (capped) {
            warnings += "Your activity would add ${uncapped - sedentaryBudget(p)} kcal to today's budget. FORMA limits the increase to " +
                "${p.maxActivityBudgetIncreaseKcal} kcal because wearable calorie estimates often run high. The extra activity still counts toward your actual deficit."
        }
        val held = raw < p.minimumBudgetKcal
        val base = maxOf(raw, p.minimumBudgetKcal)
        if (held) {
            warnings += "The calculated budget ($raw kcal) is below your ${p.minimumBudgetKcal} kcal minimum, so " +
                "FORMA is holding it at the minimum. Your real deficit today will be smaller than planned."
        }
        val headroom = (base - p.minimumBudgetKcal).coerceAtLeast(0)
        val applied = repaymentScheduled.coerceIn(0, headroom)
        if (applied < repaymentScheduled) {
            warnings += "Carryover repayment reduced from $repaymentScheduled to $applied kcal to stay at or above " +
                "your minimum budget. The rest carries forward."
        }
        val final = base - applied
        val deficitReq = when (p.budgetMode) {
            BudgetMode.DYNAMIC -> p.desiredDeficitKcal
            BudgetMode.FIXED -> (projectedExpenditure - (p.fixedBudgetKcal ?: base)).roundToInt()
        }
        if (p.budgetMode == BudgetMode.DYNAMIC &&
            (p.desiredDeficitKcal > LARGE_DEFICIT_KCAL ||
                p.desiredDeficitKcal > projectedExpenditure * LARGE_DEFICIT_FRACTION)
        ) {
            warnings += "A ${p.desiredDeficitKcal} kcal daily deficit is aggressive. Larger deficits make it harder " +
                "to keep muscle and train well; around 500 kcal is a sustainable default."
        }
        return BudgetResult(
            rawBudget = raw,
            baseBudget = base,
            heldAtMinimum = held,
            repaymentScheduled = repaymentScheduled,
            repaymentApplied = applied,
            finalBudget = final,
            plannedDeficit = deficitReq,
            impliedDeficit = (projectedExpenditure - final).roundToInt(),
            warnings = warnings,
            cappedByActivityLimit = capped,
            sedentaryBudget = sedentaryBudget(p),
        )
    }
}

/** Everything the dashboard shows for one day. */
data class DaySummary(
    val expenditure: Expenditure,
    val budget: BudgetResult,
    val consumed: Macros,
    val proteinTargetG: Int,
) {
    /** Budget − consumed. Can be negative (over budget). This is NOT a deficit. */
    val remainingKcal: Int get() = budget.finalBudget - consumed.kcal.roundToInt()

    /** Estimated burned so far − consumed. Positive = deficit, negative = surplus. */
    val actualDeficitSoFar: Int get() = (expenditure.burnedSoFarKcal - consumed.kcal).roundToInt()

    /** Projected full-day expenditure − consumed, if nothing more is eaten. */
    val projectedDeficit: Int get() = (expenditure.projectedDayKcal - consumed.kcal).roundToInt()

    val proteinRemainingG: Int get() = (proteinTargetG - consumed.proteinG).roundToInt().coerceAtLeast(0)

    /** Within today's budget (calories remaining ≥ 0). */
    val onTrack: Boolean get() = remainingKcal >= 0

    /** Over budget by this many kcal (0 when within budget). */
    val overBudgetKcal: Int get() = (-remainingKcal).coerceAtLeast(0)

    companion object {
        fun compute(p: EnergyProfile, input: DayEnergyInputs, consumed: Macros, repaymentScheduled: Int): DaySummary {
            val exp = ExpenditureEstimator.estimate(p, input)
            val budget = BudgetCalculator.compute(p, exp.projectedDayKcal, repaymentScheduled)
            return DaySummary(exp, budget, consumed, p.proteinTargetG)
        }
    }
}
