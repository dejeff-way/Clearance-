package com.forma.engine

import kotlin.math.roundToInt

enum class MacroStatus { TO_GO, ON_TARGET, OVER }

data class MacroTarget(val targetG: Int, val consumedG: Double, val share: String) {
    val remainingG: Int get() = (targetG - consumedG).roundToInt()
    /** Within ±10% counts as on target; more than 10% over is "over". */
    val status: MacroStatus get() = when {
        consumedG > targetG * 1.10 -> MacroStatus.OVER
        consumedG >= targetG * 0.90 -> MacroStatus.ON_TARGET
        else -> MacroStatus.TO_GO
    }
    val fraction: Float get() = if (targetG <= 0) 0f else (consumedG / targetG).toFloat()
}

data class MacroTargets(val protein: MacroTarget, val carbs: MacroTarget, val fat: MacroTarget, val notes: List<String>)

/**
 * Daily macro targets derived from the day's calorie budget:
 *  - protein: the user's fixed target,
 *  - fat: a share of the budget (default 30%), never below [MIN_FAT_G],
 *  - carbs: the calories left after protein and fat.
 * Either fat or carbs can be fixed in grams instead.
 */
object MacroCalculator {
    const val MIN_FAT_G = 50
    const val MIN_CARBS_G = 50

    fun targets(
        budgetKcal: Int, proteinG: Int, fatPct: Int, fixedFatG: Int?, fixedCarbG: Int?,
        consumed: Macros,
    ): MacroTargets {
        val notes = mutableListOf<String>()
        val fat = fixedFatG ?: (budgetKcal * fatPct / 100.0 / 9.0).roundToInt().let {
            if (it < MIN_FAT_G) { notes += "Fat target held at $MIN_FAT_G g — very low fat isn't recommended."; MIN_FAT_G } else it
        }
        val carbs = fixedCarbG ?: ((budgetKcal - proteinG * 4 - fat * 9) / 4.0).roundToInt().let {
            if (it < MIN_CARBS_G) { notes += "Your budget leaves little room for carbs after protein and fat."; it.coerceAtLeast(0) } else it
        }
        val total = proteinG * 4 + carbs * 4 + fat * 9
        fun pct(kcal: Int) = if (total <= 0) "" else "${(kcal * 100.0 / total).roundToInt()}% of calories"
        return MacroTargets(
            MacroTarget(proteinG, consumed.proteinG, pct(proteinG * 4)),
            MacroTarget(carbs, consumed.carbsG ?: 0.0, if (fixedCarbG != null) "fixed" else "rest of budget · ${pct(carbs * 4)}"),
            MacroTarget(fat, consumed.fatG ?: 0.0, if (fixedFatG != null) "fixed" else "$fatPct% of budget"),
            notes,
        )
    }
}
