package com.forma.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forma.app.data.seed.KosherStatus
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Radius
import com.forma.app.ui.theme.Space

/** The base surface. Flat, hairline border, generous padding. */
@Composable
fun FormaCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(Space.l),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Forma.colors
    val shape = RoundedCornerShape(Radius.l)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = Forma.colors.textTertiary) {
    Text(text.uppercase(), style = Forma.type.overline, color = color, modifier = modifier)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Overline(title, Modifier.weight(1f), color = Forma.colors.textSecondary)
        if (action != null && onAction != null) {
            Text(action, style = Forma.type.label, color = Forma.colors.accent,
                modifier = Modifier.clip(RoundedCornerShape(Radius.s)).clickable(onClick = onAction).padding(Space.xs))
        }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = Space.l, bottom = Space.s), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) {
                Overline(subtitle)
                Spacer(Modifier.height(Space.xs))
            }
            Text(title, style = Forma.type.display, color = Forma.colors.text)
        }
        trailing()
    }
}

@Composable
fun BackBar(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
        Text(title, style = Forma.type.heading, color = Forma.colors.text, modifier = Modifier.weight(1f).padding(start = Space.xs),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = Forma.colors.text, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
}

enum class Tone { NEUTRAL, ACCENT, WARN, NEGATIVE }

@Composable
fun toneColors(t: Tone): Pair<Color, Color> {
    val c = Forma.colors
    return when (t) {
        Tone.NEUTRAL -> c.surfaceAlt to c.textSecondary
        Tone.ACCENT -> c.accentSoft to c.accent
        Tone.WARN -> c.warnSoft to c.warn
        Tone.NEGATIVE -> c.negativeSoft to c.negative
    }
}

@Composable
fun Tag(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier) {
    val (bg, fg) = toneColors(tone)
    Text(
        text, style = Forma.type.caption, color = fg, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** Kosher designation chip: outlined with a coloured dot so it reads clearly without shouting. */
@Composable
fun KosherTag(status: KosherStatus, isFish: Boolean = false, modifier: Modifier = Modifier) {
    val c = Forma.colors
    val color = when (status) {
        KosherStatus.MEAT -> c.meat
        KosherStatus.DAIRY -> c.dairy
        KosherStatus.PAREVE -> c.pareve
        KosherStatus.DEPENDS -> c.textSecondary
    }
    val label = if (isFish) "Pareve · Fish" else status.label
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, c.line, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = Forma.type.caption, color = c.textSecondary, maxLines = 1)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val c = Forma.colors
    val bg by animateColorAsState(if (enabled) c.accent else c.surfaceAlt, label = "btn")
    Row(
        modifier.heightIn(min = 50.dp).clip(RoundedCornerShape(Radius.m)).background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = Space.xl),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (enabled) c.onAccent else c.textTertiary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(Space.s)) }
        Text(text, style = Forma.type.bodyStrong, color = if (enabled) c.onAccent else c.textTertiary)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, color: Color = Forma.colors.text) {
    val c = Forma.colors
    Row(
        modifier.heightIn(min = 50.dp).clip(RoundedCornerShape(Radius.m)).border(1.dp, c.lineStrong, RoundedCornerShape(Radius.m))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = Space.l),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (enabled) color else c.textTertiary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(Space.s)) }
        Text(text, style = Forma.type.bodyStrong, color = if (enabled) color else c.textTertiary)
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, color: Color = Forma.colors.accent, modifier: Modifier = Modifier) {
    Text(text, style = Forma.type.label, color = color,
        modifier = modifier.clip(RoundedCornerShape(Radius.s)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Space.s, vertical = Space.s))
}

/** Compact segmented control. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Forma.colors
    Row(
        modifier.clip(RoundedCornerShape(Radius.m)).background(c.surfaceAlt).padding(3.dp),
    ) {
        options.forEach { o ->
            val sel = o == selected
            val bg by animateColorAsState(if (sel) c.surface else Color.Transparent, tween(180), label = "seg")
            Box(
                Modifier.weight(1f).heightIn(min = 36.dp).clip(RoundedCornerShape(Radius.s)).background(bg)
                    .then(if (sel) Modifier.border(1.dp, c.line, RoundedCornerShape(Radius.s)) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(o) }.padding(horizontal = Space.s),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(o), style = Forma.type.label, color = if (sel) c.text else c.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
fun ThinProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = Forma.colors.accent, height: Dp = 4.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(500), label = "p")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Forma.colors.track)) {
        Box(Modifier.fillMaxWidth(f).height(height).clip(CircleShape).background(color))
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Forma.colors.line))
}

@Composable
fun StatBlock(label: String, value: String, unit: String? = null, modifier: Modifier = Modifier, valueColor: Color = Forma.colors.text, style: TextStyle = Forma.type.number) {
    Column(modifier) {
        Text(label, style = Forma.type.caption, color = Forma.colors.textSecondary, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = style, color = valueColor, maxLines = 1)
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(unit, style = Forma.type.caption, color = Forma.colors.textTertiary, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}

/** A key/value row for breakdowns. */
@Composable
fun ValueRow(label: String, value: String, detail: String? = null, valueColor: Color = Forma.colors.text, muted: Boolean = false, strike: Boolean = false) {
    val c = Forma.colors
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(end = Space.m)) {
            Text(label, style = Forma.type.body, color = if (muted) c.textTertiary else c.text)
            if (detail != null) Text(detail, style = Forma.type.caption, color = c.textTertiary)
        }
        Text(value, style = Forma.type.number.copy(textDecoration = if (strike) androidx.compose.ui.text.style.TextDecoration.LineThrough else null),
            color = if (muted) c.textTertiary else valueColor)
    }
}

@Composable
fun Note(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.Info) {
    val (bg, fg) = toneColors(tone)
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(bg).padding(Space.m), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(Modifier.width(Space.s))
        Text(text, style = Forma.type.bodySmall, color = if (tone == Tone.NEUTRAL) Forma.colors.textSecondary else Forma.colors.text)
    }
}

@Composable
fun FormaField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    suffix: String? = null, numeric: Boolean = false, decimal: Boolean = true, singleLine: Boolean = true,
    isError: Boolean = false, supporting: String? = null, placeholder: String? = null,
) {
    val c = Forma.colors
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            when {
                !numeric -> onValueChange(v)
                v.isEmpty() || v.matches(Regex(if (decimal) "^\\d*[.,]?\\d*$" else "^\\d*$")) -> onValueChange(v.replace(',', '.'))
            }
        },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        isError = isError,
        placeholder = placeholder?.let { { Text(it, color = c.textTertiary) } },
        suffix = suffix?.let { { Text(it, style = Forma.type.bodySmall, color = c.textTertiary) } },
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number) else KeyboardOptions.Default,
        shape = RoundedCornerShape(Radius.m),
        textStyle = Forma.type.body.copy(color = c.text),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent, unfocusedBorderColor = c.lineStrong, focusedLabelColor = c.accent,
            unfocusedLabelColor = c.textSecondary, cursorColor = c.accent, focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
        ),
    )
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = Forma.colors
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }.padding(vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Space.m)) {
            Text(title, style = Forma.type.body, color = if (enabled) c.text else c.textTertiary)
            if (subtitle != null) Text(subtitle, style = Forma.type.caption, color = c.textTertiary)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent, checkedTrackColor = c.accent, uncheckedThumbColor = c.textTertiary,
                uncheckedTrackColor = c.surfaceAlt, uncheckedBorderColor = c.lineStrong,
            ),
        )
    }
}

/** List row with icon, title, subtitle and trailing content; used in settings and lists. */
@Composable
fun ListRow(
    title: String, subtitle: String? = null, icon: ImageVector? = null, onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}, titleColor: Color = Forma.colors.text,
) {
    val c = Forma.colors
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.l))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Forma.type.body, color = titleColor)
            if (subtitle != null) Text(subtitle, style = Forma.type.caption, color = c.textTertiary)
        }
        trailing()
    }
}

/** Round check control used for meals and exercises. */
@Composable
fun CheckCircle(checked: Boolean, onToggle: () -> Unit, description: String, modifier: Modifier = Modifier) {
    val c = Forma.colors
    val bg by animateColorAsState(if (checked) c.accent else Color.Transparent, label = "chk")
    Box(
        modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(bg)
                .border(1.5.dp, if (checked) c.accent else c.lineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(Icons.Outlined.Check, null, tint = c.onAccent, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = Forma.type.heading, color = Forma.colors.text)
        Spacer(Modifier.height(Space.xs))
        Text(body, style = Forma.type.bodySmall, color = Forma.colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(Space.m)); action() }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Forma.colors
    Text(
        text, style = Forma.type.label, color = if (selected) c.onAccent else c.textSecondary,
        modifier = modifier.clip(RoundedCornerShape(50)).background(if (selected) c.accent else Color.Transparent)
            .border(1.dp, if (selected) c.accent else c.lineStrong, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
