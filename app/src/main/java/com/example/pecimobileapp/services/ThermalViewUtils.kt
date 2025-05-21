package com.example.pecimobileapp.services

import android.util.Log
import com.example.pecimobileapp.viewmodels.WebSocketViewModel

/**
 * Classe utilitária para lidar com a configuração da visualização da câmera térmica
 */
object ThermalViewUtils {
    private const val TAG = "ThermalViewUtils"
    
    /**
     * Configura o modo de visualização térmica em todos os lugares necessários
     */
    fun setupThermalOnlyMode(wsViewModel: WebSocketViewModel) {
        // Define que deve mostrar apenas imagem térmica com 100% de opacidade
        wsViewModel.setShowOnlyThermal(true)
        
        // Registra que está usando o modo apenas térmico
        Log.d(TAG, "Modo de visualização térmica exclusiva ativado")
    }
}
