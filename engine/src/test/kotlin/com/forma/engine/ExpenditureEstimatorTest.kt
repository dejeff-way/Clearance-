package com.forma.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpenditureEstimatorTest {
    private val p = EnergyProfile(Sex.MALE, 22, 75.0, 260.0)
    private val bmr = Metabolism.bmr(p)
    private val day = LocalDate.of(2026, 11, 22)

    private fun session(
        id: String, src: SessionSource, start: Long?, minutes: Double, kcal: Double? = null,
        byDevice: Boolean = true, met: Double = 5.0,
    ) = ActivitySession(id, src, id, start, start?.plus((minutes * 60_000).toLong()), minutes, kcal, met, byDevice)

    @Test fun `uses health connect total for a complete day and adds nothing that is already included`() {
        val input = DayEnergyInputs(
            day, 1.0, hcTotalKcal = 3100.0, hcActiveKcal = 700.0, steps = 12000,
            sessions = listOf(
                session("program", SessionSource.PROGRAM, 1_000_000, 50.0),
                session("hc", SessionSource.HEALTH_CONNECT, 1_000_000, 50.0),
            ),
        )
        val e = ExpenditureEstimator.estimate(p, input)
        assertEquals(ExpenditureSource.HEALTH_CONNECT_TOTAL, e.source)
        assertEquals(3100.0, e.projectedDayKcal, 0.01)
        assertEquals(3100.0, e.burnedSoFarKcal, 0.01)
    }

    @Test fun `adds only sessions explicitly not recorded by a device on top of a total`() {
        val untracked = session("pool", SessionSource.MANUAL, null, 60.0, kcal = 500.0, byDevice = false)
        val e = ExpenditureEstimator.estimate(p, DayEnergyInputs(day, 1.0, hcTotalKcal = 3000.0, sessions = listOf(untracked)))
        // Net = 500 − resting share for one hour (baseline/24)
        val expectedNet = 500.0 - Metabolism.baseline(p) / 24.0
        assertEquals(3000.0 + expectedNet, e.projectedDayKcal, 0.5)
    }

    @Test fun `rejects implausibly low total and falls back`() {
        val e = ExpenditureEstimator.estimate(p, DayEnergyInputs(day, 1.0, hcTotalKcal = 400.0, hcActiveKcal = 450.0))
        assertTrue(e.healthConnectTotalRejected)
        assertEquals(ExpenditureSource.HEALTH_CONNECT_ACTIVE, e.source)
        assertEquals(bmr + 450.0, e.projectedDayKcal, 0.5)
    }

    @Test fun `partial day total is projected forward at resting rate`() {
        val e = ExpenditureEstimator.estimate(p, DayEnergyInputs(day, 0.5, hcTotalKcal = 1600.0))
        assertEquals(ExpenditureSource.HEALTH_CONNECT_TOTAL, e.source)
        assertEquals(1600.0, e.burnedSoFarKcal, 0.01)
        assertEquals(1600.0 + 0.5 * bmr * ExpenditureEstimator.REMAINING_DAY_FACTOR, e.projectedDayKcal, 0.5)
        assertFalse(e.dayComplete)
    }

    @Test fun `estimate mode with nothing logged equals baseline`() {
        val e = ExpenditureEstimator.estimate(p, DayEnergyInputs(day, 1.0))
        assertEquals(ExpenditureSource.ESTIMATED, e.source)
        assertEquals(bmr * 1.2, e.projectedDayKcal, 0.5)
    }

    @Test fun `estimate mode uses the larger of steps and sessions, never both`() {
        val s = session("walk", SessionSource.MANUAL, null, 30.0, met = 4.0, byDevice = false)
        val onlySessions = ExpenditureEstimator.estimate(p, DayEnergyInputs(day, 1.0, sessions = listOf(s)))
        val onlySteps = ExpenditureEstimator.estimate(p, DayEnergyInputs(day, 1.0, steps = 15000))
        val both = ExpenditureEstimator.estimate(p, DayEnergyInputs(day, 1.0, steps = 15000, sessions = listOf(s)))
        val maxOfTwo = maxOf(onlySessions.projectedDayKcal, onlySteps.projectedDayKcal)
        assertEquals(maxOfTwo, both.projectedDayKcal, 0.01)
        assertTrue(both.projectedDayKcal < onlySessions.projectedDayKcal + onlySteps.projectedDayKcal - bmr * 1.2)
    }

    @Test fun `steps below baseline add nothing`() {
        val e = ExpenditureEstimator.estimate(p, DayEnergyInputs(day, 1.0, steps = 4000))
        assertEquals(bmr * 1.2, e.projectedDayKcal, 0.5)
    }

    @Test fun `device-recorded program workout overlapping a health connect session is counted once`() {
        val hc = session("hc", SessionSource.HEALTH_CONNECT, 10_000_000, 50.0, met = 5.0)
        val prog = session("prog", SessionSource.PROGRAM, 10_600_000, 45.0, met = 5.0)
        val one = ExpenditureEstimator.estimate(p, DayEnergyInputs(day, 1.0, sessions = listOf(hc)))
        val both = ExpenditureEstimator.estimate(p, DayEnergyInputs(day, 1.0, sessions = listOf(hc, prog)))
        assertEquals(one.projectedDayKcal, both.projectedDayKcal, 0.01)
    }

    @Test fun `non overlapping sessions are both counted in estimate mode`() {
        val a = session("a", SessionSource.HEALTH_CONNECT, 0, 30.0)
        val b = session("b", SessionSource.PROGRAM, 10_000_000, 30.0)
        val e = ExpenditureEstimator.estimate(p, DayEnergyInputs(day, 1.0, sessions = listOf(a, b)))
        val single = ExpenditureEstimator.estimate(p, DayEnergyInputs(day, 1.0, sessions = listOf(a)))
        assertTrue(e.projectedDayKcal > single.projectedDayKcal)
    }

    @Test fun `net session kcal removes the resting share and is never negative`() {
        val weightKg = Metabolism.lbToKg(260.0)
        val baseline = Metabolism.baseline(p)
        val tiny = session("x", SessionSource.MANUAL, null, 60.0, kcal = 20.0)
        assertEquals(0.0, ExpenditureEstimator.netSessionKcal(tiny, baseline, 1.0, weightKg), 0.0)
    }

    @Test fun `active mode adds bmr and active`() {
        val e = ExpenditureEstimator.estimate(p, DayEnergyInputs(day, 1.0, hcActiveKcal = 600.0, steps = 9000))
        assertEquals(ExpenditureSource.HEALTH_CONNECT_ACTIVE, e.source)
        assertEquals(bmr + 600, e.projectedDayKcal, 0.5)
    }
}
