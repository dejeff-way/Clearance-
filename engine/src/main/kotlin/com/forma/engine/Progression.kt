package com.forma.engine

data class SetResult(val weightLb: Double?, val reps: Int?)

enum class ProgressionAction { INCREASE, REPEAT, CONSOLIDATE, NO_DATA }

data class ProgressionAdvice(val action: ProgressionAction, val suggestedWeightLb: Double?, val message: String)

/**
 * Double progression: work within a rep range; when every set reaches the top of the range, add a small load.
 */
object Progression {
    fun advise(repRange: IntRange, lastSession: List<SetResult>, incrementLb: Double): ProgressionAdvice {
        val sets = lastSession.filter { it.reps != null && it.reps > 0 }
        if (sets.isEmpty()) return ProgressionAdvice(ProgressionAction.NO_DATA, null,
            "Pick a weight you could lift for 2–3 more reps than the target.")
        val weight = sets.mapNotNull { it.weightLb }.maxOrNull()
        val allTop = sets.all { it.reps!! >= repRange.last }
        val anyBelow = sets.any { it.reps!! < repRange.first }
        return when {
            allTop && weight != null -> ProgressionAdvice(ProgressionAction.INCREASE, weight + incrementLb,
                "All sets hit ${repRange.last} reps last time. Try ${fmt(weight + incrementLb)} lb.")
            allTop -> ProgressionAdvice(ProgressionAction.INCREASE, null,
                "All sets hit ${repRange.last} reps last time — make it slightly harder.")
            anyBelow -> ProgressionAdvice(ProgressionAction.CONSOLIDATE, weight,
                "Some sets fell below ${repRange.first} reps. Stay${weight?.let { " at ${fmt(it)} lb" } ?: ""} " +
                    "until every set reaches ${repRange.first}.")
            else -> ProgressionAdvice(ProgressionAction.REPEAT, weight,
                "Repeat${weight?.let { " ${fmt(it)} lb" } ?: ""} and aim for one more rep per set.")
        }
    }

    /** Linear progression: add load every session in which all prescribed sets reached the target reps. */
    fun adviseLinear(repRange: IntRange, lastSession: List<SetResult>, incrementLb: Double, prescribedSets: Int): ProgressionAdvice {
        val sets = lastSession.filter { it.reps != null && it.reps > 0 }
        if (sets.isEmpty()) return ProgressionAdvice(ProgressionAction.NO_DATA, null, "Start light and add ${fmt(incrementLb)} lb each successful session.")
        val weight = sets.mapNotNull { it.weightLb }.maxOrNull()
        val success = sets.size >= prescribedSets && sets.all { it.reps!! >= repRange.first }
        return when {
            success && weight != null -> ProgressionAdvice(ProgressionAction.INCREASE, weight + incrementLb,
                "Every set hit ${repRange.first}+ last time. Add ${fmt(incrementLb)} lb → ${fmt(weight + incrementLb)} lb.")
            success -> ProgressionAdvice(ProgressionAction.INCREASE, null, "Every set hit the target — make it a little harder.")
            else -> ProgressionAdvice(ProgressionAction.REPEAT, weight,
                "Not every set reached ${repRange.first} reps. Repeat${weight?.let { " ${fmt(it)} lb" } ?: ""} before adding weight.")
        }
    }

    private fun fmt(w: Double) = if (w % 1.0 == 0.0) w.toInt().toString() else "%.1f".format(w)
}
