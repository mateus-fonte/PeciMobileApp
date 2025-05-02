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


    /** ❶ lista crua de "IP (interface)" */
    val allNetworkIPs: StateFlow<List<String>> =
        wsService.connectionStats
            .map { it.allNetworkIPs }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** ❷ extrai só o IP da interface de Wi-Fi (wlan0) ou fallback vazio */
    val accessPointIp: StateFlow<String> =
        allNetworkIPs
            .map { list ->
                list.firstOrNull { entry ->
                    // ajusta aqui o nome da interface que for seu AP — ex: "wlan" ou "ap"
                    entry.contains("(ap") && entry.length <= 22
                }?.substringBefore(" ")
                // substringBefore(" ") pega tudo antes do espaço, i.e. só o IP sem "(..."
                    ?: ""
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, "")

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



}
