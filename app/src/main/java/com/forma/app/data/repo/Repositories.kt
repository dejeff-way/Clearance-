package com.forma.app.data.repo

import com.forma.app.data.db.ExerciseSessionEntity
import com.forma.app.data.db.FoodLogEntity
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.db.IngredientEntity
import com.forma.app.data.db.MeasurementEntity
import com.forma.app.data.db.ProfileEntity
import com.forma.app.data.db.RecipeEntity
import com.forma.app.data.db.WeightEntity
import com.forma.app.data.seed.MilkOption
import com.forma.app.data.seed.RecipeSeed
import com.forma.app.util.TimeProvider
import com.forma.engine.Macros
import com.forma.engine.Portion
import com.forma.engine.PortionScaler
import com.forma.engine.ScalableIngredient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach

class ProfileRepository(private val db: FormaDatabase, private val time: TimeProvider) {
    val profile: Flow<ProfileEntity> = db.profile().observe()
        .onEach { if (it == null) ensure() }
        .filterNotNull()

    suspend fun ensure(): ProfileEntity =
        db.profile().get() ?: ProfileEntity(programStartEpochDay = time.today().toEpochDay()).also {
            db.profile().upsert(it)
        }

    suspend fun update(transform: (ProfileEntity) -> ProfileEntity) {
        val cur = ensure()
        db.profile().upsert(transform(cur).copy(id = 1, updatedAt = time.nowMillis()))
    }
}

/** A recipe with its ingredients and live nutrition helpers. */
data class RecipeFull(val recipe: RecipeEntity, val ingredients: List<IngredientEntity>) {
    val milk: MilkOption? get() =
        if (!recipe.usesMilkChoice) null
        else MilkOption.from(recipe.selectedMilk) ?: MilkOption.from(recipe.defaultMilk) ?: MilkOption.ONE_PERCENT

    fun macros(portion: Portion, servings: Double = 1.0, milkOverride: MilkOption? = null): Macros =
        RecipeMath.macros(ingredients, portion, servings, milkOverride ?: milk)
}

/** Unit used for a recipe whose nutrition was entered as a per-serving total rather than per ingredient. */
const val TOTAL_UNIT = "serving-total"

object RecipeMath {
    fun scalable(i: IngredientEntity, milk: MilkOption?): ScalableIngredient {
        val m = if (i.isMilk) (milk ?: MilkOption.ONE_PERCENT).forMl(i.amount)
        else Macros(i.kcal, i.proteinG, i.carbsG, i.fatG)
        return ScalableIngredient(i.amount, i.largeAmount, m, i.optional)
    }

    fun macros(ings: List<IngredientEntity>, portion: Portion, servings: Double, milk: MilkOption?): Macros =
        PortionScaler.total(ings.map { scalable(it, milk) }, portion, servings)

    fun ingredientMacros(i: IngredientEntity, portion: Portion, milk: MilkOption?): Macros =
        scalable(i, milk).macros(portion)
}

class RecipeRepository(private val db: FormaDatabase) {
    val recipes: Flow<List<RecipeEntity>> = db.recipes().observeAll()
    val allIngredients: Flow<List<IngredientEntity>> = db.recipes().observeAllIngredients()
    fun recipe(id: String) = db.recipes().observe(id)
    fun ingredients(id: String) = db.recipes().observeIngredients(id)

    suspend fun full(id: String): RecipeFull? {
        val r = db.recipes().get(id) ?: return null
        return RecipeFull(r, db.recipes().ingredients(id))
    }

    /** Inserts or refreshes built-in recipes, preserving favourites, photos and milk choice. */
    suspend fun seed() {
        val existing = db.recipes().all().associateBy { it.id }
        RecipeSeed.all.forEachIndexed { i, s ->
            val cur = existing[s.id]
            if (cur == null || cur.seedVersion < RecipeSeed.SEED_VERSION) {
                val (r, ings) = RecipeSeed.toEntities(s, i)
                db.recipes().replaceRecipe(
                    r.copy(isFavorite = cur?.isFavorite ?: false, photoUri = cur?.photoUri, selectedMilk = cur?.selectedMilk),
                    ings,
                )
            }
        }
    }

    /** Creates or updates a user recipe. Built-in recipes are never overwritten (copy them instead). */
    suspend fun saveCustom(recipe: RecipeEntity, ingredients: List<IngredientEntity>): String {
        val existing = db.recipes().get(recipe.id)
        require(existing == null || !existing.builtIn) { "Built-in recipes can't be edited — make a copy" }
        require(recipe.name.isNotBlank()) { "Give the recipe a name" }
        val order = existing?.sortOrder ?: ((db.recipes().maxSortOrder() ?: 0) + 1)
        val r = recipe.copy(
            builtIn = false, sortOrder = order, isFavorite = existing?.isFavorite ?: recipe.isFavorite,
            photoUri = existing?.photoUri ?: recipe.photoUri,
            usesMilkChoice = ingredients.any { it.isMilk },
            // Fields the editor doesn't show are carried over from the stored recipe.
            tags = recipe.tags.ifEmpty { existing?.tags.orEmpty() },
            fiberG = recipe.fiberG ?: existing?.fiberG,
            servings = existing?.servings ?: recipe.servings,
            storageNotes = recipe.storageNotes.ifEmpty { existing?.storageNotes.orEmpty() },
            nutritionSource = existing?.nutritionSource ?: recipe.nutritionSource,
            origin = existing?.origin ?: if (recipe.origin == "BUILT_IN") "USER" else recipe.origin,
            importedAt = existing?.importedAt ?: recipe.importedAt,
            sourceJson = existing?.sourceJson ?: recipe.sourceJson,
        )
        db.recipes().replaceRecipe(r, ingredients.mapIndexed { i, g -> g.copy(recipeId = r.id, position = i) })
        return r.id
    }

    suspend fun deleteCustom(id: String) = db.recipes().deleteCustom(id)

    suspend fun setFavorite(id: String, fav: Boolean) = db.recipes().setFavorite(id, fav)
    suspend fun setMilk(id: String, milk: MilkOption) = db.recipes().setMilk(id, milk.name)
    suspend fun setPhoto(id: String, uri: String?) = db.recipes().setPhoto(id, uri)
}

class FoodRepository(private val db: FormaDatabase, private val recipes: RecipeRepository, private val time: TimeProvider) {
    fun day(epochDay: Long) = db.food().observeDay(epochDay)
    fun range(from: Long, to: Long) = db.food().observeRange(from, to)
    val dailyTotals = db.food().observeDailyTotals()

    suspend fun logRecipe(
        recipeId: String, meal: String, portion: Portion, servings: Double, epochDay: Long, eatenAt: Long,
        milk: MilkOption? = null,
    ): Long {
        val full = recipes.full(recipeId) ?: error("Recipe $recipeId not found")
        val m = full.macros(portion, servings, milk)
        return db.food().insert(
            FoodLogEntity(
                epochDay = epochDay, eatenAt = eatenAt, mealType = meal, name = full.recipe.name,
                recipeId = recipeId, portion = portion.name, servings = servings, milkOption = (milk ?: full.milk)?.name,
                kcal = m.kcal, proteinG = m.proteinG, carbsG = m.carbsG, fatG = m.fatG,
                kosherStatus = full.recipe.kosherStatus,
            ),
        )
    }

    suspend fun logManual(
        name: String, meal: String, kcal: Double, protein: Double?, carbs: Double?, fat: Double?,
        epochDay: Long, eatenAt: Long, note: String? = null,
    ): Long {
        require(kcal >= 0) { "Calories can't be negative" }
        return db.food().insert(
            FoodLogEntity(
                epochDay = epochDay, eatenAt = eatenAt, mealType = meal, name = name.ifBlank { "Food" },
                kcal = kcal, proteinG = protein ?: 0.0, carbsG = carbs, fatG = fat, note = note,
            ),
        )
    }

    /** Changes portion/servings of a recipe entry and recomputes its nutrition from the ingredients. */
    suspend fun changePortion(id: Long, portion: Portion, servings: Double) {
        val e = db.food().get(id) ?: return
        val rid = e.recipeId ?: return
        val full = recipes.full(rid) ?: return
        val m = full.macros(portion, servings, MilkOption.from(e.milkOption))
        db.food().update(e.copy(portion = portion.name, servings = servings, kcal = m.kcal, proteinG = m.proteinG,
            carbsG = m.carbsG, fatG = m.fatG))
    }

    suspend fun update(e: FoodLogEntity) = db.food().update(e)
    suspend fun get(id: Long) = db.food().get(id)
    suspend fun delete(id: Long) = db.food().delete(id)
}

class BodyRepository(
    private val db: FormaDatabase,
    private val profile: ProfileRepository,
    private val time: TimeProvider,
) {
    val weights = db.body().observeWeights()
    val measurements = db.body().observeMeasurements()

    suspend fun logWeight(lb: Double, epochDay: Long = time.today().toEpochDay(), note: String? = null): Long {
        require(lb in 50.0..1000.0) { "Enter a realistic weight" }
        val at = if (epochDay == time.today().toEpochDay()) time.nowMillis()
        else time.startOfDayMillis(java.time.LocalDate.ofEpochDay(epochDay)) + 8 * 3_600_000L
        val id = db.body().insertWeight(WeightEntity(epochDay = epochDay, measuredAt = at, weightLb = lb, note = note))
        syncCurrentWeight()
        return id
    }

    suspend fun updateWeight(e: WeightEntity) { db.body().updateWeight(e); syncCurrentWeight() }
    suspend fun deleteWeight(id: Long) { db.body().deleteWeight(id); syncCurrentWeight() }

    /** Keeps profile.currentWeightLb aligned with the most recent weigh-in. */
    suspend fun syncCurrentWeight() {
        val latest = db.body().latestWeight() ?: return
        profile.update { it.copy(currentWeightLb = latest.weightLb) }
    }

    suspend fun addMeasurement(waistIn: Double, epochDay: Long, note: String?) {
        require(waistIn in 15.0..90.0) { "Enter a realistic waist measurement" }
        db.body().insertMeasurement(MeasurementEntity(epochDay = epochDay, waistIn = waistIn, note = note))
    }

    suspend fun updateMeasurement(m: MeasurementEntity) = db.body().updateMeasurement(m)
    suspend fun deleteMeasurement(id: Long) = db.body().deleteMeasurement(id)
}

class ActivityRepository(private val db: FormaDatabase) {
    val sessions = db.sessions().observeAll()

    suspend fun addManual(
        epochDay: Long, title: String, minutes: Double, kcal: Double?, met: Double, recordedByDevice: Boolean,
        startTime: Long?, note: String?,
    ): Long {
        require(minutes > 0 && minutes <= 600) { "Enter a duration between 1 and 600 minutes" }
        return db.sessions().insert(
            ExerciseSessionEntity(
                epochDay = epochDay, source = "MANUAL", title = title.ifBlank { "Exercise" }, startTime = startTime,
                endTime = startTime?.plus((minutes * 60_000).toLong()), durationMinutes = minutes, kcal = kcal,
                met = met, recordedByDevice = recordedByDevice, note = note,
            ),
        )
    }

    suspend fun update(e: ExerciseSessionEntity) = db.sessions().update(e)
    suspend fun delete(id: Long) = db.sessions().delete(id)
}
