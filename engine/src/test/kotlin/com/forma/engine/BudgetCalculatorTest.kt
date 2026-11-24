package com.forma.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetCalculatorTest {
    private val p = EnergyProfile(Sex.MALE, 22, 75.0, 260.0)

    @Test fun `dynamic budget is expenditure minus deficit`() {
        val b = BudgetCalculator.compute(p, 2900.0, 0)
        assertEquals(2400, b.finalBudget)
        assertEquals(500, b.plannedDeficit)
        assertFalse(b.heldAtMinimum)
    }

    @Test fun `higher expenditure raises the budget`() {
        assertEquals(2700, BudgetCalculator.compute(p, 3200.0, 0).finalBudget)
    }

    @Test fun `budget is held at minimum with a warning instead of going below`() {
        val b = BudgetCalculator.compute(p, 2300.0, 0)
        assertEquals(2000, b.finalBudget)
        assertTrue(b.heldAtMinimum)
        assertTrue(b.warnings.isNotEmpty())
    }

    @Test fun `carryover repayment reduces the budget`() {
        val b = BudgetCalculator.compute(p, 2800.0, 200)
        assertEquals(2300, b.baseBudget)
        assertEquals(2100, b.finalBudget)
        assertEquals(200, b.repaymentApplied)
    }

    @Test fun `repayment is trimmed so the budget never drops below minimum`() {
        val b = BudgetCalculator.compute(p, 2600.0, 200) // base 2100 → only 100 headroom
        assertEquals(100, b.repaymentApplied)
        assertEquals(2000, b.finalBudget)
    }

    @Test fun `fixed mode uses the chosen target`() {
        val fixed = p.copy(budgetMode = BudgetMode.FIXED, fixedBudgetKcal = 2300)
        val b = BudgetCalculator.compute(fixed, 3100.0, 0)
        assertEquals(2300, b.finalBudget)
        assertEquals(800, b.plannedDeficit)
    }

    @Test fun `aggressive deficit triggers a caution`() {
        val b = BudgetCalculator.compute(p.copy(desiredDeficitKcal = 1200), 3400.0, 0)
        assertTrue(b.warnings.any { it.contains("aggressive") })
    }

    @Test fun `remaining and actual deficit are distinct quantities`() {
        val day = LocalDate.of(2026, 11, 22)
        // Spec example: burned 2900, consumed 2100 → deficit 800.
        val s = DaySummary.compute(p, DayEnergyInputs(day, 1.0, hcTotalKcal = 2900.0), Macros(2100.0, 150.0), 0)
        assertEquals(2400, s.budget.finalBudget)
        assertEquals(300, s.remainingKcal)
        assertEquals(800, s.actualDeficitSoFar)
        assertEquals(30, s.proteinRemainingG)
    }

    @Test fun `going over budget shows negative remaining but can still be a deficit`() {
        val day = LocalDate.of(2026, 11, 22)
        val s = DaySummary.compute(p, DayEnergyInputs(day, 1.0, hcTotalKcal = 2900.0), Macros(2600.0, 150.0), 0)
        assertEquals(-200, s.remainingKcal)
        assertEquals(200, s.overBudgetKcal)
        assertEquals(300, s.actualDeficitSoFar)
    }
}
