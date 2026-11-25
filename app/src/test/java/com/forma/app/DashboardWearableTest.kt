package com.forma.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.health.DailyAggregate
import com.forma.app.health.HcAvailability
import com.forma.app.health.HcDataType
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
class DashboardWearableTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val c get() = (rule.activity.application as FormaApp).container
    private val today get() = TestEnv.time.today()

    @Before fun ready() {
        runBlocking { c.initialize() }
        rule.waitUntil(8000) { rule.onAllNodesWithTagExists("remaining_kcal") }
    }

    private fun text(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString { it.text }

    @Test fun `tiles show honest blanks when Health Connect isn't connected`() {
        assertEquals("—", text("tile_value_STEPS"))
        assertEquals("—", text("tile_value_HEART_RATE"))
        assertEquals("—", text("tile_value_SLEEP"))
        assertTrue(text("sync_status").contains("not connected"))
    }

    @Test fun `band data appears after refresh, updates live and opens history`() {
        val hc = TestEnv.health
        hc.availability = HcAvailability.AVAILABLE
        hc.granted = HcDataType.entries.toSet() - HcDataType.HRV
        hc.aggregates = (0..20).associate { i ->
            val d = today.minusDays(i.toLong())
            d to DailyAggregate(d, totalKcal = if (i == 0) 1800.0 else 2900.0 + i * 11, steps = 6000L + i * 377, exerciseMinutes = if (i % 2 == 0) 45.0 else null,
                basalKcal = if (i == 0) 1250.0 else 2250.0, distanceM = 5200.0 + i * 50, avgHr = 74.0 + i % 3, minHr = 52.0, maxHr = 140.0, restingHr = 60.0 - i * 0.1)
        }
        // Connect: in the app this happens through the permission screen; here we record the grant and refresh.
        runBlocking { c.db.syncMeta().upsert(com.forma.app.data.db.SyncMetaEntity(grantedPermissions = "STEPS")) }
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("sync_refresh") }
        rule.onNodeWithTag("sync_refresh").performClick()
        rule.waitUntil(8000) { text("tile_value_STEPS") == "6,000" }
        assertEquals("74", text("tile_value_HEART_RATE"))
        assertTrue(text("sync_status").contains("synced"))
        rule.onRoot().captureRoboImage("screenshots/21_dashboard_wearable_light.png")

        // New data arrives (band synced more steps) → dashboard updates after the next sync
        hc.aggregates = hc.aggregates + (today to hc.aggregates[today]!!.copy(steps = 10450))
        hc.pendingUpserts = listOf(today.atTime(15, 0).atZone(TestEnv.time.zone()).toInstant())
        rule.onNodeWithTag("sync_refresh").performClick()
        rule.waitUntil(8000) { text("tile_value_STEPS") == "10,450" }

        rule.onNodeWithTag("tile_STEPS").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("metric_STEPS") }
        rule.onRoot().captureRoboImage("screenshots/22_metric_steps_light.png")
        rule.activity.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("tile_RESTING_HR") }
        rule.onNodeWithTag("tile_RESTING_HR").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("metric_RESTING_HR") }
        rule.onRoot().captureRoboImage("screenshots/23_metric_rhr_light.png")
    }

    @Test fun `health connect screen reports what is actually arriving`() {
        val hc = TestEnv.health
        hc.availability = HcAvailability.AVAILABLE
        hc.granted = setOf(HcDataType.STEPS, HcDataType.HEART_RATE)
        hc.aggregates = mapOf(today to DailyAggregate(today, steps = 4200, origins = mapOf(HcDataType.STEPS to setOf("com.fitbit.FitbitMobile"))))
        runBlocking { c.healthSyncer.sync() }
        rule.onNodeWithTag("tab_settings").performClick()
        rule.waitUntil(5000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Health Connect"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(androidx.compose.ui.test.hasText("Health Connect"), useUnmergedTree = true).performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("hc_check_STEPS") }
        rule.waitUntil(5000) { text("hc_check_STEPS").contains("Fitbit") }
        assertTrue(text("hc_check_STEPS").startsWith("Data on 1 of 30 days"))
        assertTrue(text("hc_check_HEART_RATE").contains("no records found"))
        assertTrue(text("hc_check_SLEEP").startsWith("Not allowed"))
        assertTrue(rule.onAllNodesWithTagExists("hc_allow_more"))
        rule.onRoot().captureRoboImage("screenshots/24_health_data_check_light.png")
    }
}
