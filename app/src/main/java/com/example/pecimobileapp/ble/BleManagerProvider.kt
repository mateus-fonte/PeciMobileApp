package com.example.pecimobileapp.ble

import android.content.Context
import android.util.Log

/**
 * Provedor singleton para gerenciar uma única instância do BleManager
 * Isso garante que todos os componentes do app usem a mesma instância
 * para gerenciar conexões BLE
 */
object BleManagerProvider {
    private const val TAG = "BleManagerProvider"
    private var instance: BleManager? = null
    
    @Synchronized
    fun initialize(context: Context) {
        if (instance == null) {
            Log.d(TAG, "Inicializando nova instância do BleManager")
            instance = BleManager(context.applicationContext)
        }
    }
    
    fun getInstance(): BleManager {
        return instance ?: throw IllegalStateException(
            "BleManager não foi inicializado. Chame BleManagerProvider.initialize(context) primeiro."
        )
    }

    fun isInitialized(): Boolean {
        return instance != null
    }

    /**
     * Usado principalmente para testes ou quando precisar reiniciar o BleManager
     */
    internal fun reset() {
        Log.d(TAG, "Resetando instância do BleManager")
        instance?.disconnect()
        instance = null
    }
}
