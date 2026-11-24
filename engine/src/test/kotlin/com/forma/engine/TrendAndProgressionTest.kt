package com.forma.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TrendAndProgressionTest {
    private val start = LocalDate.of(2026, 10, 1)

    private fun series(days: Int, startLb: Double, perDay: Double, noise: Boolean = true) = (0 until days).map {
        val n = if (noise) (if (it % 2 == 0) 0.8 else -0.8) else 0.0
        WeightPoint(start.plusDays(it.toLong()), startLb + perDay * it + n)
    }

    @Test fun `rolling average smooths daily noise`() {
        val t = TrendAnalyzer.rolling7(series(14, 260.0, 0.0))
        val last = t.last()
        assertEquals(260.0, last.average7, 0.2)
    }

    @Test fun `multiple weigh-ins on one day are averaged`() {
        val pts = listOf(WeightPoint(start, 260.0), WeightPoint(start, 262.0))
        assertEquals(261.0, TrendAnalyzer.daily(pts).single().lb, 0.001)
    }

    @Test fun `slope is recovered`() {
        val s = TrendAnalyzer.slopePerDay(series(28, 260.0, -0.2, noise = false))!!
        assertEquals(-0.2, s, 1e-9)
    }

    @Test fun `no recommendation before 14 days`() {
        assertNull(TrendAnalyzer.recommend(series(10, 260.0, -0.1), 2300.0, 2800.0, 10, 259.0, start, start.plusDays(10)))
    }

    @Test fun `on track at about one pound per week`() {
        val r = TrendAnalyzer.recommend(series(28, 260.0, -1.0 / 7), 2300.0, 2800.0, 25, 256.0, start, start.plusDays(28))
        assertNotNull(r); assertEquals(RecommendationKind.ON_TRACK, r!!.kind); assertEquals(0, r.deficitChangeKcal)
    }

    @Test fun `fast loss after early phase suggests eating a bit more`() {
        val r = TrendAnalyzer.recommend(series(28, 260.0, -4.0 / 7), 2000.0, 3000.0, 25, 250.0, start.minusDays(30), start.plusDays(28))!!
        assertEquals(RecommendationKind.EAT_MORE, r.kind)
        assertEquals(-200, r.deficitChangeKcal)
    }

    @Test fun `fast loss in first three weeks holds`() {
        val r = TrendAnalyzer.recommend(series(15, 260.0, -4.0 / 7), 2000.0, 3000.0, 15, 252.0, start, start.plusDays(15))!!
        assertEquals(RecommendationKind.HOLD, r.kind)
    }

    @Test fun `stalled loss suggests a modest change`() {
        val r = TrendAnalyzer.recommend(series(28, 260.0, 0.0), 2300.0, 2800.0, 25, 260.0, start, start.plusDays(28))!!
        assertEquals(RecommendationKind.EAT_LESS, r.kind)
        assertEquals(150, r.deficitChangeKcal)
    }

    @Test fun `sparse logging asks for more data`() {
        val r = TrendAnalyzer.recommend(series(28, 260.0, -0.1), 2300.0, 2800.0, 4, 258.0, start, start.plusDays(28))!!
        assertEquals(RecommendationKind.CHECK_LOGGING, r.kind)
    }

    @Test fun `progression increases when all sets hit the top`() {
        val a = Progression.advise(10..12, List(3) { SetResult(90.0, 12) }, 5.0)
        assertEquals(ProgressionAction.INCREASE, a.action); assertEquals(95.0, a.suggestedWeightLb!!, 0.0)
    }

    @Test fun `progression consolidates when sets fall short`() {
        assertEquals(ProgressionAction.CONSOLIDATE, Progression.advise(10..12, listOf(SetResult(90.0, 12), SetResult(90.0, 8)), 5.0).action)
    }

    @Test fun `portion scaling is ingredient based`() {
        val chicken = ScalableIngredient(170.0, 230.0, Macros(281.0, 52.7, 0.0, 6.1))
        val oil = ScalableIngredient(1.0, 1.0, Macros(40.0, 0.0, 0.0, 4.5))
        val sriracha = ScalableIngredient(1.0, 1.0, Macros(5.0, 0.0, 1.0, 0.0), optional = true)
        val std = PortionScaler.total(listOf(chicken, oil, sriracha), Portion.STANDARD)
        val large = PortionScaler.total(listOf(chicken, oil, sriracha), Portion.LARGE)
        assertEquals(321.0, std.kcal, 0.01)
        assertEquals(281.0 * 230 / 170 + 40.0, large.kcal, 0.01) // oil not scaled; optional excluded
        assertEquals(2 * std.kcal, PortionScaler.total(listOf(chicken, oil), Portion.STANDARD, 2.0).kcal, 0.01)
    }
}
