package app.lawnchair.smartspace.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Bundle
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

    private val defaultIcon = Icon.createWithResource(context, R.drawable.ic_music_note)

    override val isAvailable: Boolean = isPowerampInstalled(context)

    override val internalTargets = callbackFlow {
        var title: String? = null
        var artist: String? = null
        var playing = false

        fun publish() {
            val target = if (playing && !title.isNullOrEmpty()) {
                getSmartspaceTarget(title!!, artist)
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
                    playing = intent.getIntExtra(EXTRA_STATE, STATE_NO_STATE) == STATE_PLAYING &&
                        !intent.getBooleanExtra(EXTRA_PAUSED, false)
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

    private fun getSmartspaceTarget(title: String, artist: String?): SmartspaceTarget {
        val subtitle = artist?.takeIf { it.isNotEmpty() }
        val intent = context.packageManager.getLaunchIntentForPackage(POWERAMP_PACKAGE)
        return SmartspaceTarget(
            id = "poweramp-${title.hashCode()}-${artist.hashCode()}",
            headerAction = SmartspaceAction(
                id = "powerampAction-${title.hashCode()}",
                icon = defaultIcon,
                title = title,
                subtitle = subtitle,
                intent = intent,
            ),
            score = SmartspaceScores.SCORE_MEDIA,
            featureType = SmartspaceTarget.FeatureType.FEATURE_MEDIA,
        )
    }

    companion object {
        const val POWERAMP_PACKAGE = "com.maxmpz.audioplayer"

        private const val ACTION_TRACK_CHANGED = "com.maxmpz.audioplayer.TRACK_CHANGED"
        private const val ACTION_STATUS_CHANGED = "com.maxmpz.audioplayer.STATUS_CHANGED"
        private const val EXTRA_TRACK = "track"
        private const val EXTRA_STATE = "state"
        private const val EXTRA_PAUSED = "paused"
        private const val STATE_NO_STATE = -1
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
