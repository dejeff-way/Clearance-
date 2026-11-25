package com.forma.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** One planned meal. Planning never logs food; the user logs from the plan when they actually eat. */
@Serializable
@Entity(tableName = "meal_plan", indices = [Index("epochDay"), Index("batchId")])
data class MealPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    /** BREAKFAST, LUNCH, SUPPER, SNACKS */
    val mealType: String,
    val recipeId: String,
    /** STANDARD or LARGE */
    val portion: String = "STANDARD",
    val servings: Double = 1.0,
    /** Entries cooked together (meal prep) share a batch id. */
    val batchId: String? = null,
    /** The day the batch is cooked. */
    val isPrepDay: Boolean = false,
    /** Food-log entry created when the user tapped Log. */
    val loggedFoodId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Ticked shopping-list items, per week. */
@Serializable
@Entity(tableName = "shopping_checks", primaryKeys = ["weekStartEpochDay", "itemKey"])
data class ShoppingCheckEntity(val weekStartEpochDay: Long, val itemKey: String, val checked: Boolean = true)

/** Ingredients the user always has (salt, oil…), hidden from shopping lists. */
@Serializable
@Entity(tableName = "pantry_always")
data class PantryItemEntity(@PrimaryKey val itemKey: String, val label: String)
