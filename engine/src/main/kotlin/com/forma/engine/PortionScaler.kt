package com.forma.engine

enum class Portion { STANDARD, LARGE }

/**
 * Ingredient-level portion scaling. Each ingredient states its own standard and large amounts — e.g. protein and
 * starch increase for a large portion while oil, spices and sauces may not — so nutrition follows the ingredients
 * instead of multiplying the whole recipe by a single factor.
 */
data class ScalableIngredient(
    val standardAmount: Double,
    val largeAmount: Double,
    /** Nutrition of [standardAmount]. */
    val macrosStandard: Macros,
    /** Optional ingredients are listed but excluded from nutrition. */
    val optional: Boolean = false,
) {
    fun amount(p: Portion) = if (p == Portion.LARGE) largeAmount else standardAmount

    fun macros(p: Portion): Macros {
        if (optional) return Macros.ZERO
        if (standardAmount == 0.0) return macrosStandard // "to taste" items: fixed, usually ~0
        return macrosStandard * (amount(p) / standardAmount)
    }
}

object PortionScaler {
    fun total(ingredients: List<ScalableIngredient>, portion: Portion, servings: Double = 1.0): Macros =
        ingredients.fold(Macros.ZERO) { acc, i -> acc + i.macros(portion) } * servings
}
