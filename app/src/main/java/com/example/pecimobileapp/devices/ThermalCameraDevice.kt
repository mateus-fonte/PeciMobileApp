package com.example.pecimobileapp.devices

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import androidx.preference.PreferenceManager
import com.example.pecimobileapp.ble.BleManager
import com.example.pecimobileapp.ble.BleManagerProvider
import com.example.pecimobileapp.services.WebSocketServerService
import com.example.pecimobileapp.utils.OpenCVUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Implementation of Device interface for Thermal Camera with WebSocket connectivity
 */
class ThermalCameraDevice(
    private val context: Context,
    override val bluetoothDevice: BluetoothDevice?
) : Device {
    
    private val TAG = "ThermalCameraDevice"
    private val CAMERA_MAC_ADDRESS_KEY = "thermal_camera_mac_address"
    private val CAMERA_NAME_KEY = "thermal_camera_name"
    
    // Use the existing BleManager
    private val bleManager: BleManager = BleManagerProvider.getInstance()
    
    // WebSocket server for thermal camera data
    private val webSocketServer = WebSocketServerService(context.applicationContext)
    
    // SharedPreferences for saving device info
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    
    // Coroutine scope for device operations
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    override val id: String = bluetoothDevice?.address ?: "unknown_thermal"
    
    override val name: String? = bluetoothDevice?.name ?: "Thermal Camera"
    
    override val macAddress: String = bluetoothDevice?.address ?: "00:00:00:00:00:00"
    
    override val type: Device.DeviceType = Device.DeviceType.THERMAL_CAMERA
    
    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // Device-specific values
    private val _avgTemperature = MutableStateFlow<Float?>(null)
    val avgTemperature: StateFlow<Float?> = _avgTemperature.asStateFlow()
    
    private val _maxTemperature = MutableStateFlow<Float?>(null)
    val maxTemperature: StateFlow<Float?> = _maxTemperature.asStateFlow()
    
    private val _minTemperature = MutableStateFlow<Float?>(null)
    val minTemperature: StateFlow<Float?> = _minTemperature.asStateFlow()
    
    private val _latestCameraImage = MutableStateFlow<Bitmap?>(null)
    val latestCameraImage: StateFlow<Bitmap?> = _latestCameraImage.asStateFlow()
    
    private val _faceData = MutableStateFlow<List<OpenCVUtils.FaceData>>(emptyList())
    val faceData: StateFlow<List<OpenCVUtils.FaceData>> = _faceData.asStateFlow()
    
    private val _serverState = MutableStateFlow(WebSocketServerService.ServerState.Stopped)
    val serverState: StateFlow<WebSocketServerService.ServerState> = _serverState.asStateFlow()
    
    private val _configProgress = MutableStateFlow(0f)
    val configProgress: StateFlow<Float> = _configProgress.asStateFlow()
    
    init {
        // Start observing BleManager and WebSocket server values
        observeBleManagerValues()
        observeWebSocketValues()
        
        // Save the device info to shared preferences
        saveDeviceToPreferences()
    }
    
    override fun getValues(): Map<String, Any?> {
        return mapOf(
            "avg_temperature" to _avgTemperature.value,
            "max_temperature" to _maxTemperature.value,
            "min_temperature" to _minTemperature.value,
            "connected" to _isConnected.value,
            "server_running" to (_serverState.value == WebSocketServerService.ServerState.Running),
            "face_count" to _faceData.value.size
        )
    }
    
    override suspend fun connect(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Connecting to Thermal Camera device: $macAddress")
                
                // Use the BleManager to connect
                bluetoothDevice?.let {
                    bleManager.connectCam(it)
                    return@withContext true
                }
                
                Log.e(TAG, "No BluetoothDevice available to connect")
                false
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to Thermal Camera device: ${e.message}", e)
                false
            }
        }
    }
    
    override suspend fun reconnect(): Boolean {
        Log.d(TAG, "Attempting to reconnect to Thermal Camera device: $macAddress")
        return connect()
    }
    
    override suspend fun disconnect(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Disconnecting from Thermal Camera device: $macAddress")
                bleManager.disconnect()
                
                // Stop the WebSocket server if it's running
                stopWebSocketServer()
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting from Thermal Camera device: ${e.message}", e)
                false
            }
        }
    }
    
    override suspend fun forget() {
        // Remove the device from shared preferences
        sharedPreferences.edit()
            .remove(CAMERA_MAC_ADDRESS_KEY)
            .remove(CAMERA_NAME_KEY)
            .apply()
        
        Log.d(TAG, "Forgetting Thermal Camera device: $macAddress")
    }
    
    override suspend fun sendConfig(config: Map<String, String>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Sending configuration to Thermal Camera device: $macAddress")
                
                val ssid = config["ssid"] ?: return@withContext false
                val password = config["password"] ?: return@withContext false
                val serverIp = config["server_ip"] ?: return@withContext false
                
                // Send config via BleManager
                bleManager.sendWifiConfig(ssid, password, serverIp)
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error sending configuration to Thermal Camera device: ${e.message}", e)
                false
            }
        }
    }
    
    /**
     * Start the WebSocket server
     */
    suspend fun startWebSocketServer(port: Int = 8080): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting WebSocket server on port $port")
                webSocketServer.startServer(port)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error starting WebSocket server: ${e.message}", e)
                false
            }
        }
    }
    
    /**
     * Stop the WebSocket server
     */
    suspend fun stopWebSocketServer(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Stopping WebSocket server")
                webSocketServer.stopServer()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping WebSocket server: ${e.message}", e)
                false
            }
        }
    }
    
    /**
     * Get the current server port
     */
    fun getServerPort(): Int {
        return webSocketServer.getPort()
    }
    
    /**
     * Save device info to SharedPreferences
     */
    private fun saveDeviceToPreferences() {
        if (bluetoothDevice != null) {
            sharedPreferences.edit()
                .putString(CAMERA_MAC_ADDRESS_KEY, macAddress)
                .putString(CAMERA_NAME_KEY, name)
                .apply()
            
            Log.d(TAG, "Saved thermal camera device to preferences: $macAddress")
        }
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
            // Observe average temperature
            bleManager.avgTemp.collect { temp ->
                _avgTemperature.value = temp
            }
        }
        
        coroutineScope.launch {
            // Observe max temperature
            bleManager.maxTemp.collect { temp ->
                _maxTemperature.value = temp
            }
        }
        
        coroutineScope.launch {
            // Observe min temperature
            bleManager.minTemp.collect { temp ->
                _minTemperature.value = temp
            }
        }
        
        coroutineScope.launch {
            // Observe configuration progress
            bleManager.configProgress.collect { progress ->
                _configProgress.value = progress
            }
        }
    }
    
    /**
     * Observe values from the WebSocket server
     */
    private fun observeWebSocketValues() {
        coroutineScope.launch {
            // Observe server state
            webSocketServer.isRunning.collect { running ->
                _serverState.value = if (running) 
                    WebSocketServerService.ServerState.Running 
                else 
                    WebSocketServerService.ServerState.Stopped
            }
        }
        
        coroutineScope.launch {
            // Observe camera image and face data
            webSocketServer.processedImage.collect { (bitmap, faces) ->
                _latestCameraImage.value = bitmap
                _faceData.value = faces
            }
        }
    }
}
