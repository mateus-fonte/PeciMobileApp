package com.example.pecimobileapp.viewmodels

import android.app.Application
import android.graphics.Bitmap
import android.bluetooth.BluetoothDevice
import android.content.SharedPreferences
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.example.pecimobileapp.services.WebSocketServerService
import kotlinx.coroutines.flow.*
import com.example.pecimobileapp.utils.OpenCVUtils
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.isActive
import com.example.pecimobileapp.ble.BleManager
import com.example.pecimobileapp.ble.BleManagerProvider
import com.example.pecimobileapp.ble.DeviceType

/**
 * ViewModel para gerenciar o servidor WebSocket e os dados recebidos
 */
class WebSocketViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "WebSocketViewModel"

    // SharedPreferences para armazenamento persistente
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val CAMERA_MAC_ADDRESS_KEY = "thermal_camera_mac_address"
    private val CAMERA_NAME_KEY = "thermal_camera_name"

    // Estados possíveis da configuração WiFi para ESP32
    sealed class WifiConfigStatus {
        object NotConfigured : WifiConfigStatus()
        object Configuring : WifiConfigStatus()
        object Configured : WifiConfigStatus()
        data class Failed(val reason: String) : WifiConfigStatus()
    }    // Serviço WebSocket
    private val webSocketServer = WebSocketServerService(application.applicationContext)

    // Usar BleManager compartilhado do Provider (já deve estar inicializado via BluetoothApplication)
    private val bleManager = BleManagerProvider.getInstance().getBleManager()

    // Observável para o estado de conexão da câmera
    private val _isCameraConnected = MutableStateFlow(false)
    val isCameraConnected: StateFlow<Boolean> = _isCameraConnected

    // Armazena a última temperatura válida do rosto principal
    private var lastValidFaceTemperature: Float = 0f

    // Armazena a porta atual do servidor WebSocket
    private val _currentServerPort = MutableStateFlow<Int>(8080)
    val currentServerPort: StateFlow<Int> = _currentServerPort

    // Estado do servidor
    private val _serverState = MutableStateFlow<WebSocketServerService.ServerState>(WebSocketServerService.ServerState.Stopped)
    val serverState: StateFlow<WebSocketServerService.ServerState> = _serverState
    
    // Estado do Access Point
    private val _isApActive = MutableStateFlow(false)
    val isApActive: StateFlow<Boolean> = _isApActive
    
    // Estado das configurações WiFi do ESP32
    private val _wifiConfigStatus = MutableStateFlow<WifiConfigStatus>(WifiConfigStatus.NotConfigured)
    val wifiConfigStatus: StateFlow<WifiConfigStatus> = _wifiConfigStatus

    // Estado para progresso da configuração (combinando com o progresso do BleManager)
    private val _setupProgress = MutableStateFlow(0f)
    val setupProgress: StateFlow<Float> = _setupProgress
    
    // Estado para indicar se uma imagem foi recebida
    private val _imageReceived = MutableStateFlow(false)
    val imageReceived: StateFlow<Boolean> = _imageReceived
    
    // Estado para armazenar qualquer erro de conexão
    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError

    // Observáveis para a UI
    val isServerRunning: StateFlow<Boolean> = webSocketServer.isRunning
    val latestCameraImage: StateFlow<Pair<Bitmap?, String>> = webSocketServer.latestCameraImage
    val latestThermalData: StateFlow<Pair<FloatArray?, String>> = webSocketServer.latestThermalData
    val connectionStats: StateFlow<WebSocketServerService.ConnectionStats> = webSocketServer.connectionStats
    
    // Imagem processada com OpenCV (com detecção facial e sobreposição térmica)
    val processedImage: StateFlow<Pair<Bitmap?, List<OpenCVUtils.FaceData>>> = webSocketServer.processedImage

    // Flag para controlar se deve mostrar apenas imagem térmica com 100% de opacidade
    private val _showOnlyThermal = MutableStateFlow(true)
    val showOnlyThermal: StateFlow<Boolean> = _showOnlyThermal

    /**
     * Ativa ou desativa a exibição apenas da imagem térmica com opacidade 100%
     * @param enable True para mostrar apenas imagem térmica, False para mostrar sobreposição normal
     */
    fun setShowOnlyThermal(enable: Boolean) {
        _showOnlyThermal.value = enable
        // Atualiza o serviço WebSocket com a nova configuração
        webSocketServer.setShowOnlyThermal(enable)
    }

    /**
     * Verifica se a permissão BLUETOOTH_CONNECT está concedida
     * @return true se a permissão está concedida, false caso contrário
     */
    private fun hasBluetoothPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val context = getApplication<Application>().applicationContext
            return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return true // Para versões anteriores ao Android 12
    }

    /**
     * Acessa o nome do dispositivo com tratamento de permissão
     * @param device O dispositivo Bluetooth
     * @return O nome do dispositivo ou "Câmera Térmica" como fallback
     */
    private fun getDeviceName(device: BluetoothDevice): String {
        return try {
            if (hasBluetoothPermission()) {
                device.name ?: "Câmera Térmica"
            } else {
                "Câmera Térmica"
            }
        } catch (e: SecurityException) {
            "Câmera Térmica"
        }
    }

    /**
     * Acessa o endereço MAC do dispositivo com tratamento de permissão
     * @param device O dispositivo Bluetooth
     * @return O endereço MAC do dispositivo ou null em caso de erro
     */
    private fun getDeviceAddress(device: BluetoothDevice): String? {
        return try {
            if (hasBluetoothPermission()) {
                device.address
            } else {
                null
            }
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * Armazena o último dispositivo Bluetooth da câmera térmica conectado
     * Usado para garantir que a referência persista entre reconexões automáticas
     */
    private var _lastConnectedThermalCamera: BluetoothDevice? = null
      /**
     * Define o dispositivo da câmera térmica que está atualmente conectado
     * Isso é crucial para que a UI possa recuperar a referência durante reconexões
     * Também salva o endereço MAC e nome do dispositivo nas SharedPreferences para persistência
     */
    fun setThermalCameraDevice(device: BluetoothDevice) {
        try {
            _lastConnectedThermalCamera = device
            
            // Salvar o endereço MAC e nome nas SharedPreferences para persistência
            val deviceAddress = getDeviceAddress(device)
            val deviceName = getDeviceName(device)
            
            if (deviceAddress != null) {
                sharedPreferences.edit()
                    .putString(CAMERA_MAC_ADDRESS_KEY, deviceAddress)
                    .putString(CAMERA_NAME_KEY, deviceName)
                    .apply()
                
                // Registrar no BleManagerProvider
                BleManagerProvider.getInstance().registerConnectedDevice(
                    device, 
                    DeviceType.THERMAL_CAMERA
                )
                
                android.util.Log.d(TAG, "Dispositivo da câmera térmica armazenado em SharedPreferences: $deviceName ($deviceAddress)")
            } else {
                android.util.Log.e(TAG, "Não foi possível obter o endereço MAC do dispositivo")
            }
        } catch (e: SecurityException) {
            android.util.Log.e(TAG, "Erro de permissão ao acessar dispositivo Bluetooth: ${e.message}", e)
        }
    }
      /**
     * Obtém o dispositivo da câmera térmica atualmente conectado
     * Primeiro tenta usar o BleManagerProvider, depois a referência em memória, 
     * e por último tenta reconstruir o dispositivo a partir do endereço MAC salvo
     * 
     * @return O dispositivo Bluetooth da câmera ou null se não houver conexão ativa
     */
    fun getThermalCameraDevice(): BluetoothDevice? {
        // 1. Primeiro, tenta obter do BleManagerProvider (a fonte mais confiável)
        val providerDevice = BleManagerProvider.getInstance().getConnectedDevice(DeviceType.THERMAL_CAMERA)
        if (providerDevice != null) {
            // Atualiza a referência interna para manter consistência
            _lastConnectedThermalCamera = providerDevice
            return providerDevice
        }
        
        // 2. Se já temos uma referência em memória, retorná-la
        if (_lastConnectedThermalCamera != null) {
            return _lastConnectedThermalCamera
        }
        
        // 3. Se não temos um dispositivo salvo, tente recuperar do BleManager
        if (bleManager.isConnected.value) {
            try {
                val currentType = bleManager.currentDeviceType
                if (currentType == DeviceType.THERMAL_CAMERA) {
                    val field = BleManager::class.java.getDeclaredField("lastDevice")
                    field.isAccessible = true
                    _lastConnectedThermalCamera = field.get(bleManager) as? BluetoothDevice
                    if (_lastConnectedThermalCamera != null) {
                        // Registra no provider para consistência futura
                        BleManagerProvider.getInstance().registerConnectedDevice(
                            _lastConnectedThermalCamera!!,
                            DeviceType.THERMAL_CAMERA
                        )
                        return _lastConnectedThermalCamera
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Não foi possível recuperar o dispositivo do BleManager", e)
            }
        }
        
        // 4. Se ainda não temos o dispositivo, tentar reconstruí-lo a partir do endereço MAC salvo
        try {
            val savedMacAddress = sharedPreferences.getString(CAMERA_MAC_ADDRESS_KEY, null)
            if (savedMacAddress != null) {
                android.util.Log.d(TAG, "Tentando recuperar dispositivo do endereço MAC salvo: $savedMacAddress")
                
                // Verificar se temos a permissão de Bluetooth antes de prosseguir
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    // Para Android 12+ (API 31+), precisamos verificar a permissão BLUETOOTH_CONNECT
                    val context = getApplication<Application>().applicationContext
                    if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != 
                            android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        android.util.Log.w(TAG, "Permissão BLUETOOTH_CONNECT não concedida")
                        return null
                    }
                }
                
                try {
                    // Obter o adaptador Bluetooth do sistema para recuperar o dispositivo pelo endereço
                    val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                    if (bluetoothAdapter != null) {
                        // Usar BluetoothAdapter.getRemoteDevice(address) para reconstruir o objeto do dispositivo
                        val device = bluetoothAdapter.getRemoteDevice(savedMacAddress)
                        _lastConnectedThermalCamera = device
                        // Registrar no provider para consistência futura
                        BleManagerProvider.getInstance().registerConnectedDevice(
                            device,
                            DeviceType.THERMAL_CAMERA
                        )
                        android.util.Log.d(TAG, "Dispositivo reconstruído com sucesso a partir do endereço MAC salvo")
                        return device
                    }
                } catch (e: SecurityException) {
                    // Captura especificamente SecurityException que pode ser lançada se a permissão não foi concedida
                    android.util.Log.e(TAG, "Erro de permissão ao acessar Bluetooth: ${e.message}", e)
                    return null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Erro ao recuperar dispositivo a partir do endereço MAC salvo", e)
        }
        
        return null
    }    // Inicializa o monitoramento de imagens recebidas
    init {
        Log.d(TAG, "Inicializando WebSocketViewModel com BleManager compartilhado")
        
        // Garantir que o modo de visualização térmica esteja ativado desde o início
        setShowOnlyThermal(true)
        Log.d(TAG, "Modo de visualização térmica exclusiva ativado por padrão")

        // Observa as imagens da câmera para atualizar o status de imageReceived
        viewModelScope.launch {
            latestCameraImage.collect { (bitmap, _) ->
                if (bitmap != null && !_imageReceived.value) {
                    _imageReceived.value = true
                    _connectionError.value = null // Limpa qualquer erro quando uma imagem é recebida
                }
            }
        }
        
        // Observa as imagens processadas (incluindo imagens térmicas) para atualizar o status de imageReceived
        viewModelScope.launch {
            processedImage.collect { (bitmap, _) ->
                if (bitmap != null && !_imageReceived.value) {
                    _imageReceived.value = true
                    _connectionError.value = null // Limpa qualquer erro quando uma imagem é recebida
                    Log.d(TAG, "Imagem térmica processada recebida, atualizando imageReceived")
                }
            }
        }
        
        // Observa o estado do servidor para detectar e reportar erros
        viewModelScope.launch {
            serverState.collect { state ->
                when (state) {
                    is WebSocketServerService.ServerState.HotspotNotActive -> {
                        _connectionError.value = "O hotspot do dispositivo não está ativo. Por favor, ative-o e tente novamente."
                    }
                    is WebSocketServerService.ServerState.Error -> {
                        _connectionError.value = "Erro na conexão WebSocket: ${state.message}"
                    }
                    is WebSocketServerService.ServerState.Stopped -> {
                        // Quando o servidor é parado intencionalmente, não mostramos erro
                        if (_connectionError.value?.contains("hotspot") != true) {
                            _connectionError.value = null
                        }
                        _imageReceived.value = false
                    }
                    else -> {
                        // Para outros estados, não fazemos nada
                    }
                }
            }
        }
        
        // Observa a contagem de clientes para detectar desconexões
        viewModelScope.launch {
            var lastClientCount = 0
            connectionStats.collect { stats ->
                if (lastClientCount > 0 && stats.clientsCount == 0) {
                    _connectionError.value = "A câmera térmica foi desconectada do servidor WebSocket."
                }
                lastClientCount = stats.clientsCount
            }
        }

        // Observa o estado de conexão do BleManager da câmera
        viewModelScope.launch {
            bleManager.isConnected.collect { isConnected -> 
                _isCameraConnected.value = isConnected
                
                // Se a conexão for perdida, atualiza o estado
                if (!isConnected && _isCameraConnected.value) {
                    android.util.Log.d(TAG, "Conexão BLE com a câmera perdida")
                }
            }
        }
        
        // Observa se a conexão foi perdida explicitamente
        viewModelScope.launch {
            bleManager.connectionLost.collect { isLost ->
                if (isLost && _wifiConfigStatus.value !is WifiConfigStatus.Configuring) {
                    android.util.Log.d(TAG, "BleManager reportou perda de conexão")
                    _connectionError.value = "A conexão Bluetooth com a câmera foi perdida"
                }
            }
        }
    }
    
    /**
     * Inicia o servidor WebSocket
     */
    fun startServer(port: Int): Pair<Boolean, WebSocketServerService.ServerState> {
        val result = webSocketServer.startServer(port) { actualPort ->
            // Armazena a porta real em que o servidor foi iniciado
            _currentServerPort.value = actualPort
            android.util.Log.d(TAG, "Servidor WebSocket iniciado na porta: $actualPort")
        }
        if (result) {
            _serverState.value = WebSocketServerService.ServerState.Running
            _connectionError.value = null
        } else {
            // Em vez de automaticamente assumir que o hotspot não está ativo,
            // definimos um estado de erro genérico para fornecer feedback mais preciso
            _serverState.value = WebSocketServerService.ServerState.Error("Não foi possível iniciar o servidor WebSocket")
            _connectionError.value = "Falha ao iniciar servidor WebSocket. Verifique as configurações de rede e tente novamente."
        }
        return Pair(result, _serverState.value)
    }

    /**
     * Para o servidor WebSocket
     */
    fun stopServer() = viewModelScope.launch {
        webSocketServer.stopServer()
        _serverState.value = WebSocketServerService.ServerState.Stopped
        _imageReceived.value = false
    }

    /**
     * Limpa erros de conexão
     */
    fun clearConnectionError() {
        _connectionError.value = null
    }

    // — NOVO — derivamos esses StateFlows para usar diretamente em Compose:

    /**
     * True se houver pelo menos um cliente WS conectado
     */
    val isWsConnected: StateFlow<Boolean> = connectionStats
        .map { stats -> stats.clientsCount > 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Média dinâmica de temperatura térmica
     */
    val avgThermalFlow: StateFlow<Float> = latestThermalData
        .map { (arr, _) -> arr?.average()?.toFloat() ?: 0f }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    /**
     * Máxima dinâmica de temperatura térmica
     */
    val maxThermalFlow: StateFlow<Float> = latestThermalData
        .map { (arr, _) -> arr?.maxOrNull()?.toFloat() ?: 0f }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    /**
     * Mínima dinâmica de temperatura térmica
     */
    val minThermalFlow: StateFlow<Float> = latestThermalData
        .map { (arr, _) -> arr?.minOrNull()?.toFloat() ?: 0f }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    // Limpa recursos ao destruir o ViewModel
    override fun onCleared() {
        super.onCleared()
        webSocketServer.stopServer()
    }
    
    /**
     * Verifica se o Access Point está ativo
     * @return true se o AP estiver ativo, false caso contrário
     */
    fun checkAccessPointStatus(): Boolean {
        val isActive = webSocketServer.isAccessPointActive()
        _isApActive.value = isActive
        return isActive
    }
    
    /**
     * Obtém o IP da interface ap0 (Access Point)
     * @return IP da interface ap0 ou null se não estiver disponível
     */
    fun getAccessPointIp(): String? {
        return webSocketServer.getDeviceIpAddress()
    }
    
    /**
     * Inicia o servidor WebSocket após verificar se o AP está ativo
     * Se o AP não estiver ativo, retorna false e não inicia o servidor
     */
    fun startServerWithApCheck(port: Int = 8080): Pair<Boolean, WebSocketServerService.ServerState> {
        // Verifica primeiro se o AP está ativo
        val isApActive = checkAccessPointStatus()
        android.util.Log.d(TAG, "Verificação de AP antes de iniciar servidor: ${if (isApActive) "ATIVO" else "INATIVO"}")
        
        if (!isApActive) {
            _serverState.value = WebSocketServerService.ServerState.HotspotNotActive
            _connectionError.value = "O hotspot do dispositivo não está ativo. Por favor, ative-o para usar a câmera térmica."
            return Pair(false, _serverState.value)
        }
        
        // Se o AP estiver ativo, tenta iniciar o servidor
        val result = startServer(port)
        
        // Se o servidor iniciou com sucesso, garantimos que o estado do servidor seja Running
        // Isso evita condições de corrida onde o servidor inicia mas depois é marcado como parado
        if (result.first) {
            _serverState.value = WebSocketServerService.ServerState.Running
            
            // Garantir que, após iniciar o servidor com sucesso, quaisquer erros anteriores sejam limpos
            _connectionError.value = null
            
            // Iniciar uma coroutine para verificar se o servidor continua ativo após inicialização bem-sucedida
            viewModelScope.launch {
                // Pequeno atraso para garantir que o servidor tenha tempo para estabilizar
                delay(500)
                // Verifica se o servidor está realmente rodando (via serviço)
                if (webSocketServer.isRunning.value) {
                    android.util.Log.d(TAG, "Servidor WebSocket está rodando após verificação adicional")
                    // Forçar o estado como Running para evitar falsos negativos
                    _serverState.value = WebSocketServerService.ServerState.Running
                }
            }
        }
        
        return result
    }
    
      /**
     * Envia a configuração WiFi para o ESP32 conectado e inicia o servidor WebSocket
     */ 
    private fun sendWifiConfig(ssid: String, password: String) = viewModelScope.launch {
        try {
            _wifiConfigStatus.value = WifiConfigStatus.Configuring
            _setupProgress.value = 0f

            // Verificar conexão BLE
            if (!bleManager.isConnected.value) {
                _wifiConfigStatus.value = WifiConfigStatus.Failed("Dispositivo BLE não conectado")
                _connectionError.value = "Câmera não conectada via Bluetooth. Reconecte e tente novamente."
                return@launch
            }
            
            // Verificar se o AP está ativo
            if (!checkAccessPointStatus()) {
                _wifiConfigStatus.value = WifiConfigStatus.Failed("Access Point não está ativo")
                _connectionError.value = "Access Point não está ativo. Ative o hotspot e tente novamente."
                return@launch
            }
            
            // Obter o IP da interface do access point
            val ip = getAccessPointIp()
            if (ip == null) {
                _wifiConfigStatus.value = WifiConfigStatus.Failed("Não foi possível obter o IP do Access Point")
                _connectionError.value = "Não foi possível obter o IP do Access Point."
                return@launch
            }
            
            // Iniciar servidor WebSocket primeiro para garantir que está pronto
            val serverStarted = startServerWithApCheck()
            if (!serverStarted.first) {
                _wifiConfigStatus.value = WifiConfigStatus.Failed("Não foi possível iniciar o servidor WebSocket")
                return@launch
            }
            
            // Coletar estado inicial do progresso da configuração
            var progressJob = viewModelScope.launch {
                bleManager.configProgress.collect { progress ->
                    _setupProgress.value = progress
                }
            }
            
            // Enviar configurações para o dispositivo com o IP e porta corretos
            val ipWithPort = "${ip}:${_currentServerPort.value}"
            android.util.Log.d(TAG, "Enviando configuração WiFi: SSID=$ssid, IP=$ipWithPort")
            
            // Configurar um timeout para o envio das configurações
            var configSuccess = false
            try {
                withTimeout(30000) { // 30 segundos de timeout
                    bleManager.sendAllConfigs(ssid, password, ipWithPort)
                    // Aguardar até que o progresso atinja 100% ou ocorra timeout
                    var attempts = 0
                    while (bleManager.configProgress.value < 1f && attempts < 60) {
                        delay(500)
                        attempts++
                    }
                    configSuccess = bleManager.configProgress.value >= 1f
                }
            } catch (e: TimeoutCancellationException) {
                _wifiConfigStatus.value = WifiConfigStatus.Failed("Timeout ao enviar configurações")
                _connectionError.value = "Timeout ao enviar configurações WiFi. Tente novamente."
                return@launch
            } finally {
                progressJob.cancel() // Parar de monitorar o progresso
            }
            
            if (!configSuccess) {
                _wifiConfigStatus.value = WifiConfigStatus.Failed("Falha ao enviar configurações")
                _connectionError.value = "Falha ao enviar configurações WiFi. Verifique a conexão Bluetooth e tente novamente."
                return@launch
            }
            
            _wifiConfigStatus.value = WifiConfigStatus.Configuring
            _connectionError.value = null
            
            // Aguardar a conexão do ESP32 ao WebSocket
            withTimeout(10000) { // 10 segundos de timeout
                var attempts = 0
                while (webSocketServer.connectionStats.value.clientsCount == 0 && attempts < 20) {
                    delay(500)
                    attempts++
                }
            }
            
            // Verificar se o ESP32 conectou ao WebSocket
            if (webSocketServer.connectionStats.value.clientsCount == 0) {
                _wifiConfigStatus.value = WifiConfigStatus.Failed("ESP32 não se conectou ao WebSocket")
                _connectionError.value = "ESP32 não se conectou ao servidor. Verifique as credenciais WiFi."
            } else {
                _wifiConfigStatus.value = WifiConfigStatus.Configured
                _setupProgress.value = 1f
            }
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Erro ao enviar configuração WiFi", e)
            _wifiConfigStatus.value = WifiConfigStatus.Failed(e.message ?: "Erro desconhecido")
            _connectionError.value = "Erro ao configurar WiFi: ${e.message}"
        }
    }
    
    /**
     * Configura o ESP32 para se conectar ao Access Point e iniciar o servidor WebSocket
     * 
     * Fluxo:
     * 1. Verifica se o Access Point está ativo
     * 2. Obtém o IP da interface ap0
     * 3. Inicia o servidor WebSocket para obter a porta real
     * 4. Envia as configurações WiFi para o ESP32 via BLE
     * 5. Aguarda até que o ESP32 se conecte e inicia o servidor WebSocket
     * 
     * @param bleManager gerenciador BLE para enviar dados (opcional, se não fornecido usa o interno)
     * @param ssid SSID do Access Point
     * @param password senha do Access Point
     */
     suspend fun configureEsp32AndStartServer(
        bleManager: com.example.pecimobileapp.ble.BleManager? = null, 
        ssid: String, 
        password: String
    ): Boolean {
        try {
            Log.d(TAG, "Iniciando configuração do ESP32")
            
            // Usar o bleManager fornecido ou o interno se não for fornecido
            val manager = bleManager ?: this@WebSocketViewModel.bleManager
            
            // Verificar estado atual da conexão
            val isConnected = manager.isConnected.value
            Log.d(TAG, "Estado da conexão BLE: ${if (isConnected) "Conectado" else "Desconectado"}")
            val lastCameraAddress = _lastConnectedThermalCamera?.let { getDeviceAddress(it) }
            Log.d(TAG, "Última câmera conhecida: ${lastCameraAddress ?: "Nenhuma"}")
            
            // Verificar se o SSID e senha são válidos
            if (ssid.isBlank() || password.isBlank()) {
                Log.e(TAG, "SSID ou senha inválidos")
                _wifiConfigStatus.value = WifiConfigStatus.Failed(
                    "SSID e senha não podem estar vazios"
                )
                return false
            }
            
            // Tentar reconectar se necessário
            if (!isConnected && lastCameraAddress != null) {
                Log.d(TAG, "Tentando reconectar à câmera...")
                if (reconnectThermalCamera()) {
                    // Aguardar até 10 segundos pela reconexão
                    var reconnectAttempts = 0
                    while (!manager.isConnected.value && reconnectAttempts < 20) {
                        delay(500)
                        reconnectAttempts++
                    }
                }
            }
            
            // Verificar novamente a conexão após tentativa de reconexão
            if (!manager.isConnected.value) {
                Log.e(TAG, "Nenhum dispositivo conectado após tentativa de reconexão")
                _wifiConfigStatus.value = WifiConfigStatus.Failed(
                    "Nenhum dispositivo conectado. Por favor, conecte a câmera térmica primeiro."
                )
                return false
            }
            
            // Verificar tipo de dispositivo
            val deviceType = manager.currentDeviceType
            if (deviceType != DeviceType.THERMAL_CAMERA) {
                Log.e(TAG, "Dispositivo conectado não é uma câmera térmica")
                _wifiConfigStatus.value = WifiConfigStatus.Failed(
                    "Dispositivo conectado não é uma câmera térmica"
                )
                return false
            }
            
            // Tentar enviar configurações
            var configSuccess = false
            var lastError: String? = null
            
            try {
                withTimeout(40000) { // 40 segundos de timeout total
                    sendWifiConfig(ssid, password)
                    
                    // Monitorar o status da configuração
                    while (true) {
                        if (!currentCoroutineContext().isActive) {
                            lastError = "Operação cancelada"
                            break
                        }
                        
                        when (val status = _wifiConfigStatus.value) {
                            is WifiConfigStatus.Configured -> {
                                configSuccess = true
                                break
                            }
                            is WifiConfigStatus.Failed -> {
                                lastError = status.reason
                                Log.e(TAG, "Falha na configuração: $lastError")
                                break
                            }
                            is WifiConfigStatus.Configuring -> {
                                Log.d(TAG, "Configuração em andamento... Progresso: ${_setupProgress.value * 100}%")
                                delay(500)
                            }
                            else -> delay(500)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                lastError = "Timeout ao configurar ESP32"
                Log.e(TAG, lastError, e)
            }
            
            if (!configSuccess) {
                _wifiConfigStatus.value = WifiConfigStatus.Failed(lastError ?: "Erro desconhecido")
            }
            
            return configSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao configurar ESP32", e)
            _wifiConfigStatus.value = WifiConfigStatus.Failed(e.message ?: "Erro desconhecido")
            return false
        }
    }
    
    /**
     * Prepara o ViewModel para uma nova tentativa de configuração após uma falha
     * Reseta o estado de configuração WiFi para NotConfigured
     */
    fun prepareRetry() {
        _wifiConfigStatus.value = WifiConfigStatus.NotConfigured
        _setupProgress.value = 0f
        _connectionError.value = null
    }
    
    /**
     * Retorna a temperatura do maior rosto detectado (considerando a área do rosto)
     * Se nenhum rosto for detectado, retorna a última temperatura válida medida
     * @return temperatura do maior rosto ou a última temperatura válida
     */
    fun getLargestFaceTemperature(): Float {
        val faceData = processedImage.value.second
        val bitmap = processedImage.value.first
        
        // Se não houver dados de rosto ou a imagem, retorna a última temperatura válida
        if (faceData.isEmpty() || bitmap == null) {
            return lastValidFaceTemperature
        }
        
        // Tamanho mínimo em pixels para considerar um rosto como "próximo" (aproximadamente 3 palmos)
        // Aumentando este valor para detectar apenas rostos realmente próximos
        val MIN_FACE_AREA = if (bitmap.width > 640) 25000 else 12500 // Valores aumentados para maior rigor
        
        // Filtrar rostos por:
        // 1. Temperatura normal humana (35°C a 40°C)
        // 2. Tamanho do rosto (para garantir proximidade)
        val validFaces = faceData.filter { face ->
            val area = face.width * face.height
            val isValidTemperature = face.temperature in 35.0f..40.0f
            val isCloseEnough = area >= MIN_FACE_AREA
            
            // Logar para depuração
            android.util.Log.d("FaceDetection", 
                "Rosto: ${face.x},${face.y} - Área: $area px² - Temp: ${face.temperature}°C - " +
                "Válido: ${isValidTemperature && isCloseEnough}")
            
            isValidTemperature && isCloseEnough
        }
        
        if (validFaces.isNotEmpty()) {
            // Encontrar o rosto válido com a maior área (o mais próximo)
            val largestValidFace = validFaces.maxByOrNull { it.width * it.height }
            
            if (largestValidFace != null) {
                lastValidFaceTemperature = largestValidFace.temperature
                return largestValidFace.temperature
            }
        }
        
        // Se não encontrou rostos válidos, retorna a última temperatura válida
        return lastValidFaceTemperature
    }
    
    /**
     * Verifica se o Access Point está ativo antes de permitir a configuração da câmera
     * Se o AP não estiver ativo, retorna falso indicando que o usuário deve ser avisado
     * 
     * @return Pair<Boolean, String> - Primeiro valor: true se pode prosseguir, false caso contrário
     *                               - Segundo valor: mensagem de erro, ou string vazia se não houver erro
     */
    fun checkBeforeCameraConfig(): Pair<Boolean, String> {
        // Verificar se o Access Point está ativo
        if (!checkAccessPointStatus()) {
            return Pair(false, "O Access Point não está ativo. Por favor, ative o hotspot do dispositivo antes de configurar a câmera.")
        }
        
        // Obter o IP da interface ap0
        val apIp = getAccessPointIp()
        if (apIp == null) {
            return Pair(false, "Não foi possível obter o IP do Access Point. Verifique se o hotspot está configurado corretamente.")
        }
        
        // Se tudo estiver OK, retorna true com mensagem vazia
        return Pair(true, "")
    }

    /**
     * Tenta reconectar à última câmera térmica conhecida se houver uma
     * @return true se a tentativa de reconexão foi iniciada, false caso contrário
     */
    fun reconnectThermalCamera(): Boolean {
        return BleManagerProvider.getInstance().reconnectLastDevice(DeviceType.THERMAL_CAMERA)
    }

    /**
     * Obtém o BleManager principal usado para a câmera térmica
     * Este método é necessário para que a UI possa acessar o estado interno do BleManager
     * durante reconexões automáticas
     */
    fun getBleManager(): BleManager {
        return bleManager
    }
}
