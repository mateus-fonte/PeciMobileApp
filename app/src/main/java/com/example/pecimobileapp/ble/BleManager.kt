package com.example.pecimobileapp.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import com.example.pecimobileapp.ble.DeviceType
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.pecimobileapp.mqtt.MqttManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*
import kotlin.math.pow

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


    // Manter registro do tipo de dispositivo atual
    var currentDeviceType: DeviceType? = null
        private set

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private var gatt: BluetoothGatt? = null

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

    private val _desempenhoPct = MutableStateFlow(0f)
    val desempenhoPct: StateFlow<Float> = _desempenhoPct


    private val totalConfigSteps = 5
    private val writeQueue = ArrayDeque<Pair<UUID, ByteArray>>()
    private var lastDevice: BluetoothDevice? = null
    private var retryCount = 0
    private val maxRetries = 3
    private val retryDelayMs = 5000L
    private var activityStarted = false

    private var expectingDisconnect = false

    fun setExpectingDisconnect(expecting: Boolean) {
        expectingDisconnect = expecting
    }

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
    private val connectionTimeout = 600000L // 10 minutos
    private var connectionCheckActive = false

    // Sessão atual
    private var groupId: String = "grupo1"
    private var userId: String = "aluno01"
    private var exerciseId: String = "exercicio_teste"
    private var selectedZone: Int = 1
    private var zonas: List<Pair<String, IntRange>> = emptyList()
    private var tempoTotal = 0
    private var tempoNaZonaAlvo = 0

    fun setSessionParameters(
    group: String?,
    user: String,
    exercise: String,
    selectedZone: Int,
    zonasList: List<Pair<String, IntRange>>
) {
    // Se não houver grupo, use o userId como groupId para tópicos individuais
    this.groupId = group ?: user
    this.userId = user
    this.exerciseId = exercise
    this.selectedZone = selectedZone
    this.zonas = zonasList
}

    @RequiresApi(Build.VERSION_CODES.O)
    fun startActivity() {
        activityStarted = true
        tempoTotal = 0
        tempoNaZonaAlvo = 0
        MqttManager.WorkoutSessionManager.resetSession()
        Log.d(TAG, "Atividade iniciada: envio de dados via MQTT liberado")
    }

    fun stopActivity() {
        activityStarted = false
        Log.d(TAG, "Atividade encerrada: envio de dados via MQTT bloqueado")
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
        // Desconectar qualquer conexão existente
        gatt?.let { existingGatt ->
            if (existingGatt.device.address != device.address) {
                Log.d(TAG, """
                    Desconectando dispositivo anterior:
                    Endereço anterior: ${existingGatt.device.address}
                    Novo endereço: ${device.address}
                """.trimIndent())
                existingGatt.disconnect()
                existingGatt.close()
                gatt = null
            }
        }
        
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
        updateLastCommunicationTime()
    }    

    @SuppressLint("MissingPermission")
fun connectPpg(device: BluetoothDevice) {
    Log.d(TAG, """
        ===== Conectando PPG =====
        Nome: ${device.name ?: "N/A"}
        Endereço: ${device.address}
        Tipo: ${device.type}
        Estado atual: ${if (_connected.value) "Conectado" else "Desconectado"}
        ====================
    """.trimIndent())

    // Desconectar dispositivo PPG anterior, se necessário
    BleManagerProvider.getInstance().getConnectedDevice(DeviceType.PPG)?.let {
        Log.d(TAG, "Desconectando dispositivo PPG anterior: ${it.address}")
        disconnect()
    }

    currentDeviceType = DeviceType.PPG
    connect(device)

    // Registrar o dispositivo no provider
    BleManagerProvider.getInstance().registerConnectedDevice(device, DeviceType.PPG)
}

    @SuppressLint("MissingPermission")
fun connectCam(device: BluetoothDevice) {
    Log.d(TAG, """
        ===== Conectando à Câmera Térmica =====
        Nome: ${device.name ?: "N/A"}
        Endereço: ${device.address}
        Tipo: ${device.type}
        Estado atual: ${if (_connected.value) "Conectado" else "Desconectado"}
        ====================
    """.trimIndent())

    // Verificar permissões BLE
    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        Log.e(TAG, "Permissão BLUETOOTH_CONNECT não concedida. Não é possível conectar.")
        return
    }

    // Desconectar dispositivo anterior do mesmo tipo, se necessário
    BleManagerProvider.getInstance().getConnectedDevice(DeviceType.THERMAL_CAMERA)?.let {
        Log.d(TAG, "Desconectando câmera térmica anterior: ${it.address}")
        disconnect()
    }

    // Atualizar o tipo de dispositivo atual
    currentDeviceType = DeviceType.THERMAL_CAMERA

    // Iniciar conexão com o dispositivo
    connect(device)

    // Registrar o dispositivo no BleManagerProvider
    BleManagerProvider.getInstance().registerConnectedDevice(device, DeviceType.THERMAL_CAMERA)

    Log.d(TAG, "Câmera térmica conectada com sucesso: ${device.address}")
}
    
    private var maxRetryAttempts = 3

    private fun attemptReconnect() {
        if (retryCount < maxRetryAttempts) {
            retryCount++
            val delayMs = (1000L * (2.0.pow(retryCount - 1))).toLong() // Exponential backoff
            Log.d(TAG, "Tentativa de reconexão $retryCount de $maxRetryAttempts em ${delayMs}ms")
            
            Handler(Looper.getMainLooper()).postDelayed({
                lastDevice?.let { device ->
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "Iniciando tentativa de reconexão $retryCount")
                        connect(device)
                    }
                }
            }, delayMs)
        } else {
            Log.e(TAG, "Máximo de tentativas de reconexão atingido")
            _connectionLost.value = true
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

    private fun checkConnection() {
        if (!_connected.value) return

        val timeSinceLastCommunication = System.currentTimeMillis() - lastCommunicationTime
        if (timeSinceLastCommunication > connectionTimeout) {
            Log.d(TAG, "Conexão parece estar inativa por $timeSinceLastCommunication ms. Tentando reconectar...")
            attemptReconnect() // Tenta reconectar antes de considerar desconectado
        }
    }

    // Lidar com conexão perdida
    private fun handleConnectionLost() {
        if (_connected.value) {
            Log.d(TAG, "Detectada desconexão por inatividade")
            _connected.value = false
            _connectionLost.value = true
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
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
        val deviceInfo = "${g.device.name ?: g.device.address} (${currentDeviceType ?: "Unknown Type"})"

        // Map de status para strings legíveis
        val statusStr = when (status) {
            BluetoothGatt.GATT_SUCCESS -> "SUCESSO"
            133 -> "GATT_ERROR (Possível timeout)"
            8 -> "GATT_CONN_TIMEOUT"
            19 -> "GATT_CONN_TERMINATE_PEER_USER"
            22 -> "GATT_CONN_TERMINATE_LOCAL_HOST"
            62 -> "GATT_ERROR (Possível falha na conexão)"
            else -> "DESCONHECIDO ($status)"
        }

        // Map dos estados para strings legíveis
        val stateStr = when (newState) {
            BluetoothGatt.STATE_DISCONNECTED -> "DESCONECTADO"
            BluetoothGatt.STATE_CONNECTING -> "CONECTANDO"
            BluetoothGatt.STATE_CONNECTED -> "CONECTADO"
            BluetoothGatt.STATE_DISCONNECTING -> "DESCONECTANDO"
            else -> "DESCONHECIDO"
        }

        Log.d(TAG, """
            ===== Mudança de Estado BLE =====
            Dispositivo: $deviceInfo
            Estado: $stateStr
            Status: $statusStr
            Desconexão Esperada: $expectingDisconnect
            Tipo: ${currentDeviceType?.name ?: "Unknown"}
            ================================
        """.trimIndent())

        when (newState) {
            BluetoothGatt.STATE_DISCONNECTED -> {
                _connected.value = false
                gatt?.close()
                gatt = null                // Handle unexpected disconnections
                if (!expectingDisconnect) {
                    _connectionLost.value = true
                    Log.w(TAG, "Desconexão inesperada detectada para $deviceInfo")
                    
                    when (status) {
                        19, 22 -> { // Terminated by peer or host - don't auto reconnect
                            Log.d(TAG, "Conexão terminada normalmente: $statusStr")
                        }
                        else -> { // Auto reconnect for all other cases
                            Log.d(TAG, "Tentando reconexão automática após desconexão inesperada")
                            Handler(Looper.getMainLooper()).postDelayed({
                                lastDevice?.let { device ->
                                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                        Log.d(TAG, "Iniciando reconexão para $deviceInfo")
                                        connect(device)
                                    }
                                }
                            }, 2000) // 2 second delay before reconnecting
                        }
                    }
                }
                // Reset the flag after use
                expectingDisconnect = false            }
        }
        when (newState) {
            BluetoothGatt.STATE_CONNECTED -> {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    _connected.value = true
                    _connectionLost.value = false
                    retryCount = 0 // Reset retry count on successful connection
                    
                    // Start discovering services
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Se reconectou, verifica se o dispositivo é a câmera térmica e reinicia o processo
                        if (currentDeviceType == DeviceType.THERMAL_CAMERA) {
                            Log.d(TAG, "Reconexão bem sucedida com a câmera térmica, reiniciando processo de configuração")
                            // Tenta resgatar últimas configurações conhecidas
                            val prefs = context.getSharedPreferences("wifi_config", Context.MODE_PRIVATE)
                            val lastSsid = prefs.getString("last_ssid", null)
                            val lastPass = prefs.getString("last_pass", null)
                            val lastServerIp = prefs.getString("last_server_ip", null)
                            
                            if (lastSsid != null && lastPass != null && lastServerIp != null) {
                                Log.d(TAG, "Reenviando últimas configurações conhecidas: SSID=$lastSsid")
                                sendAllConfigs(lastSsid, lastPass, lastServerIp)
                            }
                        }
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            g.discoverServices()
                        }
                    }, 600) // Add a small delay before service discovery
                } else {
                    Log.e(TAG, "Conexão falhou com status: $statusStr")
                    g.close()
                    // Try to reconnect if the connection failed
                    attemptReconnect()
                }
            }
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
    // Verificar se os dados vêm do dispositivo conectado
    if (g.device.address != lastDevice?.address) {
        Log.w(TAG, """
            Ignorando dados de dispositivo não autorizado:
            Recebido de: ${g.device.address}
            Dispositivo conectado: ${lastDevice?.address}
        """.trimIndent())
        return
    }

    val raw = String(characteristic.value, Charsets.UTF_8)
    updateLastCommunicationTime()

    if (!activityStarted) {
        Log.d(TAG, "Dados recebidos, mas atividade ainda não começou. Ignorando envio MQTT.")
        return
    }

    when (characteristic.uuid) {
        HR_CHAR_UUID -> {
            if (currentDeviceType == DeviceType.PPG) {
                Log.d(TAG, "Dados PPG recebidos (raw): $raw")

                // Extrair todos os dígitos da string
                val bpmStr = raw.substringAfterLast(".").takeWhile { it.isDigit() }
                val hr = bpmStr.toIntOrNull()?:0//.takeIf { it in 30..220 }

                // Log detalhado dos valores
                Log.d(TAG, """
                        ===== Dados PPG =====
                        Raw data: $raw
                        Dígitos filtrados: $bpmStr
                        BPM calculado: ${hr ?: "inválido"}
                        ====================
                    """.trimIndent())

                hr?.let {
                    _ppgHeartRate.value = it
                    Log.d(TAG, "Atualizando BPM: $it")
                    saveToFile(raw)
                    // --- NOVO: cálculo do rating (porcentagem de tempo na zona alvo) ---
                    tempoTotal++
                    if (selectedZone > 0 && it in (zonas.getOrNull(selectedZone - 1)?.second ?: 0..0)) {
                        tempoNaZonaAlvo++
                    }
                    _desempenhoPct.value = if (tempoTotal > 0) (tempoNaZonaAlvo.toFloat() / tempoTotal) * 100f else 0f
                    Log.d(TAG, "Cálculo de desempenho: tempoTotal=$tempoTotal, tempoNaZonaAlvo=$tempoNaZonaAlvo, desempenhoPct=$desempenhoPct")
                    // --- FIM NOVO ---

                    Log.d(TAG, "Publicando BPM: $it (Usuário: $userId, Exercício: $exerciseId, Zona: $selectedZone, Rating: $desempenhoPct)")
                    MqttManager.publishSensorData(
                        groupId, userId, exerciseId, "ppg", it, selectedZone, zonas, _desempenhoPct.value
                    )
                }
            } else {
                Log.w(TAG, "Ignorando dados PPG de dispositivo incorreto (DeviceType: ${currentDeviceType})")
            }
        }        SENSOR_DATA1_UUID, SENSOR_DATA2_UUID, SENSOR_DATA3_UUID -> {
            if (currentDeviceType == DeviceType.THERMAL_CAMERA) {
                Log.d(TAG, "Recebendo dados de temperatura da câmera térmica: $raw")
                  // Verificar se é uma mensagem de erro
                if (raw.endsWith(".ERR")) {
                    Log.w(TAG, "Erro reportado pela câmera térmica")
                    // Definir valores de erro para todas as temperaturas
                    _avgTemp.value = null
                    _maxTemp.value = null
                    _minTemp.value = null
                    // Não publicamos no MQTT em caso de erro
                    return
                }
                
                // Processamento normal dos dados
                val temp = raw.substringAfter('.', "0").filter(Char::isDigit).toIntOrNull()?.div(100f)
                if (temp != null) {
                    val source = when (characteristic.uuid) {
                        SENSOR_DATA1_UUID -> { _avgTemp.value = temp; "avg_temp" }
                        SENSOR_DATA2_UUID -> { _maxTemp.value = temp; "max_temp" }
                        SENSOR_DATA3_UUID -> { _minTemp.value = temp; "min_temp" }
                        else -> "unknown"
                    }
                    saveToFile(raw)
                    MqttManager.publishSensorData(groupId, userId, exerciseId, source, temp, selectedZone, zonas)
                } else {
                    Log.e(TAG, "Erro ao parsear dados de temperatura: $raw")
                }
            } else {
                Log.d(TAG, "Ignorando dados de temperatura de dispositivo não câmera")
            }
        }
    }
}    
    @SuppressLint("MissingPermission")
    private fun writeNextConfig() {
        if (!_connected.value) {
            Log.e(TAG, "Tentativa de escrever config enquanto desconectado")
            _allConfigSent.value = false
            return
        }

        val g = gatt ?: run {
            Log.e(TAG, "GATT inválido ao tentar escrever config")
            _allConfigSent.value = false
            return
        }

        if (writeQueue.isEmpty()) {
            Log.d(TAG, "Todas as configurações foram enviadas com sucesso")
            _allConfigSent.value = true
            _configProgress.value = 1f
            return
        }

        val (uuid, data) = writeQueue.first()
        val characteristic = g.getService(CONFIG_SERVICE_UUID)?.getCharacteristic(uuid)

        if (characteristic == null) {
            Log.e(TAG, "Característica não encontrada para UUID: $uuid")
            _allConfigSent.value = false
            return
        }

        try {
            // Verificar permissão primeiro
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Permissão BLUETOOTH_CONNECT não concedida")
                _allConfigSent.value = false
                return
            }

            // Definir valor e tentar escrever
            characteristic.value = data
            val success = g.writeCharacteristic(characteristic)

            if (!success) {
                Log.e(TAG, "Falha ao iniciar escrita para característica: $uuid")
                _allConfigSent.value = false
                return
            }

            Log.d(TAG, """
                ===== Escrevendo configuração =====
                UUID: $uuid
                Tamanho dos dados: ${data.size} bytes
                Dados: ${String(data)}
                ================================
            """.trimIndent())

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao escrever característica: ${e.message}")
            _allConfigSent.value = false
        }
    }
    @SuppressLint("MissingPermission")
    fun sendAllConfigs(ssid: String, password: String, serverIp: String) {
        if (!_connected.value) {
            Log.e(TAG, "Tentativa de enviar configurações sem conexão BLE ativa")
            return
        }
        
        // Salvar configurações para uso futuro
        context.getSharedPreferences("wifi_config", Context.MODE_PRIVATE).edit().apply {
            putString("last_ssid", ssid)
            putString("last_pass", password)
            putString("last_server_ip", serverIp)
            apply()
        }

        val g = gatt
        if (g == null) {
            Log.e(TAG, "GATT não está disponível para enviar configurações")
            return
        }

        // Resetar progresso e estado
        _configProgress.value = 0f
        _allConfigSent.value = false

        Log.d(TAG, """
            ===== Iniciando envio de configurações WiFi =====
            SSID: $ssid
            Server IP: $serverIp
            Tipo de dispositivo: ${currentDeviceType}
            Estado da conexão: ${if (_connected.value) "Conectado" else "Desconectado"}
            ==============================================
        """.trimIndent())

        // Verificar o serviço de configuração
        val configService = g.getService(CONFIG_SERVICE_UUID)
        if (configService == null) {
            Log.e(TAG, "Serviço de configuração não encontrado!")
            return
        }

        // Verificar as características
        val ssidChar = configService.getCharacteristic(CONFIG_SSID_UUID)
        val passChar = configService.getCharacteristic(CONFIG_PASS_UUID)
        val ipChar = configService.getCharacteristic(CONFIG_SERVERIP_UUID)

        if (ssidChar == null || passChar == null || ipChar == null) {
            Log.e(TAG, "Características de configuração não encontradas!")
            return
        }

        // Set that we're expecting a disconnect after sending configs
        expectingDisconnect = true
        writeQueue.clear()

        // Adiciona configs na ordem correta
        writeQueue.addAll(listOf(
            CONFIG_SSID_UUID to ssid.toByteArray(),
            CONFIG_PASS_UUID to password.toByteArray(),
            CONFIG_SERVERIP_UUID to serverIp.toByteArray()
        ))

        Log.d(TAG, "Fila de configurações preparada com ${writeQueue.size} itens")

        // Iniciar envio imediatamente
        writeNextConfig()
    }
    
    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        super.onCharacteristicWrite(gatt, characteristic, status)
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "Configuração escrita com sucesso: ${characteristic.uuid}")
            if (writeQueue.isNotEmpty()) {
                writeQueue.removeFirst()
            }

            // Atualizar progresso
            val total = 3f // Total de configs esperadas
            val remaining = writeQueue.size
            _configProgress.value = ((total - remaining) / total)

            // Adicionar delay antes da próxima escrita
            Handler(Looper.getMainLooper()).postDelayed({
                if (writeQueue.isNotEmpty()) {
                    writeNextConfig()
                } else {
                    Log.d(TAG, "Todas as configurações enviadas. Aguardando desconexão...")
                    _allConfigSent.value = true
                    _configProgress.value = 1f
                }
            }, 200) // 200ms de delay entre escritas
        } else {
            Log.e(TAG, "Falha ao escrever característica: ${characteristic.uuid}, status: $status")
            _allConfigSent.value = false
        }
    }

    private fun saveToFile(data: String) {
        try {
            val path = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(path, "bpm_log.txt")
            FileWriter(file, true).use { it.append(data).append("\n")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao salvar no arquivo", e)
        }
    }
    @SuppressLint("MissingPermission")
    fun disconnect() {
    // Atualizar o provider antes de desconectar
    currentDeviceType?.let {
        BleManagerProvider.getInstance().unregisterConnectedDevice(it)
    }

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

    /**
     * Encontra uma característica BLE pelo UUID em todos os serviços disponíveis
     * @param uuid UUID da característica a ser encontrada
     * @return BluetoothGattCharacteristic encontrada ou null se não encontrada
     */
    private fun findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
        val g = gatt ?: return null

        // Procura em todos os serviços
        g.services?.forEach { service ->
            service.characteristics?.forEach { characteristic ->
                if (characteristic.uuid == uuid) {
                    return characteristic
                }
            }
        }

        return null
    }
}