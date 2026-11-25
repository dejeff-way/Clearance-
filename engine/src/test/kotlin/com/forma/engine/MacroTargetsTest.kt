package com.forma.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroTargetsTest {
    @Test fun `fat is a share of the budget and carbs fill the rest`() {
        val t = MacroCalculator.targets(2400, 180, 30, null, null, Macros(0.0, 0.0, 0.0, 0.0))
        assertEquals(80, t.fat.targetG)            // 2400 × 30% / 9
        assertEquals(240, t.carbs.targetG)         // (2400 − 720 − 720) / 4
        assertEquals(180, t.protein.targetG)
    }

    @Test fun `targets follow the budget`() {
        val small = MacroCalculator.targets(2000, 180, 30, null, null, Macros.ZERO)
        val big = MacroCalculator.targets(2800, 180, 30, null, null, Macros.ZERO)
        assertTrue(big.carbs.targetG > small.carbs.targetG && big.fat.targetG > small.fat.targetG)
    }

    @Test fun `fixed grams override and fat has a floor`() {
        val fixed = MacroCalculator.targets(2400, 180, 30, 70, 200, Macros.ZERO)
        assertEquals(70, fixed.fat.targetG); assertEquals(200, fixed.carbs.targetG)
        val low = MacroCalculator.targets(1400, 180, 20, null, null, Macros.ZERO)
        assertEquals(MacroCalculator.MIN_FAT_G, low.fat.targetG)
        assertTrue(low.notes.isNotEmpty())
    }

    @Test fun `status to go, on target and over`() {
        val t = MacroCalculator.targets(2400, 180, 30, null, null, Macros(1500.0, 100.0, 230.0, 95.0))
        assertEquals(MacroStatus.TO_GO, t.protein.status); assertEquals(80, t.protein.remainingG)
        assertEquals(MacroStatus.ON_TARGET, t.carbs.status)
        assertEquals(MacroStatus.OVER, t.fat.status); assertEquals(-15, t.fat.remainingG)
    }
}
