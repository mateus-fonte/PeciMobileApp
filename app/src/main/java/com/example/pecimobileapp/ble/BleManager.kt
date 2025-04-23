package com.example.pecimobileapp.ble

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.util.*

class BleManager(private val context: Context) {

    // UUIDs do seu ESP32 e do serviço de Heart Rate
    private val TIME_UUID      = UUID.fromString("ca68ebcd-a0e5-4174-896d-15ba005b668e")
    private val MODE_UUID      = UUID.fromString("0b5a208c-b1df-4d3d-b188-6a50268ac8c8")
    private val ID_UUID        = UUID.fromString("eee66a40-0189-4dff-9310-b5736f86ee9c")
    private val HR_SERVICE_UUID= UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
    private val HR_CHAR_UUID   = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
    private val CLIENT_CFG_UUID= UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothGatt: BluetoothGatt? = null

    // — Scan results flow —
    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val list = _scanResults.value.toMutableList()
            if (list.none { it.device.address == result.device.address }) {
                list += result
                _scanResults.value = list
            }
        }
    }

    /** Inicia o scan BLE (com permissão) */
    @SuppressLint("MissingPermission")
    fun startBleScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Permissão BLUETOOTH_SCAN não concedida", Toast.LENGTH_SHORT).show()
            return
        }

        val scanner = BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner
            ?: run {
                Toast.makeText(context, "BLE não disponível", Toast.LENGTH_SHORT).show()
                return
            }

        _scanResults.value = emptyList()
        scanner.startScan(scanCallback)
        Handler(Looper.getMainLooper()).postDelayed({
            scanner.stopScan(scanCallback)
            Toast.makeText(context, "Scan BLE finalizado", Toast.LENGTH_SHORT).show()
        }, 10_000)
    }

    // — Heart rate notifications flow —
    private val _heartRate = MutableStateFlow<Int?>(null)
    val heartRate: StateFlow<Int?> = _heartRate

    // — Config write completion flow —
    private val _configSent = MutableStateFlow(false)
    val configSent: StateFlow<Boolean> = _configSent

    /** Conecta e já habilita notificações de Heart Rate */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Permissão BLUETOOTH_CONNECT não concedida", Toast.LENGTH_SHORT).show()
            return
        }

        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt, status: Int, newState: Int
            ) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Conectado ao BLE", Toast.LENGTH_SHORT).show()
                    }
                    gatt.discoverServices()
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val svc = gatt.getService(HR_SERVICE_UUID) ?: return
                    val chr = svc.getCharacteristic(HR_CHAR_UUID) ?: return
                    gatt.setCharacteristicNotification(chr, true)
                    val desc = chr.getDescriptor(CLIENT_CFG_UUID) ?: return
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(desc)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid == HR_CHAR_UUID) {
                    val flag = characteristic.properties
                    val format = if (flag and 0x01 != 0)
                        BluetoothGattCharacteristic.FORMAT_UINT16
                    else
                        BluetoothGattCharacteristic.FORMAT_UINT8
                    val hr = characteristic.getIntValue(format, 1)
                    _heartRate.value = hr
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                Handler(Looper.getMainLooper()).post {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        _configSent.value = true
                        Toast.makeText(
                            context,
                            "Config ${characteristic.uuid} enviada com sucesso",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Erro ao enviar config (${characteristic.uuid}) status $status",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    /** Escreve em qualquer characteristic por UUID */
    @SuppressLint("MissingPermission")
    private fun writeConfig(uuid: UUID, data: ByteArray) {
        val gatt = bluetoothGatt ?: run {
            Toast.makeText(context, "Não conectado ao BLE", Toast.LENGTH_SHORT).show()
            return
        }

        val chr = gatt.services
            .asSequence()
            .flatMap { it.characteristics.asSequence() }
            .firstOrNull { it.uuid == uuid }
            ?: run {
                Toast.makeText(context, "Characteristic $uuid não encontrada", Toast.LENGTH_SHORT).show()
                return
            }

        chr.value = data
        gatt.writeCharacteristic(chr)
    }

    /** Envia timestamp (8 bytes) */
    fun sendTimeConfig(ts: Long) {
        _configSent.value = false
        val buf = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(ts).array()
        writeConfig(TIME_UUID, buf)
    }

    /** Envia mode (1 byte) */
    fun sendModeConfig(mode: Int) {
        _configSent.value = false
        writeConfig(MODE_UUID, byteArrayOf(mode.toByte()))
    }

    /** Envia ID (UTF‑8) */
    fun sendIdConfig(id: String) {
        _configSent.value = false
        writeConfig(ID_UUID, id.toByteArray(Charsets.UTF_8))
    }
}
