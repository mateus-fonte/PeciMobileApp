package com.example.pecimobileapp.ble

import android.bluetooth.BluetoothDevice
import com.example.pecimobileapp.ble.DeviceType
import android.content.Context
import java.lang.IllegalStateException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.util.Log

/**
 * Singleton responsável por gerenciar as instâncias de BleManager e manter o estado
 * de dispositivos conectados de forma consistente em toda a aplicação.
 */
class BleManagerProvider private constructor() {
    private val TAG = "BleManagerProvider"
    

    private var ppgManager: BleManager? = null
    private var camManager: BleManager? = null
    
    // Armazena os dispositivos conectados por tipo
    private val connectedDevices = mutableMapOf<DeviceType, BluetoothDevice>()
    
    // StateFlow observáveis para cada tipo de dispositivo
    private val _thermalCameraDevice = MutableStateFlow<BluetoothDevice?>(null)
    val thermalCameraDevice: StateFlow<BluetoothDevice?> = _thermalCameraDevice
    
    private val _ppgDevice = MutableStateFlow<BluetoothDevice?>(null)
    val ppgDevice: StateFlow<BluetoothDevice?> = _ppgDevice
    
    
    companion object {
        @Volatile
        private var instance: BleManagerProvider? = null
        
        fun getInstance(): BleManagerProvider {
            return instance ?: synchronized(this) {
                instance ?: BleManagerProvider().also { instance = it }
            }
        }
    }
    
    /**
     * Inicializa o BleManagerProvider com um contexto.
     * Deve ser chamado uma vez, geralmente na classe Application.
     */
    fun initialize(context: Context) {
        synchronized(this) {
            if (ppgManager == null) ppgManager = BleManager(context)
            if (camManager == null) camManager = BleManager(context)
        }
        }
    
    /**
     * Retorna a instância de BleManager existente, ou lança uma exceção se não inicializado.
     */
    fun getBleManager(type: DeviceType): BleManager {
        return when (type) {
            DeviceType.PPG -> ppgManager ?: throw IllegalStateException("PPG BleManager not initialized")
            DeviceType.THERMAL_CAMERA -> camManager ?: throw IllegalStateException("Cam BleManager not initialized")
        }
    }
    
    /**
     * Registra um dispositivo como conectado e atualiza os StateFlows correspondentes.
     * 
     * @param device O dispositivo Bluetooth a ser registrado
     * @param type O tipo do dispositivo (THERMAL_CAMERA ou PPG)
     */
    fun registerConnectedDevice(device: BluetoothDevice, type: DeviceType) {
    Log.d(TAG, "Registrando dispositivo ${device.address} como ${type.name}")

    // Só desconecta o PPG anterior se for do mesmo tipo
    if (type == DeviceType.PPG && _ppgDevice.value != null){
        Log.d(TAG, "Desconectando dispositivo PPG anterior para conectar ao novo")
        ppgManager?.disconnect()
    }
    if (type == DeviceType.THERMAL_CAMERA && _thermalCameraDevice.value != null) {
        Log.d(TAG, "Desconectando câmera térmica anterior para conectar ao novo")
        camManager?.disconnect()
    }
    connectedDevices[type] = device

    when (type) {
        DeviceType.THERMAL_CAMERA -> _thermalCameraDevice.value = device
        DeviceType.PPG -> _ppgDevice.value = device
    }
}
    
    /**
     * Remove o registro de um dispositivo conectado.
     * 
     * @param type O tipo do dispositivo a ser removido
     */
    fun unregisterConnectedDevice(type: DeviceType) {
        Log.d(TAG, "Removendo registro de dispositivo ${type.name}")
        connectedDevices.remove(type)
        
        when (type) {
            DeviceType.THERMAL_CAMERA -> _thermalCameraDevice.value = null
            DeviceType.PPG -> _ppgDevice.value = null
        }
    }
    
    /**
     * Obtém o dispositivo conectado para um determinado tipo.
     * 
     * @param type O tipo do dispositivo desejado
     * @return O dispositivo Bluetooth ou null se não houver um conectado deste tipo
     */
    fun getConnectedDevice(type: DeviceType): BluetoothDevice? {
        return connectedDevices[type]
    }
    
    /**
     * Verifica se há um dispositivo conectado para o tipo especificado.
     * 
     * @param type O tipo do dispositivo
     * @return true se houver um dispositivo deste tipo conectado, false caso contrário
     */
    fun hasConnectedDevice(type: DeviceType): Boolean {
        return connectedDevices.containsKey(type)
    }
    
    /**
     * Reconecta ao último dispositivo conhecido de um determinado tipo, se disponível.
     * 
     * @param type O tipo do dispositivo para reconectar
     * @return true se a reconexão foi iniciada, false se não havia dispositivo para reconectar
     */
    fun reconnectLastDevice(type: DeviceType): Boolean {
    val device = connectedDevices[type] ?: return false

    Log.d(TAG, "Tentando reconectar ao último dispositivo ${type.name}: ${device.address}")
    val manager = getBleManager(type)

    when (type) {
        DeviceType.THERMAL_CAMERA -> manager.connectCam(device)
        DeviceType.PPG -> manager.connectPpg(device)
    }

    return true
}
    
    /**
     * Desconecta todos os dispositivos e limpa o registro de dispositivos conectados.
     */
    fun disconnectAll() {
    Log.d(TAG, "Desconectando todos os dispositivos")
    ppgManager?.disconnect()
    camManager?.disconnect()
    connectedDevices.clear()
    _thermalCameraDevice.value = null
    _ppgDevice.value = null
    }
}
