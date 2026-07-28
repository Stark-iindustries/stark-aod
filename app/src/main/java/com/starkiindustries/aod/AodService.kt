package com.starkiindustries.aod

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.*
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import kotlinx.coroutines.*

class AodService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    // ── Lifecycle / SavedState boilerplate (required for ComposeView in overlay) ──
    private val vmStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = vmStore

    private val ssrc = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = ssrc.savedStateRegistry

    companion object {
        const val CHANNEL_ID = "stark_aod"
        const val NOTIF_ID   = 1
        var isRunning        = false

        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, AodService::class.java))
        fun stop(ctx: Context)  = ctx.stopService(Intent(ctx, AodService::class.java))
    }

    private lateinit var wm: WindowManager
    private var aodView: AodView? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> showAod()
                Intent.ACTION_SCREEN_ON  -> hideAod()
            }
        }
    }

    override fun onCreate() {
        ssrc.performRestore(null)
        super.onCreate()
        isRunning = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        createChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            startForeground(NOTIF_ID, buildNotif(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else
            startForeground(NOTIF_ID, buildNotif())

        @Suppress("UnspecifiedRegisterReceiverFlag")
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? { super.onBind(intent); return null }

    override fun onDestroy() {
        isRunning = false
        hideAod()
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        scope.cancel()
        vmStore.clear()
        super.onDestroy()
    }

    // ── AOD window management ─────────────────────────────────────────────────

    private fun showAod() {
        if (aodView != null) return
        acquireWakeLock()
        try {
            val v = AodView(
                context       = this,
                lifecycleOwner = this,
                scope         = scope,
                onTap         = { hideAodAndWake() }
            ).also { aodView = it }
            wm.addView(v, aodParams())
        } catch (e: Exception) {
            releaseWakeLock()
        }
    }

    private fun hideAod() {
        aodView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        aodView = null
        releaseWakeLock()
    }

    /** Tap on AOD: wake screen fully so system keyguard takes over. */
    private fun hideAodAndWake() {
        hideAod()
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "stark_aod:wake"
            ).apply { acquire(2000L); release() }
        } catch (_: Exception) {}
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "stark_aod:dim"
            ).also { it.acquire(4 * 60 * 60 * 1000L) } // 4h max
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }

    private fun aodParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            @Suppress("DEPRECATION") WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
        PixelFormat.OPAQUE
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        // Near-zero brightness = true AOD on AMOLED, very dim on LCD
        screenBrightness = 0.015f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Stark AOD", NotificationManager.IMPORTANCE_MIN)
            .apply { setShowBadge(false) }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun buildNotif() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Stark AOD")
        .setContentText("Always-On Display is active")
        .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()
}
