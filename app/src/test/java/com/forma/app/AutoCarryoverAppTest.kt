package com.forma.app

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.repo.EnergyModel
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestFormaApp::class, sdk = [35], qualifiers = "w400dp-h2400dp-xxhdpi")
class AutoCarryoverAppTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val c get() = (rule.activity.application as FormaApp).container
    private val today get() = TestEnv.time.today()

    @Before fun ready() {
        runBlocking { c.initialize() }
        rule.waitUntil(8000) { rule.onAllNodesWithTagExists("remaining_kcal") }
    }

    private fun model(until: (EnergyModel) -> Boolean): EnergyModel = runBlocking {
        withTimeout(5000) { while (true) { val m = c.energyModel.value; if (m != null && until(m)) return@withTimeout m; delay(50) }; error("") }
    }

    private fun overeat(day: java.time.LocalDate, overBy: Int) {
        val base = model { true }.baseBudget(day)
        runBlocking { c.food.logManual("Big dinner", "SUPPER", (base + overBy).toDouble(), 60.0, null, null, day.toEpochDay(), c.time.startOfDayMillis(day) + 19 * 3_600_000L) }
    }

    @Test fun `going over yesterday lowers today automatically with nothing recorded`() {
        val y = today.minusDays(1)
        runBlocking { c.profile.update { it.copy(autoCarryoverFromEpochDay = today.minusDays(3).toEpochDay()) } }
        overeat(y, 300)
        val m = model { it.hasFood(y) && it.carryover.autoItems.isNotEmpty() }
        assertEquals(300, m.carryover.autoItems.single().kcal)
        assertEquals(200, m.today.budget.repaymentApplied)
        assertEquals(100, m.carryover.balanceAfterToday)
        assertTrue(runBlocking { c.db.carryover().all() }.isEmpty())
        // Home: no carryover card, no weight-trend card, just a line inside the budget card
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("carry_line") }
        assertTrue(rule.onAllNodes(hasText("Weight trend", substring = true)).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodes(hasText("Manage")).fetchSemanticsNodes().isEmpty())
        rule.onRoot().captureRoboImage("screenshots/30_home_auto_carryover.png")
    }

    @Test fun `days before automatic carryover started are ignored`() {
        overeat(today.minusDays(2), 400)   // autoFrom = today (set at first launch)
        val m = model { it.hasFood(today.minusDays(2)) }
        assertTrue(m.carryover.autoItems.isEmpty())
        assertEquals(0, m.today.budget.repaymentApplied)
    }

    @Test fun `today going over only shows a hint until the day ends`() {
        overeat(today, 250)
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("auto_carry_hint") }
        val m = model { it.hasFood(today) }
        assertTrue(m.carryover.autoItems.isEmpty())
        assertEquals(0, m.carryover.balanceAfterToday)
    }

    @Test fun `unlogged estimate goes into the food log and is not counted twice`() {
        runBlocking { c.profile.update { it.copy(autoCarryoverFromEpochDay = today.minusDays(3).toEpochDay()) } }
        val y = today.minusDays(1)
        overeat(y, 0)
        rule.onNodeWithTag("tab_settings").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("settings") }
        rule.onNode(hasText("Calorie carryover", substring = false), useUnmergedTree = true).performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("record_unlogged") }
        assertTrue(rule.onAllNodesWithTagExists("auto_note"))
        assertTrue(!rule.onAllNodesWithTagExists("record_overage"))
        rule.onNodeWithTag("record_unlogged").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("co_amount") }
        rule.onNodeWithTag("co_amount").performTextInput("250")
        rule.onNodeWithTag("co_save").performClick()
        val m = model { it.carryover.autoItems.isNotEmpty() }
        assertTrue(runBlocking { c.db.carryover().all() }.isEmpty())
        assertEquals(250, m.carryover.autoItems.single().kcal)
        assertEquals(200, m.today.budget.repaymentApplied)
    }

    @Test fun `turning automatic carryover off stops it`() {
        runBlocking { c.profile.update { it.copy(autoCarryoverFromEpochDay = today.minusDays(3).toEpochDay()) } }
        overeat(today.minusDays(1), 300)
        model { it.today.budget.repaymentApplied == 200 }
        runBlocking { c.profile.update { it.copy(autoCarryover = false) } }
        model { it.today.budget.repaymentApplied == 0 && it.autoCarryoverFrom == null }
    }

    @Test fun `database migrates from version 7`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("migrate8.db")
        val schema = Json.parseToJsonElement(File("schemas/com.forma.app.data.db.FormaDatabase/7.json").readText()).jsonObject["database"]!!.jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath("migrate8.db").apply { parentFile?.mkdirs() }, null)
        schema["entities"]!!.jsonArray.forEach { e ->
            val o = e.jsonObject; val t = o["tableName"]!!.jsonPrimitive.content
            sql.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t))
            o["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t)) }
        }
        schema["setupQueries"]!!.jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }
        sql.execSQL("INSERT INTO profile (id, displayName, sex, ageYears, heightInches, startWeightLb, currentWeightLb, mainGoal, secondaryGoal, skiDateEpochDay, gym, trainingDays, workoutMinutes, baselineActivityFactor, maintenanceActivityFactor, desiredDeficitKcal, minimumBudgetKcal, maxRepaymentPerDay, budgetMode, proteinTargetG, programStartEpochDay, updatedAt, stepGoal, wearableAdjustmentPct, maxActivityBonusKcal, fatPctOfBudget) VALUES (1,'','MALE',22,75,260,258,'a','b',20819,'LA Fitness','SUNDAY',55,1.2,1.375,500,2000,200,'DYNAMIC',185,20000,1,12000,0,500,30)")
        sql.version = 7; sql.close()
        val db = androidx.room.Room.databaseBuilder(ctx, FormaDatabase::class.java, "migrate8.db")
            .addMigrations(FormaDatabase.MIGRATION_1_2, FormaDatabase.MIGRATION_2_3, FormaDatabase.MIGRATION_3_4, FormaDatabase.MIGRATION_4_5,
                FormaDatabase.MIGRATION_5_6, FormaDatabase.MIGRATION_6_7, FormaDatabase.MIGRATION_7_8).build()
        runBlocking {
            val p = db.profile().get()!!
            assertEquals(185, p.proteinTargetG); assertTrue(p.autoCarryover); assertNull(p.autoCarryoverFromEpochDay)
        }
        db.close(); ctx.deleteDatabase("migrate8.db")
    }
}
