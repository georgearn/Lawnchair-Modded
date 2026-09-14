package app.lawnchair.statusbar

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Covers the status bar clock's screen rect with an opaque view matching the current
 * status bar color. Unlike toggling `icon_blacklist` alone, this doesn't depend on how
 * SystemUI's Clock view reacts to that flag (it can end up INVISIBLE instead of GONE
 * across shade open/close, leaving a blank gap) — it just paints over whatever is there.
 */
class ClockOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    private fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun show(color: Int) {
        if (overlayView != null || !canDrawOverlays()) return

        val statusBarHeight = context.resources
            .getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it > 0 }
            ?.let { context.resources.getDimensionPixelSize(it) }
            ?: return

        val view = View(context).apply { setBackgroundColor(color) }
        val params = WindowManager.LayoutParams(
            (CLOCK_COVER_WIDTH_DP * context.resources.displayMetrics.density).toInt(),
            statusBarHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.END }

        runCatching { windowManager.addView(view, params) }
            .onSuccess { overlayView = view }
            .onFailure { Log.w(TAG, "Failed to add clock cover overlay", it) }
    }

    fun hide() {
        val view = overlayView ?: return
        runCatching { windowManager.removeView(view) }
        overlayView = null
    }

    companion object {
        private const val TAG = "ClockOverlay"
        private const val CLOCK_COVER_WIDTH_DP = 56
    }
}
