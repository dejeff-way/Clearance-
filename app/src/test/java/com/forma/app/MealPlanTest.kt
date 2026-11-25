package com.forma.app

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.db.IngredientEntity
import com.forma.app.data.plan.IngredientUse
import com.forma.app.data.plan.ShoppingListBuilder
import com.forma.app.data.plan.StoreSection
import com.forma.engine.Portion
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestFormaApp::class, sdk = [35], qualifiers = "w400dp-h2400dp-xxhdpi")
class MealPlanTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val c get() = (rule.activity.application as FormaApp).container
    private val today = LocalDate.of(2026, 11, 24)            // Tuesday
    private val nextSunday = LocalDate.of(2026, 11, 29)
    private fun text(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    @Before fun ready() {
        runBlocking { c.initialize() }
        rule.waitUntil(8000) { rule.onAllNodesWithTagExists("remaining_kcal") }
    }

    private fun ing(name: String, amt: Double, unit: String, note: String? = null) =
        IngredientEntity("r", 0, name, amt, unit, amt, note, 0.0, 0.0, 0.0, 0.0)

    @Test fun `builder sums across recipes, converts units and gives grams and pounds`() {
        val uses = listOf(
            IngredientUse(ing("Chicken breast, boneless skinless", 170.0, "g", "cooked weight (≈225 g raw)"), 510.0, "Shawarma", null),
            IngredientUse(ing("Chicken breast", 180.0, "g", "cooked weight"), 360.0, "Garlic Chicken", null),
            IngredientUse(ing("Garlic, minced", 2.0, "cloves"), 6.0, "Shawarma", null),
            IngredientUse(ing("Garlic, minced", 3.0, "cloves"), 3.0, "Garlic Chicken", null),
            IngredientUse(ing("Olive oil", 1.0, "tsp"), 3.0, "Shawarma", null),
            IngredientUse(ing("Olive oil", 1.0, "tbsp"), 1.0, "Other", null),
            IngredientUse(ing("Cooked white rice", 100.0, "g"), 600.0, "Bowls", null),
            IngredientUse(ing("Whole eggs", 2.0, "large"), 14.0, "Scramble", null),
            IngredientUse(ing("Salt and black pepper", 0.0, "to taste"), 0.0, "Scramble", null),
        )
        val list = ShoppingListBuilder.build(uses, emptySet())
        val chicken = list.single { it.key == "chicken breast" }
        assertEquals(StoreSection.MEAT, chicken.section)
        // 870 g cooked ÷ 0.75 = 1,160 g raw ≈ 2.6 lb
        assertEquals("1,160 g · 2.6 lb", chicken.quantity)
        assertTrue(chicken.note!!.contains("cooked weight"))
        assertEquals("Shawarma, Garlic Chicken", chicken.usedIn)
        assertEquals("9 cloves", list.single { it.key == "garlic" }.quantity)
        assertEquals("2 tbsp", list.single { it.key == "olive oil" }.quantity)          // 3 tsp + 1 tbsp
        assertEquals(StoreSection.STAPLES, list.single { it.key == "olive oil" }.section)
        assertEquals("200 g · 0.4 lb dry", list.single { it.key == "cooked white rice" }.quantity)
        assertEquals("14", list.single { it.key == "whole eggs" }.quantity)
        assertEquals("", list.single { it.key == "salt and black pepper" }.quantity)
        // "Always have" hides items
        assertTrue(ShoppingListBuilder.build(uses, setOf("olive oil")).none { it.key == "olive oil" })
    }

    @Test fun `plan with meal prep produces the right shopping list from real recipes`() = runBlocking {
        // Breakfast every day except Shabbos, a 3-day shawarma prep, one large supper, and blank days.
        (0..5).forEach { c.mealPlan.add(nextSunday.plusDays(it.toLong()), "BREAKFAST", "protein_egg_scramble", Portion.STANDARD, 1.0) }
        assertEquals(3, c.mealPlan.add(nextSunday, "LUNCH", "chicken_shawarma_bowl", Portion.STANDARD, 1.0, prepDays = 3))
        c.mealPlan.add(nextSunday.plusDays(1), "SUPPER", "garlic_chicken_potatoes", Portion.LARGE, 1.0)
        assertEquals(2, c.mealPlan.add(nextSunday.plusDays(5), "SUPPER", "lean_beef_burger_bowl", Portion.STANDARD, 1.0, prepDays = 4)) // capped at Saturday
        val list = c.mealPlan.shoppingList(nextSunday, emptySet())
        assertEquals("12", list.single { it.key == "whole eggs" }.quantity)
        assertEquals("900 g · 2.0 lb", list.single { it.key == "liquid egg whites" }.quantity)
        // Chicken: shawarma 3 × 170 g cooked + garlic chicken large 240 g cooked = 750 g cooked → 1,000 g raw
        assertEquals("1,000 g · 2.2 lb", list.single { it.key == "chicken breast" }.quantity)
        assertTrue(list.single { it.key == "chicken breast" }.usedIn.contains("Chicken Shawarma Bowl ×3"))
        // Beef burger 2 × 170 g raw (no cooked conversion)
        assertEquals("340 g · 0.7 lb", list.single { it.key == "lean ground beef" }.quantity)
        assertFalse("optional items aren't bought", list.any { it.key.contains("sweetener") })
        // Produce in shopper units: 3 × ½ cup diced (270 g) + 2 × ½ medium (123 g) tomato ≈ 395 g → 3 tomatoes;
        // red onion 3 × ¼ cup + 2 × 2 tbsp ≈ 160 g → 1 onion; lemon juice 4 tbsp → 2 lemons
        assertEquals("3 tomatoes (≈ 395 g)", list.single { it.key == "tomato" }.quantity)
        assertEquals("1 onion (≈ 160 g)", list.single { it.key == "red onion" }.quantity)
        assertEquals("2 lemons (4 tbsp juice)", list.single { it.key == "lemon juice" }.quantity) // shawarma 3 × 1 tbsp + garlic chicken 1 tbsp
        assertTrue(list.single { it.key == "baby spinach" }.quantity.endsWith("lb") || list.single { it.key == "baby spinach" }.quantity.endsWith("g"))
        // Week boundaries: nothing from other weeks, blank days are fine
        assertTrue(c.mealPlan.shoppingList(nextSunday.plusWeeks(1), emptySet()).isEmpty())
        val entries = c.db.mealPlan().range(nextSunday.toEpochDay(), nextSunday.plusDays(6).toEpochDay())
        assertEquals(1, entries.count { it.isPrepDay && it.recipeId == "chicken_shawarma_bowl" })
        assertEquals(nextSunday.toEpochDay(), entries.single { it.isPrepDay && it.recipeId == "chicken_shawarma_bowl" }.epochDay)
    }

    @Test fun `removing, copying and logging`() = runBlocking {
        c.mealPlan.add(today, "LUNCH", "chicken_shawarma_bowl", Portion.STANDARD, 1.0, prepDays = 3)
        val batch = c.db.mealPlan().range(today.toEpochDay(), today.plusDays(6).toEpochDay())
        c.mealPlan.remove(batch.first { it.isPrepDay }, wholeBatch = false)
        val left = c.db.mealPlan().range(today.toEpochDay(), today.plusDays(6).toEpochDay())
        assertEquals(2, left.size); assertEquals(1, left.count { it.isPrepDay })        // cook day moves to the next day
        c.mealPlan.remove(left.first(), wholeBatch = true)
        assertTrue(c.db.mealPlan().range(today.toEpochDay(), today.plusDays(6).toEpochDay()).isEmpty())

        c.mealPlan.add(today, "SUPPER", "garlic_london_broil", Portion.STANDARD, 1.0)
        assertEquals(1, c.mealPlan.copyWeek(c.mealPlan.weekStart(today), c.mealPlan.weekStart(today).plusWeeks(1)))
        assertEquals(1, c.db.mealPlan().range(today.plusDays(7).toEpochDay(), today.plusDays(7).toEpochDay()).size)

        // Planning never logs food; logging happens only on request
        assertTrue(c.db.food().all().isEmpty())
        val e = c.db.mealPlan().range(today.toEpochDay(), today.toEpochDay()).single()
        val foodId = c.mealPlan.log(e)
        assertEquals("Garlic London Broil", c.db.food().get(foodId)!!.name)
        assertEquals(foodId, c.db.mealPlan().get(e.id)!!.loggedFoodId)
    }

    @Test fun `plan next week in the app, meal prep, shopping list and log today`() {
        rule.onNodeWithTag("tab_recipes").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("open_meal_plan") }
        rule.onNodeWithTag("open_meal_plan").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("plan_week") }
        rule.onNodeWithTag("plan_next").performClick()                                    // next week (Tuesday → this week by default)
        rule.waitUntil(5000) { text("plan_week").startsWith("Nov 29") }
        rule.onNodeWithTag("plan_add_SUN_LUNCH").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("plan_pick_chicken_shawarma_bowl") }
        rule.onNodeWithTag("plan_pick_chicken_shawarma_bowl").performClick()
        rule.onNodeWithTag("plan_prep_3").performClick()
        rule.onNodeWithTag("plan_confirm").performClick()
        rule.waitUntil(5000) { runBlocking { c.db.mealPlan().all() }.size == 3 }
        rule.onNodeWithTag("plan_add_MON_BREAKFAST").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("plan_pick_protein_egg_scramble") }
        rule.onNodeWithTag("plan_pick_protein_egg_scramble").performClick()
        rule.onNodeWithTag("plan_confirm").performClick()
        rule.waitUntil(5000) { runBlocking { c.db.mealPlan().all() }.size == 4 }
        rule.onNodeWithTag("plan_week").performScrollTo()
        rule.onRoot().captureRoboImage("screenshots/27_meal_plan_light.png")

        rule.onNodeWithTag("open_shopping").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("shop_qty_chicken breast") }
        assertEquals("680 g · 1.5 lb", text("shop_qty_chicken breast"))                  // 3 × 170 g cooked → raw
        rule.onNodeWithTag("shop_check_chicken breast").performClick()
        rule.waitUntil(5000) { text("shopping_progress").startsWith("1 of") }
        rule.onRoot().captureRoboImage("screenshots/28_shopping_list_light.png")

        // Today's plan on Home: a suggestion with one-tap logging (never automatic)
        runBlocking { c.mealPlan.add(TestEnv.time.today(), "SUPPER", "lemon_garlic_salmon", Portion.STANDARD, 1.0) }
        repeat(2) { rule.activity.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("tab_home") }
        rule.onNodeWithTag("tab_home").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("today_plan") }
        assertTrue(runBlocking { c.db.food().all() }.isEmpty())
        val e = runBlocking { c.db.mealPlan().range(TestEnv.time.today().toEpochDay(), TestEnv.time.today().toEpochDay()) }.single()
        rule.onNodeWithTag("plan_log_${e.id}").performScrollTo().performClick()
        rule.waitUntil(5000) { runBlocking { c.db.food().all() }.size == 1 }
        assertEquals("SUPPER", runBlocking { c.db.food().all() }.single().mealType)
    }

    @Test fun `database migrates from version 5`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("migrate6.db")
        val schema = Json.parseToJsonElement(File("schemas/com.forma.app.data.db.FormaDatabase/5.json").readText()).jsonObject["database"]!!.jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath("migrate6.db").apply { parentFile?.mkdirs() }, null)
        schema["entities"]!!.jsonArray.forEach { e ->
            val o = e.jsonObject; val t = o["tableName"]!!.jsonPrimitive.content
            sql.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t))
            o["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t)) }
        }
        schema["setupQueries"]!!.jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }
        sql.execSQL("INSERT INTO food_log (epochDay,eatenAt,createdAt,mealType,name,servings,kcal,proteinG) VALUES (20000,1,1,'LUNCH','Kept',1.0,450,30)")
        sql.version = 5; sql.close()
        val db = androidx.room.Room.databaseBuilder(ctx, FormaDatabase::class.java, "migrate6.db")
            .addMigrations(FormaDatabase.MIGRATION_1_2, FormaDatabase.MIGRATION_2_3, FormaDatabase.MIGRATION_3_4, FormaDatabase.MIGRATION_4_5, FormaDatabase.MIGRATION_5_6, FormaDatabase.MIGRATION_6_7, FormaDatabase.MIGRATION_7_8).build()
        runBlocking {
            assertEquals("Kept", db.food().all().single().name)
            assertTrue(db.mealPlan().all().isEmpty())
            assertNull(db.mealPlan().get(1))
            assertNotNull(db.mealPlan())
        }
        db.close(); ctx.deleteDatabase("migrate6.db")
    }
}
