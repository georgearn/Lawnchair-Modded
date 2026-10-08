package app.lawnchair.smartspace.provider

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import com.android.launcher3.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/**
 * Shows the track playing in Poweramp using its broadcast API instead of the media notification,
 * so it doesn't depend on notification access.
 */
class PowerampProvider(context: Context) : SmartspaceDataSource(
    context,
    R.string.smartspace_poweramp,
    { smartspacePoweramp },
) {

    override val isAvailable: Boolean = isPowerampInstalled(context)

    override val internalTargets = callbackFlow {
        var title: String? = null
        var artist: String? = null
        var playing = false
        var stopped = true

        fun publish() {
            // Stay visible while paused so the controls can resume playback; hide once stopped.
            val target = if (!stopped && !title.isNullOrEmpty()) {
                getSmartspaceTarget(title!!, artist, playing)
            } else {
                null
            }
            trySend(listOfNotNull(target))
        }

        fun readTrack(track: Bundle?) {
            track ?: return
            title = track.getString(KEY_TITLE)
            artist = track.getString(KEY_ARTIST)
        }

        fun handle(intent: Intent) {
            when (intent.action) {
                ACTION_TRACK_CHANGED -> readTrack(intent.getBundleExtra(EXTRA_TRACK))
                ACTION_STATUS_CHANGED -> {
                    val state = intent.getIntExtra(EXTRA_STATE, STATE_NO_STATE)
                    stopped = state == STATE_STOPPED || state == STATE_NO_STATE
                    playing = state == STATE_PLAYING && !intent.getBooleanExtra(EXTRA_PAUSED, false)
                    // Since build 948 the track fields are delivered directly in the extras.
                    intent.getStringExtra(KEY_TITLE)?.let {
                        title = it
                        artist = intent.getStringExtra(KEY_ARTIST)
                    }
                    readTrack(intent.getBundleExtra(EXTRA_TRACK))
                }
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handle(intent)
                publish()
            }
        }
        // Both broadcasts are sticky, so the latest of each is replayed on registration.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_TRACK_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )?.let { handle(it) }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_STATUS_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )?.let { handle(it) }
        publish()

        awaitClose { context.unregisterReceiver(receiver) }
    }

    private fun sendCommand(command: Int) {
        val intent = Intent(ACTION_API_COMMAND)
            .setComponent(ComponentName(POWERAMP_PACKAGE, API_RECEIVER_NAME))
            .putExtra(EXTRA_COMMAND, command)
        try {
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send Poweramp command $command", e)
        }
    }

    private fun getSmartspaceTarget(title: String, artist: String?, playing: Boolean): SmartspaceTarget {
        val subtitle = artist?.takeIf { it.isNotEmpty() }
        val intent = context.packageManager.getLaunchIntentForPackage(POWERAMP_PACKAGE)
        return SmartspaceTarget(
            id = "poweramp-${title.hashCode()}-${artist.hashCode()}",
            headerAction = SmartspaceAction(
                id = "powerampAction-${title.hashCode()}",
                title = title,
                subtitle = subtitle,
                intent = intent,
            ),
            score = SmartspaceScores.SCORE_MEDIA,
            mediaControls = SmartspaceTarget.MediaControls(
                isPlaying = playing,
                onPrevious = Runnable { sendCommand(COMMAND_PREVIOUS) },
                onPlayPause = Runnable { sendCommand(COMMAND_TOGGLE_PLAY_PAUSE) },
                onNext = Runnable { sendCommand(COMMAND_NEXT) },
            ),
            featureType = SmartspaceTarget.FeatureType.FEATURE_MEDIA,
        )
    }

    companion object {
        const val POWERAMP_PACKAGE = "com.maxmpz.audioplayer"

        private const val ACTION_TRACK_CHANGED = "com.maxmpz.audioplayer.TRACK_CHANGED"
        private const val ACTION_STATUS_CHANGED = "com.maxmpz.audioplayer.STATUS_CHANGED"
        private const val TAG = "PowerampProvider"
        private const val ACTION_API_COMMAND = "com.maxmpz.audioplayer.API_COMMAND"
        private const val API_RECEIVER_NAME = "com.maxmpz.audioplayer.player.PowerampAPIReceiver"
        private const val EXTRA_COMMAND = "cmd"
        private const val COMMAND_TOGGLE_PLAY_PAUSE = 1
        private const val COMMAND_NEXT = 4
        private const val COMMAND_PREVIOUS = 5
        private const val EXTRA_TRACK = "track"
        private const val EXTRA_STATE = "state"
        private const val EXTRA_PAUSED = "paused"
        private const val STATE_NO_STATE = -1
        private const val STATE_STOPPED = 0
        private const val STATE_PLAYING = 1
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"

        fun isPowerampInstalled(context: Context): Boolean = try {
            context.packageManager.getPackageInfo(POWERAMP_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
