package com.forma.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CarryoverLedgerTest {
    private val d0 = LocalDate.of(2026, 11, 20)
    private fun ledger(items: List<CarryoverItem>, today: LocalDate, base: (LocalDate) -> Int? = { 2300 }) =
        CarryoverLedger.compute(items, 200, 2000, today, base)

    @Test fun `overage of 300 repays 200 then 100`() {
        val items = listOf(CarryoverItem(1, d0, 300))
        val sameDay = ledger(items, d0)
        assertEquals(0, sameDay.scheduledToday)            // repayment starts tomorrow
        assertEquals(300, sameDay.balanceAfterToday)
        assertEquals(d0.plusDays(2), sameDay.projectedClearDate)

        val day1 = ledger(items, d0.plusDays(1))
        assertEquals(300, day1.balanceStartOfToday)
        assertEquals(200, day1.scheduledToday)
        assertEquals(100, day1.balanceAfterToday)

        val day2 = ledger(items, d0.plusDays(2))
        assertEquals(100, day2.scheduledToday)
        assertEquals(0, day2.balanceAfterToday)
        assertEquals(d0.plusDays(2), day2.clearedOn)

        val day3 = ledger(items, d0.plusDays(3))
        assertEquals(0, day3.scheduledToday)
        assertNull(day3.projectedClearDate)
    }

    @Test fun `repayment never pushes a past day below the minimum and the remainder carries forward`() {
        val items = listOf(CarryoverItem(1, d0, 300))
        // Day 1 base budget is at the minimum → nothing repaid that day.
        val s = ledger(items, d0.plusDays(2)) { if (it == d0.plusDays(1)) 2000 else 2300 }
        assertEquals(0, s.history.first().repaid)
        assertEquals(300, s.balanceStartOfToday)
        assertEquals(200, s.scheduledToday)
        assertEquals(100, s.balanceAfterToday)
    }

    @Test fun `partial headroom repays partially`() {
        val items = listOf(CarryoverItem(1, d0, 300))
        val s = ledger(items, d0.plusDays(2)) { if (it == d0.plusDays(1)) 2050 else 2300 }
        assertEquals(50, s.history.first().repaid)
        assertEquals(250, s.balanceStartOfToday)
    }

    @Test fun `multiple overages accumulate but daily repayment stays capped`() {
        val items = listOf(CarryoverItem(1, d0, 300), CarryoverItem(2, d0.plusDays(1), 400))
        val s = ledger(items, d0.plusDays(2))
        // Day1 repays 200 of 300 → 100 left; day2 owes 100 + 400 = 500, repays 200.
        assertEquals(500, s.balanceStartOfToday)
        assertEquals(200, s.scheduledToday)
        assertEquals(300, s.balanceAfterToday)
        assertEquals(d0.plusDays(4), s.projectedClearDate)
    }

    @Test fun `cancelled or zero entries are ignored`() {
        val s = ledger(listOf(CarryoverItem(1, d0, 0)), d0.plusDays(1))
        assertEquals(0, s.scheduledToday)
        assertEquals(0, s.balanceAfterToday)
    }

    @Test fun `editing an overage downward recomputes history`() {
        val before = ledger(listOf(CarryoverItem(1, d0, 600)), d0.plusDays(2))
        assertEquals(400, before.balanceStartOfToday)
        val after = ledger(listOf(CarryoverItem(1, d0, 250)), d0.plusDays(2))
        assertEquals(50, after.balanceStartOfToday)
        assertEquals(50, after.scheduledToday)
        assertEquals(0, after.balanceAfterToday)
    }

    @Test fun `ledger and budget calculator agree for today`() {
        val p = EnergyProfile(Sex.MALE, 22, 75.0, 260.0)
        val s = ledger(listOf(CarryoverItem(1, d0, 300)), d0.plusDays(1))
        val b = BudgetCalculator.compute(p, 2800.0, s.scheduledToday)
        assertEquals(2100, b.finalBudget) // spec example: 2,300 − 200 = 2,100
    }
}
