package app.lawnchair.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import android.view.View
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
    private var album: String? = null
    private var durationSeconds = 0
    private var positionSeconds = 0
    private var playing = false
    private var stopped = true
    private var lastPositionUpdate = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handle(intent)
            val progressOnly = intent.action == PowerampApi.ACTION_TRACK_POS_SYNC
            render(progressOnly)
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
            PowerampApi.ACTION_TRACK_POS_SYNC,
        ).forEach {
            // The first two are sticky, so the latest of each is replayed on registration.
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
        render(progressOnly = false)
    }

    private fun readTrack(track: Bundle?) {
        track ?: return
        title = track.getString(PowerampApi.KEY_TITLE)
        artist = track.getString(PowerampApi.KEY_ARTIST)
        album = track.getString(PowerampApi.KEY_ALBUM)
        durationSeconds = track.getInt(PowerampApi.KEY_DURATION, 0)
    }

    private fun handle(intent: Intent) {
        when (intent.action) {
            PowerampApi.ACTION_TRACK_CHANGED -> {
                readTrack(intent.getBundleExtra(PowerampApi.EXTRA_TRACK))
                positionSeconds = 0
            }
            PowerampApi.ACTION_STATUS_CHANGED -> {
                val state = intent.getIntExtra(PowerampApi.EXTRA_STATE, PowerampApi.STATE_NO_STATE)
                stopped = state == PowerampApi.STATE_STOPPED || state == PowerampApi.STATE_NO_STATE
                playing = state == PowerampApi.STATE_PLAYING &&
                    !intent.getBooleanExtra(PowerampApi.EXTRA_PAUSED, false)
                // Since build 948 the track fields are delivered directly in the extras.
                intent.getStringExtra(PowerampApi.KEY_TITLE)?.let {
                    title = it
                    artist = intent.getStringExtra(PowerampApi.KEY_ARTIST)
                    album = intent.getStringExtra(PowerampApi.KEY_ALBUM)
                    durationSeconds = intent.getIntExtra(PowerampApi.KEY_DURATION, durationSeconds)
                }
                readTrack(intent.getBundleExtra(PowerampApi.EXTRA_TRACK))
            }
            PowerampApi.ACTION_TRACK_POS_SYNC ->
                positionSeconds = intent.getIntExtra(PowerampApi.EXTRA_POSITION, positionSeconds)
        }
    }

    @Synchronized
    private fun render(progressOnly: Boolean) {
        val context = appContext ?: return
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(MusicWidgetProvider.componentName(context))
        if (ids.isEmpty()) return

        if (progressOnly) {
            // Position ticks arrive about every second, only redraw the bar a little less often.
            val now = SystemClock.elapsedRealtime()
            if (now - lastPositionUpdate < PROGRESS_THROTTLE_MS) return
            lastPositionUpdate = now
            val views = RemoteViews(context.packageName, R.layout.music_widget)
            applyProgress(views)
            manager.partiallyUpdateAppWidget(ids, views)
            return
        }
        manager.updateAppWidget(ids, buildViews(context))
    }

    private fun applyProgress(views: RemoteViews) {
        val show = !stopped && durationSeconds > 0
        views.setViewVisibility(R.id.music_progress, if (show) View.VISIBLE else View.INVISIBLE)
        if (show) {
            views.setProgressBar(R.id.music_progress, durationSeconds, positionSeconds.coerceIn(0, durationSeconds), false)
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.music_widget)
        val installed = PowerampApi.isInstalled(context)
        val hasTrack = installed && !stopped && !title.isNullOrEmpty()

        when {
            !installed -> {
                views.setTextViewText(R.id.music_title, context.getString(R.string.music_widget_not_installed))
                views.setTextViewText(R.id.music_artist, "")
                views.setTextViewText(R.id.music_detail, "")
            }
            !hasTrack -> {
                views.setTextViewText(R.id.music_title, context.getString(R.string.music_widget_nothing_playing))
                views.setTextViewText(R.id.music_artist, context.getString(R.string.music_widget_tap_to_open))
                views.setTextViewText(R.id.music_detail, "")
            }
            else -> {
                views.setTextViewText(R.id.music_title, title)
                views.setTextViewText(R.id.music_artist, artist.orEmpty())
                views.setTextViewText(R.id.music_detail, album.orEmpty())
            }
        }
        applyProgress(views)

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

    private const val PROGRESS_THROTTLE_MS = 2000L
}
