package com.forma.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SleepScoreTest {
    @Test fun `a full night with healthy stages scores near 100`() {
        val r = SleepScore.score(SleepNight(450.0, deepMin = 80.0, lightMin = 250.0, remMin = 120.0, awakeMin = 20.0))!!
        assertEquals(50, r.duration); assertEquals(25, r.quality); assertEquals(25, r.restoration)
        assertEquals(100, r.score); assertEquals("Excellent", r.label)
    }

    @Test fun `short restless night scores low`() {
        val r = SleepScore.score(SleepNight(300.0, deepMin = 20.0, lightMin = 240.0, remMin = 40.0, awakeMin = 80.0))!!
        assertEquals(25, r.duration)               // 5 h is halfway between 3 h and 7 h
        assertEquals(15, r.quality)                // deep 6.7 % of a 13 % norm → 6.4, REM 13.3 % of 20 % → 8.3
        assertEquals(17, r.restoration)            // efficiency 300 / 380 = 79 % (norm 85 %)
        assertEquals(57, r.score); assertEquals("Poor", r.label)
    }

    @Test fun `no stage data means no score rather than a guess`() {
        assertNull(SleepScore.score(SleepNight(420.0)))
    }

    @Test fun `efficiency at the 85 percent clinical cut-off gets full restoration`() {
        val r = SleepScore.score(SleepNight(425.0, 60.0, 280.0, 85.0, 75.0))!!   // 425 / 500 = 85 %
        assertEquals(25, r.restoration); assertEquals(25, r.quality)
    }
}

class StrengthRecordsTest {
    private val d0 = LocalDate.of(2026, 10, 4)   // a Sunday
    private fun s(day: Long, ex: String, w: Double? = null, r: Int? = null, sec: Int? = null) = LoggedSet(d0.plusDays(day), ex, w, r, sec)

    @Test fun `no PRs during the first four weeks`() {
        val sets = listOf(s(0, "face_pull", 35.0, 10), s(14, "face_pull", 40.0, 12))
        val r = StrengthRecords.forWeek(sets, d0.plusDays(14))
        assertFalse(r.unlocked); assertEquals(d0.plusDays(28), r.unlocksOn)
    }

    @Test fun `heavier and more reps is a PR after the baseline`() {
        val sets = listOf(s(0, "face_pull", 35.0, 10), s(7, "face_pull", 35.0, 10), s(28, "face_pull", 40.0, 12))
        val r = StrengthRecords.forWeek(sets, d0.plusDays(28))
        assertTrue(r.unlocked)
        val pr = r.records.single()
        assertEquals(40.0, pr.best.weightLb); assertEquals(12, pr.best.reps); assertEquals(35.0, pr.previous.weightLb)
    }

    @Test fun `estimated strength decides trade-offs between weight and reps`() {
        // 40 × 8 ≈ 50.7, 35 × 10 ≈ 46.7 → PR.  37.5 × 8 ≈ 47.5 vs 47.5 × 1? only strictly better counts
        assertTrue(StrengthRecords.estimatedMax(40.0, 8) > StrengthRecords.estimatedMax(35.0, 10))
        val sets = listOf(s(0, "row", 100.0, 12), s(28, "row", 115.0, 8))   // 145.7 vs 140 → PR
        assertEquals(1, StrengthRecords.forWeek(sets, d0.plusDays(28)).records.size)
        val worse = listOf(s(0, "row", 100.0, 12), s(28, "row", 110.0, 8))   // 139.3 < 140: heavier, but fewer reps than it takes
        assertTrue(StrengthRecords.forWeek(worse, d0.plusDays(28)).records.isEmpty())
    }

    @Test fun `matching your best is not a PR, holds and bodyweight reps count`() {
        val sets = listOf(s(0, "curl", 30.0, 10), s(28, "curl", 30.0, 10),
            s(1, "plank", sec = 30), s(29, "plank", sec = 40), s(2, "dead_bug", r = 8), s(30, "dead_bug", r = 10))
        val r = StrengthRecords.forWeek(sets, d0.plusDays(28)).records
        assertEquals(setOf("plank", "dead_bug"), r.map { it.exerciseKey }.toSet())
        assertEquals(RecordMetric.HOLD, r.first { it.exerciseKey == "plank" }.metric)
    }

    @Test fun `a brand new exercise has nothing to beat`() {
        val sets = listOf(s(0, "curl", 30.0, 10), s(28, "new_lift", 50.0, 10))
        assertTrue(StrengthRecords.forWeek(sets, d0.plusDays(28)).records.isEmpty())
    }
}

class WeeklyStatsTest {
    private val sun = LocalDate.of(2026, 11, 15)

    @Test fun `totals, averages and fat estimate only count days with data`() {
        val days = listOf(
            WeekDayData(sun, 2000.0, 600, 9000, SleepNight(450.0, 80.0, 250.0, 120.0, 20.0)),
            WeekDayData(sun.plusDays(1), 2400.0, 300, 12500, SleepNight(360.0)),
            WeekDayData(sun.plusDays(2), null, null, 4000, null),
            WeekDayData(sun.plusDays(6), 1900.0, 850, null, null),
            WeekDayData(sun.plusDays(7), 5000.0, -1000, 50000, null),   // next week: ignored
        )
        val w = WeeklyStats.compute(sun, days, stepGoal = 10000)
        assertEquals(3, w.daysLogged); assertEquals(6300, w.kcalTotal); assertEquals(2100, w.kcalAvg)
        assertEquals(1750, w.deficitTotal)
        assertEquals(25500L, w.stepsTotal); assertEquals(8500L, w.stepsAvg); assertEquals(1, w.stepGoalDays)
        assertEquals(sun.plusDays(1) to 12500L, w.stepsBest)
        assertEquals(2, w.sleepNights); assertEquals(sun, w.bestNight!!.date)
        assertNull(w.worstNight)                     // the second night had no stages, so only one night was scored
        assertEquals(100, w.sleepScoreAvg)
        assertEquals(405, w.sleepAvgMin)
    }

    @Test fun `an empty week has no averages`() {
        val w = WeeklyStats.compute(sun, emptyList(), 10000)
        assertNull(w.kcalAvg); assertNull(w.sleepScoreAvg); assertEquals(0, w.daysLogged)
    }
}

class WeeklyStatsNightsTest {
    @Test fun `no toughest night when every night scored the same`() {
        val sun = LocalDate.of(2026, 11, 15)
        val n = SleepNight(450.0, 80.0, 250.0, 120.0, 20.0)
        val w = WeeklyStats.compute(sun, (0L..2L).map { WeekDayData(sun.plusDays(it), sleep = n) }, 10000)
        assertNull(w.worstNight); assertEquals(100, w.bestNight!!.score)
    }
}
