package com.example.pecimobileapp.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
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
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.util.*
import kotlin.concurrent.thread

/**
 * Serviço que implementa um servidor WebSocket para receber dados do ESP32-CAM
 */
class WebSocketServerService(private val context: Context) {
    
    // Possíveis estados do servidor
    sealed class ServerState {
        object Running : ServerState()
        object Stopped : ServerState()
        object HotspotNotActive : ServerState()
    }
    
    // Porta padrão para o servidor WebSocket
    private val DEFAULT_PORT = 8080
    private var port = DEFAULT_PORT
    private var server: ESPWebSocketServer? = null
    
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
     * Inicia o servidor WebSocket
     */
    fun startServer(port: Int = DEFAULT_PORT): Boolean {
        if (_isRunning.value) {
            Log.d(TAG, "O servidor já está em execução na porta $port")
            return true
        }
        
        this.port = port
        
        try {
            server = ESPWebSocketServer(InetSocketAddress(port))
            
            // Iniciar o servidor em uma thread separada
            thread(start = true) {
                server?.start()
            }
            
            val serverIP = getIpAddress()
            Log.d(TAG, "Servidor iniciado em $serverIP:$port")
            
            _isRunning.value = true
            _connectionStats.value = _connectionStats.value.copy(
                serverAddress = "$serverIP:$port"
            )
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar o servidor WebSocket", e)
            return false
        }
    }
    
    /**
     * Para o servidor WebSocket
     */
    fun stopServer() {
        if (!_isRunning.value) {
            Log.d(TAG, "O servidor já está parado")
            return
        }
        
        try {
            server?.stop()
            server = null
            _isRunning.value = false
            Log.d(TAG, "Servidor WebSocket parado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar o servidor WebSocket", e)
        }
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
                if (networkInterface.isUp && networkInterface.name == "ap0") {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is InetAddress && 
                                address.hostAddress.indexOf(':') < 0) { // Filtra IPv6
                            Log.d(TAG, "Access Point ap0 está ativo com IP: ${address.hostAddress}")
                            return true
                        }
                    }
                }
            }
            Log.d(TAG, "Access Point ap0 não está ativo")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar status do Access Point", e)
        }
        return false
    }
    
    /**
     * Obtém o IP específico da interface ap0 (Access Point)
     * @return IP da interface ap0 ou null se não estiver disponível
     */
    fun getAp0IpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isUp && networkInterface.name == "ap0") {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is InetAddress && 
                                address.hostAddress.indexOf(':') < 0) { // Filtra IPv6
                            Log.d(TAG, "IP da interface ap0: ${address.hostAddress}")
                            return address.hostAddress
                        }
                    }
                }
            }
            Log.d(TAG, "Interface ap0 não encontrada ou sem endereço IP válido")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter endereço IP da interface ap0", e)
        }
        return null
    }
    
    /**
     * Obtém o endereço IP do dispositivo
     */
    private fun getIpAddress(): String {
        // Tentar primeiro obter o IP da interface ap0 (prioridade para o IP do hotspot)
        getAp0IpAddress()?.let { return it }
        
        // Fallback: WifiManager para outros casos
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipAddress = wifiManager.connectionInfo.ipAddress
            
            if (ipAddress != 0) {
                return String.format(
                    Locale.getDefault(),
                    "%d.%d.%d.%d",
                    ipAddress and 0xff,
                    ipAddress shr 8 and 0xff,
                    ipAddress shr 16 and 0xff,
                    ipAddress shr 24 and 0xff
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter endereço IP", e)
        }
        
        return "127.0.0.1" // IP localhost como último recurso
    }
    
    /**
     * Implementação do servidor WebSocket
     */
    inner class ESPWebSocketServer(address: InetSocketAddress) : WebSocketServer(address) {
        
        init {
            // Configurações para tornar o servidor mais estável
            connectionLostTimeout = 60  // Aumenta o timeout para 60 segundos
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
                
                // Processar os dados binários recebidos
                processReceivedData(byteArray)
                
                // Confirmação para mensagens binárias
                conn.send("RECEIVED_BINARY")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao processar mensagem binária", e)
            }
        }
        
        override fun onError(conn: WebSocket?, ex: Exception) {
            Log.e(TAG, "Erro na conexão WebSocket ${conn?.remoteSocketAddress}", ex)
        }
        
        override fun onStart() {
            Log.d(TAG, "Servidor WebSocket iniciado na porta $port")
            setConnectionLostTimeout(60);
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