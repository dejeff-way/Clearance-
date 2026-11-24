package com.forma.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.data.importer.RecipeImportFormat
import com.forma.app.data.importer.RecipeImportFormat.Severity
import com.forma.app.data.importer.TextRecipeParser
import com.forma.app.data.repo.RecipeMath
import com.forma.app.data.repo.TOTAL_UNIT
import com.forma.engine.Portion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestFormaApp::class, sdk = [35])
class RecipeImportFormatTest {
    private fun one(json: String) = RecipeImportFormat.parse(json).recipes.single()
    private fun errors(p: RecipeImportFormat.Parsed) = p.issues.filter { it.severity == Severity.ERROR }

    @Test fun `user example imports with per-serving totals, large multiplier, tags, storage and fiber`() {
        val r = RecipeImportFormat.parse(ImportFixtures.USER_EXAMPLE)
        assertTrue(r.fileIssues.isEmpty())
        val p = r.recipes.single()
        assertTrue(p.issues.toString(), p.valid)
        val e = p.recipe!!
        assertEquals("chicken-shawarma-001", e.id); assertEquals("LUNCH", e.category); assertEquals("MEAT", e.kosherStatus)
        assertEquals(listOf("high-protein", "meal-prep"), e.tags)
        assertEquals(4.0, e.fiberG!!, 0.0)
        assertTrue(e.storageNotes.any { it.contains("3 days") }); assertTrue(e.storageNotes.any { it.contains("Reheat") })
        assertEquals(4, e.cookSteps.size)
        assertEquals("IMPORT", e.origin); assertTrue(e.sourceJson!!.contains("chicken-shawarma-001"))
        // No ingredient-level nutrition → per-serving total row
        assertEquals(1, p.ingredients.count { it.unit == TOTAL_UNIT })
        assertEquals(540.0, RecipeMath.macros(p.ingredients, Portion.STANDARD, 1.0, null).kcal, 0.01)
        assertEquals(540.0 * 1.3, RecipeMath.macros(p.ingredients, Portion.LARGE, 1.0, null).kcal, 0.01)
        assertEquals(180.0 * 1.3, p.ingredients.first { it.name == "Chicken breast" }.largeAmount, 0.01)
    }

    @Test fun `ingredient level nutrition is divided by servings and scales selectively`() {
        val p = one(ImportFixtures.file(ImportFixtures.recipe("beef-1")))
        assertTrue(p.issues.toString(), p.valid)
        assertEquals(0, p.ingredients.count { it.unit == TOTAL_UNIT })
        assertEquals(200.0, p.ingredients[0].amount, 0.0) // 400 g / 2 servings
        assertEquals(392.0, RecipeMath.macros(p.ingredients, Portion.STANDARD, 1.0, null).kcal, 0.01)
        // Large (default 1.5×) scales beef but not oil
        assertEquals(352.0 * 1.5 + 40.0, RecipeMath.macros(p.ingredients, Portion.LARGE, 1.0, null).kcal, 0.01)
    }

    @Test fun `missing required fields are reported with exact paths`() {
        val p = one("""{"schema":"forma.recipe","version":1,"recipes":[{"name":"x","ingredients":[{"quantity":2}]}]}""")
        val paths = errors(p).map { it.path }.toSet()
        assertTrue(paths.containsAll(listOf("recipes[0].id", "recipes[0].category", "recipes[0].kosher_type", "recipes[0].nutrition_per_serving",
            "recipes[0].ingredients[0].name", "recipes[0].instructions")))
        assertFalse(p.valid)
    }

    @Test fun `bad values are rejected`() {
        val p = one(ImportFixtures.file(ImportFixtures.recipe("b", category = "Brunch").replace("\"calories\": 392, \"protein_g\": 40", "\"calories\": 100, \"protein_g\": 60")))
        val msgs = errors(p).joinToString { it.message }
        assertTrue(msgs, msgs.contains("Brunch"))
        assertTrue(msgs, msgs.contains("exceeds calories"))
    }

    @Test fun `meat with dairy is blocked but non-dairy look-alikes are fine`() {
        val dairy = ImportFixtures.recipe("m1").replace("\"Olive oil\"", "\"Parmesan cheese\"")
        assertTrue(errors(one(ImportFixtures.file(dairy))).any { it.message.contains("never be combined") })
        val coconut = ImportFixtures.recipe("m2").replace("\"Olive oil\"", "\"Coconut milk\"")
        assertTrue(one(ImportFixtures.file(coconut)).valid)
        val dairyWithMeat = ImportFixtures.recipe("d1", kosher = "Dairy")
        assertTrue(errors(one(ImportFixtures.file(dairyWithMeat))).any { it.message.contains("meat ingredient") })
    }

    @Test fun `category and kosher mappings`() {
        val dinner = one(ImportFixtures.file(ImportFixtures.recipe("x1", category = "Dinner")))
        assertEquals("SUPPER", dinner.recipe!!.category)
        assertTrue(dinner.issues.any { it.severity == Severity.WARNING && it.message.contains("Supper") })
        val dessert = ImportFixtures.recipe("x2", category = "Dessert", kosher = "Pareve").replace("Lean ground beef", "Dates")
        assertEquals("SNACKS", one(ImportFixtures.file(dessert)).recipe!!.category)
        val fish = ImportFixtures.recipe("x3", kosher = "Fish").replace("Lean ground beef", "Salmon fillet")
        val f = one(ImportFixtures.file(fish)).recipe!!
        assertEquals("PAREVE", f.kosherStatus); assertTrue(f.isFish)
    }

    @Test fun `duplicate ids in one file are flagged`() {
        val r = RecipeImportFormat.parse(ImportFixtures.file(ImportFixtures.recipe("dup"), ImportFixtures.recipe("DUP")))
        assertTrue(r.recipes[0].valid)
        assertTrue(errors(r.recipes[1]).any { it.message.contains("Duplicate id") })
    }

    @Test fun `kosher sourcing is flagged instead of assumed`() {
        val noNotes = ImportFixtures.recipe("k1").replace(Regex(",\\s*\"kosher_notes\": \\[[^]]*]"), "")
        val p = one(ImportFixtures.file(noNotes))
        assertTrue(p.valid)
        assertTrue(p.issues.any { it.path.endsWith("kosher_notes") && it.severity == Severity.WARNING })
        assertTrue(p.issues.any { it.message.contains("No certification or sourcing info for") })
    }

    @Test fun `unknown fields are kept and reported, newer versions accepted`() {
        val text = ImportFixtures.file(ImportFixtures.recipe("u1", extra = ", \"glycemic_index\": 40")).replace("\"version\": 1", "\"version\": 2")
        val r = RecipeImportFormat.parse(text)
        assertTrue(r.fileIssues.any { it.message.contains("version 2") })
        val p = r.recipes.single()
        assertTrue(p.valid)
        assertTrue(p.issues.any { it.severity == Severity.INFO && it.message.contains("glycemic_index") })
        assertTrue(p.recipe!!.sourceJson!!.contains("glycemic_index"))
    }

    @Test fun `accepts code fences, bare objects and arrays and rejects garbage`() {
        assertTrue(RecipeImportFormat.parse("```json\n${ImportFixtures.USER_EXAMPLE}\n```").recipes.single().valid)
        assertTrue(RecipeImportFormat.parse(ImportFixtures.recipe("solo")).recipes.single().valid)
        assertEquals(2, RecipeImportFormat.parse("[${ImportFixtures.recipe("a1")},${ImportFixtures.recipe("a2")}]").recipes.size)
        assertTrue(RecipeImportFormat.parse("not json").fileIssues.single().severity == Severity.ERROR)
    }

    @Test fun `preview fixes re-validate`() {
        val p = one(ImportFixtures.file(ImportFixtures.recipe("f1", category = "Brunch")))
        assertFalse(p.valid)
        assertTrue(RecipeImportFormat.withFixes(p, "Lunch", null, null, null).valid)
    }

    @Test fun `plain text recipes are parsed`() {
        val obj = TextRecipeParser.parse("""
            Lemon Herb Chicken
            Bright weeknight chicken.
            Serves: 2
            Ingredients
            - 400 g chicken breast
            - 1 ½ tbsp olive oil
            - 2 cloves garlic
            - salt to taste
            Instructions
            1. Marinate the chicken.
            2. Grill until 165F.
            Nutrition: 380 calories, 46 g protein
        """.trimIndent())
        val p = RecipeImportFormat.parseRecipe(0, obj)
        // Category and kosher type must be confirmed by the user
        assertTrue(errors(p).any { it.path.endsWith("category") }); assertTrue(errors(p).any { it.path.endsWith("kosher_type") })
        val fixed = RecipeImportFormat.withFixes(p, "Supper", "Meat", null, null)
        assertTrue(fixed.issues.toString(), fixed.valid)
        val r = fixed.recipe!!
        assertEquals("Lemon Herb Chicken", r.name); assertEquals(2, r.servings); assertEquals(2, r.cookSteps.size)
        assertEquals("Marinate the chicken.", r.cookSteps[0])
        assertEquals(200.0, fixed.ingredients.first { it.name == "chicken breast" }.amount, 0.0)
        assertEquals(0.75, fixed.ingredients.first { it.name == "olive oil" }.amount, 0.001)
        assertEquals(380.0, RecipeMath.macros(fixed.ingredients, Portion.STANDARD, 1.0, null).kcal, 0.01)
    }
}
