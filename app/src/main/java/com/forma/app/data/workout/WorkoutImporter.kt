package com.forma.app.data.workout

import androidx.room.withTransaction
import com.forma.app.data.db.ExerciseEntity
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.db.ProgramDayEntity
import com.forma.app.data.db.ProgramEntity
import com.forma.app.data.db.WorkoutEntity
import com.forma.app.data.db.WorkoutSlotEntity
import com.forma.app.data.importer.RecipeImportFormat.Issue
import com.forma.app.data.importer.RecipeImportFormat.Severity
import com.forma.app.data.importer.ImportOutcome
import com.forma.app.data.importer.ImportReport
import com.forma.app.data.importer.ImportReportLine
import com.forma.app.data.importer.ImportStatus
import com.forma.app.data.seed.ExerciseKind
import com.forma.app.util.TimeProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import java.time.DayOfWeek
import kotlin.math.roundToInt

/** What a candidate is. */
enum class WorkoutItemKind { WORKOUT, PROGRAM }

/** A validated workout or program ready to be saved (entities are null when there are errors). */
data class WorkoutParsed(
    val kind: WorkoutItemKind,
    val index: Int,
    val path: String,
    val sourceId: String?,
    val name: String,
    val raw: JsonObject,
    val workout: WorkoutEntity? = null,
    val slots: List<WorkoutSlotEntity> = emptyList(),
    val newExercises: List<ExerciseEntity> = emptyList(),
    val program: ProgramEntity? = null,
    val days: List<ProgramDayEntity> = emptyList(),
    /** Workout ids a program references. */
    val references: Set<String> = emptySet(),
    val issues: List<Issue>,
) {
    val valid get() = (workout != null || program != null) && issues.none { it.severity == Severity.ERROR }
}

data class WorkoutCandidate(
    val parsed: WorkoutParsed,
    val status: ImportStatus,
    val targetId: String,
    val include: Boolean,
    /** Workouts: weekday to add it to in the active schedule (every week). Null = library only. */
    val scheduleOn: DayOfWeek? = null,
    /** Programs: make active after import. */
    val activate: Boolean = false,
    /** Programs: start next week instead of this week. */
    val startNextWeek: Boolean = false,
    val existingName: String? = null,
) {
    val errors get() = parsed.issues.filter { it.severity == Severity.ERROR }
    val warnings get() = parsed.issues.filter { it.severity == Severity.WARNING }
    val infos get() = parsed.issues.filter { it.severity == Severity.INFO }
}

data class WorkoutImportPlan(val fileIssues: List<Issue>, val candidates: List<WorkoutCandidate>)

/**
 * FORMA Workout Import v1 (`"schema": "forma.workout", "version": 1`). Same architecture and error-reporting
 * conventions as the recipe importer: validate → preview → user approval → save → report. Importing never changes the
 * active schedule unless the user explicitly chooses to schedule a workout or activate a program.
 */
class WorkoutImporter(private val db: FormaDatabase, private val time: TimeProvider) {
    companion object {
        const val SCHEMA = "forma.workout"
        const val VERSION = 1
        val json = Json { isLenient = true; ignoreUnknownKeys = true; prettyPrint = true }
        private val ID_RE = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$")
        val CATEGORIES = listOf("Upper Body", "Lower Body", "Legs", "Full Body", "Push", "Pull", "Core", "Cardio", "Mobility", "Conditioning", "Other")
        val DIFFICULTIES = listOf("Beginner", "Intermediate", "Advanced")
        private val KNOWN_TOP = setOf("schema", "version", "workouts", "programs", "generator", "generated_at", "name", "description")
        private val KNOWN_WORKOUT = setOf("id", "name", "category", "difficulty", "duration_minutes", "goal", "goals", "description", "focus", "notes",
            "exercises", "tags", "progression", "met")
        private val KNOWN_EXERCISE = setOf("id", "name", "sets", "reps", "duration", "duration_seconds", "duration_minutes", "rest_seconds", "equipment",
            "instructions", "alternatives", "muscle_groups", "type", "tracks_weight", "progression", "increment_lb")
        private val KNOWN_PROGRAM = setOf("id", "name", "description", "weeks", "schedule", "progression", "notes")

        fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
        fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60).ifEmpty { "x" }
    }

    // ── Parsing ───────────────────────────────────────────────

    suspend fun analyze(text: String): WorkoutImportPlan {
        val library = db.library().exercises()
        val fileIssues = mutableListOf<Issue>()
        val root: JsonElement = try {
            json.parseToJsonElement(text.trim().removePrefix("﻿").let { t ->
                if (t.startsWith("```")) t.removePrefix("```json").removePrefix("```").removeSuffix("```").trim() else t
            })
        } catch (e: Exception) {
            return WorkoutImportPlan(listOf(Issue(Severity.ERROR, "file", "Not valid JSON: ${e.message?.lineSequence()?.firstOrNull()}")), emptyList())
        }
        if (root is JsonObject && (root.s("schema") == "forma.recipe" || root["recipes"] is JsonArray))
            return WorkoutImportPlan(listOf(Issue(Severity.ERROR, "schema", "This is a recipe file — import it from Recipes → Import.")), emptyList())
        val (workouts, programs) = when {
            root is JsonObject && (root["workouts"] is JsonArray || root["programs"] is JsonArray) -> {
                val schema = root.s("schema"); val version = root.n("version")?.toInt()
                if (schema == null) fileIssues += Issue(Severity.WARNING, "schema", "Missing \"schema\": \"$SCHEMA\" — reading as $SCHEMA v$VERSION.")
                else if (schema != SCHEMA) fileIssues += Issue(if (schema == "forma.recipe") Severity.ERROR else Severity.WARNING, "schema",
                    if (schema == "forma.recipe") "This is a recipe file — import it from Recipes → Import." else "Unexpected schema \"$schema\".")
                if (version != null && version > VERSION) fileIssues += Issue(Severity.WARNING, "version", "File is version $version; known fields are imported and the original data is kept.")
                (root.keys - KNOWN_TOP).takeIf { it.isNotEmpty() }?.let { fileIssues += Issue(Severity.INFO, "file", "Unrecognised top-level fields ignored: ${it.joinToString()}") }
                (root["workouts"] as? JsonArray ?: JsonArray(emptyList())) to (root["programs"] as? JsonArray ?: JsonArray(emptyList()))
            }
            root is JsonObject && root["exercises"] is JsonArray -> JsonArray(listOf(root)) to JsonArray(emptyList())
            root is JsonObject && root["schedule"] is JsonArray -> JsonArray(emptyList()) to JsonArray(listOf(root))
            root is JsonArray -> root to JsonArray(emptyList())
            else -> return WorkoutImportPlan(listOf(Issue(Severity.ERROR, "file", "Expected an object with \"workouts\" and/or \"programs\".")), emptyList())
        }
        if (fileIssues.any { it.severity == Severity.ERROR }) return WorkoutImportPlan(fileIssues, emptyList())
        if (workouts.isEmpty() && programs.isEmpty()) fileIssues += Issue(Severity.ERROR, "file", "No workouts or programs found.")

        val programProgression = (programs.firstOrNull() as? JsonObject)?.get("progression") as? JsonObject
        val parsedWorkouts = workouts.mapIndexed { i, el -> parseWorkout(i, el, library, programProgression) }
        val fileWorkoutIds = parsedWorkouts.mapNotNull { it.sourceId?.lowercase() }.toSet()
        val libraryWorkoutIds = db.library().workouts().map { it.id.lowercase() }.toSet()
        val parsedPrograms = programs.mapIndexed { i, el -> parseProgram(i, el, fileWorkoutIds, libraryWorkoutIds) }
        val all = dedupe(parsedWorkouts) + dedupe(parsedPrograms)
        return WorkoutImportPlan(fileIssues, classify(all))
    }

    private fun dedupe(items: List<WorkoutParsed>): List<WorkoutParsed> {
        val seen = HashMap<String, Int>()
        return items.map { p ->
            val id = p.sourceId ?: return@map p
            val first = seen.putIfAbsent(id.lowercase(), p.index)
            if (first != null) p.copy(issues = p.issues + Issue(Severity.ERROR, "${p.path}.id", "Duplicate id \"$id\" — already used earlier in this file."))
            else p
        }
    }

    suspend fun classify(items: List<WorkoutParsed>): List<WorkoutCandidate> {
        val workouts = db.library().workouts().associateBy { it.id.lowercase() }
        val programs = db.library().programs().associateBy { it.id.lowercase() }
        return items.map { p ->
            val existing: Pair<Boolean, String>? = p.sourceId?.lowercase()?.let { id ->
                if (p.kind == WorkoutItemKind.WORKOUT) workouts[id]?.let { it.builtIn to it.name } else programs[id]?.let { it.builtIn to it.name }
            }
            when {
                !p.valid -> WorkoutCandidate(p, ImportStatus.INVALID, p.sourceId ?: "", false)
                existing?.first == true -> WorkoutCandidate(p, ImportStatus.BUILT_IN_CONFLICT, "${p.sourceId}-imported", true, existingName = existing.second)
                existing != null -> WorkoutCandidate(p, ImportStatus.UPDATE, p.sourceId!!, false, existingName = existing.second)
                else -> WorkoutCandidate(p, ImportStatus.NEW, p.sourceId!!, true)
            }
        }
    }

    private fun parseWorkout(i: Int, el: JsonElement, library: List<ExerciseEntity>, defaultProgression: JsonObject?): WorkoutParsed {
        val p = "workouts[$i]"
        val o = el as? JsonObject ?: return WorkoutParsed(WorkoutItemKind.WORKOUT, i, p, null, "Workout ${i + 1}", JsonObject(emptyMap()),
            issues = listOf(Issue(Severity.ERROR, p, "Each workout must be an object.")))
        val issues = mutableListOf<Issue>()
        fun err(path: String, m: String) { issues += Issue(Severity.ERROR, path, m) }
        fun warn(path: String, m: String) { issues += Issue(Severity.WARNING, path, m) }
        fun info(path: String, m: String) { issues += Issue(Severity.INFO, path, m) }
        (o.keys - KNOWN_WORKOUT).takeIf { it.isNotEmpty() }?.let { info(p, "Extra fields kept but not shown: ${it.joinToString()}") }

        val id = o.s("id")?.trim()
        if (id.isNullOrEmpty()) err("$p.id", "Required — e.g. \"ski-legs-001\".") else if (!ID_RE.matches(id)) err("$p.id", "Use letters, numbers, dots, dashes or underscores.")
        val name = o.s("name")?.trim()
        if (name.isNullOrEmpty()) err("$p.name", "Required.")
        val catRaw = o.s("category")?.trim()
        val category = when {
            catRaw.isNullOrEmpty() -> { err("$p.category", "Required — e.g. ${CATEGORIES.take(6).joinToString()}."); "Other" }
            else -> CATEGORIES.firstOrNull { norm(it) == norm(catRaw) } ?: run {
                info("$p.category", "\"$catRaw\" isn't a standard category; kept as written."); catRaw.split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            }
        }
        val diffRaw = o.s("difficulty")?.trim()
        val difficulty = DIFFICULTIES.firstOrNull { it.equals(diffRaw, true) } ?: run {
            if (diffRaw == null) warn("$p.difficulty", "Missing — set to Beginner.") else err("$p.difficulty", "Use Beginner, Intermediate or Advanced (got \"$diffRaw\").")
            "Beginner"
        }
        val goals = o.list("goal") + o.list("goals")
        val tags = o.list("tags").map { it.lowercase().replace(' ', '-') }.distinct()
        val workoutProgression = (o["progression"] as? JsonObject) ?: defaultProgression

        val exArr = o["exercises"] as? JsonArray
        if (exArr == null || exArr.isEmpty()) err("$p.exercises", "Required — at least one exercise.")
        val slots = mutableListOf<WorkoutSlotEntity>()
        val newExercises = LinkedHashMap<String, ExerciseEntity>()
        val libraryByName = library.associateBy { norm(it.name) }
        val usedSlotIds = HashSet<String>()
        var estMinutes = 0.0
        val workoutId = id ?: "x"

        fun resolveExercise(nameText: String, kind: ExerciseKind, pattern: String?, instruction: String?, equipment: String?, muscles: List<String>, tracks: Boolean?): ExerciseEntity {
            libraryByName[norm(nameText)]?.let { return it }
            newExercises["x-" + slug(nameText)]?.let { return it }
            val e = ExerciseEntity(
                key = "x-" + slug(nameText), name = nameText.trim(), pattern = pattern ?: "x:${slug(nameText)}", kind = kind.name,
                instruction = instruction.orEmpty(), equipment = equipment, muscleGroups = muscles,
                tracksWeight = tracks ?: (kind == ExerciseKind.STRENGTH && equipment?.lowercase()?.contains("bodyweight") != true),
                incrementLb = 5.0,
            )
            newExercises[e.key] = e
            return e
        }

        exArr?.forEachIndexed { j, ee ->
            val ep = "$p.exercises[$j]"
            val eo = ee as? JsonObject ?: run { err(ep, "Each exercise must be an object."); return@forEachIndexed }
            (eo.keys - KNOWN_EXERCISE).takeIf { it.isNotEmpty() }?.let { info(ep, "Extra fields kept: ${it.joinToString()}") }
            val ename = eo.s("name")?.trim()
            if (ename.isNullOrEmpty()) { err("$ep.name", "Required."); return@forEachIndexed }
            val exId = eo.s("id")?.trim()?.takeIf { it.isNotEmpty() } ?: slug(ename)
            if (!usedSlotIds.add(exId.lowercase())) err("$ep.id", "Duplicate exercise id \"$exId\" in this workout.")
            val sets = eo.n("sets")
            if (sets == null) err("$ep.sets", "Required — a whole number from 1 to 10.")
            else if (sets < 1 || sets > 10 || sets % 1.0 != 0.0) err("$ep.sets", "Must be a whole number from 1 to 10.")
            // Reps or duration
            val repsEl = eo["reps"]
            val repsText = when (repsEl) { is JsonPrimitive -> repsEl.contentOrNull?.trim(); else -> null }
            val timeInReps = repsText?.let { Regex("^(\\d+(?:\\.\\d+)?)\\s*(s|sec|secs|second|seconds|min|mins|minute|minutes)\\b", RegexOption.IGNORE_CASE).find(it) }
            val durMin = eo.n("duration_minutes") ?: eo.n("duration_seconds")?.let { it / 60.0 } ?: eo.s("duration")?.let(::parseDurationMinutes)
                ?: timeInReps?.let { m -> m.groupValues[1].toDouble().let { v -> if (m.groupValues[2].startsWith("s", true)) v / 60.0 else v } }
            var repRange: IntRange? = null
            var target = ""
            if (repsText != null && repsText.isNotEmpty()) {
                val m = Regex("^(\\d+)\\s*(?:[-–—]|to)\\s*(\\d+)").find(repsText)
                val single = Regex("^(\\d+)").find(repsText)
                when {
                    timeInReps != null -> {}
                    m != null -> { val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt(); if (a > b || b > 100) err("$ep.reps", "\"$repsText\" isn't a valid range.") else repRange = a..b }
                    single != null -> { val a = single.groupValues[1].toInt(); if (a !in 1..100) err("$ep.reps", "Reps must be 1–100.") else repRange = a..a }
                    Regex("amrap|max|failure", RegexOption.IGNORE_CASE).containsMatchIn(repsText) ->
                        info("$ep.reps", "\"$repsText\" kept as written; progression tips need a number or range.")
                    else -> err("$ep.reps", "\"$repsText\" isn't a rep count. Use a number, a range like \"10-12\", AMRAP, or a time like \"30 s\".")
                }
                target = repsText.replace("-", "–")
            }
            if (durMin != null) {
                if (durMin <= 0 || durMin > 180) err("$ep.duration", "Duration must be between a few seconds and 180 minutes.")
                if (target.isEmpty()) target = formatDuration(durMin)
            }
            if (target.isEmpty()) err("$ep.reps", "Give reps (e.g. \"10-12\") or a duration (e.g. \"duration_minutes\": 20).")
            val rest = eo.n("rest_seconds")?.let { if (it < 0 || it > 600) { err("$ep.rest_seconds", "Must be 0–600 seconds."); 60 } else it.roundToInt() }
                ?: run { warn("$ep.rest_seconds", "Missing — using 60 seconds."); 60 }
            val typeRaw = eo.s("type")?.uppercase()
            val kind = typeRaw?.let { t -> ExerciseKind.entries.firstOrNull { it.name == t } ?: run { warn("$ep.type", "Unknown type \"${eo.s("type")}\" — inferred instead."); null } }
                ?: if (durMin != null && repRange == null) ExerciseKind.CARDIO else ExerciseKind.STRENGTH
            val timed = repRange == null && durMin != null
            val muscles = eo.list("muscle_groups")
            val equipment = eo.s("equipment")
            val instructions = eo.s("instructions")
            val main = resolveExercise(ename, kind, null, instructions, equipment, muscles, eo.b("tracks_weight"))
            if (main.builtIn) info("$ep.name", "Matched to the library exercise \"${main.name}\" — your history and substitutions carry over.")
            val altKeys = mutableListOf<String>()
            when (val alts = eo["alternatives"]) {
                null, is JsonNull -> warn("$ep.alternatives", if (main.builtIn) "No alternatives listed — FORMA offers exercises with the same movement pattern." else "No alternatives listed — this movement can't be substituted.")
                is JsonArray -> alts.forEachIndexed { k, a ->
                    val (an, ai, aeq) = when (a) {
                        is JsonPrimitive -> Triple(a.contentOrNull?.trim(), null, null)
                        is JsonObject -> Triple(a.s("name")?.trim(), a.s("instructions"), a.s("equipment"))
                        else -> Triple(null, null, null)
                    }
                    if (an.isNullOrEmpty()) { warn("$ep.alternatives[$k]", "Empty alternative skipped."); return@forEachIndexed }
                    if (norm(an) == norm(ename)) return@forEachIndexed
                    altKeys += resolveExercise(an, kind, main.pattern, ai, aeq, muscles, null).key
                }
                else -> err("$ep.alternatives", "Must be a list of exercise names.")
            }
            val prog = (eo["progression"] as? JsonObject) ?: workoutProgression
            val progType = when (prog?.s("type")?.lowercase()?.replace(' ', '_')) {
                null -> if (repRange != null && main.tracksWeight) "DOUBLE" else "NONE"
                "double", "double_progression" -> "DOUBLE"
                "linear", "linear_progression" -> "LINEAR"
                "none", "off" -> "NONE"
                else -> { warn("$ep.progression.type", "Use double_progression, linear or none — using double_progression."); "DOUBLE" }
            }
            val inc = eo.n("increment_lb") ?: prog?.n("increment_lb")
            if (inc != null && (inc <= 0 || inc > 50)) err("$ep.increment_lb", "Must be between 0 and 50 lb.")
            val s = sets?.toInt()?.coerceIn(1, 10) ?: 1
            estMinutes += if (timed) (durMin ?: 0.0) * s else s * (0.75 + rest / 60.0)
            slots += WorkoutSlotEntity(
                workoutId = workoutId, position = j, slotKey = "$workoutId#$exId", exerciseKey = main.key, sets = s, target = target,
                repMin = repRange?.first, repMax = repRange?.last, timed = timed, restSeconds = rest,
                met = when (kind) { ExerciseKind.CARDIO -> 5.5; ExerciseKind.MOBILITY, ExerciseKind.BALANCE -> 2.5; ExerciseKind.CORE -> 3.5; else -> 4.5 },
                alternatives = altKeys.distinct(),
                instructionOverride = instructions?.takeIf { main.builtIn && it.isNotBlank() },
                progression = progType, incrementLb = inc,
            )
        }
        val duration = o.n("duration_minutes")?.let { if (it < 5 || it > 240) { err("$p.duration_minutes", "Must be 5–240 minutes."); 50 } else it.roundToInt() }
            ?: run { val e = estMinutes.roundToInt().coerceIn(10, 180); warn("$p.duration_minutes", "Missing — estimated at $e minutes."); e }
        val hasErrors = issues.any { it.severity == Severity.ERROR }
        val entity = if (hasErrors || id == null || name == null) null else WorkoutEntity(
            id = id, name = name, category = category, difficulty = difficulty, durationMinutes = duration,
            focus = o.s("focus") ?: goals.joinToString(" · "),
            notes = o.s("notes") ?: o.s("description").orEmpty(), goals = goals, tags = tags,
            met = o.n("met") ?: (slots.map { it.met }.average().takeIf { !it.isNaN() } ?: 4.5),
            builtIn = false, origin = "IMPORT", sourceJson = o.toString(),
        )
        return WorkoutParsed(WorkoutItemKind.WORKOUT, i, p, id, name ?: "Workout ${i + 1}", o, entity, slots, newExercises.values.toList(), issues = issues)
    }

    private fun parseProgram(i: Int, el: JsonElement, fileWorkouts: Set<String>, libraryWorkouts: Set<String>): WorkoutParsed {
        val p = "programs[$i]"
        val o = el as? JsonObject ?: return WorkoutParsed(WorkoutItemKind.PROGRAM, i, p, null, "Program ${i + 1}", JsonObject(emptyMap()),
            issues = listOf(Issue(Severity.ERROR, p, "Each program must be an object.")))
        val issues = mutableListOf<Issue>()
        fun err(path: String, m: String) { issues += Issue(Severity.ERROR, path, m) }
        (o.keys - KNOWN_PROGRAM).takeIf { it.isNotEmpty() }?.let { issues += Issue(Severity.INFO, p, "Extra fields kept: ${it.joinToString()}") }
        val id = o.s("id")?.trim()
        if (id.isNullOrEmpty()) err("$p.id", "Required.") else if (!ID_RE.matches(id)) err("$p.id", "Use letters, numbers, dots, dashes or underscores.")
        val name = o.s("name")?.trim()
        if (name.isNullOrEmpty()) err("$p.name", "Required.")
        val weeks = o.n("weeks")?.let { if (it < 1 || it > 52 || it % 1.0 != 0.0) { err("$p.weeks", "Must be a whole number from 1 to 52."); 1 } else it.toInt() } ?: 1
        val sched = o["schedule"] as? JsonArray
        if (sched == null || sched.isEmpty()) err("$p.schedule", "Required — a list of { \"day\": \"Sunday\", \"workout\": \"workout-id\" } entries.")
        val days = mutableListOf<ProgramDayEntity>()
        val refs = HashSet<String>()
        val seen = HashSet<Pair<Int, Int>>()
        sched?.forEachIndexed { k, se ->
            val sp = "$p.schedule[$k]"
            val so = se as? JsonObject ?: run { err(sp, "Must be an object."); return@forEachIndexed }
            val dayName = so.s("day")?.trim()
            val dow = dayName?.let { d -> DayOfWeek.entries.firstOrNull { it.name.startsWith(d.uppercase().take(3)) && d.length >= 3 } }
            if (dow == null) { err("$sp.day", "Use a weekday name like \"Sunday\" (got \"${dayName ?: ""}\")."); return@forEachIndexed }
            val weekEl = so["week"]
            val week = when {
                weekEl == null || weekEl is JsonNull -> 0
                (weekEl as? JsonPrimitive)?.contentOrNull?.lowercase() == "all" -> 0
                else -> (weekEl as? JsonPrimitive)?.doubleOrNull?.toInt()?.takeIf { it in 1..weeks } ?: run { err("$sp.week", "Must be 1–$weeks or \"all\"."); return@forEachIndexed }
            }
            if (!seen.add(week to dow.value)) err(sp, "${dow.name.lowercase().replaceFirstChar(Char::uppercase)} ${if (week == 0) "(every week)" else "in week $week"} is listed twice.")
            val rest = so.b("rest") == true
            val wid = so.s("workout")?.trim()
            if (!rest && wid.isNullOrEmpty()) err("$sp.workout", "Give a workout id, or \"rest\": true.")
            if (wid != null && wid.lowercase() !in fileWorkouts && wid.lowercase() !in libraryWorkouts) err("$sp.workout", "No workout with id \"$wid\" in this file or your library.")
            wid?.let { refs += it }
            days += ProgramDayEntity(id ?: "x", week, dow.value, if (rest) null else wid, rest, so.b("optional") == true, so.s("label") ?: if (rest) "Rest" else null)
        }
        val listed = days.filter { it.week == 0 }.map { it.dayOfWeek }.toSet()
        val gaps = DayOfWeek.entries.filter { it.value !in listed && days.none { d -> d.dayOfWeek == it.value } }
        if (gaps.isNotEmpty()) issues += Issue(Severity.INFO, "$p.schedule", "Nothing scheduled on ${gaps.joinToString { it.name.lowercase().replaceFirstChar(Char::uppercase) }}.")
        val program = if (issues.any { it.severity == Severity.ERROR } || id == null || name == null) null
        else ProgramEntity(id, name, o.s("description").orEmpty(), weeks, builtIn = false, origin = "IMPORT", sourceJson = o.toString())
        return WorkoutParsed(WorkoutItemKind.PROGRAM, i, p, id, name ?: "Program ${i + 1}", o, program = program, days = days, references = refs, issues = issues)
    }

    // ── Saving ────────────────────────────────────────────────

    /**
     * Saves approved items. Workout history is never touched: sessions keep their own titles, and slot keys are stable
     * (`workoutId#exerciseId`) so updated workouts line up with past logs.
     */
    suspend fun commit(plan: WorkoutImportPlan, cands: List<WorkoutCandidate>, library: WorkoutLibraryRepository): ImportReport {
        val now = time.nowMillis()
        val lines = mutableListOf<ImportReportLine>()
        val dao = db.library()
        val renamed = cands.filter { it.include && it.parsed.kind == WorkoutItemKind.WORKOUT && it.parsed.sourceId != null }
            .associate { it.parsed.sourceId!!.lowercase() to it.targetId }
        val skipped = mutableListOf<WorkoutCandidate>()
        // Workouts first so programs can reference them.
        for (c in cands.sortedBy { it.parsed.kind.ordinal }) {
            val p = c.parsed
            val warn = p.issues.filter { it.severity == Severity.WARNING }.map { it.toString() }
            if (!p.valid) { lines += ImportReportLine(p.name, p.sourceId, ImportOutcome.FAILED, p.issues.filter { it.severity != Severity.INFO }.map { it.toString() }); continue }
            if (!c.include) {
                skipped += c
                lines += ImportReportLine(p.name, p.sourceId, ImportOutcome.SKIPPED, listOf(if (c.status == ImportStatus.UPDATE) "Already in your library — not replaced (not approved)." else "Not selected."))
                continue
            }
            try {
                val existed: Boolean
                if (p.kind == WorkoutItemKind.WORKOUT) {
                    val w = p.workout!!
                    existed = dao.workout(c.targetId) != null
                    db.withTransaction {
                        dao.upsertExercises(p.newExercises.filter { e -> dao.exercises().none { it.key == e.key && it.builtIn } })
                        dao.replaceWorkout(
                            w.copy(id = c.targetId, importedAt = now, sortOrder = dao.workout(c.targetId)?.sortOrder ?: ((dao.maxWorkoutOrder() ?: 0) + 1)),
                            p.slots.map { it.copy(workoutId = c.targetId, slotKey = it.slotKey.replaceBefore('#', c.targetId)) },
                        )
                    }
                    val details = warn.toMutableList()
                    if (c.status == ImportStatus.BUILT_IN_CONFLICT) details += "Saved as ${c.targetId}; the built-in workout wasn't changed."
                    if (c.scheduleOn != null) {
                        val catalog = WorkoutCatalogLoader.load(db)
                        val date = com.forma.engine.TrendAnalyzer.weekStart(time.today()).plusDays((c.scheduleOn.value % 7).toLong())
                        library.schedule(date, c.targetId, rest = false, optional = false, scope = ScheduleScope.EVERY_WEEK, catalog = catalog)
                        details += "Scheduled every ${c.scheduleOn.name.lowercase().replaceFirstChar(Char::uppercase)} in your active program."
                    } else details += "Saved to the library (your schedule wasn't changed)."
                    lines += ImportReportLine(w.name, p.sourceId, if (existed) ImportOutcome.UPDATED else ImportOutcome.IMPORTED, details)
                } else {
                    val prog = p.program!!
                    val missing = p.references.filter { ref -> dao.workout(renamed[ref.lowercase()] ?: ref) == null }
                    if (missing.isNotEmpty()) {
                        lines += ImportReportLine(prog.name, p.sourceId, ImportOutcome.FAILED, listOf("Uses workouts that weren't imported: ${missing.joinToString()}."))
                        continue
                    }
                    existed = dao.program(c.targetId) != null
                    dao.replaceProgram(prog.copy(id = c.targetId, importedAt = now),
                        p.days.map { d -> d.copy(programId = c.targetId, workoutId = d.workoutId?.let { renamed[it.lowercase()] ?: it }) })
                    val details = warn.toMutableList()
                    if (c.activate) {
                        val start = if (c.startNextWeek) time.today().plusWeeks(1) else time.today()
                        library.activate(c.targetId, start)
                        details += "Now your active program (week 1 starts ${com.forma.engine.TrendAnalyzer.weekStart(start)}). Past workouts are kept."
                    } else details += "Saved to your programs (not active)."
                    lines += ImportReportLine(prog.name, p.sourceId, if (existed) ImportOutcome.UPDATED else ImportOutcome.IMPORTED, details)
                }
            } catch (e: Exception) {
                lines += ImportReportLine(p.name, p.sourceId, ImportOutcome.FAILED, listOf("Couldn't save: ${e.message}"))
            }
        }
        return ImportReport(lines, plan.fileIssues)
    }

    // ── Export ────────────────────────────────────────────────

    /** Exports workouts and programs in forma.workout v1. Programs pull in the workouts they use. */
    suspend fun export(workoutIds: Collection<String>?, programIds: Collection<String>?): String {
        val catalog = WorkoutCatalogLoader.load(db)
        val programs = catalog.programs.filter { programIds == null || it.id in programIds }
        val wantedWorkouts = (workoutIds?.toSet() ?: catalog.workoutsById.keys) + programs.flatMap { p -> p.days.mapNotNull { it.workoutId } }
        val workouts = catalog.workouts.filter { it.id in wantedWorkouts }
        val root = buildJsonObject {
            put("schema", SCHEMA); put("version", VERSION); put("generator", "FORMA")
            put("generated_at", java.time.Instant.ofEpochMilli(time.nowMillis()).toString())
            put("workouts", JsonArray(workouts.map { w -> exportWorkout(w, catalog) }))
            put("programs", JsonArray(programs.map { p -> exportProgram(p) }))
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun exportWorkout(w: WorkoutDef, catalog: WorkoutCatalog) = buildJsonObject {
        val e = w.entity
        put("id", e.id); put("name", e.name); put("category", e.category); put("difficulty", e.difficulty)
        put("duration_minutes", e.durationMinutes); put("goal", JsonArray(e.goals.map(::JsonPrimitive)))
        if (e.focus.isNotBlank()) put("focus", e.focus)
        if (e.notes.isNotBlank()) put("notes", e.notes)
        put("tags", JsonArray(e.tags.map(::JsonPrimitive)))
        put("exercises", JsonArray(w.slots.map { s ->
            val ex = catalog.exercise(s.exerciseKey)
            buildJsonObject {
                put("id", s.slotKey.substringAfter('#', s.slotKey))
                put("name", ex.name); put("sets", s.sets)
                if (s.timed) put("duration", s.target) else put("reps", s.target.replace('–', '-'))
                put("rest_seconds", s.restSeconds)
                ex.equipment?.let { put("equipment", it) }
                put("instructions", s.instructionOverride ?: ex.instruction)
                put("type", ex.kind.lowercase())
                put("alternatives", JsonArray(catalog.substitutesFor(s).drop(1).map { JsonPrimitive(it.name) }))
                if (ex.muscleGroups.isNotEmpty()) put("muscle_groups", JsonArray(ex.muscleGroups.map(::JsonPrimitive)))
                put("progression", buildJsonObject {
                    put("type", when (s.progression) { "LINEAR" -> "linear"; "NONE" -> "none"; else -> "double_progression" })
                    put("increment_lb", s.incrementLb ?: ex.incrementLb)
                })
            }
        }))
    }

    private fun exportProgram(p: ProgramDef) = buildJsonObject {
        put("id", p.id); put("name", p.entity.name); put("description", p.entity.description); put("weeks", p.weeks)
        put("schedule", JsonArray(p.days.sortedWith(compareBy({ it.week }, { it.dayOfWeek % 7 })).map { d ->
            buildJsonObject {
                put("week", if (d.week == 0) JsonPrimitive("all") else JsonPrimitive(d.week))
                put("day", DayOfWeek.of(d.dayOfWeek).name.lowercase().replaceFirstChar(Char::uppercase))
                if (d.rest) put("rest", true) else d.workoutId?.let { put("workout", it) }
                if (d.optional) put("optional", true)
                d.label?.let { put("label", it) }
            }
        }))
    }

    private fun parseDurationMinutes(s: String): Double? {
        val t = s.lowercase()
        val n = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:[-–]\\s*(\\d+(?:\\.\\d+)?))?").find(t) ?: return null
        val v = n.groupValues[2].toDoubleOrNull() ?: n.groupValues[1].toDouble()
        return when { t.contains("sec") || Regex("\\d\\s*s\\b").containsMatchIn(t) -> v / 60.0; t.contains("h") && !t.contains("ch") -> v * 60; else -> v }
    }

    private fun formatDuration(min: Double) = if (min < 1) "${(min * 60).roundToInt()} s" else "${if (min % 1.0 == 0.0) min.toInt() else min} min"
}

/** Loads a catalog snapshot directly (used outside of flows). */
object WorkoutCatalogLoader {
    suspend fun load(db: FormaDatabase): WorkoutCatalog {
        val d = db.library()
        val programs = d.programs()
        return WorkoutCatalog(d.exercises(), d.workouts(), d.workouts().flatMap { d.slots(it.id) }, programs,
            programs.flatMap { d.programDays(it.id) }, d.active())
    }
}

private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
private fun JsonObject.n(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
private fun JsonObject.b(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
private fun JsonObject.list(k: String): List<String> = when (val e = this[k]) {
    is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
    is JsonPrimitive -> e.contentOrNull?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    else -> emptyList()
}

