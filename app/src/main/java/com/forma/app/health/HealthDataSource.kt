package com.forma.app.health

import java.time.Instant
import java.time.LocalDate

/** Health Connect availability on this device. */
enum class HcAvailability {
    AVAILABLE,

    /** Health Connect is installed but needs an update (or is being set up). */
    UPDATE_REQUIRED,

    /** Not supported / not installed on this device. */
    UNAVAILABLE,
}

/** The data types FORMA reads. Each maps to exactly one read permission. */
enum class HcDataType(val label: String, val purpose: String) {
    TOTAL_CALORIES("Total calories burned", "Preferred source for daily energy expenditure"),
    ACTIVE_CALORIES("Active calories burned", "Used when total calories aren't available"),
    BASAL_CALORIES("Resting (basal) calories", "Measured resting energy instead of a formula"),
    STEPS("Steps", "Steps card, step goal and estimated expenditure"),
    DISTANCE("Distance", "Distance travelled each day and per workout"),
    FLOORS("Floors climbed", "Daily floors"),
    EXERCISE("Exercise sessions", "Watch workouts in your history; avoids double-counting"),
    HEART_RATE("Heart rate", "Daily and workout heart rate"),
    RESTING_HEART_RATE("Resting heart rate", "Resting heart-rate trend"),
    HRV("Heart-rate variability", "HRV (RMSSD) trend"),
    SLEEP("Sleep", "Sleep duration and stages"),
    OXYGEN_SATURATION("Oxygen saturation (SpO₂)", "SpO₂ trend"),
    RESPIRATORY_RATE("Respiratory rate", "Breathing-rate trend"),
    WEIGHT("Weight", "Imports weigh-ins from your scale"),
    BODY_FAT("Body fat", "Body-fat percentage trend"),
}

data class DailyAggregate(
    val date: LocalDate,
    val totalKcal: Double? = null,
    val activeKcal: Double? = null,
    val steps: Long? = null,
    val exerciseMinutes: Double? = null,
    val basalKcal: Double? = null,
    val distanceM: Double? = null,
    val floors: Double? = null,
    val avgHr: Double? = null,
    val minHr: Double? = null,
    val maxHr: Double? = null,
    val restingHr: Double? = null,
    /** Package names of apps that contributed to each type on this day. */
    val origins: Map<HcDataType, Set<String>> = emptyMap(),
)

data class HcExerciseSession(
    val id: String,
    val start: Instant,
    val end: Instant,
    val exerciseType: Int,
    val title: String?,
    val origin: String? = null,
)

/** Heart rate, energy and distance measured during one workout (each null when not recorded). */
data class SessionStats(val avgHr: Double?, val maxHr: Double?, val totalKcal: Double?, val activeKcal: Double?, val distanceM: Double?)

data class HcWeight(val id: String, val time: Instant, val lb: Double, val origin: String? = null)

enum class SleepStage { AWAKE, LIGHT, DEEP, REM, SLEEPING, OTHER }

data class HcSleepStage(val start: Instant, val end: Instant, val stage: SleepStage)

data class HcSleep(val id: String, val start: Instant, val end: Instant, val stages: List<HcSleepStage> = emptyList(), val origin: String? = null)

/** A single instantaneous reading (HRV ms, SpO₂ %, breaths/min, body fat %). */
data class HcSample(val id: String, val time: Instant, val value: Double, val origin: String? = null)

/** Result of reading Health Connect's change log. */
data class HcChanges(
    /** Times of inserted/updated records, so only affected days are re-read. */
    val upsertTimes: List<Instant>,
    val deletedIds: List<String>,
    val nextToken: String,
    /** The token is older than Health Connect keeps (about 30 days) — a full re-sync is needed. */
    val expired: Boolean,
)

/**
 * Abstraction over Health Connect so sync logic can be unit tested without a device.
 * Implementations must only read types whose permission is granted.
 */
interface HealthDataSource {
    fun availability(): HcAvailability
    suspend fun grantedTypes(): Set<HcDataType>
    suspend fun backgroundReadAvailable(): Boolean
    suspend fun backgroundReadGranted(): Boolean
    suspend fun dailyAggregates(from: LocalDate, toInclusive: LocalDate, types: Set<HcDataType>): List<DailyAggregate>
    suspend fun exerciseSessions(from: Instant, to: Instant): List<HcExerciseSession>
    suspend fun sessionStats(start: Instant, end: Instant, types: Set<HcDataType>): SessionStats
    suspend fun weights(from: Instant, to: Instant): List<HcWeight>
    suspend fun sleep(from: Instant, to: Instant): List<HcSleep>
    /** Instantaneous readings for HRV, SPO2, RESPIRATORY_RATE or BODY_FAT. */
    suspend fun samples(type: HcDataType, from: Instant, to: Instant): List<HcSample>
    suspend fun changesToken(types: Set<HcDataType>): String
    suspend fun changes(token: String): HcChanges
    suspend fun revokeAll()
}
