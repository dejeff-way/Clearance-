package com.forma.app

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.prefs.AppSettings
import com.forma.app.data.repo.EnergyModel
import com.forma.app.di.AppContainer
import com.forma.app.health.DailyAggregate
import com.forma.app.health.HcDataType
import com.forma.app.health.HcExerciseSession
import com.forma.app.health.HcSample
import com.forma.app.health.HcSleep
import com.forma.app.health.HcSleepStage
import com.forma.app.health.HealthSyncer
import com.forma.app.health.SessionStats
import com.forma.app.health.SleepStage
import com.forma.app.health.SyncOutcome
import com.forma.app.ui.health.dataCheck
import com.forma.app.ui.metrics.Metric
import com.forma.app.ui.metrics.MetricValues
import com.forma.engine.ExpenditureSource
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(application = TestFormaApp::class, sdk = [35])
class WearableSyncTest {
    private lateinit var c: AppContainer
    private lateinit var hc: FakeHealth
    private val today = LocalDate.of(2026, 11, 24)
    private val zone get() = TestEnv.time.zone()
    private val fitbit = "com.fitbit.FitbitMobile"
    private fun at(d: LocalDate, h: Int, m: Int = 0): Instant = d.atTime(h, m).atZone(zone).toInstant()

    @Before fun setUp() = runBlocking {
        TestEnv.time = FixedTime(); hc = FakeHealth(); TestEnv.health = hc
        c = TestEnv.container(ApplicationProvider.getApplicationContext())
        c.initialize()
        val y = today.minusDays(1)
        val o = HcDataType.entries.associateWith { setOf(fitbit) }
        hc.aggregates = mapOf(
            y to DailyAggregate(y, totalKcal = 3150.0, activeKcal = 900.0, steps = 11200, exerciseMinutes = 48.0, basalKcal = 2250.0,
                distanceM = 8046.72, floors = 9.0, avgHr = 78.0, minHr = 52.0, maxHr = 151.0, restingHr = 58.0, origins = o),
            today to DailyAggregate(today, totalKcal = 1650.0, steps = 5200, basalKcal = 1200.0, avgHr = 74.0, minHr = 55.0, maxHr = 120.0, restingHr = 57.0, origins = o),
        )
        hc.sessions = listOf(HcExerciseSession("run1", at(y, 18), at(y, 18, 48), 56, null, fitbit))
        hc.stats = mapOf("run1" to SessionStats(avgHr = 142.0, maxHr = 168.0, totalKcal = 520.0, activeKcal = 430.0, distanceM = 6400.0))
        hc.sleeps = listOf(HcSleep("sl1", at(y, 23), at(today, 7), listOf(
            HcSleepStage(at(y, 23), at(y, 23, 20), SleepStage.AWAKE), HcSleepStage(at(y, 23, 20), at(today, 1), SleepStage.LIGHT),
            HcSleepStage(at(today, 1), at(today, 2, 30), SleepStage.DEEP), HcSleepStage(at(today, 2, 30), at(today, 4), SleepStage.REM),
            HcSleepStage(at(today, 4), at(today, 7), SleepStage.LIGHT),
        ), fitbit))
        hc.samples = mapOf(
            HcDataType.HRV to listOf(HcSample("h1", at(today, 3), 40.0, fitbit), HcSample("h2", at(today, 5), 50.0, fitbit)),
            HcDataType.OXYGEN_SATURATION to listOf(HcSample("o1", at(today, 4), 96.0, fitbit)),
            HcDataType.RESPIRATORY_RATE to listOf(HcSample("r1", at(today, 4), 14.5, fitbit)),
        )
    }

    @After fun tearDown() { c.db.close() }

    private fun model(pred: (EnergyModel) -> Boolean = { true }): EnergyModel = runBlocking {
        withTimeout(5000) { while (true) { val m = c.energyModel.filterNotNull().first(); if (pred(m)) return@withTimeout m; kotlinx.coroutines.delay(50) }; error("") }
    }

    @Test fun `all supported data types are imported`() = runBlocking {
        assertTrue(c.healthSyncer.sync() is SyncOutcome.Success)
        val y = c.db.energy().get(today.minusDays(1).toEpochDay())!!
        assertEquals(2250.0, y.hcBasalKcal!!, 0.0); assertEquals(8046.72, y.distanceM!!, 0.0); assertEquals(9.0, y.floors!!, 0.0)
        assertEquals(78.0, y.avgHr!!, 0.0); assertEquals(151.0, y.maxHr!!, 0.0); assertEquals(58.0, y.restingHr!!, 0.0)
        val t = c.db.energy().get(today.toEpochDay())!!
        assertEquals(45.0, t.hrvMs!!, 0.0); assertEquals(96.0, t.spo2Pct!!, 0.0); assertEquals(14.5, t.respRate!!, 0.0)
        // Sleep: 23:00–07:00 with 20 min awake → 7h40 asleep, stages kept
        assertEquals(460.0, t.sleepMinutes!!, 0.01); assertEquals(90.0, t.sleepDeepMin!!, 0.01); assertEquals(90.0, t.sleepRemMin!!, 0.01)
        assertEquals(20.0, t.sleepAwakeMin!!, 0.01)
        assertEquals(setOf(fitbit), HealthSyncer.decodeSources(y.sources)[HcDataType.STEPS])
        val run = c.db.sessions().byHcId("run1")!!
        assertEquals(142.0, run.avgHr!!, 0.0); assertEquals(168.0, run.maxHr!!, 0.0); assertEquals(520.0, run.kcal!!, 0.0)
        assertEquals(6400.0, run.distanceM!!, 0.0); assertEquals(fitbit, run.dataOrigin); assertEquals("Run", run.title)
    }

    @Test fun `unavailable metrics stay null, never zero`() = runBlocking {
        hc.granted = setOf(HcDataType.STEPS)
        c.healthSyncer.sync()
        val t = c.db.energy().get(today.toEpochDay())!!
        assertEquals(5200L, t.steps)
        assertNull(t.avgHr); assertNull(t.hrvMs); assertNull(t.sleepMinutes); assertNull(t.distanceM); assertNull(t.hcTotalKcal)
        val m = model { it.s.energy.isNotEmpty() }
        val settings = AppSettings()
        assertNull(MetricValues.value(Metric.HEART_RATE, m, today, settings))
        assertNull(MetricValues.value(Metric.DISTANCE, m, today, settings))
        assertEquals(0, dataCheck(HcDataType.HEART_RATE, m, emptyList(), emptyList()).days)
        assertEquals(2, dataCheck(HcDataType.STEPS, m, emptyList(), emptyList()).days)
    }

    @Test fun `later syncs are incremental via change tokens`() = runBlocking {
        c.healthSyncer.sync()
        assertEquals(today.minusDays(29) to today, hc.aggregateCalls.last())
        assertNotNull(c.db.syncMeta().get()!!.changesToken)
        c.healthSyncer.sync()
        assertEquals(today.minusDays(1) to today, hc.aggregateCalls.last())           // only today + yesterday
        assertTrue(c.db.syncMeta().get()!!.lastSyncMode!!.startsWith("Incremental"))
        hc.pendingUpserts = listOf(at(today.minusDays(5), 9))                        // band uploaded an old day
        c.healthSyncer.sync()
        assertEquals(today.minusDays(5) to today, hc.aggregateCalls.last())
        hc.tokenExpired = true
        c.healthSyncer.sync()
        assertEquals(today.minusDays(29) to today, hc.aggregateCalls.last())          // expired → full re-sync
    }

    @Test fun `a permission change invalidates the token`() = runBlocking {
        c.healthSyncer.sync(); c.healthSyncer.sync()
        hc.granted = hc.granted - HcDataType.HEART_RATE
        c.healthSyncer.sync()
        assertEquals(today.minusDays(29) to today, hc.aggregateCalls.last())
        assertNull("revoked data isn't shown as current", c.db.energy().get(today.toEpochDay())!!.avgHr)
    }

    @Test fun `temporarily missing data keeps the last valid reading, deletions clear it`() = runBlocking {
        c.healthSyncer.sync()
        hc.aggregates = hc.aggregates - today                       // band hasn't synced yet today
        c.healthSyncer.sync()
        val t = c.db.energy().get(today.toEpochDay())!!
        assertEquals(5200L, t.steps); assertEquals(74.0, t.avgHr!!, 0.0)
        hc.pendingDeletes = listOf("some-steps-record")             // user deleted data in Health Connect
        c.healthSyncer.sync()
        assertNull(c.db.energy().get(today.toEpochDay())!!.steps)
    }

    @Test fun `updated records replace their old version without duplicates`() = runBlocking {
        c.healthSyncer.sync()
        hc.stats = mapOf("run1" to SessionStats(avgHr = 145.0, maxHr = 170.0, totalKcal = 530.0, activeKcal = null, distanceM = 6500.0))
        hc.pendingUpserts = listOf(at(today.minusDays(1), 18))
        repeat(2) { c.healthSyncer.sync() }
        assertEquals(1, c.db.sessions().count())
        assertEquals(145.0, c.db.sessions().byHcId("run1")!!.avgHr!!, 0.0)
    }

    @Test fun `calorie engine uses wearable data without double counting`() = runBlocking {
        c.healthSyncer.sync()
        val m = model { it.s.energy.isNotEmpty() }
        val y = m.summary(today.minusDays(1))
        assertEquals(ExpenditureSource.HEALTH_CONNECT_TOTAL, y.expenditure.source)
        assertEquals(3150.0, y.expenditure.projectedDayKcal, 0.01)      // active 900 NOT added on top of total
        assertEquals(2250.0, y.expenditure.baselineKcal, 0.01)          // measured basal
        assertEquals(900.0, y.expenditure.activeKcal, 0.01)
        assertTrue(y.expenditure.lines.any { it.label.startsWith("Run") && it.kcal == null })   // watch workout excluded
        assertTrue(y.expenditure.lines.any { it.label.startsWith("Steps") && it.kcal == null }) // steps excluded
        assertEquals(900.0, MetricValues.value(Metric.ACTIVE_KCAL, m, today.minusDays(1), AppSettings())!!, 0.01)
        // Conservative adjustment + budget cap from the profile
        c.profile.update { it.copy(wearableAdjustmentPct = -10, maxActivityBonusKcal = 300) }
        val m2 = model { it.s.profile.wearableAdjustmentPct == -10 }
        val y2 = m2.summary(today.minusDays(1))
        assertEquals(3150.0 - 90.0, y2.expenditure.projectedDayKcal, 0.01)
        assertTrue(y2.budget.cappedByActivityLimit)
    }

    @Test fun `watch workouts overlapping a FORMA workout count once`() = runBlocking {
        c.healthSyncer.sync()
        val y = today.minusDays(1)
        val sid = c.workouts.ensureSession(y, "MON")
        val s = c.db.workouts().sessionById(sid)!!
        c.db.workouts().updateSession(s.copy(startedAt = at(y, 18, 5).toEpochMilli(), completedAt = at(y, 18, 50).toEpochMilli()))
        val m = model { it.s.workouts.any { w -> w.completedAt != null } }
        assertEquals(1, MetricValues.workoutsInWeek(m, com.forma.engine.TrendAnalyzer.weekStart(y)))
    }

    @Test fun `wearable data persists across restarts`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("forma.db")
        val c1 = TestEnv.container(ctx, inMemory = false); c1.initialize()
        c1.healthSyncer.sync(); c1.db.close()
        val db2 = FormaDatabase.create(ctx)
        val y = db2.energy().get(today.minusDays(1).toEpochDay())!!
        assertEquals(58.0, y.restingHr!!, 0.0); assertEquals(11200L, y.steps)
        assertNotNull(db2.syncMeta().get()!!.lastSuccessAt)
        db2.close(); ctx.deleteDatabase("forma.db")
        Unit
    }

    @Test fun `database migrates from version 3 keeping synced data`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("migrate4.db")
        val schema = Json.parseToJsonElement(File("schemas/com.forma.app.data.db.FormaDatabase/3.json").readText()).jsonObject["database"]!!.jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath("migrate4.db").apply { parentFile?.mkdirs() }, null)
        schema["entities"]!!.jsonArray.forEach { e ->
            val o = e.jsonObject; val t = o["tableName"]!!.jsonPrimitive.content
            sql.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t))
            o["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t)) }
        }
        schema["setupQueries"]!!.jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }
        sql.execSQL("INSERT INTO daily_energy (epochDay, hcTotalKcal, steps, sleepMinutes, syncedAt) VALUES (20000, 3000, 9000, 420, 1)")
        sql.execSQL("INSERT INTO profile (id, displayName, sex, ageYears, heightInches, startWeightLb, currentWeightLb, mainGoal, secondaryGoal, skiDateEpochDay, gym, trainingDays, workoutMinutes, baselineActivityFactor, maintenanceActivityFactor, desiredDeficitKcal, minimumBudgetKcal, maxRepaymentPerDay, budgetMode, proteinTargetG, programStartEpochDay, updatedAt) VALUES (1,'','MALE',22,75,260,258,'a','b',20819,'LA Fitness','SUNDAY',55,1.2,1.375,500,2000,200,'DYNAMIC',180,20000,1)")
        sql.version = 3; sql.close()
        val db = androidx.room.Room.databaseBuilder(ctx, FormaDatabase::class.java, "migrate4.db")
            .addMigrations(FormaDatabase.MIGRATION_1_2, FormaDatabase.MIGRATION_2_3, FormaDatabase.MIGRATION_3_4, FormaDatabase.MIGRATION_4_5, FormaDatabase.MIGRATION_5_6, FormaDatabase.MIGRATION_6_7, FormaDatabase.MIGRATION_7_8).build()
        runBlocking {
            val e = db.energy().get(20000)!!
            assertEquals(9000L, e.steps); assertNull(e.avgHr)
            val p = db.profile().get()!!
            assertEquals(10000, p.stepGoal); assertEquals(0, p.wearableAdjustmentPct); assertEquals(500, p.maxActivityBonusKcal)
        }
        db.close(); ctx.deleteDatabase("migrate4.db")
    }
}
