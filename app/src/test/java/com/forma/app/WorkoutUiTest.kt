package com.forma.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.data.workout.WorkoutCatalogLoader
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestFormaApp::class, sdk = [35], qualifiers = "w400dp-h2400dp-xxhdpi")
class WorkoutUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val c get() = (rule.activity.application as FormaApp).container

    @Before fun ready() {
        runBlocking { c.initialize() }
        rule.waitUntil(8000) { rule.onAllNodesWithTagExists("remaining_kcal") }
        rule.onNodeWithTag("tab_workout").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("workout_title") }
    }

    @Test fun `import a program, review, activate it and see it scheduled`() {
        rule.onNodeWithTag("open_workout_import").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("wpaste_input") }
        rule.onNodeWithTag("wpaste_input").performTextInput(WorkoutFixtures.EIGHT_WEEK)
        rule.onNodeWithTag("wcheck").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("wcand_0") }
        // Activate the program (index 3: after the three workouts)
        rule.onNodeWithTag("wimport_list").performScrollToNode(hasTestTag("wcand_3"))
        rule.onRoot().captureRoboImage("screenshots/18_workout_import_preview_light.png")
        val programIndex = 3
        rule.activity.runOnUiThread { }
        runBlocking { } // keep ordering explicit
        rule.onNodeWithTag("wcand_$programIndex").performScrollTo()
        rule.onNode(androidx.compose.ui.test.hasText("Make this my active program")).performClick()
        rule.onNodeWithTag("wconfirm").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("import_report") }
        val cat = runBlocking { WorkoutCatalogLoader.load(c.db) }
        assertEquals("ski-8-week", cat.activeProgram!!.id)
        rule.onNodeWithTag("import_done").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("workout_title") }
        rule.onRoot().captureRoboImage("screenshots/19_workout_after_import_light.png")
        rule.onNodeWithTag("open_schedule").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("sched_SUN") }
        rule.onRoot().captureRoboImage("screenshots/20_schedule_light.png")
    }

    @Test fun `user example saves to library only, then schedule and do today`() {
        rule.onNodeWithTag("open_workout_import").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("wpaste_input") }
        rule.onNodeWithTag("wpaste_input").performTextInput(WorkoutFixtures.USER_EXAMPLE)
        rule.onNodeWithTag("wcheck").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("wcand_0") }
        rule.onNodeWithTag("wconfirm").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("import_report") }
        assertEquals("TUE", runBlocking { WorkoutCatalogLoader.load(c.db) }.planFor(TestEnv.time.today()).workout!!.id) // schedule untouched
        rule.onNodeWithTag("import_done").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("open_library") }
        rule.onNodeWithTag("open_library").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("lib_ski-legs-001") }
        rule.onRoot().captureRoboImage("screenshots/16_workout_library_light.png")
        rule.onNodeWithTag("lib_ski-legs-001").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("workout_detail") }
        rule.onRoot().captureRoboImage("screenshots/17_workout_detail_light.png")
        rule.onNodeWithTag("detail_do_today").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("start_workout") }
        rule.onNodeWithTag("start_workout").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("check_ski-legs-001#leg-press") }
        rule.onNodeWithTag("swap_ski-legs-001#leg-press").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("sub_x-hack-squat") }
        rule.onNodeWithTag("sub_x-hack-squat").performClick()
        rule.waitUntil(5000) { runBlocking { c.db.workouts().allExerciseLogs() }.any { it.exerciseKey == "x-hack-squat" } }
        val s = runBlocking { c.db.workouts().allSessions() }.single()
        assertEquals("ski-legs-001", s.templateKey)
        assertTrue(runBlocking { WorkoutCatalogLoader.load(c.db) }.planFor(TestEnv.time.today()).workout!!.id == "TUE")
    }
}
