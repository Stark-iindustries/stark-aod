package com.starkiindustries.aod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Settings.canDrawOverlays(context)) {
            AodService.start(context)
        }
    }
}
