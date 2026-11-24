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
    ACTIVE_CALORIES("Active calories burned", "Fallback when total calories aren't available"),
    STEPS("Steps", "Daily activity and estimated expenditure"),
    EXERCISE("Exercise sessions", "Workout duration and avoiding double-counting"),
    WEIGHT("Weight", "Imports weigh-ins from your scale"),
    SLEEP("Sleep", "Shows sleep duration on the dashboard"),
}

data class DailyAggregate(
    val date: LocalDate,
    val totalKcal: Double?,
    val activeKcal: Double?,
    val steps: Long?,
    val exerciseMinutes: Double?,
)

data class HcExerciseSession(
    val id: String,
    val start: Instant,
    val end: Instant,
    val exerciseType: Int,
    val title: String?,
)

data class HcWeight(val id: String, val time: Instant, val lb: Double)

data class HcSleep(val id: String, val start: Instant, val end: Instant)

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
    suspend fun weights(from: Instant, to: Instant): List<HcWeight>
    suspend fun sleep(from: Instant, to: Instant): List<HcSleep>
    suspend fun revokeAll()
}
