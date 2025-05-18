package com.example.pecimobileapp.viewmodels

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.example.pecimobileapp.ble.DeviceType
import androidx.lifecycle.viewModelScope
import com.example.pecimobileapp.ble.BleManager
import com.example.pecimobileapp.ble.BleManagerProvider
import com.example.pecimobileapp.mqtt.MqttManager
import com.example.pecimobileapp.services.WebSocketServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RealTimeViewModel(
    app: Application
    ) : AndroidViewModel(app) {
    private val bleProvider = BleManagerProvider.getInstance()
    private val bleManager = bleProvider.getBleManager()
    private val wsService = WebSocketServerService(app)
    private val _nome = MutableStateFlow<String?>(null)

    val nome: StateFlow<String?> = _nome


    val scanResultsPpg: StateFlow<List<ScanResult>> = bleManager.scanResults
    val scanResultsCam: StateFlow<List<ScanResult>> = bleManager.scanResults

    val desempenhoPct: StateFlow<Float> = bleManager.desempenhoPct

    private val _outrosParticipantes = MutableStateFlow<Map<String, ParticipanteData>>(emptyMap())
    val outrosParticipantes: StateFlow<Map<String, ParticipanteData>> = _outrosParticipantes

    private val _isPpgConnected = MutableStateFlow(false)
    val isPpgConnected: StateFlow<Boolean> = _isPpgConnected

    private val _isCamConnected = MutableStateFlow(false)
    val isCamConnected: StateFlow<Boolean> = _isCamConnected

    private val _ppgConnectionLost = MutableStateFlow(false)
    val ppgConnectionLost: StateFlow<Boolean> = _ppgConnectionLost

    private val _camConnectionLost = MutableStateFlow(false)
    val camConnectionLost: StateFlow<Boolean> = _camConnectionLost

    val ppgHeartRate: StateFlow<Int?> = bleManager.ppgHeartRate
    val avgTemp: StateFlow<Float?> = bleManager.avgTemp
    val maxTemp: StateFlow<Float?> = bleManager.maxTemp
    val minTemp: StateFlow<Float?> = bleManager.minTemp

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _selectedZone = MutableStateFlow(1)
    val selectedZone: StateFlow<Int> = _selectedZone
    private val _zonas = MutableStateFlow<List<Pair<String, IntRange>>>(emptyList())
    val zonas: StateFlow<List<Pair<String, IntRange>>> = _zonas

    val currentZone: StateFlow<Int> = combine(ppgHeartRate, zonas) { hr, zonasList ->
        val zona = if (hr == null || hr <= 0) {
            0
        } else {
            val idx = zonasList.indexOfFirst { hr in it.second }
            if (idx >= 0) idx + 1 else 0
        }
        Log.d("RealTimeViewModel", "Zona atual: $zona") // Adiciona o log
        zona
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)


    // Adicionados para persistência de contexto da sessão
    private val _groupId = MutableStateFlow<String?>(null)
    val groupId: StateFlow<String?> = _groupId

    private val _userId = MutableStateFlow("aluno01")
    val userId: StateFlow<String> = _userId

    private val _exerciseId = MutableStateFlow("exercicio_teste")
    val exerciseId: StateFlow<String> = _exerciseId

    private val _activityStarted = MutableStateFlow(false)
    val activityStarted: StateFlow<Boolean> = _activityStarted

    val readyToStart: StateFlow<Boolean> = combine(isPpgConnected, isCamConnected) { ppg, cam -> ppg && cam }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val accessPointIp: StateFlow<String> = flow {
        emit(wsService.getDeviceIpAddress())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        // Observar mudanças de conexão através do viewModelScope
        viewModelScope.launch {
            bleManager.isConnected.collect { isConnected ->
                when (bleManager.currentDeviceType) {
                    DeviceType.PPG -> {
                        _isPpgConnected.value = isConnected
                        if (!isConnected) {
                            _ppgConnectionLost.value = true
                        }
                    }
                    DeviceType.THERMAL_CAMERA -> {
                        _isCamConnected.value = isConnected
                        if (!isConnected) {
                            _camConnectionLost.value = true
                        }
                    }
                    null -> {
                        // Device type not set
                    }
                }
            }
        }

        // Observar perda de conexão
        viewModelScope.launch {
            bleManager.connectionLost.collect { isLost ->
                when (bleManager.currentDeviceType) {
                    DeviceType.PPG -> _ppgConnectionLost.value = isLost
                    DeviceType.THERMAL_CAMERA -> _camConnectionLost.value = isLost
                    null -> {}
                }
            }
        }
    }


    fun loadUserProfile(context: Context) {
    viewModelScope.launch {
        ProfilePreferences.nomeFlow(context).collectLatest { nome ->
            _nome.value = nome
            Log.d("RealTimeViewModel", "Nome carregado: $nome")
        }
        ProfilePreferences.userIdFlow(context).collectLatest { id ->
            _userId.value = id ?: "default_user"
            Log.d("RealTimeViewModel", "user_id carregado: $id")
        }
    }
}

    fun connectPpg(device: BluetoothDevice) {
        bleManager.connectPpg(device)
        _isPpgConnected.value = true
        _ppgConnectionLost.value = false
    }

    fun connectCam(device: BluetoothDevice) {
        bleManager.connectCam(device)
        _isCamConnected.value = true
        _camConnectionLost.value = false
    }

    fun startPpgScan() {
        viewModelScope.launch {
            _scanning.value = true
            bleManager.startScan()
            kotlinx.coroutines.delay(10000) // 10 segundos
            _scanning.value = false
        }
    }

    fun startCamScan() {
        viewModelScope.launch {
            _scanning.value = true
            bleManager.startScan()
            kotlinx.coroutines.delay(10000) // 10 segundos
            _scanning.value = false
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

    fun disconnectPpg() {
        bleManager.disconnect()
        _isPpgConnected.value = false
        _ppgConnectionLost.value = true
    }

    fun disconnectCam() {
        bleManager.disconnect()
        _isCamConnected.value = false
        _camConnectionLost.value = true
    }

    fun setWorkoutParameters(
    zone: Int,
    zonasList: List<Pair<String, IntRange>>,
    group: String? = null,
    user: String,
    exercise: String,
    nome: String?
) {
    Log.d("RealTimeViewModel", "setWorkoutParameters chamado com: zone=$zone, group=$group, user=$user, exercise=$exercise")
    Log.d("RealTimeViewModel", "zonasList=$zonasList")

    _selectedZone.value = zone
    _zonas.value = zonasList
    // Se não houver grupo, groupId será igual ao userId
    _groupId.value = group ?: user
    _userId.value = user
    _exerciseId.value = exercise
    _nome.value = nome

    bleManager.setSessionParameters(group, user, exercise, zone, zonasList, nome)
}

    fun prepareForWorkout() {
        viewModelScope.launch(Dispatchers.IO) { // Certifique-se de usar Dispatchers.IO
            try {
                bleManager.startActivity()
                MqttManager.connect()
                Log.d("RealTimeViewModel", "Preparação para treino concluída")
            } catch (e: Exception) {
                Log.e("RealTimeViewModel", "Erro ao preparar para treino", e)
            }
        }
    }

    fun subscribeToGroup(groupId: String) {
    MqttManager.subscribe("/group/$groupId/data") { message ->
        try {
            val json = org.json.JSONObject(message)
            val nome = json.optString("nome", json.optString("user_uid", ""))
            val rating = json.optDouble("rating", 0.0).toFloat()
            val zonaAlvo = json.optInt("zona_alvo", 0)
            val userId = json.optString("user_uid", "")

            // Não atualize o próprio usuário
            if (userId != _userId.value) {
                val updated = _outrosParticipantes.value.toMutableMap()
                updated[nome] = ParticipanteData(rating, zonaAlvo)
                _outrosParticipantes.value = updated
            }
        } catch (e: Exception) {
            Log.e("RealTimeViewModel", "Erro ao processar mensagem do grupo", e)
        }
    }
}


    fun updateParticipanteRating(userId: String, rating: Float) {
    val updatedMap = _outrosParticipantes.value.toMutableMap()
    val participanteAtual = updatedMap[userId]
    updatedMap[userId] = ParticipanteData(
        rating = rating,
        zonaAlvo = participanteAtual?.zonaAlvo ?: 0 // Mantém a zona atual ou usa 0 como padrão
    )
    _outrosParticipantes.value = updatedMap
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

    fun resetSession() {
        _outrosParticipantes.value = emptyMap()

        MqttManager.WorkoutSessionManager.resetSession()
        Log.d("RealTimeViewModel", "Sessão redefinida")
    }

    fun getBleManager(): BleManager = bleManager
}
data class ParticipanteData(
    val rating: Float,
    val zonaAlvo: Int
)