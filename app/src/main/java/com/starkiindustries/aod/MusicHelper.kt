package com.starkiindustries.aod

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent

data class MusicInfo(
    val title: String,
    val artist: String,
    val isPlaying: Boolean
)

object MusicHelper {

    fun getCurrent(context: Context): MusicInfo? {
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn = ComponentName(context, NotifListenerService::class.java)
            val controller = msm.getActiveSessions(cn).firstOrNull() ?: return null
            val meta = controller.metadata ?: return null
            val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: return null
            val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            MusicInfo(title, artist, playing)
        } catch (_: Exception) {
            null
        }
    }

    fun previous(context: Context) = key(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    fun playPause(context: Context) = key(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun next(context: Context) = key(context, KeyEvent.KEYCODE_MEDIA_NEXT)

    private fun key(context: Context, code: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}
