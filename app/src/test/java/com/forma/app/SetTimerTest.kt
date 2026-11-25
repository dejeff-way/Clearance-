package com.forma.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.data.seed.ExerciseKind
import com.forma.app.data.workout.SetTimer
import com.forma.app.data.workout.TimerAdvice
import com.forma.app.data.workout.TimerKind
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

class TimerAdviceTest {
    @Test fun `hold lengths are read from the target text`() {
        assertEquals(20 to 40, TimerAdvice.holdRange("20–40 s"))
        assertEquals(45 to 45, TimerAdvice.holdRange("45 s / side"))
        assertEquals(300 to 300, TimerAdvice.holdRange("5 min"))
        assertEquals(30 to 60, TimerAdvice.holdRange("30-60 sec"))
        assertNull(TimerAdvice.holdRange("10–12"))
        assertTrue(TimerAdvice.perSide("45 s / side")); assertTrue(TimerAdvice.perSide("30 s / leg")); assertFalse(TimerAdvice.perSide("20–40 s"))
    }

    @Test fun `holds get a countdown but long cardio stays in minutes`() {
        assertTrue(TimerAdvice.hasHoldTimer(true, ExerciseKind.CORE, "20–40 s"))
        assertTrue(TimerAdvice.hasHoldTimer(true, ExerciseKind.MOBILITY, "5 min"))
        assertFalse(TimerAdvice.hasHoldTimer(true, ExerciseKind.CARDIO, "20–25 min"))
        assertFalse(TimerAdvice.hasHoldTimer(false, ExerciseKind.STRENGTH, "10–12"))
    }

    @Test fun `start from last time else the low end`() {
        assertEquals(20, TimerAdvice.defaultHold("20–40 s", null))
        assertEquals(35, TimerAdvice.defaultHold("20–40 s", 35))
    }

    @Test fun `recommended rest uses the workout's value, otherwise sensible defaults`() {
        assertEquals(90, TimerAdvice.recommendedRest(90, ExerciseKind.STRENGTH, true, false))
        assertEquals(90, TimerAdvice.recommendedRest(0, ExerciseKind.STRENGTH, true, false))
        assertEquals(60, TimerAdvice.recommendedRest(0, ExerciseKind.STRENGTH, false, false))
        assertEquals(45, TimerAdvice.recommendedRest(0, ExerciseKind.CORE, false, false))
        assertEquals(30, TimerAdvice.recommendedRest(0, ExerciseKind.CORE, false, true))
    }

    @Test fun `phases run get ready then hold, and per-side adds a switch`() {
        val t = SetTimer.hold(1, "x", "Plank", 0, 3, 30, perSide = false, restAfter = 45, now = 0)
        assertEquals("Get ready", t.at(1_000).phase!!.label)
        val mid = t.at(13_000)
        assertEquals("Hold", mid.phase!!.label); assertEquals(20_000, mid.remainingMs); assertEquals(10, mid.heldSeconds)
        assertTrue(t.at(33_000).done); assertEquals(30, t.at(40_000).heldSeconds)
        val side = SetTimer.hold(1, "x", "Side hold", 0, 2, 45, perSide = true, restAfter = 15, now = 0)
        assertEquals(listOf("Get ready", "First side", "Switch sides", "Second side"), side.phases.map { it.label })
        assertEquals("Switch sides", side.at(50_000).phase!!.label)
        assertEquals(90, side.at(200_000).heldSeconds)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestFormaApp::class, sdk = [35], qualifiers = "w400dp-h2400dp-xxhdpi")
class SetTimerUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val c get() = (rule.activity.application as FormaApp).container
    private fun advanceSeconds(s: Long) { TestEnv.time.now = TestEnv.time.now.plusSeconds(s) }
    private fun text(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
    private fun exists(tag: String) = rule.onAllNodesWithTagExists(tag)

    @Before fun ready() {
        runBlocking { c.initialize() }
        rule.waitUntil(8000) { exists("remaining_kcal") }
        c.workoutOverride.value = TestEnv.time.today() to "WED"     // Upper B ends with planks
        rule.onNodeWithTag("tab_workout").performClick()
        rule.waitUntil(5000) { exists("start_workout") }
        rule.onNodeWithTag("start_workout").performScrollTo().performClick()
        rule.waitUntil(5000) { exists("pause_workout") }
    }

    @Test fun `plank hold counts down, logs the set, then starts the recommended rest`() {
        rule.onNodeWithTag("hold_len_WED_6", useUnmergedTree = true).performScrollTo()
        assertEquals("20 s", text("hold_len_WED_6"))
        rule.onNodeWithTag("hold_plus_WED_6").performClick()
        rule.waitUntil(3000) { text("hold_len_WED_6") == "25 s" }
        rule.onNodeWithTag("hold_WED_6_0").performScrollTo().performClick()
        rule.waitUntil(5000) { exists("set_timer") }
        assertEquals("Get ready", text("timer_phase"))
        advanceSeconds(13)                                  // 3 s get-ready + 10 s into the hold
        rule.waitUntil(3000) { text("timer_phase") == "Hold" && text("timer_remaining") == "0:15" }
        rule.onRoot().captureRoboImage("screenshots/31_plank_timer_light.png")
        advanceSeconds(16)
        rule.waitUntil(5000) { runBlocking { c.db.workouts().allSetLogs() }.any { it.setIndex == 0 && it.durationMin != null && Math.round(it.durationMin!! * 60) == 25L } }
        // Rest starts on its own with the workout's 45 s
        rule.waitUntil(5000) { c.setTimers.current.value?.kind == TimerKind.REST }
        rule.waitUntil(3000) { text("timer_phase") == "Rest" && text("timer_remaining") == "0:45" }
        rule.onNodeWithTag("rest_plus").performClick()
        rule.waitUntil(3000) { text("timer_remaining") == "1:00" }
        rule.onRoot().captureRoboImage("screenshots/32_rest_timer_light.png")
        advanceSeconds(61)
        rule.waitUntil(5000) { c.setTimers.current.value == null && !exists("set_timer") }
        rule.waitUntil(3000) { text("hold_done_WED_6_0").startsWith("✓ 25 s") }
    }

    @Test fun `stopping a hold early saves the time actually held`() {
        rule.onNodeWithTag("hold_WED_6_1").performScrollTo().performClick()
        rule.waitUntil(5000) { exists("set_timer") }
        advanceSeconds(3 + 12)
        rule.waitUntil(3000) { c.setTimers.tick.value?.heldSeconds == 12 }
        rule.onNodeWithTag("timer_stop").performClick()
        rule.waitUntil(5000) { runBlocking { c.db.workouts().allSetLogs() }.any { it.setIndex == 1 && it.durationMin != null && Math.round(it.durationMin!! * 60) == 12L } }
        assertNull(c.setTimers.current.value)
    }

    @Test fun `rest button on a strength set starts the recommended rest and can be skipped`() {
        rule.onNodeWithTag("rest_WED_1_0").performScrollTo().performClick()
        rule.waitUntil(5000) { exists("set_timer") }
        assertEquals("1:30", text("timer_remaining"))
        rule.onNodeWithTag("rest_minus").performClick()
        rule.waitUntil(3000) { text("timer_remaining") == "1:15" }
        rule.onNodeWithTag("rest_skip").performClick()
        rule.waitUntil(3000) { !exists("set_timer") }
        assertTrue(runBlocking { c.db.workouts().allSetLogs() }.isEmpty())
    }
}
