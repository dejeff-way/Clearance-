package com.forma.app.data.workout

import com.forma.app.data.db.ActiveProgramEntity
import com.forma.app.data.db.ExerciseEntity
import com.forma.app.data.db.ProgramDayEntity
import com.forma.app.data.db.ProgramEntity
import com.forma.app.data.db.WorkoutEntity
import com.forma.app.data.db.WorkoutSlotEntity
import com.forma.app.data.seed.ExerciseKind
import com.forma.engine.TrendAnalyzer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ProgressionType { DOUBLE, LINEAR, NONE }

data class WorkoutDef(val entity: WorkoutEntity, val slots: List<WorkoutSlotEntity>) {
    val id get() = entity.id
    val name get() = entity.name
}

data class ProgramDef(val entity: ProgramEntity, val days: List<ProgramDayEntity>) {
    val id get() = entity.id
    val weeks get() = entity.weeks.coerceAtLeast(1)

    /** A specific-week row overrides the every-week (week 0) row. */
    fun dayFor(week: Int, dow: DayOfWeek): ProgramDayEntity? =
        days.firstOrNull { it.week == week && it.dayOfWeek == dow.value } ?: days.firstOrNull { it.week == 0 && it.dayOfWeek == dow.value }
}

data class PlannedDay(
    val date: LocalDate,
    val week: Int,
    val workout: WorkoutDef?,
    val rest: Boolean,
    val optional: Boolean,
    val label: String?,
) {
    /** Counts toward the weekly completion target. */
    val required get() = workout != null && !rest && !optional
    val title get() = workout?.name ?: label ?: if (rest) "Rest" else "Nothing scheduled"
}

/** Snapshot of the workout library and the active schedule. */
class WorkoutCatalog(
    exercises: List<ExerciseEntity>,
    workouts: List<WorkoutEntity>,
    slots: List<WorkoutSlotEntity>,
    programs: List<ProgramEntity>,
    programDays: List<ProgramDayEntity>,
    val active: ActiveProgramEntity?,
) {
    val exercises: Map<String, ExerciseEntity> = exercises.associateBy { it.key }
    private val slotsByWorkout = slots.groupBy { it.workoutId }
    val workouts: List<WorkoutDef> = workouts.map { WorkoutDef(it, slotsByWorkout[it.id].orEmpty().sortedBy { s -> s.position }) }
    val workoutsById: Map<String, WorkoutDef> = this.workouts.associateBy { it.id }
    private val daysByProgram = programDays.groupBy { it.programId }
    val programs: List<ProgramDef> = programs.map { ProgramDef(it, daysByProgram[it.id].orEmpty()) }
    val activeProgram: ProgramDef? = active?.let { a -> this.programs.firstOrNull { it.id == a.programId } }

    fun exercise(key: String): ExerciseEntity = exercises[key] ?: ExerciseEntity(
        key, key.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }, "unknown:$key", ExerciseKind.STRENGTH.name, "",
    )

    fun kind(key: String): ExerciseKind = runCatching { ExerciseKind.valueOf(exercise(key).kind) }.getOrDefault(ExerciseKind.STRENGTH)

    /** 0-based number of weeks since the program started (negative before it starts). */
    fun weeksSinceStart(date: LocalDate): Long {
        val start = active?.startEpochDay?.let(LocalDate::ofEpochDay) ?: return 0
        return Math.floorDiv(ChronoUnit.DAYS.between(start, TrendAnalyzer.weekStart(date)), 7L)
    }

    /** 1-based program week; multi-week programs repeat from week 1 after the last week. */
    fun weekOf(date: LocalDate): Int {
        val p = activeProgram ?: return 1
        val w = weeksSinceStart(date)
        return if (w < 0) 1 else (w % p.weeks).toInt() + 1
    }

    fun planFor(date: LocalDate): PlannedDay {
        val p = activeProgram ?: return PlannedDay(date, 1, null, false, false, "No active program")
        val week = weekOf(date)
        val d = p.dayFor(week, date.dayOfWeek) ?: return PlannedDay(date, week, null, false, false, null)
        return PlannedDay(date, week, d.workoutId?.let { workoutsById[it] }, d.rest, d.optional, d.label)
    }

    fun requiredInWeekOf(date: LocalDate): Int {
        val ws = TrendAnalyzer.weekStart(date)
        return (0..6).count { planFor(ws.plusDays(it.toLong())).required }
    }

    /** Approved alternatives for a slot; when none are listed, any library exercise with the same movement pattern. */
    fun substitutesFor(slot: WorkoutSlotEntity): List<ExerciseEntity> {
        val original = exercise(slot.exerciseKey)
        val alts = if (slot.alternatives.isNotEmpty()) slot.alternatives.map(::exercise)
        else exercises.values.filter { it.pattern == original.pattern && it.key != original.key }.sortedBy { it.name }
        return (listOf(original) + alts).distinctBy { it.key }
    }

    fun slot(slotKey: String): WorkoutSlotEntity? = workouts.firstNotNullOfOrNull { w -> w.slots.firstOrNull { it.slotKey == slotKey } }

    fun progressionOf(slot: WorkoutSlotEntity) = runCatching { ProgressionType.valueOf(slot.progression) }.getOrDefault(ProgressionType.DOUBLE)

    companion object {
        val SUNDAY_FIRST = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
    }
}
