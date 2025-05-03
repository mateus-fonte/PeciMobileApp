package com.example.pecimobileapp.viewmodels

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pecimobileapp.ble.BleManager
import com.example.pecimobileapp.services.WebSocketServerService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RealTimeViewModel(app: Application) : AndroidViewModel(app) {
    private val blePpg = BleManager(app)
    private val bleCam = BleManager(app)

    // servidor WS para obter o IP - não iniciamos automaticamente
    private val wsService = WebSocketServerService(app)

    // 1) ScanResults separados
    val scanResultsPpg: StateFlow<List<ScanResult>> = blePpg.scanResults
    val scanResultsCam: StateFlow<List<ScanResult>> = bleCam.scanResults

    // 2) Estados de conexão
    val isPpgConnected: StateFlow<Boolean> = blePpg.isConnected
    val isCamConnected: StateFlow<Boolean> = bleCam.isConnected

    // novo: flows de “perda de conexão”
    val ppgConnectionLost: StateFlow<Boolean> = blePpg.connectionLost
    val camConnectionLost: StateFlow<Boolean> = bleCam.connectionLost

    // 3) Dados por notify
    val ppgHeartRate: StateFlow<Int?> = blePpg.ppgHeartRate
    val avgTemp:       StateFlow<Float?> = bleCam.avgTemp
    val maxTemp:       StateFlow<Float?> = bleCam.maxTemp
    val minTemp:       StateFlow<Float?> = bleCam.minTemp

    // 4) já conectados?
    val readyToStart: StateFlow<Boolean> = combine(isPpgConnected, isCamConnected) { ppg, cam -> ppg && cam }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)


    /** ❶ IP do dispositivo para conectividade */
    val accessPointIp: StateFlow<String> = flow {
        // Obtém o IP diretamente sem iniciar o servidor
        emit(wsService.getDeviceIpAddress())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // UI triggers
    fun startPpgScan() = viewModelScope.launch { blePpg.startScan() }
    fun connectPpg(device: BluetoothDevice) = viewModelScope.launch { blePpg.connectPpg(device) }

    fun startCamScan() = viewModelScope.launch { bleCam.startScan() }
    fun connectCam(device: BluetoothDevice) = viewModelScope.launch { bleCam.connectCam(device) }

    /** Enfileira e envia TODAS as configs de uma vez */
    fun sendAllConfigs(
        ssid: String,
        password: String
    ) = viewModelScope.launch {
        val ip = accessPointIp.value
        bleCam.sendAllConfigs(ssid, password, ip)
    }

    /** Retorna o BleManager da câmera térmica para uso com WebSocketViewModel */
    fun getBleManager(): BleManager {
        return bleCam
    }

}
