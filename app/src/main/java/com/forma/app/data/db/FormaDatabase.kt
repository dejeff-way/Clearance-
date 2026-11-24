package com.forma.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ProfileEntity::class, FoodLogEntity::class, RecipeEntity::class, IngredientEntity::class,
        DailyEnergyEntity::class, ExerciseSessionEntity::class, CarryoverEntity::class, WeightEntity::class,
        MeasurementEntity::class, WorkoutSessionEntity::class, ExerciseLogEntity::class, SetLogEntity::class,
        SubstitutionPrefEntity::class, SyncMetaEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FormaDatabase : RoomDatabase() {
    abstract fun profile(): ProfileDao
    abstract fun food(): FoodDao
    abstract fun recipes(): RecipeDao
    abstract fun energy(): EnergyDao
    abstract fun sessions(): SessionDao
    abstract fun carryover(): CarryoverDao
    abstract fun body(): BodyDao
    abstract fun workouts(): WorkoutDao
    abstract fun syncMeta(): SyncMetaDao

    companion object {
        /** v1 → v2: recipe import fields. Existing data is kept; user-created recipes are marked USER. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE recipes ADD COLUMN fiberG REAL")
                db.execSQL("ALTER TABLE recipes ADD COLUMN servings INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE recipes ADD COLUMN storageNotes TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE recipes ADD COLUMN nutritionSource TEXT NOT NULL DEFAULT 'ESTIMATED'")
                db.execSQL("ALTER TABLE recipes ADD COLUMN origin TEXT NOT NULL DEFAULT 'BUILT_IN'")
                db.execSQL("ALTER TABLE recipes ADD COLUMN importedAt INTEGER")
                db.execSQL("ALTER TABLE recipes ADD COLUMN sourceJson TEXT")
                db.execSQL("UPDATE recipes SET origin = 'USER' WHERE builtIn = 0")
            }
        }

        fun create(context: Context, inMemory: Boolean = false): FormaDatabase {
            val b = if (inMemory) Room.inMemoryDatabaseBuilder(context, FormaDatabase::class.java)
            else Room.databaseBuilder(context, FormaDatabase::class.java, "forma.db")
            return b.addMigrations(MIGRATION_1_2).build()
        }
    }
}
