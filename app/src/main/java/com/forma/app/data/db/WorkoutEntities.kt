package com.forma.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Exercise library (built-in movements plus any imported ones). Never deleted, so history always resolves. */
@Serializable
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val key: String,
    val name: String,
    /** Movement pattern; exercises sharing a pattern can substitute for each other. */
    val pattern: String,
    /** STRENGTH, CARDIO, MOBILITY, CORE, BALANCE */
    val kind: String,
    val instruction: String,
    val equipment: String? = null,
    val muscleGroups: List<String> = emptyList(),
    val tracksWeight: Boolean = true,
    val incrementLb: Double = 5.0,
    val builtIn: Boolean = false,
)

/** A workout in the library. Scheduling lives in programs, not here. */
@Serializable
@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val difficulty: String,
    val durationMinutes: Int,
    val focus: String,
    val notes: String,
    val goals: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val met: Double = 4.5,
    val builtIn: Boolean = false,
    /** BUILT_IN, IMPORT or USER */
    val origin: String = "IMPORT",
    val importedAt: Long? = null,
    val sourceJson: String? = null,
    val sortOrder: Int = 0,
)

/** One exercise slot in a workout. [slotKey] is globally unique and stable across updates so history lines up. */
@Serializable
@Entity(tableName = "workout_slots", primaryKeys = ["workoutId", "position"], indices = [Index("slotKey")])
data class WorkoutSlotEntity(
    val workoutId: String,
    val position: Int,
    val slotKey: String,
    val exerciseKey: String,
    val sets: Int,
    /** Display prescription, e.g. "10–12" or "20 min". */
    val target: String,
    val repMin: Int? = null,
    val repMax: Int? = null,
    val timed: Boolean = false,
    val restSeconds: Int = 60,
    val met: Double = 4.5,
    /** Approved substitutes (exercise keys). Empty = any exercise with the same movement pattern. */
    val alternatives: List<String> = emptyList(),
    val instructionOverride: String? = null,
    /** DOUBLE, LINEAR or NONE */
    val progression: String = "DOUBLE",
    val incrementLb: Double? = null,
)

@Serializable
@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val weeks: Int = 1,
    val builtIn: Boolean = false,
    val origin: String = "IMPORT",
    val importedAt: Long? = null,
    val sourceJson: String? = null,
)

/** Day assignment. week = 0 applies to every week unless a specific week overrides it. dayOfWeek: 1 = Monday … 7 = Sunday. */
@Serializable
@Entity(tableName = "program_days", primaryKeys = ["programId", "week", "dayOfWeek"])
data class ProgramDayEntity(
    val programId: String,
    val week: Int,
    val dayOfWeek: Int,
    val workoutId: String? = null,
    val rest: Boolean = false,
    val optional: Boolean = false,
    val label: String? = null,
)

@Serializable
@Entity(tableName = "active_program")
data class ActiveProgramEntity(
    @PrimaryKey val id: Int = 1,
    val programId: String,
    /** Sunday of the program's first week. */
    @ColumnInfo(defaultValue = "0") val startEpochDay: Long,
)
