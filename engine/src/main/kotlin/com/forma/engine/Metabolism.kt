package com.forma.engine

import kotlin.math.roundToInt

object Metabolism {
    const val KG_PER_LB = 0.45359237
    const val CM_PER_IN = 2.54

    /** Energy content assumed per pound of body-weight change. A rough convention, used only for trend math. */
    const val KCAL_PER_LB = 3500.0

    fun lbToKg(lb: Double) = lb * KG_PER_LB
    fun inToCm(inches: Double) = inches * CM_PER_IN

    /**
     * Mifflin–St Jeor resting energy expenditure:
     *   men:   10·kg + 6.25·cm − 5·age + 5
     *   women: 10·kg + 6.25·cm − 5·age − 161
     */
    fun bmr(sex: Sex, weightLb: Double, heightIn: Double, ageYears: Int): Double {
        val base = 10.0 * lbToKg(weightLb) + 6.25 * inToCm(heightIn) - 5.0 * ageYears
        return base + if (sex == Sex.MALE) 5.0 else -161.0
    }

    fun bmr(p: EnergyProfile): Double = bmr(p.sex, p.weightLb, p.heightInches, p.ageYears)

    /** Non-exercise baseline used when no measured expenditure exists. */
    fun baseline(p: EnergyProfile): Double = bmr(p) * p.baselineActivityFactor

    /** Initial maintenance estimate including typical training, shown as an estimate during onboarding. */
    fun maintenanceEstimate(p: EnergyProfile): Double = bmr(p) * p.maintenanceActivityFactor

    /** Starting budget: maintenance estimate minus the desired deficit, never below the minimum. */
    fun initialBudget(p: EnergyProfile): Int =
        maxOf(p.minimumBudgetKcal, (maintenanceEstimate(p) - p.desiredDeficitKcal).roundToInt())
}
