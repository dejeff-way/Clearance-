package com.forma.app.data.workout

import androidx.room.withTransaction
import com.forma.app.data.db.ActiveProgramEntity
import com.forma.app.data.db.ExerciseEntity
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.db.ProgramDayEntity
import com.forma.app.data.db.ProgramEntity
import com.forma.app.data.db.WorkoutEntity
import com.forma.app.data.db.WorkoutSlotEntity
import com.forma.app.data.seed.ExerciseLibrary
import com.forma.app.data.seed.WorkoutProgram
import com.forma.app.util.TimeProvider
import com.forma.engine.TrendAnalyzer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.LocalDate

enum class ScheduleScope { THIS_WEEK, EVERY_WEEK }

class WorkoutLibraryRepository(private val db: FormaDatabase, private val time: TimeProvider) {
    private val dao = db.library()

    companion object {
        const val BASE_PROGRAM = "forma-base"
        const val MY_PROGRAM = "my-program"
        private val CATEGORY = mapOf("SUN" to "Upper Body", "MON" to "Legs", "TUE" to "Cardio", "WED" to "Upper Body", "THU" to "Legs", "SAT" to "Mobility")
    }

    val catalog: Flow<WorkoutCatalog> = combine(
        listOf(dao.observeExercises(), dao.observeWorkouts(), dao.observeSlots(), dao.observePrograms(), dao.observeProgramDays(), dao.observeActive()),
    ) { a ->
        @Suppress("UNCHECKED_CAST")
        WorkoutCatalog(
            a[0] as List<ExerciseEntity>, a[1] as List<WorkoutEntity>, a[2] as List<WorkoutSlotEntity>,
            a[3] as List<ProgramEntity>, a[4] as List<ProgramDayEntity>, a[5] as ActiveProgramEntity?,
        )
    }

    /** Writes the built-in exercises, workouts and base program (idempotent) and makes sure a program is active. */
    suspend fun seed() = db.withTransaction {
        dao.upsertExercises(ExerciseLibrary.all.map {
            ExerciseEntity(it.key, it.name, it.pattern, it.kind.name, it.instruction, null, emptyList(), it.tracksWeight, it.incrementLb, builtIn = true)
        })
        WorkoutProgram.days.filter { !it.rest }.forEachIndexed { i, d ->
            dao.replaceWorkout(
                WorkoutEntity(d.key, d.title, CATEGORY[d.key] ?: "Other", "Beginner", d.estimatedMinutes, d.focus, d.notes,
                    goals = listOf("Fat loss", "Strength", "Ski preparation"), tags = listOf("forma-base"), met = d.met,
                    builtIn = true, origin = "BUILT_IN", sortOrder = i),
                d.slots.mapIndexed { j, s ->
                    WorkoutSlotEntity(d.key, j, s.key, s.exerciseKey, s.sets, s.target, s.repRange?.first, s.repRange?.last, s.timed,
                        s.restSeconds, s.met, progression = if (s.repRange != null) "DOUBLE" else "NONE")
                },
            )
        }
        dao.replaceProgram(
            ProgramEntity(BASE_PROGRAM, "FORMA Base Program", "Sunday–Thursday training, Friday rest for Shabbos, optional light session after Shabbos.",
                weeks = 1, builtIn = true, origin = "BUILT_IN"),
            WorkoutProgram.days.map { d ->
                ProgramDayEntity(BASE_PROGRAM, 0, d.day.value, if (d.rest) null else d.key, rest = d.rest, optional = d.optional,
                    label = if (d.rest) d.title else null)
            },
        )
        if (dao.active() == null || dao.program(dao.active()!!.programId) == null) {
            dao.setActive(ActiveProgramEntity(programId = BASE_PROGRAM, startEpochDay = TrendAnalyzer.weekStart(time.today()).toEpochDay()))
        }
    }

    /** Makes a program active. Week 1 starts on the Sunday of [startWeekOf]. Existing history is untouched. */
    suspend fun activate(programId: String, startWeekOf: LocalDate = time.today()) {
        requireNotNull(dao.program(programId)) { "Program not found" }
        dao.setActive(ActiveProgramEntity(programId = programId, startEpochDay = TrendAnalyzer.weekStart(startWeekOf).toEpochDay()))
    }

    /**
     * Puts a workout (or rest) on a day of the active schedule. Built-in programs are never edited: the first change
     * copies the active program to "My program" and activates that copy, keeping the same start date.
     */
    suspend fun schedule(date: LocalDate, workoutId: String?, rest: Boolean, optional: Boolean, scope: ScheduleScope, catalog: WorkoutCatalog) =
        db.withTransaction {
            val active = dao.active() ?: return@withTransaction
            var programId = active.programId
            val program = dao.program(programId) ?: return@withTransaction
            if (program.builtIn) {
                programId = MY_PROGRAM
                dao.replaceProgram(
                    program.copy(id = MY_PROGRAM, name = "My program", description = "Your schedule, based on ${program.name}.", builtIn = false,
                        origin = "USER", sourceJson = null, importedAt = null),
                    dao.programDays(program.id).map { it.copy(programId = MY_PROGRAM) },
                )
                dao.setActive(active.copy(programId = MY_PROGRAM))
            }
            val week = if (scope == ScheduleScope.EVERY_WEEK) 0 else catalog.weekOf(date)
            if (scope == ScheduleScope.EVERY_WEEK) {
                // An every-week change replaces week-specific entries for that weekday.
                (1..program.weeks).forEach { dao.deleteProgramDay(programId, it, date.dayOfWeek.value) }
            }
            dao.upsertProgramDays(listOf(ProgramDayEntity(programId, week, date.dayOfWeek.value, workoutId, rest, optional,
                label = if (rest) "Rest" else null)))
        }

    /** Deletes an imported/user workout. Refused while a program still schedules it. */
    suspend fun deleteWorkout(id: String) {
        val w = dao.workout(id) ?: return
        require(!w.builtIn) { "Built-in workouts can't be deleted" }
        val users = dao.programs().filter { p -> dao.programDays(p.id).any { it.workoutId == id } }
        require(users.isEmpty()) { "Used by ${users.joinToString { it.name }} — remove it from the schedule first" }
        dao.deleteWorkout(id)
    }

    suspend fun deleteProgram(id: String) {
        val p = dao.program(id) ?: return
        require(!p.builtIn) { "The built-in program can't be deleted" }
        if (dao.active()?.programId == id) activate(BASE_PROGRAM)
        dao.deleteProgram(id)
    }

    fun dowLabel(d: DayOfWeek) = d.name.lowercase().replaceFirstChar { it.uppercase() }
}
