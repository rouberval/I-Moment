package com.example.moment.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import java.nio.charset.Charset
import java.util.UUID

class BleAdvertiser(private val context: Context, private val bluetoothAdapter: BluetoothAdapter?) {

    private val TAG = "BleAdvertiser"
    private var isAdvertising = false

    // Define a unique UUID for your application's service
    // Moved to a companion object to be accessible by BleScanner and other components
    // You can generate one from https://www.uuidgenerator.net/
    // private val serviceUuid: ParcelUuid = ParcelUuid.fromString("00001101-0000-1000-8000-00805F9B34FB") // Example UUID

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            isAdvertising = true
            Log.i(TAG, "BLE Advertising started successfully. Settings: $settingsInEffect")
            // You could update UI or state here
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            isAdvertising = false
            Log.e(TAG, "BLE Advertising onStartFailure: $errorCode")
            handleAdvertiseError(errorCode)
            // You could update UI or state here, and potentially try to restart advertising
        }
    }

    fun startAdvertising(deviceName: String?) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled or not available.")
            return
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_ADVERTISE permission not granted.")
            // This should ideally be handled by requesting permission before calling this method
            return
        }

        if (isAdvertising) {
            Log.d(TAG, "Already advertising.")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // Or other modes like ADVERTISE_MODE_LOW_POWER
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM) // Or other power levels
            .setConnectable(true) // Set to true if you want devices to connect
            .setTimeout(0) // 0 for continuous advertising, or a duration in milliseconds
            .build()

        val advertiseDataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // Set to true to include device name, false to save space for service data
            .addServiceUuid(SERVICE_UUID) // Include your app's unique service UUID

        // Optionally, add manufacturer specific data or service data
        // For example, to send a short piece of the user's name or a temporary ID
        // Keep this data small, as advertising packets have limited space (around 20-30 bytes payload)
        if (!deviceName.isNullOrEmpty()) {
            val shortName = if (deviceName.length > 8) deviceName.substring(0, 8) else deviceName // Example: Max 8 chars
            // Using Service Data to send custom information
            // SERVICE_UUID is used to identify the service data payload
             advertiseDataBuilder.addServiceData(SERVICE_UUID, shortName.toByteArray(Charset.forName("UTF-8")))
        }


        val advertiseData = advertiseDataBuilder.build()

        // Some devices might have issues with advertising if the data is too large,
        // especially if including both device name and service data.
        // Android might not start advertising or might truncate data.
        // It's good practice to check the size of the data.

        Log.d(TAG, "Starting BLE Advertising...")
        bluetoothAdapter.bluetoothLeAdvertiser?.startAdvertising(settings, advertiseData, advertiseCallback)
            ?: Log.e(TAG, "Failed to get BluetoothLeAdvertiser.")
    }

    fun stopAdvertising() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled or not available for stopping advertising.")
            return
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_ADVERTISE permission not granted for stopping.")
            return
        }

        if (isAdvertising && bluetoothAdapter.bluetoothLeAdvertiser != null) {
            Log.d(TAG, "Stopping BLE Advertising...")
            bluetoothAdapter.bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
            isAdvertising = false
        } else {
            Log.d(TAG, "Not currently advertising or advertiser not available.")
        }
    }

    private fun handleAdvertiseError(errorCode: Int) {
        when (errorCode) {
            AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> Log.e(TAG, "Advertising failed: Data too large.")
            AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> Log.e(TAG, "Advertising failed: Too many advertisers.")
            AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> Log.e(TAG, "Advertising failed: Already started.")
            AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> Log.e(TAG, "Advertising failed: Internal error.")
            AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> Log.e(TAG, "Advertising failed: Feature unsupported.")
            else -> Log.e(TAG, "Advertising failed with unknown error code: $errorCode")
        }
    }

    fun isAdvertising(): Boolean = isAdvertising

    companion object {
        // Moved SERVICE_UUID here to be accessible by BleScanner and other components
        val SERVICE_UUID: ParcelUuid = ParcelUuid.fromString("00002d41-0000-1000-8000-00805f9b34fb") // Custom UUID for Moment app
    }
}
