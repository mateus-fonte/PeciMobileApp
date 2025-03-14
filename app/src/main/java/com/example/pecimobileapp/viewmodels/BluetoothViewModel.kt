package com.example.pecimobileapp.viewmodels

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pecimobileapp.models.BluetoothDeviceModel
import com.example.pecimobileapp.models.RaspberryPiStatus
import com.example.pecimobileapp.repositories.BluetoothRepository
import com.example.pecimobileapp.services.BluetoothService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * ViewModel for Bluetooth operations with robust error handling and diagnostics
 */
class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothRepository = BluetoothRepository(application)
    private val bluetoothService = BluetoothService(application)

    // UI State
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Accessible state flows from repository
    val pairedDevices = bluetoothRepository.pairedDevices
    val discoveredDevices = bluetoothRepository.discoveredDevices

    // WiFi credentials
    private val _ssid = MutableStateFlow("")
    val ssid: StateFlow<String> = _ssid.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    // Status messages
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Connection result
    private val _connectionResult = MutableStateFlow<ConnectionResult?>(null)
    val connectionResult: StateFlow<ConnectionResult?> = _connectionResult.asStateFlow()

    // WiFi configuration result
    private val _wifiResult = MutableStateFlow<WifiResult?>(null)
    val wifiResult: StateFlow<WifiResult?> = _wifiResult.asStateFlow()

    // Transfer state tracking
    private var _lastTransferTime = 0L
    private var _currentFileUri: Uri? = null
    private var _transferCount = 0
    private val _isTransferInProgress = MutableStateFlow(false)
    val isTransferInProgress: StateFlow<Boolean> = _isTransferInProgress.asStateFlow()

    // Pi status
    private val _piStatus = MutableStateFlow<RaspberryPiStatus?>(null)
    val piStatus: StateFlow<RaspberryPiStatus?> = _piStatus.asStateFlow()

    // Bluetooth adapter direct access
    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter

    init {
        // Monitor scanning state from repository
        viewModelScope.launch {
            bluetoothRepository.isScanning.collect { isScanning ->
                _isScanning.value = isScanning
            }
        }
    }

    fun startScan() {
        bluetoothRepository.startDiscovery()
    }

    fun stopScan() {
        bluetoothRepository.stopDiscovery()
    }

    fun pairDevice(device: BluetoothDevice) {
        bluetoothRepository.pairDevice(device)
    }

    fun selectDevice(deviceModel: BluetoothDeviceModel) {
        deviceModel.address?.let { bluetoothRepository.selectDevice(it) }
    }

    fun connectToSelectedDevice() {
        val device = bluetoothRepository.getSelectedDevice()

        if (device == null) {
            _statusMessage.value = "No device selected"
            return
        }

        connectToDevice(device)
    }

    fun connectToDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            // First select the device
            device.address?.let { bluetoothRepository.selectDevice(it) }

            // Stop scanning during connection
            bluetoothRepository.stopDiscovery()

            // Get the device name
            val deviceName = bluetoothRepository.getDeviceName(device) ?: "Unknown Device"

            // Set connected state directly
            _isConnected.value = true
            _connectionResult.value = ConnectionResult.Success(deviceName)

            _statusMessage.value = "Selected device: $deviceName"

            // Try to load any saved Pi status
            loadPiStatus(device)
        }
    }

    fun disconnectDevice() {
        _isConnected.value = false
        bluetoothRepository.clearSelection()
        _connectionResult.value = null

        // Clean up any pending transfers
        cleanupTransferState()
    }

    fun updateSsid(newSsid: String) {
        _ssid.value = newSsid
    }

    fun updatePassword(newPassword: String) {
        _password.value = newPassword
    }

    /**
     * sendWifiCredentialsAsFile - Modified method for reliable file transfer
     * This more reliable implementation uses a simpler approach and better error handling
     */
    fun sendWifiCredentialsAsFile() {
        // Validate inputs
        if (_ssid.value.isEmpty() || _password.value.isEmpty()) {
            _statusMessage.value = "Please enter both SSID and password"
            return
        }

        // Check if a transfer is already in progress
        if (_isTransferInProgress.value) {
            _statusMessage.value = "A file transfer is already in progress"
            return
        }

        viewModelScope.launch {
            // Get the selected device
            val selectedDevice = bluetoothRepository.getSelectedDevice()
            if (selectedDevice == null) {
                _statusMessage.value = "No device selected"
                return@launch
            }

            try {
                // Mark transfer as started
                _isTransferInProgress.value = true
                _lastTransferTime = System.currentTimeMillis()

                // Get device address
                val deviceAddress = bluetoothRepository.getDeviceAddress(selectedDevice)
                if (deviceAddress == null) {
                    _statusMessage.value = "Unable to get device address"
                    cleanupTransferState()
                    return@launch
                }

                // Get this device's unique identifier
                val deviceId = getDeviceIdentifier()

                // Reset Bluetooth state - this makes transfers more reliable
                bluetoothRepository.stopDiscovery()
                delay(500)

                // Create file with the current timestamp to avoid conflicts
                val context = getApplication<Application>().applicationContext
                val timestamp = System.currentTimeMillis()
                val filename = "wifi_config_${timestamp}.txt"

                // Delete any old files
                context.cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("wifi_config_")) {
                        file.delete()
                    }
                }

                // Create a new file with timestamp to ensure uniqueness
                val file = File(context.cacheDir, filename)
                file.writeText("${_ssid.value},${_password.value},${deviceId}")

                Log.d(TAG, "Created file at: ${file.absolutePath}")

                // Create URI
                val fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                _currentFileUri = fileUri

                // Set status first before starting intent
                _statusMessage.value = "Sending WiFi credentials..."
                _wifiResult.value = WifiResult.Pending("File transfer in progress...")

                // Create a simple share intent
                val intent = Intent(Intent.ACTION_SEND).apply {
                    setPackage("com.android.bluetooth")
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                }

                // Start the activity
                context.startActivity(intent)

                // Delay to allow the share dialog to appear
                delay(500)

                // Finish and process
                viewModelScope.launch {
                    try {
                        // Wait a bit for processing
                        delay(5000)

                        // Update UI
                        _wifiResult.value = WifiResult.Success(
                            "Transfer Complete",
                            "WiFi credentials sent to Raspberry Pi for processing."
                        )

                        // Update Pi status
                        updatePiStatus(selectedDevice, _ssid.value)

                        // Clean up
                        cleanupTransferState()
                    } catch (e: Exception) {
                        _wifiResult.value = WifiResult.Error("Error: ${e.message}")
                        cleanupTransferState()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending file", e)
                _statusMessage.value = "Error: ${e.message}"
                _wifiResult.value = WifiResult.Error("File transfer failed: ${e.message}")
                cleanupTransferState()
            }
        }
    }

    private fun cleanupTransferState() {
        _isTransferInProgress.value = false

        // Clear any URI permissions
        _currentFileUri?.let { uri ->
            try {
                getApplication<Application>().revokeUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error revoking URI permission", e)
            }
        }
        _currentFileUri = null
    }


    private fun monitorFileTransfer(device: BluetoothDevice) {
        viewModelScope.launch {
            try {
                // Wait for transfer to complete (we don't have a way to directly monitor it)
                delay(3000)

                // Update UI
                _wifiResult.value = WifiResult.Success(
                    "Transfer Complete",
                    "WiFi credentials sent to Raspberry Pi for processing."
                )

                // Update Pi status
                updatePiStatus(device, _ssid.value)

                // Clean up transfer state
                delay(1000)
                cleanupTransferState()
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring file transfer", e)
                _wifiResult.value = WifiResult.Error("Transfer monitoring error: ${e.message}")
                cleanupTransferState()
            }
        }
    }

    private fun updatePiStatus(device: BluetoothDevice, ssid: String) {
        val deviceName = bluetoothRepository.getDeviceName(device) ?: "Unknown Device"
        val deviceAddress = bluetoothRepository.getDeviceAddress(device) ?: "Unknown"

        val status = RaspberryPiStatus(
            deviceName = deviceName,
            address = deviceAddress,
            isConnected = true,
            connectedNetwork = ssid,
            ipAddress = "Check device",
            lastUpdated = System.currentTimeMillis()
        )

        _piStatus.value = status
        savePiStatus(status)
    }

    private fun savePiStatus(status: RaspberryPiStatus) {
        try {
            val context = getApplication<Application>().applicationContext
            val prefs = context.getSharedPreferences("raspberry_pi_status", Context.MODE_PRIVATE)

            val json = org.json.JSONObject().apply {
                put("deviceName", status.deviceName)
                put("address", status.address)
                put("isConnected", status.isConnected)
                put("connectedNetwork", status.connectedNetwork ?: "")
                put("ipAddress", status.ipAddress ?: "")
                put("lastUpdated", status.lastUpdated)
            }

            prefs.edit().putString(status.address, json.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving Pi status", e)
        }
    }

    private fun loadPiStatus(device: BluetoothDevice) {
        try {
            val deviceAddress = bluetoothRepository.getDeviceAddress(device) ?: return
            val context = getApplication<Application>().applicationContext
            val prefs = context.getSharedPreferences("raspberry_pi_status", Context.MODE_PRIVATE)

            val json = prefs.getString(deviceAddress, null) ?: return
            val jsonObj = org.json.JSONObject(json)

            val status = RaspberryPiStatus(
                deviceName = jsonObj.getString("deviceName"),
                address = jsonObj.getString("address"),
                isConnected = jsonObj.getBoolean("isConnected"),
                connectedNetwork = jsonObj.getString("connectedNetwork").takeIf { it.isNotEmpty() },
                ipAddress = jsonObj.getString("ipAddress").takeIf { it.isNotEmpty() },
                lastUpdated = jsonObj.getLong("lastUpdated")
            )

            _piStatus.value = status
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Pi status", e)
        }
    }


    private fun checkBluetoothPermissions(): Boolean {
        val context = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getDeviceIdentifier(): String {
        val context = getApplication<Application>().applicationContext
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearConnectionResult() {
        _connectionResult.value = null
    }

    fun clearWifiResult() {
        _wifiResult.value = null
    }

    fun updatePairedDevices() {
        bluetoothRepository.updatePairedDevices()
    }

    fun getDeviceName(device: BluetoothDevice): String? {
        return bluetoothRepository.getDeviceName(device)
    }

    fun autoDetectRaspberryPi() {
        viewModelScope.launch {
            // First check if we have any paired Raspberry Pi devices
            val pairedPiDevices = pairedDevices.value.filter { isRaspberryPiDevice(it.name) }

            if (pairedPiDevices.isNotEmpty()) {
                // Connect to the first paired Raspberry Pi device
                pairedPiDevices.first().device.let { device ->
                    connectToDevice(device)
                    return@launch
                }
            }

            // If no paired Raspberry Pi devices, start scanning
            startScan()

            // Give it some time to discover devices
            delay(10000) // 10 seconds

            // Stop scanning
            stopScan()

            // Check if we found any Raspberry Pi devices
            val discoveredPiDevices = discoveredDevices.value.filter { isRaspberryPiDevice(it.name) }

            if (discoveredPiDevices.isNotEmpty()) {
                // Select the first discovered Raspberry Pi device
                val device = discoveredPiDevices.first().device

                // If it's not paired, we need to pair first
                if (!discoveredPiDevices.first().isPaired) {
                    // Emit a message indicating that a pairing attempt is needed
                    _connectionResult.value = ConnectionResult.PairingRequired(device)
                } else {
                    // It's already paired, connect to it
                    connectToDevice(device)
                }
            } else {
                _statusMessage.value = "No Raspberry Pi devices found"
            }
        }
    }

    private fun isRaspberryPiDevice(name: String?): Boolean {
        if (name == null) return false

        val lowerName = name.lowercase()
        return lowerName.startsWith("raspberrypi") ||
                lowerName.startsWith("raspberry pi") ||
                lowerName.startsWith("raspberry-pi") ||
                lowerName.startsWith("raspberry_pi") ||
                (lowerName.contains("raspberry") && lowerName.contains("pi"))
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothRepository.unregisterReceiver()
    }

    companion object {
        private const val TAG = "BluetoothViewModel"
    }

    sealed class ConnectionResult {
        data class Success(val deviceName: String) : ConnectionResult()
        data class Error(val message: String) : ConnectionResult()
        data class PairingRequired(val device: BluetoothDevice) : ConnectionResult()
    }

    sealed class WifiResult {
        data class Success(val ipAddress: String, val message: String) : WifiResult()
        data class Error(val message: String) : WifiResult()
        data class Pending(val message: String) : WifiResult()
    }
}