package com.forma.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forma.app.data.prefs.AppSettings
import com.forma.app.data.prefs.ThemeMode
import com.forma.app.ui.LocalContainer
import com.forma.app.ui.nav.FormaRoot
import com.forma.app.ui.theme.FormaTheme
import android.graphics.Color as AColor

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        routeFrom(intent)
    }

    /** Opens the screen a notification points to (e.g. the weekly report). */
    private fun routeFrom(intent: android.content.Intent?) {
        val route = intent?.getStringExtra(com.forma.app.data.report.ReportNotifier.EXTRA_ROUTE) ?: return
        intent.removeExtra(com.forma.app.data.report.ReportNotifier.EXTRA_ROUTE)
        (application as FormaApp).container.pendingRoute.value = route
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as FormaApp).container
        routeFrom(intent)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            val dark = when (settings.theme) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            androidx.compose.runtime.LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
                )
            }
            CompositionLocalProvider(LocalContainer provides container) {
                FormaTheme(settings.theme) { FormaRoot() }
            }
        }
    }
}
