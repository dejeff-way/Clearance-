package com.forma.app.health

import com.forma.app.data.db.DailyEnergyEntity
import com.forma.app.data.db.ExerciseSessionEntity
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.db.SyncMetaEntity
import com.forma.app.data.db.WeightEntity
import com.forma.app.util.TimeProvider
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate

sealed interface SyncOutcome {
    data class Success(val days: Int, val sessions: Int, val weights: Int) : SyncOutcome
    data object Unavailable : SyncOutcome
    data object NoPermissions : SyncOutcome
    data class Failed(val message: String) : SyncOutcome
}

/**
 * Pulls Health Connect data into Room. Idempotent: records are keyed by their Health Connect id (unique index), so
 * re-syncing never creates duplicates, and records deleted in Health Connect are removed from the synced window.
 */
class HealthSyncer(
    private val db: FormaDatabase,
    private val source: HealthDataSource,
    private val time: TimeProvider,
) {
    private val mutex = Mutex()

    companion object {
        /** Default read window without the history permission is 30 days before the first grant. */
        const val FIRST_SYNC_DAYS = 30
        const val ROUTINE_SYNC_DAYS = 7
    }

    suspend fun sync(days: Int? = null): SyncOutcome = mutex.withLock {
        val now = time.nowMillis()
        val meta = db.syncMeta().get() ?: SyncMetaEntity()

        if (source.availability() != HcAvailability.AVAILABLE) {
            db.syncMeta().upsert(meta.copy(lastAttemptAt = now, lastError = "Health Connect is not available on this device"))
            return SyncOutcome.Unavailable
        }
        val granted = try { source.grantedTypes() } catch (e: Exception) {
            db.syncMeta().upsert(meta.copy(lastAttemptAt = now, lastError = e.message ?: "Couldn't read permissions"))
            return SyncOutcome.Failed(e.message ?: "Couldn't read permissions")
        }
        if (granted.isEmpty()) {
            db.syncMeta().upsert(meta.copy(lastAttemptAt = now, grantedPermissions = "", lastError = "No permissions granted"))
            return SyncOutcome.NoPermissions
        }

        val window = days ?: if (meta.lastSuccessAt == null) FIRST_SYNC_DAYS else ROUTINE_SYNC_DAYS
        val today = time.today()
        val from = today.minusDays((window - 1).toLong())
        val fromInstant = Instant.ofEpochMilli(time.startOfDayMillis(from))
        val toInstant = Instant.ofEpochMilli(now)

        return try {
            val aggregates = source.dailyAggregates(from, today, granted)
            val sleepByDay: Map<LocalDate, Double> = if (HcDataType.SLEEP in granted) {
                source.sleep(fromInstant.minusSeconds(12 * 3600), toInstant)
                    .groupBy { time.dayOf(it.end.toEpochMilli()) }
                    .mapValues { (_, v) -> v.sumOf { (it.end.toEpochMilli() - it.start.toEpochMilli()) / 60_000.0 } }
            } else emptyMap()
            val sessions = if (HcDataType.EXERCISE in granted) source.exerciseSessions(fromInstant, toInstant) else null
            val weights = if (HcDataType.WEIGHT in granted) source.weights(fromInstant, toInstant) else null

            var sessionCount = 0
            var weightCount = 0
            db.withTransaction {
                aggregates.forEach { a ->
                    db.energy().upsert(
                        DailyEnergyEntity(
                            epochDay = a.date.toEpochDay(),
                            hcTotalKcal = a.totalKcal, hcActiveKcal = a.activeKcal, steps = a.steps,
                            exerciseMinutes = a.exerciseMinutes,
                            sleepMinutes = sleepByDay[a.date],
                            syncedAt = now,
                        ),
                    )
                }
                if (sessions != null) {
                    val ids = sessions.map { it.id }.toSet()
                    db.sessions().hcInRange(from.toEpochDay(), today.toEpochDay())
                        .filter { it.hcId !in ids }.forEach { db.sessions().delete(it.id) }
                    sessions.forEach { s ->
                        val minutes = (s.end.toEpochMilli() - s.start.toEpochMilli()) / 60_000.0
                        val row = ExerciseSessionEntity(
                            epochDay = time.dayOf(s.start.toEpochMilli()).toEpochDay(),
                            source = "HEALTH_CONNECT",
                            title = s.title?.takeIf { it.isNotBlank() } ?: ExerciseTypes.name(s.exerciseType),
                            exerciseType = s.exerciseType, startTime = s.start.toEpochMilli(),
                            endTime = s.end.toEpochMilli(), durationMinutes = minutes,
                            met = ExerciseTypes.met(s.exerciseType), recordedByDevice = true, hcId = s.id,
                        )
                        val existing = db.sessions().byHcId(s.id)
                        if (existing == null) db.sessions().insert(row) else db.sessions().update(row.copy(id = existing.id, createdAt = existing.createdAt))
                        sessionCount++
                    }
                }
                if (weights != null) {
                    val ids = weights.map { it.id }.toSet()
                    db.body().hcWeightsInRange(from.toEpochDay(), today.toEpochDay())
                        .filter { it.hcId !in ids }.forEach { db.body().deleteWeight(it.id) }
                    weights.forEach { w ->
                        val row = WeightEntity(
                            epochDay = time.dayOf(w.time.toEpochMilli()).toEpochDay(),
                            measuredAt = w.time.toEpochMilli(), weightLb = w.lb, source = "HEALTH_CONNECT", hcId = w.id,
                        )
                        val existing = db.body().weightByHcId(w.id)
                        if (existing == null) db.body().insertWeight(row) else db.body().updateWeight(row.copy(id = existing.id, note = existing.note))
                        weightCount++
                    }
                    db.body().latestWeight()?.let { latest ->
                        db.profile().get()?.let { p -> db.profile().upsert(p.copy(currentWeightLb = latest.weightLb)) }
                    }
                }
                db.syncMeta().upsert(
                    meta.copy(
                        lastAttemptAt = now, lastSuccessAt = now, lastError = null,
                        grantedPermissions = granted.joinToString(",") { it.name },
                        lastRecordCount = aggregates.size + sessionCount + weightCount,
                    ),
                )
            }
            SyncOutcome.Success(aggregates.size, sessionCount, weightCount)
        } catch (e: SecurityException) {
            db.syncMeta().upsert(meta.copy(lastAttemptAt = now, lastError = "Permission denied: ${e.message ?: "revoked"}"))
            SyncOutcome.Failed("Permission denied")
        } catch (e: Exception) {
            db.syncMeta().upsert(meta.copy(lastAttemptAt = now, lastError = e.message ?: e.javaClass.simpleName))
            SyncOutcome.Failed(e.message ?: "Sync failed")
        }
    }

    /** Called after the user disconnects: clears permission metadata but keeps already-imported history. */
    suspend fun markDisconnected() {
        val meta = db.syncMeta().get() ?: SyncMetaEntity()
        db.syncMeta().upsert(meta.copy(grantedPermissions = "", backgroundSyncEnabled = false, lastError = "Disconnected"))
    }

    suspend fun setBackground(enabled: Boolean) {
        val meta = db.syncMeta().get() ?: SyncMetaEntity()
        db.syncMeta().upsert(meta.copy(backgroundSyncEnabled = enabled))
    }
}
