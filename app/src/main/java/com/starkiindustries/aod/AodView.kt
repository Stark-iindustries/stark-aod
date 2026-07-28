package com.starkiindustries.aod

import android.content.Context
import android.hardware.BatteryState
import android.os.BatteryManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

// ─── Host view ───────────────────────────────────────────────────────────────

class AodView(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val scope: CoroutineScope,
    private val onTap: () -> Unit
) : FrameLayout(context) {

    init {
        // Wire lifecycle owners so ComposeView can compose correctly
        setViewTreeLifecycleOwner(lifecycleOwner)
        if (lifecycleOwner is SavedStateRegistryOwner) setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        if (lifecycleOwner is ViewModelStoreOwner)     setViewTreeViewModelStoreOwner(lifecycleOwner)

        val cv = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            if (lifecycleOwner is SavedStateRegistryOwner) setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            if (lifecycleOwner is ViewModelStoreOwner)     setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    AodScreen(context = context, onTap = onTap)
                }
            }
        }
        addView(cv)
    }
}

// ─── Root screen ─────────────────────────────────────────────────────────────

@Composable
private fun AodScreen(context: Context, onTap: () -> Unit) {

    // ── Clock ──
    var timeStr by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("HH:mm",         Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEEE, MMM d",   Locale.getDefault())
        while (true) {
            val now = Date()
            timeStr = timeFmt.format(now)
            dateStr = dateFmt.format(now)
            delay(1_000)
        }
    }

    // ── Battery ──
    var battery by remember { mutableIntStateOf(getBattery(context)) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); battery = getBattery(context) }
    }

    // ── Weather (refresh every 30 min) ──
    var weather by remember { mutableStateOf<WeatherData?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            weather = WeatherHelper.fetch()
            delay(30 * 60_000L)
        }
    }

    // ── Steps ──
    var steps by remember { mutableLongStateOf(0L) }
    DisposableEffect(Unit) {
        val helper = StepHelper(context) { steps = it }
        helper.start()
        onDispose { helper.stop() }
    }

    // ── Music ──
    var music by remember { mutableStateOf<MusicInfo?>(null) }
    LaunchedEffect(Unit) {
        while (true) { music = MusicHelper.getCurrent(context); delay(3_000) }
    }

    // ── Notification dots ──
    val notifApps = NotifListenerService.apps

    // Full-screen black canvas; tap anywhere (except controls) dismisses
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 60.dp)
        ) {

            // ── Large time ──────────────────────────────────────────────────
            Text(
                text = timeStr,
                color = Color.White,
                fontSize = 92.sp,
                fontWeight = FontWeight.Thin,
                letterSpacing = (-3).sp,
                lineHeight = 88.sp
            )

            // ── Date ────────────────────────────────────────────────────────
            Text(
                text = dateStr,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 0.2.sp
            )

            Spacer(Modifier.height(36.dp))

            // ── Status row: battery + weather ──────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(icon = Icons.Rounded.BatteryFull, label = "$battery%")
                weather?.let { w ->
                    StatusChip(
                        icon = weatherIcon(w.condition),
                        label = "${w.tempC}°  ${w.condition}"
                    )
                }
            }

            // ── Step count ──────────────────────────────────────────────────
            if (steps > 0L) {
                Spacer(Modifier.height(12.dp))
                StatusChip(
                    icon = Icons.Rounded.DirectionsWalk,
                    label = formatSteps(steps)
                )
            }

            // ── Music player ────────────────────────────────────────────────
            music?.let { m ->
                Spacer(Modifier.height(32.dp))
                MusicPlayer(context = context, info = m)
            }

            // ── Notification dots ───────────────────────────────────────────
            if (notifApps.isNotEmpty()) {
                Spacer(Modifier.height(32.dp))
                NotifDots(notifApps)
            }
        }
    }
}

// ─── Status chip ─────────────────────────────────────────────────────────────

@Composable
private fun StatusChip(icon: ImageVector, label: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(14.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Light)
    }
}

// ─── Music player ─────────────────────────────────────────────────────────────

@Composable
private fun MusicPlayer(context: Context, info: MusicInfo) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Rounded.MusicNote, null,
                tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(13.dp))
            Text(
                text = buildString {
                    append(info.title)
                    if (info.artist.isNotBlank()) append("  —  ${info.artist}")
                },
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 280.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaButton(Icons.Rounded.SkipPrevious, 28.dp) { MusicHelper.previous(context) }
            MediaButton(
                icon = if (info.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                size = 36.dp
            ) { MusicHelper.playPause(context) }
            MediaButton(Icons.Rounded.SkipNext, 28.dp) { MusicHelper.next(context) }
        }
    }
}

@Composable
private fun MediaButton(icon: ImageVector, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size + 8.dp)
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.80f), modifier = Modifier.size(size))
    }
}

// ─── Notification dots ────────────────────────────────────────────────────────

@Composable
private fun NotifDots(apps: List<NotifApp>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        apps.take(8).forEach { _ ->
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.60f))
            )
        }
        if (apps.size > 8) {
            Text("+${apps.size - 8}", color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun getBattery(context: Context): Int {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
}

private fun formatSteps(steps: Long): String {
    return if (steps >= 1_000) "${"%.1f".format(steps / 1_000f)}k steps"
    else "$steps steps"
}

private fun weatherIcon(condition: String): ImageVector {
    val c = condition.lowercase()
    return when {
        "sun" in c || "clear" in c                       -> Icons.Rounded.WbSunny
        "rain" in c || "drizzle" in c || "shower" in c  -> Icons.Rounded.Umbrella
        "snow" in c || "blizzard" in c || "sleet" in c  -> Icons.Rounded.AcUnit
        "thunder" in c || "storm" in c || "lightning" in c -> Icons.Rounded.Thunderstorm
        "fog" in c || "mist" in c || "haze" in c        -> Icons.Rounded.WaterDrop
        "cloud" in c || "overcast" in c                  -> Icons.Rounded.WbCloudy
        else                                              -> Icons.Rounded.Cloud
    }
}
