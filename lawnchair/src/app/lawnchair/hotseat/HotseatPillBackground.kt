package app.lawnchair.hotseat

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.util.repeatOnAttached
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.roundToInt

/**
 * iOS style rounded background drawn behind the hotseat icons. Its bounds follow the
 * icon area of [view], which is the view minus its padding.
 */
private class HotseatPillDrawable(private val view: View) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val horizontalInset = 12 * view.resources.displayMetrics.density
    private val verticalInset = 8 * view.resources.displayMetrics.density

    fun setColor(color: Int) {
        paint.color = color
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        rect.set(
            maxOf(view.paddingLeft - horizontalInset, 0f),
            maxOf(view.paddingTop - verticalInset, 0f),
            minOf(view.width - view.paddingRight + horizontalInset, view.width.toFloat()),
            minOf(view.height - view.paddingBottom + verticalInset, view.height.toFloat()),
        )
        if (rect.isEmpty) return
        // Radius larger than half the height is clamped, giving fully rounded ends.
        val radius = minOf(rect.height() / 2f, 32 * view.resources.displayMetrics.density)
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Shows or hides the pill behind the hotseat icons following the dock background prefs. */
fun bindHotseatPillBackground(view: View) {
    val prefs2 = PreferenceManager2.getInstance(view.context)
    val drawable = HotseatPillDrawable(view)
    view.repeatOnAttached {
        combine(
            prefs2.dockBackground.get(),
            prefs2.dockBackgroundOpacity.get(),
            ::Pair,
        )
            .onEach { (enabled, opacity) ->
                if (!enabled) {
                    view.background = null
                    return@onEach
                }
                // Opposite shade of the text so the pill stays readable on any wallpaper.
                val textColor = Themes.getAttrColor(view.context, R.attr.workspaceTextColor)
                val base = if (ColorUtils.calculateLuminance(textColor) > 0.5) Color.BLACK else Color.WHITE
                drawable.setColor(ColorUtils.setAlphaComponent(base, (opacity.coerceIn(0f, 1f) * 255).roundToInt()))
                view.background = drawable
                view.invalidate()
            }
            .launchIn(this)
    }
}
