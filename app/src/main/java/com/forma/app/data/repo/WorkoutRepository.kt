package com.forma.app.data.repo

import com.forma.app.data.db.ExerciseLogEntity
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.db.SetLogEntity
import com.forma.app.data.db.SubstitutionPrefEntity
import com.forma.app.data.db.WorkoutSessionEntity
import com.forma.app.util.TimeProvider
import androidx.room.withTransaction
import java.time.LocalDate

class WorkoutRepository(private val db: FormaDatabase, private val time: TimeProvider) {
    private val dao = db.workouts()
    val sessions = dao.observeSessions()
    val exerciseLogs = dao.observeExerciseLogs()
    val setLogs = dao.observeSetLogs()
    val prefs = dao.observePrefs()

    /** Returns the session for [date]+[templateKey], creating it (and one log per slot) if needed. */
    suspend fun ensureSession(date: LocalDate, workoutId: String): Long = db.withTransaction {
        val workout = db.library().workout(workoutId) ?: error("Unknown workout $workoutId")
        val slots = db.library().slots(workoutId)
        val existing = dao.session(date.toEpochDay(), workoutId)
        val id = existing?.id ?: dao.insertSession(
            WorkoutSessionEntity(epochDay = date.toEpochDay(), templateKey = workoutId, title = workout.name),
        )
        val prefs = dao.allPrefs().associate { it.slotKey to it.exerciseKey }
        slots.forEach { slot ->
            if (dao.exerciseLog(id, slot.slotKey) == null) {
                dao.insertExerciseLog(ExerciseLogEntity(sessionId = id, slotKey = slot.slotKey,
                    exerciseKey = prefs[slot.slotKey] ?: slot.exerciseKey))
            }
        }
        id
    }

    private suspend fun log(sessionId: Long, slotKey: String): ExerciseLogEntity =
        dao.exerciseLog(sessionId, slotKey) ?: error("No log for $slotKey")

    suspend fun setCompleted(sessionId: Long, slotKey: String, done: Boolean) = db.withTransaction {
        val l = log(sessionId, slotKey)
        dao.updateExerciseLog(l.copy(completed = done, updatedAt = time.nowMillis()))
        val s = dao.sessionById(sessionId) ?: return@withTransaction
        val now = time.nowMillis()
        val started = s.startedAt ?: now
        val all = dao.exerciseLogsFor(sessionId)
        val allDone = all.isNotEmpty() && all.all { it.completed }
        val base = if (allDone) closePause(s, now) else s
        dao.updateSession(base.copy(
            startedAt = started,
            completedAt = if (allDone) (s.completedAt ?: now) else null,
        ))
    }

    suspend fun substitute(sessionId: Long, slotKey: String, exerciseKey: String, remember: Boolean) = db.withTransaction {
        val l = log(sessionId, slotKey)
        if (l.exerciseKey != exerciseKey) {
            dao.deleteSetsFor(l.id) // sets belonged to the previous exercise
            dao.updateExerciseLog(l.copy(exerciseKey = exerciseKey, updatedAt = time.nowMillis()))
        }
        val template = db.library().workouts().firstNotNullOfOrNull { w -> db.library().slots(w.id).firstOrNull { it.slotKey == slotKey } }?.exerciseKey
            ?: l.exerciseKey
        if (remember) {
            if (exerciseKey == template) dao.deletePref(slotKey) else dao.upsertPref(SubstitutionPrefEntity(slotKey, exerciseKey))
        }
    }

    suspend fun clearRememberedSubstitution(slotKey: String) = dao.deletePref(slotKey)

    suspend fun logSet(sessionId: Long, slotKey: String, index: Int, weight: Double?, reps: Int?, minutes: Double?) =
        db.withTransaction {
            val l = log(sessionId, slotKey)
            val cur = dao.setLog(l.id, index)
            if (cur == null) dao.insertSetLog(SetLogEntity(exerciseLogId = l.id, setIndex = index, weightLb = weight,
                reps = reps, durationMin = minutes))
            else dao.updateSetLog(cur.copy(weightLb = weight, reps = reps, durationMin = minutes))
            val s = dao.sessionById(sessionId)
            if (s != null && s.startedAt == null) dao.updateSession(s.copy(startedAt = time.nowMillis()))
        }

    suspend fun setExerciseNotes(sessionId: Long, slotKey: String, notes: String) {
        val l = log(sessionId, slotKey)
        dao.updateExerciseLog(l.copy(notes = notes.ifBlank { null }))
    }

    suspend fun setSessionNotes(sessionId: Long, notes: String) {
        val s = dao.sessionById(sessionId) ?: return
        dao.updateSession(s.copy(notes = notes.ifBlank { null }))
    }

    /** Finishes the workout. A running pause is closed first. [minutes] overrides the measured duration. */
    suspend fun finish(sessionId: Long, minutes: Int? = null) {
        val s = dao.sessionById(sessionId) ?: return
        val now = time.nowMillis()
        val closed = closePause(s, now)
        val done = closed.copy(startedAt = closed.startedAt ?: now, completedAt = now)
        dao.updateSession(if (minutes != null) withDuration(done, minutes) else done)
    }

    suspend fun reopen(sessionId: Long) {
        val s = dao.sessionById(sessionId) ?: return
        dao.updateSession(s.copy(completedAt = null, pausedAt = null))
    }

    /** Starts the timer (no-op if already started). */
    suspend fun startTimer(sessionId: Long) {
        val s = dao.sessionById(sessionId) ?: return
        if (s.startedAt == null) dao.updateSession(s.copy(startedAt = time.nowMillis()))
    }

    suspend fun pause(sessionId: Long) {
        val s = dao.sessionById(sessionId) ?: return
        if (s.completedAt != null || s.pausedAt != null) return
        val now = time.nowMillis()
        dao.updateSession(s.copy(startedAt = s.startedAt ?: now, pausedAt = now))
    }

    suspend fun resume(sessionId: Long) {
        val s = dao.sessionById(sessionId) ?: return
        dao.updateSession(closePause(s, time.nowMillis()))
    }

    /** Corrects the recorded duration of a finished (or running) workout. */
    suspend fun setDuration(sessionId: Long, minutes: Int) {
        require(minutes in 1..600) { "Enter between 1 and 600 minutes" }
        val s = dao.sessionById(sessionId) ?: return
        dao.updateSession(withDuration(closePause(s, time.nowMillis()), minutes))
    }

    private fun closePause(s: WorkoutSessionEntity, now: Long) =
        if (s.pausedAt == null) s else s.copy(pausedMs = s.pausedMs + (now - s.pausedAt).coerceAtLeast(0), pausedAt = null)

    /** Keeps the end time and moves the start so active time equals [minutes] (pauses cleared). */
    private fun withDuration(s: WorkoutSessionEntity, minutes: Int): WorkoutSessionEntity {
        val end = s.completedAt ?: time.nowMillis()
        return s.copy(startedAt = end - minutes * 60_000L, pausedMs = 0, pausedAt = null)
    }

    suspend fun deleteSession(sessionId: Long) = db.withTransaction {
        dao.exerciseLogsFor(sessionId).forEach { dao.deleteSetsFor(it.id) }
        dao.deleteLogsFor(sessionId)
        dao.deleteSession(sessionId)
    }
}
