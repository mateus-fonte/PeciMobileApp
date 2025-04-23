// RealTimeViewModel.kt
package com.example.pecimobileapp.viewmodels

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pecimobileapp.ble.BleManager
import com.example.pecimobileapp.models.RealTimeData
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class RealTimeViewModel(application: Application) : AndroidViewModel(application) {
    private val bleManager = BleManager(application)

    // ➊ expõe os resultados do scan
    val scanResults: StateFlow<List<android.bluetooth.le.ScanResult>> = bleManager.scanResults

    // ➋ expõe o fluxo de heartRate
    val heartRate = bleManager.heartRate

    /** Indica quando o último write terminou com sucesso */
    val configSent: StateFlow<Boolean> = bleManager.configSent

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _realTimeData = MutableStateFlow<RealTimeData?>(null)
    val realTimeData = _realTimeData.asStateFlow()

    init {
        bleManager.heartRate
            .onEach { hr ->
                if (hr != null) {
                    _isConnected.value = true
                    val prior = _realTimeData.value
                    _realTimeData.value = if (prior != null) {
                        prior.copy(heartRate = hr)
                    } else {
                        RealTimeData(
                            heartRate = hr,
                            averageTemperature = 0f,
                            timestamp = System.currentTimeMillis(),
                            value = 0f
                        )
                    }
                } else {
                    _isConnected.value = false
                    _realTimeData.value = null
                }
            }
            .launchIn(viewModelScope)
    }

    /** Inicia o scan BLE */
    fun startBleScan() = viewModelScope.launch {
        bleManager.startBleScan()
    }

    /** Conecta ao dispositivo selecionado */
    fun connectToDevice(device: BluetoothDevice) = viewModelScope.launch {
        bleManager.connectToDevice(device)
    }

    /** Envia configurações */
    fun sendTimeConfig(ts: Long)  = viewModelScope.launch { bleManager.sendTimeConfig(ts) }
    fun sendModeConfig(m: Int)    = viewModelScope.launch { bleManager.sendModeConfig(m) }
    fun sendIdConfig(id: String)  = viewModelScope.launch { bleManager.sendIdConfig(id) }
}
