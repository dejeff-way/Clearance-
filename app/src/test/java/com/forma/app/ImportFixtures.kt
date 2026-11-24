package com.forma.app

object ImportFixtures {
    /** The example from the import spec, verbatim. */
    const val USER_EXAMPLE = """
{
  "schema": "forma.recipe",
  "version": 1,
  "recipes": [
    {
      "id": "chicken-shawarma-001",
      "name": "High-Protein Chicken Shawarma Bowl",
      "category": "Lunch",
      "kosher_type": "Meat",
      "description": "Chicken shawarma with rice and fresh vegetables.",
      "prep_time_minutes": 15,
      "cook_time_minutes": 20,
      "servings": 1,
      "ingredients": [
        { "name": "Chicken breast", "quantity": 180, "unit": "g", "notes": "Raw weight" },
        { "name": "Cooked rice", "quantity": 120, "unit": "g" },
        { "name": "Cucumber", "quantity": 75, "unit": "g" },
        { "name": "Olive oil", "quantity": 5, "unit": "ml" }
      ],
      "instructions": [
        "Season the chicken with shawarma spices.",
        "Cook chicken thoroughly.",
        "Prepare rice and vegetables.",
        "Assemble the bowl and serve."
      ],
      "nutrition_per_serving": { "calories": 540, "protein_g": 57, "carbs_g": 48, "fat_g": 12, "fiber_g": 4 },
      "portions": { "standard_multiplier": 1, "large_multiplier": 1.3 },
      "tags": ["high-protein", "meal-prep"],
      "storage": { "refrigerator_days": 3, "reheating": "Reheat thoroughly." },
      "kosher_notes": "Use kosher-certified chicken and ingredients."
    }
  ]
}"""

    fun recipe(id: String, name: String = "Recipe $id", category: String = "Supper", kosher: String = "Meat", extra: String = "") = """
    {
      "id": "$id", "name": "$name", "category": "$category", "kosher_type": "$kosher",
      "ingredients": [
        { "name": "Lean ground beef", "quantity": 400, "unit": "g", "nutrition": { "calories": 704, "protein_g": 80, "carbs_g": 0, "fat_g": 40 } },
        { "name": "Olive oil", "quantity": 10, "unit": "ml", "nutrition": { "calories": 80, "protein_g": 0, "carbs_g": 0, "fat_g": 9 }, "scales_with_portion": false }
      ],
      "instructions": ["Brown the beef to 160F."],
      "servings": 2,
      "nutrition_per_serving": { "calories": 392, "protein_g": 40, "carbs_g": 0, "fat_g": 24.5 },
      "kosher_notes": ["Kosher-certified beef from a reliable butcher; certified oil."]$extra
    }"""

    fun file(vararg recipes: String) = """{ "schema": "forma.recipe", "version": 1, "recipes": [ ${recipes.joinToString(",")} ] }"""
}
