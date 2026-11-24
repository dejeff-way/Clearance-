package com.forma.app.data.seed

enum class Category(val label: String) {
    BREAKFAST("Breakfast"), LUNCH("Lunch"), SUPPER("Supper"), SNACKS("Snacks");

    companion object { fun from(s: String?) = entries.firstOrNull { it.name == s } ?: SNACKS }
}

/** Meals in the food log use the same four slots as the recipe collections. */
typealias MealType = Category

enum class KosherStatus(val label: String) {
    MEAT("Meat"), DAIRY("Dairy"), PAREVE("Pareve"), DEPENDS("Depends on product");

    companion object { fun from(s: String?) = entries.firstOrNull { it.name == s } }
}
