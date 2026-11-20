package app.lawnchair.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/** Constants and helpers for the Poweramp broadcast API. */
object PowerampApi {
    const val PACKAGE = "com.maxmpz.audioplayer"

    const val ACTION_TRACK_CHANGED = "com.maxmpz.audioplayer.TRACK_CHANGED"
    const val ACTION_STATUS_CHANGED = "com.maxmpz.audioplayer.STATUS_CHANGED"
    const val ACTION_TRACK_POS_SYNC = "com.maxmpz.audioplayer.TRACK_POS_SYNC"

    private const val ACTION_API_COMMAND = "com.maxmpz.audioplayer.API_COMMAND"
    private const val API_RECEIVER_NAME = "com.maxmpz.audioplayer.player.PowerampAPIReceiver"
    private const val EXTRA_COMMAND = "cmd"
    private const val TAG = "PowerampApi"

    const val COMMAND_TOGGLE_PLAY_PAUSE = 1
    const val COMMAND_NEXT = 4
    const val COMMAND_PREVIOUS = 5

    const val EXTRA_TRACK = "track"
    const val EXTRA_STATE = "state"
    const val EXTRA_PAUSED = "paused"
    const val EXTRA_POSITION = "pos"

    const val STATE_NO_STATE = -1
    const val STATE_STOPPED = 0
    const val STATE_PLAYING = 1

    const val KEY_TITLE = "title"
    const val KEY_ARTIST = "artist"
    const val KEY_ALBUM = "album"
    const val KEY_DURATION = "dur"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (_: Exception) {
        false
    }

    fun sendCommand(context: Context, command: Int) {
        val intent = Intent(ACTION_API_COMMAND)
            .setComponent(ComponentName(PACKAGE, API_RECEIVER_NAME))
            .putExtra(EXTRA_COMMAND, command)
        try {
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send Poweramp command $command", e)
        }
    }
}
