package com.forma.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.forma.app.data.db.DailyEnergyEntity
import com.forma.app.data.db.ExerciseLogEntity
import com.forma.app.data.db.ExerciseSessionEntity
import com.forma.app.data.db.FoodLogEntity
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.db.MeasurementEntity
import com.forma.app.data.db.ProfileEntity
import com.forma.app.data.db.SetLogEntity
import com.forma.app.data.db.SubstitutionPrefEntity
import com.forma.app.data.db.WeightEntity
import com.forma.app.data.db.WorkoutSessionEntity
import com.forma.app.data.repo.EnergyModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RecipeState(val id: String, val isFavorite: Boolean, val selectedMilk: String?)

@Serializable
data class BackupFile(
    val format: String = "forma-backup",
    val version: Int = 1,
    val exportedAt: Long,
    val profile: ProfileEntity?,
    val food: List<FoodLogEntity>,
    val recipeStates: List<RecipeState>,
    val energy: List<DailyEnergyEntity>,
    val sessions: List<ExerciseSessionEntity>,
    val weights: List<WeightEntity>,
    val measurements: List<MeasurementEntity>,
    val workouts: List<WorkoutSessionEntity>,
    val exerciseLogs: List<ExerciseLogEntity>,
    val setLogs: List<SetLogEntity>,
    val substitutions: List<SubstitutionPrefEntity>,
    val customRecipes: List<com.forma.app.data.db.RecipeEntity> = emptyList(),
    val customIngredients: List<com.forma.app.data.db.IngredientEntity> = emptyList(),
    val mealPlan: List<com.forma.app.data.db.MealPlanEntity> = emptyList(),
    val pantryAlways: List<com.forma.app.data.db.PantryItemEntity> = emptyList(),
    val weeklyReports: List<com.forma.app.data.db.WeeklyReportEntity> = emptyList(),
)

class BackupManager(private val context: Context, private val db: FormaDatabase) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun snapshot(now: Long): BackupFile = BackupFile(
        exportedAt = now,
        profile = db.profile().get(),
        food = db.food().all(),
        recipeStates = db.recipes().all().map { RecipeState(it.id, it.isFavorite, it.selectedMilk) },
        energy = db.energy().all(),
        sessions = db.sessions().all(),
        weights = db.body().allWeights(),
        measurements = db.body().allMeasurements(),
        workouts = db.workouts().allSessions(),
        exerciseLogs = db.workouts().allExerciseLogs(),
        setLogs = db.workouts().allSetLogs(),
        substitutions = db.workouts().allPrefs(),
        customRecipes = db.recipes().all().filter { !it.builtIn },
        customIngredients = db.recipes().all().filter { !it.builtIn }.flatMap { db.recipes().ingredients(it.id) },
        mealPlan = db.mealPlan().all(),
        weeklyReports = db.reports().all(),
    )

    fun encode(b: BackupFile): String = json.encodeToString(BackupFile.serializer(), b)
    fun decode(text: String): BackupFile {
        val b = json.decodeFromString(BackupFile.serializer(), text)
        require(b.format == "forma-backup") { "This file is not a FORMA backup" }
        return b
    }

    suspend fun exportTo(uri: Uri, now: Long) {
        val text = encode(snapshot(now))
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error("Couldn't open the file for writing")
    }

    suspend fun importFrom(uri: Uri) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Couldn't read the file")
        restore(decode(text))
    }

    /** Replaces all user data with the backup. Built-in recipes are kept; favourites and milk choices restored. */
    suspend fun restore(b: BackupFile) = db.withTransaction {
        db.openHelper.writableDatabase.apply {
            listOf("food_log", "daily_energy", "exercise_sessions", "weights", "measurements",
                "workout_sessions", "exercise_logs", "set_logs", "substitution_prefs").forEach { execSQL("DELETE FROM $it") }
        }
        b.profile?.let { db.profile().upsert(it) }
        b.food.forEach { db.food().insert(it) }
        b.recipeStates.forEach { db.recipes().setFavorite(it.id, it.isFavorite); db.recipes().setMilk(it.id, it.selectedMilk) }
        b.energy.forEach { db.energy().upsert(it) }
        b.sessions.forEach { db.sessions().insert(it) }
        b.weights.forEach { db.body().insertWeight(it) }
        b.measurements.forEach { db.body().insertMeasurement(it) }
        b.workouts.forEach { db.workouts().insertSession(it) }
        b.exerciseLogs.forEach { db.workouts().insertExerciseLog(it) }
        b.setLogs.forEach { db.workouts().insertSetLog(it) }
        b.substitutions.forEach { db.workouts().upsertPref(it) }
        db.recipes().all().filter { !it.builtIn }.forEach { db.recipes().deleteCustom(it.id) }
        db.openHelper.writableDatabase.execSQL("DELETE FROM meal_plan")
        db.mealPlan().insertAll(b.mealPlan.map { it.copy(loggedFoodId = null) })
        b.pantryAlways.forEach { db.mealPlan().upsertPantry(it) }
        b.weeklyReports.forEach { db.reports().upsert(it) }
        b.customRecipes.forEach { r -> db.recipes().replaceRecipe(r.copy(photoUri = null), b.customIngredients.filter { it.recipeId == r.id }) }
    }

    /** Daily history as CSV for spreadsheets. */
    fun historyCsv(model: EnergyModel, days: Int): String = buildString {
        appendLine("date,intake_kcal,protein_g,expenditure_kcal,expenditure_source,budget_kcal,deficit_kcal,weight_lb")
        model.history(days).forEach { (d, s) ->
            val w = model.s.weights.filter { it.epochDay == d.toEpochDay() }.map { it.weightLb }.average()
            appendLine(listOf(
                d, s.consumed.kcal.toInt(), s.consumed.proteinG.toInt(), s.expenditure.projectedDayKcal.toInt(),
                s.expenditure.source.name, s.budget.finalBudget, s.projectedDeficit,
                if (w.isNaN()) "" else "%.1f".format(w),
            ).joinToString(","))
        }
    }

    fun writeText(uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error("Couldn't open the file for writing")
    }

    suspend fun resetAll() = db.withTransaction {
        db.openHelper.writableDatabase.apply {
            listOf("food_log", "daily_energy", "exercise_sessions", "weights", "measurements",
                "workout_sessions", "exercise_logs", "set_logs", "substitution_prefs", "sync_meta", "profile", "meal_plan", "shopping_checks", "pantry_always")
                .forEach { execSQL("DELETE FROM $it") }
            execSQL("DELETE FROM recipe_ingredients WHERE recipeId IN (SELECT id FROM recipes WHERE builtIn = 0)")
            execSQL("DELETE FROM recipes WHERE builtIn = 0")
            execSQL("UPDATE recipes SET isFavorite = 0, photoUri = NULL, selectedMilk = NULL")
        }
    }
}
