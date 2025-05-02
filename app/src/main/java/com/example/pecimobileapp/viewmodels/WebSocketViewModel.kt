package com.example.pecimobileapp.viewmodels

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pecimobileapp.services.WebSocketServerService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel para gerenciar o servidor WebSocket e os dados recebidos
 */
class WebSocketViewModel(application: Application) : AndroidViewModel(application) {

    // Serviço WebSocket
    private val webSocketServer = WebSocketServerService(application.applicationContext)

    // Observáveis para a UI
    val isServerRunning: StateFlow<Boolean> =
        webSocketServer.isRunning

    val latestCameraImage: StateFlow<Pair<Bitmap?, String>> =
        webSocketServer.latestCameraImage

    val latestThermalData: StateFlow<Pair<FloatArray?, String>> =
        webSocketServer.latestThermalData

    val connectionStats: StateFlow<WebSocketServerService.ConnectionStats> =
        webSocketServer.connectionStats

    /**
     * Inicia o servidor WebSocket
     */
    fun startServer(port: Int = 8080) {
        viewModelScope.launch {
            webSocketServer.startServer(port)
        }
    }

    /**
     * Para o servidor WebSocket
     */
    fun stopServer() {
        viewModelScope.launch {
            webSocketServer.stopServer()
        }
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
}
