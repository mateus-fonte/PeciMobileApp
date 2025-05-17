package com.example.pecimobileapp.viewmodels

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pecimobileapp.ble.BleManager
import com.example.pecimobileapp.ble.BleManagerProvider
import com.example.pecimobileapp.ble.DeviceType
import com.example.pecimobileapp.services.SimpleWebSocketService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

class SimpleWebSocketViewModel(application: Application) : AndroidViewModel(application) {
    private val wsService = SimpleWebSocketService(application.applicationContext)
    private val bleManager = BleManagerProvider.getInstance().getBleManager()
    
    private val _setupProgress = MutableStateFlow(0f)
    val setupProgress: StateFlow<Float> = _setupProgress
    
    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError
    
    val isConnected = wsService.isConnected
    val latestImage = wsService.latestImage
    val latestThermalData = wsService.latestThermalData
    val serverState = wsService.serverState
    
    fun startServer(port: Int = 8080): Boolean {
        if (!wsService.isAccessPointActive()) {
            _connectionError.value = "Ative o hotspot do dispositivo primeiro"
            return false
        }
        return wsService.startServer(port)
    }
    
    suspend fun configureEsp32(ssid: String, password: String): Boolean {
        try {
            _setupProgress.value = 0.2f
            
            // 1. Verify AP and start server
            if (!wsService.isAccessPointActive()) {
                _connectionError.value = "Please activate the device's hotspot first"
                return false
            }
            
            // 2. Get AP IP
            val serverIp = wsService.getDeviceIpAddress() ?: run {
                _connectionError.value = "Could not get hotspot IP"
                return false
            }
            
            // 3. Start WebSocket server
            if (!startServer()) {
                _connectionError.value = "Could not start WebSocket server"
                return false
            }
            
            // Wait a bit to ensure server is fully initialized
            delay(2000)
            _setupProgress.value = 0.4f
            
            // 4. Check BLE connection
            if (!bleManager.isConnected.value) {
                _connectionError.value = "Connect the camera via Bluetooth first"
                return false
            }
            
            _setupProgress.value = 0.6f
            
            // 5. Send WiFi configuration
            val serverAddress = "$serverIp:8080"
            withTimeout(45000) { // Increased to 45 seconds total
                // Send configuration
                bleManager.sendAllConfigs(ssid, password, serverAddress)
                _setupProgress.value = 0.7f
                
                // Wait for BLE disconnect (should happen when ESP32 switches to WiFi mode)
                var attempts = 0
                while (bleManager.isConnected.value && attempts < 20) {
                    delay(500)
                    attempts++
                }
                _setupProgress.value = 0.75f

                _connectionError.value = "ESP32 disconnected from BLE, waiting for WiFi connection..."
                
                // Give time for ESP32 to connect to WiFi (20 seconds)
                delay(2000) // Initial pause for ESP32 to start WiFi connection
                attempts = 0
                while (!isConnected.value && attempts < 40) {
                    // Update progress from 75% to 95% during wait
                    _setupProgress.value = 0.75f + (attempts.toFloat() / 40f * 0.20f)
                    delay(500)
                    attempts++
                    
                    // Update message every 2 seconds
                    if (attempts % 4 == 0) {
                        val seconds = attempts / 2
                        if (seconds < 10) {
                            _connectionError.value = "ESP32 connecting to WiFi... (${seconds}s)"
                        } else {
                            _connectionError.value = "ESP32 trying to connect to server... (${seconds}s)"
                        }
                    }
                }
            }
            
            if (!isConnected.value) {
                _connectionError.value = "ESP32 did not connect to server. Please verify your WiFi credentials."
                return false
            }
            
            _setupProgress.value = 1.0f
            _connectionError.value = null
            return true
            
        } catch (e: Exception) {
            _connectionError.value = "Error: ${e.message}"
            return false
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        wsService.stopServer()
    }
}
