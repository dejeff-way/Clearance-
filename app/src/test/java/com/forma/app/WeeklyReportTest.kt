package com.forma.app

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.data.db.DailyEnergyEntity
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.db.WeightEntity
import com.forma.app.data.report.ReportNotifier
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestFormaApp::class, sdk = [35], qualifiers = "w400dp-h2400dp-xxhdpi")
class WeeklyReportTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val c get() = (rule.activity.application as FormaApp).container
    private val today get() = TestEnv.time.today()                 // Tue Nov 24, 2026
    private val lastWeek = LocalDate.of(2026, 11, 15)              // Sun Nov 15 – Sat Nov 21
    private fun text(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    @Before fun ready() {
        runBlocking { c.initialize() }
        rule.waitUntil(8000) { rule.onAllNodesWithTagExists("remaining_kcal") }
    }

    private fun at(d: LocalDate, hour: Int) = c.time.startOfDayMillis(d) + hour * 3_600_000L

    /** One week of food, steps and sleep, plus 5 weeks of face pulls and planks for PRs. */
    private fun seed() = runBlocking {
        (0L..6L).forEach { i ->
            val d = lastWeek.plusDays(i)
            c.food.logManual("Day food", "LUNCH", 2000.0, 150.0, 200.0, 60.0, d.toEpochDay(), at(d, 13))
            c.db.energy().upsert(DailyEnergyEntity(d.toEpochDay(), steps = 8000L + i * 1000,
                sleepMinutes = 450.0, sleepDeepMin = 80.0, sleepLightMin = 250.0, sleepRemMin = 120.0, sleepAwakeMin = 20.0))
        }
        c.db.body().insertWeight(WeightEntity(epochDay = lastWeek.minusDays(5).toEpochDay(), measuredAt = at(lastWeek.minusDays(5), 7), weightLb = 258.0))
        c.db.body().insertWeight(WeightEntity(epochDay = lastWeek.plusDays(2).toEpochDay(), measuredAt = at(lastWeek.plusDays(2), 7), weightLb = 256.5))
        // Baseline from Oct 14 (Wednesdays): face pull 35 × 10, plank 30 s
        var d = LocalDate.of(2026, 10, 14)
        while (d.isBefore(lastWeek)) {
            val sid = c.workouts.ensureSession(d, "WED")
            c.workouts.logSet(sid, "WED_5", 0, 35.0, 10, null)
            c.workouts.logSet(sid, "WED_6", 0, null, null, 0.5)
            c.workouts.finish(sid)
            d = d.plusWeeks(1)
        }
        // Last week: 40 × 12 and a 45 s plank → 2 PRs. The row matches nothing earlier, so it isn't one.
        val sid = c.workouts.ensureSession(lastWeek.plusDays(3), "WED")
        c.workouts.logSet(sid, "WED_5", 0, 40.0, 12, null)
        c.workouts.logSet(sid, "WED_6", 0, null, null, 0.75)
        c.workouts.logSet(sid, "WED_1", 0, 120.0, 10, null)
        c.workouts.finish(sid)
        withTimeout(5000) { while (c.energyModel.value?.hasFood(lastWeek.plusDays(6)) != true) delay(50) }
    }

    @Test fun `last week's report has food, deficit, sleep, steps, workouts and PRs`() {
        seed()
        val r = runBlocking { c.reports.refresh() }
        assertNotNull(r); r!!
        assertEquals(lastWeek.toEpochDay(), r.weekStart)
        assertEquals(7, r.daysLogged); assertEquals(14000, r.kcalTotal); assertEquals(2000, r.kcalAvg)
        val m = c.energyModel.value!!
        assertEquals((0L..6L).sumOf { m.summary(lastWeek.plusDays(it)).projectedDeficit }, r.deficitTotal)
        assertEquals(100, r.sleepScore); assertEquals(7, r.sleepNights); assertEquals(450, r.sleepAvgMin)
        assertEquals(77000L, r.stepsTotal); assertEquals(11000L, r.stepsAvg); assertEquals(5, r.stepGoalDays)
        assertEquals(256.5, r.weightAvg!!, 0.01); assertEquals(-1.5, r.weightChange!!, 0.01)
        assertEquals(1, r.workoutsDone)
        assertTrue(r.prsUnlocked)
        assertEquals(setOf("Face pull", "Plank"), r.prs.map { it.exercise }.toSet())
        val fp = r.prs.first { it.exercise == "Face pull" }
        assertEquals("40 lb × 12", fp.now); assertEquals("35 lb × 10", fp.before)
        assertEquals("45 s", r.prs.first { it.exercise == "Plank" }.now)
        assertTrue(ReportNotifier.summary(r).contains("2 PRs"))

        // Earlier weeks are filled in for the history (already dismissed); the latest isn't
        val all = runBlocking { c.db.reports().all() }
        assertTrue(all.size >= 5)
        assertFalse(all.first { it.weekStartEpochDay == lastWeek.toEpochDay() }.dismissed)
        assertTrue(all.filter { it.weekStartEpochDay != lastWeek.toEpochDay() }.all { it.dismissed })
        // Weeks before the 4-week baseline don't call out PRs
        val early = c.reports.decode(all.first { it.weekStartEpochDay == LocalDate.of(2026, 10, 18).toEpochDay() })
        assertFalse(early.prsUnlocked); assertTrue(early.prs.isEmpty())
        // Refreshing again doesn't announce it twice
        assertNull(runBlocking { c.reports.refresh() })
    }

    @Test fun `home card shows until dismissed, report and history screens`() {
        seed(); runBlocking { c.reports.refresh() }
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("weekly_card") }
        rule.onRoot().captureRoboImage("screenshots/33_weekly_card_light.png")
        rule.onNodeWithTag("weekly_card").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("weekly_report") }
        rule.waitUntil(3000) { rule.onAllNodes(hasText("You broke 2 PRs this week"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().captureRoboImage("screenshots/34_weekly_report_light.png")
        rule.onNodeWithTag("report_sleep").performScrollTo()
        rule.onNodeWithTag("stage_bar", useUnmergedTree = true).assertExists()
        rule.onRoot().captureRoboImage("screenshots/35_weekly_report_sleep_light.png")
        rule.onNodeWithTag("report_title").performScrollTo()
        rule.onNode(hasText("All weeks")).performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("weekly_reports") }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("weekly_dismiss") }
        rule.onNodeWithTag("weekly_dismiss").performClick()
        rule.waitUntil(5000) { !rule.onAllNodesWithTagExists("weekly_card") }
        assertTrue(runBlocking { c.db.reports().get(lastWeek.toEpochDay()) }!!.dismissed)
        // Still available in the history afterwards
        rule.onNodeWithTag("tab_progress").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("open_reports") }
        rule.onNodeWithTag("open_reports").performClick()
        rule.waitUntil(5000) { rule.onAllNodes(hasText("Nov 15 – 21", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun `notification tap opens the report`() {
        seed(); runBlocking { c.reports.refresh() }
        c.pendingRoute.value = "report/${lastWeek.toEpochDay()}"
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("weekly_report") }
    }

    @Test fun `report comes out Sunday at 6 am`() {
        val zone = ZoneId.of("America/New_York")
        val saved = TestEnv.time.now
        try {
            TestEnv.time.now = ZonedDateTime.of(2026, 11, 29, 5, 59, 0, 0, zone)   // Sunday before 6
            assertEquals(lastWeek, c.reports.latestReadyWeek())
            TestEnv.time.now = ZonedDateTime.of(2026, 11, 29, 6, 0, 0, 0, zone)
            assertEquals(LocalDate.of(2026, 11, 22), c.reports.latestReadyWeek())
            TestEnv.time.now = ZonedDateTime.of(2026, 12, 5, 23, 0, 0, 0, zone)    // Saturday night
            assertEquals(LocalDate.of(2026, 11, 22), c.reports.latestReadyWeek())
        } finally { TestEnv.time.now = saved }
    }

    @Test fun `no data means no report`() {
        assertNull(runBlocking { c.reports.refresh() })
        assertTrue(runBlocking { c.db.reports().all() }.isEmpty())
    }

    @Test fun `reports are kept in backups`() = runBlocking {
        seed(); c.reports.refresh()
        val b = c.backup.snapshot(0)
        assertTrue(b.weeklyReports.isNotEmpty())
        val n = b.weeklyReports.size
        c.backup.restore(c.backup.decode(c.backup.encode(b)))
        assertEquals(n, c.db.reports().all().size)
    }

    @Test fun `database migrates from version 8`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("migrate9.db")
        val schema = Json.parseToJsonElement(File("schemas/com.forma.app.data.db.FormaDatabase/8.json").readText()).jsonObject["database"]!!.jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath("migrate9.db").apply { parentFile?.mkdirs() }, null)
        schema["entities"]!!.jsonArray.forEach { e ->
            val o = e.jsonObject; val t = o["tableName"]!!.jsonPrimitive.content
            sql.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t))
            o["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t)) }
        }
        schema["setupQueries"]!!.jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }
        sql.execSQL("INSERT INTO food_log (id, epochDay, eatenAt, createdAt, mealType, name, servings, kcal, proteinG) VALUES (1, 20500, 1, 1, 'LUNCH', 'Kept', 1, 500, 20)")
        sql.version = 8; sql.close()
        val db = androidx.room.Room.databaseBuilder(ctx, FormaDatabase::class.java, "migrate9.db")
            .addMigrations(FormaDatabase.MIGRATION_1_2, FormaDatabase.MIGRATION_2_3, FormaDatabase.MIGRATION_3_4, FormaDatabase.MIGRATION_4_5,
                FormaDatabase.MIGRATION_5_6, FormaDatabase.MIGRATION_6_7, FormaDatabase.MIGRATION_7_8, FormaDatabase.MIGRATION_8_9, FormaDatabase.MIGRATION_9_10).build()
        runBlocking {
            assertEquals("Kept", db.food().all().single().name)
            assertTrue(db.reports().all().isEmpty())
        }
        db.close(); ctx.deleteDatabase("migrate9.db")
    }
}
