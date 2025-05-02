package com.example.pecimobileapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.pecimobileapp.ui.navigation.AppNavigation
import com.example.pecimobileapp.ui.theme.PeciMobileAppTheme
import com.example.pecimobileapp.viewmodels.BluetoothViewModel
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import java.nio.charset.StandardCharsets
import java.util.*

class MainActivity : ComponentActivity() {
    private val bluetoothViewModel: BluetoothViewModel by viewModels()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val mqttHandler = Handler(Looper.getMainLooper())
    private val mqttClient = MqttClient.builder()
        .useMqttVersion3()
        .serverHost("48.217.187.110")
        .serverPort(1883)
        .identifier("AndroidClient_${UUID.randomUUID()}")
        .buildBlocking()

    private val requestBluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            ensureBluetoothIsEnabled()
        } else {
            Toast.makeText(
                this,
                "As permissões de Bluetooth são necessárias para o funcionamento do app",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            bluetoothViewModel.updatePairedDevices()
        } else {
            Toast.makeText(
                this,
                "Bluetooth é obrigatório para o funcionamento do app",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PeciMobileAppTheme {
                AppNavigation(bluetoothViewModel)
            }
        }

        connectAndSendDummyData()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth não está disponível neste dispositivo", Toast.LENGTH_LONG).show()
            return
        }

        checkBluetoothPermissions()
    }

    private fun checkBluetoothPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestBluetoothPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            ensureBluetoothIsEnabled()
        }
    }

    private fun ensureBluetoothIsEnabled() {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            bluetoothViewModel.updatePairedDevices()
        }
    }

    private fun connectAndSendDummyData() {
        try {
            mqttClient.connect()
            startSendingDummyData()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startSendingDummyData() {
        mqttHandler.postDelayed(object : Runnable {
            var bpm = 80
            var temp = 36.5
            val alunoId = "aluno01"

            override fun run() {
                val bpmVariation = (-2..2).random()
                bpm = (bpm + bpmVariation).coerceIn(60, 100)
                val tempVariation = listOf(-0.1, 0.0, 0.1).random()
                temp = (temp + tempVariation).coerceIn(36.0, 38.0)
                val timestamp = System.currentTimeMillis()

                val ppgPayload = """{"ts":$timestamp,"id":"$alunoId","bpm":$bpm}"""
                val swPayload = """{"ts":$timestamp,"id":"$alunoId","bpm":${bpm + 1}}"""
                val camPayload = """{"ts":$timestamp,"id":"$alunoId","temp":$temp}"""

                mqttClient.publishWith()
                    .topic("/ppg/bpm")
                    .payload(ppgPayload.toByteArray(StandardCharsets.UTF_8))
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .send()

                mqttClient.publishWith()
                    .topic("/sw/bpm")
                    .payload(swPayload.toByteArray(StandardCharsets.UTF_8))
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .send()

                mqttClient.publishWith()
                    .topic("/cam/temp")
                    .payload(camPayload.toByteArray(StandardCharsets.UTF_8))
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .send()

                mqttHandler.postDelayed(this, 2000)
            }
        }, 2000)
    }
}
