package com.forma.app.health

import androidx.room.withTransaction
import com.forma.app.data.db.DailyEnergyEntity
import com.forma.app.data.db.ExerciseSessionEntity
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.db.SyncMetaEntity
import com.forma.app.data.db.WeightEntity
import com.forma.app.util.TimeProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate

sealed interface SyncOutcome {
    data class Success(val days: Int, val sessions: Int, val weights: Int, val mode: String = "") : SyncOutcome
    data object Unavailable : SyncOutcome
    data object NoPermissions : SyncOutcome
    data class Failed(val message: String) : SyncOutcome
}

/**
 * Pulls Health Connect data into Room.
 *
 * - **Incremental:** after the first full sync, Health Connect's change log (change tokens) tells FORMA which days
 *   changed, so only those days are re-read. An expired token or a permission change triggers a full re-sync.
 * - **Idempotent:** records are keyed by their Health Connect id, so re-syncing never duplicates anything; updated
 *   records replace their old version and deleted records are removed.
 * - **Keeps last good values:** if Health Connect momentarily returns nothing for a day (e.g. the band hasn't synced
 *   yet), previously imported readings are kept rather than replaced with blanks. Values are only cleared when the
 *   permission is revoked or Health Connect reports deletions.
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
        /** Today and yesterday are always re-read so late band syncs are picked up. */
        const val ALWAYS_REFRESH_DAYS = 2
        private val SAMPLE_TYPES = listOf(HcDataType.HRV, HcDataType.OXYGEN_SATURATION, HcDataType.RESPIRATORY_RATE, HcDataType.BODY_FAT)

        fun encodeSources(m: Map<HcDataType, Set<String>>): String? =
            m.filterValues { it.isNotEmpty() }.entries.joinToString(";") { (t, s) -> "${t.name}:${s.sorted().joinToString("|")}" }.ifEmpty { null }

        fun decodeSources(s: String?): Map<HcDataType, Set<String>> =
            s.orEmpty().split(';').filter { it.contains(':') }.mapNotNull { part ->
                val (t, pk) = part.split(':', limit = 2)
                HcDataType.entries.firstOrNull { it.name == t }?.let { it to pk.split('|').filter(String::isNotBlank).toSet() }
            }.toMap()
    }

    /** Syncs only if the last successful sync is older than [maxAgeMillis] (used when screens open). */
    suspend fun syncIfStale(maxAgeMillis: Long = 2 * 60_000): SyncOutcome? {
        val last = db.syncMeta().get()?.lastSuccessAt
        if (last != null && time.nowMillis() - last < maxAgeMillis) return null
        if (source.availability() != HcAvailability.AVAILABLE) return null
        if (runCatching { source.grantedTypes() }.getOrDefault(emptySet()).isEmpty()) return null
        return sync()
    }

    /** True while a sync is running (drives the "Syncing…" indicator). */
    val running = kotlinx.coroutines.flow.MutableStateFlow(false)

    suspend fun sync(days: Int? = null): SyncOutcome {
        running.value = true
        return try { doSync(days) } finally { running.value = false }
    }

    private suspend fun doSync(days: Int?): SyncOutcome = mutex.withLock {
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
            db.syncMeta().upsert(meta.copy(lastAttemptAt = now, grantedPermissions = "", lastError = "No permissions granted",
                changesToken = null, tokenTypes = ""))
            return SyncOutcome.NoPermissions
        }
        val grantedKey = granted.map { it.name }.sorted().joinToString(",")
        val today = time.today()
        val zone = time.zone()

        return try {
            // ── Decide the window ─────────────────────────────
            var from: LocalDate
            var deletedIds: List<String> = emptyList()
            val newToken: String
            val mode: String
            val canIncrement = days == null && meta.lastSuccessAt != null && meta.changesToken != null && meta.tokenTypes == grantedKey
            val changes = if (canIncrement) runCatching { source.changes(meta.changesToken!!) }.getOrNull() else null
            if (changes != null && !changes.expired) {
                deletedIds = changes.deletedIds
                val earliestChange = changes.upsertTimes.minOrNull()?.let { time.dayOf(it.toEpochMilli()) }
                from = listOfNotNull(today.minusDays((ALWAYS_REFRESH_DAYS - 1).toLong()), earliestChange).min()
                if (deletedIds.isNotEmpty()) from = minOf(from, today.minusDays((ROUTINE_SYNC_DAYS - 1).toLong()))
                from = maxOf(from, today.minusDays((FIRST_SYNC_DAYS - 1).toLong()))
                newToken = changes.nextToken
                mode = "Incremental"
            } else {
                // Take the token first so nothing written during the read is missed next time.
                newToken = source.changesToken(granted)
                val window = days ?: if (meta.lastSuccessAt == null || changes?.expired == true || meta.tokenTypes != grantedKey) FIRST_SYNC_DAYS else ROUTINE_SYNC_DAYS
                from = today.minusDays((window - 1).toLong())
                mode = "Full"
            }
            val fromInstant = Instant.ofEpochMilli(time.startOfDayMillis(from))
            val toInstant = Instant.ofEpochMilli(now)

            // ── Read ─────────────────────────────────────────
            val aggregates = source.dailyAggregates(from, today, granted)
            val sleeps = if (HcDataType.SLEEP in granted) source.sleep(fromInstant.minusSeconds(12 * 3600), toInstant) else null
            val sessions = if (HcDataType.EXERCISE in granted) source.exerciseSessions(fromInstant, toInstant) else null
            val weights = if (HcDataType.WEIGHT in granted) source.weights(fromInstant, toInstant) else null
            val samples = SAMPLE_TYPES.filter { it in granted }.associateWith { source.samples(it, fromInstant, toInstant) }
            val stats = sessions?.associate { s -> s.id to runCatching { source.sessionStats(s.start, s.end, granted) }.getOrNull() }.orEmpty()

            data class SleepDay(val total: Double, val deep: Double?, val light: Double?, val rem: Double?, val awake: Double?)
            val sleepByDay: Map<LocalDate, SleepDay> = sleeps.orEmpty().groupBy { time.dayOf(it.end.toEpochMilli()) }.mapValues { (_, list) ->
                fun mins(a: Instant, b: Instant) = (b.toEpochMilli() - a.toEpochMilli()) / 60_000.0
                fun stage(st: SleepStage) = list.flatMap { it.stages }.filter { it.stage == st }.sumOf { mins(it.start, it.end) }.takeIf { it > 0 }
                val hasStages = list.any { it.stages.isNotEmpty() }
                val total = list.sumOf { s ->
                    if (s.stages.isEmpty()) mins(s.start, s.end) else s.stages.filter { it.stage != SleepStage.AWAKE }.sumOf { mins(it.start, it.end) }
                }
                SleepDay(total, if (hasStages) stage(SleepStage.DEEP) else null, if (hasStages) stage(SleepStage.LIGHT) else null,
                    if (hasStages) stage(SleepStage.REM) else null, if (hasStages) stage(SleepStage.AWAKE) else null)
            }
            val sampleByDay: Map<HcDataType, Map<LocalDate, Double>> = samples.mapValues { (t, list) ->
                list.groupBy { time.dayOf(it.time.toEpochMilli()) }.mapValues { (_, v) ->
                    if (t == HcDataType.BODY_FAT) v.maxBy { it.time }.value else v.map { it.value }.average()
                }
            }
            val extraOrigins = HashMap<LocalDate, MutableMap<HcDataType, Set<String>>>()
            sleeps.orEmpty().forEach { s -> s.origin?.let { o -> extraOrigins.getOrPut(time.dayOf(s.end.toEpochMilli())) { mutableMapOf() }.merge(HcDataType.SLEEP, setOf(o)) { a, b -> a + b } } }
            samples.forEach { (t, list) -> list.forEach { s -> s.origin?.let { o -> extraOrigins.getOrPut(time.dayOf(s.time.toEpochMilli())) { mutableMapOf() }.merge(t, setOf(o)) { a, b -> a + b } } } }

            var sessionCount = 0
            var weightCount = 0
            db.withTransaction {
                // ── Daily rows: merge, keeping last good values ──
                val deletionsSeen = deletedIds.isNotEmpty()
                fun <T> keep(new: T?, old: T?, type: HcDataType): T? = when {
                    type !in granted -> null                     // permission revoked → don't show stale data as current
                    new != null -> new
                    deletionsSeen -> null                        // something was deleted; trust the fresh read
                    else -> old                                  // temporarily missing → keep the last valid reading
                }
                aggregates.forEach { a ->
                    val d = a.date
                    val old = db.energy().get(d.toEpochDay())
                    val sl = sleepByDay[d]
                    val origins = (decodeSources(old?.sources).filterKeys { it in granted }) + a.origins + extraOrigins[d].orEmpty()
                    db.energy().upsert(
                        DailyEnergyEntity(
                            epochDay = d.toEpochDay(),
                            hcTotalKcal = keep(a.totalKcal, old?.hcTotalKcal, HcDataType.TOTAL_CALORIES),
                            hcActiveKcal = keep(a.activeKcal, old?.hcActiveKcal, HcDataType.ACTIVE_CALORIES),
                            steps = keep(a.steps, old?.steps, HcDataType.STEPS),
                            exerciseMinutes = keep(a.exerciseMinutes, old?.exerciseMinutes, HcDataType.EXERCISE),
                            sleepMinutes = keep(sl?.total, old?.sleepMinutes, HcDataType.SLEEP),
                            syncedAt = now,
                            hcBasalKcal = keep(a.basalKcal, old?.hcBasalKcal, HcDataType.BASAL_CALORIES),
                            distanceM = keep(a.distanceM, old?.distanceM, HcDataType.DISTANCE),
                            floors = keep(a.floors, old?.floors, HcDataType.FLOORS),
                            avgHr = keep(a.avgHr, old?.avgHr, HcDataType.HEART_RATE),
                            minHr = keep(a.minHr, old?.minHr, HcDataType.HEART_RATE),
                            maxHr = keep(a.maxHr, old?.maxHr, HcDataType.HEART_RATE),
                            restingHr = keep(a.restingHr, old?.restingHr, HcDataType.RESTING_HEART_RATE),
                            hrvMs = keep(sampleByDay[HcDataType.HRV]?.get(d), old?.hrvMs, HcDataType.HRV),
                            spo2Pct = keep(sampleByDay[HcDataType.OXYGEN_SATURATION]?.get(d), old?.spo2Pct, HcDataType.OXYGEN_SATURATION),
                            respRate = keep(sampleByDay[HcDataType.RESPIRATORY_RATE]?.get(d), old?.respRate, HcDataType.RESPIRATORY_RATE),
                            bodyFatPct = keep(sampleByDay[HcDataType.BODY_FAT]?.get(d), old?.bodyFatPct, HcDataType.BODY_FAT),
                            sleepDeepMin = keep(sl?.deep, old?.sleepDeepMin, HcDataType.SLEEP),
                            sleepLightMin = keep(sl?.light, old?.sleepLightMin, HcDataType.SLEEP),
                            sleepRemMin = keep(sl?.rem, old?.sleepRemMin, HcDataType.SLEEP),
                            sleepAwakeMin = keep(sl?.awake, old?.sleepAwakeMin, HcDataType.SLEEP),
                            sources = encodeSources(origins),
                        ),
                    )
                }

                // ── Exercise sessions ──
                deletedIds.forEach { id -> db.sessions().byHcId(id)?.let { db.sessions().delete(it.id) } }
                if (sessions != null) {
                    val ids = sessions.map { it.id }.toSet()
                    db.sessions().hcInRange(from.toEpochDay(), today.toEpochDay()).filter { it.hcId !in ids }.forEach { db.sessions().delete(it.id) }
                    sessions.forEach { s ->
                        val minutes = (s.end.toEpochMilli() - s.start.toEpochMilli()) / 60_000.0
                        val st = stats[s.id]
                        val row = ExerciseSessionEntity(
                            epochDay = time.dayOf(s.start.toEpochMilli()).toEpochDay(),
                            source = "HEALTH_CONNECT",
                            title = s.title?.takeIf { it.isNotBlank() } ?: ExerciseTypes.name(s.exerciseType),
                            exerciseType = s.exerciseType, startTime = s.start.toEpochMilli(), endTime = s.end.toEpochMilli(),
                            durationMinutes = minutes, kcal = st?.totalKcal ?: st?.activeKcal,
                            met = ExerciseTypes.met(s.exerciseType), recordedByDevice = true, hcId = s.id,
                            avgHr = st?.avgHr, maxHr = st?.maxHr, distanceM = st?.distanceM, dataOrigin = s.origin,
                        )
                        val existing = db.sessions().byHcId(s.id)
                        if (existing == null) db.sessions().insert(row) else db.sessions().update(row.copy(id = existing.id, createdAt = existing.createdAt))
                        sessionCount++
                    }
                }

                // ── Weights ──
                deletedIds.forEach { id -> db.body().weightByHcId(id)?.let { db.body().deleteWeight(it.id) } }
                if (weights != null) {
                    val ids = weights.map { it.id }.toSet()
                    db.body().hcWeightsInRange(from.toEpochDay(), today.toEpochDay()).filter { it.hcId !in ids }.forEach { db.body().deleteWeight(it.id) }
                    weights.forEach { w ->
                        val row = WeightEntity(
                            epochDay = time.dayOf(w.time.toEpochMilli()).toEpochDay(),
                            measuredAt = w.time.toEpochMilli(), weightLb = w.lb, source = "HEALTH_CONNECT", hcId = w.id,
                        )
                        val existing = db.body().weightByHcId(w.id)
                        if (existing == null) db.body().insertWeight(row) else db.body().updateWeight(row.copy(id = existing.id, note = existing.note))
                        weightCount++
                    }
                }
                db.body().latestWeight()?.let { latest ->
                    db.profile().get()?.let { p -> if (p.currentWeightLb != latest.weightLb) db.profile().upsert(p.copy(currentWeightLb = latest.weightLb)) }
                }
                db.syncMeta().upsert(
                    meta.copy(
                        lastAttemptAt = now, lastSuccessAt = now, lastError = null,
                        grantedPermissions = granted.joinToString(",") { it.name },
                        lastRecordCount = aggregates.size + sessionCount + weightCount,
                        changesToken = newToken, tokenTypes = grantedKey,
                        lastSyncMode = "$mode · ${aggregates.size} day${if (aggregates.size == 1) "" else "s"}",
                    ),
                )
            }
            SyncOutcome.Success(aggregates.size, sessionCount, weightCount, mode)
        } catch (e: SecurityException) {
            db.syncMeta().upsert(meta.copy(lastAttemptAt = now, lastError = "Permission denied: ${e.message ?: "revoked"}", changesToken = null))
            SyncOutcome.Failed("Permission denied")
        } catch (e: Exception) {
            db.syncMeta().upsert(meta.copy(lastAttemptAt = now, lastError = e.message ?: e.javaClass.simpleName))
            SyncOutcome.Failed(e.message ?: "Sync failed")
        }
    }

    /** Called after the user disconnects: clears permission metadata but keeps already-imported history. */
    suspend fun markDisconnected() {
        val meta = db.syncMeta().get() ?: SyncMetaEntity()
        db.syncMeta().upsert(meta.copy(grantedPermissions = "", backgroundSyncEnabled = false, lastError = "Disconnected", changesToken = null, tokenTypes = ""))
    }

    suspend fun setBackground(enabled: Boolean) {
        val meta = db.syncMeta().get() ?: SyncMetaEntity()
        db.syncMeta().upsert(meta.copy(backgroundSyncEnabled = enabled))
    }
}
