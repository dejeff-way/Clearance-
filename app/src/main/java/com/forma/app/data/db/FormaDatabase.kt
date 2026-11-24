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
    version = 1,
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
        fun create(context: Context, inMemory: Boolean = false): FormaDatabase {
            val b = if (inMemory) Room.inMemoryDatabaseBuilder(context, FormaDatabase::class.java)
            else Room.databaseBuilder(context, FormaDatabase::class.java, "forma.db")
            return b.build()
        }
    }
}
