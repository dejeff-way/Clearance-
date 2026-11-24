package com.forma.app.data.seed

import com.forma.app.data.db.IngredientEntity
import com.forma.app.data.db.RecipeEntity

/**
 * Built-in recipe library. Nutrition is calculated per ingredient from USDA FoodData Central values and typical US
 * product labels. All figures are estimates until checked against the exact products you buy and weighed portions.
 *
 * Bump [SEED_VERSION] when recipe content changes; user state (favourites, photos, milk choice) is preserved.
 */
object RecipeSeed {
    const val SEED_VERSION = 1

    data class Ing(
        val name: String,
        val amount: Double,
        val unit: String,
        val large: Double,
        val kcal: Double,
        val p: Double,
        val c: Double,
        val f: Double,
        val note: String? = null,
        val optional: Boolean = false,
        val isMilk: Boolean = false,
    )

    data class Seed(
        val id: String,
        val name: String,
        val category: Category,
        val kosher: KosherStatus,
        val isFish: Boolean = false,
        val summary: String,
        val prepMinutes: Int,
        val cookMinutes: Int,
        val ingredients: List<Ing>,
        val prep: List<String>,
        val cook: List<String>,
        val kosherNotes: List<String>,
        val safety: List<String> = emptyList(),
        val tips: List<String> = emptyList(),
        val standardLabel: String,
        val largeLabel: String,
        val refKcal: Int? = null,
        val refProtein: Int? = null,
        val defaultMilk: MilkOption? = null,
    )

    private fun toTaste(name: String, note: String? = null) = Ing(name, 0.0, "to taste", 0.0, 0.0, 0.0, 0.0, 0.0, note)
    private fun milk(ml: Double, large: Double, note: String? = null) =
        Ing("Milk of choice", ml, "ml", large, 0.0, 0.0, 0.0, 0.0, note, isMilk = true)

    // Shared kosher guidance -------------------------------------------------------------------------------------
    private const val MEAT_NOTE = "Meat (fleishig). Use chicken or beef with a reliable kosher certification — it " +
        "will already be kashered (soaked and salted). Prepare with meat utensils and never serve with dairy."
    private const val MEAT_WAIT = "Dairy should not follow this meal until your customary waiting time has passed."
    private const val DAIRY_NOTE = "Dairy (milchig). Not for serving with or after a meat meal — observe your " +
        "customary waiting time after meat. Use dairy utensils."
    private const val DAIRY_CERT = "Yogurt, milk and protein powder each need a reliable hechsher; follow your own " +
        "practice regarding chalav Yisrael."
    private const val TNUVA_NOTE = "Tnuva Qualify is a milk-based (whey) protein, so anything made with it is " +
        "Dairy regardless of which milk you use. Check the package for its hechsher and dairy designation."
    private const val FISH_NOTE = "Fish is pareve, but by long-standing custom it is not eaten together with meat. " +
        "Keep it separate from meat dishes and utensils according to your custom."
    private const val RAW_FISH_SOURCE = "Raw fish must come from a reliable kosher fish supplier (for example, a " +
        "kosher fish store under supervision). Salmon and tuna have fins and scales, but skinless fillets cannot be " +
        "identified by sight, so certification or supervised cutting is required."
    private const val SUSHI_GRADE = "\"Sushi-grade\" is a marketing term about freshness and handling — it is not a " +
        "kosher certification, and not every supermarket fish counter sells fish suitable for eating raw."
    private const val PRODUCE_CHECK = "Leafy greens, fresh herbs and some vegetables can harbour insects. Use " +
        "pre-checked certified produce or check according to your rabbi's guidance."
    private const val CERTIFIED_PANTRY = "Packaged items (sauces, spices, oils, vinegar, beans) should carry a " +
        "reliable hechsher. Don't assume a product is kosher just because it contains no meat."
    private const val ESTIMATE = "Nutrition is an estimate from typical ingredient values."

    private val RAW_FISH_SAFETY = listOf(
        "Buy fish that the supplier confirms is intended for raw consumption and has been frozen to destroy " +
            "parasites (FDA guidance: −4 °F / −20 °C for 7 days, or −31 °F / −35 °C for 15 hours).",
        "Keep the fish at or below 40 °F (4 °C) until the moment you cut it. Use a clean, dedicated board and knife.",
        "Eat raw fish the day you prepare it. Discard anything left at room temperature for more than 2 hours " +
            "(1 hour above 90 °F / 32 °C).",
        "Raw fish is not recommended for anyone pregnant, immunocompromised, or for young children.",
        "Cool cooked rice quickly — spread it out and refrigerate within 1 hour — before assembling.",
    )

    val all: List<Seed> = listOf(
        // ─────────────────────────────── BREAKFAST ───────────────────────────────
        Seed(
            id = "protein_egg_scramble", name = "Protein Egg Scramble", category = Category.BREAKFAST,
            kosher = KosherStatus.PAREVE,
            summary = "Soft-scrambled eggs and whites folded through spinach, mushrooms and peppers.",
            prepMinutes = 5, cookMinutes = 8,
            ingredients = listOf(
                Ing("Whole eggs", 2.0, "large", 3.0, 143.0, 12.6, 0.7, 9.5),
                Ing("Liquid egg whites", 150.0, "g", 200.0, 78.0, 16.4, 1.1, 0.3, "certified kosher carton"),
                Ing("Baby spinach", 1.0, "cup", 1.5, 7.0, 0.9, 1.1, 0.1, "pre-checked"),
                Ing("Mushrooms, diced", 0.5, "cup", 0.75, 8.0, 1.1, 1.1, 0.1),
                Ing("Bell pepper, diced", 0.25, "cup", 0.5, 10.0, 0.4, 2.2, 0.1),
                Ing("Cooking spray", 1.0, "second spray", 1.0, 7.0, 0.0, 0.0, 0.8, "labels round this to 0"),
                toTaste("Salt and black pepper"),
            ),
            prep = listOf(
                "Dice the mushrooms and pepper into small, even pieces (about ¼ inch) so they cook quickly.",
                "Check the spinach if it isn't pre-checked. Roughly chop it.",
                "Crack each egg into a clear glass first and check for blood spots, then add it to a bowl. " +
                    "Add the egg whites, a pinch of salt and pepper, and whisk until uniform.",
            ),
            cook = listOf(
                "Heat a nonstick pan over medium heat and coat lightly with cooking spray.",
                "Cook the mushrooms and peppers for 3–4 minutes, stirring, until the mushrooms release their " +
                    "liquid and begin to brown.",
                "Add the spinach and stir for 30–60 seconds until just wilted.",
                "Lower the heat to medium-low. Pour in the eggs and let them sit for 15 seconds, then push them " +
                    "slowly from the edges to the centre with a spatula.",
                "Keep folding until there is no visible liquid egg and the curds are set but still glossy — " +
                    "about 2–3 minutes. Season and serve immediately.",
            ),
            kosherNotes = listOf(
                "Pareve when cooked in a pareve pan with a pareve cooking spray. Adding cheese or butter makes it Dairy.",
                "Check each egg for blood spots before adding it to the bowl.",
                PRODUCE_CHECK,
            ),
            safety = listOf("Cook eggs until no liquid egg remains (160 °F / 71 °C)."),
            tips = listOf("Add a few pinches of chilli flakes or smoked paprika for flavour without calories."),
            standardLabel = "2 eggs + 150 g whites", largeLabel = "3 eggs + 200 g whites",
            refKcal = 310, refProtein = 34,
        ),
        Seed(
            id = "berry_greek_yogurt_bowl", name = "Berry Greek Yogurt Bowl", category = Category.BREAKFAST,
            kosher = KosherStatus.DAIRY,
            summary = "Thick nonfat Greek yogurt with strawberries, blueberries, chia and cinnamon.",
            prepMinutes = 5, cookMinutes = 0,
            ingredients = listOf(
                Ing("Plain nonfat Greek yogurt", 200.0, "g", 280.0, 118.0, 20.6, 7.2, 0.8),
                Ing("Strawberries, sliced", 0.5, "cup", 0.75, 27.0, 0.6, 6.4, 0.3, "checked"),
                Ing("Blueberries", 0.5, "cup", 0.75, 42.0, 0.5, 10.7, 0.2),
                Ing("Chia seeds", 10.0, "g", 15.0, 49.0, 1.7, 4.2, 3.1),
                Ing("Ground cinnamon", 0.25, "tsp", 0.25, 2.0, 0.0, 0.5, 0.0),
                Ing("Zero-calorie sweetener", 1.0, "packet", 1.0, 0.0, 0.0, 0.0, 0.0, optional = true),
            ),
            prep = listOf(
                "Rinse the berries. Hull and slice the strawberries; check them according to your practice.",
                "Spoon the yogurt into a bowl and smooth the top.",
                "Arrange the strawberries and blueberries over the yogurt.",
                "Scatter the chia seeds and dust with cinnamon. Sweeten if you like.",
            ),
            cook = listOf("No cooking required. For a thicker texture, let it sit 5 minutes so the chia hydrates."),
            kosherNotes = listOf(DAIRY_NOTE, DAIRY_CERT, "Strawberries require checking for insects."),
            tips = listOf("Frozen berries work — thaw them in the fridge overnight for a quick morning bowl."),
            standardLabel = "200 g yogurt", largeLabel = "280 g yogurt",
            refKcal = 290, refProtein = 30,
        ),
        Seed(
            id = "protein_overnight_oats", name = "Protein Overnight Oats", category = Category.BREAKFAST,
            kosher = KosherStatus.DAIRY,
            summary = "Rolled oats soaked overnight with Greek yogurt, protein powder and milk.",
            prepMinutes = 5, cookMinutes = 0,
            ingredients = listOf(
                Ing("Rolled oats", 40.0, "g", 60.0, 152.0, 5.3, 27.1, 2.6),
                Ing("Plain nonfat Greek yogurt", 150.0, "g", 200.0, 89.0, 15.5, 5.4, 0.6),
                Ing("Tnuva Qualify protein powder", 0.5, "scoop", 0.75, 60.0, 12.5, 2.0, 0.75, "½ scoop = 16.5 g"),
                milk(100.0, 150.0, "unsweetened"),
                Ing("Ground cinnamon", 0.25, "tsp", 0.25, 2.0, 0.0, 0.5, 0.0),
                Ing("Mixed berries", 0.5, "cup", 0.75, 36.0, 0.5, 8.6, 0.2, "add before eating"),
            ),
            prep = listOf(
                "In a jar or container, whisk the protein powder with the milk until there are no lumps.",
                "Stir in the yogurt, oats and cinnamon until evenly combined.",
                "Cover and refrigerate for at least 6 hours, or overnight.",
                "In the morning, stir (add a splash of milk if too thick) and top with berries.",
            ),
            cook = listOf("No cooking required. Can be warmed for 60–90 seconds in a dairy microwave dish if preferred."),
            kosherNotes = listOf(DAIRY_NOTE, TNUVA_NOTE, DAIRY_CERT),
            tips = listOf("Keeps 3 days refrigerated — make several jars at once."),
            standardLabel = "40 g oats", largeLabel = "60 g oats",
            refKcal = 350, refProtein = 32, defaultMilk = MilkOption.ONE_PERCENT,
        ),

        // ─────────────────────────────── LUNCH ───────────────────────────────
        Seed(
            id = "salmon_poke_bowl", name = "Salmon Poke Bowl", category = Category.LUNCH,
            kosher = KosherStatus.PAREVE, isFish = true,
            summary = "Cubed raw salmon over rice with cucumber, avocado, carrot and a tamari dressing.",
            prepMinutes = 20, cookMinutes = 20,
            ingredients = listOf(
                Ing("Raw salmon (kosher supplier, for raw use)", 150.0, "g", 200.0, 312.0, 30.6, 0.0, 20.1, "farmed Atlantic"),
                Ing("Cooked white rice, cooled", 100.0, "g", 150.0, 130.0, 2.7, 28.2, 0.3),
                Ing("Cucumber, diced", 0.5, "cup", 0.75, 8.0, 0.3, 1.9, 0.1),
                Ing("Avocado", 0.25, "whole", 0.25, 80.0, 1.0, 4.3, 7.3),
                Ing("Carrot, shredded", 0.25, "cup", 0.5, 11.0, 0.3, 2.7, 0.1),
                Ing("Scallions, sliced", 2.0, "tbsp", 2.0, 4.0, 0.2, 0.9, 0.0, "checked"),
                Ing("Tamari or soy sauce (certified)", 1.0, "tbsp", 1.0, 11.0, 1.9, 1.0, 0.0),
                Ing("Rice vinegar, unseasoned (certified)", 1.0, "tbsp", 1.0, 2.0, 0.0, 0.0, 0.0),
                Ing("Sesame seeds", 1.0, "tsp", 1.0, 17.0, 0.5, 0.7, 1.5),
                Ing("Sriracha (certified)", 1.0, "tsp", 1.0, 5.0, 0.1, 1.0, 0.1, optional = true),
            ),
            prep = listOf(
                "Keep the salmon refrigerated until the last moment. On a clean, dedicated board, remove any skin " +
                    "and pin bones, then cut into ¾-inch cubes with a sharp knife.",
                "Toss the salmon with the tamari and half the rice vinegar. Return it to the fridge while you prepare " +
                    "the rest (no longer than 30 minutes).",
                "Dice the cucumber, shred the carrot, slice the scallions and cut the avocado just before serving.",
            ),
            cook = listOf(
                "Rinse the rice until the water runs mostly clear. Cook according to the package (about 1 : 1.25 " +
                    "rice to water, covered, 15–18 minutes), then rest 10 minutes.",
                "Spread the hot rice on a tray to cool, then refrigerate within 1 hour. Season with the remaining " +
                    "rice vinegar.",
                "Assemble: rice first, then salmon, cucumber, avocado and carrot in sections.",
                "Finish with scallions, sesame seeds and sriracha if using. Eat immediately.",
            ),
            kosherNotes = listOf(RAW_FISH_SOURCE, SUSHI_GRADE, FISH_NOTE, CERTIFIED_PANTRY, PRODUCE_CHECK),
            safety = RAW_FISH_SAFETY,
            tips = listOf("Wild salmon is leaner — roughly 100 kcal less per 150 g than farmed Atlantic."),
            standardLabel = "150 g salmon · 100 g rice", largeLabel = "200 g salmon · 150 g rice",
            refKcal = 570, refProtein = 43,
        ),
        Seed(
            id = "tuna_poke_bowl", name = "Tuna Poke Bowl", category = Category.LUNCH,
            kosher = KosherStatus.PAREVE, isFish = true,
            summary = "Lean raw tuna with rice, cucumber, avocado, carrot and sesame.",
            prepMinutes = 20, cookMinutes = 20,
            ingredients = listOf(
                Ing("Raw tuna (kosher supplier, for raw use)", 150.0, "g", 200.0, 164.0, 36.6, 0.0, 0.8, "yellowfin"),
                Ing("Cooked white rice, cooled", 100.0, "g", 150.0, 130.0, 2.7, 28.2, 0.3),
                Ing("Cucumber, diced", 0.5, "medium", 0.75, 23.0, 1.0, 5.4, 0.2),
                Ing("Avocado", 0.25, "whole", 0.25, 80.0, 1.0, 4.3, 7.3),
                Ing("Carrot, shredded", 0.25, "cup", 0.5, 11.0, 0.3, 2.7, 0.1),
                Ing("Scallions, sliced", 2.0, "tbsp", 2.0, 4.0, 0.2, 0.9, 0.0, "checked"),
                Ing("Tamari or soy sauce (certified)", 1.0, "tbsp", 1.0, 11.0, 1.9, 1.0, 0.0),
                Ing("Rice vinegar, unseasoned (certified)", 1.0, "tbsp", 1.0, 2.0, 0.0, 0.0, 0.0),
                Ing("Sesame seeds", 1.0, "tsp", 1.0, 17.0, 0.5, 0.7, 1.5),
            ),
            prep = listOf(
                "Dice the cucumber, shred the carrot and slice the scallions. Halve and slice the avocado last.",
                "Keep the tuna on ice or in the fridge until needed. On a clean, dedicated board, trim any dark " +
                    "bloodline and sinew, then cut into ¾-inch cubes.",
                "Toss the tuna with the tamari and half the rice vinegar; keep cold.",
            ),
            cook = listOf(
                "Cook and rest the rice as for the salmon bowl, then cool it quickly on a tray and refrigerate " +
                    "within 1 hour.",
                "Season the rice with the remaining vinegar.",
                "Assemble the rice, tuna, cucumber, avocado and carrot. Top with scallions and sesame seeds.",
                "Serve immediately — don't let the assembled bowl sit out.",
            ),
            kosherNotes = listOf(RAW_FISH_SOURCE, SUSHI_GRADE, FISH_NOTE, CERTIFIED_PANTRY, PRODUCE_CHECK),
            safety = RAW_FISH_SAFETY,
            standardLabel = "150 g tuna · 100 g rice", largeLabel = "200 g tuna · 150 g rice",
            refKcal = 490, refProtein = 42,
        ),
        Seed(
            id = "chicken_shawarma_bowl", name = "Chicken Shawarma Bowl", category = Category.LUNCH,
            kosher = KosherStatus.MEAT,
            summary = "Spiced, lemony chicken strips over rice with crisp cucumber, tomato and red onion.",
            prepMinutes = 15, cookMinutes = 15,
            ingredients = listOf(
                Ing("Chicken breast, boneless skinless", 170.0, "g", 230.0, 281.0, 52.7, 0.0, 6.1, "cooked weight (≈225 g raw)"),
                Ing("Cooked white rice", 120.0, "g", 180.0, 156.0, 3.2, 33.8, 0.4),
                Ing("Cucumber, diced", 0.5, "cup", 0.75, 8.0, 0.3, 1.9, 0.1),
                Ing("Tomato, diced", 0.5, "cup", 0.75, 16.0, 0.8, 3.5, 0.2),
                Ing("Red onion, thinly sliced", 0.25, "cup", 0.25, 16.0, 0.4, 3.7, 0.0),
                Ing("Lemon juice", 1.0, "tbsp", 1.0, 3.0, 0.1, 1.0, 0.0),
                Ing("Garlic, minced", 2.0, "cloves", 2.0, 9.0, 0.4, 2.0, 0.0),
                Ing("Ground cumin", 1.0, "tsp", 1.0, 8.0, 0.4, 0.9, 0.5),
                Ing("Paprika", 1.0, "tsp", 1.0, 7.0, 0.3, 1.2, 0.3),
                Ing("Ground turmeric", 0.5, "tsp", 0.5, 5.0, 0.1, 1.0, 0.1),
                Ing("Ground coriander", 0.5, "tsp", 0.5, 3.0, 0.1, 0.5, 0.2),
                Ing("Olive oil", 1.0, "tsp", 1.0, 40.0, 0.0, 0.0, 4.5),
                toTaste("Salt and black pepper"),
            ),
            prep = listOf(
                "Slice the chicken across the grain into ½-inch strips.",
                "In a bowl, mix the lemon juice, garlic, olive oil, cumin, paprika, turmeric, coriander, ½ tsp salt " +
                    "and pepper. Toss the chicken to coat. Marinate 15 minutes (or up to 12 hours, refrigerated).",
                "Dice the cucumber and tomato and slice the onion.",
            ),
            cook = listOf(
                "Heat a heavy skillet or grill pan over medium-high heat until very hot.",
                "Lay the chicken in a single layer without crowding. Cook 3–4 minutes per side until browned and " +
                    "cooked through to 165 °F (74 °C).",
                "Rest the chicken for 2 minutes. Warm the rice.",
                "Serve the chicken over the rice with the vegetables alongside, and squeeze over extra lemon.",
            ),
            kosherNotes = listOf(MEAT_NOTE, MEAT_WAIT, CERTIFIED_PANTRY,
                "Use a pareve or meat sauce only — no yogurt or tahini-yogurt sauces. Plain tahini (certified pareve) is fine."),
            safety = listOf("Cook chicken to 165 °F (74 °C). Refrigerate marinating chicken; discard used marinade."),
            tips = listOf("Batch-cook the chicken for 3 days of lunches. Weigh it after cooking for accurate logging."),
            standardLabel = "170 g cooked chicken", largeLabel = "230 g cooked chicken",
            refKcal = 530, refProtein = 55,
        ),
        Seed(
            id = "mediterranean_beef_bowl", name = "Mediterranean Beef Bowl", category = Category.LUNCH,
            kosher = KosherStatus.MEAT,
            summary = "Cumin-browned lean beef over rice with cucumber, tomato, red onion, parsley and lemon.",
            prepMinutes = 10, cookMinutes = 12,
            ingredients = listOf(
                Ing("Lean kosher ground beef (90/10)", 150.0, "g", 200.0, 326.0, 39.2, 0.0, 17.6, "cooked weight"),
                Ing("Cooked white rice", 100.0, "g", 150.0, 130.0, 2.7, 28.2, 0.3),
                Ing("Cucumber, diced", 0.5, "cup", 0.75, 8.0, 0.3, 1.9, 0.1),
                Ing("Tomato, diced", 0.5, "cup", 0.75, 16.0, 0.8, 3.5, 0.2),
                Ing("Red onion, finely diced", 2.0, "tbsp", 2.0, 8.0, 0.2, 1.9, 0.0),
                Ing("Fresh parsley, chopped", 2.0, "tbsp", 2.0, 3.0, 0.2, 0.5, 0.1, "checked"),
                Ing("Lemon juice", 1.0, "tbsp", 1.0, 3.0, 0.1, 1.0, 0.0),
                Ing("Olive oil", 1.0, "tsp", 1.0, 40.0, 0.0, 0.0, 4.5),
                Ing("Ground cumin", 0.5, "tsp", 0.5, 4.0, 0.2, 0.5, 0.2),
                toTaste("Salt and black pepper"),
            ),
            prep = listOf(
                "Dice the cucumber, tomato and red onion; chop the parsley.",
                "Toss the vegetables with the lemon juice, olive oil and a pinch of salt to make a quick salad.",
            ),
            cook = listOf(
                "Heat a skillet over medium-high heat. Add the beef and break it up with a spatula.",
                "Cook 6–8 minutes, stirring occasionally, until well browned with no pink remaining " +
                    "(160 °F / 71 °C). Drain off excess fat.",
                "Season with cumin, salt and pepper and cook 1 minute more.",
                "Spoon the beef over warm rice, add the salad, and finish with parsley and a squeeze of lemon.",
            ),
            kosherNotes = listOf(MEAT_NOTE, MEAT_WAIT, PRODUCE_CHECK),
            safety = listOf("Ground beef must reach 160 °F (71 °C)."),
            tips = listOf("Kosher ground beef is often 80–85% lean; 90/10 or leaner keeps fat in check. " +
                "Draining the pan matters."),
            standardLabel = "150 g cooked beef", largeLabel = "200 g cooked beef",
            refKcal = 520, refProtein = 43,
        ),
        Seed(
            id = "chicken_fajita_bowl", name = "Chicken Fajita Bowl", category = Category.LUNCH,
            kosher = KosherStatus.MEAT,
            summary = "Smoky chicken with charred peppers and onions, black beans, rice and lime.",
            prepMinutes = 10, cookMinutes = 15,
            ingredients = listOf(
                Ing("Chicken breast", 170.0, "g", 230.0, 281.0, 52.7, 0.0, 6.1, "cooked weight (≈225 g raw)"),
                Ing("Cooked white rice", 100.0, "g", 150.0, 130.0, 2.7, 28.2, 0.3),
                Ing("Bell pepper, sliced", 0.5, "whole", 1.0, 16.0, 0.5, 3.6, 0.2),
                Ing("Onion, sliced", 0.5, "whole", 0.5, 22.0, 0.6, 5.1, 0.1),
                Ing("Black beans, rinsed (certified)", 0.5, "cup", 0.5, 109.0, 7.2, 19.8, 0.4),
                Ing("Paprika", 1.0, "tsp", 1.0, 7.0, 0.3, 1.2, 0.3),
                Ing("Ground cumin", 0.5, "tsp", 0.5, 4.0, 0.2, 0.5, 0.2),
                Ing("Garlic powder", 0.5, "tsp", 0.5, 5.0, 0.2, 1.1, 0.0),
                Ing("Lime juice", 1.0, "tbsp", 1.0, 4.0, 0.1, 1.3, 0.0),
                Ing("Olive oil", 1.0, "tsp", 1.0, 40.0, 0.0, 0.0, 4.5),
                toTaste("Salt"),
            ),
            prep = listOf(
                "Slice the chicken into thin strips. Slice the pepper and onion into ¼-inch strips.",
                "Mix the paprika, cumin, garlic powder and ½ tsp salt. Toss the chicken with half the spice mix and " +
                    "half the oil; toss the vegetables with the rest.",
                "Rinse and drain the black beans.",
            ),
            cook = listOf(
                "Heat a large skillet over high heat. Cook the chicken in one layer, 3–4 minutes per side, until " +
                    "cooked through to 165 °F (74 °C). Transfer to a plate.",
                "In the same pan, cook the peppers and onions 5–6 minutes until softened and lightly charred.",
                "Warm the beans in the pan for 1 minute.",
                "Serve the chicken, vegetables and beans over rice. Finish with lime juice.",
            ),
            kosherNotes = listOf(MEAT_NOTE, MEAT_WAIT, CERTIFIED_PANTRY,
                "No cheese or sour cream — use salsa or a pareve guacamole instead."),
            safety = listOf("Cook chicken to 165 °F (74 °C)."),
            standardLabel = "170 g cooked chicken", largeLabel = "230 g cooked chicken",
            refKcal = 490, refProtein = 50,
        ),

        // ─────────────────────────────── SUPPER ───────────────────────────────
        Seed(
            id = "lemon_garlic_salmon", name = "Lemon Garlic Salmon", category = Category.SUPPER,
            kosher = KosherStatus.PAREVE, isFish = true,
            summary = "Roasted salmon with lemon, garlic and dill, crisp potatoes and broccoli.",
            prepMinutes = 10, cookMinutes = 30,
            ingredients = listOf(
                Ing("Salmon fillet (kosher)", 180.0, "g", 230.0, 374.0, 36.7, 0.0, 24.1, "raw weight, farmed"),
                Ing("Broccoli florets", 200.0, "g", 250.0, 68.0, 5.6, 13.3, 0.7, "checked or certified frozen"),
                Ing("Potatoes, cubed", 150.0, "g", 225.0, 116.0, 3.1, 26.2, 0.2, "raw weight"),
                Ing("Lemon juice + zest", 1.0, "tbsp", 1.0, 3.0, 0.1, 1.0, 0.0),
                Ing("Garlic, minced", 2.0, "cloves", 2.0, 9.0, 0.4, 2.0, 0.0),
                Ing("Fresh dill, chopped", 1.0, "tbsp", 1.0, 1.0, 0.1, 0.2, 0.0, "checked"),
                Ing("Olive oil", 1.0, "tsp", 1.0, 40.0, 0.0, 0.0, 4.5),
                toTaste("Salt and black pepper"),
            ),
            prep = listOf(
                "Heat the oven to 425 °F (220 °C). Line a sheet pan with parchment.",
                "Cut the potatoes into ¾-inch cubes. Cut the broccoli into even florets and check as required.",
                "Mix the lemon juice, zest, garlic, dill, a pinch of salt and pepper.",
            ),
            cook = listOf(
                "Toss the potatoes with half the oil and a pinch of salt. Roast 15 minutes.",
                "Add the broccoli to the pan, tossed with the remaining oil. Push the vegetables to the sides.",
                "Place the salmon in the centre, skin-side down, and spoon the lemon-garlic mixture over it.",
                "Roast 10–14 minutes until the salmon flakes easily and reaches 145 °F (63 °C) at the thickest point.",
                "Rest 2 minutes and serve with extra lemon.",
            ),
            kosherNotes = listOf(
                "Buy salmon from a reliable kosher source. Skinless fillets need certification or supervised cutting.",
                FISH_NOTE,
                "Broccoli is commonly infested; many authorities recommend certified pre-checked frozen florets.",
            ),
            safety = listOf("Cook fish to 145 °F (63 °C) or until opaque and flaking."),
            tips = listOf("Wild salmon is leaner — about 120 kcal less per 180 g fillet than farmed Atlantic."),
            standardLabel = "180 g salmon", largeLabel = "230 g salmon",
            refKcal = 460, refProtein = 42,
        ),
        Seed(
            id = "garlic_london_broil", name = "Garlic London Broil", category = Category.SUPPER,
            kosher = KosherStatus.MEAT,
            summary = "Garlic-rosemary London broil, sliced thin against the grain, with green beans and potatoes.",
            prepMinutes = 10, cookMinutes = 30,
            ingredients = listOf(
                Ing("Kosher London broil (shoulder cut)", 180.0, "g", 240.0, 243.0, 39.6, 0.0, 9.0, "raw weight, trimmed"),
                Ing("Green beans, trimmed", 200.0, "g", 250.0, 62.0, 3.6, 14.3, 0.4),
                Ing("Potatoes, cubed", 150.0, "g", 225.0, 116.0, 3.1, 26.2, 0.2, "raw weight"),
                Ing("Garlic, minced", 2.0, "cloves", 2.0, 9.0, 0.4, 2.0, 0.0),
                Ing("Dried rosemary", 1.0, "tsp", 1.0, 4.0, 0.1, 0.8, 0.2),
                Ing("Black pepper", 0.5, "tsp", 0.5, 3.0, 0.1, 0.7, 0.0),
                Ing("Olive oil", 1.0, "tsp", 1.0, 40.0, 0.0, 0.0, 4.5),
                toTaste("Salt"),
            ),
            prep = listOf(
                "Pat the beef dry. Rub with garlic, rosemary, pepper and a light pinch of salt (kosher meat is " +
                    "already salted). Rest 20 minutes at room temperature, or marinate up to 24 hours refrigerated.",
                "Heat the oven to 425 °F (220 °C). Cube the potatoes and toss with half the oil.",
            ),
            cook = listOf(
                "Roast the potatoes 25–30 minutes, turning once, until golden.",
                "Heat the broiler (or a grill) to high. Broil the beef 4–6 inches from the heat, 5–7 minutes per " +
                    "side, to your preferred doneness — 145 °F (63 °C) for medium-rare, followed by a 3-minute rest.",
                "Meanwhile, steam the green beans 4–5 minutes and toss with the remaining oil and a pinch of salt.",
                "Rest the beef 5–10 minutes, then slice very thinly against the grain.",
            ),
            kosherNotes = listOf(
                MEAT_NOTE, MEAT_WAIT,
                "Kosher \"London broil\" is usually a shoulder cut. Hindquarter cuts such as true top round are " +
                    "rarely sold kosher outside Israel, because the hindquarter requires removing the sciatic nerve " +
                    "and forbidden fats. Ask your kosher butcher for a lean shoulder London broil.",
            ),
            safety = listOf("Whole cuts of beef: 145 °F (63 °C) plus a 3-minute rest."),
            standardLabel = "180 g raw beef", largeLabel = "240 g raw beef",
            refKcal = 480, refProtein = 52,
        ),
        Seed(
            id = "lean_beef_burger_bowl", name = "Lean Beef Burger Bowl", category = Category.SUPPER,
            kosher = KosherStatus.MEAT,
            summary = "A juicy lean burger over crisp lettuce with tomato, pickles, onion and roasted potatoes.",
            prepMinutes = 10, cookMinutes = 30,
            ingredients = listOf(
                Ing("Lean kosher ground beef (90/10)", 170.0, "g", 227.0, 299.0, 34.0, 0.0, 17.0, "raw weight"),
                Ing("Romaine lettuce, shredded", 2.0, "cups", 2.0, 16.0, 1.2, 3.1, 0.3, "checked"),
                Ing("Tomato, sliced", 0.5, "medium", 0.5, 11.0, 0.5, 2.3, 0.1),
                Ing("Dill pickles (certified)", 2.0, "spears", 2.0, 8.0, 0.3, 1.7, 0.1),
                Ing("Red onion, sliced", 2.0, "tbsp", 2.0, 8.0, 0.2, 1.9, 0.0),
                Ing("Potatoes, wedges", 150.0, "g", 225.0, 116.0, 3.1, 26.2, 0.2, "raw weight"),
                Ing("Yellow mustard (certified)", 1.0, "tbsp", 1.0, 9.0, 0.6, 0.9, 0.5),
                Ing("Ketchup (certified)", 1.0, "tbsp", 1.0, 17.0, 0.2, 4.6, 0.0),
                toTaste("Salt and black pepper"),
            ),
            prep = listOf(
                "Heat the oven to 425 °F (220 °C). Cut the potatoes into wedges, season with salt and pepper and " +
                    "spread on a parchment-lined tray (a light mist of cooking spray helps them crisp).",
                "Form the beef into one patty about ¾ inch thick, pressing a slight dimple in the centre. Season " +
                    "with pepper.",
                "Mix the mustard and ketchup for a simple burger sauce.",
            ),
            cook = listOf(
                "Roast the potatoes 25–30 minutes, turning once.",
                "Heat a skillet or grill pan over medium-high heat. Cook the patty 4–5 minutes per side until it " +
                    "reaches 160 °F (71 °C) inside.",
                "Rest the burger 2 minutes, then slice it.",
                "Build the bowl: lettuce, tomato, pickles and onion, then the burger and potatoes. Drizzle with sauce.",
            ),
            kosherNotes = listOf(MEAT_NOTE, MEAT_WAIT, CERTIFIED_PANTRY,
                "Check that any burger bun or sauce you add is pareve, not dairy."),
            safety = listOf("Ground beef must reach 160 °F (71 °C)."),
            standardLabel = "170 g raw beef", largeLabel = "227 g (½ lb) raw beef",
            refKcal = 510, refProtein = 43,
        ),
        Seed(
            id = "garlic_beef_meatballs", name = "Garlic Beef Meatballs", category = Category.SUPPER,
            kosher = KosherStatus.MEAT,
            summary = "Baked garlic-parsley meatballs in tomato sauce with zucchini and rice.",
            prepMinutes = 15, cookMinutes = 25,
            ingredients = listOf(
                Ing("Lean kosher ground beef (90/10)", 170.0, "g", 230.0, 299.0, 34.0, 0.0, 17.0, "raw weight"),
                Ing("Egg white", 1.0, "large", 1.0, 17.0, 3.6, 0.2, 0.1),
                Ing("Garlic, minced", 2.0, "cloves", 2.0, 9.0, 0.4, 2.0, 0.0),
                Ing("Fresh parsley, chopped", 2.0, "tbsp", 2.0, 3.0, 0.2, 0.5, 0.1, "checked"),
                Ing("Paprika", 0.5, "tsp", 0.5, 3.0, 0.1, 0.6, 0.1),
                Ing("Tomato sauce, plain (certified)", 150.0, "g", 200.0, 36.0, 1.8, 8.0, 0.3, "no sugar added"),
                Ing("Zucchini, sliced", 150.0, "g", 200.0, 26.0, 1.8, 4.7, 0.5),
                Ing("Cooked white rice", 100.0, "g", 150.0, 130.0, 2.7, 28.2, 0.3),
                toTaste("Salt and black pepper"),
            ),
            prep = listOf(
                "Heat the oven to 400 °F (200 °C) and line a tray with parchment.",
                "In a bowl, mix the beef, egg white, garlic, parsley, paprika, ½ tsp salt and pepper with your " +
                    "hands until just combined — overmixing makes them dense.",
                "Roll into 8–10 even meatballs (about 1½ inches).",
            ),
            cook = listOf(
                "Bake the meatballs 15–18 minutes until browned and 160 °F (71 °C) inside.",
                "Meanwhile, sauté the zucchini in a nonstick pan with a pinch of salt for 4–5 minutes.",
                "Warm the tomato sauce in a small pot; add the baked meatballs and simmer 3–5 minutes.",
                "Serve the meatballs and sauce with the zucchini and rice.",
            ),
            kosherNotes = listOf(MEAT_NOTE, MEAT_WAIT, CERTIFIED_PANTRY,
                "Don't finish with parmesan — this is a meat dish."),
            safety = listOf("Ground beef must reach 160 °F (71 °C)."),
            standardLabel = "170 g raw beef", largeLabel = "230 g raw beef",
            refKcal = 490, refProtein = 46,
        ),
        Seed(
            id = "garlic_chicken_potatoes", name = "Garlic Chicken and Potatoes", category = Category.SUPPER,
            kosher = KosherStatus.MEAT,
            summary = "Paprika-garlic roast chicken with rosemary potatoes and steamed green beans.",
            prepMinutes = 10, cookMinutes = 35,
            ingredients = listOf(
                Ing("Chicken breast", 180.0, "g", 240.0, 297.0, 55.8, 0.0, 6.5, "cooked weight (≈240 g raw)"),
                Ing("Potatoes, cubed", 200.0, "g", 300.0, 154.0, 4.1, 35.0, 0.2, "raw weight"),
                Ing("Green beans, trimmed", 150.0, "g", 200.0, 47.0, 2.7, 10.7, 0.3),
                Ing("Garlic, minced", 3.0, "cloves", 3.0, 13.0, 0.6, 3.0, 0.0),
                Ing("Paprika", 1.0, "tsp", 1.0, 7.0, 0.3, 1.2, 0.3),
                Ing("Dried rosemary", 1.0, "tsp", 1.0, 4.0, 0.1, 0.8, 0.2),
                Ing("Lemon juice", 1.0, "tbsp", 1.0, 3.0, 0.1, 1.0, 0.0),
                Ing("Olive oil", 1.0, "tsp", 1.0, 40.0, 0.0, 0.0, 4.5),
                toTaste("Salt and black pepper"),
            ),
            prep = listOf(
                "Heat the oven to 425 °F (220 °C).",
                "Cube the potatoes and toss with half the oil, rosemary, a pinch of salt and pepper.",
                "Pound the chicken to an even ¾-inch thickness. Rub with garlic, paprika, lemon juice, the remaining " +
                    "oil, salt and pepper.",
            ),
            cook = listOf(
                "Roast the potatoes 15 minutes.",
                "Add the chicken to the tray (or pan-sear it 5–6 minutes per side in a hot skillet).",
                "Roast another 18–22 minutes, until the chicken reaches 165 °F (74 °C) and the potatoes are golden.",
                "Steam the green beans 4–5 minutes until bright and tender-crisp.",
                "Rest the chicken 3 minutes before slicing. Serve together.",
            ),
            kosherNotes = listOf(MEAT_NOTE, MEAT_WAIT, CERTIFIED_PANTRY),
            safety = listOf("Cook chicken to 165 °F (74 °C)."),
            standardLabel = "180 g cooked chicken", largeLabel = "240 g cooked chicken",
            refKcal = 440, refProtein = 55,
        ),

        // ─────────────────────────────── SNACKS ───────────────────────────────
        Seed(
            id = "chocolate_tnuva_creami", name = "Chocolate Tnuva Ninja Creami", category = Category.SNACKS,
            kosher = KosherStatus.DAIRY,
            summary = "Thick dark-chocolate protein ice cream made in the Ninja Creami.",
            prepMinutes = 5, cookMinutes = 0,
            ingredients = listOf(
                Ing("Tnuva Qualify Dark Chocolate Fudge protein", 1.0, "scoop", 1.5, 120.0, 25.0, 4.0, 1.5, "33 g scoop"),
                milk(200.0, 300.0),
                Ing("Vanilla extract", 0.5, "tsp", 0.5, 6.0, 0.0, 0.3, 0.0, optional = true),
                Ing("Zero-calorie sweetener", 1.0, "packet", 1.0, 0.0, 0.0, 0.0, 0.0, optional = true),
            ),
            prep = listOf(
                "Add the milk to a blender or shaker, then the protein powder (and vanilla/sweetener if using).",
                "Blend 20–30 seconds until completely smooth, with no dry clumps.",
                "Pour into the Ninja Creami pint, staying below the max-fill line. Let the foam settle.",
                "Lid and freeze flat for 24 hours (per the manufacturer's instructions).",
            ),
            cook = listOf(
                "Remove the pint and let it sit 5 minutes. Fit it to the outer bowl and paddle lid.",
                "Spin on LITE ICE CREAM.",
                "If it looks powdery, add 1–2 tbsp of milk and RE-SPIN once or twice until creamy.",
                "Log any extra milk you add during re-spins as a manual entry for accuracy.",
            ),
            kosherNotes = listOf(DAIRY_NOTE, TNUVA_NOTE, DAIRY_CERT),
            tips = listOf("Nutrition updates with the milk you choose below.",
                "Known powder nutrition per 33 g scoop: 120 kcal · 25 g protein · 1.5 g fat · 4 g carbs · 1 g sugar."),
            standardLabel = "1 scoop · 200 ml milk", largeLabel = "1½ scoops · 300 ml milk",
            defaultMilk = MilkOption.ONE_PERCENT,
        ),
        Seed(
            id = "coffee_protein_creami", name = "Coffee Protein Creami", category = Category.SNACKS,
            kosher = KosherStatus.DAIRY,
            summary = "Mocha protein ice cream — chocolate protein, milk and cooled coffee.",
            prepMinutes = 5, cookMinutes = 0,
            ingredients = listOf(
                Ing("Tnuva Qualify Dark Chocolate Fudge protein", 1.0, "scoop", 1.5, 120.0, 25.0, 4.0, 1.5, "33 g scoop"),
                milk(150.0, 225.0),
                Ing("Brewed coffee, cooled", 50.0, "ml", 75.0, 1.0, 0.1, 0.0, 0.0),
                Ing("Vanilla extract", 0.5, "tsp", 0.5, 6.0, 0.0, 0.3, 0.0, optional = true),
                Ing("Zero-calorie sweetener", 1.0, "packet", 1.0, 0.0, 0.0, 0.0, 0.0, optional = true),
            ),
            prep = listOf(
                "Brew the coffee and cool it completely.",
                "Blend the milk, coffee and protein powder (plus vanilla/sweetener if using) until smooth.",
                "Pour into the Creami pint below the max-fill line. Freeze flat for 24 hours.",
            ),
            cook = listOf(
                "Rest the pint 5 minutes, then spin on LITE ICE CREAM.",
                "Add 1–2 tbsp milk and RE-SPIN if needed until smooth and scoopable.",
            ),
            kosherNotes = listOf(DAIRY_NOTE, TNUVA_NOTE,
                "Plain brewed coffee generally doesn't need certification; flavoured coffees do."),
            tips = listOf("Nutrition updates with the milk you choose below."),
            standardLabel = "1 scoop · 150 ml milk", largeLabel = "1½ scoops · 225 ml milk",
            defaultMilk = MilkOption.ONE_PERCENT,
        ),
        Seed(
            id = "greek_yogurt_snack", name = "Greek Yogurt Snack", category = Category.SNACKS,
            kosher = KosherStatus.DAIRY,
            summary = "Nonfat Greek yogurt with sliced strawberries and cinnamon.",
            prepMinutes = 3, cookMinutes = 0,
            ingredients = listOf(
                Ing("Plain nonfat Greek yogurt", 170.0, "g", 250.0, 100.0, 17.5, 6.1, 0.7),
                Ing("Strawberries, sliced", 0.5, "cup", 0.75, 27.0, 0.6, 6.4, 0.3, "checked"),
                Ing("Ground cinnamon", 0.25, "tsp", 0.25, 2.0, 0.0, 0.5, 0.0),
                Ing("Zero-calorie sweetener", 1.0, "packet", 1.0, 0.0, 0.0, 0.0, 0.0, optional = true),
            ),
            prep = listOf(
                "Spoon the yogurt into a bowl.",
                "Top with sliced strawberries, a dusting of cinnamon and sweetener if you like.",
            ),
            cook = listOf("No cooking required."),
            kosherNotes = listOf(DAIRY_NOTE, DAIRY_CERT, "Strawberries require checking for insects."),
            standardLabel = "170 g yogurt", largeLabel = "250 g yogurt",
            refKcal = 160, refProtein = 20,
        ),
        Seed(
            id = "fruit_and_protein", name = "Fruit and Protein", category = Category.SNACKS,
            kosher = KosherStatus.DEPENDS,
            summary = "A crisp apple with a protein shake — the fastest high-protein snack.",
            prepMinutes = 2, cookMinutes = 0,
            ingredients = listOf(
                Ing("Apple", 1.0, "medium", 1.0, 95.0, 0.5, 25.1, 0.3),
                Ing("Protein powder", 1.0, "scoop", 1.5, 120.0, 25.0, 4.0, 1.5, "values for Tnuva Qualify"),
                Ing("Cold water", 300.0, "ml", 400.0, 0.0, 0.0, 0.0, 0.0),
            ),
            prep = listOf(
                "Add the water to a shaker bottle, then the protein powder. Shake 15–20 seconds.",
                "Wash and slice the apple.",
            ),
            cook = listOf("No cooking required."),
            kosherNotes = listOf(
                "Kosher status depends on the protein product: whey-based powders such as Tnuva Qualify are Dairy; " +
                    "a certified pareve plant protein makes this Pareve.",
                "If using a dairy powder, don't have it with or after a meat meal until your waiting time has passed.",
            ),
            tips = listOf("If you use a different powder, log a manual entry with its label values."),
            standardLabel = "1 apple · 1 scoop", largeLabel = "1 apple · 1½ scoops",
            refKcal = 190, refProtein = 25,
        ),
    )

    fun toEntities(s: Seed, order: Int): Pair<RecipeEntity, List<IngredientEntity>> {
        val r = RecipeEntity(
            id = s.id, name = s.name, category = s.category.name, kosherStatus = s.kosher.name, isFish = s.isFish,
            summary = s.summary, prepMinutes = s.prepMinutes, cookMinutes = s.cookMinutes,
            prepSteps = s.prep, cookSteps = s.cook, kosherNotes = s.kosherNotes, safetyNotes = s.safety,
            tips = s.tips + ESTIMATE, standardPortionLabel = s.standardLabel, largePortionLabel = s.largeLabel,
            referenceKcal = s.refKcal, referenceProteinG = s.refProtein,
            usesMilkChoice = s.ingredients.any { it.isMilk }, defaultMilk = s.defaultMilk?.name,
            artKey = s.id, builtIn = true, seedVersion = SEED_VERSION, sortOrder = order,
        )
        val ings = s.ingredients.mapIndexed { i, g ->
            IngredientEntity(
                recipeId = s.id, position = i, name = g.name, amount = g.amount, unit = g.unit, largeAmount = g.large,
                note = g.note, kcal = g.kcal, proteinG = g.p, carbsG = g.c, fatG = g.f, optional = g.optional,
                isMilk = g.isMilk,
            )
        }
        return r to ings
    }
}
