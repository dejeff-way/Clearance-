package com.forma.app

import android.content.Context
import com.forma.app.data.db.FormaDatabase
import com.forma.app.di.AppContainer
import com.forma.app.health.DailyAggregate
import com.forma.app.health.HcAvailability
import com.forma.app.health.HcDataType
import com.forma.app.health.HcExerciseSession
import com.forma.app.health.HcSleep
import com.forma.app.health.HcWeight
import com.forma.app.health.HealthDataSource
import com.forma.app.util.TimeProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Fixed clock: Tuesday 24 Nov 2026, 13:00 New York. */
class FixedTime(var now: ZonedDateTime = ZonedDateTime.of(2026, 11, 24, 13, 0, 0, 0, ZoneId.of("America/New_York"))) : TimeProvider() {
    override fun zone(): ZoneId = now.zone
    override fun nowMillis(): Long = now.toInstant().toEpochMilli()
}

/** Scriptable Health Connect for tests. Never touches the real SDK. */
class FakeHealth : HealthDataSource {
    var availability = HcAvailability.AVAILABLE
    var granted: Set<HcDataType> = HcDataType.entries.toSet()
    var aggregates: Map<LocalDate, DailyAggregate> = emptyMap()
    var sessions: List<HcExerciseSession> = emptyList()
    var weights: List<HcWeight> = emptyList()
    var sleeps: List<HcSleep> = emptyList()
    var throwOnRead: Exception? = null
    var revoked = false

    override fun availability() = availability
    override suspend fun grantedTypes() = if (availability == HcAvailability.AVAILABLE) granted else emptySet()
    override suspend fun backgroundReadAvailable() = false
    override suspend fun backgroundReadGranted() = false
    override suspend fun dailyAggregates(from: LocalDate, toInclusive: LocalDate, types: Set<HcDataType>): List<DailyAggregate> {
        throwOnRead?.let { throw it }
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(toInclusive) }.map { d ->
            val a = aggregates[d]
            DailyAggregate(
                d,
                a?.totalKcal.takeIf { HcDataType.TOTAL_CALORIES in types },
                a?.activeKcal.takeIf { HcDataType.ACTIVE_CALORIES in types },
                a?.steps.takeIf { HcDataType.STEPS in types },
                a?.exerciseMinutes.takeIf { HcDataType.EXERCISE in types },
            )
        }.toList()
    }
    override suspend fun exerciseSessions(from: Instant, to: Instant) = sessions.filter { it.start >= from && it.start <= to }
    override suspend fun weights(from: Instant, to: Instant) = weights.filter { it.time >= from && it.time <= to }
    override suspend fun sleep(from: Instant, to: Instant) = sleeps.filter { it.end >= from && it.end <= to }
    override suspend fun revokeAll() { revoked = true; granted = emptySet() }
}

/** Global test environment read by [TestFormaApp] when Robolectric creates the application. */
object TestEnv {
    var health = FakeHealth()
    var time = FixedTime()

    fun container(context: Context, inMemory: Boolean = true, tickMillis: Long = 250) =
        AppContainer(context, FormaDatabase.create(context, inMemory), time, health, tickMillis = tickMillis)
}

open class TestFormaApp : FormaApp() {
    override fun createContainer(): AppContainer {
        // Fresh fakes per application instance (Robolectric creates one per test). UI tests start "unavailable".
        TestEnv.health = FakeHealth().apply { availability = HcAvailability.UNAVAILABLE }
        TestEnv.time = FixedTime()
        return TestEnv.container(this, tickMillis = 3_600_000)
    }
}
