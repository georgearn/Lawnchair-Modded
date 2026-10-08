package app.lawnchair.smartspace.provider

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.BatteryManager
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.util.broadcastReceiverFlow
import com.android.launcher3.R
import kotlinx.coroutines.flow.map

class BatteryStatusProvider(context: Context) : SmartspaceDataSource(
    context,
    R.string.smartspace_battery_status,
    { smartspaceBatteryStatus },
) {
    override val internalTargets = broadcastReceiverFlow(context, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        .map { intent ->
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
            val full = status == BatteryManager.BATTERY_STATUS_FULL
            val level = (
                100f *
                    intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) /
                    intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                ).toInt()
            listOfNotNull(getSmartspaceTarget(charging, full, level))
        }

    // Charging state is already shown under the clock, so only a low (not charging) battery gets a card.
    private fun getSmartspaceTarget(charging: Boolean, full: Boolean, level: Int): SmartspaceTarget? {
        if (charging || full || level > 15) return null
        return SmartspaceTarget(
            id = "batteryStatus",
            headerAction = SmartspaceAction(
                id = "batteryStatusAction",
                icon = Icon.createWithResource(context, R.drawable.ic_battery_low),
                title = context.getString(R.string.smartspace_battery_low),
                subtitle = context.getString(R.string.n_percent, level),
            ),
            score = SmartspaceScores.SCORE_LOW_BATTERY,
            featureType = SmartspaceTarget.FeatureType.FEATURE_CALENDAR,
        )
    }
}
