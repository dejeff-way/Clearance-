package com.forma.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import kotlin.reflect.KClass

/** Real Health Connect implementation using the official androidx.health.connect client. */
class HealthConnectSource(private val context: Context) : HealthDataSource {

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

        val recordTypes: Map<HcDataType, KClass<out Record>> = mapOf(
            HcDataType.TOTAL_CALORIES to TotalCaloriesBurnedRecord::class,
            HcDataType.ACTIVE_CALORIES to ActiveCaloriesBurnedRecord::class,
            HcDataType.STEPS to StepsRecord::class,
            HcDataType.EXERCISE to ExerciseSessionRecord::class,
            HcDataType.WEIGHT to WeightRecord::class,
            HcDataType.SLEEP to SleepSessionRecord::class,
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

    override suspend fun dailyAggregates(
        from: LocalDate, toInclusive: LocalDate, types: Set<HcDataType>,
    ): List<DailyAggregate> {
        val c = requireClient()
        val range = TimeRangeFilter.between(from.atStartOfDay(), toInclusive.plusDays(1).atStartOfDay())

        // One request per metric: an aggregate that includes an unauthorised metric throws, and per-metric data
        // origins tell us whether any app actually wrote data (so we never present a default as a measurement).
        suspend fun <T : Any> perDay(metric: androidx.health.connect.client.aggregate.AggregateMetric<T>): Map<LocalDate, T> =
            c.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(metric), range, Period.ofDays(1)))
                .mapNotNull { g ->
                    val v = g.result[metric]
                    if (v != null && g.result.dataOrigins.isNotEmpty()) g.startTime.toLocalDate() to v else null
                }.toMap()

        val total = if (HcDataType.TOTAL_CALORIES in types) perDay(TotalCaloriesBurnedRecord.ENERGY_TOTAL) else emptyMap()
        val active = if (HcDataType.ACTIVE_CALORIES in types) perDay(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL) else emptyMap()
        val steps = if (HcDataType.STEPS in types) perDay(StepsRecord.COUNT_TOTAL) else emptyMap()
        val exercise = if (HcDataType.EXERCISE in types) perDay(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL) else emptyMap()

        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(toInclusive) }.map { d ->
            DailyAggregate(
                date = d,
                totalKcal = total[d]?.inKilocalories,
                activeKcal = active[d]?.inKilocalories,
                steps = steps[d],
                exerciseMinutes = exercise[d]?.toMinutes()?.toDouble(),
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
            HcExerciseSession(it.metadata.id, it.startTime, it.endTime, it.exerciseType, it.title)
        }

    override suspend fun weights(from: Instant, to: Instant): List<HcWeight> =
        readAll(WeightRecord::class, from, to).map { HcWeight(it.metadata.id, it.time, it.weight.inPounds) }

    override suspend fun sleep(from: Instant, to: Instant): List<HcSleep> =
        readAll(SleepSessionRecord::class, from, to).map { HcSleep(it.metadata.id, it.startTime, it.endTime) }

    override suspend fun revokeAll() {
        client?.permissionController?.revokeAllPermissions()
    }
}
