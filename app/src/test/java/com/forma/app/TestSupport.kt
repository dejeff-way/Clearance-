package com.forma.app

import android.content.Context
import com.forma.app.data.db.FormaDatabase
import com.forma.app.di.AppContainer
import com.forma.app.health.DailyAggregate
import com.forma.app.health.HcAvailability
import com.forma.app.health.HcDataType
import com.forma.app.health.HcChanges
import com.forma.app.health.HcExerciseSession
import com.forma.app.health.HcSample
import com.forma.app.health.SessionStats
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
    var stats: Map<String, SessionStats> = emptyMap()
    var weights: List<HcWeight> = emptyList()
    var sleeps: List<HcSleep> = emptyList()
    var samples: Map<HcDataType, List<HcSample>> = emptyMap()
    var throwOnRead: Exception? = null
    var revoked = false

    // Change log simulation
    var tokenCounter = 0
    var pendingUpserts: List<Instant> = emptyList()
    var pendingDeletes: List<String> = emptyList()
    var tokenExpired = false
    val aggregateCalls = mutableListOf<Pair<LocalDate, LocalDate>>()

    override fun availability() = availability
    override suspend fun grantedTypes() = if (availability == HcAvailability.AVAILABLE) granted else emptySet()
    override suspend fun backgroundReadAvailable() = false
    override suspend fun backgroundReadGranted() = false
    override suspend fun dailyAggregates(from: LocalDate, toInclusive: LocalDate, types: Set<HcDataType>): List<DailyAggregate> {
        throwOnRead?.let { throw it }
        aggregateCalls += from to toInclusive
        fun <T> g(t: HcDataType, v: T?) = v.takeIf { t in types }
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(toInclusive) }.map { d ->
            val a = aggregates[d]
            DailyAggregate(
                d, g(HcDataType.TOTAL_CALORIES, a?.totalKcal), g(HcDataType.ACTIVE_CALORIES, a?.activeKcal), g(HcDataType.STEPS, a?.steps),
                g(HcDataType.EXERCISE, a?.exerciseMinutes), g(HcDataType.BASAL_CALORIES, a?.basalKcal), g(HcDataType.DISTANCE, a?.distanceM),
                g(HcDataType.FLOORS, a?.floors), g(HcDataType.HEART_RATE, a?.avgHr), g(HcDataType.HEART_RATE, a?.minHr),
                g(HcDataType.HEART_RATE, a?.maxHr), g(HcDataType.RESTING_HEART_RATE, a?.restingHr),
                a?.origins.orEmpty().filterKeys { it in types },
            )
        }.toList()
    }
    override suspend fun exerciseSessions(from: Instant, to: Instant) = sessions.filter { it.start >= from && it.start <= to }
    override suspend fun sessionStats(start: Instant, end: Instant, types: Set<HcDataType>) =
        sessions.firstOrNull { it.start == start }?.let { stats[it.id] } ?: SessionStats(null, null, null, null, null)
    override suspend fun weights(from: Instant, to: Instant) = weights.filter { it.time >= from && it.time <= to }
    override suspend fun sleep(from: Instant, to: Instant) = sleeps.filter { it.end >= from && it.end <= to }
    override suspend fun samples(type: HcDataType, from: Instant, to: Instant) = samples[type].orEmpty().filter { it.time >= from && it.time <= to }
    override suspend fun changesToken(types: Set<HcDataType>) = "token-${++tokenCounter}"
    override suspend fun changes(token: String): HcChanges {
        if (tokenExpired) return HcChanges(emptyList(), emptyList(), token, expired = true)
        val r = HcChanges(pendingUpserts, pendingDeletes, "token-${++tokenCounter}", expired = false)
        pendingUpserts = emptyList(); pendingDeletes = emptyList()
        return r
    }
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
