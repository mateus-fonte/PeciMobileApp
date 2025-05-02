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
private val SENSOR_DATA1_UUID      = UUID.fromString("89aa9a0d-48c4-4c32-9854-e3c7f44ec091") // avg_temp
private val SENSOR_DATA2_UUID      = UUID.fromString("a430a2ed-0a76-4418-a5ad-4964699ba17c") // max_temp
private val SENSOR_DATA3_UUID      = UUID.fromString("853f9ba1-94aa-4124-92ff-5a8f576767e4") // min_temp

private val CLIENT_CFG_UUID        = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

class BleManager(private val context: Context) {
    private val TAG = "BleManager"

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private var gatt: BluetoothGatt? = null

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults

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
    fun startScan() {
        val btAdapter = adapter
        if (btAdapter == null) {
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
        val scanner = btAdapter.bluetoothLeScanner ?: return
        scanner.startScan(scanCb)
        Handler(Looper.getMainLooper()).postDelayed({ scanner.stopScan(scanCb) }, 10_000)
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Permissão BLUETOOTH_CONNECT não concedida", Toast.LENGTH_SHORT).show()
            return
        }

        gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _connected.value = false
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) return

                listOf(
                    HR_SERVICE_UUID to HR_CHAR_UUID,
                    SENSOR_SERVICE_UUID to SENSOR_DATA1_UUID,
                    SENSOR_SERVICE_UUID to SENSOR_DATA2_UUID,
                    SENSOR_SERVICE_UUID to SENSOR_DATA3_UUID
                ).forEach { (svcUuid, chrUuid) ->
                    g.getService(svcUuid)?.getCharacteristic(chrUuid)?.let { chr ->
                        g.setCharacteristicNotification(chr, true)
                        chr.getDescriptor(CLIENT_CFG_UUID)?.apply {
                            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            g.writeDescriptor(this)
                        }
                    }
                }
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    _connected.value = true
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                val raw = String(characteristic.value, Charsets.UTF_8)
                val groupId = "grupo1"
                val userId = "aluno01"
                val exerciseId = "exercicio_teste"

                when (characteristic.uuid) {
                    HR_CHAR_UUID -> {
                        val idx = raw.indexOf('.')
                        val afterDot = if (idx != -1 && idx + 1 < raw.length) raw.substring(idx + 1) else raw
                        val digits = afterDot.filter { it.isDigit() }
                        val hrValue = digits.toIntOrNull()
                        _ppgHeartRate.value = hrValue
                        saveToFile(raw)

                        hrValue?.let {
                            MqttManager.publishSensorData(groupId, userId, exerciseId, "ppg", it)
                        }
                    }

                    SENSOR_DATA1_UUID, SENSOR_DATA2_UUID, SENSOR_DATA3_UUID -> {
                        val frac = raw.substringAfter('.', "0")
                        val digits = frac.filter { it.isDigit() }
                        val temp = digits.toIntOrNull()?.div(100f) ?: 0f

                        val source = when (characteristic.uuid) {
                            SENSOR_DATA1_UUID -> {
                                _avgTemp.value = temp; "avg_temp"
                            }
                            SENSOR_DATA2_UUID -> {
                                _maxTemp.value = temp; "max_temp"
                            }
                            SENSOR_DATA3_UUID -> {
                                _minTemp.value = temp; "min_temp"
                            }
                            else -> "unknown"
                        }

                        saveToFile(raw)
                        MqttManager.publishSensorData(groupId, userId, exerciseId, source, temp)
                    }
                }
            }
        })
    }

    fun connectPpg(device: BluetoothDevice) = connect(device)
    fun connectCam(device: BluetoothDevice) = connect(device)

    private fun saveToFile(data: String) {
        try {
            val path = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(path, "bpm_log.txt")
            val writer = FileWriter(file, true)
            writer.append(data).append("\n").flush()
            writer.close()
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao salvar no arquivo", e)
        }
    }
}
