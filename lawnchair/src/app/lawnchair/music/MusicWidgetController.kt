package app.lawnchair.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.android.launcher3.R

/**
 * Tracks Poweramp through its sticky broadcasts and keeps every [MusicWidgetProvider]
 * instance up to date. Poweramp broadcasts are implicit, so they can only be received
 * by a receiver registered at runtime; this lives as long as the launcher process.
 */
object MusicWidgetController {

    private var appContext: Context? = null
    private var registered = false

    private var title: String? = null
    private var artist: String? = null
    private var playing = false
    private var stopped = true

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handle(intent)
            render()
        }
    }

    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (registered) return
        registered = true
        listOf(
            PowerampApi.ACTION_TRACK_CHANGED,
            PowerampApi.ACTION_STATUS_CHANGED,
        ).forEach {
            // Both broadcasts are sticky, so the latest of each is replayed on registration.
            ContextCompat.registerReceiver(app, receiver, IntentFilter(it), ContextCompat.RECEIVER_EXPORTED)
                ?.let(::handle)
        }
    }

    @Synchronized
    fun stop() {
        val app = appContext ?: return
        if (!registered) return
        registered = false
        try {
            app.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    fun refresh(context: Context) {
        appContext = context.applicationContext
        render()
    }

    private fun readTrack(track: Bundle?) {
        track ?: return
        title = track.getString(PowerampApi.KEY_TITLE)
        artist = track.getString(PowerampApi.KEY_ARTIST)
    }

    private fun handle(intent: Intent) {
        when (intent.action) {
            PowerampApi.ACTION_TRACK_CHANGED ->
                readTrack(intent.getBundleExtra(PowerampApi.EXTRA_TRACK))
            PowerampApi.ACTION_STATUS_CHANGED -> {
                val state = intent.getIntExtra(PowerampApi.EXTRA_STATE, PowerampApi.STATE_NO_STATE)
                stopped = state == PowerampApi.STATE_STOPPED || state == PowerampApi.STATE_NO_STATE
                playing = state == PowerampApi.STATE_PLAYING &&
                    !intent.getBooleanExtra(PowerampApi.EXTRA_PAUSED, false)
                // Since build 948 the track fields are delivered directly in the extras.
                intent.getStringExtra(PowerampApi.KEY_TITLE)?.let {
                    title = it
                    artist = intent.getStringExtra(PowerampApi.KEY_ARTIST)
                }
                readTrack(intent.getBundleExtra(PowerampApi.EXTRA_TRACK))
            }
        }
    }

    @Synchronized
    private fun render() {
        val context = appContext ?: return
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(MusicWidgetProvider.componentName(context))
        if (ids.isEmpty()) return

        manager.updateAppWidget(ids, buildViews(context))
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.music_widget)
        val installed = PowerampApi.isInstalled(context)
        val hasTrack = installed && !stopped && !title.isNullOrEmpty()

        when {
            !installed -> {
                views.setTextViewText(R.id.music_title, context.getString(R.string.music_widget_not_installed))
                views.setTextViewText(R.id.music_artist, "")
            }
            !hasTrack -> {
                views.setTextViewText(R.id.music_title, context.getString(R.string.music_widget_nothing_playing))
                views.setTextViewText(R.id.music_artist, context.getString(R.string.music_widget_tap_to_open))
            }
            else -> {
                views.setTextViewText(R.id.music_title, title)
                views.setTextViewText(R.id.music_artist, artist.orEmpty())
            }
        }

        views.setImageViewResource(
            R.id.music_play_pause,
            if (hasTrack && playing) R.drawable.ic_media_pause else R.drawable.ic_media_play,
        )
        views.setOnClickPendingIntent(R.id.music_previous, commandIntent(context, PowerampApi.COMMAND_PREVIOUS))
        views.setOnClickPendingIntent(R.id.music_play_pause, commandIntent(context, PowerampApi.COMMAND_TOGGLE_PLAY_PAUSE))
        views.setOnClickPendingIntent(R.id.music_next, commandIntent(context, PowerampApi.COMMAND_NEXT))

        context.packageManager.getLaunchIntentForPackage(PowerampApi.PACKAGE)?.let {
            views.setOnClickPendingIntent(
                R.id.music_root,
                PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        }
        return views
    }

    private fun commandIntent(context: Context, command: Int): PendingIntent {
        val intent = Intent(MusicWidgetProvider.ACTION_COMMAND)
            .setComponent(MusicWidgetProvider.componentName(context))
            .putExtra(MusicWidgetProvider.EXTRA_COMMAND, command)
        return PendingIntent.getBroadcast(
            context,
            command,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
