package com.example.pecimobileapp.services

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.pecimobileapp.models.WebSocketData
import com.example.pecimobileapp.utils.OpenCVUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.opencv.*
import org.opencv.android.OpenCVLoader
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.ConnectException
import java.nio.ByteBuffer
import java.util.*
import kotlin.concurrent.thread
import java.net.Inet4Address

/**
 * Serviço que implementa um servidor WebSocket para receber dados do ESP32-CAM
 */
class WebSocketServerService(private val context: Context) {
    
    // Possíveis estados do servidor
    sealed class ServerState {
        object Running : ServerState()
        object Stopped : ServerState()
        object HotspotNotActive : ServerState()
        data class Error(val message: String) : ServerState()
    }
    
    // Porta padrão para o servidor WebSocket
    private val DEFAULT_PORT = 8080
    private var port = DEFAULT_PORT
    private var server: ESPWebSocketServer? = null
    
    // Servidor de teste para simular porta ocupada
    private var testServer: ServerSocket? = null
    
    // Controle de reconexão automática
    private var autoReconnect = true
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private var reconnectHandler: Handler? = null
    private val reconnectIntervalMs = 5000L // 5 segundos entre tentativas
    
    // Para observar o estado do servidor
    private val _serverState = MutableStateFlow<ServerState>(ServerState.Stopped)
    val serverState: StateFlow<ServerState> = _serverState
    
    // OpenCV
    private val openCVUtils: OpenCVUtils by lazy {
            OpenCVUtils(context)
    }
    
    // Para observar o estado da conexão
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    
    // Para armazenar os dados mais recentes
    private val _latestCameraImage = MutableStateFlow<Pair<Bitmap?, String>>(Pair(null, ""))
    val latestCameraImage: StateFlow<Pair<Bitmap?, String>> = _latestCameraImage
    
    private val _latestThermalData = MutableStateFlow<Pair<FloatArray?, String>>(Pair(null, ""))
    val latestThermalData: StateFlow<Pair<FloatArray?, String>> = _latestThermalData
    
    // Para armazenar a imagem processada com OpenCV
    private val _processedImage = MutableStateFlow<Pair<Bitmap?, List<OpenCVUtils.FaceData>>>(Pair(null, emptyList()))
    val processedImage: StateFlow<Pair<Bitmap?, List<OpenCVUtils.FaceData>>> = _processedImage
    
    // Métricas de conexão
    private val _connectionStats = MutableStateFlow(ConnectionStats())
    val connectionStats: StateFlow<ConnectionStats> = _connectionStats
    
    init {
        // Inicializar o OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "Falha ao inicializar o OpenCV")
        } else {
            Log.d(TAG, "OpenCV inicializado com sucesso")
        }
    }
    
    // Classe para manter estatísticas de conexão
    data class ConnectionStats(
        val clientsCount: Int = 0,
        val receivedMessages: Int = 0,
        val serverAddress: String = "",
        val detectedFaces: Int = 0
    )
    
    /**
     * Define se a reconexão automática deve ser habilitada ou não
     * @param enabled true para habilitar, false para desabilitar
     */
    fun setAutoReconnect(enabled: Boolean) {
        autoReconnect = enabled
        Log.d(TAG, "Reconexão automática ${if (enabled) "habilitada" else "desabilitada"}")
    }
    
    /**
     * Inicia o servidor WebSocket
     * @param port Porta a ser utilizada pelo servidor (padrão: 8080)
     * @param notifyPort Uma função que recebe a porta selecionada (útil para enviar ao ESP32)
     * @return true se o servidor foi iniciado com sucesso, false caso contrário
     */
    fun startServer(port: Int = DEFAULT_PORT, notifyPort: ((Int) -> Unit)? = null): Boolean {
        Log.d(TAG, "Solicitação para iniciar servidor WebSocket na porta $port")
        
        // Resetar tentativas de reconexão ao iniciar manualmente
        reconnectAttempts = 0
        
        // Verificar e encerrar todas as instâncias anteriores antes de continuar
        if (!checkAndTerminatePriorInstances(port)) {
            Log.w(TAG, "Não foi possível encerrar todas as instâncias anteriores, continuando mesmo assim...")
            // Continuamos mesmo assim, mas registramos o aviso
        }
        
        if (_isRunning.value && server != null) {
            Log.d(TAG, "O servidor já está em execução na porta $this.port")
            // Notifica a porta atual
            notifyPort?.invoke(this.port)
            return true
        }

        // Começamos assumindo que usaremos a porta solicitada
        this.port = port
        
        // Verificar se a porta está disponível antes de iniciar o servidor
        var portAvailable = isPortAvailable(port)
        
        // Se a porta principal não estiver disponível, tenta liberá-la
        if (!portAvailable) {
            Log.w(TAG, "Porta $port já está em uso. Tentando liberar...")
            
            // Tentar liberar a porta
            if (releasePort(port)) {
                portAvailable = true
                Log.d(TAG, "Porta $port liberada com sucesso")
            } else {
                Log.e(TAG, "Não foi possível liberar a porta $port. Tentando portas alternativas...")
                
                // Tentar portas alternativas
                val alternativePorts = listOf(8081, 8082, 8083, 8090, 9000)
                for (alternativePort in alternativePorts) {
                    if (isPortAvailable(alternativePort)) {
                        Log.d(TAG, "Usando porta alternativa $alternativePort")
                        this.port = alternativePort
                        portAvailable = true
                        break
                    }
                }
            }
            
            // Se não conseguimos encontrar nenhuma porta disponível, reportar falha
            if (!portAvailable) {
                Log.e(TAG, "Não foi possível encontrar uma porta disponível")
                _serverState.value = ServerState.Error("Todas as portas estão ocupadas")
                return false
            }
        }
        
        try {
            Log.d(TAG, "Iniciando servidor WebSocket na porta ${this.port}")
            
            // Configurações adicionais para tentar evitar o problema de porta em uso
            System.setProperty("sun.net.maxDatagramSockets", "1024")
            
            // Criar o servidor com configurações otimizadas
            server = ESPWebSocketServer(InetSocketAddress(this.port)).apply {
                isTcpNoDelay = true // Desabilita o algoritmo de Nagle para melhor desempenho
                isReuseAddr = true  // Permite reutilizar endereços que estão em TIME_WAIT
            }
            
            // Iniciar o servidor em uma thread separada com tratamento de erro
            val serverThread = thread(start = true) {
                try {
                    server?.start()
                } catch (e: Exception) {
                    Log.e(TAG, "Erro durante a execução do servidor WebSocket", e)
                    _isRunning.value = false
                    server = null
                }
            }
            
            // Aguardar inicialização do servidor com tentativas repetidas
            var serverStarted = false
            var attempts = 0
            val maxAttempts = 10
            
            while (!serverStarted && attempts < maxAttempts) {
                attempts++
                Thread.sleep(500)  // Pausa menor entre verificações
                
                if (server != null && server?.isRunning == true) {
                    serverStarted = true
                    Log.d(TAG, "Servidor iniciado com sucesso após $attempts tentativas")
                    break
                }
                
                Log.d(TAG, "Aguardando inicialização do servidor (tentativa $attempts de $maxAttempts)...")
            }
            
            // Verificar se o servidor iniciou corretamente
            if (!serverStarted) {
                Log.e(TAG, "Falha ao iniciar o servidor WebSocket após $maxAttempts tentativas")
                stopServer() // Garantir limpeza de recursos
                return false
            }
            
            val serverIP = getIpAddress()
            Log.d(TAG, "Servidor iniciado com sucesso em $serverIP:${this.port}")
            
            // Notifica a porta selecionada
            notifyPort?.invoke(this.port)
            
            // Adicionar um log confirmando que estamos prontos para processar conexões
            Log.d(TAG, "Servidor WebSocket pronto para aceitar conexões")
            
            _isRunning.value = true
            _connectionStats.value = _connectionStats.value.copy(
                serverAddress = "$serverIP:${this.port}"
            )
            _serverState.value = ServerState.Running
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar o servidor WebSocket", e)
            _isRunning.value = false
            server = null
            _serverState.value = ServerState.Error("Falha ao iniciar o servidor: ${e.message}")
            scheduleReconnect()
            return false
        }
    }
    
    /**
     * Para o servidor WebSocket e libera todos os recursos associados
     * @param disableReconnect Se true, desabilita a reconexão automática
     */
    fun stopServer(disableReconnect: Boolean = false) {
        try {
            Log.d(TAG, "Parando servidor WebSocket na porta $port")
            
            // Cancelar qualquer tentativa de reconexão pendente
            reconnectHandler?.removeCallbacksAndMessages(null)
            
            if (disableReconnect) {
                autoReconnect = false
            }
            
            // Salva uma referência local ao servidor atual
            val currentServer = server
            
            // Limpa a referência global imediatamente para evitar acesso concorrente
            server = null
            _isRunning.value = false
            
            // Encerra o servidor se ele existir
            if (currentServer != null) {
                try {
                    // Tenta um encerramento elegante com timeout maior
                    currentServer.stop(2000) // Aumentando timeout para 2 segundos
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao parar o servidor WebSocket normalmente, forçando parada", e)
                    try {
                        // Se falhar, força o encerramento imediato
                        currentServer.stop(0) 
                    } catch (e2: Exception) {
                        Log.e(TAG, "Erro ao forçar a parada do servidor", e2)
                    }
                }
            }
            
            // Aguardar um período maior para garantir que o SO libere totalmente a porta
            Thread.sleep(1500)
            
            Log.d(TAG, "Servidor WebSocket encerrado com sucesso")
            _serverState.value = ServerState.Stopped
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar o servidor WebSocket", e)
            _serverState.value = ServerState.Error("Falha ao parar o servidor: ${e.message}")
        }
    }
    
    /**
     * Tenta reconectar o servidor após uma falha
     */
    private fun scheduleReconnect() {
        if (!autoReconnect || reconnectAttempts >= maxReconnectAttempts) {
            if (reconnectAttempts >= maxReconnectAttempts) {
                Log.w(TAG, "Número máximo de tentativas de reconexão atingido ($maxReconnectAttempts)")
                _serverState.value = ServerState.Error("Falha após $maxReconnectAttempts tentativas de reconexão")
            }
            return
        }
        
        reconnectAttempts++
        Log.d(TAG, "Agendando tentativa de reconexão $reconnectAttempts de $maxReconnectAttempts em ${reconnectIntervalMs}ms")
        
        if (reconnectHandler == null) {
            reconnectHandler = Handler(Looper.getMainLooper())
        }
        
        reconnectHandler?.postDelayed({
            Log.d(TAG, "Tentando reconectar servidor na porta $port (tentativa $reconnectAttempts)")
            if (startServer(port)) {
                Log.d(TAG, "Reconexão bem-sucedida na tentativa $reconnectAttempts")
                reconnectAttempts = 0
                _serverState.value = ServerState.Running
            } else {
                Log.w(TAG, "Falha na tentativa de reconexão $reconnectAttempts")
                _serverState.value = ServerState.Error("Falha na tentativa de reconexão $reconnectAttempts")
                scheduleReconnect()
            }
        }, reconnectIntervalMs)
    }

    @SuppressLint("DefaultLocale")
    fun getBestIpAddress(context: Context): String {
        val TAG = "IpUtils"

        // Tentar via interfaces de rede (melhor abordagem)
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (!ip.startsWith("127") && !ip.startsWith("169")) {
                            Log.d(TAG, "IP via NetworkInterface: $ip")
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter IP via NetworkInterface", e)
        }

        // Fallback via WifiManager
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ip = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) {
                val ipStr = String.format(
                    "%d.%d.%d.%d",
                    ip and 0xff,
                    ip shr 8 and 0xff,
                    ip shr 16 and 0xff,
                    ip shr 24 and 0xff
                )
                Log.d(TAG, "IP via WifiManager: $ipStr")
                return ipStr
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter IP via WifiManager", e)
        }

        // Último recurso
        Log.w(TAG, "Nenhum IP válido encontrado. Retornando localhost.")
        return "127.0.0.1"
    }
    
    /**
     * Obtém o endereço IP do dispositivo sem iniciar o servidor
     * Este método pode ser usado por outros componentes que precisam apenas do IP
     */
    fun getDeviceIpAddress(): String {
        return getIpAddress()
    }
    
    /**
     * Verifica se o Access Point (ap0) está ativo
     * @return true se o AP estiver ativo, false caso contrário
     */
    fun isAccessPointActive(): Boolean {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress &&
                        address is Inet4Address &&
                        address.hostAddress != "127.0.0.1" &&
                        !address.hostAddress.startsWith("169.")) { // ignora IPs inválidos

                        Log.d(TAG, "Interface ${networkInterface.name} está ativa com IP: ${address.hostAddress}")
                        return true
                    }
                }
            }
            Log.d(TAG, "Nenhuma interface com IP válido foi encontrada")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar status do Access Point", e)
        }
        return false
    }
    
    /**
     * Obtém o IP específico da interface ap0 (Access Point)
     * @return IP da interface ap0 ou null se não estiver disponível
     */

    
    /**
     * Obtém o endereço IP do dispositivo
     */
    private fun getIpAddress(): String {
        return getBestIpAddress(context)
    }
    
    /**
     * Verifica se uma porta está disponível para uso
     * @param port Porta a ser verificada
     * @return true se disponível, false caso contrário
     */
    private fun isPortAvailable(port: Int): Boolean {
        var socket: ServerSocket? = null
        var result = false
        
        try {
            // Tenta criar um socket de servidor na porta específica
            // Se for bem-sucedido, a porta está disponível
            socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("0.0.0.0", port), 1)
            result = true
            Log.d(TAG, "Porta $port está disponível")
            // Consideramos a porta disponível se conseguirmos abrir um ServerSocket
            return true
        } catch (e: Exception) {
            Log.d(TAG, "Porta $port está ocupada: ${e.message}")
            result = false
        } finally {
            // Fechar o socket de teste quando terminar
            try {
                socket?.close()
                // Adicionar um pequeno delay após fechar o socket
                Thread.sleep(200)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao fechar socket de teste", e)
            }
        }
        
        // Se chegarmos aqui, a porta não está disponível na verificação primária
        return false
    }
    
    /**
     * Verifica e libera a porta principal, sem verificar todas as portas da lista
     * @param port A porta principal que desejamos usar
     * @return true se a porta principal está disponível, false caso contrário
     */
    private fun checkAndTerminatePriorInstances(port: Int): Boolean {
        Log.d(TAG, "Verificando disponibilidade da porta $port antes de iniciar servidor")
        
        // Primeira verificação - se a porta está disponível, retorna true imediatamente
        if (isPortAvailable(port)) {
            Log.d(TAG, "Porta $port está disponível")
            // Se a verificação primária diz que a porta está disponível, confiamos nela
            return true
        } else {
            // A verificação primária já indicou que a porta não está disponível
            Log.w(TAG, "Porta $port em uso, tentando liberar")
        }
        
        // Tenta liberar a porta
        val released = releasePort(port)
        
        if (released) {
            Log.d(TAG, "Porta $port liberada com sucesso")
            return true
        } else {
            Log.e(TAG, "Não foi possível liberar a porta $port")
            return false
        }
    }
    
    /**
     * Tenta liberar uma porta em uso, usando um método mais simples e robusto
     * que evita acessar campos internos da classe ServerSocket que causava NoSuchFieldException.
     * @param port A porta a ser liberada
     * @return true se a operação foi bem-sucedida, false caso contrário
     */
    private fun releasePort(port: Int): Boolean {
        Log.d(TAG, "Tentando liberar a porta $port")
        
        try {
            // Tenta uma abordagem simplificada que não depende de acessar o campo impl
            // Tenta abrir um socket na mesma porta com SO_REUSEADDR
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(port))
            socket.close()
            
            // Aguarda um momento para que o sistema operacional libere completamente a porta
            Thread.sleep(100)
            
            // Verifica novamente se a porta está disponível 
            if (isPortAvailable(port)) {
                Log.d(TAG, "Sucesso na liberação forçada da porta $port")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao tentar liberar a porta $port: ${e.message}")
        }
        
        return false
    }
    
    /**
     * Implementação do servidor WebSocket
     */
    inner class ESPWebSocketServer(address: InetSocketAddress) : WebSocketServer(address) {
        
        // Adicionar uma propriedade para verificar o estado de execução
        private var _isRunning = false
        val isRunning: Boolean
            get() = _isRunning
          init {
            // Configurações para tornar o servidor mais estável
            connectionLostTimeout = 30  // Aumentar para 30 segundos
            setConnectionLostTimeout(30)
            maxConnections = 5  // Limitar número de conexões simultâneas
        }
        
        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            Log.d(TAG, "Nova conexão: ${conn.remoteSocketAddress}")
            
            // Envia uma mensagem de confirmação para o cliente
            try {
                conn.send("CONNECTED")
                Log.d(TAG, "Enviado CONNECTED para ${conn.remoteSocketAddress}")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao enviar confirmação", e)
            }
            
            _connectionStats.value = _connectionStats.value.copy(
                clientsCount = connections.size
            )
        }
        
        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            Log.d(TAG, "Conexão fechada: ${conn.remoteSocketAddress}, código: $code, razão: $reason, remoto: $remote")
            _connectionStats.value = _connectionStats.value.copy(
                clientsCount = connections.size
            )
        }
        
        override fun onMessage(conn: WebSocket, message: String) {
            Log.d(TAG, "Mensagem de texto recebida de ${conn.remoteSocketAddress}: $message")
            _connectionStats.value = _connectionStats.value.copy(
                receivedMessages = _connectionStats.value.receivedMessages + 1
            )
            
            // Confirmação para mensagens de texto
            try {
                conn.send("RECEIVED_TEXT")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao enviar confirmação de texto", e)
            }
        }
          override fun onMessage(conn: WebSocket, message: ByteBuffer) {
            Log.d(TAG, "Dados binários recebidos de ${conn.remoteSocketAddress}, tamanho: ${message.remaining()} bytes")
            
            _connectionStats.value = _connectionStats.value.copy(
                receivedMessages = _connectionStats.value.receivedMessages + 1
            )
            
            try {
                val byteArray = ByteArray(message.remaining())
                message.get(byteArray)
                
                // Enviar confirmação antes de processar para evitar timeout
                try {
                    if (conn.isOpen) {
                        conn.send("RECEIVED_BINARY")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao enviar confirmação binária", e)
                }
                
                // Processar os dados binários recebidos
                processReceivedData(byteArray)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao processar mensagem binária", e)
            }
        }
        
        override fun onError(conn: WebSocket?, ex: Exception) {
            Log.e(TAG, "Erro na conexão WebSocket ${conn?.remoteSocketAddress}", ex)
            
            // Se o erro for grave, pode indicar que o servidor parou
            if (conn == null && !_isRunning) {
                _serverState.value = ServerState.Error("Erro crítico no servidor: ${ex.message}")
                // Tentar reconectar automaticamente
                scheduleReconnect()
            }
        }
        
        override fun onStart() {
            Log.d(TAG, "Servidor WebSocket iniciado na porta $port")
            setConnectionLostTimeout(20); // Redtimeout de 20 segundos
            _isRunning = true
            _serverState.value = ServerState.Running
            // Resetar tentativas de reconexão quando o servidor inicia com sucesso
            reconnectAttempts = 0
        }
        
        // Sobrescrever o método de parada para atualizar o estado
        override fun stop(timeout: Int) {
            super.stop(timeout)
            _isRunning = false
        }
    }
    
    /**
     * Processa os dados binários recebidos via WebSocket
     */
    private fun processReceivedData(data: ByteArray) {
        try {
            val wsData = WebSocketData.parseFromBinary(data) ?: return
            
            Log.d(TAG, "Dados recebidos: tipo=${wsData.type}, timestamp=${wsData.getFormattedTimestamp()}")
            
            when (wsData.type) {
                WebSocketData.DataType.CAMERA_IMAGE -> {
                    val imageBytes = wsData.data as ByteArray
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    _latestCameraImage.value = Pair(bitmap, wsData.getFormattedTimestamp())
                    
                    // Tentar processar a imagem com OpenCV se tivermos tanto a imagem quanto os dados térmicos
                    processImageWithOpenCV()
                }
                
                WebSocketData.DataType.THERMAL_DATA -> {
                    val thermalData = wsData.data as FloatArray
                    _latestThermalData.value = Pair(thermalData, wsData.getFormattedTimestamp())
                    
                    // Tentar processar a imagem com OpenCV se tivermos tanto a imagem quanto os dados térmicos
                    processImageWithOpenCV()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar dados binários", e)
        }
    }
    
    /**
     * Processa a imagem da câmera com OpenCV e sobrepõe dados térmicos
     */
    private fun processImageWithOpenCV() {
        val cameraImage = _latestCameraImage.value.first
        val thermalData = _latestThermalData.value.first
        
        if (cameraImage != null) {
            try {
                // Processar a imagem com detecção facial e dados térmicos
                Log.d(TAG, "Iniciando processamento de imagem com OpenCV")
                val result = openCVUtils.processImagesWithFaceDetection(cameraImage, thermalData)
                _processedImage.value = result
                
                // Atualiza as estatísticas de conexão
                _connectionStats.value = _connectionStats.value.copy(
                    detectedFaces = result.second.size
                )
                
                Log.d(TAG, "Processamento de imagem concluído. Faces detectadas: ${result.second.size}")
                
                // Registrar temperaturas faciais
                result.second.forEach { faceData ->
                    if (!faceData.temperature.isNaN()) {
                        Log.d(TAG, "Temperatura facial: ${String.format("%.1f°C", faceData.temperature)}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao processar imagem com OpenCV", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "WebSocketServerService"
    }
}