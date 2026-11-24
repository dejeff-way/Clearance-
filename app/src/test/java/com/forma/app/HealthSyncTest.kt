package com.forma.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.di.AppContainer
import com.forma.app.health.DailyAggregate
import com.forma.app.health.HcAvailability
import com.forma.app.health.HcDataType
import com.forma.app.health.HcExerciseSession
import com.forma.app.health.HcWeight
import com.forma.app.health.SyncOutcome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(application = TestFormaApp::class, sdk = [35])
class HealthSyncTest {
    private lateinit var c: AppContainer
    private lateinit var hc: FakeHealth
    private val today = LocalDate.of(2026, 11, 24)
    private val zone = TestEnv.time.zone()

    private fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant()

    @Before fun setUp() = runBlocking {
        TestEnv.time = FixedTime(); hc = FakeHealth(); TestEnv.health = hc
        c = TestEnv.container(ApplicationProvider.getApplicationContext())
        c.initialize()
        hc.aggregates = mapOf(
            today.minusDays(1) to DailyAggregate(today.minusDays(1), 3150.0, 820.0, 11200, 50.0),
            today to DailyAggregate(today, 1650.0, 300.0, 5200, null),
        )
        hc.sessions = listOf(HcExerciseSession("s1", at(today.minusDays(1), 18), at(today.minusDays(1), 19), 70, "Strength"))
        hc.weights = listOf(HcWeight("w1", at(today, 7), 258.8), HcWeight("w2", at(today.minusDays(2), 7), 259.6))
    }

    @After fun tearDown() { c.db.close() }

    @Test fun `sync imports aggregates sessions and weights`() = runBlocking {
        val r = c.healthSyncer.sync()
        assertTrue(r is SyncOutcome.Success)
        assertEquals(3150.0, c.db.energy().get(today.minusDays(1).toEpochDay())!!.hcTotalKcal!!, 0.0)
        assertEquals(11200L, c.db.energy().get(today.minusDays(1).toEpochDay())!!.steps)
        assertEquals(1, c.db.sessions().count())
        assertEquals(2, c.db.body().weightCount())
        assertEquals(258.8, c.db.profile().get()!!.currentWeightLb, 0.0)
        val meta = c.db.syncMeta().get()!!
        assertTrue(meta.lastSuccessAt != null && meta.lastError == null)
        assertEquals(HcDataType.entries.size, meta.grantedPermissions.split(",").size)
    }

    @Test fun `re-syncing never creates duplicates`() = runBlocking {
        repeat(3) { c.healthSyncer.sync() }
        assertEquals(1, c.db.sessions().count())
        assertEquals(2, c.db.body().weightCount())
        assertEquals(30, c.db.energy().all().size) // first sync window; later syncs upsert the same days
    }

    @Test fun `records deleted in health connect are removed from the synced window`() = runBlocking {
        c.healthSyncer.sync()
        hc.weights = hc.weights.filter { it.id == "w1" }
        hc.sessions = emptyList()
        c.healthSyncer.sync()
        assertEquals(1, c.db.body().weightCount())
        assertEquals(0, c.db.sessions().count())
    }

    @Test fun `manual weights are never touched by sync`() = runBlocking {
        c.body.logWeight(261.0)
        hc.weights = emptyList()
        c.healthSyncer.sync()
        assertEquals(1, c.db.body().weightCount())
        assertEquals("MANUAL", c.db.body().allWeights().single().source)
    }

    @Test fun `only granted types are stored`() = runBlocking {
        hc.granted = setOf(HcDataType.STEPS)
        c.healthSyncer.sync()
        val y = c.db.energy().get(today.minusDays(1).toEpochDay())!!
        assertNull(y.hcTotalKcal); assertNull(y.hcActiveKcal)
        assertEquals(11200L, y.steps)
        assertEquals(0, c.db.sessions().count())
        assertEquals(0, c.db.body().weightCount())
    }

    @Test fun `permission denied state is reported honestly`() = runBlocking {
        hc.granted = emptySet()
        assertEquals(SyncOutcome.NoPermissions, c.healthSyncer.sync())
        assertTrue(c.db.energy().all().isEmpty())
        assertEquals("No permissions granted", c.db.syncMeta().get()!!.lastError)
    }

    @Test fun `unavailable state is reported and nothing is faked`() = runBlocking {
        hc.availability = HcAvailability.UNAVAILABLE
        assertEquals(SyncOutcome.Unavailable, c.healthSyncer.sync())
        assertTrue(c.db.energy().all().isEmpty())
        assertNull(c.db.syncMeta().get()!!.lastSuccessAt)
    }

    @Test fun `revoked permission mid-read is caught`() = runBlocking {
        hc.throwOnRead = SecurityException("revoked")
        val r = c.healthSyncer.sync()
        assertTrue(r is SyncOutcome.Failed)
        assertTrue(c.db.syncMeta().get()!!.lastError!!.startsWith("Permission denied"))
    }

    @Test fun `synced total becomes the expenditure source without double counting`() = runBlocking {
        c.healthSyncer.sync()
        // Wait for the live model to pick up the sync
        var m = c.energyModel.value
        repeat(100) { if (m?.s?.energy?.isNotEmpty() == true) return@repeat; Thread.sleep(50); m = c.energyModel.value }
        val y = m!!.summary(today.minusDays(1))
        assertEquals(com.forma.engine.ExpenditureSource.HEALTH_CONNECT_TOTAL, y.expenditure.source)
        assertEquals(3150.0, y.expenditure.projectedDayKcal, 0.01) // HC session + steps excluded
        assertEquals(2650, y.budget.finalBudget)
    }
}
