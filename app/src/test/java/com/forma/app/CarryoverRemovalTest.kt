package com.forma.app

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.data.db.FormaDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(application = TestFormaApp::class, sdk = [35])
class CarryoverRemovalTest {
    @Test fun `upgrading drops the carryover table and keeps everything else`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("migrate10.db")
        val schema = Json.parseToJsonElement(File("schemas/com.forma.app.data.db.FormaDatabase/9.json").readText()).jsonObject["database"]!!.jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath("migrate10.db").apply { parentFile?.mkdirs() }, null)
        schema["entities"]!!.jsonArray.forEach { e ->
            val o = e.jsonObject; val t = o["tableName"]!!.jsonPrimitive.content
            sql.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t))
            o["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t)) }
        }
        schema["setupQueries"]!!.jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }
        sql.execSQL("INSERT INTO food_log (id, epochDay, eatenAt, createdAt, mealType, name, servings, kcal, proteinG) VALUES (1, 20500, 1, 1, 'LUNCH', 'Kept', 1, 500, 20)")
        sql.execSQL("INSERT INTO carryover (id, epochDay, kcal, kind, cancelled, createdAt, updatedAt) VALUES (1, 20500, 300, 'OVERAGE', 0, 1, 1)")
        sql.version = 9; sql.close()
        val db = androidx.room.Room.databaseBuilder(ctx, FormaDatabase::class.java, "migrate10.db")
            .addMigrations(FormaDatabase.MIGRATION_8_9, FormaDatabase.MIGRATION_9_10).build()
        runBlocking { assertEquals("Kept", db.food().all().single().name) }
        val tables = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'").use { cur ->
            buildList { while (cur.moveToNext()) add(cur.getString(0)) }
        }
        assertFalse("carryover" in tables)
        db.close(); ctx.deleteDatabase("migrate10.db")
    }
}
