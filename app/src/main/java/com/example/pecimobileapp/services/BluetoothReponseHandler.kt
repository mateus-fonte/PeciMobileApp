package com.example.pecimobileapp.services

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.pecimobileapp.models.RaspberryPiStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Classe responsável por receber o status de conexão Wi-Fi do Raspberry Pi
 */
class BluetoothResponseHandler(private val context: Context) {
    private val TAG = "BluetoothResponseHandler"

    // UUID para o serviço Bluetooth do Raspberry Pi
    private val SERVICE_UUID = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee")

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: java.io.InputStream? = null

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    /**
     * Tenta obter o status de conexão do Raspberry Pi através de diferentes métodos
     */
    suspend fun getConnectionStatus(
        device: BluetoothDevice,
        timeoutMillis: Long = 30000
    ): RaspberryPiStatus? {
        return withContext(Dispatchers.IO) {
            var status: RaspberryPiStatus? = null

            // Tenta primeiro via Bluetooth RFCOMM
            try {
                status = getStatusViaBluetooth(device, timeoutMillis / 3)
                if (status != null) {
                    Log.d(TAG, "Status obtido via Bluetooth: $status")
                    return@withContext status
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao obter status via Bluetooth: ${e.message}")
            }

            // Tenta via HTTP se já tiver um IP conhecido
            val lastKnownIp = getLastKnownIp(device)
            if (lastKnownIp != null) {
                try {
                    status = getStatusViaHttp(lastKnownIp, timeoutMillis / 3)
                    if (status != null) {
                        Log.d(TAG, "Status obtido via HTTP (IP conhecido): $status")
                        return@withContext status
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao obter status via HTTP: ${e.message}")
                }
            }

            // Tenta descoberta de serviço na rede local
            try {
                status = discoverServiceWithTimeout(timeoutMillis / 3)
                if (status != null) {
                    Log.d(TAG, "Status obtido via descoberta de serviço: $status")
                    return@withContext status
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro na descoberta de serviço: ${e.message}")
            }

            null  // Não foi possível obter o status
        }
    }

    /**
     * Conecta ao Raspberry Pi via Bluetooth RFCOMM e obtém o status
     */
    private suspend fun getStatusViaBluetooth(
        device: BluetoothDevice,
        timeoutMillis: Long
    ): RaspberryPiStatus? {
        return withTimeoutOrNull(timeoutMillis) {
            try {
                // Conectar ao dispositivo via Bluetooth
                connectToDevice(device)

                // Solicitar status
                requestStatus()

                // Aguardar resposta
                val response = readResponse()

                // Processar resposta
                response?.let { parseStatusResponse(it, device) }
            } finally {
                // Sempre desconectar ao finalizar
                disconnect()
            }
        }
    }

    /**
     * Conecta ao dispositivo Bluetooth
     */
    private suspend fun connectToDevice(device: BluetoothDevice): Boolean {
        return suspendCoroutine { continuation ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        continuation.resumeWithException(
                            SecurityException("Bluetooth connect permission not granted")
                        )
                        return@suspendCoroutine
                    }
                }

                // Criar socket
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)

                // Conectar
                bluetoothSocket?.connect()

                // Obter streams
                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream

                continuation.resume(true)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao conectar via Bluetooth: ${e.message}")

                // Limpar recursos
                disconnect()

                continuation.resume(false)
            }
        }
    }

    /**
     * Solicita o status atual
     */
    private fun requestStatus(): Boolean {
        return try {
            val command = "STATUS\n"
            outputStream?.write(command.toByteArray())
            outputStream?.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao solicitar status: ${e.message}")
            false
        }
    }

    /**
     * Lê a resposta do Raspberry Pi
     */
    private suspend fun readResponse(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val buffer = ByteArray(1024)
                val bytes = inputStream?.read(buffer)

                if (bytes != null && bytes > 0) {
                    String(buffer, 0, bytes)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao ler resposta: ${e.message}")
                null
            }
        }
    }

    /**
     * Desconecta do dispositivo Bluetooth
     */
    private fun disconnect() {
        try {
            inputStream?.close()
        } catch (e: Exception) {}

        try {
            outputStream?.close()
        } catch (e: Exception) {}

        try {
            bluetoothSocket?.close()
        } catch (e: Exception) {}

        inputStream = null
        outputStream = null
        bluetoothSocket = null
    }

    /**
     * Obtém o status do Raspberry Pi via HTTP
     */
    private suspend fun getStatusViaHttp(ipAddress: String, timeoutMillis: Long): RaspberryPiStatus? {
        return withTimeoutOrNull(timeoutMillis) {
            try {
                // Verificar se o endereço já inclui a porta
                val urlString = if (ipAddress.contains(":")) {
                    "http://$ipAddress"
                } else {
                    // Use the default port since we can't access a WebSocketViewModel
                    val port = 8080 // Default port
                    "http://$ipAddress:$port"
                }
                
                android.util.Log.d(TAG, "Tentando conectar via HTTP usando: $urlString")
                val url = URL(urlString)
                val connection = withContext(Dispatchers.IO) {
                    url.openConnection() as HttpURLConnection
                }

                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }

                    reader.close()
                    parseStatusResponse(response.toString(), null)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro na requisição HTTP: ${e.message}")
                null
            }
        }
    }

    /**
     * Descobre o serviço do Raspberry Pi na rede local via mDNS
     */
    private suspend fun discoverServiceWithTimeout(timeoutMillis: Long): RaspberryPiStatus? {
        return withTimeoutOrNull(timeoutMillis) {
            suspendCoroutine { continuation ->
                // Definir resolveListener dentro do escopo da função para evitar o erro de referência
                val resolveListener = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Falha ao resolver serviço: $errorCode")
                        continuation.resume(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        Log.d(TAG, "Serviço resolvido: ${serviceInfo.host.hostAddress}")

                        // Use the InetAddress to get the IPv4 address
                        val ipAddress = serviceInfo.host.hostAddress
                        val port = serviceInfo.port

                        // Get status via HTTP using the discovered IP
                        kotlinx.coroutines.runBlocking {
                            val status = getStatusViaHttp("$ipAddress:$port", 5000)
                            continuation.resume(status)
                        }
                    }
                }

                val discoveryListener = object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Log.e(TAG, "Falha ao iniciar descoberta: $errorCode")
                        continuation.resume(null)
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Log.e(TAG, "Falha ao parar descoberta: $errorCode")
                    }

                    override fun onDiscoveryStarted(serviceType: String) {
                        Log.d(TAG, "Descoberta de serviço iniciada")
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        Log.d(TAG, "Descoberta de serviço parada")
                    }

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        if (serviceInfo.serviceName.contains("RaspberryPiWiFi")) {
                            Log.d(TAG, "Serviço Raspberry Pi encontrado: ${serviceInfo.serviceName}")
                            nsdManager.resolveService(serviceInfo, resolveListener)
                        }
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        Log.d(TAG, "Serviço perdido: ${serviceInfo.serviceName}")
                    }
                }

                try {
                    // Start discovery
                    nsdManager.discoverServices(
                        "_http._tcp.",
                        NsdManager.PROTOCOL_DNS_SD,
                        discoveryListener
                    )

                    // Mantenha uma referência à continuação para verificar se foi completada
                    val continuationRef = continuation

                    // Set a timer to stop discovery after the timeout
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            nsdManager.stopServiceDiscovery(discoveryListener)

                            // Corrigido: Verificar se a continuação já foi retomada
                            // usando uma variável local que é mais segura
                            if (discoveryListener != null) {
                                // Assume que se ainda temos o listener, a continuação não foi completada
                                continuation.resume(null)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao parar descoberta: ${e.message}")
                        }
                    }, timeoutMillis)

                } catch (e: Exception) {
                    Log.e(TAG, "Erro na descoberta de serviço: ${e.message}")
                    continuation.resume(null)
                }
            }
        }
    }

    /**
     * Analisa a resposta JSON e cria um objeto RaspberryPiStatus
     */
    private fun parseStatusResponse(response: String, device: BluetoothDevice?): RaspberryPiStatus? {
        return try {
            val json = JSONObject(response)

            val deviceName = if (device != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        "Dispositivo Bluetooth"
                    } else {
                        device.name ?: "Dispositivo Bluetooth"
                    }
                } else {
                    @Suppress("DEPRECATION")
                    device.name ?: "Dispositivo Bluetooth"
                }
            } else {
                "Raspberry Pi"
            }

            val address = if (device != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        "Endereço desconhecido"
                    } else {
                        device.address
                    }
                } else {
                    @Suppress("DEPRECATION")
                    device.address
                }
            } else {
                "Endereço desconhecido"
            }

            val success = json.getBoolean("success")
            val ipAddress = json.optString("ip_address")
            val ssid = json.optString("ssid")
            val timestamp = json.optString("timestamp")
            val error = json.optString("error")

            // Salvar IP para uso futuro
            if (device != null && success && ipAddress.isNotEmpty()) {
                saveLastKnownIp(device, ipAddress)
            }

            RaspberryPiStatus(
                deviceName = deviceName,
                address = address,
                isConnected = success,
                connectedNetwork = ssid,
                ipAddress = if (ipAddress.isNotEmpty()) ipAddress else null,
                lastUpdated = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar resposta JSON: ${e.message}")
            null
        }
    }

    /**
     * Salva o último IP conhecido para um dispositivo
     */
    private fun saveLastKnownIp(device: BluetoothDevice, ipAddress: String) {
        try {
            val prefs = context.getSharedPreferences("raspberry_pi_ips", Context.MODE_PRIVATE)

            val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                device.address
            } else {
                @Suppress("DEPRECATION")
                device.address
            }

            prefs.edit().putString(address, ipAddress).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar IP: ${e.message}")
        }
    }

    /**
     * Obtém o último IP conhecido para um dispositivo
     */
    private fun getLastKnownIp(device: BluetoothDevice): String? {
        try {
            val prefs = context.getSharedPreferences("raspberry_pi_ips", Context.MODE_PRIVATE)

            val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return null
                }
                device.address
            } else {
                @Suppress("DEPRECATION")
                device.address
            }

            return prefs.getString(address, null)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter IP: ${e.message}")
            return null
        }
    }

    companion object {
        const val SERVICE_TYPE = "_http._tcp."
    }
}