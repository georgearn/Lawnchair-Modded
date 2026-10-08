package app.lawnchair.smartspace.provider

import android.content.Context
import app.lawnchair.smartspace.model.SmartspaceTarget
import com.android.launcher3.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Battery status is shown under the clock on the date card (see BcSmartspaceCard), so this source
 * has no card of its own. It only exists to provide the toggle for that row.
 */
class BatteryStatusProvider(context: Context) : SmartspaceDataSource(
    context,
    R.string.smartspace_battery_status,
    { smartspaceBatteryStatus },
) {
    override val internalTargets: Flow<List<SmartspaceTarget>> = flowOf(emptyList())
}
