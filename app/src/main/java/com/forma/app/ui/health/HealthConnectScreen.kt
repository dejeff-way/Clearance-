package com.forma.app.ui.health

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.forma.app.di.AppContainer
import com.forma.app.health.HcAvailability
import com.forma.app.health.HcDataType
import com.forma.app.health.HealthConnectSource
import com.forma.app.health.SyncOutcome
import com.forma.app.health.SyncWorker
import com.forma.app.ui.components.BackBar
import com.forma.app.ui.components.ConfirmDialog
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.Note
import com.forma.app.ui.components.PrimaryButton
import com.forma.app.ui.components.SecondaryButton
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.SwitchRow
import com.forma.app.ui.components.Tag
import com.forma.app.ui.components.Tone
import com.forma.app.ui.components.ValueRow
import com.forma.app.ui.formaViewModel
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import com.forma.app.util.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HcUiState(
    val availability: HcAvailability = HcAvailability.UNAVAILABLE,
    val granted: Set<HcDataType> = emptySet(),
    val backgroundAvailable: Boolean = false,
    val backgroundGranted: Boolean = false,
    val loading: Boolean = true,
    val syncing: Boolean = false,
    val lastOutcome: String? = null,
)

class HealthViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(HcUiState())
    val ui = _ui.asStateFlow()
    val meta = c.syncMeta

    fun refresh() = viewModelScope.launch {
        val src = c.healthSource
        val a = runCatching { src.availability() }.getOrDefault(HcAvailability.UNAVAILABLE)
        val g = if (a == HcAvailability.AVAILABLE) runCatching { src.grantedTypes() }.getOrDefault(emptySet()) else emptySet()
        val bgA = a == HcAvailability.AVAILABLE && runCatching { src.backgroundReadAvailable() }.getOrDefault(false)
        val bgG = bgA && runCatching { src.backgroundReadGranted() }.getOrDefault(false)
        _ui.value = _ui.value.copy(availability = a, granted = g, backgroundAvailable = bgA, backgroundGranted = bgG, loading = false)
    }

    fun onPermissionsResult() = viewModelScope.launch {
        refresh().join()
        if (_ui.value.granted.isNotEmpty()) syncNow()
    }

    fun syncNow() = viewModelScope.launch {
        _ui.value = _ui.value.copy(syncing = true)
        val r = runCatching { c.healthSyncer.sync() }.getOrElse { SyncOutcome.Failed(it.message ?: "Sync failed") }
        _ui.value = _ui.value.copy(
            syncing = false,
            lastOutcome = when (r) {
                is SyncOutcome.Success -> "Synced ${r.days} days, ${r.sessions} exercise sessions, ${r.weights} weigh-ins."
                SyncOutcome.NoPermissions -> "No permissions granted yet."
                SyncOutcome.Unavailable -> "Health Connect isn't available."
                is SyncOutcome.Failed -> "Sync failed: ${r.message}"
            },
        )
    }

    fun setBackground(enabled: Boolean) = viewModelScope.launch {
        c.healthSyncer.setBackground(enabled)
        SyncWorker.schedule(c.context, enabled && _ui.value.backgroundGranted)
    }

    fun disconnect() = viewModelScope.launch {
        runCatching { c.healthSource.revokeAll() }
        c.healthSyncer.markDisconnected()
        SyncWorker.schedule(c.context, false)
        refresh()
    }
}

@Composable
fun HealthConnectScreen(nav: NavController) {
    val vm = formaViewModel { HealthViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val meta by vm.meta.collectAsStateWithLifecycle(null)
    val context = LocalContext.current
    val c = Forma.colors
    var confirmDisconnect by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.onPermissionsResult() }
    val bgLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (HealthConnectSource.BACKGROUND_PERMISSION in granted) vm.setBackground(true)
        vm.refresh()
    }
    LaunchedEffect(Unit) { vm.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val connected = ui.granted.isNotEmpty()

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("health")) {
        BackBar("Health Connect", { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
            FormaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(
                        when { connected -> c.accent; ui.availability == HcAvailability.AVAILABLE -> c.warn; else -> c.textTertiary },
                    ))
                    Spacer(Modifier.width(Space.s))
                    Text(
                        when {
                            ui.loading -> "Checking…"
                            ui.availability == HcAvailability.UNAVAILABLE -> "Not available on this device"
                            ui.availability == HcAvailability.UPDATE_REQUIRED -> "Health Connect needs an update"
                            connected -> "Connected · ${ui.granted.size} of ${HcDataType.entries.size} data types"
                            else -> "Available · not connected"
                        },
                        style = Forma.type.heading, color = c.text, modifier = Modifier.testTag("hc_status"),
                    )
                }
                Spacer(Modifier.height(Space.m))
                ValueRow("Last successful sync", meta?.lastSuccessAt?.let { Fmt.ago(it, System.currentTimeMillis()) } ?: "Never")
                meta?.lastError?.takeIf { it.isNotBlank() && connected }?.let { ValueRow("Last issue", it, valueColor = c.warn) }
                ui.lastOutcome?.let { Text(it, style = Forma.type.caption, color = c.textSecondary) }
            }
            Spacer(Modifier.height(Space.m))

            when (ui.availability) {
                HcAvailability.AVAILABLE -> {
                    PrimaryButton(if (connected) "Review permissions" else "Connect Health Connect", {
                        runCatching { permissionLauncher.launch(HealthConnectSource.readPermissions) }
                    }, Modifier.fillMaxWidth().testTag("hc_connect"))
                    if (connected) {
                        Spacer(Modifier.height(Space.s))
                        SecondaryButton(if (ui.syncing) "Syncing…" else "Sync now", { vm.syncNow() }, Modifier.fillMaxWidth().testTag("hc_sync"),
                            icon = Icons.Outlined.Sync, enabled = !ui.syncing)
                    }
                }
                HcAvailability.UPDATE_REQUIRED -> {
                    Note("Health Connect is installed but needs to be updated before FORMA can read data.", Tone.WARN)
                    Spacer(Modifier.height(Space.s))
                    PrimaryButton("Update Health Connect", { openPlayStore(context) }, Modifier.fillMaxWidth())
                }
                HcAvailability.UNAVAILABLE -> {
                    Note(
                        if (Build.VERSION.SDK_INT >= 34) "Health Connect is built into Android 14 and later (Settings → Security & privacy → Privacy → Health Connect). It may be disabled on this device or restricted by a work profile."
                        else "On Android 13 and earlier, install the Health Connect app from Google Play, then return here. FORMA works fully offline without it, using clearly labelled estimates.",
                        Tone.NEUTRAL,
                    )
                    if (Build.VERSION.SDK_INT < 34) {
                        Spacer(Modifier.height(Space.s))
                        PrimaryButton("Get Health Connect", { openPlayStore(context) }, Modifier.fillMaxWidth())
                    }
                }
            }

            SectionHeader("Data FORMA reads")
            FormaCard {
                HcDataType.entries.forEachIndexed { i, t ->
                    if (i > 0) Hairline()
                    val ok = t in ui.granted
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.m).testTag("hc_type_${t.name}"), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (ok) Icons.Outlined.Check else Icons.Outlined.Close, null, tint = if (ok) c.accent else c.textTertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text(t.label, style = Forma.type.body, color = c.text)
                            Text(t.purpose, style = Forma.type.caption, color = c.textTertiary)
                        }
                        Tag(if (ok) "Authorised" else if (ui.availability == HcAvailability.AVAILABLE) "Not authorised" else "Unavailable", if (ok) Tone.ACCENT else Tone.NEUTRAL)
                    }
                }
            }
            Spacer(Modifier.height(Space.s))
            Text("FORMA only reads data. It never writes to Health Connect, and nothing leaves your phone. " +
                "It reads up to 30 days back (the default without the history permission).",
                style = Forma.type.caption, color = c.textTertiary)

            if (ui.availability == HcAvailability.AVAILABLE && connected) {
                SectionHeader("Background sync")
                FormaCard {
                    if (ui.backgroundAvailable) {
                        SwitchRow(
                            "Sync in the background", "Hourly refresh so the dashboard is current when you open it. Requires an extra permission.",
                            (meta?.backgroundSyncEnabled ?: false) && ui.backgroundGranted,
                            { on ->
                                if (on && !ui.backgroundGranted) runCatching { bgLauncher.launch(setOf(HealthConnectSource.BACKGROUND_PERMISSION)) }
                                else vm.setBackground(on)
                            },
                        )
                    } else {
                        Text("Background reading isn't supported by this device's Health Connect version. FORMA syncs each time you open the app.",
                            style = Forma.type.bodySmall, color = c.textSecondary)
                    }
                }
                SectionHeader("Manage")
                FormaCard {
                    com.forma.app.ui.components.ListRow("Open Health Connect", "Change app sources and priorities", onClick = { openHcSettings(context) })
                    Hairline()
                    com.forma.app.ui.components.ListRow("Disconnect FORMA", "Revokes all permissions. Imported history stays in FORMA.", onClick = { confirmDisconnect = true }, titleColor = c.negative)
                }
            }
        }
    }
    if (confirmDisconnect) ConfirmDialog("Disconnect Health Connect?", "FORMA will stop reading data and fall back to estimates. Already-imported history is kept.",
        "Disconnect", { confirmDisconnect = false; vm.disconnect() }, { confirmDisconnect = false }, destructive = true)
}

private fun openPlayStore(context: android.content.Context) {
    val uri = Uri.parse("market://details?id=${HealthConnectSource.PROVIDER_PACKAGE}&url=healthconnect%3A%2F%2Fonboarding")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${HealthConnectSource.PROVIDER_PACKAGE}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openHcSettings(context: android.content.Context) {
    val intent = if (Build.VERSION.SDK_INT >= 34) Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
    else Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}
