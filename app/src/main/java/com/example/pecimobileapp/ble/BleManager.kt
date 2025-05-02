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
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

private val HR_SERVICE_UUID        = UUID.fromString("e626a696-36ba-45b3-a444-5c28eb674dd5")
private val HR_CHAR_UUID           = UUID.fromString("aa4fe3ac-56c4-42c7-856e-500b8d4b1a01")

private val SENSOR_SERVICE_UUID    = UUID.fromString("b07d5e84-4d21-4d4a-8694-5ed9f6aa2aee")
private val SENSOR_DATA1_UUID      = UUID.fromString("89aa9a0d-48c4-4c32-9854-e3c7f44ec091")
private val SENSOR_DATA2_UUID      = UUID.fromString("a430a2ed-0a76-4418-a5ad-4964699ba17c")
private val SENSOR_DATA3_UUID      = UUID.fromString("853f9ba1-94aa-4124-92ff-5a8f576767e4")

private val CLIENT_CFG_UUID        = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

// UUIDs do serviço de configuração e suas 6 características
private val CONFIG_SERVICE_UUID      = UUID.fromString("0a3b6985-dad6-4759-8852-dcb266d3a59e")
private val CONFIG_TIME_UUID         = UUID.fromString("ca68ebcd-a0e5-4174-896d-15ba005b668e")
private val CONFIG_ID_UUID           = UUID.fromString("eee66a40-0189-4dff-9310-b5736f86ee9c")
private val CONFIG_FREQ_UUID         = UUID.fromString("e742e008-0366-4ec2-b815-98b814112ddc")
private val CONFIG_SSID_UUID         = UUID.fromString("ab35e54e-fde4-4f83-902a-07785de547b9")
private val CONFIG_PASS_UUID         = UUID.fromString("c1c4b63b-bf3b-4e35-9077-d5426226c710")
private val CONFIG_SERVERIP_UUID     = UUID.fromString("0c954d7e-9249-456d-b949-cc079205d393")

class BleManager(private val context: Context) : BluetoothGattCallback() {

    private val TAG = "BleManager"

    // fila de writes para as 6 configs
    private val writeQueue = ArrayDeque<Pair<UUID, ByteArray>>()
    private val _allConfigSent = MutableStateFlow(false)
    val allConfigSent: StateFlow<Boolean> = _allConfigSent

    // GATT e BLE
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private var gatt: BluetoothGatt? = null

    // para guardar o último device e controlar tentativas
    private var lastDevice: BluetoothDevice? = null
    private var retryCount = 0
    private val maxRetries = 3
    private val retryDelayMs = 5_000L

    // scan results
    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults

    // — sinaliza que não conseguiu reconectar após maxRetries —
    private val _connectionLost = MutableStateFlow(false)
    val connectionLost: StateFlow<Boolean> = _connectionLost

    // estados de conexão e dados recebidos
    private val _connected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _connected

    private val _ppgHeartRate = MutableStateFlow<Int?>(null)
    val ppgHeartRate: StateFlow<Int?> = _ppgHeartRate

    private val _avgTemp = MutableStateFlow<Float?>(null)
    val avgTemp: StateFlow<Float?> = _avgTemp

    private val _maxTemp = MutableStateFlow<Float?>(null)
    val maxTemp: StateFlow<Float?> = _maxTemp

    private val _minTemp = MutableStateFlow<Float?>(null)
    val minTemp: StateFlow<Float?> = _minTemp

    // scan callback
    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val list = _scanResults.value.toMutableList()
            if (list.none { it.device.address == result.device.address }) {
                list += result
                _scanResults.value = list
            }
        }
    }

    /** Inicia o scan BLE (10s) */
    @SuppressLint("MissingPermission")
    fun startScan() {
        val btAdapter = adapter ?: run {
            Toast.makeText(context, "BLE não disponível", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Permissão BLUETOOTH_SCAN não concedida", Toast.LENGTH_SHORT).show()
            return
        }
        _scanResults.value = emptyList()
        val scanner = btAdapter.bluetoothLeScanner ?: run {
            Toast.makeText(context, "Scanner BLE não disponível", Toast.LENGTH_SHORT).show()
            return
        }
        scanner.startScan(scanCb)
        Handler(Looper.getMainLooper()).postDelayed({
            scanner.stopScan(scanCb)
        }, 10_000)
    }

    /** Conecta e habilita NOTIFY em todas as sensors + preenche fila de writes */
    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        lastDevice = device
        retryCount = 0
        _connectionLost.value = false
        doConnect(device)

        val btAdapter = adapter ?: run {
            Toast.makeText(context, "BLE não disponível", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Permissão BLUETOOTH_CONNECT não concedida", Toast.LENGTH_SHORT).show()
            return
        }
        gatt = device.connectGatt(context, false, this)
    }

    @SuppressLint("MissingPermission")
    private fun doConnect(device: BluetoothDevice) {
        gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    retryCount = 0
                    _connected.value = true
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _connected.value = false
                    attemptReconnect()
                }
            }
        })
    }
    private fun attemptReconnect() {
        val device = lastDevice ?: return
        if (retryCount < maxRetries) {
            retryCount++
            Handler(Looper.getMainLooper()).postDelayed({
                doConnect(device)
            }, retryDelayMs)
        } else {
            _connectionLost.value = true
        }
    }

    fun connectPpg(device: BluetoothDevice) = connect(device)
    fun connectCam(device: BluetoothDevice) = connect(device)

    // —— BluetoothGattCallback ——

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            g.discoverServices()
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            _connected.value = false
        }
    }

    @SuppressLint("MissingPermission")
    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) return

        // habilita NOTIFY em todas as 4 características de sensor
        listOf(
            HR_SERVICE_UUID    to HR_CHAR_UUID,
            SENSOR_SERVICE_UUID to SENSOR_DATA1_UUID,
            SENSOR_SERVICE_UUID to SENSOR_DATA2_UUID,
            SENSOR_SERVICE_UUID to SENSOR_DATA3_UUID
        ).forEach { (svc, chrUuid) ->
            g.getService(svc)
                ?.getCharacteristic(chrUuid)
                ?.also { chr ->
                    g.setCharacteristicNotification(chr, true)
                    chr.getDescriptor(CLIENT_CFG_UUID)
                        ?.apply {
                            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            g.writeDescriptor(this)
                        }
                }
        }
    }

    override fun onDescriptorWrite(
        g: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            _connected.value = true
        }
    }


    override fun onCharacteristicChanged(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        val raw = String(characteristic.value, Charsets.UTF_8)
        when (characteristic.uuid) {
            HR_CHAR_UUID -> {
                val afterDot = raw.substringAfter('.', "")
                val digits = afterDot.filter(Char::isDigit)
                _ppgHeartRate.value = digits.toIntOrNull()
                saveToFile(raw)
            }
            SENSOR_DATA1_UUID -> {
                val frac = raw.substringAfter('.', "0").filter(Char::isDigit)
                _avgTemp.value = frac.toIntOrNull()?.div(100f)
                saveToFile(raw)
            }
            SENSOR_DATA2_UUID -> {
                val frac = raw.substringAfter('.', "0").filter(Char::isDigit)
                _maxTemp.value = frac.toIntOrNull()?.div(100f)
                saveToFile(raw)
            }
            SENSOR_DATA3_UUID -> {
                val frac = raw.substringAfter('.', "0").filter(Char::isDigit)
                _minTemp.value = frac.toIntOrNull()?.div(100f)
                saveToFile(raw)
            }
        }
    }

    // ========== CONFIG WRITES ==========

    // ❷ método interno que dispara o próximo write da fila
    @SuppressLint("MissingPermission")
    private fun writeNextConfig() {
        val (uuid, data) = writeQueue.firstOrNull() ?: return
        val g = gatt ?: run {
            Toast.makeText(context, "Não conectado ao BLE", Toast.LENGTH_SHORT).show()
            writeQueue.clear()
            return
        }
        val svc = g.getService(CONFIG_SERVICE_UUID) ?: run {
            Toast.makeText(context, "Serviço de config não encontrado", Toast.LENGTH_SHORT).show()
            writeQueue.clear()
            return
        }
        val chr = svc.getCharacteristic(uuid) ?: run {
            Toast.makeText(context, "Characteristic $uuid não encontrada", Toast.LENGTH_SHORT).show()
            writeQueue.removeFirst()
            writeNextConfig()
            return
        }
        chr.value = data
        if (!g.writeCharacteristic(chr)) {
            // falhou imediatamente
            Toast.makeText(context, "Falha no write de $uuid", Toast.LENGTH_SHORT).show()
            writeQueue.removeFirst()
            writeNextConfig()
        }
    }


    fun sendAllConfigs(
        ssid: String,
        password: String,
        serverIp: String
    ) {
        writeQueue.clear()
        writeQueue.addAll(listOf(
            CONFIG_SSID_UUID    to ssid.toByteArray(Charsets.UTF_8),
            CONFIG_PASS_UUID    to password.toByteArray(Charsets.UTF_8),
            CONFIG_SERVERIP_UUID to serverIp.toByteArray(Charsets.UTF_8)
        ))
        writeNextConfig()
    }

    override fun onCharacteristicWrite(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        super.onCharacteristicWrite(g, characteristic, status)
        val uuid = characteristic.uuid
        val ok = status == BluetoothGatt.GATT_SUCCESS
        Handler(Looper.getMainLooper()).post { Toast.makeText(context,"Write ${characteristic.uuid} → ${if (ok) "OK" else "ERRO $status"}", Toast.LENGTH_SHORT).show() }

        // só removemos se for o próximo esperado
        if (writeQueue.firstOrNull()?.first == uuid) {
            writeQueue.removeFirst()
            writeNextConfig()
        } else {
            // se desencontrou, limpa tudo
            writeQueue.clear()
        }
    }

    private fun saveToFile(data: String) {
        try {
            val path = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(path, "bpm_log.txt")
            val writer = FileWriter(file, true)
            writer.append(data).append("\n")
            writer.flush()
            writer.close()
            Log.d(TAG, "Salvo no arquivo: $data no caminho: ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao salvar no arquivo", e)
        }
    }
}

// utilitário para removê-lo sem erro
private fun <E> ArrayDeque<E>.removeFirstOrNull() {
    if (isNotEmpty()) removeFirst()
}
