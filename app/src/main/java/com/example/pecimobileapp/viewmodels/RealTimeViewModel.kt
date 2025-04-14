package com.example.pecimobileapp.viewmodels

import androidx.lifecycle.ViewModel
import com.example.pecimobileapp.models.RealTimeData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RealTimeViewModel : ViewModel() {
    // Armazena o último dado recebido (ou null se não houver dados)
    private val _realTimeData = MutableStateFlow<RealTimeData?>(null)
    val realTimeData: StateFlow<RealTimeData?> get() = _realTimeData

    // Indica se os sensores estão conectados
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> get() = _isConnected

    // Função para atualizar os dados e definir que os sensores estão conectados
    fun updateData(newData: RealTimeData) {
        _realTimeData.value = newData
        _isConnected.value = true
    }

    // Função para indicar que os sensores foram desconectados (ou ainda não configurados)
    fun disconnectSensors() {
        _isConnected.value = false
        _realTimeData.value = null
    }
}
