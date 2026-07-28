package com.starkiindustries.aod

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* state refreshed in onResume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SetupScreen() }
    }

    override fun onResume() {
        super.onResume()
        // Auto-start if all required permissions are already granted
        if (overlayOk() && notifListenerOk() && batteryOk() && !AodService.isRunning) {
            AodService.start(this)
        }
    }

    // ── Permission helpers ────────────────────────────────────────────────────

    private fun overlayOk()      = Settings.canDrawOverlays(this)
    private fun notifListenerOk(): Boolean {
        val raw = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return raw.contains(packageName)
    }
    private fun batteryOk() =
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
    private fun actRecOk() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    // ── UI ───────────────────────────────────────────────────────────────────

    @Composable
    private fun SetupScreen() {
        // Recompose on resume via a tick state
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(500); tick++ } }

        val overlay   = overlayOk()
        val notif     = notifListenerOk()
        val battery   = batteryOk()
        val actRec    = actRecOk()
        val allOk     = overlay && notif && battery
        val running   = AodService.isRunning

        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Stark AOD",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Thin,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(4.dp))
            Text("Always-On Display",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Light
            )

            Spacer(Modifier.height(52.dp))

            PermRow("Display over other apps", overlay) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
            PermRow("Notification access", notif) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            PermRow("Battery unrestricted", battery) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")))
            }
            PermRow("Activity recognition  (optional, for steps)", actRec, optional = true) {
                permLauncher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
            }

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = {
                    if (running) AodService.stop(this@MainActivity)
                    else if (allOk) AodService.start(this@MainActivity)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) Color(0xFFFF453A) else Color.White,
                    contentColor   = Color.Black
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = allOk || running
            ) {
                Text(
                    text = when {
                        running -> "Stop AOD"
                        allOk   -> "Start AOD Service"
                        else    -> "Grant required permissions first"
                    },
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
            }

            if (running) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "AOD is active — screen dims when you lock",
                    color = Color(0xFF30D158),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }

    @Composable
    private fun PermRow(
        label: String,
        granted: Boolean,
        optional: Boolean = false,
        onGrant: () -> Unit
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    color = if (granted) Color.White else Color.White.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                    fontWeight = if (optional) FontWeight.Light else FontWeight.Normal
                )
            }
            Spacer(Modifier.width(12.dp))
            if (granted) {
                Icon(Icons.Rounded.CheckCircle, null,
                    tint = Color(0xFF30D158), modifier = Modifier.size(22.dp))
            } else {
                TextButton(onClick = onGrant, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Enable →", color = Color(0xFF0A84FF), fontSize = 13.sp)
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
    }
}
