package com.example.pecimobileapp

import android.app.Application
import android.util.Log
import com.example.pecimobileapp.ble.BleManagerProvider

/**
 * Main application class
 * Esta classe é responsável pela inicialização de componentes globais do aplicativo
 */
class BluetoothApplication : Application() {

    companion object {
        private const val TAG = "BluetoothApplication"
    }

    override fun onCreate() {
        super.onCreate()
        
        // Inicializar o BleManagerProvider
        try {
            BleManagerProvider.getInstance().initialize(applicationContext)
            Log.d(TAG, "BleManagerProvider inicializado com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar BleManagerProvider: ${e.message}", e)
        }

        // Outros componentes podem ser inicializados aqui
    }
}