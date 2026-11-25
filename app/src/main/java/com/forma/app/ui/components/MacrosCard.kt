package com.forma.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import com.forma.engine.MacroStatus
import com.forma.engine.MacroTarget
import com.forma.engine.MacroTargets
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Protein, carbs and fat against the day's targets. [missingInfo] = entries logged without carb/fat values, so the
 * totals may be incomplete.
 */
@Composable
fun MacrosCard(t: MacroTargets, missingInfo: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = Forma.colors
    FormaCard(modifier.testTag("macros_card"), onClick = onClick) {
        Overline("Macros")
        listOf(Triple("Protein", t.protein, "protein"), Triple("Carbs", t.carbs, "carbs"), Triple("Fat", t.fat, "fat")).forEach { (label, m, key) ->
            Spacer(Modifier.height(Space.m))
            MacroRow(label, m, key)
        }
        if (missingInfo > 0) {
            Spacer(Modifier.height(Space.m))
            Text("$missingInfo item${if (missingInfo == 1) "" else "s"} today ${if (missingInfo == 1) "has" else "have"} no carb or fat info, so those totals may be low.",
                style = Forma.type.caption, color = c.warn)
        }
        t.notes.forEach { Text(it, style = Forma.type.caption, color = c.textTertiary, modifier = Modifier.padding(top = Space.xs)) }
    }
}

@Composable
private fun MacroRow(label: String, m: MacroTarget, key: String) {
    val c = Forma.colors
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Forma.type.caption, color = c.textSecondary)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${m.consumedG.roundToInt()}", style = Forma.type.number, color = c.text, modifier = Modifier.testTag("macro_${key}_value"))
                Text(" / ${m.targetG} g", style = Forma.type.caption, color = c.textTertiary, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                when (m.status) {
                    MacroStatus.TO_GO -> "${m.remainingG} g to go"
                    MacroStatus.ON_TARGET -> "On target"
                    MacroStatus.OVER -> "${abs(m.remainingG)} g over"
                },
                style = Forma.type.label,
                color = when (m.status) { MacroStatus.TO_GO -> c.textSecondary; MacroStatus.ON_TARGET -> c.accent; MacroStatus.OVER -> c.warn },
                modifier = Modifier.testTag("macro_${key}_status"),
            )
            Text(m.share, style = Forma.type.caption, color = c.textTertiary)
        }
    }
    Spacer(Modifier.height(6.dp))
    ThinProgress(m.fraction.coerceAtMost(1f), color = if (m.status == MacroStatus.OVER) c.warn else c.accent, height = 4.dp, modifier = Modifier.fillMaxWidth())
}
