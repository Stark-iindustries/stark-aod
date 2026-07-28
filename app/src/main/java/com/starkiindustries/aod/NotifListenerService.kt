package com.starkiindustries.aod

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateListOf

data class NotifApp(val pkg: String)

class NotifListenerService : NotificationListenerService() {

    companion object {
        /** Live list of packages that currently have notifications. Observed by Compose. */
        val apps = mutableStateListOf<NotifApp>()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        // Ignore our own notifications
        if (pkg == packageName) return
        if (apps.none { it.pkg == pkg }) {
            apps.add(NotifApp(pkg))
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        apps.removeAll { it.pkg == sbn.packageName }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Populate from already-active notifications
        apps.clear()
        try {
            activeNotifications?.forEach { sbn ->
                if (sbn.packageName != packageName && apps.none { it.pkg == sbn.packageName })
                    apps.add(NotifApp(sbn.packageName))
            }
        } catch (_: Exception) {}
    }

    override fun onListenerDisconnected() {
        apps.clear()
    }
}
