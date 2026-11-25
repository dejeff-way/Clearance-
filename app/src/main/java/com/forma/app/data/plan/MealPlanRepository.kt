package com.forma.app.data.plan

import androidx.room.withTransaction
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.db.MealPlanEntity
import com.forma.app.data.db.PantryItemEntity
import com.forma.app.data.db.ShoppingCheckEntity
import com.forma.app.data.repo.FoodRepository
import com.forma.app.data.repo.RecipeRepository
import com.forma.app.util.TimeProvider
import com.forma.engine.Portion
import com.forma.engine.TrendAnalyzer
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

/** Weekly meal planning (Sunday–Saturday). Plans are suggestions only; nothing is logged automatically. */
class MealPlanRepository(
    private val db: FormaDatabase,
    private val recipes: RecipeRepository,
    private val food: FoodRepository,
    private val time: TimeProvider,
) {
    private val dao = db.mealPlan()

    fun weekStart(d: LocalDate): LocalDate = TrendAnalyzer.weekStart(d)
    fun week(start: LocalDate): Flow<List<MealPlanEntity>> = dao.observeRange(start.toEpochDay(), start.plusDays(6).toEpochDay())
    fun day(d: LocalDate): Flow<List<MealPlanEntity>> = dao.observeRange(d.toEpochDay(), d.toEpochDay())
    fun checks(weekStart: LocalDate) = dao.observeChecks(weekStart.toEpochDay())
    val pantry = dao.observePantry()

    /**
     * Plans [recipeId] for [mealType] on [day]. With [prepDays] > 1 this is meal prep: cooked once on [day] and eaten
     * on the following days in the same slot (never past Saturday of that week).
     */
    suspend fun add(day: LocalDate, mealType: String, recipeId: String, portion: Portion, servings: Double, prepDays: Int = 1): Int {
        val lastDay = weekStart(day).plusDays(6)
        val days = (0 until prepDays.coerceIn(1, 7)).map { day.plusDays(it.toLong()) }.filter { !it.isAfter(lastDay) }
        val batch = if (days.size > 1) UUID.randomUUID().toString() else null
        dao.insertAll(days.mapIndexed { i, d ->
            MealPlanEntity(epochDay = d.toEpochDay(), mealType = mealType, recipeId = recipeId, portion = portion.name, servings = servings,
                batchId = batch, isPrepDay = batch != null && i == 0)
        })
        return days.size
    }

    suspend fun update(e: MealPlanEntity) = dao.update(e)

    /** Changes portion/servings for one entry, or the whole meal-prep batch. */
    suspend fun changePortion(e: MealPlanEntity, portion: Portion, servings: Double, wholeBatch: Boolean) = db.withTransaction {
        val targets = if (wholeBatch && e.batchId != null) dao.batch(e.batchId) else listOf(e)
        targets.forEach { dao.update(it.copy(portion = portion.name, servings = servings)) }
    }

    suspend fun remove(e: MealPlanEntity, wholeBatch: Boolean) = db.withTransaction {
        if (wholeBatch && e.batchId != null) dao.deleteBatch(e.batchId)
        else {
            dao.delete(e.id)
            // Removing the cook day of a batch moves "cook" to the next remaining day.
            if (e.isPrepDay && e.batchId != null) dao.batch(e.batchId).firstOrNull()?.let { dao.update(it.copy(isPrepDay = true)) }
        }
    }

    suspend fun clearWeek(start: LocalDate) = dao.deleteRange(start.toEpochDay(), start.plusDays(6).toEpochDay())

    /** Copies one week's plan into another (same weekdays). Returns how many meals were copied. */
    suspend fun copyWeek(from: LocalDate, to: LocalDate): Int = db.withTransaction {
        val src = dao.range(from.toEpochDay(), from.plusDays(6).toEpochDay())
        val shift = to.toEpochDay() - from.toEpochDay()
        val batchMap = src.mapNotNull { it.batchId }.distinct().associateWith { UUID.randomUUID().toString() }
        dao.insertAll(src.map { it.copy(id = 0, epochDay = it.epochDay + shift, batchId = it.batchId?.let(batchMap::get), loggedFoodId = null, createdAt = time.nowMillis()) })
        src.size
    }

    /** Logs a planned meal to the food log (only when the user taps Log). */
    suspend fun log(e: MealPlanEntity): Long {
        val day = LocalDate.ofEpochDay(e.epochDay)
        val at = if (day == time.today()) time.nowMillis() else time.startOfDayMillis(day) + 12 * 3_600_000L
        val id = food.logRecipe(e.recipeId, e.mealType, Portion.valueOf(e.portion), e.servings, e.epochDay, at)
        dao.update(e.copy(loggedFoodId = id))
        return id
    }

    /** Builds the shopping list for a week from the planned meals. */
    suspend fun shoppingList(start: LocalDate, alwaysHave: Set<String>): List<ShoppingItem> {
        val entries = dao.range(start.toEpochDay(), start.plusDays(6).toEpochDay())
        val uses = entries.flatMap { e ->
            val full = recipes.full(e.recipeId) ?: return@flatMap emptyList()
            val large = e.portion == Portion.LARGE.name
            full.ingredients.map { i -> IngredientUse(i, (if (large) i.largeAmount else i.amount) * e.servings, full.recipe.name, full.milk) }
        }
        return ShoppingListBuilder.build(uses, alwaysHave)
    }

    suspend fun setChecked(start: LocalDate, key: String, checked: Boolean) = dao.upsertCheck(ShoppingCheckEntity(start.toEpochDay(), key, checked))
    suspend fun clearChecks(start: LocalDate) = dao.clearChecks(start.toEpochDay())
    suspend fun alwaysHave(key: String, label: String) = dao.upsertPantry(PantryItemEntity(key, label))
    suspend fun removeAlwaysHave(key: String) = dao.deletePantry(key)

}
