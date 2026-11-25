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

class AutoCarryoverTest {
    private val d0 = LocalDate.of(2026, 11, 20)

    /** Simulates logged intake per day against a 2,300 base budget. */
    private fun run(intake: Map<LocalDate, Int>, today: LocalDate, from: LocalDate = d0, manual: List<CarryoverItem> = emptyList()) =
        CarryoverLedger.compute(manual, 200, 2000, today, { 2300 }, from) { d, repaid ->
            intake[d]?.let { it - (2300 - repaid) }?.coerceAtLeast(0)
        }

    @Test fun `logged overage carries to the next day automatically`() {
        val s = run(mapOf(d0 to 2600), d0.plusDays(1))
        assertEquals(300, s.autoItems.single().kcal)
        assertEquals(200, s.scheduledToday)
        assertEquals(100, s.balanceAfterToday)
    }

    @Test fun `today is never counted until it is over`() {
        val s = run(mapOf(d0 to 3000), d0)
        assertEquals(0, s.autoItems.size)
        assertEquals(0, s.balanceAfterToday)
    }

    @Test fun `repayment lowers the next day's budget, and eating within it adds nothing more`() {
        // Day 1 over by 300; day 2 budget 2,100 and eats 2,100 → no new overage; day 3 repays the last 100.
        val s = run(mapOf(d0 to 2600, d0.plusDays(1) to 2100), d0.plusDays(2))
        assertEquals(1, s.autoItems.size)
        assertEquals(100, s.scheduledToday)
        // Eating the normal 2,300 on a repayment day is itself an overage of 200 vs the reduced budget
        val s2 = run(mapOf(d0 to 2600, d0.plusDays(1) to 2300), d0.plusDays(2))
        assertEquals(listOf(300, 200), s2.autoItems.map { it.kcal })
    }

    @Test fun `days before automatic carryover started are ignored`() {
        val s = run(mapOf(d0 to 2900, d0.plusDays(1) to 2500), d0.plusDays(2), from = d0.plusDays(1))
        assertEquals(listOf(200), s.autoItems.map { it.kcal })
    }

    @Test fun `off means only manual entries count`() {
        val s = CarryoverLedger.compute(emptyList(), 200, 2000, d0.plusDays(2), { 2300 }, null) { _, _ -> 999 }
        assertEquals(0, s.balanceAfterToday)
    }
}
