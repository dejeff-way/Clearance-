package com.forma.app.data.plan

import com.forma.app.data.db.IngredientEntity
import com.forma.app.data.repo.TOTAL_UNIT
import com.forma.app.data.seed.MilkOption
import com.forma.app.util.Fmt
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class StoreSection(val label: String) {
    MEAT("Meat & poultry"), FISH("Fish"), DAIRY("Dairy & eggs"), PRODUCE("Produce"), GRAINS("Grains & bread"),
    PANTRY("Pantry & canned"), STAPLES("Check you have"), OTHER("Other"),
}

/** One ingredient use from one planned meal (already scaled for portion and servings). */
data class IngredientUse(val ingredient: IngredientEntity, val amount: Double, val recipeName: String, val milk: MilkOption?)

data class ShoppingItem(
    val key: String,
    val name: String,
    val section: StoreSection,
    /** Human-readable quantity, e.g. "1,230 g · 2.7 lb". Empty for "to taste". */
    val quantity: String,
    /** Extra guidance, e.g. "Recipes use cooked weight — buy about this much raw." */
    val note: String?,
    /** Which planned meals need it, e.g. "Chicken Shawarma Bowl ×3". */
    val usedIn: String,
)

/**
 * Turns planned meals into a shopping list. Ingredients are matched by a normalised name ("Garlic, minced" and
 * "Garlic" are the same item), then summed per unit family (mass, volume, count) so different recipes' amounts add up.
 */
object ShoppingListBuilder {
    private const val G_PER_LB = 453.59237
    private const val G_PER_OZ = 28.349523125
    private val MASS = mapOf("g" to 1.0, "kg" to 1000.0, "oz" to G_PER_OZ, "lb" to G_PER_LB)
    private val VOLUME = mapOf("ml" to 1.0, "l" to 1000.0, "tsp" to 4.92892, "tbsp" to 14.7868, "cup" to 236.588, "cups" to 236.588)

    /** "Chicken breast, boneless skinless" → "chicken breast"; strips sourcing notes in brackets. */
    fun normalize(name: String): String = name.lowercase(Locale.US)
        .replace(Regex("\\(.*?\\)"), "")
        .substringBefore(',')
        .replace(Regex("\\b(certified|kosher|plain|fresh|large|medium|small)\\b"), "")
        .replace(Regex("\\s+"), " ").trim()

    fun displayName(name: String): String = name.replace(Regex("\\s*\\(.*?\\)"), "").substringBefore(',').trim()

    fun section(key: String): StoreSection = when {
        listOf("salt", "pepper", "cumin", "paprika", "turmeric", "coriander", "cinnamon", "rosemary", "garlic powder", "oil", "cooking spray",
            "vinegar", "vanilla", "sweetener", "black pepper").any { key == it || key.endsWith(" $it") || key.startsWith("$it ") } && !key.contains("bell") -> StoreSection.STAPLES
        listOf("chicken", "beef", "turkey", "lamb", "veal", "london broil", "steak", "meat").any { key.contains(it) } -> StoreSection.MEAT
        listOf("salmon", "tuna", "fish", "cod", "tilapia").any { key.contains(it) } -> StoreSection.FISH
        listOf("yogurt", "milk", "egg", "cheese", "cream", "butter").any { key.contains(it) } && !key.contains("almond") && !key.contains("oat milk") -> StoreSection.DAIRY
        listOf("rice", "oats", "bread", "wrap", "tortilla", "pita", "pasta", "noodle", "quinoa").any { key.contains(it) } -> StoreSection.GRAINS
        key.contains("sauce") || key.contains("pickle") || key.contains("ketchup") || key.contains("mustard") -> StoreSection.PANTRY
        listOf("spinach", "berr", "strawberr", "cucumber", "tomato", "onion", "scallion", "garlic", "potato", "broccoli", "green bean", "lettuce",
            "romaine", "carrot", "avocado", "lemon", "lime", "bell pepper", "zucchini", "mushroom", "parsley", "dill", "apple", "pepper", "banana")
            .any { key.contains(it) } -> StoreSection.PRODUCE
        else -> StoreSection.PANTRY
    }

    private fun family(unit: String) = when {
        unit in MASS -> "mass"
        unit in VOLUME -> "volume"
        unit == "to taste" || unit.isBlank() -> "taste"
        unit == TOTAL_UNIT -> "skip"
        else -> "count:${unit.removeSuffix("s")}"
    }

    /** How produce is bought: grams per cup when measured by volume, and weight of one item when sold by the piece. */
    private data class Produce(val match: String, val gPerCup: Double?, val gPerUnit: Double?, val unit: String?, val plural: String? = null)

    private val PRODUCE = listOf(
        Produce("spinach", 30.0, null, null), Produce("mushroom", 70.0, null, null),
        Produce("strawberr", 152.0, null, null), Produce("blueberr", 148.0, null, null), Produce("berries", 145.0, null, null),
        Produce("cucumber", 120.0, 300.0, "cucumber"), Produce("tomato", 180.0, 123.0, "tomato", "tomatoes"),
        Produce("onion", 160.0, 150.0, "onion"), Produce("bell pepper", 150.0, 120.0, "pepper"),
        Produce("carrot", 110.0, 61.0, "carrot"), Produce("lettuce", 47.0, 600.0, "head"), Produce("romaine", 47.0, 600.0, "head"),
        Produce("zucchini", 125.0, 200.0, "zucchini", "zucchini"), Produce("broccoli", 90.0, null, null),
        Produce("green bean", 110.0, null, null), Produce("potato", 150.0, null, null),
    )
    private val HERBS = listOf("parsley", "dill", "scallion", "cilantro", "basil", "mint")
    private val JUICE = mapOf("lemon juice" to (3.0 to "lemon"), "lime juice" to (2.0 to "lime"))

    /** Shopper-friendly quantity for produce, or null to use the generic units. */
    private fun produceQuantity(key: String, list: List<IngredientUse>): String? {
        fun ml(u: IngredientUse) = VOLUME[u.ingredient.unit]?.let { u.amount * it }
        JUICE.entries.firstOrNull { key.contains(it.key) }?.let { (_, v) ->
            val tbsp = list.sumOf { (ml(it) ?: 0.0) / 14.7868 }
            val n = ceil(tbsp / v.first - 0.05).toInt().coerceAtLeast(1)
            return "$n ${v.second}${if (n == 1) "" else "s"} (${Fmt.amount(roundQuarter(tbsp))} tbsp juice)"
        }
        if (HERBS.any { key.contains(it) }) {
            val cups = list.sumOf { (ml(it) ?: 0.0) / 236.588 }
            val n = ceil(cups / 1.0 - 0.01).toInt().coerceAtLeast(1)
            return "$n bunch${if (n == 1) "" else "es"}"
        }
        val p = PRODUCE.firstOrNull { key.contains(it.match) } ?: return null
        var grams = 0.0
        for (u in list) {
            val unit = u.ingredient.unit
            grams += when {
                unit in MASS -> u.amount * MASS.getValue(unit)
                unit in VOLUME && p.gPerCup != null -> u.amount * VOLUME.getValue(unit) / 236.588 * p.gPerCup
                unit in setOf("whole", "medium", "large", "small") && p.gPerUnit != null -> u.amount * p.gPerUnit
                else -> return null
            }
        }
        if (p.gPerUnit != null && p.unit != null) {
            val n = ceil(grams / p.gPerUnit - 0.25).toInt().coerceAtLeast(1) // recipe volumes are approximate
            return "$n ${if (n == 1) p.unit else p.plural ?: "${p.unit}s"} (≈ ${mass(grams).substringBefore(" ·")})"
        }
        return mass(grams)
    }

    fun build(uses: List<IngredientUse>, alwaysHave: Set<String>): List<ShoppingItem> {
        val relevant = uses.filter { !it.ingredient.optional && it.ingredient.unit != TOTAL_UNIT }
        val byKey = relevant.groupBy { u -> normalize(if (u.ingredient.isMilk) (u.milk ?: MilkOption.ONE_PERCENT).label else u.ingredient.name) }
        return byKey.filterKeys { it !in alwaysHave && it.isNotBlank() && !it.contains("water") }.map { (key, list) ->
            val first = list.first()
            val name = displayName(if (first.ingredient.isMilk) (first.milk ?: MilkOption.ONE_PERCENT).label else first.ingredient.name)
            val parts = mutableListOf<String>()
            var note: String? = null
            list.groupBy { family(it.ingredient.unit) }.toSortedMap().forEach { (fam, us) ->
                when {
                    fam == "mass" -> {
                        val cooked = us.filter { it.ingredient.note?.contains("cooked weight", true) == true }
                        val raw = us - cooked.toSet()
                        val rawG = raw.sumOf { it.amount * MASS.getValue(it.ingredient.unit) }
                        val cookedG = cooked.sumOf { it.amount * MASS.getValue(it.ingredient.unit) }
                        val isRice = key.contains("cooked") && key.contains("rice")
                        when {
                            isRice -> {
                                val dry = (rawG + cookedG) / 3.0
                                parts += "${mass(dry)} dry"
                                note = "Plan uses ${mass(rawG + cookedG)} cooked rice ≈ ${mass(dry)} uncooked."
                            }
                            cookedG > 0 -> {
                                val buy = rawG + cookedG / 0.75
                                parts += mass(buy)
                                note = "Recipes use cooked weight (${mass(cookedG)}); meat loses about 25% when cooked, so buy about this much raw."
                            }
                            else -> parts += mass(rawG)
                        }
                    }
                    fam == "volume" -> parts += volume(us.sumOf { it.amount * VOLUME.getValue(it.ingredient.unit) })
                    fam == "taste" -> {}
                    fam.startsWith("count:") -> {
                        val unit = fam.removePrefix("count:")
                        val n = us.sumOf { it.amount }
                        parts += count(n, unit)
                    }
                }
            }
            val produce = if (section(key) == StoreSection.PRODUCE) produceQuantity(key, list) else null
            ShoppingItem(
                key = key, name = name.replaceFirstChar { it.uppercase() },
                section = if (displayName(name).isBlank()) StoreSection.OTHER else section(key),
                quantity = produce ?: parts.joinToString(" + "),
                note = note,
                usedIn = list.groupBy { it.recipeName }.entries.joinToString { (r, l) -> if (l.size > 1) "$r ×${l.size}" else r },
            )
        }.sortedWith(compareBy({ it.section.ordinal }, { it.name }))
    }

    /** "1,230 g · 2.7 lb"; small amounts stay in grams only. */
    fun mass(g: Double): String {
        val grams = (ceil(g / 5 - 1e-3) * 5).roundToInt().coerceAtLeast(1)
        return if (g < 40) "$grams g" else "${Fmt.int(grams)} g · ${Fmt.one(g / G_PER_LB)} lb"
    }

    fun volume(ml: Double): String = when {
        ml < 15 -> "${Fmt.amount(roundQuarter(ml / 4.92892))} tsp"
        ml < 60 -> "${Fmt.amount(roundQuarter(ml / 14.7868))} tbsp"
        else -> roundQuarter(ml / 236.588).let { cups -> "${Fmt.int((ceil(ml / 10) * 10))} ml · ${Fmt.amount(cups)} cup${if (cups > 1.0) "s" else ""}" }
    }

    private fun roundQuarter(v: Double) = (ceil(v * 4) / 4.0).coerceAtLeast(0.25)

    private fun count(n: Double, unit: String): String {
        val whole = ceil(n - 0.01).toInt()
        return when (unit) {
            "whole", "medium", "large", "piece" -> if (n % 1.0 > 0.01) "${Fmt.amount(roundQuarter(n))} (buy $whole)" else "$whole"
            "clove" -> "${whole} clove${if (whole == 1) "" else "s"}"
            "scoop" -> "${Fmt.amount(roundQuarter(n))} scoop${if (n > 1) "s" else ""}"
            "spear" -> "$whole spears"
            "packet" -> "$whole packet${if (whole == 1) "" else "s"}"
            "second spray" -> "a few sprays"
            else -> "${Fmt.amount(roundQuarter(n))} $unit"
        }
    }
}
