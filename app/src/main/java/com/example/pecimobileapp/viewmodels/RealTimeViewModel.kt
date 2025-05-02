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

    // servidor WS para obter o IP
    private val wsService = WebSocketServerService(app).apply {
        startServer()
    }

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

    // 5) IP do servidor WS
    val serverAddress: StateFlow<String> =
        wsService.connectionStats
            .map { stats ->
                // stats.serverAddress vem no formato "IP:porta"
                stats.serverAddress.substringBefore(":")
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, "")


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
        val ip = serverAddress.value
        bleCam.sendAllConfigs(ssid, password, ip)
    }



}
