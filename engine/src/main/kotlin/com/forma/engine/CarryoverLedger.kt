package com.forma.engine

import java.time.LocalDate

enum class CarryoverKind {
    /** "I went over my budget by N kcal" — only for food that is NOT already logged. */
    OVERAGE,

    /** "I ate more than I logged" — an estimate of unlogged intake. */
    UNLOGGED_ADJUSTMENT,
}

data class CarryoverItem(
    val id: Long,
    /** The day the overage happened. Repayment starts the following day. */
    val date: LocalDate,
    val kcal: Int,
    val kind: CarryoverKind = CarryoverKind.OVERAGE,
)

data class CarryoverDay(
    val date: LocalDate,
    val outstandingAtStart: Int,
    val scheduled: Int,
    val repaid: Int,
    val limitedByMinimum: Boolean,
    val projected: Boolean,
)

data class CarryoverState(
    val totalRecorded: Int,
    val totalRepaidThroughToday: Int,
    /** Balance still owed at the start of today (before today's repayment). */
    val balanceStartOfToday: Int,
    /** Scheduled repayment for today (before the minimum-budget check, which [BudgetCalculator] applies). */
    val scheduledToday: Int,
    /** Balance remaining after today's repayment. */
    val balanceAfterToday: Int,
    val history: List<CarryoverDay>,
    val projection: List<CarryoverDay>,
    /** Last day of repayment, or null if nothing is owed. */
    val projectedClearDate: LocalDate?,
    /** The day the balance was last fully cleared (in the past/today). */
    val clearedOn: LocalDate?,
)

/**
 * Pure, deterministic carryover ledger. Repayments are not stored; they are recomputed from the recorded
 * overages, the daily cap, and each past day's base budget. Editing or cancelling an overage therefore updates
 * everything consistently.
 *
 * Rules:
 *  - Repayment begins the day after the overage.
 *  - At most [maxPerDay] kcal is repaid per day.
 *  - Repayment never pushes a day's budget below [minimumBudget]; anything that doesn't fit carries forward.
 *  - Carryover is independent of the actual-deficit calculation.
 */
object CarryoverLedger {
    const val PROJECTION_LIMIT_DAYS = 90

    fun compute(
        items: List<CarryoverItem>,
        maxPerDay: Int,
        minimumBudget: Int,
        today: LocalDate,
        baseBudgetFor: (LocalDate) -> Int?,
    ): CarryoverState {
        val valid = items.filter { it.kcal > 0 }.sortedBy { it.date }
        if (valid.isEmpty() || maxPerDay <= 0) {
            val total = valid.sumOf { it.kcal }
            return CarryoverState(total, 0, total, 0, total, emptyList(), emptyList(), null, null)
        }
        val history = mutableListOf<CarryoverDay>()
        var repaid = 0
        var clearedOn: LocalDate? = null
        var scheduledToday = 0
        var balanceStartToday = 0
        var d = valid.first().date.plusDays(1)
        while (!d.isAfter(today)) {
            val owedBefore = valid.filter { it.date.isBefore(d) }.sumOf { it.kcal } - repaid
            if (d == today) balanceStartToday = owedBefore.coerceAtLeast(0)
            if (owedBefore > 0) {
                val scheduled = minOf(maxPerDay, owedBefore)
                if (d == today) scheduledToday = scheduled
                // Never push a day's budget below the minimum. Today's figure mirrors BudgetCalculator, which
                // applies the same rule with live expenditure.
                val headroom = baseBudgetFor(d)?.let { it - minimumBudget } ?: scheduled
                val r = scheduled.coerceAtMost(headroom.coerceAtLeast(0))
                history += CarryoverDay(d, owedBefore, scheduled, r, r < scheduled, projected = false)
                repaid += r
                if (owedBefore - history.last().repaid <= 0) clearedOn = d
            }
            d = d.plusDays(1)
        }
        val total = valid.sumOf { it.kcal }
        // Overages recorded today begin tomorrow; include everything for the forward balance.
        val balanceAfterToday = (total - repaid).coerceAtLeast(0)
        if (balanceAfterToday > 0) clearedOn = null

        val projection = mutableListOf<CarryoverDay>()
        var owed = balanceAfterToday
        var p = today.plusDays(1)
        var n = 0
        while (owed > 0 && n < PROJECTION_LIMIT_DAYS) {
            val r = minOf(maxPerDay, owed)
            projection += CarryoverDay(p, owed, r, r, limitedByMinimum = false, projected = true)
            owed -= r
            p = p.plusDays(1); n++
        }
        val clearDate = when {
            projection.isNotEmpty() -> projection.last().date
            balanceStartToday > 0 && balanceAfterToday == 0 -> today
            else -> null
        }
        return CarryoverState(
            totalRecorded = total,
            totalRepaidThroughToday = repaid,
            balanceStartOfToday = balanceStartToday,
            scheduledToday = scheduledToday,
            balanceAfterToday = balanceAfterToday,
            history = history,
            projection = projection,
            projectedClearDate = clearDate,
            clearedOn = clearedOn,
        )
    }
}
