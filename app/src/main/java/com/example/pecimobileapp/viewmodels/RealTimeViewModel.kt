package com.example.pecimobileapp.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pecimobileapp.ble.BleManager
import com.example.pecimobileapp.models.RealTimeData
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RealTimeViewModel(application: Application) : AndroidViewModel(application) {
    private val bleManager = BleManager(application)

    // ➊ expõe os resultados do scan
    val scanResults: StateFlow<List<android.bluetooth.le.ScanResult>> = bleManager.scanResults

    // ➋ expõe o fluxo de heartRate
    val heartRate: StateFlow<Int?> = bleManager.heartRate

    // ➌ expõe quando o write de configuração foi confirmado
    val configSent: StateFlow<Boolean> = bleManager.configSent

    // ➍ nosso estado interno de "estamos prontos p/ mostrar dados?"
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = bleManager.isConnected

    // ➎ dados que a UI consome
    private val _realTimeData = MutableStateFlow<RealTimeData?>(null)
    val realTimeData: StateFlow<RealTimeData?> = _realTimeData.asStateFlow()

    init {
        // Combine heartRate + configSent: só emitimos dados após configSent == true E hr != null
        combine(heartRate, configSent) { hr, sent -> hr to sent }
            .onEach { (hr, sent) ->
                if (sent && hr != null) {
                    _isConnected.value = true
                    // atualiza ou cria novo RealTimeData
                    val now = System.currentTimeMillis()
                    _realTimeData.value = _realTimeData.value
                        ?.copy(heartRate = hr, timestamp = now)
                        ?: RealTimeData(
                            heartRate         = hr,
                            averageTemperature = 0f,        // ou monte aqui
                            timestamp         = now,
                            value             = 0f         // ajuste conforme seu modelo
                        )
                } else {
                    _isConnected.value = false
                    _realTimeData.value = null
                }
            }
            .launchIn(viewModelScope)
    }

    /** Dispara o scan BLE */
    fun startBleScan() = viewModelScope.launch {
        bleManager.startBleScan()
    }

    /** Conecta ao device selecionado */
    fun connectToDevice(device: android.bluetooth.BluetoothDevice) = viewModelScope.launch {
        bleManager.connectToDevice(device)
    }

    /** Envia o timestamp fixo */
    fun sendTimeConfig(ts: Long) = viewModelScope.launch {
        bleManager.sendTimeConfig(ts)
    }

    /** Envia o mode (2 fixo) */
    fun sendModeConfig(mode: Int) = viewModelScope.launch {
        bleManager.sendModeConfig(mode)
    }

    /** Envia o ID */
    fun sendIdConfig(id: String) = viewModelScope.launch {
        bleManager.sendIdConfig(id)
    }
}
