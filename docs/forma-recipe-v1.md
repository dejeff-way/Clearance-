# FORMA Recipe Import v1 (`forma.recipe`)

Import in the app: **Recipes → Import** (or Settings → Import & export recipes). You can import a `.json` file, paste JSON, or paste ordinary recipe text.
In the app, **Copy format instructions** copies the prompt below so you can paste it into ChatGPT.

## Fields
| Field | Required | Notes |
|---|---|---|
| `id` | yes | Unique, 1–80 chars: letters, numbers, `.` `-` `_`. Reusing an id **updates** that recipe (only after you approve it in the preview). Ids matching a built-in recipe import as a separate copy. |
| `name` | yes | |
| `category` | yes | `Breakfast`, `Lunch`, `Supper`, `Snacks`. `Dinner` → Supper, `Dessert` → Snacks (with a warning). |
| `kosher_type` | yes | `Meat`, `Dairy`, `Pareve`, `Fish`. A meat + dairy combination is rejected. |
| `description` | | |
| `prep_time_minutes`, `cook_time_minutes` | | 0–1440 |
| `servings` | | 1–50, default 1. Quantities are for the whole batch; FORMA stores them per serving. |
| `ingredients[]` | yes | `name` (required), `quantity` (number, or text like `"1 1/2"`; omit for "to taste"), `unit`, `notes`, `optional`, `scales_with_portion` (default true), `kosher_certification`, `nutrition` {`calories`, `protein_g`, `carbs_g`, `fat_g`} for that quantity. Plain strings such as `"180 g chicken"` are also accepted. |
| `instructions[]` | yes* | Or `prep_instructions[]` + `cook_instructions[]`. |
| `nutrition_per_serving` | yes | `calories` and `protein_g` required; `carbs_g`, `fat_g`, `fiber_g` optional. Checked for impossible values and macro/calorie mismatches. |
| `nutrition_source` | | `estimated` (default) or `verified_labels` |
| `portions` | | `large_multiplier` 1–3 (default 1.5). Ingredients with `scales_with_portion: false` stay the same. |
| `tags[]` | | Searchable in the Recipes tab. |
| `storage` | | `refrigerator_days`, `freezer_months`, `reheating`, `notes` (or a single string). |
| `kosher_notes` | | String or list. If it's missing, or packaged/meat/fish ingredients have no certification info, you get a warning; nothing is assumed kosher. |
| `safety_notes[]`, `notes[]` | | |
| `image` | | `https://…` link (downloaded on import) or `data:image/…;base64,…`. Optional. |

Nutrition: when **every** non-optional ingredient has `nutrition`, FORMA uses the ingredient values, so large portions scale accurately. Otherwise it uses `nutrition_per_serving`.
Unknown fields are reported and kept: the original JSON is stored with each recipe. Files with a newer `version` are read for the fields this version knows.

Export (Recipes → Import → Export) writes the same format, including photos, so collections move between phones.

## Prompt for ChatGPT
```
When I ask for recipes, reply ONLY with JSON in the FORMA Recipe Import v1 format:

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
- No desserts category: put sweet items under Snacks.
```
