package com.forma.app

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.importer.ImportOutcome
import com.forma.app.data.importer.ImportStatus
import com.forma.app.di.AppContainer
import com.forma.engine.Portion
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(application = TestFormaApp::class, sdk = [35])
class RecipeImporterTest {
    private lateinit var c: AppContainer

    @Before fun setUp() = runBlocking {
        TestEnv.time = FixedTime(); TestEnv.health = FakeHealth()
        c = TestEnv.container(ApplicationProvider.getApplicationContext())
        c.initialize()
    }

    @After fun tearDown() { c.db.close() }

    @Test fun `new recipes import and are immediately usable in the food log`() = runBlocking {
        val plan = c.recipeImporter.analyzeJson(ImportFixtures.USER_EXAMPLE)
        assertEquals(ImportStatus.NEW, plan.candidates.single().status)
        assertTrue(plan.candidates.single().include)
        val report = c.recipeImporter.commit(plan, plan.candidates)
        assertEquals(1, report.count(ImportOutcome.IMPORTED))
        val r = c.db.recipes().get("chicken-shawarma-001")!!
        assertEquals("LUNCH", r.category); assertFalse(r.builtIn); assertNotNull(r.importedAt)
        c.food.logRecipe(r.id, "LUNCH", Portion.LARGE, 1.0, TestEnv.time.today().toEpochDay(), TestEnv.time.nowMillis())
        assertEquals(540 * 1.3, c.db.food().all().single().kcal, 0.01)
    }

    @Test fun `matching id needs approval and keeps favourite and photo`() = runBlocking {
        val first = c.recipeImporter.analyzeJson(ImportFixtures.USER_EXAMPLE)
        c.recipeImporter.commit(first, first.candidates)
        c.recipes.setFavorite("chicken-shawarma-001", true)
        c.recipes.setPhoto("chicken-shawarma-001", "/tmp/photo.jpg")
        val updated = ImportFixtures.USER_EXAMPLE.replace("\"calories\": 540", "\"calories\": 500").replace("High-Protein Chicken", "Lighter Chicken")
        val plan = c.recipeImporter.analyzeJson(updated)
        val cand = plan.candidates.single()
        assertEquals(ImportStatus.UPDATE, cand.status)
        assertFalse("updates are opt-in", cand.include)
        val skipped = c.recipeImporter.commit(plan, plan.candidates)
        assertEquals(1, skipped.count(ImportOutcome.SKIPPED))
        assertEquals("High-Protein Chicken Shawarma Bowl", c.db.recipes().get("chicken-shawarma-001")!!.name)

        val approved = c.recipeImporter.commit(plan, listOf(cand.copy(include = true)))
        assertEquals(1, approved.count(ImportOutcome.UPDATED))
        val r = c.db.recipes().get("chicken-shawarma-001")!!
        assertEquals("Lighter Chicken Shawarma Bowl", r.name)
        assertTrue(r.isFavorite); assertEquals("/tmp/photo.jpg", r.photoUri)
        assertEquals(500.0, c.recipes.full(r.id)!!.macros(Portion.STANDARD).kcal, 0.01)
        assertEquals(1, c.db.recipes().all().count { !it.builtIn })
    }

    @Test fun `built-in ids are never overwritten`() = runBlocking {
        val json = ImportFixtures.file(ImportFixtures.recipe("salmon_poke_bowl", name = "My Poke"))
        val plan = c.recipeImporter.analyzeJson(json)
        val cand = plan.candidates.single()
        assertEquals(ImportStatus.BUILT_IN_CONFLICT, cand.status)
        c.recipeImporter.commit(plan, plan.candidates)
        assertEquals("Salmon Poke Bowl", c.db.recipes().get("salmon_poke_bowl")!!.name)
        assertEquals("My Poke", c.db.recipes().get("salmon_poke_bowl-imported")!!.name)
    }

    @Test fun `bulk import reports every outcome`() = runBlocking {
        val many = (1..20).map { ImportFixtures.recipe("bulk-$it") } + ImportFixtures.recipe("bad", category = "Brunch")
        val plan = c.recipeImporter.analyzeJson(ImportFixtures.file(*many.toTypedArray()))
        assertEquals(21, plan.candidates.size)
        val report = c.recipeImporter.commit(plan, plan.candidates)
        assertEquals(20, report.count(ImportOutcome.IMPORTED))
        assertEquals(1, report.count(ImportOutcome.FAILED))
        assertTrue(report.asText().contains("Brunch"))
        assertEquals(20, c.db.recipes().all().count { it.origin == "IMPORT" })
    }

    @Test fun `embedded images are saved`() = runBlocking {
        val png = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        val plan = c.recipeImporter.analyzeJson(ImportFixtures.file(ImportFixtures.recipe("img-1", extra = ", \"image\": \"$png\"")))
        c.recipeImporter.commit(plan, plan.candidates)
        val path = c.db.recipes().get("img-1")!!.photoUri
        assertNotNull(path); assertTrue(File(path!!).length() > 0)
    }

    @Test fun `export and re-import round trip`() = runBlocking {
        val plan = c.recipeImporter.analyzeJson(ImportFixtures.file(ImportFixtures.recipe("rt-1"), ImportFixtures.recipe("rt-2")))
        c.recipeImporter.commit(plan, plan.candidates)
        val exported = c.recipeImporter.export(listOf("rt-1", "rt-2"))
        val root = Json.parseToJsonElement(exported).jsonObject
        assertEquals("forma.recipe", root["schema"]!!.jsonPrimitive.content)
        assertEquals(2, root["recipes"]!!.jsonArray.size)
        val before = c.recipes.full("rt-1")!!.macros(Portion.LARGE).kcal
        // Re-import into a fresh library
        c.db.recipes().deleteCustom("rt-1"); c.db.recipes().deleteCustom("rt-2")
        val again = c.recipeImporter.analyzeJson(exported)
        assertTrue(again.candidates.all { it.parsed.valid && it.status == ImportStatus.NEW })
        c.recipeImporter.commit(again, again.candidates)
        assertEquals(before, c.recipes.full("rt-1")!!.macros(Portion.LARGE).kcal, 0.5)
        // The whole built-in library also exports and validates
        val all = c.recipeImporter.analyzeJson(c.recipeImporter.export())
        assertTrue(all.candidates.joinToString { it.parsed.issues.filter { i -> i.severity.name == "ERROR" }.toString() }, all.candidates.all { it.parsed.valid })
    }

    @Test fun `database migrates from version 1 keeping existing data`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("migrate.db")
        val schema = Json.parseToJsonElement(File("schemas/com.forma.app.data.db.FormaDatabase/1.json").readText()).jsonObject["database"]!!.jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath("migrate.db").apply { parentFile?.mkdirs() }, null)
        schema["entities"]!!.jsonArray.forEach { e ->
            val o = e.jsonObject; val table = o["tableName"]!!.jsonPrimitive.content
            sql.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            o["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        schema["setupQueries"]!!.jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }
        sql.execSQL("INSERT INTO recipes (id,name,category,kosherStatus,isFish,summary,prepMinutes,cookMinutes,prepSteps,cookSteps,kosherNotes,safetyNotes,tips,standardPortionLabel,largePortionLabel,usesMilkChoice,isFavorite,artKey,builtIn,seedVersion,sortOrder) " +
            "VALUES ('custom_x','Old custom','LUNCH','PAREVE',0,'',0,0,'[]','[\"Mix\"]','[]','[]','[]','S','L',0,1,'custom_LUNCH',0,1,99)")
        sql.execSQL("INSERT INTO food_log (epochDay,eatenAt,createdAt,mealType,name,servings,kcal,proteinG) VALUES (20000,1,1,'LUNCH','Old entry',1.0,450,30)")
        sql.version = 1
        sql.close()

        val db = androidx.room.Room.databaseBuilder(ctx, FormaDatabase::class.java, "migrate.db").addMigrations(FormaDatabase.MIGRATION_1_2, FormaDatabase.MIGRATION_2_3, FormaDatabase.MIGRATION_3_4, FormaDatabase.MIGRATION_4_5, FormaDatabase.MIGRATION_5_6).build()
        runBlocking {
            val r = db.recipes().get("custom_x")!!
            assertEquals("USER", r.origin); assertTrue(r.isFavorite); assertEquals(emptyList<String>(), r.tags); assertEquals(1, r.servings)
            assertEquals("Old entry", db.food().all().single().name)
        }
        db.close()
        ctx.deleteDatabase("migrate.db")
    }
}
