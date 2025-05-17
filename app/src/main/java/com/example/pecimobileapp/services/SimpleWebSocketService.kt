package com.example.pecimobileapp.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer

class SimpleWebSocketService(private val context: Context) {
    
    sealed class ServerState {
        object Running : ServerState()
        object Stopped : ServerState()
        object Error : ServerState()
    }
    
    private var server: WSServer? = null
    private var currentPort = 8080
    
    private val _serverState = MutableStateFlow<ServerState>(ServerState.Stopped)
    val serverState: StateFlow<ServerState> = _serverState
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
      private val _latestImage = MutableStateFlow<Bitmap?>(null)
    val latestImage: StateFlow<Bitmap?> = _latestImage
    
    private val _latestThermalData = MutableStateFlow<FloatArray?>(null)
    val latestThermalData: StateFlow<FloatArray?> = _latestThermalData
    
    private var reconnectJob: kotlinx.coroutines.Job? = null
    
    inner class WSServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {
        override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
            _isConnected.value = true
        }
        
        override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
            _isConnected.value = connections.isNotEmpty()
        }
        
        override fun onMessage(conn: WebSocket?, message: String?) {
            // Não usado, mas precisa ser implementado
        }
        
        override fun onMessage(conn: WebSocket?, message: ByteBuffer?) {
            if (message == null) return
            when (message[0].toInt()) {
                1 -> { // Imagem
                    val imageBytes = ByteArray(message.remaining() - 1)
                    message.position(1)
                    message.get(imageBytes)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    _latestImage.value = bitmap
                }
                2 -> { // Dados Térmicos
                    message.position(1)
                    val thermalData = FloatArray((message.remaining()) / 4)
                    for (i in thermalData.indices) {
                        thermalData[i] = message.float
                    }
                    _latestThermalData.value = thermalData
                }            }
        }
        
        override fun onError(conn: WebSocket?, ex: Exception?) {
            Log.e("WSServer", "Erro: ${ex?.message}")
            _serverState.value = ServerState.Error
        }
        
        override fun onStart() {
            _serverState.value = ServerState.Running
        }
    }
    
    fun getDeviceIpAddress(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface.name == "ap0") {
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        }
        return null
    }
    
    fun isAccessPointActive(): Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return try {
            val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible = true
            method.invoke(wifiManager) as Boolean
        } catch (e: Exception) {
            false
        }
    }
    
    fun startServer(port: Int = 8080): Boolean {
        if (!isAccessPointActive()) return false
        
        stopServer()
        currentPort = port
        
        try {
            server = WSServer(port)
            server?.start()
            return true
        } catch (e: Exception) {
            Log.e("WSServer", "Erro ao iniciar servidor: ${e.message}")
            _serverState.value = ServerState.Error
            return false
        }
    }
    
    fun stopServer() {
        server?.stop()
        server = null
        _serverState.value = ServerState.Stopped
        _isConnected.value = false
    }
}
