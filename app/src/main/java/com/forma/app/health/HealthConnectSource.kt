package com.forma.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import kotlin.reflect.KClass

/**
 * Real Health Connect implementation using the official androidx.health.connect client.
 *
 * Steps, calories, distance, floors and heart rate use Health Connect's aggregation API, which de-duplicates
 * overlapping data from multiple apps according to the user's app priority in Health Connect. Nothing read here is
 * logged.
 */
class HealthConnectSource(private val context: Context) : HealthDataSource {

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

        val recordTypes: Map<HcDataType, KClass<out Record>> = mapOf(
            HcDataType.TOTAL_CALORIES to TotalCaloriesBurnedRecord::class,
            HcDataType.ACTIVE_CALORIES to ActiveCaloriesBurnedRecord::class,
            HcDataType.BASAL_CALORIES to BasalMetabolicRateRecord::class,
            HcDataType.STEPS to StepsRecord::class,
            HcDataType.DISTANCE to DistanceRecord::class,
            HcDataType.FLOORS to FloorsClimbedRecord::class,
            HcDataType.EXERCISE to ExerciseSessionRecord::class,
            HcDataType.HEART_RATE to HeartRateRecord::class,
            HcDataType.RESTING_HEART_RATE to RestingHeartRateRecord::class,
            HcDataType.HRV to HeartRateVariabilityRmssdRecord::class,
            HcDataType.SLEEP to SleepSessionRecord::class,
            HcDataType.OXYGEN_SATURATION to OxygenSaturationRecord::class,
            HcDataType.RESPIRATORY_RATE to RespiratoryRateRecord::class,
            HcDataType.WEIGHT to WeightRecord::class,
            HcDataType.BODY_FAT to BodyFatRecord::class,
        )

        fun permissionFor(t: HcDataType): String = HealthPermission.getReadPermission(recordTypes.getValue(t))

        /** Only the permissions for features FORMA actually has. */
        val readPermissions: Set<String> = HcDataType.entries.map(::permissionFor).toSet()

        const val BACKGROUND_PERMISSION = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    }

    private val client: HealthConnectClient? by lazy {
        if (availability() == HcAvailability.AVAILABLE) HealthConnectClient.getOrCreate(context) else null
    }

    override fun availability(): HcAvailability =
        when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> HcAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.UPDATE_REQUIRED
            else -> HcAvailability.UNAVAILABLE
        }

    private fun requireClient() = client ?: throw IllegalStateException("Health Connect is not available")

    override suspend fun grantedTypes(): Set<HcDataType> {
        val c = client ?: return emptySet()
        val granted = c.permissionController.getGrantedPermissions()
        return HcDataType.entries.filter { permissionFor(it) in granted }.toSet()
    }

    override suspend fun backgroundReadAvailable(): Boolean {
        val c = client ?: return false
        return c.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }

    override suspend fun backgroundReadGranted(): Boolean {
        val c = client ?: return false
        return BACKGROUND_PERMISSION in c.permissionController.getGrantedPermissions()
    }

    override suspend fun dailyAggregates(from: LocalDate, toInclusive: LocalDate, types: Set<HcDataType>): List<DailyAggregate> {
        val c = requireClient()
        val range = TimeRangeFilter.between(from.atStartOfDay(), toInclusive.plusDays(1).atStartOfDay())
        val origins = HashMap<Pair<LocalDate, HcDataType>, Set<String>>()

        // One request per metric: an aggregate that includes an unauthorised metric throws, and per-metric data
        // origins tell us whether any app actually wrote data (so we never present a default as a measurement).
        suspend fun <T : Any> perDay(type: HcDataType, metric: AggregateMetric<T>): Map<LocalDate, T> {
            if (type !in types) return emptyMap()
            return c.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(metric), range, Period.ofDays(1))).mapNotNull { g ->
                val v = g.result[metric]
                if (v != null && g.result.dataOrigins.isNotEmpty()) {
                    val d = g.startTime.toLocalDate()
                    origins.merge(d to type, g.result.dataOrigins.map { it.packageName }.toSet()) { a, b -> a + b }
                    d to v
                } else null
            }.toMap()
        }

        val total = perDay(HcDataType.TOTAL_CALORIES, TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        val active = perDay(HcDataType.ACTIVE_CALORIES, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        val basal = perDay(HcDataType.BASAL_CALORIES, BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL)
        val steps = perDay(HcDataType.STEPS, StepsRecord.COUNT_TOTAL)
        val exercise = perDay(HcDataType.EXERCISE, ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)
        val distance = perDay(HcDataType.DISTANCE, DistanceRecord.DISTANCE_TOTAL)
        val floors = perDay(HcDataType.FLOORS, FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL)
        val hrAvg = perDay(HcDataType.HEART_RATE, HeartRateRecord.BPM_AVG)
        val hrMin = perDay(HcDataType.HEART_RATE, HeartRateRecord.BPM_MIN)
        val hrMax = perDay(HcDataType.HEART_RATE, HeartRateRecord.BPM_MAX)
        val rhr = perDay(HcDataType.RESTING_HEART_RATE, RestingHeartRateRecord.BPM_AVG)

        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(toInclusive) }.map { d ->
            DailyAggregate(
                date = d,
                totalKcal = total[d]?.inKilocalories, activeKcal = active[d]?.inKilocalories, steps = steps[d],
                exerciseMinutes = exercise[d]?.toMinutes()?.toDouble(), basalKcal = basal[d]?.inKilocalories,
                distanceM = distance[d]?.inMeters, floors = floors[d],
                avgHr = hrAvg[d]?.toDouble(), minHr = hrMin[d]?.toDouble(), maxHr = hrMax[d]?.toDouble(), restingHr = rhr[d]?.toDouble(),
                origins = HcDataType.entries.mapNotNull { t -> origins[d to t]?.let { t to it } }.toMap(),
            )
        }.toList()
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, from: Instant, to: Instant): List<T> {
        val c = requireClient()
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val resp = c.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(from, to), pageToken = token))
            out += resp.records
            token = resp.pageToken
        } while (token != null)
        return out
    }

    override suspend fun exerciseSessions(from: Instant, to: Instant): List<HcExerciseSession> =
        readAll(ExerciseSessionRecord::class, from, to).map {
            HcExerciseSession(it.metadata.id, it.startTime, it.endTime, it.exerciseType, it.title, it.metadata.dataOrigin.packageName)
        }

    override suspend fun sessionStats(start: Instant, end: Instant, types: Set<HcDataType>): SessionStats {
        val c = requireClient()
        suspend fun <T : Any> one(type: HcDataType, metric: AggregateMetric<T>): T? =
            if (type !in types) null
            else runCatching { c.aggregate(AggregateRequest(setOf(metric), TimeRangeFilter.between(start, end))).let { r -> if (r.dataOrigins.isEmpty()) null else r[metric] } }.getOrNull()
        return SessionStats(
            avgHr = one(HcDataType.HEART_RATE, HeartRateRecord.BPM_AVG)?.toDouble(),
            maxHr = one(HcDataType.HEART_RATE, HeartRateRecord.BPM_MAX)?.toDouble(),
            totalKcal = one(HcDataType.TOTAL_CALORIES, TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories,
            activeKcal = one(HcDataType.ACTIVE_CALORIES, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
            distanceM = one(HcDataType.DISTANCE, DistanceRecord.DISTANCE_TOTAL)?.inMeters,
        )
    }

    override suspend fun weights(from: Instant, to: Instant): List<HcWeight> =
        readAll(WeightRecord::class, from, to).map { HcWeight(it.metadata.id, it.time, it.weight.inPounds, it.metadata.dataOrigin.packageName) }

    override suspend fun sleep(from: Instant, to: Instant): List<HcSleep> =
        readAll(SleepSessionRecord::class, from, to).map { r ->
            HcSleep(r.metadata.id, r.startTime, r.endTime, r.stages.map { st ->
                HcSleepStage(st.startTime, st.endTime, when (st.stage) {
                    SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStage.AWAKE
                    SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStage.LIGHT
                    SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.DEEP
                    SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.REM
                    SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStage.SLEEPING
                    else -> SleepStage.OTHER
                })
            }, r.metadata.dataOrigin.packageName)
        }

    override suspend fun samples(type: HcDataType, from: Instant, to: Instant): List<HcSample> = when (type) {
        HcDataType.HRV -> readAll(HeartRateVariabilityRmssdRecord::class, from, to).map { HcSample(it.metadata.id, it.time, it.heartRateVariabilityMillis, it.metadata.dataOrigin.packageName) }
        HcDataType.OXYGEN_SATURATION -> readAll(OxygenSaturationRecord::class, from, to).map { HcSample(it.metadata.id, it.time, it.percentage.value, it.metadata.dataOrigin.packageName) }
        HcDataType.RESPIRATORY_RATE -> readAll(RespiratoryRateRecord::class, from, to).map { HcSample(it.metadata.id, it.time, it.rate, it.metadata.dataOrigin.packageName) }
        HcDataType.BODY_FAT -> readAll(BodyFatRecord::class, from, to).map { HcSample(it.metadata.id, it.time, it.percentage.value, it.metadata.dataOrigin.packageName) }
        else -> emptyList()
    }

    override suspend fun changesToken(types: Set<HcDataType>): String =
        requireClient().getChangesToken(ChangesTokenRequest(types.map { recordTypes.getValue(it) }.toSet()))

    override suspend fun changes(token: String): HcChanges {
        val c = requireClient()
        val times = mutableListOf<Instant>()
        val deleted = mutableListOf<String>()
        var next = token
        while (true) {
            val r = c.getChanges(next)
            if (r.changesTokenExpired) return HcChanges(emptyList(), emptyList(), next, expired = true)
            r.changes.forEach { ch ->
                when (ch) {
                    is UpsertionChange -> recordTime(ch.record)?.let { times += it }
                    is DeletionChange -> deleted += ch.recordId
                }
            }
            next = r.nextChangesToken
            if (!r.hasMore) break
        }
        return HcChanges(times, deleted, next, expired = false)
    }

    private fun recordTime(r: Record): Instant? = when (r) {
        is TotalCaloriesBurnedRecord -> r.startTime
        is ActiveCaloriesBurnedRecord -> r.startTime
        is StepsRecord -> r.startTime
        is DistanceRecord -> r.startTime
        is FloorsClimbedRecord -> r.startTime
        is ExerciseSessionRecord -> r.startTime
        is HeartRateRecord -> r.startTime
        is SleepSessionRecord -> r.endTime
        is BasalMetabolicRateRecord -> r.time
        is RestingHeartRateRecord -> r.time
        is HeartRateVariabilityRmssdRecord -> r.time
        is OxygenSaturationRecord -> r.time
        is RespiratoryRateRecord -> r.time
        is WeightRecord -> r.time
        is BodyFatRecord -> r.time
        else -> null
    }

    override suspend fun revokeAll() {
        client?.permissionController?.revokeAllPermissions()
    }
}
