package com.forma.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.health.HcAvailability
import com.forma.app.health.HcDataType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Drives the real Compose UI end to end (Robolectric, in-memory DB, fake Health Connect). */
@RunWith(AndroidJUnit4::class)
@Config(application = TestFormaApp::class, sdk = [35], qualifiers = "w400dp-h2400dp-xxhdpi")
class UiFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val c get() = (rule.activity.application as FormaApp).container

    @Before fun ready() {
        runBlocking { c.initialize() }
        rule.waitUntil(8000) { rule.onAllNodesWithTagExists("remaining_kcal") }
    }

    private fun text(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }

    private fun waitFor(timeout: Long = 5000, cond: () -> Boolean) = rule.waitUntil(timeout, cond)

    @Test fun `bottom navigation reaches every section`() {
        listOf("recipes" to "recipes", "workout" to "workout", "progress" to "progress", "settings" to "settings", "home" to "home").forEach { (tab, screen) ->
            rule.onNodeWithTag("tab_$tab").performClick()
            waitFor { rule.onAllNodesWithTagExists(screen) }
        }
    }

    @Test fun `manual food entry updates remaining immediately`() {
        val before = text("remaining_kcal").replace(",", "").toInt()
        rule.onNodeWithTag("add_food").performScrollTo().performClick()
        waitFor { rule.onAllNodesWithTagExists("add_food_screen") }
        rule.onNodeWithText("Manual entry").performClick()
        rule.onNodeWithTag("manual_name").performTextInput("Bagel")
        rule.onNodeWithTag("manual_kcal").performTextInput("350")
        rule.onNodeWithTag("manual_add").performClick()
        waitFor { rule.onAllNodesWithTagExists("remaining_kcal") && text("remaining_kcal").replace(",", "").toInt() == before - 350 }
        assertEquals(1, runBlocking { c.db.food().all() }.size)
    }

    @Test fun `recipe portion scaling, logging, editing and deleting`() {
        rule.onNodeWithTag("tab_recipes").performClick()
        rule.onNodeWithTag("filter_LUNCH").performClick()
        waitFor { rule.onAllNodesWithTagExists("recipe_chicken_shawarma_bowl") }
        rule.onNodeWithTag("recipe_chicken_shawarma_bowl").performClick()
        waitFor { rule.onAllNodesWithTagExists("recipe_detail") }
        val std = runBlocking { c.recipes.full("chicken_shawarma_bowl")!!.macros(com.forma.engine.Portion.STANDARD).kcal.toInt() }
        val large = runBlocking { c.recipes.full("chicken_shawarma_bowl")!!.macros(com.forma.engine.Portion.LARGE).kcal }
        waitFor { rule.onAllNodesWithTagExists("nutrition_kcal_${Math.round(std.toDouble())}") || rule.onAllNodes(hasText("$std")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Large").performClick()
        waitFor { rule.onAllNodesWithTagExists("nutrition_kcal_${Math.round(large)}") }
        rule.onNodeWithTag("add_to_log").performClick()
        waitFor { rule.onAllNodesWithTagExists("confirm_add") }
        rule.onNodeWithTag("confirm_add").performClick()
        waitFor { runBlocking { c.db.food().all() }.size == 1 }
        val e = runBlocking { c.db.food().all() }.single()
        assertEquals("LARGE", e.portion); assertEquals(large, e.kcal, 0.01); assertEquals("LUNCH", e.mealType)

        // Changing the portion of a logged entry recomputes nutrition from the ingredients
        runBlocking { c.food.changePortion(e.id, com.forma.engine.Portion.STANDARD, 2.0) }
        assertEquals(std * 2.0, runBlocking { c.db.food().get(e.id)!!.kcal }, 2.0)
    }

    @Test fun `food log edit sheet deletes an entry`() {
        runBlocking { c.food.logManual("Soup", "SUPPER", 220.0, 10.0, null, null, TestEnv.time.today().toEpochDay(), TestEnv.time.nowMillis()) }
        rule.onNodeWithText("Food log").performScrollTo().performClick()
        waitFor { rule.onAllNodesWithTagExists("food_log") && rule.onAllNodes(hasText("Soup")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Soup").performClick()
        waitFor { rule.onAllNodesWithTagExists("delete_entry") }
        rule.onNodeWithTag("delete_entry").performClick()
        rule.onNodeWithText("Delete").performClick()
        waitFor { runBlocking { c.db.food().all() }.isEmpty() }
    }

    @Test fun `log weight from home`() {
        rule.onNodeWithTag("log_weight").performScrollTo().performClick()
        waitFor { rule.onAllNodesWithTagExists("weight_input") }
        rule.onNodeWithTag("weight_input").performTextClearanceAndInput("257.4")
        rule.onNodeWithTag("weight_save").performClick()
        waitFor { runBlocking { c.db.body().allWeights() }.isNotEmpty() }
        assertEquals(257.4, runBlocking { c.db.body().allWeights() }.single().weightLb, 0.01)
        waitFor { runBlocking { c.db.profile().get()!!.currentWeightLb } == 257.4 }
    }

    @Test fun `workout start, check off, substitute`() {
        rule.onNodeWithTag("tab_workout").performClick()
        waitFor { rule.onAllNodesWithTagExists("workout_title") }
        rule.onNodeWithTag("day_MON").performClick()
        rule.onNodeWithTag("start_workout").performClick()
        waitFor { rule.onAllNodesWithTagExists("check_MON_1") }
        rule.onNodeWithTag("check_MON_1").performClick()
        waitFor { runBlocking { c.db.workouts().allExerciseLogs() }.any { it.slotKey == "MON_1" && it.completed } }
        rule.onNodeWithTag("swap_MON_1").performClick()
        waitFor { rule.onAllNodesWithTagExists("sub_goblet_squat") }
        rule.onNodeWithTag("sub_goblet_squat").performClick()
        waitFor { runBlocking { c.db.workouts().allExerciseLogs() }.any { it.slotKey == "MON_1" && it.exerciseKey == "goblet_squat" } }
        waitFor { text("exname_MON_1") == "Goblet squat" }
    }

    @Test fun `record carryover overage reduces tomorrow not today`() {
        rule.onNodeWithText("Manage").performScrollTo().performClick()
        waitFor { rule.onAllNodesWithTagExists("carryover") }
        rule.onNodeWithTag("record_overage").performClick()
        waitFor { rule.onAllNodesWithTagExists("co_amount") }
        rule.onNodeWithText("Today").performClick()
        rule.onNodeWithTag("co_amount").performTextInput("300")
        rule.onNodeWithTag("co_save").performClick()
        waitFor { runBlocking { c.db.carryover().all() }.size == 1 }
        waitFor { text("carryover_status").contains("Projected to clear") }
        assertTrue(text("carryover_status").contains("200 kcal a day"))
    }

    @Test fun `health connect unavailable and permission denied states`() {
        rule.onNodeWithTag("tab_settings").performClick()
        rule.onNodeWithText("Health Connect").performClick()
        waitFor { rule.onAllNodesWithTagExists("hc_status") && text("hc_status") == "Not available on this device" }
        rule.onNodeWithTag("hc_type_TOTAL_CALORIES").assertIsDisplayed()

        // Now: available, but nothing granted
        TestEnv.health.availability = HcAvailability.AVAILABLE
        TestEnv.health.granted = emptySet()
        rule.activity.runOnUiThread { rule.activity.recreate() }
        waitFor(8000) { rule.onAllNodesWithTagExists("hc_status") && text("hc_status") == "Available · not connected" }
        assertNotNull(rule.onNodeWithTag("hc_connect"))

        // Partially granted
        TestEnv.health.granted = setOf(HcDataType.STEPS, HcDataType.WEIGHT)
        rule.activity.runOnUiThread { rule.activity.recreate() }
        waitFor(8000) { rule.onAllNodesWithTagExists("hc_status") && text("hc_status").startsWith("Connected · 2 of") }
    }

    @Test fun `theme switch and targets persist`() {
        rule.onNodeWithTag("tab_settings").performClick()
        rule.onNodeWithText("Dark").performClick()
        waitFor { runBlocking { c.settings.settings.first() }.theme.name == "DARK" }
        rule.onNodeWithText("Targets & budget").performClick()
        waitFor { rule.onAllNodesWithTagExists("protein_input") }
        rule.onNodeWithTag("protein_input").performTextClearanceAndInput("190")
        rule.onNodeWithTag("save_targets").performScrollTo().performClick()
        waitFor { runBlocking { c.db.profile().get()!!.proteinTargetG } == 190 }
    }
}
