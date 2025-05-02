package com.example.pecimobileapp.viewmodels

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pecimobileapp.services.WebSocketServerService
import com.example.pecimobileapp.utils.OpenCVUtils
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para gerenciar o servidor WebSocket e os dados recebidos
 */
class WebSocketViewModel(application: Application) : AndroidViewModel(application) {
    
    // Serviço WebSocket
    private val webSocketServer = WebSocketServerService(application.applicationContext)
    
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
    fun startServer(port: Int) = viewModelScope.launch {
        webSocketServer.startServer(port)
    }
    
    /**
     * Para o servidor WebSocket
     */
    fun stopServer() = viewModelScope.launch {
        webSocketServer.stopServer()
    }
    
    /**
     * Limpa o ViewModel ao ser destruído
     */
    override fun onCleared() {
        super.onCleared()
        webSocketServer.stopServer()
    }
}