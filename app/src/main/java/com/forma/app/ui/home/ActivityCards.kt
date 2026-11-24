package com.forma.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Stairs
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.forma.app.data.db.SyncMetaEntity
import com.forma.app.data.prefs.AppSettings
import com.forma.app.data.repo.EnergyModel
import com.forma.app.health.HcDataType
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.ThinProgress
import com.forma.app.ui.metrics.Metric
import com.forma.app.ui.metrics.MetricValues
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import com.forma.app.util.Fmt
import java.time.LocalDate

/** One dashboard tile. [value] null → shown as "—" with an honest reason, never as zero. */
private data class Tile(
    val metric: Metric, val icon: ImageVector, val label: String, val value: String?, val unit: String?,
    val sub: String?, val progress: Float? = null,
)

/**
 * Live activity & health tiles bound to the synced Health Connect data in Room. They update automatically whenever a
 * sync writes new rows. Tapping opens the metric's history.
 */
@Composable
fun ActivityGrid(model: EnergyModel, sync: SyncMetaEntity?, settings: AppSettings, onOpen: (Metric) -> Unit) {
    val today = model.s.today
    val e = model.s.energy[today.toEpochDay()]
    val granted = sync?.grantedPermissions.orEmpty().split(',').filter { it.isNotBlank() }.toSet()
    val connected = granted.isNotEmpty()

    fun missing(t: HcDataType?, has: Boolean, fallback: String? = null): String? = when {
        has -> fallback
        t == null -> "No data yet"
        !connected -> "Not connected"
        t.name !in granted -> "Not allowed — tap to fix"
        else -> "No data yet today"
    }

    /** Most recent value within [days] days, with a "Mon"-style label when it isn't from today. */
    fun recent(days: Int, f: (com.forma.app.data.db.DailyEnergyEntity) -> Double?): Pair<Double, LocalDate>? =
        (0 until days).map { today.minusDays(it.toLong()) }.firstNotNullOfOrNull { d -> model.s.energy[d.toEpochDay()]?.let(f)?.let { it to d } }

    val goal = model.s.profile.stepGoal.coerceAtLeast(1)
    val steps = e?.steps
    val formaMinutes = model.s.workouts.filter { it.epochDay == today.toEpochDay() && it.completedAt != null && it.startedAt != null }
        .sumOf { ((it.completedAt!! - it.startedAt!!) / 60_000.0).coerceIn(0.0, 240.0) }.takeIf { it > 0 }
    val exercise = e?.exerciseMinutes ?: formaMinutes
    val distance = MetricValues.value(Metric.DISTANCE, model, today, settings)
    val rhr = recent(4) { it.restingHr }
    val hrv = recent(4) { it.hrvMs }
    val sleep = e?.sleepMinutes
    val burned = model.today.expenditure
    val hasHrvHistory = (0 until 30).any { model.s.energy[today.minusDays(it.toLong()).toEpochDay()]?.hrvMs != null }
    val hasFloors = (0 until 30).any { model.s.energy[today.minusDays(it.toLong()).toEpochDay()]?.floors != null }
    fun dayTag(d: LocalDate) = if (d == today) null else if (d == today.minusDays(1)) "Yesterday" else Fmt.dow(d)

    val tiles = buildList {
        add(Tile(Metric.STEPS, Icons.AutoMirrored.Outlined.DirectionsWalk, "Steps", steps?.let { Fmt.int(it) }, null,
            if (steps != null) (if (steps >= goal) "Goal reached · ${Fmt.int(goal)}" else "${Fmt.int(goal - steps)} to ${Fmt.int(goal)}") else missing(HcDataType.STEPS, false),
            progress = steps?.let { (it.toFloat() / goal).coerceIn(0f, 1f) }))
        add(Tile(Metric.EXPENDITURE, Icons.Outlined.LocalFireDepartment, "Burned so far", Fmt.kcal(burned.burnedSoFarKcal), "kcal",
            when (burned.source) {
                com.forma.engine.ExpenditureSource.HEALTH_CONNECT_TOTAL -> "Wearable · est."
                com.forma.engine.ExpenditureSource.HEALTH_CONNECT_ACTIVE -> "Wearable active + resting"
                com.forma.engine.ExpenditureSource.ESTIMATED -> "Estimated from profile"
            }))
        add(Tile(Metric.EXERCISE, Icons.Outlined.Timer, "Exercise", exercise?.let { Fmt.int(it) }, "min",
            if (e?.exerciseMinutes != null) "Health Connect" else if (formaMinutes != null) "FORMA workouts" else missing(HcDataType.EXERCISE, false)))
        add(Tile(Metric.DISTANCE, Icons.Outlined.Place, "Distance", distance?.let { Fmt.one(it) }, Metric.DISTANCE.unit(settings),
            missing(HcDataType.DISTANCE, distance != null, "Today")))
        add(Tile(Metric.HEART_RATE, Icons.Outlined.FavoriteBorder, "Heart rate", e?.avgHr?.let { Fmt.int(it) }, "bpm avg",
            if (e?.avgHr != null) listOfNotNull(e.minHr, e.maxHr).takeIf { it.size == 2 }?.let { "Range ${it[0].toInt()}–${it[1].toInt()}" } ?: "Today"
            else missing(HcDataType.HEART_RATE, false)))
        add(Tile(Metric.RESTING_HR, Icons.Outlined.MonitorHeart, "Resting HR", rhr?.first?.let { Fmt.int(it) }, "bpm",
            rhr?.second?.let { dayTag(it) ?: "Today" } ?: missing(HcDataType.RESTING_HEART_RATE, false)))
        add(Tile(Metric.SLEEP, Icons.Outlined.Bedtime, "Sleep", sleep?.let { "${(it / 60).toInt()}h ${(it % 60).toInt()}m" }, null,
            if (sleep != null) e.sleepDeepMin?.let { "Deep ${it.toInt()}m · REM ${(e.sleepRemMin ?: 0.0).toInt()}m" } ?: "Last night"
            else missing(HcDataType.SLEEP, false)))
        if (hasHrvHistory || HcDataType.HRV.name in granted) add(Tile(Metric.HRV, Icons.Outlined.Waves, "HRV", hrv?.first?.let { Fmt.int(it) }, "ms",
            hrv?.second?.let { dayTag(it) ?: "Today" } ?: missing(HcDataType.HRV, false)))
        if (hasFloors) add(Tile(Metric.FLOORS, Icons.Outlined.Stairs, "Floors", e?.floors?.let { Fmt.int(it) }, null, missing(HcDataType.FLOORS, e?.floors != null, "Today")))
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                row.forEach { t -> TileCard(t, Modifier.weight(1f)) { onOpen(t.metric) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TileCard(t: Tile, modifier: Modifier, onClick: () -> Unit) {
    val c = Forma.colors
    FormaCard(modifier.testTag("tile_${t.metric.name}"), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(t.icon, null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Space.xs))
            Text(t.label, style = Forma.type.caption, color = c.textSecondary)
        }
        Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(t.value ?: "—", style = Forma.type.numberLarge, color = if (t.value == null) c.textTertiary else c.text,
                modifier = Modifier.testTag("tile_value_${t.metric.name}"))
            if (t.value != null && t.unit != null) Text(" ${t.unit}", style = Forma.type.caption, color = c.textTertiary, modifier = Modifier.padding(bottom = 3.dp))
        }
        t.progress?.let { Spacer(Modifier.height(Space.s)); ThinProgress(it, height = 3.dp) }
        t.sub?.let { Text(it, style = Forma.type.caption, color = if (it.contains("tap to fix")) c.warn else c.textTertiary, maxLines = 1, modifier = Modifier.padding(top = 2.dp)) }
    }
}
