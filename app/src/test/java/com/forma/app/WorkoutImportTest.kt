package com.forma.app

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.importer.ImportOutcome
import com.forma.app.data.importer.ImportStatus
import com.forma.app.data.importer.RecipeImportFormat.Severity
import com.forma.app.data.workout.ScheduleScope
import com.forma.app.data.workout.WorkoutCatalogLoader
import com.forma.app.data.workout.WorkoutItemKind
import com.forma.app.data.workout.WorkoutLibraryRepository
import com.forma.app.di.AppContainer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(application = TestFormaApp::class, sdk = [35])
class WorkoutImportTest {
    private lateinit var c: AppContainer
    private val today = LocalDate.of(2026, 11, 24) // Tuesday
    private val sunday = LocalDate.of(2026, 11, 22)

    @Before fun setUp() = runBlocking {
        TestEnv.time = FixedTime(); TestEnv.health = FakeHealth()
        c = TestEnv.container(ApplicationProvider.getApplicationContext())
        c.initialize()
    }

    @After fun tearDown() { c.db.close() }

    private suspend fun catalog() = WorkoutCatalogLoader.load(c.db)
    private suspend fun importAll(text: String, tweak: (com.forma.app.data.workout.WorkoutCandidate) -> com.forma.app.data.workout.WorkoutCandidate = { it }) =
        c.workoutImporter.analyze(text).let { p -> c.workoutImporter.commit(p, p.candidates.map(tweak), c.library) }

    @Test fun `built-in program is seeded and active`() = runBlocking {
        val cat = catalog()
        assertEquals(WorkoutLibraryRepository.BASE_PROGRAM, cat.activeProgram!!.id)
        assertEquals("TUE", cat.planFor(today).workout!!.id)
        assertTrue(cat.planFor(sunday.plusDays(5)).rest)       // Friday
        assertTrue(cat.planFor(sunday.plusDays(6)).optional)   // Saturday
        assertEquals(5, cat.requiredInWeekOf(today))
    }

    @Test fun `user example imports to the library without touching the schedule`() = runBlocking {
        val plan = c.workoutImporter.analyze(WorkoutFixtures.USER_EXAMPLE)
        val cand = plan.candidates.single()
        assertTrue(cand.parsed.issues.toString(), cand.parsed.valid)
        assertEquals(ImportStatus.NEW, cand.status)
        assertTrue(cand.parsed.issues.none { it.severity == Severity.WARNING })
        // Names resolve to the existing library so history and substitutions carry over.
        assertEquals("leg_press", cand.parsed.slots[0].exerciseKey)
        assertEquals("seated_leg_curl", cand.parsed.slots[1].exerciseKey)
        assertTrue("goblet_squat" in cand.parsed.slots[0].alternatives)
        assertTrue("lying_leg_curl" in cand.parsed.slots[1].alternatives)
        assertEquals(10, cand.parsed.slots[0].repMin); assertEquals(12, cand.parsed.slots[0].repMax)
        val report = c.workoutImporter.commit(plan, plan.candidates, c.library)
        assertEquals(1, report.count(ImportOutcome.IMPORTED))
        val cat = catalog()
        val w = cat.workoutsById["ski-legs-001"]!!
        assertEquals(listOf("ski-prep", "lower-body", "strength"), w.entity.tags)
        assertEquals("Hack Squat", cat.substitutesFor(w.slots[0]).first { it.key == "x-hack-squat" }.name)
        assertEquals("squat", cat.exercise("x-hack-squat").pattern) // grouped with its parent movement
        // Schedule unchanged
        assertEquals(WorkoutLibraryRepository.BASE_PROGRAM, cat.activeProgram!!.id)
        assertEquals("TUE", cat.planFor(today).workout!!.id)
    }

    @Test fun `multi-week program with week overrides, activation and cycling`() = runBlocking {
        val report = importAll(WorkoutFixtures.EIGHT_WEEK) { if (it.parsed.kind == WorkoutItemKind.PROGRAM) it.copy(activate = true) else it }
        assertEquals(4, report.count(ImportOutcome.IMPORTED))
        val cat = catalog()
        assertEquals("ski-8-week", cat.activeProgram!!.id)
        assertEquals(sunday.toEpochDay(), cat.active!!.startEpochDay)
        assertEquals(1, cat.weekOf(today))
        assertEquals("w-b", cat.planFor(sunday.plusDays(1)).workout!!.id)                    // week 1 Monday
        assertEquals("w-peak", cat.planFor(sunday.plusWeeks(4).plusDays(1)).workout!!.id)    // week 5 Monday override
        assertEquals("w-b", cat.planFor(sunday.plusWeeks(5).plusDays(1)).workout!!.id)       // week 6 back to default
        assertEquals(1, cat.weekOf(sunday.plusWeeks(8)))                                       // repeats after week 8
        assertNull(cat.planFor(today).workout)                                                 // Tuesday unscheduled
        assertEquals(3, cat.requiredInWeekOf(today))                                           // Sun, Mon, Wed
        assertTrue(cat.planFor(sunday.plusDays(6)).optional)
    }

    @Test fun `validation reports exact paths`() = runBlocking {
        val bad = """{ "schema": "forma.workout", "version": 1,
          "workouts": [ { "id": "w1", "name": "Bad", "category": "Legs", "difficulty": "Expert",
             "exercises": [ { "name": "Squat", "reps": "lots" }, { "id": "x", "sets": 3, "reps": "8" } ] } ],
          "programs": [ { "id": "p1", "name": "P", "weeks": 4, "schedule": [ { "day": "Funday", "workout": "w1" }, { "day": "Monday", "workout": "missing" }, { "week": 9, "day": "Tuesday", "workout": "w1" } ] } ] }"""
        val plan = c.workoutImporter.analyze(bad)
        val errs = plan.candidates.flatMap { it.errors }.map { "${it.path}: ${it.message}" }
        assertTrue(errs.toString(), errs.any { it.startsWith("workouts[0].difficulty") })
        assertTrue(errs.any { it.startsWith("workouts[0].exercises[0].sets") })
        assertTrue(errs.any { it.startsWith("workouts[0].exercises[0].reps") })
        assertTrue(errs.any { it.startsWith("workouts[0].exercises[1].name") })
        assertTrue(errs.any { it.startsWith("programs[0].schedule[0].day") })
        assertTrue(errs.any { it.startsWith("programs[0].schedule[1].workout") && it.contains("missing") })
        assertTrue(errs.any { it.startsWith("programs[0].schedule[2].week") })
        assertTrue(plan.candidates.all { it.status == ImportStatus.INVALID })
        val recipeFile = c.workoutImporter.analyze("""{ "schema": "forma.recipe", "version": 1, "recipes": [] }""")
        assertTrue(recipeFile.fileIssues.any { it.severity == Severity.ERROR && it.message.contains("recipe file") })
        val holds = c.workoutImporter.analyze(WorkoutFixtures.file(WorkoutFixtures.workout("h1", extraExercise =
            ", { \"id\": \"plank\", \"name\": \"Plank\", \"sets\": 3, \"reps\": \"45 s\", \"rest_seconds\": 45, \"type\": \"core\", \"alternatives\": [\"Dead Bug\"] }")))
        val plank = holds.candidates.single().parsed.slots.first { it.slotKey.endsWith("#plank") }
        assertTrue(plank.timed); assertNull(plank.repMin); assertEquals("plank", plank.exerciseKey)
        val dupes = c.workoutImporter.analyze(WorkoutFixtures.file(WorkoutFixtures.workout("same"), WorkoutFixtures.workout("SAME")))
        assertTrue(dupes.candidates[1].errors.any { it.message.contains("Duplicate id") })
    }

    @Test fun `updates need approval and keep workout history`() = runBlocking {
        importAll(WorkoutFixtures.file(WorkoutFixtures.workout("gym-1", sets = 3)))
        val sid = c.workouts.ensureSession(today, "gym-1")
        c.workouts.logSet(sid, "gym-1#squat", 0, 185.0, 5, null)
        c.workouts.finish(sid)

        val plan = c.workoutImporter.analyze(WorkoutFixtures.file(WorkoutFixtures.workout("gym-1", name = "Gym 1 v2", sets = 5)))
        val cand = plan.candidates.single()
        assertEquals(ImportStatus.UPDATE, cand.status); assertFalse(cand.include)
        assertEquals(1, c.workoutImporter.commit(plan, plan.candidates, c.library).count(ImportOutcome.SKIPPED))
        assertEquals(3, catalog().workoutsById["gym-1"]!!.slots[0].sets)

        c.workoutImporter.commit(plan, listOf(cand.copy(include = true)), c.library)
        val w = catalog().workoutsById["gym-1"]!!
        assertEquals("Gym 1 v2", w.name); assertEquals(5, w.slots[0].sets)
        assertEquals("gym-1#squat", w.slots[0].slotKey) // stable key → history lines up
        val session = c.db.workouts().sessionById(sid)!!
        assertNotNull(session.completedAt)
        assertEquals(185.0, c.db.workouts().allSetLogs().single().weightLb!!, 0.0)
        assertEquals("gym-1#squat", c.db.workouts().exerciseLogsFor(sid).first { it.exerciseKey == "x-barbell-back-squat" }.slotKey)
    }

    @Test fun `built-in workout ids import as a copy`() = runBlocking {
        importAll(WorkoutFixtures.file(WorkoutFixtures.workout("MON", name = "My legs")))
        val cat = catalog()
        assertEquals("Legs + Ski Prep", cat.workoutsById["MON"]!!.name)
        assertEquals("My legs", cat.workoutsById["MON-imported"]!!.name)
    }

    @Test fun `scheduling an imported workout copies the built-in program instead of editing it`() = runBlocking {
        importAll(WorkoutFixtures.file(WorkoutFixtures.workout("cardio-x"))) { it.copy(scheduleOn = DayOfWeek.WEDNESDAY) }
        val cat = catalog()
        assertEquals(WorkoutLibraryRepository.MY_PROGRAM, cat.activeProgram!!.id)
        assertEquals("cardio-x", cat.planFor(sunday.plusDays(3)).workout!!.id)
        assertEquals("TUE", cat.planFor(today).workout!!.id) // other days unchanged
        val base = cat.programs.first { it.id == WorkoutLibraryRepository.BASE_PROGRAM }
        assertEquals("WED", base.dayFor(1, DayOfWeek.WEDNESDAY)!!.workoutId)
        // This-week-only change on a multi-day basis
        c.library.schedule(sunday.plusDays(1), null, rest = true, optional = false, scope = ScheduleScope.THIS_WEEK, catalog = cat)
        assertTrue(catalog().planFor(sunday.plusDays(1)).rest)
    }

    @Test fun `deleting a scheduled workout is refused and programs can be switched back`() = runBlocking {
        importAll(WorkoutFixtures.EIGHT_WEEK) { if (it.parsed.kind == WorkoutItemKind.PROGRAM) it.copy(activate = true) else it }
        val err = runCatching { c.library.deleteWorkout("w-a") }.exceptionOrNull()
        assertTrue(err?.message?.contains("8-Week Ski Prep") == true)
        c.library.deleteProgram("ski-8-week")
        assertEquals(WorkoutLibraryRepository.BASE_PROGRAM, catalog().activeProgram!!.id)
        c.library.deleteWorkout("w-a")
        assertNull(catalog().workoutsById["w-a"])
    }

    @Test fun `export and re-import round trip`() = runBlocking {
        importAll(WorkoutFixtures.EIGHT_WEEK)
        val exported = c.workoutImporter.export(emptyList(), listOf("ski-8-week"))
        val root = Json.parseToJsonElement(exported).jsonObject
        assertEquals("forma.workout", root["schema"]!!.jsonPrimitive.content)
        assertEquals(3, root["workouts"]!!.jsonArray.size)
        val plan = c.workoutImporter.analyze(exported)
        assertTrue(plan.candidates.joinToString { it.errors.toString() }, plan.candidates.all { it.parsed.valid && it.status == ImportStatus.UPDATE })
        val whole = c.workoutImporter.analyze(c.workoutImporter.export(null, null))
        assertTrue(whole.candidates.joinToString { it.errors.toString() }, whole.candidates.all { it.parsed.valid })
    }

    @Test fun `database migrates from version 2 keeping workout history`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("migrate3.db")
        val schema = Json.parseToJsonElement(File("schemas/com.forma.app.data.db.FormaDatabase/2.json").readText()).jsonObject["database"]!!.jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath("migrate3.db").apply { parentFile?.mkdirs() }, null)
        schema["entities"]!!.jsonArray.forEach { e ->
            val o = e.jsonObject; val t = o["tableName"]!!.jsonPrimitive.content
            sql.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t))
            o["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t)) }
        }
        schema["setupQueries"]!!.jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }
        sql.execSQL("INSERT INTO workout_sessions (id, epochDay, templateKey, title, startedAt, completedAt) VALUES (1, 20000, 'MON', 'Legs + Ski Prep', 1, 3000000)")
        sql.execSQL("INSERT INTO exercise_logs (id, sessionId, slotKey, exerciseKey, completed, updatedAt) VALUES (1, 1, 'MON_1', 'goblet_squat', 1, 1)")
        sql.execSQL("INSERT INTO substitution_prefs (slotKey, exerciseKey) VALUES ('MON_1', 'goblet_squat')")
        sql.version = 2; sql.close()
        val db = androidx.room.Room.databaseBuilder(ctx, FormaDatabase::class.java, "migrate3.db")
            .addMigrations(FormaDatabase.MIGRATION_1_2, FormaDatabase.MIGRATION_2_3, FormaDatabase.MIGRATION_3_4, FormaDatabase.MIGRATION_4_5, FormaDatabase.MIGRATION_5_6).build()
        runBlocking {
            WorkoutLibraryRepository(db, TestEnv.time).seed()
            val cat = WorkoutCatalogLoader.load(db)
            assertEquals(WorkoutLibraryRepository.BASE_PROGRAM, cat.activeProgram!!.id)
            assertEquals("MON_1", cat.workoutsById["MON"]!!.slots[0].slotKey)
            assertEquals("Legs + Ski Prep", db.workouts().sessionById(1)!!.title)
            assertEquals("goblet_squat", db.workouts().allPrefs().single().exerciseKey)
        }
        db.close(); ctx.deleteDatabase("migrate3.db")
    }
}
