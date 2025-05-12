package com.example.pecimobileapp.devices

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.example.pecimobileapp.ble.BleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Global singleton device manager for the PECI Mobile App.
 * This class manages all connected devices and provides access to their data.
 */
object DevicesManager {
    private const val TAG = "DevicesManager"
    private const val PAIRED_DEVICES_KEY = "paired_devices"
    
    private lateinit var applicationContext: Context
    private lateinit var bleManager: BleManager
    private lateinit var sharedPreferences: SharedPreferences
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    // Map of device MAC addresses to Device objects
    private val devicesMap = mutableMapOf<String, Device>()
    
    // MutableStateFlow for all device values combined for UI consumption
    private val _values = MutableStateFlow<Map<String, Any?>>(emptyMap())
    val values: StateFlow<Map<String, Any?>> = _values.asStateFlow()
    
    // Track scan results
    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()
    
    // Track scanning state
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    // List of already connected devices (persisted between app uses)
    private var alreadyConnectedDevices: MutableSet<String> = mutableSetOf()
    
    /**
     * Initialize the DevicesManager with application context.
     * This must be called before using any other methods.
     */
    fun initialize(context: Context, bleManagerInstance: BleManager) {
        Log.d(TAG, "Initializing DevicesManager")
        applicationContext = context.applicationContext
        bleManager = bleManagerInstance
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        
        // Load previously paired devices
        loadSavedDevices()
        
        // Start observing BLE scan results
        observeBleManager()
    }
      /**
     * Start scanning for nearby Bluetooth devices
     */
    fun startScan() {
        Log.d(TAG, "Starting BLE scan")
        bleManager.startScan()
        // O estado _isScanning será atualizado pela observação do estado do bleManager
    }
      /**
     * Stop scanning for devices
     */
    fun stopScan() {
        Log.d(TAG, "Stopping BLE scan")
        bleManager.stopScan()
        // O estado _isScanning será atualizado pela observação do estado do bleManager
    }
    
    /**
     * Connect to a device from a scan result
     */
    suspend fun connectToDevice(result: ScanResult, type: Device.DeviceType): Device? {
        return withContext(Dispatchers.IO) {
            try {
                val device = result.device
                Log.d(TAG, "Connecting to device: ${device.address}, type: $type")
                
                // Check if we already have this device in our map
                val existingDevice = devicesMap[device.address]
                if (existingDevice != null) {
                    Log.d(TAG, "Device already exists in map, reconnecting")
                    existingDevice.connect()
                    return@withContext existingDevice
                }
                
                // Create new device based on type
                val newDevice = when (type) {
                    Device.DeviceType.PPG -> PpgDevice(applicationContext, device)
                    Device.DeviceType.THERMAL_CAMERA -> ThermalCameraDevice(applicationContext, device)
                    else -> null
                }
                
                if (newDevice != null) {
                    // Connect to the device
                    val connected = newDevice.connect()
                    if (connected) {
                        // Add to our map
                        devicesMap[device.address] = newDevice
                        
                        // Save to persistent storage
                        addDeviceToSavedList(device.address, type)
                        
                        // Update the values map
                        updateValues()
                        
                        return@withContext newDevice
                    }
                }
                
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to device: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * Get a device by its MAC address
     */
    fun getDevice(macAddress: String): Device? {
        return devicesMap[macAddress]
    }
    
    /**
     * Get all devices of a specific type
     */
    fun getDevicesByType(type: Device.DeviceType): List<Device> {
        return devicesMap.values.filter { it.type == type }
    }
    
    /**
     * Get all connected devices
     */
    fun getConnectedDevices(): List<Device> {
        return devicesMap.values.filter { it.isConnected.value }
    }
    
    /**
     * Forget a device (disconnect and remove)
     */
    suspend fun forgetDevice(macAddress: String) {
        withContext(Dispatchers.IO) {
            val device = devicesMap[macAddress]
            if (device != null) {
                Log.d(TAG, "Forgetting device: $macAddress")
                device.disconnect()
                device.forget()
                
                // Remove from our tracking
                devicesMap.remove(macAddress)
                removeDeviceFromSavedList(macAddress)
                
                // Update values
                updateValues()
            }
        }
    }
    
    /**
     * Disconnect from all devices
     */
    suspend fun disconnectAll() {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Disconnecting all devices")
            devicesMap.values.forEach { device ->
                device.disconnect()
            }
        }
    }
    
    /**
     * Attempt to reconnect to all previously connected devices
     */
    fun reconnectSavedDevices() {
        coroutineScope.launch {
            alreadyConnectedDevices.forEach { macAddress ->
                val device = devicesMap[macAddress]
                if (device != null) {
                    Log.d(TAG, "Attempting to reconnect to saved device: $macAddress")
                    device.reconnect()
                }
            }
        }
    }
    
    /**
     * Send configuration to a specific device
     */
    suspend fun sendConfig(macAddress: String, config: Map<String, String>): Boolean {
        return withContext(Dispatchers.IO) {
            val device = devicesMap[macAddress]
            if (device != null) {
                Log.d(TAG, "Sending configuration to device: $macAddress")
                device.sendConfig(config)
            } else {
                false
            }
        }
    }
    
    /**
     * Update the combined values StateFlow by collecting all device values
     */
    private fun updateValues() {
        val allValues = mutableMapOf<String, Any?>()
        
        devicesMap.values.forEach { device ->
            // Only include connected devices
            if (device.isConnected.value) {
                val deviceValues = device.getValues()
                
                // Add device type prefix to avoid key collisions
                val prefix = when (device.type) {
                    Device.DeviceType.PPG -> "ppg_"
                    Device.DeviceType.THERMAL_CAMERA -> "thermal_"
                    else -> "unknown_"
                }
                
                // Add device-specific identifier to make keys unique
                val deviceId = device.macAddress.takeLast(4).replace(":", "")
                
                deviceValues.forEach { (key, value) ->
                    allValues["${prefix}${deviceId}_$key"] = value
                }
            }
        }
        
        _values.update { allValues }
    }
      /**
     * Observe BleManager for scan results
     */
    private fun observeBleManager() {
        coroutineScope.launch {
            bleManager.scanResults.collect { results ->
                _scanResults.value = results
                // Não interromper a busca ao encontrar dispositivos
                // O BleManager já gerencia o tempo de busca adequado
            }
        }
        
        // Observa quando o BleManager termina a busca
        coroutineScope.launch {
            bleManager.isScanning.collect { isScanning ->
                _isScanning.value = isScanning
            }
        }
    }
    
    /**
     * Load saved devices from SharedPreferences
     */
    private fun loadSavedDevices() {
        try {
            // Get the set of saved device MAC addresses
            val savedDevices = sharedPreferences.getStringSet(PAIRED_DEVICES_KEY, emptySet()) ?: emptySet()
            alreadyConnectedDevices = savedDevices.toMutableSet()
            
            Log.d(TAG, "Loaded ${alreadyConnectedDevices.size} saved devices")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved devices: ${e.message}", e)
        }
    }
    
    /**
     * Add a device to the saved devices list
     */
    private fun addDeviceToSavedList(macAddress: String, type: Device.DeviceType) {
        alreadyConnectedDevices.add(macAddress)
        
        // Save to SharedPreferences
        sharedPreferences.edit()
            .putStringSet(PAIRED_DEVICES_KEY, alreadyConnectedDevices)
            .apply()
        
        Log.d(TAG, "Added device to saved list: $macAddress, type: $type")
    }
    
    /**
     * Remove a device from the saved devices list
     */
    private fun removeDeviceFromSavedList(macAddress: String) {
        alreadyConnectedDevices.remove(macAddress)
        
        // Save to SharedPreferences
        sharedPreferences.edit()
            .putStringSet(PAIRED_DEVICES_KEY, alreadyConnectedDevices)
            .apply()
        
        Log.d(TAG, "Removed device from saved list: $macAddress")
    }
}