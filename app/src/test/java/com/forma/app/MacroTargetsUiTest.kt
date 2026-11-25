package com.forma.app

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.data.db.FormaDatabase
import com.forma.engine.MacroStatus
import com.forma.engine.Portion
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestFormaApp::class, sdk = [35], qualifiers = "w400dp-h2400dp-xxhdpi")
class MacroTargetsUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val c get() = (rule.activity.application as FormaApp).container
    private fun text(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    @Before fun ready() {
        runBlocking { c.initialize() }
        rule.waitUntil(8000) { rule.onAllNodesWithTagExists("remaining_kcal") }
    }

    @Test fun `home shows protein carbs and fat against budget-based targets`() {
        val today = TestEnv.time.today().toEpochDay()
        runBlocking {
            c.food.logRecipe("protein_overnight_oats", "BREAKFAST", Portion.STANDARD, 1.0, today, TestEnv.time.nowMillis() - 5 * 3_600_000)
            c.food.logRecipe("chicken_fajita_bowl", "LUNCH", Portion.STANDARD, 1.0, today, TestEnv.time.nowMillis())
        }
        val m = runBlocking { kotlinx.coroutines.withTimeout(5000) { while (true) { val x = c.energyModel.value; if (x != null && x.hasFood(TestEnv.time.today()) && x.today.consumed.kcal > 900) return@withTimeout x; kotlinx.coroutines.delay(50) }; error("") } }
        val t = m.macroTargets(m.s.today)
        // Targets come from today's budget: fat 30% of budget, carbs = the rest after protein and fat
        assertEquals((m.today.budget.finalBudget * 0.30 / 9).roundToInt(), t.fat.targetG)
        assertEquals(((m.today.budget.finalBudget - 180 * 4 - t.fat.targetG * 9) / 4.0).roundToInt(), t.carbs.targetG)
        rule.waitUntil(5000) { text("macro_carbs_value") == "${m.today.consumed.carbsG!!.roundToInt()}" }
        assertEquals("${m.today.consumed.fatG!!.roundToInt()}", text("macro_fat_value"))
        assertTrue(text("macro_carbs_status").endsWith("g to go"))
        rule.onRoot().captureRoboImage("screenshots/29_macros_light.png")

        // A quick manual entry without carbs/fat is flagged so the totals aren't trusted blindly
        runBlocking { c.food.logManual("Snack", "SNACKS", 300.0, 10.0, null, null, today, TestEnv.time.nowMillis()) }
        rule.waitUntil(5000) { rule.onAllNodes(androidx.compose.ui.test.hasText("no carb or fat info", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

        // Fixed grams override and "over" status
        runBlocking { c.profile.update { it.copy(carbTargetG = 60) } }   // logged oats + fajita bowl ≈ 100 g carbs
        rule.waitUntil(5000) { text("macro_carbs_status").endsWith("g over") }
    }

    @Test fun `bigger budget raises carb and fat targets`() {
        val m = c.energyModel.value!!
        val small = com.forma.engine.MacroCalculator.targets(2100, 180, 30, null, null, com.forma.engine.Macros.ZERO)
        val big = com.forma.engine.MacroCalculator.targets(2700, 180, 30, null, null, com.forma.engine.Macros.ZERO)
        assertTrue(big.carbs.targetG - small.carbs.targetG > 50)
        assertEquals(MacroStatus.TO_GO, m.macroTargets(m.s.today).protein.status)
    }

    @Test fun `database migrates from version 6`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("migrate7.db")
        val schema = Json.parseToJsonElement(File("schemas/com.forma.app.data.db.FormaDatabase/6.json").readText()).jsonObject["database"]!!.jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath("migrate7.db").apply { parentFile?.mkdirs() }, null)
        schema["entities"]!!.jsonArray.forEach { e ->
            val o = e.jsonObject; val t = o["tableName"]!!.jsonPrimitive.content
            sql.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t))
            o["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t)) }
        }
        schema["setupQueries"]!!.jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }
        sql.execSQL("INSERT INTO profile (id, displayName, sex, ageYears, heightInches, startWeightLb, currentWeightLb, mainGoal, secondaryGoal, skiDateEpochDay, gym, trainingDays, workoutMinutes, baselineActivityFactor, maintenanceActivityFactor, desiredDeficitKcal, minimumBudgetKcal, maxRepaymentPerDay, budgetMode, proteinTargetG, programStartEpochDay, updatedAt, stepGoal, wearableAdjustmentPct, maxActivityBonusKcal) VALUES (1,'','MALE',22,75,260,258,'a','b',20819,'LA Fitness','SUNDAY',55,1.2,1.375,500,2000,200,'DYNAMIC',185,20000,1,12000,0,500)")
        sql.version = 6; sql.close()
        val db = androidx.room.Room.databaseBuilder(ctx, FormaDatabase::class.java, "migrate7.db")
            .addMigrations(FormaDatabase.MIGRATION_1_2, FormaDatabase.MIGRATION_2_3, FormaDatabase.MIGRATION_3_4, FormaDatabase.MIGRATION_4_5,
                FormaDatabase.MIGRATION_5_6, FormaDatabase.MIGRATION_6_7).build()
        runBlocking {
            val p = db.profile().get()!!
            assertEquals(185, p.proteinTargetG); assertEquals(12000, p.stepGoal)
            assertEquals(30, p.fatPctOfBudget); assertNull(p.fatTargetG); assertNull(p.carbTargetG)
        }
        db.close(); ctx.deleteDatabase("migrate7.db")
    }
}
