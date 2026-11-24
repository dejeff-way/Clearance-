package com.forma.app.data.importer

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Fallback for ordinary written recipes. Produces a forma.recipe object; anything it can't find (category, kosher
 * type, nutrition) is left out so the preview asks for it instead of guessing.
 */
object TextRecipeParser {
    data class Line(val qty: Double, val unit: String, val name: String)

    private val fractions = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '⅛' to 0.125)
    private val unitWords = mapOf(
        "g" to "g", "gram" to "g", "grams" to "g", "kg" to "kg", "ml" to "ml", "l" to "l", "liter" to "l", "litre" to "l",
        "cup" to "cup", "cups" to "cup", "tbsp" to "tbsp", "tablespoon" to "tbsp", "tablespoons" to "tbsp", "tsp" to "tsp",
        "teaspoon" to "tsp", "teaspoons" to "tsp", "oz" to "oz", "ounce" to "oz", "ounces" to "oz", "lb" to "lb", "lbs" to "lb",
        "pound" to "lb", "pounds" to "lb", "clove" to "clove", "cloves" to "cloves", "scoop" to "scoop", "scoops" to "scoop",
        "slice" to "slice", "slices" to "slice", "can" to "can", "cans" to "can", "pinch" to "pinch", "whole" to "whole",
        "large" to "large", "medium" to "medium", "small" to "small", "piece" to "piece", "pieces" to "piece",
    )

    fun parseQuantity(s: String): Double? {
        val t = s.trim()
        if (t.isEmpty()) return null
        var total = 0.0; var any = false
        for (part in t.split(' ', '-').filter { it.isNotBlank() }) {
            val p = part.trim()
            val f = p.lastOrNull()?.let { fractions[it] }
            when {
                p.length == 1 && f != null -> { total += f; any = true }
                f != null && p.dropLast(1).toDoubleOrNull() != null -> { total += p.dropLast(1).toDouble() + f; any = true }
                p.contains('/') -> {
                    val (a, b) = p.split('/', limit = 2)
                    val v = a.toDoubleOrNull()?.let { x -> b.toDoubleOrNull()?.takeIf { it != 0.0 }?.let { x / it } } ?: return null
                    total += v; any = true
                }
                p.replace(',', '.').toDoubleOrNull() != null -> { total += p.replace(',', '.').toDouble(); any = true }
                else -> return null
            }
        }
        return if (any) total else null
    }

    private val qtyRe = Regex("^([0-9][0-9.,/]*(?:\\s*(?:[0-9]+/[0-9]+|[½¼¾⅓⅔⅛]))?|[0-9]*[½¼¾⅓⅔⅛])\\s*")

    fun parseIngredientLine(raw: String): Line {
        var s = raw.trim().trimStart('-', '•', '*', '·', '–').trim()
        val m = qtyRe.find(s)
        val qty = m?.let { parseQuantity(it.groupValues[1]) } ?: 0.0
        if (m != null) s = s.substring(m.value.length)
        val first = s.split(' ', limit = 2)
        val unitKey = first[0].trimEnd('.', ',').lowercase()
        val unit = unitWords[unitKey]
        return if (unit != null && first.size == 2) Line(qty, unit, first[1].trim().removePrefix("of ").trim())
        else Line(qty, if (qty > 0) "whole" else "to taste", s.trim())
    }

    private enum class Section { NONE, INGREDIENTS, STEPS, NOTES }

    fun parse(text: String): JsonObject {
        val lines = text.lines().map { it.trim() }
        val title = lines.firstOrNull { it.isNotEmpty() }?.trimStart('#', ' ')?.trim() ?: "Imported recipe"
        val ingredients = mutableListOf<JsonObject>()
        val steps = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val nutrition = mutableMapOf<String, JsonPrimitive>()
        var description: String? = null
        var section = Section.NONE
        var prep: Int? = null; var cook: Int? = null; var servings: Int? = null
        lines.drop(lines.indexOfFirst { it.isNotEmpty() } + 1).forEach { line ->
            if (line.isEmpty()) return@forEach
            val low = line.lowercase().trimStart('#', ' ').trimEnd(':')
            val isHeader = !line.any { it.isDigit() }
            Regex("(calories|kcal|protein|carbs?|carbohydrates|fat|fiber|fibre)\\s*[:=-]?\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE).findAll(line).forEach { mm ->
                val key = when (mm.groupValues[1].lowercase()) {
                    "calories", "kcal" -> "calories"; "protein" -> "protein_g"; "fat" -> "fat_g"
                    "fiber", "fibre" -> "fiber_g"; else -> "carbs_g"
                }
                nutrition.putIfAbsent(key, JsonPrimitive(mm.groupValues[2].toDouble()))
            }
            Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(kcal|calories)", RegexOption.IGNORE_CASE).find(line)?.let { nutrition.putIfAbsent("calories", JsonPrimitive(it.groupValues[1].toDouble())) }
            Regex("([0-9]+)\\s*g\\s*protein", RegexOption.IGNORE_CASE).find(line)?.let { nutrition.putIfAbsent("protein_g", JsonPrimitive(it.groupValues[1].toDouble())) }
            Regex("prep(?:aration)?\\s*time\\s*[:=-]?\\s*([0-9]+)", RegexOption.IGNORE_CASE).find(line)?.let { prep = it.groupValues[1].toInt(); return@forEach }
            Regex("cook(?:ing)?\\s*time\\s*[:=-]?\\s*([0-9]+)", RegexOption.IGNORE_CASE).find(line)?.let { cook = it.groupValues[1].toInt(); return@forEach }
            Regex("(?:serves|servings|yield)\\s*[:=-]?\\s*([0-9]+)", RegexOption.IGNORE_CASE).find(line)?.let { servings = it.groupValues[1].toInt(); return@forEach }
            if (isHeader) when {
                low.startsWith("ingredient") -> { section = Section.INGREDIENTS; return@forEach }
                low.startsWith("instruction") || low.startsWith("direction") || low.startsWith("method") || low == "steps" || low.startsWith("preparation") ->
                    { section = Section.STEPS; return@forEach }
                low.startsWith("note") || low.startsWith("nutrition") -> { section = Section.NOTES; return@forEach }
            }
            if (Regex("^(nutrition|calories|kcal|protein|carbs?|fat|fiber)\\b", RegexOption.IGNORE_CASE).containsMatchIn(line)) return@forEach
            when (section) {
                Section.INGREDIENTS -> {
                    val l = parseIngredientLine(line)
                    ingredients += JsonObject(buildMap {
                        put("name", JsonPrimitive(l.name)); put("quantity", JsonPrimitive(l.qty)); put("unit", JsonPrimitive(l.unit))
                    })
                }
                Section.STEPS -> steps += line.replace(Regex("^(step\\s*)?[0-9]+[.):]\\s*", RegexOption.IGNORE_CASE), "").trimStart('-', '•', '*').trim()
                Section.NOTES -> notes += line
                Section.NONE -> if (description == null) description = line else notes += line
            }
        }
        val id = "text-" + title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "recipe" } + "-" + (text.hashCode().toUInt() % 10000u)
        return JsonObject(buildMap {
            put("id", JsonPrimitive(id))
            put("name", JsonPrimitive(title))
            description?.let { put("description", JsonPrimitive(it)) }
            prep?.let { put("prep_time_minutes", JsonPrimitive(it)) }
            cook?.let { put("cook_time_minutes", JsonPrimitive(it)) }
            servings?.let { put("servings", JsonPrimitive(it)) }
            put("ingredients", JsonArray(ingredients))
            put("instructions", JsonArray(steps.map(::JsonPrimitive)))
            if (nutrition.isNotEmpty()) put("nutrition_per_serving", JsonObject(nutrition))
            if (notes.isNotEmpty()) put("notes", JsonArray(notes.map(::JsonPrimitive)))
            put("nutrition_source", JsonPrimitive("estimated"))
        })
    }
}
