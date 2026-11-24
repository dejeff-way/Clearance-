package com.forma.app.util

import com.forma.app.data.prefs.LengthUnit
import com.forma.app.data.prefs.WeightUnit
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Fmt {
    private val nf = NumberFormat.getIntegerInstance(Locale.US)

    fun int(v: Number): String = nf.format(v.toDouble().roundToInt())
    fun kcal(v: Number): String = int(v)

    /** Signed with a real minus sign. */
    fun signed(v: Int): String = when {
        v > 0 -> "+${int(v)}"
        v < 0 -> "−${int(abs(v))}"
        else -> "0"
    }

    fun signedOne(v: Double): String = when {
        v > 0.05 -> "+" + one(v)
        v < -0.05 -> "−" + one(abs(v))
        else -> "0.0"
    }

    fun grams(v: Double?): String = if (v == null) "—" else "${v.roundToInt()}"

    fun one(v: Double): String = String.format(Locale.US, "%.1f", v)

    fun weight(lb: Double, unit: WeightUnit): String =
        if (unit == WeightUnit.KG) one(lb * 0.45359237) else one(lb)

    fun weightUnit(unit: WeightUnit) = if (unit == WeightUnit.KG) "kg" else "lb"

    /** Converts a value typed in the user's unit back to pounds. */
    fun toLb(v: Double, unit: WeightUnit) = if (unit == WeightUnit.KG) v / 0.45359237 else v
    fun fromLb(lb: Double, unit: WeightUnit) = if (unit == WeightUnit.KG) lb * 0.45359237 else lb

    fun length(inches: Double, unit: LengthUnit): String = if (unit == LengthUnit.CM) one(inches * 2.54) else one(inches)
    fun lengthUnit(unit: LengthUnit) = if (unit == LengthUnit.CM) "cm" else "in"
    fun toIn(v: Double, unit: LengthUnit) = if (unit == LengthUnit.CM) v / 2.54 else v
    fun fromIn(i: Double, unit: LengthUnit) = if (unit == LengthUnit.CM) i * 2.54 else i

    fun heightFtIn(inches: Double): String {
        val t = inches.roundToInt(); return "${t / 12}′ ${t % 12}″"
    }

    private val longDate = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)
    private val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val medDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    private val fullDate = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun longDate(d: LocalDate) = d.format(longDate)
    fun shortDate(d: LocalDate) = d.format(shortDate)
    fun medDate(d: LocalDate) = d.format(medDate)
    fun fullDate(d: LocalDate) = d.format(fullDate)
    fun dow(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
    fun dowLetter(d: java.time.DayOfWeek) = d.getDisplayName(TextStyle.NARROW, Locale.US)
    fun time(t: java.time.ZonedDateTime) = t.format(time)

    fun relativeDay(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> medDate(d)
    }

    /** ½, ¼ etc. for kitchen units. */
    fun amount(v: Double): String {
        if (v == 0.0) return ""
        val whole = v.toInt()
        val frac = v - whole
        val fs = when {
            abs(frac) < 0.01 -> ""
            abs(frac - 0.25) < 0.01 -> "¼"
            abs(frac - 0.33) < 0.02 -> "⅓"
            abs(frac - 0.5) < 0.01 -> "½"
            abs(frac - 0.67) < 0.02 -> "⅔"
            abs(frac - 0.75) < 0.01 -> "¾"
            else -> return if (v >= 10) v.roundToInt().toString() else one(v).trimEnd('0').trimEnd('.')
        }
        return if (whole == 0) fs else "$whole$fs"
    }

    fun ago(millis: Long, now: Long): String {
        val m = (now - millis) / 60_000
        return when {
            m < 1 -> "just now"
            m < 60 -> "$m min ago"
            m < 24 * 60 -> "${m / 60} h ago"
            else -> "${m / (24 * 60)} d ago"
        }
    }
}
