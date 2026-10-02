package app.lawnchair.smartspace.provider

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.getSystemService
import app.lawnchair.BlankActivity
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.util.broadcastReceiverFlow
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Shows the battery of connected Bluetooth devices as its own smartspace card. Earbuds that report
 * per-bud metadata (left, right, case) show all three, anything else shows a single percentage.
 */
class BluetoothBatteryProvider(context: Context) : SmartspaceDataSource(
    context,
    R.string.smartspace_bluetooth_battery,
    { smartspaceBluetoothBattery },
) {
    private val bluetoothManager = context.getSystemService<BluetoothManager>()

    override val isAvailable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)

    override val internalTargets = merge(
        broadcastReceiverFlow(
            context,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(ACTION_BATTERY_LEVEL_CHANGED)
            },
        ).map { },
        // Per-bud values don't always come with a broadcast, so also poll while the card is shown.
        flow {
            while (true) {
                emit(Unit)
                delay(POLL_INTERVAL_MS)
            }
        },
    )
        .map { readDevices() }
        .distinctUntilChanged()
        .map { devices -> devices.map(::toTarget) }
        .flowOn(Dispatchers.IO)

    private fun readDevices(): List<DeviceBattery> {
        if (!hasPermission()) return emptyList()
        return try {
            val adapter = bluetoothManager?.adapter ?: return emptyList()
            if (!adapter.isEnabled) return emptyList()
            adapter.bondedDevices.orEmpty()
                .filter { it.isConnectedCompat() }
                .mapNotNull { it.readBattery() }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    private fun BluetoothDevice.readBattery(): DeviceBattery? {
        val left = metadataPercent(METADATA_LEFT_BATTERY)
        val right = metadataPercent(METADATA_RIGHT_BATTERY)
        val case = metadataPercent(METADATA_CASE_BATTERY)
        val main = batteryLevelCompat().takeIf { it in 0..100 }
        if (left == null && right == null && case == null && main == null) return null
        return DeviceBattery(
            address = address,
            name = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) alias else null) ?: name ?: address,
            left = left,
            right = right,
            case = case,
            main = main,
        )
    }

    private fun toTarget(device: DeviceBattery): SmartspaceTarget {
        val parts = buildList {
            device.left?.let { add(context.getString(R.string.bluetooth_battery_left, it)) }
            device.right?.let { add(context.getString(R.string.bluetooth_battery_right, it)) }
            device.case?.let { add(context.getString(R.string.bluetooth_battery_case, it)) }
            if (isEmpty()) device.main?.let { add(context.getString(R.string.n_percent, it)) }
        }
        return SmartspaceTarget(
            id = "bluetoothBattery-${device.address}",
            headerAction = SmartspaceAction(
                id = "bluetoothBatteryAction-${device.address}",
                icon = Icon.createWithResource(context, R.drawable.ic_headphones),
                title = device.name,
                subtitle = parts.joinToString(" · "),
            ),
            score = SmartspaceScores.SCORE_BATTERY,
            featureType = SmartspaceTarget.FeatureType.FEATURE_CALENDAR,
        )
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    override suspend fun requiresSetup(): Boolean = !hasPermission()

    override suspend fun startSetup(activity: Activity) {
        // The system permission dialog, built by the platform so it targets the right package.
        val request = runCatching {
            PackageManager::class.java
                .getMethod("buildRequestPermissionsIntent", Array<String>::class.java)
                .invoke(activity.packageManager, arrayOf(Manifest.permission.BLUETOOTH_CONNECT)) as Intent
        }.getOrNull()
        val intent = request
            ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
        BlankActivity.startBlankActivityForResult(activity, intent)
    }

    private data class DeviceBattery(
        val address: String,
        val name: String,
        val left: Int?,
        val right: Int?,
        val case: Int?,
        val main: Int?,
    )

    companion object {
        private const val POLL_INTERVAL_MS = 60_000L

        private const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"

        // BluetoothDevice.METADATA_UNTETHERED_*_BATTERY (hidden API constants).
        private const val METADATA_LEFT_BATTERY = 10
        private const val METADATA_RIGHT_BATTERY = 11
        private const val METADATA_CASE_BATTERY = 12

        // The methods below are hidden APIs; reflection works because the app unseals them at startup.
        private fun BluetoothDevice.isConnectedCompat(): Boolean = runCatching {
            BluetoothDevice::class.java.getMethod("isConnected").invoke(this) as Boolean
        }.getOrDefault(false)

        private fun BluetoothDevice.batteryLevelCompat(): Int = runCatching {
            BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(this) as Int
        }.getOrDefault(-1)

        private fun BluetoothDevice.metadataPercent(key: Int): Int? = runCatching {
            val bytes = BluetoothDevice::class.java
                .getMethod("getMetadata", Int::class.javaPrimitiveType)
                .invoke(this, key) as? ByteArray
            bytes?.let { String(it).trim().toIntOrNull() }?.takeIf { it in 0..100 }
        }.getOrNull()
    }
}
