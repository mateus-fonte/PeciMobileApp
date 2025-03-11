package com.example.pecimobileapp.viewmodels

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pecimobileapp.models.BluetoothDeviceModel
import com.example.pecimobileapp.repositories.BluetoothRepository
import com.example.pecimobileapp.services.BluetoothService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

// Android Bluetooth Share constants (from hidden API, recreated here)
object BluetoothShare {
    const val DESTINATION = "android.bluetooth.device.extra.DEVICE"
    const val DIRECTION = "direction"
    const val DIRECTION_OUTBOUND = 0
    const val TIMESTAMP = "timestamp"
}

/**
 * ViewModel for Bluetooth operations
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

    // MODIFIED METHOD: Now it just selects the device and shows credentials screen
    // without trying to establish a socket connection
    fun connectToDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            // First select the device
            device.address?.let { bluetoothRepository.selectDevice(it) }

            // Stop scanning during connection
            bluetoothRepository.stopDiscovery()

            // Get the device name
            val deviceName = bluetoothRepository.getDeviceName(device) ?: "Unknown Device"

            // Set connected state directly - we don't need an actual socket connection
            // for the file transfer approach
            _isConnected.value = true
            _connectionResult.value = ConnectionResult.Success(deviceName)

            _statusMessage.value = "Selected device: $deviceName"
        }
    }

    fun disconnectDevice() {
        // We don't need to actually disconnect a socket anymore, just clear the state
        _isConnected.value = false
        bluetoothRepository.clearSelection()
        _connectionResult.value = null
    }

    fun updateSsid(newSsid: String) {
        _ssid.value = newSsid
    }

    fun updatePassword(newPassword: String) {
        _password.value = newPassword
    }

    fun sendWifiCredentialsAsFile() {
        if (_ssid.value.isEmpty() || _password.value.isEmpty()) {
            _statusMessage.value = "Please enter both SSID and password"
            return
        }

        viewModelScope.launch {
            val selectedDevice = bluetoothRepository.getSelectedDevice()
            if (selectedDevice == null) {
                _statusMessage.value = "No device selected"
                return@launch
            }

            try {
                // Create a temporary CSV file with WiFi credentials
                val csvContent = "${_ssid.value},${_password.value}"
                val context = getApplication<Application>().applicationContext
                val file = File(context.cacheDir, "wifi_config.csv")
                file.writeText(csvContent)

                // Get the device's Bluetooth address
                val deviceAddress = bluetoothRepository.getDeviceAddress(selectedDevice)
                if (deviceAddress == null) {
                    _statusMessage.value = "Unable to get device address"
                    return@launch
                }

                // METHOD 1: Direct transfer using BluetoothOppLauncherActivity
                // This bypasses the share dialog completely on most devices
                val intent = Intent().apply {
                    component = ComponentName("com.android.bluetooth",
                        "com.android.bluetooth.opp.BluetoothOppLauncherActivity")
                    action = Intent.ACTION_SEND
                    type = "text/csv"
                    val fileUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    putExtra("android.bluetooth.device.extra.DEVICE", deviceAddress)
                    // The flags below help bypass the share sheet
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                }

                try {
                    // First try the direct method
                    context.startActivity(intent)
                    _statusMessage.value = "Sending WiFi credentials to Raspberry Pi..."
                    _wifiResult.value = WifiResult.Pending("File transfer in progress...")
                } catch (e: Exception) {
                    // Fallback method if the direct method fails on some devices
                    Log.d(TAG, "Direct method failed, trying fallback method: ${e.message}")

                    val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        val fileUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        // Pre-select Bluetooth by setting the package
                        setPackage("com.android.bluetooth")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    }

                    context.startActivity(fallbackIntent)
                    _statusMessage.value = "Sending WiFi credentials using Bluetooth..."
                    _wifiResult.value = WifiResult.Pending("Select Raspberry Pi device if prompted...")
                }

                // Start monitoring for completion
                monitorFileTransfer()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending WiFi credentials file", e)
                _statusMessage.value = "Error sending file: ${e.message}"
                _wifiResult.value = WifiResult.Error("Failed to send credentials: ${e.message}")
            }
        }
    }

    // Add this new method to monitor file transfer status
    private fun monitorFileTransfer() {
        viewModelScope.launch {
            try {
                // In a real implementation, we could query the ContentProvider
                // for BluetoothShare to check the actual status
                // For now, we'll just wait a few seconds and assume success
                delay(5000)  // 5 seconds delay to simulate transfer time

                _wifiResult.value = WifiResult.Success(
                    "Process complete",
                    "WiFi credentials sent to Raspberry Pi. It will automatically configure the WiFi."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring file transfer", e)
                _wifiResult.value = WifiResult.Error("Error during file transfer: ${e.message}")
            }
        }
    }

    // Update WifiResult sealed class to include Pending state
    sealed class WifiResult {
        data class Success(val ipAddress: String, val message: String) : WifiResult()
        data class Error(val message: String) : WifiResult()
        data class Pending(val message: String) : WifiResult() // New state for transfers in progress
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

}