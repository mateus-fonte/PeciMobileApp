package com.example.pecimobileapp.viewmodels

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pecimobileapp.ble.BleManagerProvider
import com.example.pecimobileapp.mqtt.MqttManager
import com.example.pecimobileapp.services.WebSocketServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RealTimeViewModel(app: Application) : AndroidViewModel(app) {
    private val TAG = "RealTimeViewModel"

    // Usar a única instância compartilhada do BleManager
    private val bleManager = BleManagerProvider.getInstance()
    private val wsService = WebSocketServerService(app)

    private val _currentDeviceType = MutableStateFlow<BleManagerProvider.DeviceType?>(null)
    val currentDeviceType: StateFlow<BleManagerProvider.DeviceType?> = _currentDeviceType.asStateFlow()

    val scanResultsPpg: StateFlow<List<ScanResult>> = bleManager.scanResults
    val scanResultsCam: StateFlow<List<ScanResult>> = bleManager.scanResults

    val isPpgConnected = bleManager.isConnected.combine(_currentDeviceType) { isConnected, type ->
        isConnected && type == BleManagerProvider.DeviceType.PPG
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isCamConnected = bleManager.isConnected.combine(_currentDeviceType) { isConnected, type ->
        isConnected && type == BleManagerProvider.DeviceType.THERMAL_CAMERA
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val ppgConnectionLost: StateFlow<Boolean> = bleManager.connectionLost
    val camConnectionLost: StateFlow<Boolean> = bleManager.connectionLost

    val ppgHeartRate: StateFlow<Int?> = bleManager.ppgHeartRate
    val avgTemp: StateFlow<Float?> = bleManager.avgTemp
    val maxTemp: StateFlow<Float?> = bleManager.maxTemp
    val minTemp: StateFlow<Float?> = bleManager.minTemp

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

    init {
        Log.d(TAG, "Inicializando RealTimeViewModel com BleManager compartilhado")
    }

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
        bleManager.startActivity()
    }

    fun stopActivity() {
        _activityStarted.value = false
        bleManager.stopActivity()
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

        bleManager.setSessionParameters(group, user, exercise, zone, zonasList)
    }

    fun startPpgScan() = viewModelScope.launch {
        Log.d(TAG, "Iniciando scan para dispositivo PPG")
        bleManager.startScan()
    }

    fun connectPpg(device: BluetoothDevice) = viewModelScope.launch {
        Log.d(TAG, "Iniciando conexão com dispositivo PPG: ${device.name ?: device.address}")
        _currentDeviceType.value = BleManagerProvider.DeviceType.PPG
        bleManager.connectPpg(device)
    }

    fun disconnectPpg() = viewModelScope.launch {
        Log.d(TAG, "Desconectando dispositivo PPG")
        bleManager.disconnect()
        _currentDeviceType.value = null
    }

    fun startCamScan() = viewModelScope.launch {
        Log.d(TAG, "Iniciando scan para câmera térmica")
        bleManager.startScan()
    }

    fun connectCam(device: BluetoothDevice) = viewModelScope.launch {
        Log.d(TAG, "Iniciando conexão com câmera térmica: ${device.name ?: device.address}")
        _currentDeviceType.value = BleManagerProvider.DeviceType.THERMAL_CAMERA
        bleManager.connectCam(device)
    }

    fun disconnectCam() = viewModelScope.launch {
        Log.d(TAG, "Desconectando câmera térmica")
        bleManager.disconnect()
        _currentDeviceType.value = null
    }

    fun disconnect() = viewModelScope.launch {
        Log.d(TAG, "Desconectando dispositivo atual: ${_currentDeviceType.value}")
        bleManager.disconnect()
        _currentDeviceType.value = null
    }

    fun prepareForWorkout() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Preparando workout com dispositivo: ${_currentDeviceType.value}")
                bleManager.startActivity()
                MqttManager.connect()
                Log.d(TAG, "Preparação para workout concluída")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao preparar workout", e)
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
        bleManager.sendAllConfigs(ssid, password, ipWithPort)
    }

    fun getBleManager(): BleManagerProvider = bleManager
}
