package com.example.pecimobileapp.viewmodels

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pecimobileapp.ble.BleManager
import com.example.pecimobileapp.mqtt.MqttManager
import com.example.pecimobileapp.services.WebSocketServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RealTimeViewModel(app: Application) : AndroidViewModel(app) {
    private val blePpg = BleManager(app)
    private val bleCam = BleManager(app)

    private val wsService = WebSocketServerService(app)

    val scanResultsPpg: StateFlow<List<ScanResult>> = blePpg.scanResults
    val scanResultsCam: StateFlow<List<ScanResult>> = bleCam.scanResults

    val isPpgConnected: StateFlow<Boolean> = blePpg.isConnected
    val isCamConnected: StateFlow<Boolean> = bleCam.isConnected

    val ppgConnectionLost: StateFlow<Boolean> = blePpg.connectionLost
    val camConnectionLost: StateFlow<Boolean> = bleCam.connectionLost

    val ppgHeartRate: StateFlow<Int?> = blePpg.ppgHeartRate
    val avgTemp: StateFlow<Float?> = bleCam.avgTemp
    val maxTemp: StateFlow<Float?> = bleCam.maxTemp
    val minTemp: StateFlow<Float?> = bleCam.minTemp

    val readyToStart: StateFlow<Boolean> = combine(isPpgConnected, isCamConnected) { ppg, cam -> ppg && cam }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val accessPointIp: StateFlow<String> = flow {
        emit(wsService.getDeviceIpAddress())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // Zona selecionada e faixas de zona
    private val _selectedZone = MutableStateFlow(1)
    val selectedZone: StateFlow<Int> = _selectedZone

    private val _zonas = MutableStateFlow<List<Pair<String, IntRange>>>(emptyList())
    val zonas: StateFlow<List<Pair<String, IntRange>>> = _zonas

    // Adicionados para persistência de contexto da sessão
    private val _groupId = MutableStateFlow<String?>(null)
    val groupId: StateFlow<String?> = _groupId

    private val _userId = MutableStateFlow("aluno01")
    val userId: StateFlow<String> = _userId

    private val _exerciseId = MutableStateFlow("exercicio_teste")
    val exerciseId: StateFlow<String> = _exerciseId

    private val _activityStarted = MutableStateFlow(false)
    val activityStarted: StateFlow<Boolean> = _activityStarted

    fun loadUserId(context: Context) {
        viewModelScope.launch {
            ProfilePreferences.userIdFlow(context).collectLatest { id ->
                if (id != null) {
                    _userId.value = id
                    Log.d("RealTimeViewModel", "user_id carregado: $id")
                } else {
                    Log.e("RealTimeViewModel", "user_id não encontrado")
                }
            }
        }
    }

    fun startActivity() {
        _activityStarted.value = true
        blePpg.startActivity()
        bleCam.startActivity()
    }

    fun stopActivity() {
        _activityStarted.value = false
        blePpg.stopActivity()
        bleCam.stopActivity()
    }


    fun setWorkoutParameters(
    zone: Int,
    zonasList: List<Pair<String, IntRange>>,
    group: String? = null,
    user: String,
    exercise: String
) {
    Log.d("RealTimeViewModel", "Setting workout parameters -> zone: $zone, group: $group, user: $user, exercise: $exercise")

    _selectedZone.value = zone
    _zonas.value = zonasList
    _groupId.value = group
    _userId.value = user
    _exerciseId.value = exercise

    blePpg.setSessionParameters(group, user, exercise, zone, zonasList)
    bleCam.setSessionParameters(group, user, exercise, zone, zonasList)
}

    fun startPpgScan() = viewModelScope.launch { blePpg.startScan() }
    fun connectPpg(device: BluetoothDevice) = viewModelScope.launch { blePpg.connectPpg(device) }

    fun startCamScan() = viewModelScope.launch { bleCam.startScan() }
    fun connectCam(device: BluetoothDevice) = viewModelScope.launch { bleCam.connectCam(device) }

    fun prepareForWorkout() {
        viewModelScope.launch(Dispatchers.IO) { // Certifique-se de usar Dispatchers.IO
            try {
                blePpg.startActivity()
                bleCam.startActivity()
                MqttManager.connect()
                Log.d("RealTimeViewModel", "Preparação para treino concluída")
            } catch (e: Exception) {
                Log.e("RealTimeViewModel", "Erro ao preparar para treino", e)
            }
        }
    }

    fun sendAllConfigs(
        ssid: String,
        password: String
    ) = viewModelScope.launch {
        val ip = accessPointIp.value
        // Using default port 8080 since we don't have access to WebSocketViewModel
        val currentPort = 8080
        // Use port with IP address
        val ipWithPort = "$ip:$currentPort"
        android.util.Log.d("RealTimeViewModel", "Enviando IP com porta: $ipWithPort")
        bleCam.sendAllConfigs(ssid, password, ipWithPort)
    }

    fun getBleManager(): BleManager = bleCam
}
