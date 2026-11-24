package com.forma.app.data.seed

import com.forma.engine.Macros

/** Milk choices for recipes whose nutrition depends on the milk used. Values per 100 ml, typical US labels. */
enum class MilkOption(val label: String, val per100ml: Macros, val isDairy: Boolean) {
    SKIM("Skim (fat-free) milk", Macros(34.0, 3.4, 5.0, 0.1), true),
    ONE_PERCENT("1% milk", Macros(42.0, 3.4, 5.0, 1.0), true),
    TWO_PERCENT("2% milk", Macros(50.0, 3.3, 4.8, 2.0), true),
    WHOLE("Whole milk", Macros(61.0, 3.2, 4.8, 3.3), true),
    FAIRLIFE_SKIM("Ultra-filtered skim milk", Macros(34.0, 5.4, 2.5, 0.0), true),
    ALMOND("Unsweetened almond milk", Macros(13.0, 0.4, 0.3, 1.1), false),
    SOY("Unsweetened soy milk", Macros(33.0, 2.9, 1.3, 1.8), false),
    OAT("Oat milk (unsweetened)", Macros(50.0, 0.4, 7.0, 2.1), false),
    ;

    fun forMl(ml: Double): Macros = per100ml * (ml / 100.0)

    companion object {
        fun from(key: String?): MilkOption? = entries.firstOrNull { it.name == key }
    }
}
