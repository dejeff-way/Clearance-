package com.forma.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Injectable clock so tests can control "today". */
open class TimeProvider {
    open fun zone(): ZoneId = ZoneId.systemDefault()
    open fun nowMillis(): Long = System.currentTimeMillis()
    fun now(): ZonedDateTime = Instant.ofEpochMilli(nowMillis()).atZone(zone())
    fun today(): LocalDate = now().toLocalDate()

    /** Fraction of today elapsed, 0..1. */
    fun elapsedFractionToday(): Double {
        val n = now()
        val start = n.toLocalDate().atStartOfDay(zone())
        val end = start.plusDays(1)
        val total = (end.toInstant().toEpochMilli() - start.toInstant().toEpochMilli()).toDouble()
        return ((n.toInstant().toEpochMilli() - start.toInstant().toEpochMilli()) / total).coerceIn(0.0, 1.0)
    }

    fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()
    fun startOfDayMillis(d: LocalDate): Long = d.atStartOfDay(zone()).toInstant().toEpochMilli()
}
