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
        ExerciseEntity::class, WorkoutEntity::class, WorkoutSlotEntity::class, ProgramEntity::class, ProgramDayEntity::class,
        ActiveProgramEntity::class,
    ],
    version = 4,
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
    abstract fun library(): WorkoutLibraryDao

    companion object {
        /** v3 → v4: wearable metrics (heart rate, HRV, sleep stages, distance…), step goal and sync tokens. */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_energy ADD COLUMN hcBasalKcal REAL")
                db.execSQL("ALTER TABLE daily_energy ADD COLUMN distanceM REAL")
                db.execSQL("ALTER TABLE daily_energy ADD COLUMN floors REAL")
                db.execSQL("ALTER TABLE daily_energy ADD COLUMN avgHr REAL")
                db.execSQL("ALTER TABLE daily_energy ADD COLUMN minHr REAL")
                db.execSQL("ALTER TABLE daily_energy ADD COLUMN maxHr REAL")
                db.execSQL("ALTER TABLE daily_energy ADD COLUMN restingHr REAL")
                db.execSQL("ALTER TABLE daily_energy ADD COLUMN hrvMs REAL")
                db.execSQL("ALTER TABLE daily_energy ADD COLUMN spo2Pct REAL")
                db.execSQL("ALTER TABLE daily_energy ADD COLUMN respRate REAL")
                db.execSQL("ALTER TABLE daily_energy ADD COLUMN bodyFatPct REAL")
                db.execSQL("ALTER TABLE daily_energy ADD COLUMN sleepDeepMin REAL")
                db.execSQL("ALTER TABLE daily_energy ADD COLUMN sleepLightMin REAL")
                db.execSQL("ALTER TABLE daily_energy ADD COLUMN sleepRemMin REAL")
                db.execSQL("ALTER TABLE daily_energy ADD COLUMN sleepAwakeMin REAL")
                db.execSQL("ALTER TABLE daily_energy ADD COLUMN sources TEXT")
                db.execSQL("ALTER TABLE exercise_sessions ADD COLUMN avgHr REAL")
                db.execSQL("ALTER TABLE exercise_sessions ADD COLUMN maxHr REAL")
                db.execSQL("ALTER TABLE exercise_sessions ADD COLUMN distanceM REAL")
                db.execSQL("ALTER TABLE exercise_sessions ADD COLUMN dataOrigin TEXT")
                db.execSQL("ALTER TABLE profile ADD COLUMN stepGoal INTEGER NOT NULL DEFAULT 10000")
                db.execSQL("ALTER TABLE profile ADD COLUMN wearableAdjustmentPct INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE profile ADD COLUMN maxActivityBonusKcal INTEGER NOT NULL DEFAULT 500")
                db.execSQL("ALTER TABLE sync_meta ADD COLUMN changesToken TEXT")
                db.execSQL("ALTER TABLE sync_meta ADD COLUMN tokenTypes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sync_meta ADD COLUMN diagnostics TEXT")
                db.execSQL("ALTER TABLE sync_meta ADD COLUMN lastSyncMode TEXT")
            }
        }
        /** v2 → v3: workout library, programs and active schedule (seeded from the built-in plan on first launch). */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `exercises` (`key` TEXT NOT NULL, `name` TEXT NOT NULL, `pattern` TEXT NOT NULL, `kind` TEXT NOT NULL, `instruction` TEXT NOT NULL, `equipment` TEXT, `muscleGroups` TEXT NOT NULL, `tracksWeight` INTEGER NOT NULL, `incrementLb` REAL NOT NULL, `builtIn` INTEGER NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `workouts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `durationMinutes` INTEGER NOT NULL, `focus` TEXT NOT NULL, `notes` TEXT NOT NULL, `goals` TEXT NOT NULL, `tags` TEXT NOT NULL, `met` REAL NOT NULL, `builtIn` INTEGER NOT NULL, `origin` TEXT NOT NULL, `importedAt` INTEGER, `sourceJson` TEXT, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_slots` (`workoutId` TEXT NOT NULL, `position` INTEGER NOT NULL, `slotKey` TEXT NOT NULL, `exerciseKey` TEXT NOT NULL, `sets` INTEGER NOT NULL, `target` TEXT NOT NULL, `repMin` INTEGER, `repMax` INTEGER, `timed` INTEGER NOT NULL, `restSeconds` INTEGER NOT NULL, `met` REAL NOT NULL, `alternatives` TEXT NOT NULL, `instructionOverride` TEXT, `progression` TEXT NOT NULL, `incrementLb` REAL, PRIMARY KEY(`workoutId`, `position`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_slots_slotKey` ON `workout_slots` (`slotKey`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `programs` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `weeks` INTEGER NOT NULL, `builtIn` INTEGER NOT NULL, `origin` TEXT NOT NULL, `importedAt` INTEGER, `sourceJson` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `program_days` (`programId` TEXT NOT NULL, `week` INTEGER NOT NULL, `dayOfWeek` INTEGER NOT NULL, `workoutId` TEXT, `rest` INTEGER NOT NULL, `optional` INTEGER NOT NULL, `label` TEXT, PRIMARY KEY(`programId`, `week`, `dayOfWeek`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `active_program` (`id` INTEGER NOT NULL, `programId` TEXT NOT NULL, `startEpochDay` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
            }
        }

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
            return b.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
        }
    }
}
