package app.lawnchair.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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
    private var backgroundEnabled = false
    private var backgroundOpacity = 0.35f
    private var scope: CoroutineScope? = null

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
        val prefs2 = PreferenceManager2.getInstance(app)
        scope = MainScope().also {
            combine(prefs2.musicWidgetBackground.get(), prefs2.musicWidgetBackgroundOpacity.get(), ::Pair)
                .onEach { (enabled, opacity) ->
                    backgroundEnabled = enabled
                    backgroundOpacity = opacity
                    render()
                }
                .launchIn(it)
        }
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
        scope?.cancel()
        scope = null
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
        applyColors(context, views)

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

    // Same colours as the At a Glance card: wallpaper text colour, pill in the opposite shade.
    private fun applyColors(context: Context, views: RemoteViews) {
        val themed = ContextThemeWrapper(context, Themes.getActivityThemeRes(context))
        val textColor = Themes.getAttrColor(themed, R.attr.workspaceTextColor)
        views.setTextColor(R.id.music_title, textColor)
        views.setTextColor(R.id.music_artist, textColor)
        listOf(R.id.music_previous, R.id.music_play_pause, R.id.music_next, R.id.music_play_ring).forEach {
            views.setInt(it, "setColorFilter", textColor)
        }
        views.setViewVisibility(R.id.music_background, if (backgroundEnabled) View.VISIBLE else View.GONE)
        if (backgroundEnabled) {
            val base = if (ColorUtils.calculateLuminance(textColor) > 0.5) Color.BLACK else Color.WHITE
            views.setInt(R.id.music_background, "setColorFilter", base)
            views.setInt(R.id.music_background, "setImageAlpha", (backgroundOpacity.coerceIn(0f, 1f) * 255).roundToInt())
        }
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
