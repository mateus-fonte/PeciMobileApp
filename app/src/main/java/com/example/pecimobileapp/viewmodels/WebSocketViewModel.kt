package com.example.pecimobileapp.viewmodels

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    // Estados possíveis da configuração WiFi para ESP32
    sealed class WifiConfigStatus {
        object NotConfigured : WifiConfigStatus()
        object Configuring : WifiConfigStatus()
        object Configured : WifiConfigStatus()
        data class Failed(val reason: String) : WifiConfigStatus()
    }

    // Serviço WebSocket
    private val webSocketServer = WebSocketServerService(application.applicationContext)

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

    // Observáveis para a UI
    val isServerRunning: StateFlow<Boolean> = webSocketServer.isRunning
    val latestCameraImage: StateFlow<Pair<Bitmap?, String>> = webSocketServer.latestCameraImage
    val latestThermalData: StateFlow<Pair<FloatArray?, String>> = webSocketServer.latestThermalData
    val connectionStats: StateFlow<WebSocketServerService.ConnectionStats> = webSocketServer.connectionStats
    
    // Imagem processada com OpenCV (com detecção facial e sobreposição térmica)
    val processedImage: StateFlow<Pair<Bitmap?, List<OpenCVUtils.FaceData>>> = webSocketServer.processedImage
    
    /**
     * Inicia o servidor WebSocket
     */
    fun startServer(port: Int): Pair<Boolean, WebSocketServerService.ServerState> {
        val result = webSocketServer.startServer(port)
        if (result) {
            _serverState.value = WebSocketServerService.ServerState.Running
        } else {
            // Assume hotspot não ativo como causa padrão de falha
            _serverState.value = WebSocketServerService.ServerState.HotspotNotActive
        }
        return Pair(result, _serverState.value)
    }

    /**
     * Para o servidor WebSocket
     */
    fun stopServer() = viewModelScope.launch {
        webSocketServer.stopServer()
        _serverState.value = WebSocketServerService.ServerState.Stopped
    }

    /**
     * Retorna o valor mínimo do array térmico
     */
    fun getThermalMinValue(): Float {
        val thermalData = latestThermalData.value.first ?: return 0f
        return thermalData.minOrNull() ?: 0f
    }

    /**
     * Retorna o valor máximo do array térmico
     */
    fun getThermalMaxValue(): Float {
        val thermalData = latestThermalData.value.first ?: return 0f
        return thermalData.maxOrNull() ?: 0f
    }

    /**
     * Retorna o valor médio do array térmico
     */
    fun getThermalAvgValue(): Float {
        val thermalData = latestThermalData.value.first ?: return 0f
        return thermalData.average().toFloat()
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
        if (!checkAccessPointStatus()) {
            _serverState.value = WebSocketServerService.ServerState.HotspotNotActive
            return Pair(false, _serverState.value)
        }
        
        // Se o AP estiver ativo, tenta iniciar o servidor
        return startServer(port)
    }
    
    /**
     * Aguarda até que o AP esteja ativo e então inicia o servidor WebSocket
     * @param timeoutMs tempo máximo de espera em milissegundos
     * @param checkIntervalMs intervalo entre verificações em milissegundos
     */
    fun waitForApAndStartServer(port: Int = 8080, timeoutMs: Long = 30000, checkIntervalMs: Long = 1000) = viewModelScope.launch {
        var elapsed = 0L
        var serverStarted = false
        
        while (elapsed < timeoutMs && !serverStarted) {
            if (checkAccessPointStatus()) {
                val result = startServer(port)
                serverStarted = result.first
                if (serverStarted) {
                    break
                }
            }
            
            kotlinx.coroutines.delay(checkIntervalMs)
            elapsed += checkIntervalMs
        }
        
        if (!serverStarted) {
            _serverState.value = WebSocketServerService.ServerState.HotspotNotActive
        }
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
                return@launch
            }
            
            // Obter o IP da interface ap0
            val ip = getAccessPointIp()
            if (ip == null) {
                _wifiConfigStatus.value = WifiConfigStatus.Failed("Não foi possível obter o IP do Access Point")
                return@launch
            }
            
            // Log para debug
            android.util.Log.d("WebSocketViewModel", "Preparando configuração WiFi: SSID=$ssid, IP=$ip")
            
            // Aqui você usaria o bleManager para enviar os dados via BLE
            // Por exemplo: bleManager.sendWifiConfig(ssid, password, ip)
            // Como este código é intermediário, vamos apenas simular o sucesso
            
            _wifiConfigStatus.value = WifiConfigStatus.Configured
            
            // Iniciar o servidor WebSocket automaticamente após configurar o WiFi
            startServerWithApCheck()
            
        } catch (e: Exception) {
            android.util.Log.e("WebSocketViewModel", "Erro ao configurar WiFi", e)
            _wifiConfigStatus.value = WifiConfigStatus.Failed("Erro: ${e.message}")
        }
    }
    
    /**
     * Configura o ESP32 para se conectar ao Access Point e iniciar o servidor WebSocket
     * 
     * Fluxo:
     * 1. Verifica se o Access Point está ativo
     * 2. Obtém o IP da interface ap0
     * 3. Envia as configurações WiFi para o ESP32 via BLE
     * 4. Aguarda até que o AP esteja ativo e inicia o servidor WebSocket
     * 
     * @param bleManager gerenciador BLE já conectado ao ESP32
     * @param ssid SSID do Access Point
     * @param password senha do Access Point
     */
    fun configureEsp32AndStartServer(bleManager: BleManager, ssid: String, password: String) = viewModelScope.launch {
        try {
            android.util.Log.d("WebSocketViewModel", "====== INICIANDO CONFIGURAÇÃO ESP32 ======")
            android.util.Log.d("WebSocketViewModel", "SSID: $ssid, Senha: ${password.take(2)}***")
            
            // Reseta o progresso da configuração
            _setupProgress.value = 0f
            _wifiConfigStatus.value = WifiConfigStatus.Configuring
            
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
                bleProgressJob.cancel()
                return@launch
            }
            
            // 3. Envia as configurações WiFi para o ESP32 via BLE
            android.util.Log.d("WebSocketViewModel", "Enviando configurações WiFi para ESP32: SSID=$ssid, IP=$apIp")
            try {
                // Tentar usar o método sendAllConfigs do BleManager
                bleManager.sendAllConfigs(ssid, password, apIp)
                android.util.Log.d("WebSocketViewModel", "Comando de envio de configurações executado")
            } catch (e: Exception) {
                android.util.Log.e("WebSocketViewModel", "⚠️ ERRO ao enviar configurações: ${e.message}", e)
                _wifiConfigStatus.value = WifiConfigStatus.Failed("Erro ao enviar configurações: ${e.message}")
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
            
            // Configuração BLE enviada com sucesso, avançamos para 60%
            _setupProgress.value = 0.6f
            
            android.util.Log.d("WebSocketViewModel", "✅ Configurações enviadas com sucesso para ESP32")
            _wifiConfigStatus.value = WifiConfigStatus.Configured
            
            // 4. Aguarda até que o ESP32 se conecte e inicia o servidor WebSocket
            android.util.Log.d("WebSocketViewModel", "Iniciando servidor WebSocket...")
            
            try {
                // Iniciar o servidor WebSocket
                val serverResult = startServerWithApCheck()
                android.util.Log.d("WebSocketViewModel", "Resultado do servidor: $serverResult")
                
                if (serverResult.first) {
                    android.util.Log.d("WebSocketViewModel", "✅ Servidor WebSocket iniciado com sucesso!")
                    // Servidor iniciado, avançamos para 80%
                    _setupProgress.value = 0.8f
                    
                    // Observar o recebimento da primeira imagem
                    viewModelScope.launch {
                        // Espera pela primeira imagem
                        latestCameraImage.collect { (bitmap, _) ->
                            if (bitmap != null && _setupProgress.value < 1f) {
                                // Imagem recebida, progresso completo
                                _setupProgress.value = 1f
                                android.util.Log.d("WebSocketViewModel", "✅ Primeira imagem recebida, configuração completa!")
                                return@collect
                            }
                        }
                    }
                } else {
                    android.util.Log.d("WebSocketViewModel", "⚠️ Falha ao iniciar servidor: ${serverResult.second}")
                }
            } catch (e: Exception) {
                android.util.Log.e("WebSocketViewModel", "⚠️ ERRO ao iniciar servidor: ${e.message}", e)
            }
            
            // Cancelamos o job de monitoramento do BleManager
            bleProgressJob.cancel()
            
        } catch (e: Exception) {
            android.util.Log.e("WebSocketViewModel", "⚠️ ERRO GERAL: ${e.message}", e)
            _wifiConfigStatus.value = WifiConfigStatus.Failed("Erro: ${e.message}")
        }
    }
    
    /**
     * Prepara o ViewModel para uma nova tentativa de configuração após uma falha
     * Reseta o estado de configuração WiFi para NotConfigured
     */
    fun prepareRetry() {
        _wifiConfigStatus.value = WifiConfigStatus.NotConfigured
        _setupProgress.value = 0f
    }
}
