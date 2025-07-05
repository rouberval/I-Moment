package com.example.moment.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import java.nio.charset.Charset

data class DiscoveredDevice(
    val deviceAddress: String,
    val deviceName: String?, // From scan record or device itself
    val rssi: Int,
    val advertisedName: String? // Custom name from service data
)

class BleScanner(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val serviceUuid: ParcelUuid // The same UUID used for advertising
) {
    private val TAG = "BleScanner"
    private var bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())

    // Stops scanning after 10 seconds.
    private val SCAN_PERIOD: Long = 10000 // 10 seconds, adjust as needed

    private var onDeviceDiscovered: ((DiscoveredDevice) -> Unit)? = null
    private var onScanStateChanged: ((Boolean) -> Unit)? = null
    private var onScanFailed: ((Int) -> Unit)? = null

    private val discoveredDevicesMap = mutableMapOf<String, DiscoveredDevice>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let { device ->
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT // Needed for device.name on Android 12+
                    ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ) {
                    // If permission not granted, we might not get device name
                    // Log.w(TAG, "BLUETOOTH_CONNECT permission not granted, device name might be null for ${device.address}")
                }

                val scanRecord = result.scanRecord
                var advertisedNameFromServiceData: String? = null

                scanRecord?.serviceData?.get(serviceUuid)?.let { serviceDataBytes ->
                    advertisedNameFromServiceData = String(serviceDataBytes, Charset.forName("UTF-8"))
                }

                // Only add/update if we get our specific service data, or if no specific service data is expected
                if (advertisedNameFromServiceData != null) {
                     val discoveredDevice = DiscoveredDevice(
                        deviceAddress = device.address,
                        deviceName = device.name ?: "Unknown Device",
                        rssi = result.rssi,
                        advertisedName = advertisedNameFromServiceData
                    )
                    // Use device address as key to update if already seen, or add if new
                    if (!discoveredDevicesMap.containsKey(device.address) || discoveredDevicesMap[device.address]?.rssi != result.rssi) {
                        discoveredDevicesMap[device.address] = discoveredDevice
                        onDeviceDiscovered?.invoke(discoveredDevice)
                        Log.d(TAG, "Device discovered: $discoveredDevice")
                    }
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            results?.forEach { result ->
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result) // Process each result
            }
            Log.d(TAG, "Batch scan results received, size: ${results?.size}")
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "BLE Scan Failed with error code: $errorCode")
            isScanning = false
            onScanStateChanged?.invoke(false)
            onScanFailed?.invoke(errorCode)
            handleScanError(errorCode)
        }
    }

    fun startScan(
        onDeviceDiscovered: (DiscoveredDevice) -> Unit,
        onScanStateChanged: (Boolean) -> Unit,
        onScanFailed: (Int) -> Unit
    ) {
        this.onDeviceDiscovered = onDeviceDiscovered
        this.onScanStateChanged = onScanStateChanged
        this.onScanFailed = onScanFailed

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled || bluetoothLeScanner == null) {
            Log.e(TAG, "Bluetooth is not enabled or BLE Scanner not available.")
            this.onScanFailed.invoke(ScanCallback.SCAN_FAILED_INTERNAL_ERROR) // Or a custom error code
            return
        }

        val requiredPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            // BLUETOOTH_CONNECT is needed for device.name, but scanning itself doesn't strictly require it if you don't access device.name
            // However, it's good practice to ensure it if you intend to use the name.
             if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                Log.w(TAG, "BLUETOOTH_CONNECT not granted, device names might be missing.")
            }
        } else { // Pre-Android 12
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (requiredPermissions.any { ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
            Log.e(TAG, "Required scanning permissions not granted: $requiredPermissions")
            this.onScanFailed.invoke(ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED) // Example error
            return
        }


        if (isScanning) {
            Log.d(TAG, "Scan already in progress.")
            return
        }

        // Clear previously discovered devices for a fresh scan session
        discoveredDevicesMap.clear()

        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(serviceUuid) // Filter for devices advertising our specific service UUID
                .build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Use low latency for active discovery
            // .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // Report all advertisements
            // .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            // .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0) // Report results immediately
            .build()

        bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
        isScanning = true
        this.onScanStateChanged.invoke(true)
        Log.d(TAG, "BLE Scan started.")

        // Stops scanning after a pre-defined scan period.
        // handler.postDelayed({
        // if (isScanning) {
        // stopScan()
        // Log.d(TAG, "Scan stopped by timeout.")
        // }
        // }, SCAN_PERIOD)
    }

    fun stopScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled || bluetoothLeScanner == null) {
            Log.e(TAG, "Bluetooth is not enabled or BLE Scanner not available for stopping scan.")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted for stopping scan.")
            return
        }


        if (isScanning) {
            bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            this.onScanStateChanged.invoke(false)
            Log.d(TAG, "BLE Scan stopped.")
        } else {
            Log.d(TAG, "Scan not running or already stopped.")
        }
    }

    private fun handleScanError(errorCode: Int) {
        when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> Log.e(TAG, "Scan failed: Already started.")
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> Log.e(TAG, "Scan failed: App registration failed.")
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> Log.e(TAG, "Scan failed: Internal error.")
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> Log.e(TAG, "Scan failed: Feature unsupported.")
            // ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> Log.e(TAG, "Scan failed: Out of hardware resources.") (API 26+)
            // ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> Log.e(TAG, "Scan failed: Scanning too frequently.") (API 26+)
            else -> Log.e(TAG, "Scan failed with unknown error code: $errorCode")
        }
    }

    fun isScanning(): Boolean = isScanning
}
