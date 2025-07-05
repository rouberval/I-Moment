package com.example.moment

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.moment.adapter.DiscoveredDeviceAdapter
import com.example.moment.ble.BleAdvertiser
import com.example.moment.ble.BleScanner
import com.example.moment.ble.DiscoveredDevice
import com.example.moment.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val profileViewModel: ProfileViewModel by viewModels()
    private val TAG = "MainActivity"
    private var bleAdvertiser: BleAdvertiser? = null
    private var bleScanner: BleScanner? = null

    // Using LiveData or a ViewModel would be better for managing this list in a complex app
    private val discoveredDevicesList = mutableListOf<DiscoveredDevice>()
    private lateinit var deviceAdapter: DiscoveredDeviceAdapter


    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val requestBluetoothEnable =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "Bluetooth enabled by user.")
                // Proceed with Bluetooth operations
            } else {
                Log.d(TAG, "Bluetooth not enabled by user.")
                // Handle Bluetooth not enabled (e.g., show a message or disable Bluetooth features)
            }
        }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d(TAG, "${it.key} = ${it.value}")
            }
            if (permissions.values.all { it }) {
                Log.d(TAG, "All required permissions granted.")
                checkAndEnableBluetooth()
            } else {
                Log.d(TAG, "Not all permissions granted.")
                // Handle permissions not granted (e.g., show a message or disable features)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e(TAG, "Bluetooth LE not supported on this device.")
            // Handle device not supporting BLE (e.g., show an error message and close the app)
            finish()
            return
        }

        Log.d(TAG, "Checking permissions...")
        checkAndRequestPermissions()

        setupRecyclerView()

        binding.buttonOpenProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        profileViewModel.name.observe(this) { name ->
            if (name.isNotEmpty()) {
                binding.textViewStatus.text = "Hi, $name! Welcome to Moment!"
                initBleFeatures() // Initialize (or re-initialize) BLE features
            } else {
                binding.textViewStatus.text = "Welcome to Moment! Please set up your profile."
                bleAdvertiser?.stopAdvertising()
                stopBleScanAndClearList()
            }
        }
    }

    private fun setupRecyclerView() {
        deviceAdapter = DiscoveredDeviceAdapter { device ->
            // Handle item click - e.g., show a Toast for now
            Toast.makeText(this, "Clicked on ${device.advertisedName ?: device.deviceAddress}", Toast.LENGTH_SHORT).show()
            // Later, this could navigate to a chat screen or profile view
        }
        binding.recyclerViewDiscoveredDevices.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
        }
    }


    private fun hasRequiredPermissions(isForAdvertising: Boolean): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isForAdvertising) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED // connect often needed with advertise logic
            } else { // For Scanning
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED && // for names
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED // Still good for S+ for better results
            }
        } else { // Pre-Android 12
            if (isForAdvertising) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
            } else { // For Scanning
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
        }
    }


    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
             // For S+, ACCESS_FINE_LOCATION is also needed for getting scan results with device info
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        } else { // Pre-Android 12
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            // BLUETOOTH and BLUETOOTH_ADMIN are typically granted at install time for older versions if declared in manifest
            // No need to explicitly add BLUETOOTH and BLUETOOTH_ADMIN here as they are normal permissions for < S
        }

        if (requiredPermissions.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${requiredPermissions.joinToString()}")
            requestPermissionsLauncher.launch(requiredPermissions.toTypedArray())
        } else {
            Log.d(TAG, "All necessary permissions already granted.")
            checkAndEnableBluetooth() // This will subsequently try to start advertising/scanning
        }
    }

    private fun checkAndEnableBluetooth() {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported on this device.")
            finish()
            return
        }

        if (bluetoothAdapter?.isEnabled == false) {
            Log.d(TAG, "Bluetooth is disabled. Requesting user to enable it.")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            // BLUETOOTH_CONNECT is needed to *request* enabling Bluetooth programmatically on S+
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                requestBluetoothEnable.launch(enableBtIntent)
            } else {
                 Log.e(TAG, "BLUETOOTH_CONNECT permission not granted, cannot request enable Bluetooth. User needs to enable it manually or grant permission.")
                 // Consider guiding user to settings or re-requesting BLUETOOTH_CONNECT
            }
        } else {
            Log.d(TAG, "Bluetooth is already enabled.")
            initBleFeatures()
        }
    }

    private fun initBleFeatures() {
        Log.d(TAG, "Initializing BLE features...")
        if (profileViewModel.name.value?.isNotEmpty() == true) {
            if (hasRequiredPermissions(isForAdvertising = true)) {
                if (bleAdvertiser == null) bleAdvertiser = BleAdvertiser(applicationContext, bluetoothAdapter)
                bleAdvertiser?.startAdvertising(profileViewModel.name.value)
            } else {
                Log.w(TAG, "Cannot start advertising, missing permissions.")
            }

            if (hasRequiredPermissions(isForAdvertising = false)) {
                if (bleScanner == null) bleScanner = BleScanner(applicationContext, bluetoothAdapter, BleAdvertiser.SERVICE_UUID)
                startBleScan()
            } else {
                Log.w(TAG, "Cannot start scanning, missing permissions.")
            }
        } else {
            Log.i(TAG, "Profile name is empty. Advertising and scanning will not start yet.")
        }
    }


    private fun startBleScan() {
        if (bleScanner?.isScanning() == true) {
            Log.d(TAG, "Scanner already running.")
            return
        }
        if (!hasRequiredPermissions(isForAdvertising = false)) {
            Log.e(TAG, "Cannot start scan, missing permissions.")
            // Optionally, trigger permission request again or inform user
            // checkAndRequestPermissions()
            return
        }
        Log.d(TAG, "Attempting to start BLE scan.")
        discoveredDevicesList.clear()
        deviceAdapter.submitList(emptyList()) // Clear adapter
        updateScanStatusUI(true)

        bleScanner?.startScan(
            onDeviceDiscovered = { device ->
                runOnUiThread { // Ensure UI updates are on the main thread
                    val existingDeviceIndex = discoveredDevicesList.indexOfFirst { it.deviceAddress == device.deviceAddress }
                    if (existingDeviceIndex != -1) {
                        // Device already exists, update it (e.g. RSSI changed)
                        discoveredDevicesList[existingDeviceIndex] = device
                    } else {
                        // New device
                        discoveredDevicesList.add(device)
                    }
                    // Sort by RSSI (strongest signal first) or name, as preferred
                    // discoveredDevicesList.sortByDescending { it.rssi }
                    deviceAdapter.submitList(discoveredDevicesList.toList()) // Submit a new list to DiffUtil
                }
            },
            onScanStateChanged = { isScanning ->
                runOnUiThread {
                    Log.d(TAG, "Scan state changed: ${if(isScanning) "Scanning" else "Stopped"}")
                    updateScanStatusUI(isScanning)
                    if (!isScanning && discoveredDevicesList.isEmpty()) {
                        binding.textViewStatus.append("\nNo devices found.")
                    }
                }
            },
            onScanFailed = { errorCode ->
                runOnUiThread {
                    Log.e(TAG, "Scan failed with code: $errorCode")
                    updateScanStatusUI(false)
                    binding.textViewStatus.append("\nScan failed: $errorCode")
                }
            }
        )
    }

    private fun stopBleScanAndClearList() {
        Log.d(TAG, "Attempting to stop BLE scan and clear list.")
        bleScanner?.stopScan() // This will trigger onScanStateChanged(false)
        discoveredDevicesList.clear()
        deviceAdapter.submitList(emptyList())
        updateScanStatusUI(false)
    }

    private fun updateScanStatusUI(isScanning: Boolean) {
        // This is a placeholder. You might have a dedicated TextView or ProgressBar for scan status.
        if (isScanning) {
            if (!binding.textViewStatus.text.contains("Scanning...")) {
                 binding.textViewStatus.append("\nScanning...")
            }
        } else {
            binding.textViewStatus.text = binding.textViewStatus.text.toString().replace("\nScanning...", "")
            if (profileViewModel.name.value?.isNotEmpty() == true && discoveredDevicesList.isEmpty()){
                 // binding.textViewStatus.append("\nScan stopped. No devices found yet.")
            } else if (profileViewModel.name.value?.isNotEmpty() == true && discoveredDevicesList.isNotEmpty()){
                 // binding.textViewStatus.append("\nScan stopped.")
            }
        }
    }


    override fun onPause() {
        super.onPause()
        // Consider stopping scan when app is paused to save battery, unless background scanning is a feature
        // if (hasRequiredPermissions(isForAdvertising = false)) { // Check permissions before trying to stop
        // stopBleScan()
        // }
    }

    override fun onResume() {
        super.onResume()
        // If returning to app and profile is set, try to start scanning again
        // if (profileViewModel.name.value?.isNotEmpty() == true && bluetoothAdapter?.isEnabled == true) {
        //    initBleFeatures() // This will check permissions and start scan/advertise if appropriate
        // }
    }


    override fun onDestroy() {
        super.onDestroy()
        bleAdvertiser?.stopAdvertising() // No permission check needed here as it's cleanup
        bleScanner?.stopScan()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult called (should be handled by launchers mainly)")
        // Potentially re-check and initialize features if permissions were granted here
        // This is more of a fallback if launchers don't cover all scenarios or for older code.
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Log.d(TAG, "Permissions granted via onRequestPermissionsResult, re-initializing.")
            checkAndEnableBluetooth()
        }
    }
}
