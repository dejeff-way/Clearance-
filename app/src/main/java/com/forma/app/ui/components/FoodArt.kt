package com.forma.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.forma.app.ui.theme.Forma
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Generated top-down food illustrations. Each recipe is drawn from its actual components — so a poke bowl shows
 * rice, fish, cucumber and avocado in their own sections — giving the library a cohesive, editorial look without
 * stock photography. Users can replace any illustration with their own photo.
 */
enum class Vessel { BOWL, PLATE, PAN, CUP, PINT, DUO }

enum class El(val base: Long, val accent: Long) {
    RICE(0xFFF2EDE1, 0xFFE2D9C6), SALMON(0xFFE8896A, 0xFFF4B39A), TUNA(0xFFB3424D, 0xFFCF6B72),
    CUCUMBER(0xFFB8D193, 0xFF55803F), AVOCADO(0xFFC3D08A, 0xFF6E8B3D), CARROT(0xFFE8954A, 0xFFF0B072),
    SCALLION(0xFF86AD5E, 0xFFC9DDAE), SESAME(0xFFF4EBD7, 0xFF2B2B2B), CHICKEN(0xFFDDB57F, 0xFFB8854F),
    TOMATO(0xFFD9564A, 0xFFF08A7C), ONION(0xFFB47AA4, 0xFFEBD8E6), BEEF(0xFF7A4A36, 0xFF5B3526),
    PARSLEY(0xFF4E7E3C, 0xFF79A65C), PEPPERS(0xFFD9534A, 0xFFE8B53C), BEANS(0xFF2E2A30, 0xFF4A4350),
    FILLET(0xFFE8896A, 0xFFF6C1AA), BROCCOLI(0xFF4F7B3A, 0xFF76A050), POTATO(0xFFE7C47E, 0xFFC99A4E),
    GREEN_BEANS(0xFF6E9B4A, 0xFF8DB866), STEAK(0xFF8C4E3F, 0xFFC97E6C), LETTUCE(0xFFAFCB86, 0xFF7FA35A),
    PICKLES(0xFF95A64C, 0xFFC5CF85), BURGER(0xFF6B4130, 0xFF4E2E21), MEATBALLS(0xFF7F4A35, 0xFF5E3424),
    SAUCE(0xFFB9412F, 0xFFD0614C), ZUCCHINI(0xFF8DB36A, 0xFFEAF0CC), EGG(0xFFF3D36B, 0xFFFFF3CC),
    SPINACH(0xFF3F6B33, 0xFF5D8A47), MUSHROOM(0xFFBBA68C, 0xFF8E7860), YOGURT(0xFFF8F6F0, 0xFFE9E5DB),
    STRAWBERRY(0xFFD94B4F, 0xFFF4D3A4), BLUEBERRY(0xFF4A5A8C, 0xFF7584B5), CHIA(0xFF3A3A3A, 0xFF777777),
    OATS(0xFFDCC8A0, 0xFFC4AD7E), CHOCOLATE(0xFF5A3A2E, 0xFF7A5446), COFFEE(0xFF8A6448, 0xFFB08A6C),
    APPLE(0xFFC9483F, 0xFF9DBB5B), SHAKE(0xFFD9CCB9, 0xFFC2B19B), CINNAMON(0xFFA0683F, 0xFF7E4E2C),
    LEMON(0xFFF0D46A, 0xFFF7E7A8), BERRIES(0xFF8E3A5A, 0xFF4A5A8C),
}

data class ArtSpec(val vessel: Vessel, val parts: List<Pair<El, Float>>, val center: El? = null, val garnish: El? = null)

object FoodArtSpecs {
    val specs: Map<String, ArtSpec> = mapOf(
        "protein_egg_scramble" to ArtSpec(Vessel.PAN, listOf(El.EGG to 3f, El.SPINACH to 1f, El.MUSHROOM to 1f, El.PEPPERS to 0.6f)),
        "berry_greek_yogurt_bowl" to ArtSpec(Vessel.BOWL, listOf(El.YOGURT to 2f), center = El.YOGURT, garnish = El.CHIA),
        "protein_overnight_oats" to ArtSpec(Vessel.CUP, listOf(El.OATS to 1f), center = El.BERRIES, garnish = El.CINNAMON),
        "salmon_poke_bowl" to ArtSpec(Vessel.BOWL, listOf(El.RICE to 1.6f, El.SALMON to 1.6f, El.CUCUMBER to 0.8f, El.AVOCADO to 0.8f, El.CARROT to 0.6f), garnish = El.SESAME),
        "tuna_poke_bowl" to ArtSpec(Vessel.BOWL, listOf(El.RICE to 1.6f, El.TUNA to 1.6f, El.CUCUMBER to 0.8f, El.AVOCADO to 0.8f, El.CARROT to 0.6f), garnish = El.SESAME),
        "chicken_shawarma_bowl" to ArtSpec(Vessel.BOWL, listOf(El.RICE to 1.4f, El.CHICKEN to 1.8f, El.CUCUMBER to 0.7f, El.TOMATO to 0.7f, El.ONION to 0.5f), garnish = El.PARSLEY),
        "mediterranean_beef_bowl" to ArtSpec(Vessel.BOWL, listOf(El.RICE to 1.4f, El.BEEF to 1.8f, El.CUCUMBER to 0.7f, El.TOMATO to 0.7f, El.ONION to 0.4f), garnish = El.PARSLEY),
        "chicken_fajita_bowl" to ArtSpec(Vessel.BOWL, listOf(El.RICE to 1.3f, El.CHICKEN to 1.6f, El.PEPPERS to 1f, El.BEANS to 0.8f, El.ONION to 0.5f), garnish = El.LEMON),
        "lemon_garlic_salmon" to ArtSpec(Vessel.PLATE, listOf(El.BROCCOLI to 1f, El.POTATO to 1f), center = El.FILLET, garnish = El.LEMON),
        "garlic_london_broil" to ArtSpec(Vessel.PLATE, listOf(El.GREEN_BEANS to 1f, El.POTATO to 1f), center = El.STEAK),
        "lean_beef_burger_bowl" to ArtSpec(Vessel.BOWL, listOf(El.LETTUCE to 1.6f, El.POTATO to 1.2f, El.TOMATO to 0.6f, El.PICKLES to 0.6f, El.ONION to 0.4f), center = El.BURGER),
        "garlic_beef_meatballs" to ArtSpec(Vessel.PLATE, listOf(El.RICE to 1f, El.ZUCCHINI to 1f), center = El.MEATBALLS, garnish = El.PARSLEY),
        "garlic_chicken_potatoes" to ArtSpec(Vessel.PLATE, listOf(El.POTATO to 1.2f, El.GREEN_BEANS to 1f), center = El.CHICKEN, garnish = El.LEMON),
        "chocolate_tnuva_creami" to ArtSpec(Vessel.PINT, listOf(El.CHOCOLATE to 1f), center = El.CHOCOLATE),
        "coffee_protein_creami" to ArtSpec(Vessel.PINT, listOf(El.COFFEE to 1f), center = El.COFFEE),
        "greek_yogurt_snack" to ArtSpec(Vessel.CUP, listOf(El.YOGURT to 1f), center = El.STRAWBERRY, garnish = El.CINNAMON),
        "fruit_and_protein" to ArtSpec(Vessel.DUO, listOf(El.APPLE to 1f, El.SHAKE to 1f)),
    )

    fun forKey(key: String): ArtSpec = specs[key] ?: ArtSpec(Vessel.PLATE, listOf(El.RICE to 1f, El.BROCCOLI to 1f), center = El.CHICKEN)
}

@Composable
fun FoodArt(artKey: String, modifier: Modifier = Modifier, description: String = "Illustration") {
    val c = Forma.colors
    val spec = remember(artKey) { FoodArtSpecs.forKey(artKey) }
    val seed = remember(artKey) { artKey.hashCode() }
    val bg = if (c.isDark) Color(0xFF232624) else Color(0xFFEDE8DE)
    val table = if (c.isDark) Color(0xFF1C1F1D) else Color(0xFFE6E0D4)
    Box(modifier.background(bg).semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxSize()) {
            val rnd = Random(seed)
            // Subtle linen texture on the "table"
            repeat(40) {
                val y = rnd.nextFloat() * size.height
                drawLine(table, Offset(0f, y), Offset(size.width, y + rnd.nextFloat() * 6 - 3), 1f)
            }
            val cx = size.width * 0.5f
            val cy = size.height * 0.5f
            val r = min(size.width, size.height) * 0.40f
            when (spec.vessel) {
                Vessel.BOWL -> bowl(spec, Offset(cx, cy), r, rnd, c.isDark)
                Vessel.PLATE -> plate(spec, Offset(cx, cy), r * 1.08f, rnd, c.isDark)
                Vessel.PAN -> pan(spec, Offset(cx, cy), r, rnd, c.isDark)
                Vessel.CUP -> cup(spec, Offset(cx, cy), r * 0.82f, rnd)
                Vessel.PINT -> pint(spec, Offset(cx, cy), r * 0.8f, rnd)
                Vessel.DUO -> duo(Offset(cx, cy), r * 0.62f, rnd)
            }
        }
    }
}

private fun col(v: Long) = Color(v)

private fun DrawScope.shadow(center: Offset, r: Float) {
    drawCircle(Color.Black.copy(alpha = 0.07f), r * 1.04f, center + Offset(r * 0.04f, r * 0.07f))
}

private fun DrawScope.bowl(spec: ArtSpec, center: Offset, r: Float, rnd: Random, dark: Boolean) {
    shadow(center, r)
    drawCircle(if (dark) Color(0xFF3A3E3B) else Color(0xFFFAF8F3), r, center)
    drawCircle(Color.Black.copy(alpha = 0.05f), r * 0.9f, center, style = Stroke(r * 0.02f))
    val inner = r * 0.86f
    val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, inner)) }
    clipPath(clip) {
        val total = spec.parts.sumOf { it.second.toDouble() }.toFloat()
        var a = -100f + rnd.nextFloat() * 30
        spec.parts.forEach { (el, w) ->
            val sweep = w / total * 360f
            drawArc(col(el.base), a, sweep, true, center - Offset(inner, inner), Size(inner * 2, inner * 2))
            scatter(el, center, inner, a, sweep, rnd)
            a += sweep
        }
        if (spec.center != null) {
            drawCircle(col(spec.center.base), inner * 0.38f, center)
            scatterDisc(spec.center, center, inner * 0.36f, rnd)
        }
        spec.garnish?.let { g -> sprinkle(g, center, inner * 0.9f, rnd, 26) }
    }
}

private fun DrawScope.plate(spec: ArtSpec, center: Offset, r: Float, rnd: Random, dark: Boolean) {
    shadow(center, r)
    drawCircle(if (dark) Color(0xFF3A3E3B) else Color(0xFFFBFAF6), r, center)
    drawCircle(Color.Black.copy(alpha = 0.06f), r * 0.78f, center, style = Stroke(r * 0.012f))
    val inner = r * 0.74f
    // Sides occupy the bottom and right; hero sits upper-left.
    spec.parts.forEachIndexed { i, (el, _) ->
        val ang = if (i == 0) 60.0 else -20.0
        val pc = center + Offset((cos(ang * PI / 180) * inner * 0.45f).toFloat(), (sin(ang * PI / 180) * inner * 0.45f).toFloat())
        pile(el, pc, inner * 0.42f, rnd)
    }
    val hc = center + Offset(-inner * 0.28f, -inner * 0.22f)
    spec.center?.let { hero(it, hc, inner * 0.48f, rnd) }
    spec.garnish?.let { g -> sprinkle(g, hc, inner * 0.4f, rnd, 8) }
}

private fun DrawScope.pan(spec: ArtSpec, center: Offset, r: Float, rnd: Random, dark: Boolean) {
    // Handle
    rotate(-30f, center) {
        drawRoundRect(Color(0xFF2B2D2C), center + Offset(r * 0.9f, -r * 0.09f), Size(r * 0.75f, r * 0.18f), CornerRadius(r * 0.09f))
    }
    shadow(center, r)
    drawCircle(Color(0xFF2F3231), r, center)
    drawCircle(Color(0xFF3B3F3D), r * 0.9f, center)
    val inner = r * 0.86f
    clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, inner)) }) {
        repeat(9) { blob(col(El.EGG.base), center, inner * 0.75f, inner * 0.28f, rnd) }
        repeat(6) { blob(col(El.EGG.accent), center, inner * 0.7f, inner * 0.13f, rnd) }
        spec.parts.drop(1).forEach { (el, _) -> sprinkle(el, center, inner * 0.8f, rnd, 18, big = true) }
    }
}

private fun DrawScope.cup(spec: ArtSpec, center: Offset, r: Float, rnd: Random) {
    shadow(center, r)
    drawCircle(Color(0xFFDCE3E0).copy(alpha = 0.9f), r, center)
    drawCircle(Color.White.copy(alpha = 0.5f), r, center, style = Stroke(r * 0.05f))
    val inner = r * 0.88f
    clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, inner)) }) {
        val base = spec.parts.first().first
        drawCircle(col(base.base), inner, center)
        scatterDisc(base, center, inner, rnd)
        spec.center?.let { top -> sprinkle(top, center, inner * 0.62f, rnd, 12, big = true) }
        spec.garnish?.let { g -> sprinkle(g, center, inner * 0.85f, rnd, 30) }
    }
}

private fun DrawScope.pint(spec: ArtSpec, center: Offset, r: Float, rnd: Random) {
    shadow(center, r)
    drawCircle(Color(0xFFF4F4F2), r, center)
    drawCircle(Color.Black.copy(alpha = 0.07f), r * 0.92f, center, style = Stroke(r * 0.03f))
    val el = spec.parts.first().first
    val inner = r * 0.84f
    clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, inner)) }) {
        drawCircle(col(el.base), inner, center)
        // Creamy swirl ridges
        for (k in 0 until 5) {
            val rr = inner * (0.2f + k * 0.16f)
            drawArc(col(el.accent), rnd.nextFloat() * 360, 200f + rnd.nextFloat() * 80, false,
                center - Offset(rr, rr), Size(rr * 2, rr * 2), style = Stroke(inner * 0.05f, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.duo(center: Offset, r: Float, rnd: Random) {
    val a = center + Offset(-r * 0.75f, r * 0.1f)
    shadow(a, r * 0.8f)
    drawCircle(col(El.APPLE.base), r * 0.8f, a)
    drawCircle(col(0xFFE36A5F), r * 0.5f, a + Offset(-r * 0.2f, -r * 0.2f))
    drawLine(Color(0xFF5C3D2B), a + Offset(0f, -r * 0.7f), a + Offset(r * 0.06f, -r * 0.95f), r * 0.06f, StrokeCap.Round)
    drawOval(col(El.APPLE.accent), a + Offset(r * 0.08f, -r * 1.0f), Size(r * 0.36f, r * 0.18f))
    val b = center + Offset(r * 0.85f, -r * 0.05f)
    shadow(b, r * 0.62f)
    drawCircle(Color(0xFF2B2D2C), r * 0.62f, b)
    drawCircle(col(El.SHAKE.base), r * 0.52f, b)
    repeat(14) { drawCircle(Color.White.copy(alpha = 0.5f), r * 0.025f + rnd.nextFloat() * r * 0.03f, b + randIn(r * 0.45f, rnd)) }
}

private fun randIn(radius: Float, rnd: Random): Offset {
    val t = rnd.nextFloat() * 2 * PI
    val d = sqrt(rnd.nextFloat()) * radius
    return Offset((cos(t) * d).toFloat(), (sin(t) * d).toFloat())
}

private fun DrawScope.blob(color: Color, center: Offset, spread: Float, size: Float, rnd: Random) {
    val p = center + randIn(spread, rnd)
    drawOval(color, p - Offset(size, size * 0.7f), Size(size * (1.4f + rnd.nextFloat() * 0.6f), size * (1f + rnd.nextFloat() * 0.5f)))
}

/** Textured pieces scattered inside a wedge. */
private fun DrawScope.scatter(el: El, center: Offset, r: Float, startDeg: Float, sweep: Float, rnd: Random) {
    val n = (sweep / 6).toInt().coerceIn(6, 40)
    repeat(n) {
        val ang = (startDeg + 6 + rnd.nextFloat() * (sweep - 12)) * PI / 180
        val d = r * (0.42f + rnd.nextFloat() * 0.5f)
        val p = center + Offset((cos(ang) * d).toFloat(), (sin(ang) * d).toFloat())
        piece(el, p, r * 0.085f, rnd, (ang * 180 / PI).toFloat())
    }
}

private fun DrawScope.scatterDisc(el: El, center: Offset, r: Float, rnd: Random) {
    repeat(18) { piece(el, center + randIn(r * 0.85f, rnd), r * 0.16f, rnd, rnd.nextFloat() * 360) }
}

private fun DrawScope.pile(el: El, center: Offset, r: Float, rnd: Random) {
    repeat(14) { piece(el, center + randIn(r * 0.7f, rnd), r * 0.22f, rnd, rnd.nextFloat() * 360) }
}

private fun DrawScope.sprinkle(el: El, center: Offset, r: Float, rnd: Random, n: Int, big: Boolean = false) {
    repeat(n) {
        val p = center + randIn(r, rnd)
        if (big) piece(el, p, r * 0.09f, rnd, rnd.nextFloat() * 360)
        else drawOval(col(if (rnd.nextBoolean()) el.base else el.accent), p, Size(r * 0.028f, r * 0.016f))
    }
}

private fun DrawScope.piece(el: El, p: Offset, s: Float, rnd: Random, rot: Float) {
    val b = col(el.base); val a = col(el.accent)
    when (el) {
        El.RICE, El.OATS -> repeat(3) { drawOval(a, p + Offset(rnd.nextFloat() * s * 2 - s, rnd.nextFloat() * s * 2 - s), Size(s * 0.5f, s * 0.25f)) }
        El.SALMON, El.TUNA -> rotate(rot, p) {
            drawRoundRect(b, p - Offset(s, s), Size(s * 2, s * 2), CornerRadius(s * 0.35f))
            drawLine(a, p + Offset(-s * 0.7f, -s * 0.3f), p + Offset(s * 0.7f, s * 0.1f), s * 0.18f, StrokeCap.Round)
        }
        El.CUCUMBER, El.ZUCCHINI -> { drawCircle(col(if (el == El.CUCUMBER) 0xFF55803F else 0xFF5E8A3E), s * 1.05f, p); drawCircle(a.takeIf { el == El.ZUCCHINI } ?: b, s * 0.85f, p) }
        El.AVOCADO -> rotate(rot, p) { drawOval(a, p - Offset(s * 1.3f, s * 0.6f), Size(s * 2.6f, s * 1.2f)); drawOval(b, p - Offset(s * 1.1f, s * 0.45f), Size(s * 2.2f, s * 0.9f)) }
        El.CARROT -> rotate(rot, p) { drawLine(b, p - Offset(s, 0f), p + Offset(s, 0f), s * 0.28f, StrokeCap.Round) }
        El.SCALLION, El.PARSLEY -> drawCircle(if (rnd.nextBoolean()) b else a, s * 0.35f, p)
        El.CHICKEN -> rotate(rot, p) {
            drawRoundRect(b, p - Offset(s * 1.5f, s * 0.6f), Size(s * 3f, s * 1.2f), CornerRadius(s * 0.5f))
            drawLine(a, p + Offset(-s * 0.6f, -s * 0.5f), p + Offset(-s * 0.2f, s * 0.5f), s * 0.18f)
            drawLine(a, p + Offset(s * 0.3f, -s * 0.5f), p + Offset(s * 0.7f, s * 0.5f), s * 0.18f)
        }
        El.TOMATO -> { drawRoundRect(b, p - Offset(s * 0.8f, s * 0.8f), Size(s * 1.6f, s * 1.6f), CornerRadius(s * 0.3f)); drawCircle(a, s * 0.3f, p) }
        El.ONION -> rotate(rot, p) { drawArc(b, 200f, 140f, false, p - Offset(s, s), Size(s * 2, s * 2), style = Stroke(s * 0.25f, cap = StrokeCap.Round)) }
        El.BEEF, El.BEANS -> repeat(2) { drawCircle(if (rnd.nextBoolean()) b else a, s * (0.45f + rnd.nextFloat() * 0.3f), p + Offset(rnd.nextFloat() * s - s / 2, rnd.nextFloat() * s - s / 2)) }
        El.PEPPERS -> rotate(rot, p) { drawLine(if (rnd.nextBoolean()) b else a, p - Offset(s * 1.2f, 0f), p + Offset(s * 1.2f, 0f), s * 0.42f, StrokeCap.Round) }
        El.BROCCOLI -> { repeat(4) { drawCircle(if (rnd.nextBoolean()) b else a, s * 0.55f, p + Offset(rnd.nextFloat() * s - s / 2, rnd.nextFloat() * s - s / 2)) } }
        El.POTATO -> rotate(rot, p) { drawRoundRect(b, p - Offset(s * 0.8f, s * 0.8f), Size(s * 1.6f, s * 1.6f), CornerRadius(s * 0.4f)); drawRoundRect(a, p - Offset(s * 0.8f, s * 0.8f), Size(s * 1.6f, s * 0.5f), CornerRadius(s * 0.3f)) }
        El.GREEN_BEANS -> rotate(rot, p) { drawLine(b, p - Offset(s * 1.6f, 0f), p + Offset(s * 1.6f, 0f), s * 0.3f, StrokeCap.Round) }
        El.LETTUCE -> rotate(rot, p) { drawOval(if (rnd.nextBoolean()) b else a, p - Offset(s * 1.4f, s * 0.7f), Size(s * 2.8f, s * 1.4f)) }
        El.PICKLES -> { drawCircle(a, s * 0.8f, p); drawCircle(b, s * 0.8f, p, style = Stroke(s * 0.2f)) }
        El.SPINACH -> rotate(rot, p) { drawOval(if (rnd.nextBoolean()) b else a, p - Offset(s * 0.9f, s * 0.5f), Size(s * 1.8f, s * 1.0f)) }
        El.MUSHROOM -> { drawCircle(b, s * 0.7f, p); drawCircle(a, s * 0.3f, p) }
        El.STRAWBERRY -> { drawCircle(b, s * 0.85f, p); repeat(3) { drawCircle(a, s * 0.08f, p + Offset(rnd.nextFloat() * s - s / 2, rnd.nextFloat() * s - s / 2)) } }
        El.BLUEBERRY -> drawCircle(b, s * 0.55f, p)
        El.BERRIES -> drawCircle(if (rnd.nextBoolean()) col(0xFF4A5A8C) else col(0xFFD94B4F), s * 0.6f, p)
        El.YOGURT, El.SHAKE -> drawCircle(a, s * 0.4f, p)
        else -> drawCircle(a, s * 0.4f, p)
    }
}

private fun DrawScope.hero(el: El, p: Offset, s: Float, rnd: Random) {
    val b = col(el.base); val a = col(el.accent)
    when (el) {
        El.FILLET -> rotate(-18f, p) {
            drawRoundRect(b, p - Offset(s, s * 0.5f), Size(s * 2, s), CornerRadius(s * 0.4f))
            for (i in -3..3) drawLine(a, p + Offset(i * s * 0.25f, -s * 0.4f), p + Offset(i * s * 0.25f + s * 0.15f, s * 0.4f), s * 0.05f, StrokeCap.Round)
        }
        El.STEAK -> rotate(-12f, p) {
            for (i in 0 until 5) {
                val o = p + Offset(-s * 0.9f + i * s * 0.42f, -s * 0.45f)
                drawRoundRect(b, o, Size(s * 0.38f, s * 0.95f), CornerRadius(s * 0.08f))
                drawRoundRect(a, o + Offset(s * 0.06f, s * 0.1f), Size(s * 0.26f, s * 0.75f), CornerRadius(s * 0.06f))
            }
        }
        El.BURGER -> { drawCircle(b, s * 0.62f, p); drawCircle(a, s * 0.62f, p, style = Stroke(s * 0.06f)); repeat(10) { drawCircle(a, s * 0.05f, p + randIn(s * 0.5f, rnd)) } }
        El.MEATBALLS -> {
            drawCircle(col(El.SAUCE.base), s * 0.95f, p)
            for (i in 0 until 5) { val q = p + Offset((cos(i * 1.256) * s * 0.5).toFloat(), (sin(i * 1.256) * s * 0.5).toFloat()); drawCircle(b, s * 0.3f, q); drawCircle(a, s * 0.3f, q, style = Stroke(s * 0.04f)) }
        }
        El.CHICKEN -> rotate(-15f, p) {
            drawRoundRect(b, p - Offset(s, s * 0.55f), Size(s * 2, s * 1.1f), CornerRadius(s * 0.45f))
            for (i in -2..2) drawLine(a, p + Offset(i * s * 0.35f - s * 0.1f, -s * 0.45f), p + Offset(i * s * 0.35f + s * 0.15f, s * 0.45f), s * 0.07f, StrokeCap.Round)
        }
        else -> drawCircle(b, s * 0.6f, p)
    }
}
