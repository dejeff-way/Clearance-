package com.forma.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * A finished week's report, saved as it was on the Sunday it came out (the underlying logs are kept too).
 * Reports are never deleted automatically.
 */
@Serializable
@Entity(tableName = "weekly_reports")
data class WeeklyReportEntity(
    /** The Sunday the week started. */
    @PrimaryKey val weekStartEpochDay: Long,
    /** [com.forma.app.data.report.WeeklyReport] as JSON. */
    val json: String,
    val generatedAt: Long,
    @ColumnInfo(defaultValue = "0") val dismissed: Boolean = false,
)

@Dao
interface ReportDao {
    @Query("SELECT * FROM weekly_reports ORDER BY weekStartEpochDay DESC") fun observeAll(): Flow<List<WeeklyReportEntity>>
    @Query("SELECT * FROM weekly_reports ORDER BY weekStartEpochDay DESC") suspend fun all(): List<WeeklyReportEntity>
    @Query("SELECT * FROM weekly_reports WHERE weekStartEpochDay = :week") suspend fun get(week: Long): WeeklyReportEntity?
    @Query("SELECT * FROM weekly_reports WHERE weekStartEpochDay = :week") fun observe(week: Long): Flow<WeeklyReportEntity?>
    @Upsert suspend fun upsert(r: WeeklyReportEntity)
    @Query("UPDATE weekly_reports SET dismissed = 1 WHERE weekStartEpochDay = :week") suspend fun dismiss(week: Long)
}
