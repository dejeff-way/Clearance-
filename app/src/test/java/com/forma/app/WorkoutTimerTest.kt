package com.forma.app

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.data.db.FormaDatabase
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class WorkoutTimerTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val c get() = (rule.activity.application as FormaApp).container
    private fun advance(minutes: Long) { TestEnv.time.now = TestEnv.time.now.plusMinutes(minutes) }
    private fun text(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    @Before fun ready() {
        runBlocking { c.initialize() }
        rule.waitUntil(8000) { rule.onAllNodesWithTagExists("remaining_kcal") }
    }

    @Test fun `paused time is excluded and duration can be corrected`() = runBlocking {
        val sid = c.workouts.ensureSession(TestEnv.time.today(), "TUE")
        c.workouts.startTimer(sid)
        advance(20); c.workouts.pause(sid)
        advance(240)                                   // phone left on the counter
        c.workouts.resume(sid)
        advance(25); c.workouts.finish(sid)
        val s = c.db.workouts().sessionById(sid)!!
        assertEquals(45.0, s.activeMinutes(s.completedAt!!)!!, 0.01)
        c.workouts.setDuration(sid, 52)
        val s2 = c.db.workouts().sessionById(sid)!!
        assertEquals(52.0, s2.activeMinutes(s2.completedAt!!)!!, 0.01)
        assertEquals(s.completedAt, s2.completedAt)
        // Finishing while paused closes the pause
        val sid2 = c.workouts.ensureSession(TestEnv.time.today().plusDays(1), "WED")
        c.workouts.startTimer(sid2); advance(30); c.workouts.pause(sid2); advance(60); c.workouts.finish(sid2)
        val s3 = c.db.workouts().sessionById(sid2)!!
        assertNull(s3.pausedAt); assertEquals(30.0, s3.activeMinutes(s3.completedAt!!)!!, 0.01)
    }

    @Test fun `forgotten workout asks before saving 300+ minutes, and duration is editable`() {
        rule.onNodeWithTag("tab_workout").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("start_workout") }
        rule.onNodeWithTag("start_workout").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("pause_workout") }
        rule.onNodeWithTag("pause_workout").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("resume_workout") }
        rule.onNodeWithTag("resume_workout").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("pause_workout") }
        advance(323)                                     // the reported bug: walked away for hours
        rule.onNodeWithTag("finish_workout").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("finish_minutes") }
        rule.onRoot().captureRoboImage("screenshots/25_finish_check_light.png")
        rule.onNodeWithTag("finish_save_minutes").performClick()   // planned 50 min pre-filled
        rule.waitUntil(5000) { runBlocking { c.db.workouts().allSessions() }.any { it.completedAt != null } }
        val s = runBlocking { c.db.workouts().allSessions() }.single()
        assertEquals(50.0, s.activeMinutes(s.completedAt!!)!!, 0.01)
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("edit_duration") }
        rule.onNodeWithTag("edit_duration").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("duration_input") }
        rule.onNodeWithTag("duration_input").performTextClearance()
        rule.onNodeWithTag("duration_input").performTextInput("47")
        rule.onNodeWithTag("duration_save").performClick()
        rule.waitUntil(5000) { runBlocking { c.db.workouts().allSessions() }.single().let { it.activeMinutes(it.completedAt!!)!!.toInt() == 47 } }
        assertNotNull(runBlocking { c.db.workouts().allSessions() }.single().completedAt)
    }

    @Test fun `database migrates from version 4`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("migrate5.db")
        val schema = Json.parseToJsonElement(File("schemas/com.forma.app.data.db.FormaDatabase/4.json").readText()).jsonObject["database"]!!.jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath("migrate5.db").apply { parentFile?.mkdirs() }, null)
        schema["entities"]!!.jsonArray.forEach { e ->
            val o = e.jsonObject; val t = o["tableName"]!!.jsonPrimitive.content
            sql.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t))
            o["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t)) }
        }
        schema["setupQueries"]!!.jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }
        // The reported case: a workout recorded as 323 minutes
        sql.execSQL("INSERT INTO workout_sessions (id, epochDay, templateKey, title, startedAt, completedAt) VALUES (1, 20000, 'TUE', 'Cardio + Mobility', 0, ${323 * 60_000L})")
        sql.version = 4; sql.close()
        val db = androidx.room.Room.databaseBuilder(ctx, FormaDatabase::class.java, "migrate5.db")
            .addMigrations(FormaDatabase.MIGRATION_1_2, FormaDatabase.MIGRATION_2_3, FormaDatabase.MIGRATION_3_4, FormaDatabase.MIGRATION_4_5, FormaDatabase.MIGRATION_5_6).build()
        runBlocking {
            val s = db.workouts().sessionById(1)!!
            assertEquals(0L, s.pausedMs); assertEquals(323.0, s.activeMinutes(s.completedAt!!)!!, 0.01)
        }
        db.close(); ctx.deleteDatabase("migrate5.db")
    }
}
