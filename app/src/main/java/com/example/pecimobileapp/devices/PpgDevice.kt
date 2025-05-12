package com.example.pecimobileapp.devices

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.example.pecimobileapp.ble.BleManager
import com.example.pecimobileapp.ble.BleManagerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Implementation of Device interface for PPG heart rate sensor
 */
class PpgDevice(
    private val context: Context,
    override val bluetoothDevice: BluetoothDevice?
) : Device {
    
    private val TAG = "PpgDevice"
    
    // Use the existing BleManager
    private val bleManager: BleManager = BleManagerProvider.getInstance()
    
    // Coroutine scope for device operations
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    override val id: String = bluetoothDevice?.address ?: "unknown_ppg"
    
    override val name: String? = bluetoothDevice?.name ?: "PPG Device"
    
    override val macAddress: String = bluetoothDevice?.address ?: "00:00:00:00:00:00"
    
    override val type: Device.DeviceType = Device.DeviceType.PPG
    
    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // Device-specific values
    private val _heartRate = MutableStateFlow<Int?>(null)
    val heartRate: StateFlow<Int?> = _heartRate.asStateFlow()
    
    init {
        // Start observing BleManager values
        observeBleManagerValues()
    }
    
    override fun getValues(): Map<String, Any?> {
        return mapOf(
            "heart_rate" to _heartRate.value,
            "connected" to _isConnected.value
        )
    }
    
    override suspend fun connect(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Connecting to PPG device: $macAddress")
                
                // Use the BleManager to connect
                bluetoothDevice?.let {
                    bleManager.connectPpg(it)
                    return@withContext true
                }
                
                Log.e(TAG, "No BluetoothDevice available to connect")
                false
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to PPG device: ${e.message}", e)
                false
            }
        }
    }
    
    override suspend fun reconnect(): Boolean {
        Log.d(TAG, "Attempting to reconnect to PPG device: $macAddress")
        return connect()
    }
    
    override suspend fun disconnect(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Disconnecting from PPG device: $macAddress")
                bleManager.disconnect()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting from PPG device: ${e.message}", e)
                false
            }
        }
    }
    
    override suspend fun forget() {
        // No special operation needed for forgetting a PPG device
        // The DevicesManager will handle removing it from persistent storage
        Log.d(TAG, "Forgetting PPG device: $macAddress")
    }
    
    override suspend fun sendConfig(config: Map<String, String>): Boolean {
        // PPG devices generally don't need configuration
        Log.d(TAG, "PPG devices don't support configuration")
        return false
    }
    
    /**
     * Observe values from the BleManager
     */
    private fun observeBleManagerValues() {
        coroutineScope.launch {
            // Observe connection state
            bleManager.isConnected.collect { connected ->
                _isConnected.value = connected
            }
        }
        
        coroutineScope.launch {
            // Observe heart rate data
            bleManager.ppgHeartRate.collect { hr ->
                _heartRate.value = hr
            }
        }
    }
}
