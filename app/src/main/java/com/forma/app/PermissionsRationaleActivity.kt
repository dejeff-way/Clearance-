package com.forma.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.forma.app.health.HcDataType
import com.forma.app.ui.components.PrimaryButton
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.Space

/** Privacy policy / rationale shown by Health Connect when the user asks why FORMA wants access. */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FormaTheme {
                val c = Forma.colors
                Column(
                    Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().verticalScroll(rememberScrollState())
                        .padding(Space.gutter),
                ) {
                    Text("How FORMA uses your health data", style = Forma.type.title, color = c.text)
                    Spacer(Modifier.height(Space.l))
                    Text(
                        "FORMA reads data from Health Connect only to estimate your daily energy expenditure, show your " +
                            "activity and import weigh-ins. It never writes to Health Connect, never sells or shares your data, " +
                            "and stores everything only on this device.",
                        style = Forma.type.body, color = c.textSecondary,
                    )
                    Spacer(Modifier.height(Space.l))
                    HcDataType.entries.forEach {
                        Text(it.label, style = Forma.type.bodyStrong, color = c.text)
                        Text(it.purpose, style = Forma.type.bodySmall, color = c.textSecondary)
                        Spacer(Modifier.height(Space.m))
                    }
                    Text(
                        "You can revoke access at any time in FORMA (Settings → Health Connect → Disconnect) or in Health Connect itself.",
                        style = Forma.type.bodySmall, color = c.textSecondary,
                    )
                    Spacer(Modifier.height(Space.xl))
                    PrimaryButton("Close", { finish() }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
