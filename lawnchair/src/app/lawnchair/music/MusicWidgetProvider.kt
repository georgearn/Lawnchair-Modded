package app.lawnchair.music

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Standalone Poweramp player widget, available from the widget picker.
 * Live state comes from [MusicWidgetController]; buttons arrive here as broadcasts.
 */
class MusicWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        MusicWidgetController.start(context)
    }

    override fun onDisabled(context: Context) {
        MusicWidgetController.stop()
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        MusicWidgetController.start(context)
        MusicWidgetController.refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_COMMAND) {
            val command = intent.getIntExtra(EXTRA_COMMAND, -1)
            if (command >= 0) PowerampApi.sendCommand(context, command)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_COMMAND = "app.lawnchair.music.COMMAND"
        const val EXTRA_COMMAND = "command"

        fun componentName(context: Context) = ComponentName(context, MusicWidgetProvider::class.java)
    }
}
