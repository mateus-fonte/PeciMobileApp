package com.example.pecimobileapp.viewmodels

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pecimobileapp.ble.BleManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RealTimeViewModel(app: Application) : AndroidViewModel(app) {
    private val blePpg = BleManager(app)
    private val bleCam = BleManager(app)

    // 1) Resultados de scan separados
    val scanResultsPpg: StateFlow<List<ScanResult>> = blePpg.scanResults
    val scanResultsCam: StateFlow<List<ScanResult>> = bleCam.scanResults

    // 2) Status de conexão
    val isPpgConnected: StateFlow<Boolean> = blePpg.isConnected
    val isCamConnected: StateFlow<Boolean> = bleCam.isConnected

    // 3) Dados notificados
    val ppgHeartRate: StateFlow<Int?>      = blePpg.ppgHeartRate
    val avgTemp      : StateFlow<Float?>    = bleCam.avgTemp
    val maxTemp      : StateFlow<Float?>    = bleCam.maxTemp
    val minTemp      : StateFlow<Float?>    = bleCam.minTemp

    // 4) Só libera o início quando AMBOS estiverem conectados
    val readyToStart: StateFlow<Boolean> =
        combine(isPpgConnected, isCamConnected) { ppg, cam -> ppg or cam }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)


    // Métodos acionados pela UI
    fun startPpgScan()           = viewModelScope.launch { blePpg.startScan() }
    fun connectPpg(device: BluetoothDevice) = viewModelScope.launch { blePpg.connectPpg(device) }

    fun startCamScan()           = viewModelScope.launch { bleCam.startScan() }
    fun connectCam(device: BluetoothDevice) = viewModelScope.launch { bleCam.connectCam(device) }
}
