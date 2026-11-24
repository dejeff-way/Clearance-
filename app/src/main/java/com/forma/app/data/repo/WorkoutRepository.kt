package com.forma.app.data.repo

import com.forma.app.data.db.ExerciseLogEntity
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.db.SetLogEntity
import com.forma.app.data.db.SubstitutionPrefEntity
import com.forma.app.data.db.WorkoutSessionEntity
import com.forma.app.data.seed.WorkoutProgram
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
    suspend fun ensureSession(date: LocalDate, templateKey: String): Long = db.withTransaction {
        val day = WorkoutProgram.byKey(templateKey) ?: error("Unknown workout $templateKey")
        val existing = dao.session(date.toEpochDay(), templateKey)
        val id = existing?.id ?: dao.insertSession(
            WorkoutSessionEntity(epochDay = date.toEpochDay(), templateKey = templateKey, title = day.title),
        )
        val prefs = dao.allPrefs().associate { it.slotKey to it.exerciseKey }
        day.slots.forEach { slot ->
            if (dao.exerciseLog(id, slot.key) == null) {
                dao.insertExerciseLog(ExerciseLogEntity(sessionId = id, slotKey = slot.key,
                    exerciseKey = prefs[slot.key] ?: slot.exerciseKey))
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
        dao.updateSession(s.copy(
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
        val template = WorkoutProgram.days.flatMap { it.slots }.first { it.key == slotKey }.exerciseKey
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

    suspend fun finish(sessionId: Long) {
        val s = dao.sessionById(sessionId) ?: return
        val now = time.nowMillis()
        dao.updateSession(s.copy(startedAt = s.startedAt ?: now, completedAt = now))
    }

    suspend fun reopen(sessionId: Long) {
        val s = dao.sessionById(sessionId) ?: return
        dao.updateSession(s.copy(completedAt = null))
    }

    suspend fun deleteSession(sessionId: Long) = db.withTransaction {
        dao.exerciseLogsFor(sessionId).forEach { dao.deleteSetsFor(it.id) }
        dao.deleteLogsFor(sessionId)
        dao.deleteSession(sessionId)
    }
}
