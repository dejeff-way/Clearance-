package com.forma.app.data.importer

/** Instructions you can paste into ChatGPT (or any assistant) so it produces importable recipes. */
object ImportSpec {
    const val PROMPT = """When I ask for recipes, reply ONLY with JSON in the FORMA Recipe Import v1 format:

{
  "schema": "forma.recipe",
  "version": 1,
  "recipes": [
    {
      "id": "unique-lowercase-id-001",
      "name": "Recipe name",
      "category": "Breakfast | Lunch | Supper | Snacks",
      "kosher_type": "Meat | Dairy | Pareve | Fish",
      "description": "One sentence.",
      "prep_time_minutes": 15,
      "cook_time_minutes": 20,
      "servings": 1,
      "ingredients": [
        { "name": "Chicken breast", "quantity": 180, "unit": "g", "notes": "raw weight",
          "nutrition": { "calories": 216, "protein_g": 40.5, "carbs_g": 0, "fat_g": 4.7 },
          "scales_with_portion": true, "kosher_certification": "kosher-certified poultry" }
      ],
      "prep_instructions": ["Step 1", "Step 2"],
      "cook_instructions": ["Step 1", "Step 2"],
      "nutrition_per_serving": { "calories": 540, "protein_g": 57, "carbs_g": 48, "fat_g": 12, "fiber_g": 4 },
      "nutrition_source": "estimated",
      "portions": { "standard_multiplier": 1, "large_multiplier": 1.3 },
      "tags": ["high-protein", "meal-prep"],
      "storage": { "refrigerator_days": 3, "freezer_months": 2, "reheating": "Reheat to 165°F." },
      "kosher_notes": ["Use kosher-certified chicken.", "All packaged ingredients need a reliable hechsher."],
      "safety_notes": ["Cook chicken to 165°F."],
      "image": "https://... (optional)"
    }
  ]
}

Rules:
- Every recipe must be kosher. Never combine meat and dairy. Fish recipes use kosher_type "Fish".
- Quantities are for the whole batch; nutrition_per_serving is for ONE serving.
- Give ingredient-level "nutrition" for every non-optional ingredient (for the stated quantity) when you can.
- Set "scales_with_portion": false for oil, spices, sauces and garnishes that shouldn't grow in a large portion.
- kosher_notes must say which items need certification or a reliable supplier. Raw fish must come from a kosher fish supplier (sushi-grade is not a kosher certification).
- Use unique ids. To update a recipe later, reuse its id.
- No desserts category: put sweet items under Snacks."""
}
