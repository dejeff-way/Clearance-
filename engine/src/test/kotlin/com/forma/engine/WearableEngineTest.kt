package com.forma.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WearableEngineTest {
    private val p = EnergyProfile(Sex.MALE, 22, 75.0, 260.0)
    private val bmr = Metabolism.bmr(p)
    private val day = LocalDate.of(2026, 11, 22)

    @Test fun `separate baseline, active and total values add up`() {
        val e = ExpenditureEstimator.estimate(p, DayEnergyInputs(day, 1.0, hcTotalKcal = 3100.0, hcBasalKcal = 2200.0))
        assertEquals(3100.0, e.projectedDayKcal, 0.01)
        assertEquals(2200.0, e.baselineKcal, 0.01)
        assertEquals(900.0, e.activeKcal, 0.01)
        val est = ExpenditureEstimator.estimate(p, DayEnergyInputs(day, 1.0, steps = 15000))
        assertEquals(bmr * 1.2, est.baselineKcal, 0.5)
        assertEquals(est.projectedDayKcal - est.baselineKcal, est.activeKcal, 0.01)
    }

    @Test fun `measured basal replaces the BMR formula when only active calories exist`() {
        val e = ExpenditureEstimator.estimate(p, DayEnergyInputs(day, 1.0, hcActiveKcal = 600.0, hcBasalKcal = 2150.0))
        assertEquals(ExpenditureSource.HEALTH_CONNECT_ACTIVE, e.source)
        assertEquals(2750.0, e.projectedDayKcal, 0.01)
        assertEquals(600.0, e.activeKcal, 0.01)
    }

    @Test fun `active calories are never added on top of a total`() {
        val e = ExpenditureEstimator.estimate(p, DayEnergyInputs(day, 1.0, hcTotalKcal = 3000.0, hcActiveKcal = 800.0))
        assertEquals(3000.0, e.projectedDayKcal, 0.01)
    }

    @Test fun `conservative adjustment reduces only the activity part`() {
        val q = p.copy(wearableAdjustmentPct = -10)
        val total = ExpenditureEstimator.estimate(q, DayEnergyInputs(day, 1.0, hcTotalKcal = 3200.0, hcBasalKcal = 2200.0))
        assertEquals(3200.0 - 100.0, total.projectedDayKcal, 0.01) // 10% of 1,000 kcal activity
        assertEquals(-100.0, total.wearableAdjustmentKcal, 0.01)
        val active = ExpenditureEstimator.estimate(q, DayEnergyInputs(day, 1.0, hcActiveKcal = 500.0))
        assertEquals(bmr + 450.0, active.projectedDayKcal, 0.5)
        // Estimates (no wearable) are untouched
        assertEquals(bmr * 1.2, ExpenditureEstimator.estimate(q, DayEnergyInputs(day, 1.0)).projectedDayKcal, 0.5)
    }

    @Test fun `activity can raise the budget only up to the cap`() {
        val sedentary = BudgetCalculator.sedentaryBudget(p) // 2265×1.2 − 500 ≈ 2218
        val big = BudgetCalculator.compute(p, 4200.0)
        assertTrue(big.cappedByActivityLimit)
        assertEquals(sedentary + 500, big.finalBudget)
        assertTrue(big.warnings.any { it.contains("limits the increase") })
        val modest = BudgetCalculator.compute(p, 2900.0)
        assertFalse(modest.cappedByActivityLimit); assertEquals(2400, modest.finalBudget)
        val uncapped = BudgetCalculator.compute(p.copy(maxActivityBudgetIncreaseKcal = null), 4200.0)
        assertEquals(3700, uncapped.finalBudget)
        // The cap never lowers a budget below the minimum
        assertEquals(2000, BudgetCalculator.compute(p, 2100.0).finalBudget)
    }

    @Test fun `actual deficit uses full expenditure even when the budget is capped`() {
        val s = DaySummary.compute(p, DayEnergyInputs(day, 1.0, hcTotalKcal = 4200.0), Macros(2500.0, 180.0))
        assertEquals(1700, s.actualDeficitSoFar)
        assertTrue(s.budget.cappedByActivityLimit)
        assertTrue(s.onTrack)
    }
}
