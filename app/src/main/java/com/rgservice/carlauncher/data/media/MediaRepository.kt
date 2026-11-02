package com.rgservice.carlauncher.data.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import com.rgservice.carlauncher.data.Media
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Música de cualquier app (Spotify, YouTube Music, la música Bluetooth de la radio…).
 *  - Si la app publica su sesión: título, artista, carátula, progreso y controles reales.
 *  - Si no (algunas apps Bluetooth de radios chinas): igual se controla con las teclas
 *    multimedia, enviadas a la app elegida en el botón "Música" de la barra.
 */
class MediaRepository(private val ctx: Context, private val sp: SharedPreferences) {

    private val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val listener = ComponentName(ctx, CarNotificationListener::class.java)
    private var current: MediaController? = null

    fun hasPermission(): Boolean {
        val s = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return s.contains(ctx.packageName)
    }

    fun updates(): Flow<Media> = callbackFlow {
        val h = Handler(Looper.getMainLooper())
        var ctrl: MediaController? = null
        var registered = false

        var repick: () -> Unit = {}
        fun push() { trySend(build(ctrl)) }

        val cb = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                // si esta se pausó y otra está sonando, pasa a la otra
                if (state?.state != PlaybackState.STATE_PLAYING) repick() else push()
            }
            override fun onMetadataChanged(metadata: MediaMetadata?) = push()
            override fun onSessionDestroyed() = repick()
        }

        fun choose(list: List<MediaController>): MediaController? =
            list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: list.firstOrNull { it.sessionToken == ctrl?.sessionToken }
                ?: list.firstOrNull()

        fun pickFrom(list: List<MediaController>) {
            val next = choose(list)
            if (next?.sessionToken != ctrl?.sessionToken) {
                ctrl?.unregisterCallback(cb)
                ctrl = next
                current = next
                next?.registerCallback(cb, h)
            }
            push()
        }

        fun sessions(): List<MediaController> =
            try { msm.getActiveSessions(listener) } catch (e: SecurityException) { emptyList() }

        repick = { pickFrom(sessions()) }

        val changed = MediaSessionManager.OnActiveSessionsChangedListener { list -> pickFrom(list ?: emptyList()) }

        val loop = launch {
            while (isActive) {
                if (!registered && hasPermission()) {
                    try {
                        msm.addOnActiveSessionsChangedListener(changed, listener, h)
                        registered = true
                    } catch (e: SecurityException) { }
                }
                if (registered) pickFrom(sessions()) else trySend(Media(hasPermission = false))
                delay(3000)
            }
        }
        awaitClose {
            loop.cancel()
            ctrl?.unregisterCallback(cb)
            current = null
            if (registered) try { msm.removeOnActiveSessionsChangedListener(changed) } catch (e: Exception) { }
        }
    }

    private fun build(c: MediaController?): Media {
        if (c == null) return Media(hasPermission = true)
        val md = c.metadata
        val ps = c.playbackState
        val label = appLabel(c.packageName)
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
        return Media(
            source = label,
            title = title ?: if (ps?.state == PlaybackState.STATE_PLAYING) "Reproduciendo" else "En pausa",
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            playing = ps?.state == PlaybackState.STATE_PLAYING,
            positionMs = ps?.position ?: 0L,
            positionAt = ps?.lastPositionUpdateTime ?: 0L,
            speed = ps?.playbackSpeed ?: 1f,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            pkg = c.packageName,
            hasPermission = true,
            hasSession = true,
        )
    }

    private fun appLabel(pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { "Música" }

    /* ------------------------------------------------------------ controles */

    fun playPause() {
        val c = current
        if (c == null) { key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); return }
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() { current?.transportControls?.skipToNext() ?: key(KeyEvent.KEYCODE_MEDIA_NEXT) }
    fun previous() { current?.transportControls?.skipToPrevious() ?: key(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }

    /** Abre la app que está sonando (o la elegida para Música). */
    fun openPlayingApp(): String? = current?.packageName ?: sp.getString("slot_music", null)

    /**
     * Sin sesión: la tecla va a la app elegida para "Música" (por ejemplo la de Bluetooth
     * de la radio). Si no hay ninguna elegida, a la última que usó las teclas multimedia.
     */
    private fun key(code: Int) {
        val pkg = sp.getString("slot_music", null)
        val down = KeyEvent(KeyEvent.ACTION_DOWN, code)
        val up = KeyEvent(KeyEvent.ACTION_UP, code)
        if (pkg != null) {
            try {
                for (e in listOf(down, up)) {
                    ctx.sendBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg).putExtra(Intent.EXTRA_KEY_EVENT, e))
                }
                return
            } catch (e: Exception) { }
        }
        am.dispatchMediaKeyEvent(down)
        am.dispatchMediaKeyEvent(up)
    }

    /** Posición actual estimada (entre una actualización y otra de la app). */
    companion object {
        fun positionNow(m: Media): Long {
            var pos = m.positionMs
            if (m.playing && m.positionAt > 0) pos += ((SystemClock.elapsedRealtime() - m.positionAt) * m.speed).toLong()
            return if (m.durationMs > 0) pos.coerceIn(0L, m.durationMs) else pos.coerceAtLeast(0L)
        }
    }
}
