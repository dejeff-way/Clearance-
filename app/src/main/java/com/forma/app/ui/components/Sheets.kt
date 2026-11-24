package com.forma.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.forma.app.data.prefs.WeightUnit
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import com.forma.app.util.Fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormaSheet(onDismiss: () -> Unit, title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Forma.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        scrimColor = Color.Black.copy(alpha = 0.32f),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = Space.gutter).padding(bottom = Space.xl),
        ) {
            Text(title, style = Forma.type.titleSmall, color = c.text)
            if (subtitle != null) {
                Spacer(Modifier.height(Space.xs))
                Text(subtitle, style = Forma.type.bodySmall, color = c.textSecondary)
            }
            Spacer(Modifier.height(Space.l))
            content()
        }
    }
}

/** Shared weight entry sheet (Home and Progress). */
@Composable
fun WeightSheet(unit: WeightUnit, currentLb: Double?, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(currentLb?.let { Fmt.one(Fmt.fromLb(it, unit)) } ?: "") }
    val v = text.toDoubleOrNull()
    FormaSheet(onDismiss, "Log weight", "Daily weigh-ins are smoothed into a 7-day trend — one reading never decides anything.") {
        FormaField(text, { text = it }, "Weight", suffix = Fmt.weightUnit(unit), numeric = true, modifier = Modifier.testTag("weight_input"))
        Spacer(Modifier.height(Space.l))
        PrimaryButton("Save", { v?.let { onSave(Fmt.toLb(it, unit)) } }, Modifier.fillMaxWidth().testTag("weight_save"), enabled = v != null && v > 0)
    }
}

@Composable
fun ConfirmDialog(
    title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false,
) {
    val c = Forma.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, style = Forma.type.heading, color = c.text) },
        text = { Text(body, style = Forma.type.bodySmall, color = c.textSecondary) },
        confirmButton = { TextAction(confirm, onConfirm, color = if (destructive) c.negative else c.accent) },
        dismissButton = { TextAction("Cancel", onDismiss, color = c.textSecondary) },
    )
}

@Composable
fun ButtonRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth()) { content() }
}

@Composable
fun HSpace() = Spacer(Modifier.width(Space.m))
