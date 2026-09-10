package app.lawnchair.smartspace

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import app.lawnchair.LawnchairLauncher
import app.lawnchair.launcher
import app.lawnchair.launcherNullable
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.Routes
import com.android.launcher3.CheckLongPressHelper
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.views.OptionsPopupView

class SmartspaceViewContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private val previewMode: Boolean = false,
) : FrameLayout(context, attrs) {

    private val longPressHelper = CheckLongPressHelper(this) { performLongClick() }
    private val smartspaceView: View

    init {
        val inflater = LayoutInflater.from(context)
        smartspaceView = inflater.inflate(R.layout.smartspace_enhanced, this, false) as BcSmartspaceView
        smartspaceView.previewMode = previewMode
        val ctx = LawnchairLauncher.instance?.launcherNullable
        val dp = ctx?.deviceProfile
        // This container is itself a CellLayout child at column 0, so its own
        // position already accounts for the workspace margin and cell-layout
        // padding (Workspace applies both to the page before this view is ever
        // placed). Only the icon's own within-cell centering inset is missing.
        val leftPadding = dp?.let { (it.cellWidthPx - it.iconSizePx) / 2 }
        smartspaceView.setPadding(leftPadding ?: (left + 16), top, right, bottom)
        setOnLongClickListener {
            openOptions()
            true
        }
        addView(smartspaceView)
    }

    private fun openOptions() {
        if (previewMode) return

        val launcher = context.launcher
        val pos = Rect()
        launcher.dragLayer.getDescendantRectRelativeToSelf(smartspaceView, pos)
        OptionsPopupView.show<LawnchairLauncher>(launcher, RectF(pos), listOf(getCustomizeOption()), true)
    }

    private fun getCustomizeOption() = OptionsPopupView.OptionItem(
        context,
        R.string.action_customize,
        R.drawable.ic_setting,
        StatsLogManager.LauncherEvent.IGNORE,
    ) {
        context.startActivity(PreferenceActivity.createIntent(context, Routes.SMARTSPACE))
        true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        longPressHelper.onTouchEvent(ev)
        return longPressHelper.hasPerformedLongPress()
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        longPressHelper.onTouchEvent(ev)
        return true
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        longPressHelper.cancelLongPress()
    }
}
