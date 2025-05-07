package com.example.pecimobileapp.viewmodels

import android.app.Application
import android.graphics.Bitmap
import android.bluetooth.BluetoothDevice
import android.content.SharedPreferences
import android.content.Context
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
import com.example.pecimobileapp.ble.BleManager

/**
 * ViewModel para gerenciar o servidor WebSocket e os dados recebidos
 */
class WebSocketViewModel(application: Application) : AndroidViewModel(application) {

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
    }

    // Serviço WebSocket
    private val webSocketServer = WebSocketServerService(application.applicationContext)

    // BleManager para a câmera térmica
    private val bleManager = BleManager(getApplication())

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
        // Verificar se temos as permissões necessárias em Android 12+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val context = getApplication<Application>().applicationContext
            if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != 
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w("WebSocketViewModel", "Permissão BLUETOOTH_CONNECT não concedida para setThermalCameraDevice")
                return
            }
        }
        
        try {
            _lastConnectedThermalCamera = device
            
            // Salvar o endereço MAC e nome nas SharedPreferences para persistência
            sharedPreferences.edit()
                .putString(CAMERA_MAC_ADDRESS_KEY, device.address)
                .putString(CAMERA_NAME_KEY, device.name ?: "Câmera Térmica")
                .apply()
                
            android.util.Log.d("WebSocketViewModel", "Dispositivo da câmera térmica armazenado em SharedPreferences: ${device.name} (${device.address})")
        } catch (e: SecurityException) {
            android.util.Log.e("WebSocketViewModel", "Erro de permissão ao acessar dispositivo Bluetooth: ${e.message}", e)
        }
    }
    
    /**
     * Obtém o dispositivo da câmera térmica atualmente conectado
     * Primeiro tenta usar a referência em memória, depois tenta recuperar do BleManager,
     * e por último tenta reconstruir o dispositivo a partir do endereço MAC salvo
     * 
     * @return O dispositivo Bluetooth da câmera ou null se não houver conexão ativa
     */
    fun getThermalCameraDevice(): BluetoothDevice? {
        // 1. Se já temos uma referência em memória, retorná-la
        if (_lastConnectedThermalCamera != null) {
            return _lastConnectedThermalCamera
        }
        
        // 2. Se não temos um dispositivo salvo, tente recuperar do BleManager
        if (bleManager.isConnected.value) {
            try {
                val field = BleManager::class.java.getDeclaredField("lastDevice")
                field.isAccessible = true
                _lastConnectedThermalCamera = field.get(bleManager) as? BluetoothDevice
                if (_lastConnectedThermalCamera != null) {
                    return _lastConnectedThermalCamera
                }
            } catch (e: Exception) {
                android.util.Log.e("WebSocketViewModel", "Não foi possível recuperar o dispositivo do BleManager", e)
            }
        }
        
        // 3. Se ainda não temos o dispositivo, tentar reconstruí-lo a partir do endereço MAC salvo
        try {
            val savedMacAddress = sharedPreferences.getString(CAMERA_MAC_ADDRESS_KEY, null)
            if (savedMacAddress != null) {
                android.util.Log.d("WebSocketViewModel", "Tentando recuperar dispositivo do endereço MAC salvo: $savedMacAddress")
                
                // Verificar se temos a permissão de Bluetooth antes de prosseguir
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    // Para Android 12+ (API 31+), precisamos verificar a permissão BLUETOOTH_CONNECT
                    val context = getApplication<Application>().applicationContext
                    if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != 
                            android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        android.util.Log.w("WebSocketViewModel", "Permissão BLUETOOTH_CONNECT não concedida")
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
                        android.util.Log.d("WebSocketViewModel", "Dispositivo reconstruído com sucesso a partir do endereço MAC salvo")
                        return device
                    }
                } catch (e: SecurityException) {
                    // Captura especificamente SecurityException que pode ser lançada se a permissão não foi concedida
                    android.util.Log.e("WebSocketViewModel", "Erro de permissão ao acessar Bluetooth: ${e.message}", e)
                    return null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WebSocketViewModel", "Erro ao recuperar dispositivo a partir do endereço MAC salvo", e)
        }
        
        return null
    }
    
    // Inicializa o monitoramento de imagens recebidas
    init {
        // Observa as imagens da câmera para atualizar o status de imageReceived
        viewModelScope.launch {
            latestCameraImage.collect { (bitmap, _) ->
                if (bitmap != null && !_imageReceived.value) {
                    _imageReceived.value = true
                    _connectionError.value = null // Limpa qualquer erro quando uma imagem é recebida
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
                    android.util.Log.d("WebSocketViewModel", "Conexão BLE com a câmera perdida")
                }
            }
        }
        
        // Observa se a conexão foi perdida explicitamente
        viewModelScope.launch {
            bleManager.connectionLost.collect { isLost ->
                if (isLost) {
                    android.util.Log.d("WebSocketViewModel", "BleManager reportou perda de conexão")
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
            android.util.Log.d("WebSocketViewModel", "Servidor WebSocket iniciado na porta: $actualPort")
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
        return webSocketServer.getAp0IpAddress()
    }
    
    /**
     * Inicia o servidor WebSocket após verificar se o AP está ativo
     * Se o AP não estiver ativo, retorna false e não inicia o servidor
     */
    fun startServerWithApCheck(port: Int = 8080): Pair<Boolean, WebSocketServerService.ServerState> {
        // Verifica primeiro se o AP está ativo
        val isApActive = checkAccessPointStatus()
        android.util.Log.d("WebSocketViewModel", "Verificação de AP antes de iniciar servidor: ${if (isApActive) "ATIVO" else "INATIVO"}")
        
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
                    android.util.Log.d("WebSocketViewModel", "Servidor WebSocket está rodando após verificação adicional")
                    // Forçar o estado como Running para evitar falsos negativos
                    _serverState.value = WebSocketServerService.ServerState.Running
                }
            }
        }
        
        return result
    }
    
    /**
     * Prepara a configuração WiFi para o ESP32 usando BLE
     * Esta função deve ser chamada após a conexão BLE com o ESP32 e após o usuário
     * fornecer o SSID e senha do Access Point
     * 
     * @param bleManager gerenciador BLE para enviar os dados
     * @param ssid SSID do Access Point
     * @param password senha do Access Point
     */
    fun prepareWifiConfig(bleManager: Any, ssid: String, password: String) = viewModelScope.launch {
        try {
            _wifiConfigStatus.value = WifiConfigStatus.Configuring
            
            // Verificar se o AP está ativo
            if (!checkAccessPointStatus()) {
                _wifiConfigStatus.value = WifiConfigStatus.Failed("Access Point não está ativo")
                _connectionError.value = "Access Point não está ativo. Ative o hotspot e tente novamente."
                return@launch
            }
            
            // Obter o IP da interface ap0
            val ip = getAccessPointIp()
            if (ip == null) {
                _wifiConfigStatus.value = WifiConfigStatus.Failed("Não foi possível obter o IP do Access Point")
                _connectionError.value = "Não foi possível obter o IP do Access Point."
                return@launch
            }
            
            // Log para debug
            android.util.Log.d("WebSocketViewModel", "Preparando configuração WiFi: SSID=$ssid, IP=$ip")
            
            // Aqui você usaria o bleManager para enviar os dados via BLE
            // Por exemplo: bleManager.sendWifiConfig(ssid, password, ip)
            // Como este código é intermediário, vamos apenas simular o sucesso
            
            _wifiConfigStatus.value = WifiConfigStatus.Configured
            _connectionError.value = null
            
            // Iniciar o servidor WebSocket automaticamente após configurar o WiFi
            startServerWithApCheck()
            
        } catch (e: Exception) {
            android.util.Log.e("WebSocketViewModel", "Erro ao configurar WiFi", e)
            _wifiConfigStatus.value = WifiConfigStatus.Failed("Erro: ${e.message}")
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
     * @param bleManager gerenciador BLE já conectado ao ESP32
     * @param ssid SSID do Access Point
     * @param password senha do Access Point
     */
    fun configureEsp32AndStartServer(bleManager: BleManager, ssid: String, password: String) = viewModelScope.launch {
        try {
            android.util.Log.d("WebSocketViewModel", "====== INICIANDO CONFIGURAÇÃO ESP32 ======")
            android.util.Log.d("WebSocketViewModel", "SSID: $ssid, Senha: ${password.take(2)}***")
            
            // Reseta o progresso da configuração e limpa erros anteriores
            _setupProgress.value = 0f
            _wifiConfigStatus.value = WifiConfigStatus.Configuring
            _connectionError.value = null
            _imageReceived.value = false
            
            // Observa o progresso do BleManager para escritas Bluetooth
            val bleProgressJob = viewModelScope.launch {
                bleManager.configProgress.collect { progress ->
                    // Atualizamos o nosso progresso com base no progresso do BleManager
                    _setupProgress.value = progress * 0.6f  // 60% do progresso total vem da configuração BLE
                }
            }
            
            // 1. Verifica se o Access Point está ativo
            android.util.Log.d("WebSocketViewModel", "Verificando status do Access Point...")
            val isApActive = checkAccessPointStatus()
            android.util.Log.d("WebSocketViewModel", "Status do Access Point: ${if (isApActive) "ATIVO" else "INATIVO"}")
            
            if (!isApActive) {
                android.util.Log.d("WebSocketViewModel", "⚠️ ERRO: Access Point não está ativo. Configure o AP antes de continuar.")
                _wifiConfigStatus.value = WifiConfigStatus.Failed("Access Point não está ativo")
                _connectionError.value = "O hotspot do dispositivo não está ativo. Ative-o antes de continuar."
                bleProgressJob.cancel()
                return@launch
            }
            
            // 2. Obtém o IP da interface ap0
            android.util.Log.d("WebSocketViewModel", "Obtendo IP do Access Point...")
            val apIp = getAccessPointIp()
            android.util.Log.d("WebSocketViewModel", "IP obtido: $apIp")
            
            if (apIp == null) {
                android.util.Log.d("WebSocketViewModel", "⚠️ ERRO: Não foi possível obter o IP do Access Point")
                _wifiConfigStatus.value = WifiConfigStatus.Failed("Não foi possível obter o IP do Access Point")
                _connectionError.value = "Não foi possível obter o IP do hotspot. Verifique as configurações e tente novamente."
                bleProgressJob.cancel()
                return@launch
            }
            
            // 3. PRIMEIRO, inicia o servidor WebSocket para obter a porta real
            android.util.Log.d("WebSocketViewModel", "Iniciando servidor WebSocket...")
            
            try {
                // Iniciar o servidor WebSocket
                val serverResult = startServerWithApCheck()
                android.util.Log.d("WebSocketViewModel", "Resultado do servidor: $serverResult")
                
                if (serverResult.first) {
                    android.util.Log.d("WebSocketViewModel", "✅ Servidor WebSocket iniciado com sucesso na porta: ${_currentServerPort.value}")
                    // Servidor iniciado, avançamos para 80%
                    _setupProgress.value = 0.8f
                    
                    // 4. DEPOIS, envia as configurações WiFi para o ESP32 via BLE com a porta correta
                    android.util.Log.d("WebSocketViewModel", "Enviando configurações WiFi para ESP32: SSID=$ssid, IP=$apIp")
                    try {
                        // Incluir a porta real onde o servidor foi iniciado
                        val ipWithPort = "$apIp:${_currentServerPort.value}"
                        android.util.Log.d("WebSocketViewModel", "Enviando IP com porta real: $ipWithPort")
                        // Tentar usar o método sendAllConfigs do BleManager
                        bleManager.sendAllConfigs(ssid, password, ipWithPort)
                        android.util.Log.d("WebSocketViewModel", "Comando de envio de configurações executado")
                    } catch (e: Exception) {
                        android.util.Log.e("WebSocketViewModel", "⚠️ ERRO ao enviar configurações: ${e.message}", e)
                        _wifiConfigStatus.value = WifiConfigStatus.Failed("Erro ao enviar configurações: ${e.message}")
                        _connectionError.value = "Erro ao enviar configurações para a câmera: ${e.message}"
                        bleProgressJob.cancel()
                        return@launch
                    }
                    
                    // Monitorar o status do envio das configurações
                    android.util.Log.d("WebSocketViewModel", "Monitorando status do envio de configurações...")
                    
                    // Aguardar até que as configurações sejam enviadas
                    while (!bleManager.allConfigSent.value) {
                        android.util.Log.d("WebSocketViewModel", "Status de configuração: AGUARDANDO")
                        kotlinx.coroutines.delay(1000)
                    }
                    
                    android.util.Log.d("WebSocketViewModel", "Status de configuração: ENVIADO")
                    
                    // Configuração BLE enviada com sucesso, avançamos para 90%
                    _setupProgress.value = 0.9f
                    
                    android.util.Log.d("WebSocketViewModel", "✅ Configurações enviadas com sucesso para ESP32")
                    _wifiConfigStatus.value = WifiConfigStatus.Configured
                    
                    // Aguarda pela primeira imagem, com timeout de 10 segundos
                    var imageReceived = false
                    var connectionTimeoutJob: Job? = null
                    
                    // Configurar o timeout de 10 segundos
                    connectionTimeoutJob = viewModelScope.launch {
                        android.util.Log.d("WebSocketViewModel", "⏱️ Iniciando timeout de 10 segundos para receber imagem da câmera")
                        delay(10000) // 10 segundos
                        if (!imageReceived && _imageReceived.value == false) {
                            android.util.Log.d("WebSocketViewModel", "⚠️ TIMEOUT: Não foi possível receber imagem da câmera em 10 segundos")
                            _connectionError.value = "Timeout: A câmera não se conectou em 10 segundos. Verifique as configurações e tente novamente."
                            _wifiConfigStatus.value = WifiConfigStatus.Failed("Timeout na conexão")
                            _setupProgress.value = 0f
                            prepareRetry()
                            bleManager.disconnect() // Desconecta o BLE para permitir nova tentativa
                        }
                    }
                    
                    // Observar o recebimento da primeira imagem
                    val imageMonitorJob = viewModelScope.launch {
                        // Espera pela primeira imagem
                        latestCameraImage.collect { (bitmap, _) ->
                            if (bitmap != null && _setupProgress.value < 1f) {
                                // Imagem recebida, progresso completo
                                imageReceived = true
                                _setupProgress.value = 1f
                                _imageReceived.value = true
                                connectionTimeoutJob?.cancel() // Cancela o job de timeout
                                android.util.Log.d("WebSocketViewModel", "✅ Primeira imagem recebida, configuração completa!")
                                return@collect
                            }
                        }
                    }
                    
                    // Monitorar a contagem de clientes também
                    viewModelScope.launch {
                        connectionStats.collect { stats ->
                            if (stats.clientsCount > 0 && !imageReceived) {
                                imageReceived = true
                                _setupProgress.value = 0.95f
                                connectionTimeoutJob?.cancel() // Cancela o job de timeout
                                android.util.Log.d("WebSocketViewModel", "✅ Cliente conectado ao WebSocket, aguardando imagem...")
                            }
                        }
                    }
                } else {
                    android.util.Log.d("WebSocketViewModel", "⚠️ Falha ao iniciar servidor: ${serverResult.second}")
                    _connectionError.value = "Falha ao iniciar servidor WebSocket. Verifique o hotspot do dispositivo."
                }
            } catch (e: Exception) {
                android.util.Log.e("WebSocketViewModel", "⚠️ ERRO ao iniciar servidor: ${e.message}", e)
                _connectionError.value = "Erro ao iniciar servidor WebSocket: ${e.message}"
            }
            
            // Cancelamos o job de monitoramento do BleManager
            bleProgressJob.cancel()
            
        } catch (e: Exception) {
            android.util.Log.e("WebSocketViewModel", "⚠️ ERRO GERAL: ${e.message}", e)
            _wifiConfigStatus.value = WifiConfigStatus.Failed("Erro: ${e.message}")
            _connectionError.value = "Erro na configuração: ${e.message}"
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
     * Obtém o BleManager principal usado para a câmera térmica
     * Este método é necessário para que a UI possa acessar o estado interno do BleManager
     * durante reconexões automáticas
     */
    fun getBleManager(): BleManager {
        return bleManager
    }
}
