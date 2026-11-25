package com.forma.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.repo.EnergyModel
import com.forma.app.data.seed.Category
import com.forma.app.data.seed.KosherStatus
import com.forma.app.data.seed.MilkOption
import com.forma.app.data.seed.RecipeSeed
import com.forma.app.data.seed.WorkoutProgram
import com.forma.app.data.seed.ExerciseLibrary
import com.forma.app.di.AppContainer
import com.forma.engine.Portion
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(application = TestFormaApp::class, sdk = [35])
class DataLayerTest {
    private lateinit var c: AppContainer
    private val today = LocalDate.of(2026, 11, 24)

    @Before fun setUp() = runBlocking {
        TestEnv.time = FixedTime(); TestEnv.health = FakeHealth()
        c = TestEnv.container(ApplicationProvider.getApplicationContext())
        c.initialize()
    }

    @After fun tearDown() { c.db.close() }

    /** Waits for the live model to reflect a condition (Room flows are asynchronous). */
    private fun model(predicate: (EnergyModel) -> Boolean = { true }): EnergyModel = runBlocking {
        withTimeout(5000) {
            var m: EnergyModel
            while (true) {
                m = c.energyModel.filterNotNull().first()
                if (predicate(m)) break
                kotlinx.coroutines.delay(50)
            }
            m
        }
    }

    // ── Recipes ─────────────────────────────────────────────
    @Test fun `seeds 17 recipes in four collections with no desserts`() = runBlocking {
        val all = c.db.recipes().all()
        assertEquals(17, all.size)
        assertEquals(setOf("BREAKFAST", "LUNCH", "SUPPER", "SNACKS"), all.map { it.category }.toSet())
        assertEquals(3, all.count { it.category == "BREAKFAST" })
        assertEquals(5, all.count { it.category == "LUNCH" })
        assertEquals(5, all.count { it.category == "SUPPER" })
        assertEquals(4, all.count { it.category == "SNACKS" })
        assertTrue(all.filter { it.name.contains("Creami") }.all { it.category == "SNACKS" })
        all.forEach { r ->
            assertTrue("${r.id} needs prep steps", r.prepSteps.isNotEmpty())
            assertTrue("${r.id} needs cook steps", r.cookSteps.isNotEmpty())
            assertTrue("${r.id} needs kosher notes", r.kosherNotes.isNotEmpty())
            assertTrue("${r.id} needs ingredients", c.db.recipes().ingredients(r.id).isNotEmpty())
            assertNotNull(KosherStatus.from(r.kosherStatus))
        }
    }

    @Test fun `meat recipes never contain dairy ingredients`() {
        val dairyWords = listOf("milk", "yogurt", "cheese", "butter", "cream", "whey", "protein powder", "tnuva", "parmesan", "sour")
        RecipeSeed.all.filter { it.kosher == KosherStatus.MEAT }.forEach { r ->
            r.ingredients.forEach { i ->
                assertFalse("${r.id}: ${i.name} looks dairy", dairyWords.any { i.name.lowercase().contains(it) })
                assertFalse("${r.id}: milk ingredient in meat recipe", i.isMilk)
            }
        }
    }

    @Test fun `raw fish recipes carry supplier and sushi grade warnings`() {
        RecipeSeed.all.filter { it.id.contains("poke") }.forEach { r ->
            assertTrue(r.isFish)
            assertTrue(r.kosherNotes.any { it.contains("kosher fish supplier") })
            assertTrue(r.kosherNotes.any { it.contains("Sushi-grade", ignoreCase = true) })
            assertTrue(r.safety.any { it.contains("40 °F") })
        }
    }

    @Test fun `large portion uses ingredient based scaling`() = runBlocking {
        val f = c.recipes.full("chicken_shawarma_bowl")!!
        val std = f.macros(Portion.STANDARD); val large = f.macros(Portion.LARGE)
        assertTrue(large.kcal > std.kcal)
        // Not a blind multiplier: oil, spices and lemon stay constant, so the ratio is below the chicken ratio.
        assertTrue(large.kcal / std.kcal < 230.0 / 170.0)
        assertEquals(std.kcal * 2, f.macros(Portion.STANDARD, 2.0).kcal, 0.01)
    }

    @Test fun `creami nutrition follows the chosen milk`() = runBlocking {
        val f = c.recipes.full("chocolate_tnuva_creami")!!
        val skim = f.macros(Portion.STANDARD, 1.0, MilkOption.SKIM)
        val whole = f.macros(Portion.STANDARD, 1.0, MilkOption.WHOLE)
        assertEquals(120 + 68.0, skim.kcal, 0.5)      // powder + 200 ml skim
        assertEquals(120 + 122.0, whole.kcal, 0.5)    // powder + 200 ml whole
        c.recipes.setMilk("chocolate_tnuva_creami", MilkOption.ALMOND)
        assertEquals(MilkOption.ALMOND, c.recipes.full("chocolate_tnuva_creami")!!.milk)
    }

    @Test fun `favorites persist`() = runBlocking {
        c.recipes.setFavorite("salmon_poke_bowl", true)
        assertTrue(c.db.recipes().get("salmon_poke_bowl")!!.isFavorite)
        c.recipes.seed() // re-seeding must preserve user state
        assertTrue(c.db.recipes().get("salmon_poke_bowl")!!.isFavorite)
    }

    // ── Food logging ────────────────────────────────────────
    @Test fun `logging editing and deleting food updates the dashboard summary`() = runBlocking {
        val before = model().today
        val id = c.food.logRecipe("protein_egg_scramble", Category.BREAKFAST.name, Portion.STANDARD, 1.0, today.toEpochDay(), TestEnv.time.nowMillis())
        val std = c.recipes.full("protein_egg_scramble")!!.macros(Portion.STANDARD)
        val after = model { it.today.consumed.kcal > 0 }.today
        assertEquals(std.kcal, after.consumed.kcal, 0.01)
        assertTrue(kotlin.math.abs(before.remainingKcal - std.kcal.toInt() - after.remainingKcal) <= 1)

        c.food.changePortion(id, Portion.LARGE, 1.0)
        val large = c.recipes.full("protein_egg_scramble")!!.macros(Portion.LARGE)
        assertEquals(large.kcal, model { kotlin.math.abs(it.today.consumed.kcal - large.kcal) < 0.01 }.today.consumed.kcal, 0.01)

        c.food.delete(id)
        assertEquals(0.0, model { it.today.consumed.kcal == 0.0 }.today.consumed.kcal, 0.0)
    }

    @Test fun `manual entry needs only calories`() = runBlocking {
        c.food.logManual("Coffee", Category.SNACKS.name, 120.0, null, null, null, today.toEpochDay(), TestEnv.time.nowMillis())
        val e = c.db.food().all().single()
        assertEquals(120.0, e.kcal, 0.0); assertEquals(0.0, e.proteinG, 0.0); assertNull(e.carbsG); assertNull(e.fatG)
        val s = model { it.today.consumed.kcal == 120.0 }.today
        assertEquals(120.0, s.consumed.kcal, 0.0)
    }

    // ── Calorie engine integration ─────────────────────────
    @Test fun `initial budget uses BMR estimate when no health data`() {
        val m = model()
        assertEquals(2265.0, m.today.expenditure.bmrKcal, 1.0)
        assertEquals(com.forma.engine.ExpenditureSource.ESTIMATED, m.today.expenditure.source)
        // 2265 × 1.2 − 500 ≈ 2218
        assertTrue(kotlin.math.abs(2218 - m.today.budget.finalBudget) <= 2)
    }

    @Test fun `health connect total drives the budget and a manual untracked session adds on top`() = runBlocking {
        c.db.energy().upsert(com.forma.app.data.db.DailyEnergyEntity(today.minusDays(1).toEpochDay(), hcTotalKcal = 3200.0))
        val y = model { it.s.energy.isNotEmpty() }.summary(today.minusDays(1))
        assertEquals(2700, y.budget.finalBudget)
        c.activity.addManual(today.minusDays(1).toEpochDay(), "Swim", 60.0, 600.0, 7.0, recordedByDevice = false, startTime = null, note = null)
        val y2 = model { it.s.sessions.isNotEmpty() }.summary(today.minusDays(1))
        assertTrue(y2.budget.finalBudget > 2700)
        c.activity.addManual(today.minusDays(1).toEpochDay(), "Watch walk", 30.0, 200.0, 3.8, recordedByDevice = true, startTime = null, note = null)
        val y3 = model { it.s.sessions.size == 2 }.summary(today.minusDays(1))
        assertEquals(y2.budget.finalBudget, y3.budget.finalBudget) // device-recorded: not double counted
    }

    @Test fun `completed program workout raises estimated expenditure only without measured data`() = runBlocking {
        val base = model().today.expenditure.projectedDayKcal
        val sid = c.workouts.ensureSession(today, "TUE")
        WorkoutProgram.byKey("TUE")!!.slots.forEach { c.workouts.setCompleted(sid, it.key, true) }
        val after = model { it.s.workouts.any { w -> w.completedAt != null } }.today.expenditure.projectedDayKcal
        assertTrue(after > base)
        c.db.energy().upsert(com.forma.app.data.db.DailyEnergyEntity(today.toEpochDay(), hcTotalKcal = 2400.0))
        val measured = model { it.s.energy.isNotEmpty() }.today
        assertEquals(com.forma.engine.ExpenditureSource.HEALTH_CONNECT_TOTAL, measured.expenditure.source)
        assertTrue(measured.expenditure.lines.any { it.label.startsWith("FORMA") && it.kind == com.forma.engine.LineKind.EXCLUDED })
    }

    // ── Going over budget ──────────────────────────────────
    @Test fun `going over yesterday doesn't change today's budget`() = runBlocking {
        val normal = model().today.budget.finalBudget
        c.food.logManual("Big dinner", Category.SUPPER.name, 4500.0, 60.0, null, null, today.minusDays(1).toEpochDay(), TestEnv.time.nowMillis() - 18 * 3_600_000)
        val m = model { it.hasFood(today.minusDays(1)) }
        assertEquals(normal, m.today.budget.finalBudget)
        assertTrue(m.summary(today.minusDays(1)).projectedDeficit < 0)   // the surplus shows in that day's actual deficit
    }

    // ── Weight ─────────────────────────────────────────────
    @Test fun `weight log updates current weight and BMR`() = runBlocking {
        c.body.logWeight(250.0)
        val m = model { it.s.profile.currentWeightLb == 250.0 }
        assertEquals(2265.0 - 10 * 10 * 0.45359237, m.today.expenditure.bmrKcal, 1.0)
        c.body.addMeasurement(44.5, today.toEpochDay(), "morning")
        assertEquals(44.5, c.db.body().allMeasurements().single().waistIn, 0.0)
    }

    // ── Workouts ───────────────────────────────────────────
    @Test fun `workout completion, set logging and remembered substitutions`() = runBlocking {
        val sid = c.workouts.ensureSession(today, "MON")
        c.workouts.logSet(sid, "MON_1", 0, 180.0, 12, null)
        c.workouts.substitute(sid, "MON_1", "goblet_squat", remember = true)
        val logs = c.db.workouts().exerciseLogsFor(sid)
        assertEquals("goblet_squat", logs.first { it.slotKey == "MON_1" }.exerciseKey)
        assertTrue("sets for the old exercise are cleared", c.db.workouts().allSetLogs().isEmpty())
        WorkoutProgram.byKey("MON")!!.slots.forEach { c.workouts.setCompleted(sid, it.key, true) }
        assertNotNull(c.db.workouts().sessionById(sid)!!.completedAt)
        c.workouts.setCompleted(sid, "MON_2", false)
        assertNull(c.db.workouts().sessionById(sid)!!.completedAt)
        // Next week the remembered substitute is pre-applied
        val next = c.workouts.ensureSession(today.plusDays(7), "MON")
        assertEquals("goblet_squat", c.db.workouts().exerciseLogsFor(next).first { it.slotKey == "MON_1" }.exerciseKey)
    }

    @Test fun `substitutions stay within the same movement pattern`() {
        WorkoutProgram.days.flatMap { it.slots }.forEach { slot ->
            val ex = ExerciseLibrary.get(slot.exerciseKey)
            val subs = ExerciseLibrary.substitutesFor(ex.key)
            assertTrue("${ex.key} should have substitutes", subs.isNotEmpty())
            subs.forEach { assertEquals(ex.pattern, it.pattern) }
        }
        assertTrue(ExerciseLibrary.substitutesFor("leg_press").any { it.key == "goblet_squat" })
        assertTrue(ExerciseLibrary.substitutesFor("seated_leg_curl").any { it.key == "lying_leg_curl" })
        assertTrue(ExerciseLibrary.substitutesFor("db_biceps_curl").map { it.key }.containsAll(listOf("hammer_curl", "cable_curl")))
        assertTrue(ExerciseLibrary.substitutesFor("lat_pulldown").any { it.key == "assisted_pullup" })
    }

    @Test fun `program schedule matches the plan`() {
        assertTrue(WorkoutProgram.forDay(java.time.DayOfWeek.FRIDAY).rest)
        assertTrue(WorkoutProgram.forDay(java.time.DayOfWeek.FRIDAY).slots.isEmpty())
        assertTrue(WorkoutProgram.forDay(java.time.DayOfWeek.SATURDAY).optional)
        assertEquals(5, WorkoutProgram.requiredPerWeek)
        WorkoutProgram.days.filter { !it.rest }.forEach { d ->
            d.slots.forEach { s -> assertTrue(s.restSeconds >= 0); assertNotNull(ExerciseLibrary.byKey[s.exerciseKey]) }
        }
    }

    // ── Persistence ────────────────────────────────────────
    @Test fun `data survives closing and reopening the database`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("forma.db")
        val c1 = TestEnv.container(ctx, inMemory = false)
        c1.initialize()
        c1.food.logManual("Persisted", Category.LUNCH.name, 500.0, 40.0, null, null, today.toEpochDay(), TestEnv.time.nowMillis())
        c1.body.logWeight(258.4)
        c1.recipes.setFavorite("tuna_poke_bowl", true)
        c1.db.close()

        val db2 = FormaDatabase.create(ctx)
        assertEquals("Persisted", db2.food().all().single().name)
        assertEquals(258.4, db2.body().allWeights().single().weightLb, 0.0)
        assertEquals(258.4, db2.profile().get()!!.currentWeightLb, 0.0)
        assertTrue(db2.recipes().get("tuna_poke_bowl")!!.isFavorite)
        db2.close()
        ctx.deleteDatabase("forma.db")
        Unit
    }

    @Test fun `backup round trip restores everything`() = runBlocking {
        c.food.logManual("A", Category.LUNCH.name, 400.0, 30.0, null, null, today.toEpochDay(), TestEnv.time.nowMillis())
        c.body.logWeight(259.0)
        val json = c.backup.encode(c.backup.snapshot(TestEnv.time.nowMillis()))
        c.backup.resetAll()
        assertTrue(c.db.food().all().isEmpty())
        c.backup.restore(c.backup.decode(json))
        assertEquals("A", c.db.food().all().single().name)
        assertEquals(259.0, c.db.body().allWeights().single().weightLb, 0.0)
    }
}
