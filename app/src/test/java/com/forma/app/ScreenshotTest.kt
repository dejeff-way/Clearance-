package com.forma.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every main screen to PNG (light + dark) for design review. Run with:
 *   ./gradlew :app:recordRoborazziDebug --tests "*ScreenshotTest*"
 * Images land in app/screenshots/. Tall window so whole pages are visible.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestFormaApp::class, sdk = [35], qualifiers = "w400dp-h2200dp-xxhdpi")
open class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    open val suffix = "light"

    private val c get() = (rule.activity.application as FormaApp).container

    private fun load() {
        runBlocking { c.initialize(); DemoData.load(c, TestEnv.time.today()) }
        rule.waitUntil(8000) { rule.onAllNodesWithTagExists("remaining_kcal") }
        Thread.sleep(400); rule.waitForIdle()
    }

    private fun shot(name: String) { rule.waitForIdle(); Thread.sleep(300); rule.waitForIdle(); rule.onRoot().captureRoboImage("screenshots/${name}_$suffix.png") }

    @Test fun home() { load(); shot("01_home") }

    @Test fun recipes() {
        load(); rule.onNodeWithTag("tab_recipes").performClick(); rule.waitUntil(5000) { rule.onAllNodesWithTagExists("recipe_protein_egg_scramble") }
        shot("02_recipes")
        rule.onNodeWithTag("filter_LUNCH").performClick(); shot("02b_recipes_lunch")
    }

    @Test fun recipeDetail() {
        load(); rule.onNodeWithTag("tab_recipes").performClick()
        rule.onNodeWithTag("filter_LUNCH").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("recipe_salmon_poke_bowl") }
        rule.onNodeWithTag("recipe_salmon_poke_bowl").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("recipe_detail") }
        shot("03_recipe_detail")
    }

    @Test fun creamiDetail() {
        load(); rule.onNodeWithTag("tab_recipes").performClick(); rule.onNodeWithTag("filter_SNACKS").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("recipe_chocolate_tnuva_creami") }
        rule.onNodeWithTag("recipe_chocolate_tnuva_creami").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("recipe_detail") }
        shot("03b_creami_detail")
    }

    @Test fun workout() {
        load(); rule.onNodeWithTag("tab_workout").performClick(); rule.waitUntil(5000) { rule.onAllNodesWithTagExists("workout_title") }
        rule.onNodeWithTag("start_workout").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("check_TUE_1") }
        rule.onNodeWithTag("check_TUE_1").performClick()
        shot("04_workout")
    }

    @Test fun progress() { load(); rule.onNodeWithTag("tab_progress").performClick(); rule.waitUntil(5000) { rule.onAllNodesWithTagExists("progress") }; shot("05_progress") }

    @Test fun settings() { load(); rule.onNodeWithTag("tab_settings").performClick(); rule.waitUntil(5000) { rule.onAllNodesWithTagExists("settings") }; shot("06_settings") }

    @Test fun energy() {
        load(); rule.onNodeWithTag("remaining_kcal", useUnmergedTree = true).performClick()
        Thread.sleep(300); rule.waitForIdle(); shot("07_energy_breakdown")
    }

    @Test fun addFood() {
        load()
        rule.activity.runOnUiThread { }
        rule.onNodeWithTagScrolled("add_food")
        rule.onNodeWithTag("add_food").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("add_food_screen") }
        shot("08_add_food")
    }

    @Test fun healthUnavailable() {
        load(); rule.onNodeWithTag("tab_settings").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("settings") }
        rule.onNode(androidx.compose.ui.test.hasText("Health Connect")).performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("hc_status") }
        shot("09_health_unavailable")
    }
}

@Config(application = TestFormaApp::class, sdk = [35], qualifiers = "w400dp-h2200dp-night-xxhdpi")
class ScreenshotDarkTest : ScreenshotTest() { override val suffix = "dark" }
