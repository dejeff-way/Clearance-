package com.forma.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1") fun observe(): Flow<ProfileEntity?>
    @Query("SELECT * FROM profile WHERE id = 1") suspend fun get(): ProfileEntity?
    @Upsert suspend fun upsert(p: ProfileEntity)
}

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_log WHERE epochDay = :day ORDER BY eatenAt, id") fun observeDay(day: Long): Flow<List<FoodLogEntity>>
    @Query("SELECT * FROM food_log WHERE epochDay BETWEEN :from AND :to ORDER BY eatenAt") fun observeRange(from: Long, to: Long): Flow<List<FoodLogEntity>>
    @Query("SELECT * FROM food_log WHERE id = :id") suspend fun get(id: Long): FoodLogEntity?
    @Query(
        "SELECT epochDay, SUM(kcal) AS kcal, SUM(proteinG) AS proteinG, SUM(carbsG) AS carbsG, SUM(fatG) AS fatG, " +
            "COUNT(*) AS items FROM food_log GROUP BY epochDay ORDER BY epochDay",
    )
    fun observeDailyTotals(): Flow<List<DailyFoodTotals>>
    @Insert suspend fun insert(e: FoodLogEntity): Long
    @Update suspend fun update(e: FoodLogEntity)
    @Query("DELETE FROM food_log WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM food_log") suspend fun all(): List<FoodLogEntity>
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY sortOrder") fun observeAll(): Flow<List<RecipeEntity>>
    @Query("SELECT * FROM recipes WHERE id = :id") fun observe(id: String): Flow<RecipeEntity?>
    @Query("SELECT * FROM recipes WHERE id = :id") suspend fun get(id: String): RecipeEntity?
    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :id ORDER BY position") fun observeIngredients(id: String): Flow<List<IngredientEntity>>
    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :id ORDER BY position") suspend fun ingredients(id: String): List<IngredientEntity>
    @Query("SELECT * FROM recipe_ingredients ORDER BY recipeId, position") fun observeAllIngredients(): Flow<List<IngredientEntity>>
    @Query("SELECT * FROM recipes") suspend fun all(): List<RecipeEntity>
    @Upsert suspend fun upsertRecipe(r: RecipeEntity)
    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :id") suspend fun clearIngredients(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertIngredients(i: List<IngredientEntity>)
    @Query("UPDATE recipes SET isFavorite = :fav WHERE id = :id") suspend fun setFavorite(id: String, fav: Boolean)
    @Query("UPDATE recipes SET selectedMilk = :milk WHERE id = :id") suspend fun setMilk(id: String, milk: String?)
    @Query("UPDATE recipes SET photoUri = :uri WHERE id = :id") suspend fun setPhoto(id: String, uri: String?)

    @Query("DELETE FROM recipes WHERE id = :id AND builtIn = 0") suspend fun deleteCustomRecipe(id: String)
    @Query("SELECT MAX(sortOrder) FROM recipes") suspend fun maxSortOrder(): Int?

    @Transaction
    suspend fun deleteCustom(id: String) { clearIngredients(id); deleteCustomRecipe(id) }

    @Transaction
    suspend fun replaceRecipe(r: RecipeEntity, ingredients: List<IngredientEntity>) {
        upsertRecipe(r); clearIngredients(r.id); insertIngredients(ingredients)
    }
}

@Dao
interface EnergyDao {
    @Query("SELECT * FROM daily_energy ORDER BY epochDay") fun observeAll(): Flow<List<DailyEnergyEntity>>
    @Query("SELECT * FROM daily_energy WHERE epochDay = :day") suspend fun get(day: Long): DailyEnergyEntity?
    @Upsert suspend fun upsert(e: DailyEnergyEntity)
    @Query("SELECT * FROM daily_energy") suspend fun all(): List<DailyEnergyEntity>
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM exercise_sessions ORDER BY COALESCE(startTime, createdAt)") fun observeAll(): Flow<List<ExerciseSessionEntity>>
    @Query("SELECT * FROM exercise_sessions WHERE source = 'HEALTH_CONNECT' AND epochDay BETWEEN :from AND :to")
    suspend fun hcInRange(from: Long, to: Long): List<ExerciseSessionEntity>
    @Query("SELECT * FROM exercise_sessions WHERE hcId = :hcId") suspend fun byHcId(hcId: String): ExerciseSessionEntity?
    @Insert suspend fun insert(e: ExerciseSessionEntity): Long
    @Update suspend fun update(e: ExerciseSessionEntity)
    @Query("DELETE FROM exercise_sessions WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM exercise_sessions") suspend fun all(): List<ExerciseSessionEntity>
    @Query("SELECT COUNT(*) FROM exercise_sessions") suspend fun count(): Int
}

@Dao
interface CarryoverDao {
    @Query("SELECT * FROM carryover ORDER BY epochDay DESC, createdAt DESC") fun observeAll(): Flow<List<CarryoverEntity>>
    @Insert suspend fun insert(e: CarryoverEntity): Long
    @Update suspend fun update(e: CarryoverEntity)
    @Query("DELETE FROM carryover WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM carryover") suspend fun all(): List<CarryoverEntity>
}

@Dao
interface BodyDao {
    @Query("SELECT * FROM weights ORDER BY measuredAt") fun observeWeights(): Flow<List<WeightEntity>>
    @Query("SELECT * FROM weights WHERE source = 'HEALTH_CONNECT' AND epochDay BETWEEN :from AND :to")
    suspend fun hcWeightsInRange(from: Long, to: Long): List<WeightEntity>
    @Query("SELECT * FROM weights WHERE hcId = :hcId") suspend fun weightByHcId(hcId: String): WeightEntity?
    @Query("SELECT * FROM weights ORDER BY measuredAt DESC LIMIT 1") suspend fun latestWeight(): WeightEntity?
    @Insert suspend fun insertWeight(e: WeightEntity): Long
    @Update suspend fun updateWeight(e: WeightEntity)
    @Query("DELETE FROM weights WHERE id = :id") suspend fun deleteWeight(id: Long)
    @Query("SELECT * FROM weights") suspend fun allWeights(): List<WeightEntity>
    @Query("SELECT COUNT(*) FROM weights") suspend fun weightCount(): Int

    @Query("SELECT * FROM measurements ORDER BY epochDay, createdAt") fun observeMeasurements(): Flow<List<MeasurementEntity>>
    @Insert suspend fun insertMeasurement(e: MeasurementEntity): Long
    @Update suspend fun updateMeasurement(e: MeasurementEntity)
    @Query("DELETE FROM measurements WHERE id = :id") suspend fun deleteMeasurement(id: Long)
    @Query("SELECT * FROM measurements") suspend fun allMeasurements(): List<MeasurementEntity>
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_sessions ORDER BY epochDay DESC") fun observeSessions(): Flow<List<WorkoutSessionEntity>>
    @Query("SELECT * FROM workout_sessions WHERE epochDay = :day AND templateKey = :key") suspend fun session(day: Long, key: String): WorkoutSessionEntity?
    @Query("SELECT * FROM workout_sessions WHERE id = :id") suspend fun sessionById(id: Long): WorkoutSessionEntity?
    @Insert suspend fun insertSession(s: WorkoutSessionEntity): Long
    @Update suspend fun updateSession(s: WorkoutSessionEntity)
    @Query("DELETE FROM workout_sessions WHERE id = :id") suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM exercise_logs") fun observeExerciseLogs(): Flow<List<ExerciseLogEntity>>
    @Query("SELECT * FROM exercise_logs WHERE sessionId = :sessionId AND slotKey = :slot") suspend fun exerciseLog(sessionId: Long, slot: String): ExerciseLogEntity?
    @Query("SELECT * FROM exercise_logs WHERE sessionId = :sessionId") suspend fun exerciseLogsFor(sessionId: Long): List<ExerciseLogEntity>
    @Insert suspend fun insertExerciseLog(e: ExerciseLogEntity): Long
    @Update suspend fun updateExerciseLog(e: ExerciseLogEntity)
    @Query("DELETE FROM exercise_logs WHERE sessionId = :sessionId") suspend fun deleteLogsFor(sessionId: Long)

    @Query("SELECT * FROM set_logs") fun observeSetLogs(): Flow<List<SetLogEntity>>
    @Query("SELECT * FROM set_logs WHERE exerciseLogId = :logId AND setIndex = :idx") suspend fun setLog(logId: Long, idx: Int): SetLogEntity?
    @Insert suspend fun insertSetLog(s: SetLogEntity): Long
    @Update suspend fun updateSetLog(s: SetLogEntity)
    @Query("DELETE FROM set_logs WHERE exerciseLogId = :logId") suspend fun deleteSetsFor(logId: Long)

    @Query("SELECT * FROM substitution_prefs") fun observePrefs(): Flow<List<SubstitutionPrefEntity>>
    @Upsert suspend fun upsertPref(p: SubstitutionPrefEntity)
    @Query("DELETE FROM substitution_prefs WHERE slotKey = :slot") suspend fun deletePref(slot: String)

    @Query("SELECT * FROM workout_sessions") suspend fun allSessions(): List<WorkoutSessionEntity>
    @Query("SELECT * FROM exercise_logs") suspend fun allExerciseLogs(): List<ExerciseLogEntity>
    @Query("SELECT * FROM set_logs") suspend fun allSetLogs(): List<SetLogEntity>
    @Query("SELECT * FROM substitution_prefs") suspend fun allPrefs(): List<SubstitutionPrefEntity>
}

@Dao
interface SyncMetaDao {
    @Query("SELECT * FROM sync_meta WHERE id = 1") fun observe(): Flow<SyncMetaEntity?>
    @Query("SELECT * FROM sync_meta WHERE id = 1") suspend fun get(): SyncMetaEntity?
    @Upsert suspend fun upsert(m: SyncMetaEntity)
}

@Dao
interface WorkoutLibraryDao {
    @Query("SELECT * FROM exercises") fun observeExercises(): Flow<List<ExerciseEntity>>
    @Query("SELECT * FROM exercises") suspend fun exercises(): List<ExerciseEntity>
    @Upsert suspend fun upsertExercises(e: List<ExerciseEntity>)

    @Query("SELECT * FROM workouts ORDER BY sortOrder, name") fun observeWorkouts(): Flow<List<WorkoutEntity>>
    @Query("SELECT * FROM workouts") suspend fun workouts(): List<WorkoutEntity>
    @Query("SELECT * FROM workouts WHERE id = :id") suspend fun workout(id: String): WorkoutEntity?
    @Query("SELECT * FROM workout_slots ORDER BY workoutId, position") fun observeSlots(): Flow<List<WorkoutSlotEntity>>
    @Query("SELECT * FROM workout_slots WHERE workoutId = :id ORDER BY position") suspend fun slots(id: String): List<WorkoutSlotEntity>
    @Upsert suspend fun upsertWorkout(w: WorkoutEntity)
    @Query("DELETE FROM workout_slots WHERE workoutId = :id") suspend fun clearSlots(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSlots(s: List<WorkoutSlotEntity>)
    @Query("DELETE FROM workouts WHERE id = :id AND builtIn = 0") suspend fun deleteWorkoutRow(id: String)
    @Query("SELECT MAX(sortOrder) FROM workouts") suspend fun maxWorkoutOrder(): Int?

    @Transaction suspend fun replaceWorkout(w: WorkoutEntity, slots: List<WorkoutSlotEntity>) { upsertWorkout(w); clearSlots(w.id); insertSlots(slots) }
    @Transaction suspend fun deleteWorkout(id: String) { clearSlots(id); deleteWorkoutRow(id) }

    @Query("SELECT * FROM programs ORDER BY builtIn DESC, name") fun observePrograms(): Flow<List<ProgramEntity>>
    @Query("SELECT * FROM programs") suspend fun programs(): List<ProgramEntity>
    @Query("SELECT * FROM programs WHERE id = :id") suspend fun program(id: String): ProgramEntity?
    @Query("SELECT * FROM program_days") fun observeProgramDays(): Flow<List<ProgramDayEntity>>
    @Query("SELECT * FROM program_days WHERE programId = :id") suspend fun programDays(id: String): List<ProgramDayEntity>
    @Upsert suspend fun upsertProgram(p: ProgramEntity)
    @Upsert suspend fun upsertProgramDays(d: List<ProgramDayEntity>)
    @Query("DELETE FROM program_days WHERE programId = :id") suspend fun clearProgramDays(id: String)
    @Query("DELETE FROM program_days WHERE programId = :id AND week = :week AND dayOfWeek = :dow") suspend fun deleteProgramDay(id: String, week: Int, dow: Int)
    @Query("DELETE FROM programs WHERE id = :id AND builtIn = 0") suspend fun deleteProgramRow(id: String)

    @Transaction suspend fun replaceProgram(p: ProgramEntity, days: List<ProgramDayEntity>) { upsertProgram(p); clearProgramDays(p.id); upsertProgramDays(days) }
    @Transaction suspend fun deleteProgram(id: String) { clearProgramDays(id); deleteProgramRow(id) }

    @Query("SELECT * FROM active_program WHERE id = 1") fun observeActive(): Flow<ActiveProgramEntity?>
    @Query("SELECT * FROM active_program WHERE id = 1") suspend fun active(): ActiveProgramEntity?
    @Upsert suspend fun setActive(a: ActiveProgramEntity)
}
