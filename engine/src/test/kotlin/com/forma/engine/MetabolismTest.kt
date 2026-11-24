package com.forma.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MetabolismTest {
    private val profile = EnergyProfile(Sex.MALE, 22, 75.0, 260.0)

    @Test fun `mifflin st jeor matches spec for 22yo male 6ft3 260lb`() {
        // 10*117.934 + 6.25*190.5 - 110 + 5 = 2264.9
        assertEquals(2265.0, Metabolism.bmr(profile), 1.0)
    }

    @Test fun `female formula subtracts 161`() {
        val m = Metabolism.bmr(Sex.MALE, 150.0, 65.0, 30)
        val f = Metabolism.bmr(Sex.FEMALE, 150.0, 65.0, 30)
        assertEquals(166.0, m - f, 0.001)
    }

    @Test fun `initial budget is maintenance minus deficit`() {
        val maintenance = Metabolism.maintenanceEstimate(profile)
        assertEquals(2265 * 1.375, maintenance, 2.0)
        assertEquals(Math.round(maintenance - 500).toInt(), Metabolism.initialBudget(profile))
    }

    @Test fun `initial budget respects minimum`() {
        val p = profile.copy(desiredDeficitKcal = 1500)
        assertEquals(2000, Metabolism.initialBudget(p))
    }
}
