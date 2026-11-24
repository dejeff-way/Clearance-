package com.forma.app.data.importer

import android.content.Context
import android.util.Base64
import com.forma.app.data.db.FormaDatabase
import com.forma.app.data.db.IngredientEntity
import com.forma.app.data.db.RecipeEntity
import com.forma.app.data.importer.RecipeImportFormat.Issue
import com.forma.app.data.importer.RecipeImportFormat.Parsed
import com.forma.app.data.importer.RecipeImportFormat.Severity
import com.forma.app.data.repo.RecipeMath
import com.forma.app.data.repo.TOTAL_UNIT
import com.forma.app.data.seed.Category
import com.forma.app.util.TimeProvider
import com.forma.engine.Portion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class ImportStatus(val label: String) {
    NEW("New"),
    UPDATE("Already in your library"),
    BUILT_IN_CONFLICT("Same id as a built-in recipe"),
    INVALID("Needs fixing"),
}

data class ImportCandidate(
    val parsed: Parsed,
    val status: ImportStatus,
    /** Library recipe this would replace (UPDATE) or collide with (BUILT_IN_CONFLICT). */
    val existing: RecipeEntity?,
    /** Id used in the database. Built-in collisions are imported as a separate copy. */
    val targetId: String,
    val include: Boolean,
) {
    val errors get() = parsed.issues.filter { it.severity == Severity.ERROR }
    val warnings get() = parsed.issues.filter { it.severity == Severity.WARNING }
    val infos get() = parsed.issues.filter { it.severity == Severity.INFO }
}

data class ImportPlan(val fileIssues: List<Issue>, val candidates: List<ImportCandidate>)

enum class ImportOutcome { IMPORTED, UPDATED, SKIPPED, FAILED }

data class ImportReportLine(val name: String, val sourceId: String?, val outcome: ImportOutcome, val details: List<String>)

data class ImportReport(val lines: List<ImportReportLine>, val fileIssues: List<Issue>) {
    fun count(o: ImportOutcome) = lines.count { it.outcome == o }
    fun asText(): String = buildString {
        appendLine("FORMA recipe import report")
        appendLine("Imported ${count(ImportOutcome.IMPORTED)} · Updated ${count(ImportOutcome.UPDATED)} · Skipped ${count(ImportOutcome.SKIPPED)} · Failed ${count(ImportOutcome.FAILED)}")
        fileIssues.forEach { appendLine("• $it") }
        lines.forEach { l ->
            appendLine()
            appendLine("${l.outcome.name}: ${l.name}${l.sourceId?.let { " ($it)" } ?: ""}")
            l.details.forEach { appendLine("  - $it") }
        }
    }
}

/** Connects the import format to the Room recipe library. */
class RecipeImporter(private val context: Context, private val db: FormaDatabase, private val time: TimeProvider) {

    suspend fun analyzeJson(text: String): ImportPlan {
        val file = RecipeImportFormat.parse(text)
        return ImportPlan(file.fileIssues, classify(file.recipes))
    }

    suspend fun analyzeText(text: String): ImportPlan {
        val obj = TextRecipeParser.parse(text)
        val parsed = RecipeImportFormat.parseRecipe(0, obj)
        return ImportPlan(listOf(Issue(Severity.INFO, "text", "Read from plain text — check the details below before importing.")), classify(listOf(parsed)))
    }

    /** Re-classifies after the user fixes a field in the preview. */
    suspend fun reclassify(p: Parsed): ImportCandidate = classify(listOf(p)).single()

    suspend fun classify(parsed: List<Parsed>): List<ImportCandidate> {
        val library = db.recipes().all().associateBy { it.id.lowercase() }
        return parsed.map { p ->
            val id = p.sourceId
            val existing = id?.let { library[it.lowercase()] }
            when {
                !p.valid -> ImportCandidate(p, ImportStatus.INVALID, existing, id ?: "", include = false)
                existing != null && existing.builtIn -> {
                    val copyId = "${p.sourceId}-imported"
                    val copyExisting = library[copyId.lowercase()]
                    ImportCandidate(p, ImportStatus.BUILT_IN_CONFLICT, copyExisting ?: existing, copyId, include = copyExisting == null)
                }
                existing != null -> ImportCandidate(p, ImportStatus.UPDATE, existing, existing.id, include = false)
                else -> ImportCandidate(p, ImportStatus.NEW, null, p.sourceId!!, include = true)
            }
        }
    }

    suspend fun commit(plan: ImportPlan, candidates: List<ImportCandidate>): ImportReport {
        val now = time.nowMillis()
        val lines = mutableListOf<ImportReportLine>()
        var order = (db.recipes().maxSortOrder() ?: 0) + 1
        for (c in candidates) {
            val p = c.parsed
            val warn = p.issues.filter { it.severity != Severity.INFO }.map { it.toString() }
            if (!p.valid) { lines += ImportReportLine(p.name, p.sourceId, ImportOutcome.FAILED, c.errors.map { it.toString() } + warn.filterNot { it.startsWith("error") }); continue }
            if (!c.include) {
                lines += ImportReportLine(p.name, p.sourceId, ImportOutcome.SKIPPED, listOf(when (c.status) {
                    ImportStatus.UPDATE -> "Already in your library — not replaced (you didn't approve the update)."
                    ImportStatus.BUILT_IN_CONFLICT -> "Matches a built-in recipe; copy not imported."
                    else -> "Not selected."
                }))
                continue
            }
            try {
                val existing = db.recipes().get(c.targetId)
                val r = p.recipe!!.copy(
                    id = c.targetId,
                    sortOrder = existing?.sortOrder ?: order++,
                    isFavorite = existing?.isFavorite ?: false,
                    photoUri = existing?.photoUri,
                    importedAt = now,
                )
                db.recipes().replaceRecipe(r, p.ingredients.map { it.copy(recipeId = c.targetId) })
                val details = warn.toMutableList()
                if (c.status == ImportStatus.BUILT_IN_CONFLICT) details += "Imported as a separate recipe (id ${c.targetId}); the built-in recipe was not changed."
                p.imageRef?.let { ref -> saveImage(c.targetId, ref)?.let { details += it } }
                lines += ImportReportLine(r.name, p.sourceId, if (existing != null) ImportOutcome.UPDATED else ImportOutcome.IMPORTED, details)
            } catch (e: Exception) {
                lines += ImportReportLine(p.name, p.sourceId, ImportOutcome.FAILED, listOf("Couldn't save: ${e.message}"))
            }
        }
        return ImportReport(lines, plan.fileIssues)
    }

    /** Saves an embedded or linked image. Returns a message if it couldn't be used (the recipe still imports). */
    private suspend fun saveImage(recipeId: String, ref: String): String? = withContext(Dispatchers.IO) {
        try {
            val bytes: ByteArray = when {
                ref.startsWith("data:image/") -> Base64.decode(ref.substringAfter("base64,", ""), Base64.DEFAULT)
                ref.startsWith("https://") || ref.startsWith("http://") -> {
                    val conn = (URL(ref).openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 15_000 }
                    try {
                        if (conn.responseCode !in 200..299) return@withContext "Image not downloaded (HTTP ${conn.responseCode}); the illustration is used."
                        if (conn.contentType?.startsWith("image/") == false) return@withContext "Image link isn't an image; the illustration is used."
                        conn.inputStream.use { it.readBytes() }
                    } finally { conn.disconnect() }
                }
                else -> return@withContext "Image reference not supported; the illustration is used."
            }
            if (bytes.isEmpty() || bytes.size > 12_000_000) return@withContext "Image empty or too large; the illustration is used."
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val f = File(dir, "$recipeId-${time.nowMillis()}.img").apply { writeBytes(bytes) }
            db.recipes().setPhoto(recipeId, f.absolutePath)
            null
        } catch (e: Exception) {
            "Image couldn't be loaded (${e.message ?: e.javaClass.simpleName}); the illustration is used."
        }
    }

    // ── Export ────────────────────────────────────────────────

    /** Exports recipes in the same forma.recipe v1 format so collections round-trip between devices. */
    suspend fun export(ids: List<String>? = null, includePhotos: Boolean = true): String {
        val recipes = db.recipes().all().filter { ids == null || it.id in ids }.sortedBy { it.sortOrder }
        val arr = recipes.map { r -> exportOne(r, db.recipes().ingredients(r.id), includePhotos) }
        val root = buildJsonObject {
            put("schema", RecipeImportFormat.SCHEMA)
            put("version", RecipeImportFormat.VERSION)
            put("generator", "FORMA")
            put("generated_at", java.time.Instant.ofEpochMilli(time.nowMillis()).toString())
            put("recipes", JsonArray(arr))
        }
        return RecipeImportFormat.json.encodeToString(JsonObject.serializer(), root)
    }

    private suspend fun exportOne(r: RecipeEntity, ings: List<IngredientEntity>, includePhotos: Boolean): JsonObject {
        val milk = if (r.usesMilkChoice) com.forma.app.data.seed.MilkOption.from(r.selectedMilk) ?: com.forma.app.data.seed.MilkOption.from(r.defaultMilk) else null
        val m = RecipeMath.macros(ings, Portion.STANDARD, 1.0, milk)
        val total = ings.firstOrNull { it.unit == TOTAL_UNIT }
        val real = ings.filter { it.unit != TOTAL_UNIT }
        val scaled = real.filter { it.amount > 0 && it.largeAmount != it.amount }
        val large = total?.let { it.largeAmount / it.amount } ?: scaled.map { it.largeAmount / it.amount }.average().takeIf { !it.isNaN() } ?: 1.0
        val image = if (includePhotos) r.photoUri?.let { path ->
            withContext(Dispatchers.IO) { runCatching { File(path).readBytes() }.getOrNull() }
                ?.let { "data:image/jpeg;base64," + Base64.encodeToString(it, Base64.NO_WRAP) }
        } else null
        return buildJsonObject {
            put("id", r.id)
            put("name", r.name)
            put("category", Category.from(r.category).label)
            put("kosher_type", if (r.isFish) "Fish" else r.kosherStatus.lowercase().replaceFirstChar { it.uppercase() })
            put("description", r.summary)
            put("prep_time_minutes", r.prepMinutes)
            put("cook_time_minutes", r.cookMinutes)
            put("servings", 1)
            put("ingredients", JsonArray(real.map { i ->
                val im = RecipeMath.ingredientMacros(i, Portion.STANDARD, milk)
                buildJsonObject {
                    put("name", if (i.isMilk && milk != null) milk.label else i.name)
                    put("quantity", i.amount)
                    put("unit", i.unit)
                    i.note?.let { put("notes", it) }
                    if (total == null) put("nutrition", buildJsonObject {
                        put("calories", round1(im.kcal)); put("protein_g", round1(im.proteinG))
                        put("carbs_g", round1(im.carbsG ?: 0.0)); put("fat_g", round1(im.fatG ?: 0.0))
                    })
                    put("scales_with_portion", i.largeAmount != i.amount)
                    if (i.optional) put("optional", true)
                }
            }))
            if (r.prepSteps.isNotEmpty()) put("prep_instructions", JsonArray(r.prepSteps.map(::JsonPrimitive)))
            if (r.prepSteps.isNotEmpty()) put("cook_instructions", JsonArray(r.cookSteps.map(::JsonPrimitive)))
            else put("instructions", JsonArray(r.cookSteps.map(::JsonPrimitive)))
            put("nutrition_per_serving", buildJsonObject {
                put("calories", round1(m.kcal)); put("protein_g", round1(m.proteinG))
                put("carbs_g", round1(m.carbsG ?: 0.0)); put("fat_g", round1(m.fatG ?: 0.0))
                r.fiberG?.let { put("fiber_g", round1(it)) }
            })
            put("nutrition_source", if (r.nutritionSource == "VERIFIED_LABELS") "verified_labels" else "estimated")
            put("portions", buildJsonObject { put("standard_multiplier", 1); put("large_multiplier", round1(large)) })
            put("tags", JsonArray(r.tags.map(::JsonPrimitive)))
            if (r.storageNotes.isNotEmpty()) put("storage", buildJsonObject { put("notes", r.storageNotes.joinToString(" ")) })
            put("kosher_notes", JsonArray(r.kosherNotes.map(::JsonPrimitive)))
            if (r.safetyNotes.isNotEmpty()) put("safety_notes", JsonArray(r.safetyNotes.map(::JsonPrimitive)))
            image?.let { put("image", it) }
        }
    }

    private fun round1(v: Double) = Math.round(v * 10) / 10.0
}
