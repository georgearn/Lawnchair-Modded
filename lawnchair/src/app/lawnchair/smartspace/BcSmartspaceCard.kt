package app.lawnchair.smartspace

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.BatteryManager
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.layoutDirection
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.smartspace.model.hasIntent
import app.lawnchair.util.broadcastReceiverFlow
import app.lawnchair.util.repeatOnAttached
import com.android.launcher3.R
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.combine

class BcSmartspaceCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val prefs2 = PreferenceManager2.getInstance(context)
    private var baseActionIconSubtitleView: DoubleShadowTextView? = null
    private var batteryIconView: ImageView? = null
    private var batteryTextView: TextView? = null
    private var subtitleGroup: View? = null
    private var batteryShown = false
    private var subtitleEmpty = false
    private var dateView: IcuDateTextView? = null
    private var dndImageView: ImageView? = null
    private var extrasGroup: ViewGroup? = null
    private var iconDrawable: DoubleShadowIconDrawable? = null
    private var iconTintColor = 0
    private var nextAlarmImageView: ImageView? = null
    private var nextAlarmTextView: TextView? = null
    private var subtitleTextView: TextView? = null
    private lateinit var target: SmartspaceTarget
    private var titleTextView: TextView? = null
    private var mediaControlsGroup: View? = null
    private var mediaPreviousButton: ImageButton? = null
    private var mediaPlayPauseButton: ImageButton? = null
    private var mediaNextButton: ImageButton? = null
    private var topPadding = 0
    private var usePageIndicatorUi = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        dateView = findViewById(R.id.date)
        titleTextView = findViewById(R.id.title_text)
        subtitleTextView = findViewById(R.id.subtitle_text)
        baseActionIconSubtitleView = findViewById(R.id.base_action_icon_subtitle)
        extrasGroup = findViewById(R.id.smartspace_extras_group)
        subtitleGroup = findViewById(R.id.smartspace_subtitle_group)
        mediaControlsGroup = findViewById(R.id.media_controls)
        mediaPreviousButton = findViewById(R.id.media_previous)
        mediaPlayPauseButton = findViewById(R.id.media_play_pause)
        mediaNextButton = findViewById(R.id.media_next)
        topPadding = paddingTop
        extrasGroup?.let {
            dndImageView = it.findViewById(R.id.dnd_icon)
            nextAlarmImageView = it.findViewById(R.id.alarm_icon)
            nextAlarmTextView = it.findViewById(R.id.alarm_text)
            batteryIconView = it.findViewById(R.id.battery_icon)
            batteryTextView = it.findViewById(R.id.battery_text)
        }
    }

    init {
        // Only the date card (the one with the clock and date) shows the battery row.
        repeatOnAttached {
            if (dateView == null || batteryIconView == null || batteryTextView == null) return@repeatOnAttached
            combine(
                prefs2.smartspaceBatteryStatus.get(),
                broadcastReceiverFlow(context, IntentFilter(Intent.ACTION_BATTERY_CHANGED)),
            ) { enabled, intent -> if (enabled) intent else null }
                .collect { updateBatteryStatus(it) }
        }
    }

    // With no weather line, close the empty subtitle gap so the battery sits right under the date.
    private fun refreshSubtitleGroup() {
        subtitleGroup?.isGone = dateView != null && batteryShown && subtitleEmpty
    }

    private fun updateBatteryStatus(intent: Intent?) {
        val iconView = batteryIconView ?: return
        val textView = batteryTextView ?: return
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (intent == null || level < 0 || scale <= 0) {
            iconView.isVisible = false
            textView.isVisible = false
            extrasGroup?.isInvisible = true
            batteryShown = false
            refreshSubtitleGroup()
            return
        }
        val percent = context.getString(R.string.n_percent, level * 100 / scale)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val full = status == BatteryManager.BATTERY_STATUS_FULL
        val text = when {
            charging -> context.getString(
                R.string.smartspace_battery_status_with_state,
                percent,
                context.getString(R.string.smartspace_battery_charging),
            )
            full -> context.getString(
                R.string.smartspace_battery_status_with_state,
                percent,
                context.getString(R.string.smartspace_battery_full),
            )
            else -> percent
        }
        iconView.setImageResource(
            if (charging) R.drawable.ic_battery_status_charging else R.drawable.ic_battery_status,
        )
        textView.text = text
        textView.contentDescription = text
        iconView.isVisible = true
        textView.isVisible = true
        extrasGroup?.isVisible = true
        batteryShown = true
        refreshSubtitleGroup()
    }

    fun setSmartspaceTarget(target: SmartspaceTarget, multipleCards: Boolean) {
        this.target = target
        val headerAction = target.headerAction
        val baseAction = target.baseAction
        usePageIndicatorUi = multipleCards

        if (headerAction != null) {
            iconDrawable = BcSmartSpaceUtil.getIconDrawable(headerAction.icon, context)
                ?.let { DoubleShadowIconDrawable(it, context) }

            var title: CharSequence? = headerAction.title
            var subtitle = headerAction.subtitle
            val hasTitle = target.featureType == SmartspaceTarget.FeatureType.FEATURE_WEATHER ||
                !title.isNullOrEmpty()
            val hasSubtitle = !subtitle.isNullOrEmpty()
            if (!hasTitle) {
                title = subtitle
            }
            val contentDescription = headerAction.contentDescription
            setTitle(title, contentDescription, hasTitle != hasSubtitle)
            if (!hasTitle || !hasSubtitle) {
                subtitle = null
            }
            setSubtitle(subtitle, headerAction.contentDescription)
            updateIconTint()
        }

        subtitleEmpty = subtitleTextView?.text.isNullOrEmpty() &&
            baseActionIconSubtitleView?.text.isNullOrEmpty() && baseAction == null
        refreshSubtitleGroup()

        if (baseAction != null && baseActionIconSubtitleView != null) {
            val icon = BcSmartSpaceUtil.getIconDrawable(baseAction.icon, context)
                ?.let { DoubleShadowIconDrawable(it, context) }
            val iconView = baseActionIconSubtitleView!!
            if (icon != null) {
                icon.setTintList(null)
                iconView.text = baseAction.subtitle
                iconView.setCompoundDrawablesRelative(icon, null, null, null)
                iconView.isVisible = true
                BcSmartSpaceUtil.setOnClickListener(iconView, baseAction, null, "BcSmartspaceCard")
                setFormattedContentDescription(iconView, baseAction.subtitle, baseAction.contentDescription)
            } else {
                iconView.isInvisible = true
                iconView.setOnClickListener(null)
                iconView.contentDescription = null
            }
        }

        updateMediaControls(target.mediaControls)

        dateView?.let {
            val calendarAction = SmartspaceAction(
                id = headerAction?.id ?: baseAction?.id ?: UUID.randomUUID().toString(),
                title = "unusedTitle",
                intent = BcSmartSpaceUtil.getOpenCalendarIntent(),
            )
            BcSmartSpaceUtil.setOnClickListener(it, calendarAction, null, "BcSmartspaceCard")
        }

        when {
            headerAction.hasIntent -> {
                BcSmartSpaceUtil.setOnClickListener(this, headerAction, null, "BcSmartspaceCard")
            }
            baseAction.hasIntent -> {
                BcSmartSpaceUtil.setOnClickListener(this, baseAction, null, "BcSmartspaceCard")
            }
            else -> {
                BcSmartSpaceUtil.setOnClickListener(this, headerAction, null, "BcSmartspaceCard")
            }
        }
    }

    private fun updateMediaControls(controls: SmartspaceTarget.MediaControls?) {
        val group = mediaControlsGroup ?: return
        group.isVisible = controls != null
        if (controls == null) return
        mediaPlayPauseButton?.setImageResource(
            if (controls.isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play,
        )
        mediaPreviousButton?.setOnClickListener { controls.onPrevious.run() }
        mediaPlayPauseButton?.setOnClickListener { controls.onPlayPause.run() }
        mediaNextButton?.setOnClickListener { controls.onNext.run() }
        updateMediaControlsTint()
    }

    private fun updateMediaControlsTint() {
        val tint = ColorStateList.valueOf(iconTintColor)
        mediaPreviousButton?.imageTintList = tint
        mediaPlayPauseButton?.imageTintList = tint
        mediaNextButton?.imageTintList = tint
    }

    fun setPrimaryTextColor(textColor: Int) {
        titleTextView?.setTextColor(textColor)
        dateView?.setTextColor(textColor)
        subtitleTextView?.setTextColor(textColor)
        baseActionIconSubtitleView?.setTextColor(textColor)
        batteryTextView?.setTextColor(textColor)
        batteryIconView?.imageTintList = ColorStateList.valueOf(textColor)
        iconTintColor = textColor
        updateIconTint()
        updateMediaControlsTint()
    }

    fun setTitle(title: CharSequence?, contentDescription: CharSequence?, hasIcon: Boolean) {
        val titleView = titleTextView ?: return
        val isRTL = Locale.getDefault().layoutDirection == View.LAYOUT_DIRECTION_RTL
        titleView.textAlignment = if (isRTL) TEXT_ALIGNMENT_TEXT_END else TEXT_ALIGNMENT_TEXT_START
        titleView.text = title
        titleView.setCompoundDrawablesRelative(
            if (hasIcon) iconDrawable else null,
            null,
            null,
            null,
        )
        titleView.ellipsize = if (target.featureType == SmartspaceTarget.FeatureType.FEATURE_CALENDAR &&
            Locale.ENGLISH.language == context.resources.configuration.locale.language
        ) {
            TextUtils.TruncateAt.MIDDLE
        } else {
            TextUtils.TruncateAt.END
        }
        if (hasIcon) {
            setFormattedContentDescription(titleView, title, contentDescription)
        }
    }

    private fun setSubtitle(subtitle: CharSequence?, charSequence2: CharSequence?) {
        val subtitleView = subtitleTextView ?: return
        subtitleView.text = subtitle
        subtitleTextView!!.setCompoundDrawablesRelative(
            if (subtitle.isNullOrEmpty()) null else iconDrawable,
            null,
            null,
            null,
        )
        subtitleTextView!!.maxLines = if (target.featureType == SmartspaceTarget.FeatureType.FEATURE_TIPS && !usePageIndicatorUi) 2 else 1
        setFormattedContentDescription(subtitleTextView!!, subtitle, charSequence2)
    }

    private fun setFormattedContentDescription(
        textView: TextView,
        title: CharSequence?,
        contentDescription: CharSequence?,
    ) {
        textView.contentDescription = when {
            title.isNullOrEmpty() -> contentDescription
            !contentDescription.isNullOrEmpty() -> context.getString(
                R.string.generic_smartspace_concatenated_desc,
                contentDescription,
                title,
            )
            else -> title
        }
    }

    private fun updateIconTint() {
        val icon = iconDrawable ?: return
        when (target.featureType) {
            SmartspaceTarget.FeatureType.FEATURE_WEATHER -> icon.setTintList(null)
            else -> icon.setTint(iconTintColor)
        }
    }
}
