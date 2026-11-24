package com.forma.app.data.importer

import com.forma.app.data.db.IngredientEntity
import com.forma.app.data.db.RecipeEntity
import com.forma.app.data.repo.TOTAL_UNIT
import com.forma.app.data.seed.Category
import com.forma.app.data.seed.KosherStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * FORMA Recipe Import v1 (`"schema": "forma.recipe", "version": 1`).
 *
 * Parsing and validation are pure functions so they can be unit tested. Every problem is reported with a path such as
 * `recipes[3].ingredients[2].quantity` — nothing is silently dropped. Fields FORMA doesn't understand are reported as
 * info and the original object is stored verbatim with the recipe.
 */
object RecipeImportFormat {
    const val SCHEMA = "forma.recipe"
    const val VERSION = 1

    val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true; encodeDefaults = true }

    enum class Severity { ERROR, WARNING, INFO }
    data class Issue(val severity: Severity, val path: String, val message: String) {
        override fun toString() = "${severity.name.lowercase()} · $path: $message"
    }

    /** One recipe as parsed and validated (before checking against the library). */
    data class Parsed(
        val index: Int,
        val path: String,
        val sourceId: String?,
        val raw: JsonObject,
        val recipe: RecipeEntity?,
        val ingredients: List<IngredientEntity>,
        val imageRef: String?,
        val issues: List<Issue>,
    ) {
        val valid get() = recipe != null && issues.none { it.severity == Severity.ERROR }
        val name get() = recipe?.name ?: (raw["name"] as? JsonPrimitive)?.contentOrNull ?: "Recipe ${index + 1}"
    }

    data class FileResult(val recipes: List<Parsed>, val fileIssues: List<Issue>)

    private val KNOWN_TOP = setOf("schema", "version", "recipes", "generator", "generated_at", "collection", "name", "description")
    private val KNOWN_RECIPE = setOf(
        "id", "name", "category", "kosher_type", "description", "prep_time_minutes", "cook_time_minutes", "servings",
        "ingredients", "instructions", "prep_instructions", "cook_instructions", "nutrition_per_serving", "nutrition_source",
        "portions", "tags", "storage", "image", "kosher_notes", "safety_notes", "notes",
    )
    private val KNOWN_ING = setOf("name", "quantity", "unit", "notes", "nutrition", "scales_with_portion", "optional", "kosher_certification")
    private val ID_RE = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$")

    /** Parses a whole file / pasted text. Accepts the full envelope, a bare recipe object, or an array of recipes. */
    fun parse(text: String): FileResult {
        val fileIssues = mutableListOf<Issue>()
        val root: JsonElement = try {
            json.parseToJsonElement(text.trim().removePrefix("﻿").let(::stripCodeFence))
        } catch (e: Exception) {
            return FileResult(emptyList(), listOf(Issue(Severity.ERROR, "file", "Not valid JSON: ${e.message?.lineSequence()?.firstOrNull() ?: "parse error"}")))
        }
        val recipeArray: JsonArray = when {
            root is JsonObject && root["recipes"] is JsonArray -> {
                val schema = root.str("schema")
                val version = (root["version"] as? JsonPrimitive)?.doubleOrNull?.toInt()
                if (schema == null) fileIssues += Issue(Severity.WARNING, "schema", "Missing \"schema\": \"$SCHEMA\" — reading as $SCHEMA v$VERSION.")
                else if (schema != SCHEMA) fileIssues += Issue(Severity.WARNING, "schema", "Unexpected schema \"$schema\" — reading as $SCHEMA.")
                if (version == null) fileIssues += Issue(Severity.WARNING, "version", "Missing version — assuming $VERSION.")
                else if (version > VERSION) fileIssues += Issue(Severity.WARNING, "version", "File is version $version; this app reads version $VERSION. Known fields are imported and the original data is kept.")
                (root.keys - KNOWN_TOP).takeIf { it.isNotEmpty() }?.let { fileIssues += Issue(Severity.INFO, "file", "Unrecognised top-level fields ignored: ${it.joinToString()}") }
                root["recipes"] as JsonArray
            }
            root is JsonObject && root.containsKey("name") -> { fileIssues += Issue(Severity.INFO, "file", "Single recipe without the forma.recipe envelope."); JsonArray(listOf(root)) }
            root is JsonArray -> { fileIssues += Issue(Severity.INFO, "file", "Recipe list without the forma.recipe envelope."); root }
            else -> return FileResult(emptyList(), listOf(Issue(Severity.ERROR, "file", "Expected an object with a \"recipes\" list.")))
        }
        if (recipeArray.isEmpty()) fileIssues += Issue(Severity.ERROR, "recipes", "The file contains no recipes.")
        val parsed = recipeArray.mapIndexed { i, el ->
            val path = "recipes[$i]"
            if (el !is JsonObject) Parsed(i, path, null, JsonObject(emptyMap()), null, emptyList(), null, listOf(Issue(Severity.ERROR, path, "Each recipe must be an object.")))
            else parseRecipe(i, el)
        }
        // Duplicate IDs inside the same file: later copies are rejected.
        val seen = HashMap<String, Int>()
        val withDupes = parsed.map { p ->
            val id = p.sourceId ?: return@map p
            val first = seen.putIfAbsent(id.lowercase(), p.index)
            if (first != null) p.copy(issues = p.issues + Issue(Severity.ERROR, "${p.path}.id", "Duplicate id \"$id\" — already used by recipes[$first] in this file."))
            else p
        }
        return FileResult(withDupes, fileIssues)
    }

    private fun stripCodeFence(s: String): String {
        val t = s.trim()
        if (!t.startsWith("```")) return t
        return t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").removeSuffix("```").trim()
    }

    fun parseRecipe(index: Int, o: JsonObject): Parsed {
        val p = "recipes[$index]"
        val issues = mutableListOf<Issue>()
        fun err(path: String, m: String) { issues += Issue(Severity.ERROR, path, m) }
        fun warn(path: String, m: String) { issues += Issue(Severity.WARNING, path, m) }
        fun info(path: String, m: String) { issues += Issue(Severity.INFO, path, m) }

        (o.keys - KNOWN_RECIPE).takeIf { it.isNotEmpty() }?.let { info(p, "Extra fields kept with the recipe but not shown: ${it.joinToString()}") }

        // ── Identity ──
        val id = o.str("id")?.trim()
        when {
            id.isNullOrEmpty() -> err("$p.id", "Required — a unique recipe id, e.g. \"chicken-shawarma-001\".")
            !ID_RE.matches(id) -> err("$p.id", "Use 1–80 letters, numbers, dots, dashes or underscores (got \"$id\").")
        }
        val name = o.str("name")?.trim()
        if (name.isNullOrEmpty()) err("$p.name", "Required.") else if (name.length > 120) err("$p.name", "Keep names under 120 characters.")

        // ── Category ──
        val catRaw = o.str("category")?.trim()
        val category = when (catRaw?.lowercase()) {
            null, "" -> { err("$p.category", "Required — one of Breakfast, Lunch, Supper, Snacks."); null }
            "breakfast" -> Category.BREAKFAST
            "lunch" -> Category.LUNCH
            "supper" -> Category.SUPPER
            "dinner" -> { warn("$p.category", "\"Dinner\" imported as Supper."); Category.SUPPER }
            "snack", "snacks" -> Category.SNACKS
            "dessert", "desserts" -> { warn("$p.category", "FORMA has no Desserts collection — imported under Snacks."); Category.SNACKS }
            else -> { err("$p.category", "\"$catRaw\" isn't a collection. Use Breakfast, Lunch, Supper or Snacks."); null }
        }

        // ── Kosher ──
        val kRaw = o.str("kosher_type")?.trim()?.lowercase()
        var isFish = false
        val kosher = when (kRaw) {
            null, "" -> { err("$p.kosher_type", "Required — Meat, Dairy, Pareve or Fish."); null }
            "meat", "fleishig", "fleishigs" -> KosherStatus.MEAT
            "dairy", "milchig", "milchigs" -> KosherStatus.DAIRY
            "pareve", "parve" -> KosherStatus.PAREVE
            "fish" -> { isFish = true; KosherStatus.PAREVE }
            "depends", "depends on product" -> KosherStatus.DEPENDS
            else -> { err("$p.kosher_type", "\"$kRaw\" isn't recognised. Use Meat, Dairy, Pareve or Fish."); null }
        }

        // ── Times & servings ──
        fun minutes(key: String): Int {
            val e = o[key] ?: return 0
            val v = (e as? JsonPrimitive)?.doubleOrNull
            if (v == null || v < 0 || v > 24 * 60) { err("$p.$key", "Must be a number of minutes between 0 and 1440."); return 0 }
            return v.roundToInt()
        }
        val prepMin = minutes("prep_time_minutes")
        val cookMin = minutes("cook_time_minutes")
        val servings = o["servings"]?.let { e ->
            val v = (e as? JsonPrimitive)?.doubleOrNull
            if (v == null || v < 1 || v > 50 || v % 1.0 != 0.0) { err("$p.servings", "Must be a whole number from 1 to 50."); 1 } else v.toInt()
        } ?: 1
        if (servings > 1) info("$p.servings", "Batch makes $servings servings — ingredient amounts are shown per serving.")

        // ── Portions ──
        val portions = o["portions"] as? JsonObject
        val std = portions?.num("standard_multiplier") ?: 1.0
        if (std != 1.0) warn("$p.portions.standard_multiplier", "Standard is always 1 serving; ${fmt(std)} was ignored.")
        val large = portions?.num("large_multiplier") ?: 1.5
        val largeMult = if (large < 1.0 || large > 3.0) { err("$p.portions.large_multiplier", "Must be between 1 and 3."); 1.5 } else large

        // ── Nutrition per serving ──
        val n = o["nutrition_per_serving"] as? JsonObject
        var kcal: Double? = null; var protein: Double? = null; var carbs: Double? = null; var fat: Double? = null; var fiber: Double? = null
        if (n == null) err("$p.nutrition_per_serving", "Required — calories and protein_g per serving.")
        else {
            fun macro(key: String, required: Boolean, max: Double): Double? {
                val e = n[key]
                if (e == null || e is JsonNull) { if (required) err("$p.nutrition_per_serving.$key", "Required."); return null }
                val v = (e as? JsonPrimitive)?.doubleOrNull
                if (v == null) { err("$p.nutrition_per_serving.$key", "Must be a number."); return null }
                if (v < 0 || v > max) { err("$p.nutrition_per_serving.$key", "${fmt(v)} is out of range (0–${max.toInt()})."); return null }
                return v
            }
            kcal = macro("calories", true, 3000.0)
            protein = macro("protein_g", true, 300.0)
            carbs = macro("carbs_g", false, 500.0)
            fat = macro("fat_g", false, 300.0)
            fiber = macro("fiber_g", false, 100.0)
            (n.keys - setOf("calories", "protein_g", "carbs_g", "fat_g", "fiber_g")).takeIf { it.isNotEmpty() }
                ?.let { info("$p.nutrition_per_serving", "Extra nutrients kept but not tracked: ${it.joinToString()}") }
            if (kcal != null && protein != null && protein * 4 > kcal + 20) err("$p.nutrition_per_serving", "Protein alone (${fmt(protein)} g = ${(protein * 4).toInt()} kcal) exceeds calories (${kcal.toInt()}).")
            if (kcal != null && protein != null && carbs != null && fat != null) {
                val fromMacros = protein * 4 + carbs * 4 + fat * 9
                if (abs(fromMacros - kcal) > maxOf(60.0, kcal * 0.2))
                    warn("$p.nutrition_per_serving", "Macros add up to ~${fromMacros.toInt()} kcal but calories say ${kcal.toInt()}. Check the numbers.")
            }
            if (fiber != null && carbs != null && fiber > carbs) warn("$p.nutrition_per_serving.fiber_g", "Fiber is higher than total carbs.")
            if (kcal == 0.0) warn("$p.nutrition_per_serving.calories", "0 calories — is that right?")
        }
        val nutritionSource = when (o.str("nutrition_source")?.lowercase()?.replace(' ', '_')) {
            null, "", "estimated", "estimate" -> "ESTIMATED"
            "verified", "verified_labels", "labels", "label" -> "VERIFIED_LABELS"
            else -> { warn("$p.nutrition_source", "Use \"estimated\" or \"verified_labels\" — treated as estimated."); "ESTIMATED" }
        }

        // ── Ingredients ──
        val ingArr = o["ingredients"] as? JsonArray
        if (ingArr == null || ingArr.isEmpty()) err("$p.ingredients", "Required — at least one ingredient.")
        data class Ing(val name: String, val qty: Double, val unit: String, val notes: String?, val nut: DoubleArray?, val scales: Boolean, val optional: Boolean, val cert: String?)
        val ings = mutableListOf<Ing>()
        ingArr?.forEachIndexed { j, e ->
            val ip = "$p.ingredients[$j]"
            if (e is JsonPrimitive && e.isString) {
                // Plain string ingredient: "180 g chicken breast"
                val line = TextRecipeParser.parseIngredientLine(e.content)
                ings += Ing(line.name, line.qty, line.unit, null, null, true, false, null)
                info(ip, "Text ingredient parsed as ${fmt(line.qty)} ${line.unit} ${line.name}.")
                return@forEachIndexed
            }
            val io = e as? JsonObject ?: run { err(ip, "Must be an object with name, quantity and unit."); return@forEachIndexed }
            (io.keys - KNOWN_ING).takeIf { it.isNotEmpty() }?.let { info(ip, "Extra fields kept: ${it.joinToString()}") }
            val iname = io.str("name")?.trim()
            if (iname.isNullOrEmpty()) { err("$ip.name", "Required."); return@forEachIndexed }
            val qtyEl = io["quantity"]
            val qty = when {
                qtyEl == null || qtyEl is JsonNull -> 0.0
                (qtyEl as? JsonPrimitive)?.doubleOrNull != null -> qtyEl.jsonPrimitiveDouble()
                qtyEl is JsonPrimitive && qtyEl.isString -> TextRecipeParser.parseQuantity(qtyEl.content) ?: run { err("$ip.quantity", "\"${qtyEl.content}\" isn't a number."); 0.0 }
                else -> { err("$ip.quantity", "Must be a number."); 0.0 }
            }
            if (qty < 0 || qty > 10000) err("$ip.quantity", "${fmt(qty)} is out of range.")
            val unit = io.str("unit")?.trim().orEmpty().ifEmpty { if (qty == 0.0) "to taste" else "whole" }
            val nut = (io["nutrition"] as? JsonObject)?.let { no ->
                val c = no.num("calories")
                if (c == null) { warn("$ip.nutrition", "Ingredient nutrition needs calories — ignored for this ingredient."); null }
                else doubleArrayOf(c, no.num("protein_g") ?: 0.0, no.num("carbs_g") ?: 0.0, no.num("fat_g") ?: 0.0)
            }
            ings += Ing(iname, qty, unit, io.str("notes"), nut, io.bool("scales_with_portion") ?: true, io.bool("optional") ?: false, io.str("kosher_certification"))
        }

        // ── Instructions ──
        fun steps(key: String): List<String> = when (val e = o[key]) {
            null, is JsonNull -> emptyList()
            is JsonArray -> e.mapIndexedNotNull { k, s -> (s as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: run { warn("$p.$key[$k]", "Empty or non-text step skipped."); null } }
            is JsonPrimitive -> e.content.lines().map { it.trim() }.filter { it.isNotEmpty() }
            else -> { err("$p.$key", "Must be a list of steps."); emptyList() }
        }
        val prep = steps("prep_instructions")
        val cook = steps("cook_instructions")
        val general = steps("instructions")
        if (prep.isEmpty() && cook.isEmpty() && general.isEmpty()) err("$p.instructions", "Required — a list of steps.")

        // ── Text lists ──
        fun texts(key: String): List<String> = when (val e = o[key]) {
            null, is JsonNull -> emptyList()
            is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            is JsonPrimitive -> listOf(e.content.trim()).filter { it.isNotEmpty() }
            else -> emptyList()
        }
        val tags = texts("tags").map { it.lowercase().replace(' ', '-') }.distinct()
        val kosherNotes = texts("kosher_notes")
        val safety = texts("safety_notes")
        val notes = texts("notes")
        val storage = when (val s = o["storage"]) {
            is JsonObject -> listOfNotNull(
                s.num("refrigerator_days")?.let { "Refrigerate up to ${fmt(it)} day${if (it == 1.0) "" else "s"}." },
                s.num("freezer_months")?.let { "Freeze up to ${fmt(it)} month${if (it == 1.0) "" else "s"}." },
                s.str("reheating")?.let { "Reheating: $it" },
                s.str("notes"),
            )
            is JsonPrimitive -> listOf(s.content)
            else -> emptyList()
        }

        // ── Kosher validation ──
        val allNames = ings.joinToString(" | ") { it.name.lowercase() }
        val dairyHit = DAIRY.firstOrNull { w -> ings.any { containsWord(it.name, w) && NON_DAIRY.none { x -> it.name.lowercase().contains(x) } } }
        val meatHit = MEAT.firstOrNull { w -> ings.any { containsWord(it.name, w) } }
        when {
            kosher == KosherStatus.MEAT && dairyHit != null -> err("$p.kosher_type", "Marked Meat but contains a dairy ingredient (\"$dairyHit\"). Meat and dairy can never be combined.")
            kosher == KosherStatus.DAIRY && meatHit != null -> err("$p.kosher_type", "Marked Dairy but contains a meat ingredient (\"$meatHit\"). Meat and dairy can never be combined.")
            dairyHit != null && meatHit != null -> err("$p.ingredients", "Contains both meat (\"$meatHit\") and dairy (\"$dairyHit\").")
            kosher == KosherStatus.PAREVE && (dairyHit != null || meatHit != null) && !isFish ->
                err("$p.kosher_type", "Marked Pareve but contains \"${dairyHit ?: meatHit}\". Use Meat or Dairy.")
            isFish && meatHit != null -> warn("$p.kosher_type", "Fish recipe with \"$meatHit\" — by custom fish isn't eaten together with meat.")
            kosher == KosherStatus.PAREVE && !isFish && FISH.any { allNames.contains(it) } -> warn("$p.kosher_type", "Contains fish — consider kosher_type \"Fish\".")
        }
        val notesText = kosherNotes.joinToString(" ").lowercase()
        val generalCert = CERT_WORDS.any { notesText.contains(it) }
        if (kosherNotes.isEmpty()) warn("$p.kosher_notes", "No certification or sourcing notes. Ingredients are not assumed to be kosher — add kosher_notes.")
        val needCert = ings.filter { ing ->
            val n = ing.name.lowercase()
            val needs = NEEDS_CERT.any { n.contains(it) }
            val covered = ing.cert != null || CERT_WORDS.any { (ing.notes ?: "").lowercase().contains(it) || n.contains(it) } ||
                (generalCert && (MEAT + FISH).none { n.contains(it) }) ||
                ((MEAT + FISH).any { n.contains(it) } && notesText.contains("kosher"))
            needs && !covered
        }
        if (needCert.isNotEmpty()) warn("$p.ingredients", "No certification or sourcing info for: ${needCert.joinToString { it.name }}.")
        if (RAW_FISH.any { allNames.contains(it) } || (isFish && tags.any { it in setOf("sushi", "poke", "raw", "crudo", "ceviche") })) {
            if (!notesText.contains("supplier") && !notesText.contains("supervis"))
                warn("$p.kosher_notes", "Raw fish: say it must come from a reliable kosher fish supplier (sushi-grade is not a kosher certification).")
        }

        // ── Image ──
        val image = o.str("image")?.trim()?.takeIf { it.isNotEmpty() }
        if (image != null && !(image.startsWith("data:image/") || image.startsWith("https://") || image.startsWith("http://")))
            warn("$p.image", "Use an https:// link or a data:image/… base64 value. Kept in the source data; the illustration is used instead.")

        // ── Build entities (only when no errors) ──
        if (issues.any { it.severity == Severity.ERROR } || id == null || name == null || category == null || kosher == null || kcal == null || protein == null) {
            return Parsed(index, p, id, o, null, emptyList(), image, issues)
        }
        val perServing = ings.map { it.copy(qty = it.qty / servings, nut = it.nut?.map { v -> v / servings }?.toDoubleArray()) }
        val counted = perServing.filter { !it.optional }
        val ingredientLevel = counted.isNotEmpty() && counted.all { it.nut != null }
        if (!ingredientLevel && perServing.any { it.nut != null }) info("$p.ingredients", "Only some ingredients have nutrition — using the per-serving totals for portions.")
        if (ingredientLevel) {
            val sum = counted.sumOf { it.nut!![0] }
            if (abs(sum - kcal) > kcal * 0.15) warn("$p.nutrition_per_serving.calories",
                "Ingredients add up to ${sum.roundToInt()} kcal per serving but the total says ${kcal.roundToInt()}. FORMA uses the ingredient values so portions scale accurately.")
        }
        val ingredients = perServing.mapIndexed { j, g ->
            IngredientEntity(
                recipeId = id, position = j, name = g.name, amount = g.qty,
                unit = if (g.qty == 0.0) "to taste" else g.unit,
                largeAmount = if (g.scales && g.qty > 0) g.qty * largeMult else g.qty,
                note = listOfNotNull(g.notes, g.cert?.let { "certification: $it" }).joinToString(" · ").ifEmpty { null },
                kcal = if (ingredientLevel) g.nut!![0] else 0.0, proteinG = if (ingredientLevel) g.nut!![1] else 0.0,
                carbsG = if (ingredientLevel) g.nut!![2] else 0.0, fatG = if (ingredientLevel) g.nut!![3] else 0.0,
                optional = g.optional,
            )
        } + if (ingredientLevel) emptyList() else listOf(
            IngredientEntity(id, perServing.size, "Nutrition per serving", 1.0, TOTAL_UNIT, largeMult, null, kcal, protein, carbs ?: 0.0, fat ?: 0.0),
        )
        val recipe = RecipeEntity(
            id = id, name = name, category = category.name, kosherStatus = kosher.name, isFish = isFish,
            summary = o.str("description")?.trim().orEmpty(), prepMinutes = prepMin, cookMinutes = cookMin,
            prepSteps = prep, cookSteps = if (cook.isEmpty()) general else cook + general,
            kosherNotes = kosherNotes, safetyNotes = safety,
            tips = notes + "Nutrition: ${if (nutritionSource == "VERIFIED_LABELS") "calculated from verified product labels" else "estimated"} (imported).",
            standardPortionLabel = if (servings > 1) "1 of $servings servings" else "1 serving",
            largePortionLabel = "${fmt(largeMult)}× serving",
            artKey = "custom_${category.name}", builtIn = false,
            tags = tags, fiberG = fiber, servings = servings, storageNotes = storage, nutritionSource = nutritionSource,
            origin = "IMPORT", sourceJson = o.toString(),
        )
        return Parsed(index, p, id, o, recipe, ingredients, image, issues)
    }

    /** Applies user fixes from the preview (category, kosher type, calories, protein) and re-validates. */
    fun withFixes(p: Parsed, category: String?, kosherType: String?, calories: Double?, protein: Double?): Parsed {
        val m = p.raw.toMutableMap()
        category?.let { m["category"] = JsonPrimitive(it) }
        kosherType?.let { m["kosher_type"] = JsonPrimitive(it) }
        if (calories != null || protein != null) {
            val n = (m["nutrition_per_serving"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            calories?.let { n["calories"] = JsonPrimitive(it) }
            protein?.let { n["protein_g"] = JsonPrimitive(it) }
            m["nutrition_per_serving"] = JsonObject(n)
        }
        return parseRecipe(p.index, JsonObject(m))
    }

    // ── Word lists ──
    private val DAIRY = listOf("milk", "yogurt", "yoghurt", "cheese", "butter", "cream", "whey", "parmesan", "mozzarella", "ricotta", "feta", "labneh", "ghee", "kefir", "casein", "tnuva")
    private val NON_DAIRY = listOf("coconut milk", "almond milk", "oat milk", "soy milk", "rice milk", "cashew milk", "peanut butter", "almond butter",
        "cashew butter", "nut butter", "apple butter", "sunflower butter", "cream of tartar", "non-dairy", "nondairy", "pareve", "parve", "coconut cream", "dairy-free")
    private val MEAT = listOf("chicken", "beef", "turkey", "lamb", "veal", "meat", "pastrami", "salami", "brisket", "schnitzel", "duck", "goose", "sausage", "steak", "liver")
    private val FISH = listOf("salmon", "tuna", "cod", "tilapia", "trout", "halibut", "sardine", "anchov", "mackerel", "sea bass", "branzino", "flounder", "fish")
    private val RAW_FISH = listOf("raw salmon", "raw tuna", "sashimi", "sushi-grade", "sushi grade", "raw fish")
    private val NEEDS_CERT = listOf("sauce", "spice", "seasoning", "oil", "vinegar", "ketchup", "mustard", "mayo", "soy", "tamari", "broth", "stock", "powder",
        "protein", "cheese", "yogurt", "milk", "wine", "gelatin", "bread", "wrap", "tortilla", "pita", "pasta", "noodle", "cereal", "granola", "chocolate",
        "sweetener", "extract", "canned", "salsa", "hummus", "tahini", "pickle", "sausage", "chicken", "beef", "turkey", "lamb", "salmon", "tuna", "fish")
    private val CERT_WORDS = listOf("certif", "hechsher", "hashgacha", "supervis", "star-k", "crc", "kosher-certified", "reliable", "kosher brand")

    private fun containsWord(text: String, w: String) = Regex("\\b${Regex.escape(w)}", RegexOption.IGNORE_CASE).containsMatchIn(text)

    internal fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')
}

private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
private fun JsonElement.jsonPrimitiveDouble(): Double = (this as JsonPrimitive).doubleOrNull!!
