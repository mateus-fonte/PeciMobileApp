package com.example.pecimobileapp.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.pecimobileapp.mqtt.MqttManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*

private val HR_SERVICE_UUID        = UUID.fromString("e626a696-36ba-45b3-a444-5c28eb674dd5")
private val HR_CHAR_UUID           = UUID.fromString("aa4fe3ac-56c4-42c7-856e-500b8d4b1a01")

private val SENSOR_SERVICE_UUID    = UUID.fromString("b07d5e84-4d21-4d4a-8694-5ed9f6aa2aee")
private val SENSOR_DATA1_UUID      = UUID.fromString("89aa9a0d-48c4-4c32-9854-e3c7f44ec091")
private val SENSOR_DATA2_UUID      = UUID.fromString("a430a2ed-0a76-4418-a5ad-4964699ba17c")
private val SENSOR_DATA3_UUID      = UUID.fromString("853f9ba1-94aa-4124-92ff-5a8f576767e4")

private val CLIENT_CFG_UUID        = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

private val CONFIG_SERVICE_UUID    = UUID.fromString("0a3b6985-dad6-4759-8852-dcb266d3a59e")
private val CONFIG_SSID_UUID       = UUID.fromString("ab35e54e-fde4-4f83-902a-07785de547b9")
private val CONFIG_PASS_UUID       = UUID.fromString("c1c4b63b-bf3b-4e35-9077-d5426226c710")
private val CONFIG_SERVERIP_UUID   = UUID.fromString("0c954d7e-9249-456d-b949-cc079205d393")

class BleManager(private val context: Context) : BluetoothGattCallback() {

    private val TAG = "BleManager"

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private var gatt: BluetoothGatt? = null

    // Variáveis para armazenar as últimas configurações WiFi
    private var lastWifiSsid: String = ""
    private var lastWifiPassword: String = ""
    private var lastServerIp: String = ""
    private var hasStoredWifiConfig: Boolean = false

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults

    private val _connected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _connected

    private val _connectionLost = MutableStateFlow(false)
    val connectionLost: StateFlow<Boolean> = _connectionLost

    private val _ppgHeartRate = MutableStateFlow<Int?>(null)
    val ppgHeartRate: StateFlow<Int?> = _ppgHeartRate

    private val _avgTemp = MutableStateFlow<Float?>(null)
    val avgTemp: StateFlow<Float?> = _avgTemp

    private val _maxTemp = MutableStateFlow<Float?>(null)
    val maxTemp: StateFlow<Float?> = _maxTemp

    private val _minTemp = MutableStateFlow<Float?>(null)
    val minTemp: StateFlow<Float?> = _minTemp

    private val _allConfigSent = MutableStateFlow(false)
    val allConfigSent: StateFlow<Boolean> = _allConfigSent

    private val _configProgress = MutableStateFlow(0f)
    val configProgress: StateFlow<Float> = _configProgress

    private val totalConfigSteps = 5
    private val writeQueue = ArrayDeque<Pair<UUID, ByteArray>>()
    private var lastDevice: BluetoothDevice? = null
    private var retryCount = 0
    private val maxRetries = 3
    private val retryDelayMs = 5000L

    // Adicionando variáveis para verificação periódica de conexão
    private val connectionCheckHandler = Handler(Looper.getMainLooper())
    private val connectionCheckRunnable = object : Runnable {
        override fun run() {
            checkConnection()
            // Agendar novamente em 5 segundos
            connectionCheckHandler.postDelayed(this, 5000)
        }
    }
    private var lastCommunicationTime = System.currentTimeMillis()
    private val connectionTimeout = 10000L // 10 segundos
    private var connectionCheckActive = false

    // Sessão atual
    private var groupId: String = "grupo1"
    private var userId: String = "aluno01"
    private var exerciseId: String = "exercicio_teste"
    private var selectedZone: Int = 1
    private var zonas: List<Pair<String, IntRange>> = emptyList()

    fun setSessionParameters(
        group: String?,
        user: String,
        exercise: String,
        selectedZone: Int,
        zonasList: List<Pair<String, IntRange>>
    ) {
        this.groupId = group ?: "false"
        this.userId = user
        this.exerciseId = exercise
        this.selectedZone = selectedZone
        this.zonas = zonasList
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val btAdapter = adapter ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) return

        _scanResults.value = emptyList()
        val scanner = btAdapter.bluetoothLeScanner ?: return
        scanner.startScan(scanCb)
        Handler(Looper.getMainLooper()).postDelayed({ scanner.stopScan(scanCb) }, 10_000)
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val list = _scanResults.value.toMutableList()
            if (list.none { it.device.address == result.device.address }) {
                list += result
                _scanResults.value = list
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        lastDevice = device
        retryCount = 0
        _connectionLost.value = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) return

        gatt = device.connectGatt(context, false, this)

        // Iniciar verificação periódica de conexão
        startConnectionCheck()
    }

    fun connectPpg(device: BluetoothDevice) = connect(device)
    fun connectCam(device: BluetoothDevice) = connect(device)

    private fun attemptReconnect() {
        lastDevice?.let {
            if (retryCount < maxRetries) {
                retryCount++
                Handler(Looper.getMainLooper()).postDelayed({ connect(it) }, retryDelayMs)
            } else {
                _connectionLost.value = true
                stopConnectionCheck()
            }
        }
    }

    // Método para iniciar verificação periódica
    private fun startConnectionCheck() {
        if (!connectionCheckActive) {
            connectionCheckActive = true
            lastCommunicationTime = System.currentTimeMillis()
            connectionCheckHandler.postDelayed(connectionCheckRunnable, 5000)
        }
    }

    // Método para parar verificação periódica
    private fun stopConnectionCheck() {
        connectionCheckActive = false
        connectionCheckHandler.removeCallbacks(connectionCheckRunnable)
    }

    // Verificar se a conexão está ativa
    private fun checkConnection() {
        if (!_connected.value) return

        val timeSinceLastCommunication = System.currentTimeMillis() - lastCommunicationTime
        if (timeSinceLastCommunication > connectionTimeout) {
            Log.d(TAG, "Conexão parece estar inativa por $timeSinceLastCommunication ms. Considerando como desconectada.")
            handleConnectionLost()
        }
    }

    // Lidar com conexão perdida
    private fun handleConnectionLost() {
        if (_connected.value) {
            Log.d(TAG, "Detectada desconexão por inatividade")
            _connected.value = false
            _connectionLost.value = true
            gatt?.close()
            gatt = null
            stopConnectionCheck()
        }
    }

    // Atualizar timestamp da última comunicação
    private fun updateLastCommunicationTime() {
        lastCommunicationTime = System.currentTimeMillis()
    }

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            retryCount = 0
            _connected.value = true
            g.discoverServices()
            updateLastCommunicationTime()
            
            // Se isso for uma reconexão (não uma conexão inicial) e tivermos configurações WiFi armazenadas
            if (hasStoredWifiConfig && lastDevice != null) {
                Log.d(TAG, "Reconexão bem-sucedida, reenviando configurações WiFi")
                // Reenviar configurações WiFi após um pequeno delay para garantir que os serviços foram descobertos
                Handler(Looper.getMainLooper()).postDelayed({
                    if (_connected.value) {
                        sendAllConfigs(lastWifiSsid, lastWifiPassword, lastServerIp)
                        Log.d(TAG, "Configurações WiFi reenviadas automaticamente após reconexão")
                    }
                }, 2000) // 2 segundos de delay
            }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            _connected.value = false
            attemptReconnect()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) return

        listOf(
            HR_SERVICE_UUID to HR_CHAR_UUID,
            SENSOR_SERVICE_UUID to SENSOR_DATA1_UUID,
            SENSOR_SERVICE_UUID to SENSOR_DATA2_UUID,
            SENSOR_SERVICE_UUID to SENSOR_DATA3_UUID
        ).forEach { (svc, chrUuid) ->
            g.getService(svc)?.getCharacteristic(chrUuid)?.let { chr ->
                g.setCharacteristicNotification(chr, true)
                chr.getDescriptor(CLIENT_CFG_UUID)?.apply {
                    value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(this)
                }
            }
        }

        if (writeQueue.isNotEmpty()) writeNextConfig()
        updateLastCommunicationTime()
    }

    override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            _connected.value = true
        }
        updateLastCommunicationTime()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val raw = String(characteristic.value, Charsets.UTF_8)

        updateLastCommunicationTime()

        when (characteristic.uuid) {
            HR_CHAR_UUID -> {
                val afterDot = raw.substringAfter('.', "")
                val digits = afterDot.filter(Char::isDigit)
                val hr = digits.toIntOrNull()
                _ppgHeartRate.value = hr
                saveToFile(raw)
                hr?.let {
                    MqttManager.publishSensorData(groupId, userId, exerciseId, "ppg", it, selectedZone, zonas)
                }
            }

            SENSOR_DATA1_UUID, SENSOR_DATA2_UUID, SENSOR_DATA3_UUID -> {
                val temp = raw.substringAfter('.', "0").filter(Char::isDigit).toIntOrNull()?.div(100f) ?: 0f
                val source = when (characteristic.uuid) {
                    SENSOR_DATA1_UUID -> { _avgTemp.value = temp; "avg_temp" }
                    SENSOR_DATA2_UUID -> { _maxTemp.value = temp; "max_temp" }
                    SENSOR_DATA3_UUID -> { _minTemp.value = temp; "min_temp" }
                    else -> "unknown"
                }
                saveToFile(raw)
                MqttManager.publishSensorData(groupId, userId, exerciseId, source, temp, selectedZone, zonas)
            }
        }
    }

    private fun writeNextConfig() {
        val (uuid, data) = writeQueue.firstOrNull() ?: run {
            _allConfigSent.value = true
            return
        }

        val g = gatt ?: return
        val svc = g.getService(CONFIG_SERVICE_UUID) ?: run {
            writeQueue.clear()
            return
        }

        val chr = svc.getCharacteristic(uuid) ?: run {
            writeQueue.removeFirst()
            writeNextConfig()
            return
        }

        chr.value = data
        if (!g.writeCharacteristic(chr)) {
            writeQueue.removeFirst()
            writeNextConfig()
        }
        updateLastCommunicationTime()
    }

    fun sendAllConfigs(ssid: String, password: String, serverIp: String) {
        // Armazena as configurações WiFi para uso futuro durante reconexão
        lastWifiSsid = ssid
        lastWifiPassword = password
        lastServerIp = serverIp
        hasStoredWifiConfig = true
        
        writeQueue.clear()
        writeQueue.addAll(
            listOf(
                CONFIG_SSID_UUID to ssid.toByteArray(),
                CONFIG_PASS_UUID to password.toByteArray(),
                CONFIG_SERVERIP_UUID to serverIp.toByteArray()
            )
        )
        writeNextConfig()
    }

    override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        val uuid = characteristic.uuid
        val success = status == BluetoothGatt.GATT_SUCCESS

        updateLastCommunicationTime()

        if (success) {
            val step = 1f / totalConfigSteps
            _configProgress.value += step
            Log.d(TAG, "Configuração BLE: progresso atualizado para ${_configProgress.value * 100}%")
        }

        if (writeQueue.firstOrNull()?.first == uuid) {
            writeQueue.removeFirst()
            writeNextConfig()
        } else {
            writeQueue.clear()
        }
    }

    private fun saveToFile(data: String) {
        try {
            val path = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(path, "bpm_log.txt")
            FileWriter(file, true).use { it.append(data).append("\n") }
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao salvar no arquivo", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        _connected.value = false
        stopConnectionCheck()
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao desconectar", e)
        } finally {
            gatt = null
        }
    }
}
