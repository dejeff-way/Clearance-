package com.forma.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Single-row profile and targets. */
@Serializable
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String = "",
    val sex: String = "MALE",
    val ageYears: Int = 22,
    val heightInches: Double = 75.0,
    val startWeightLb: Double = 260.0,
    val currentWeightLb: Double = 260.0,
    val goalWeightLb: Double? = null,
    val mainGoal: String = "Lose body fat while maintaining and building muscle",
    val secondaryGoal: String = "Ski-ready legs, core, balance and endurance",
    val skiDateEpochDay: Long = 20819, // 2027-01-01
    val gym: String = "LA Fitness",
    /** Comma-separated DayOfWeek names. */
    val trainingDays: String = "SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY",
    val workoutMinutes: Int = 55,
    val baselineActivityFactor: Double = 1.2,
    val maintenanceActivityFactor: Double = 1.375,
    val desiredDeficitKcal: Int = 500,
    val minimumBudgetKcal: Int = 2000,
    val maxRepaymentPerDay: Int = 200,
    val budgetMode: String = "DYNAMIC",
    val fixedBudgetKcal: Int? = null,
    val proteinTargetG: Int = 180,
    val programStartEpochDay: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "food_log", indices = [Index("epochDay")])
data class FoodLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    /** When the food was eaten (used for chronological ordering). */
    val eatenAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val mealType: String,
    val name: String,
    val recipeId: String? = null,
    val portion: String? = null,
    val servings: Double = 1.0,
    val milkOption: String? = null,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val kosherStatus: String? = null,
    val note: String? = null,
)

@Serializable
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val kosherStatus: String,
    val isFish: Boolean = false,
    val summary: String,
    val prepMinutes: Int,
    val cookMinutes: Int,
    val prepSteps: List<String>,
    val cookSteps: List<String>,
    val kosherNotes: List<String>,
    val safetyNotes: List<String>,
    val tips: List<String>,
    val standardPortionLabel: String,
    val largePortionLabel: String,
    val referenceKcal: Int? = null,
    val referenceProteinG: Int? = null,
    val usesMilkChoice: Boolean = false,
    val defaultMilk: String? = null,
    val selectedMilk: String? = null,
    val isFavorite: Boolean = false,
    val photoUri: String? = null,
    val artKey: String,
    val builtIn: Boolean = true,
    val seedVersion: Int = 1,
    val sortOrder: Int = 0,
)

@Serializable
@Entity(tableName = "recipe_ingredients", primaryKeys = ["recipeId", "position"], indices = [Index("recipeId")])
data class IngredientEntity(
    val recipeId: String,
    val position: Int,
    val name: String,
    /** Standard-portion amount in [unit]. 0 for "to taste". */
    val amount: Double,
    val unit: String,
    val largeAmount: Double,
    val note: String? = null,
    /** Nutrition for [amount]. Ignored when [isMilk] (computed from the selected milk). */
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val optional: Boolean = false,
    val isMilk: Boolean = false,
)

/** Per-day data read from Health Connect. Null = not available / not authorised. */
@Serializable
@Entity(tableName = "daily_energy")
data class DailyEnergyEntity(
    @PrimaryKey val epochDay: Long,
    val hcTotalKcal: Double? = null,
    val hcActiveKcal: Double? = null,
    val steps: Long? = null,
    val exerciseMinutes: Double? = null,
    val sleepMinutes: Double? = null,
    val syncedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "exercise_sessions",
    indices = [Index("epochDay"), Index(value = ["hcId"], unique = true)],
)
data class ExerciseSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    /** HEALTH_CONNECT or MANUAL. Program workouts live in workout_sessions. */
    val source: String,
    val title: String,
    val exerciseType: Int? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val durationMinutes: Double,
    val kcal: Double? = null,
    val met: Double = 4.0,
    val recordedByDevice: Boolean = false,
    val hcId: String? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "carryover", indices = [Index("epochDay")])
data class CarryoverEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val kcal: Int,
    /** OVERAGE or UNLOGGED_ADJUSTMENT */
    val kind: String,
    val note: String? = null,
    val cancelled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "weights", indices = [Index("epochDay"), Index(value = ["hcId"], unique = true)])
data class WeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val measuredAt: Long,
    val weightLb: Double,
    /** MANUAL or HEALTH_CONNECT */
    val source: String = "MANUAL",
    val hcId: String? = null,
    val note: String? = null,
)

@Serializable
@Entity(tableName = "measurements", indices = [Index("epochDay")])
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val waistIn: Double,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "workout_sessions", indices = [Index(value = ["epochDay", "templateKey"], unique = true)])
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val templateKey: String,
    val title: String,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val notes: String? = null,
)

@Serializable
@Entity(
    tableName = "exercise_logs",
    indices = [Index(value = ["sessionId", "slotKey"], unique = true)],
)
data class ExerciseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val slotKey: String,
    /** Exercise actually performed (differs from the template when substituted). */
    val exerciseKey: String,
    val completed: Boolean = false,
    val notes: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "set_logs", indices = [Index(value = ["exerciseLogId", "setIndex"], unique = true)])
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseLogId: Long,
    val setIndex: Int,
    val weightLb: Double? = null,
    val reps: Int? = null,
    val durationMin: Double? = null,
)

/** "Use this substitute from now on" for a program slot. */
@Serializable
@Entity(tableName = "substitution_prefs")
data class SubstitutionPrefEntity(
    @PrimaryKey val slotKey: String,
    val exerciseKey: String,
)

@Serializable
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val id: Int = 1,
    val lastAttemptAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastError: String? = null,
    val grantedPermissions: String = "",
    val backgroundSyncEnabled: Boolean = false,
    val lastRecordCount: Int = 0,
)

/** Aggregated daily food totals (query projection). */
@Serializable
data class DailyFoodTotals(
    val epochDay: Long,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double?,
    val fatG: Double?,
    val items: Int,
)
