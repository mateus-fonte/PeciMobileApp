package com.example.pecimobileapp.devices

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface representing a generic device in the PECI Mobile App
 * This provides a common interface for all types of devices (PPG, Thermal Camera, etc.)
 */
interface Device {
    /**
     * Unique identifier for the device
     */
    val id: String
    
    /**
     * Human-readable name of the device
     */
    val name: String?
    
    /**
     * MAC address of the device
     */
    val macAddress: String
    
    /**
     * The raw BluetoothDevice object if available
     */
    val bluetoothDevice: BluetoothDevice?
    
    /**
     * The type of device
     */
    val type: DeviceType
    
    /**
     * Observable connection state
     */
    val isConnected: StateFlow<Boolean>
    
    /**
     * Get all data values from this device as a Map
     */
    fun getValues(): Map<String, Any?>
    
    /**
     * Connect to this device
     */
    suspend fun connect(): Boolean
    
    /**
     * Reconnect to this device if connection was lost
     */
    suspend fun reconnect(): Boolean
    
    /**
     * Disconnect from this device
     */
    suspend fun disconnect(): Boolean
    
    /**
     * Remove this device from the saved devices list
     */
    suspend fun forget()
    
    /**
     * Send configuration to this device
     * @param config Map of configuration parameters
     */
    suspend fun sendConfig(config: Map<String, String>): Boolean
    
    /**
     * Enum defining the types of devices supported
     */
    enum class DeviceType {
        PPG,
        THERMAL_CAMERA,
        UNKNOWN
    }
}