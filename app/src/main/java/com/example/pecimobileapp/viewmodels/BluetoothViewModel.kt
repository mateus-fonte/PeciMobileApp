package com.example.pecimobileapp.viewmodels

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pecimobileapp.ble.BleManager
import com.example.pecimobileapp.models.BluetoothDeviceModel
import com.example.pecimobileapp.models.RaspberryPiStatus
import com.example.pecimobileapp.repositories.BluetoothRepository
import com.example.pecimobileapp.services.BluetoothResponseHandler
import com.example.pecimobileapp.services.BluetoothService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import android.bluetooth.le.ScanResult
/**
 * ViewModel for Bluetooth operations with robust error handling and diagnostics
 */
class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    // Crie ou injete uma instância do seu BleManager
    private val bleManager = BleManager(application)
    private val bluetoothRepository = BluetoothRepository(application)
    private val bluetoothService = BluetoothService(application)
    private val bluetoothResponseHandler = BluetoothResponseHandler(application)
    val bleScanResults: StateFlow<List<ScanResult>> = bleManager.scanResults
    private val _isBleConnected = MutableStateFlow(false)
    val isBleConnected: StateFlow<Boolean> = _isBleConnected
    // Adicione esta propriedade ao BluetoothViewModel
    private val _wifiResultAcknowledged = MutableStateFlow(false)
    val wifiResultAcknowledged: StateFlow<Boolean> = _wifiResultAcknowledged.asStateFlow()
    // UI State
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    // Accessible state flows from repository
    val pairedDevices = bluetoothRepository.pairedDevices
    val discoveredDevices = bluetoothRepository.discoveredDevices
    // WiFi credentials
    private val _ssid = MutableStateFlow("")
    val ssid: StateFlow<String> = _ssid.asStateFlow()
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()
    // Status messages
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()
    // Connection result
    private val _connectionResult = MutableStateFlow<ConnectionResult?>(null)
    val connectionResult: StateFlow<ConnectionResult?> = _connectionResult.asStateFlow()
    // WiFi configuration result
    private val _wifiResult = MutableStateFlow<WifiResult?>(null)
    val wifiResult: StateFlow<WifiResult?> = _wifiResult.asStateFlow()
    // Transfer state tracking
    private var _lastTransferTime = 0L
    private var _currentFileUri: Uri? = null
    private var _transferCount = 0
    private val _isTransferInProgress = MutableStateFlow(false)
    val isTransferInProgress: StateFlow<Boolean> = _isTransferInProgress.asStateFlow()

    // Estado para verificação de status
    private val _isCheckingStatus = MutableStateFlow(false)
    val isCheckingStatus: StateFlow<Boolean> = _isCheckingStatus.asStateFlow()

    // Referência para o WebSocketViewModel
    private var webSocketViewModel: WebSocketViewModel? = null

    /**
     * Define a referência para o WebSocketViewModel para permitir comunicação entre os ViewModels
     * Isso é crucial para gerenciar o dispositivo da câmera térmica durante reconexões
     */
    fun setWebSocketViewModel(viewModel: WebSocketViewModel) {
        this.webSocketViewModel = viewModel
    }

    /**
     * Registra o dispositivo da câmera térmica no WebSocketViewModel
     */
    private fun setThermalCameraDevice(device: BluetoothDevice) {
        webSocketViewModel?.setThermalCameraDevice(device)
    }

    // Bluetooth adapter direct access
    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter

    fun startBleScanForEsp32() {
        // Isso chama a função extraída do código ESP32 para iniciar o scan
        CoroutineScope(Dispatchers.IO).launch {
            bleManager.startScan()
        }
    }

    fun startBleScan() = viewModelScope.launch(Dispatchers.IO) {
        bleManager.startScan()
    }

    fun connectBleToDevice(device: BluetoothDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            bleManager.connectPpg(device)
            // depois disso você pode atualizar _isBleConnected = true
            _isBleConnected.value = true
        }
    }



    init {
        // Monitor scanning state from repository
        viewModelScope.launch {
            bluetoothRepository.isScanning.collect { isScanning ->
                _isScanning.value = isScanning
            }
        }
    }

    fun startScan() {
        bluetoothRepository.startDiscovery()
    }

    fun stopScan() {
        bluetoothRepository.stopDiscovery()
    }

    fun pairDevice(device: BluetoothDevice) {
        bluetoothRepository.pairDevice(device)
    }

    fun selectDevice(deviceModel: BluetoothDeviceModel) {
        deviceModel.address?.let { bluetoothRepository.selectDevice(it) }
    }

    fun connectToSelectedDevice() {
        val device = bluetoothRepository.getSelectedDevice()

        if (device == null) {
            _statusMessage.value = "No device selected"
            return
        }

        connectToDevice(device)
    }

    // Quando um novo dispositivo é conectado, atualizamos o dispositivo no ViewModel
    fun connectToDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            // First select the device
            device.address?.let { bluetoothRepository.selectDevice(it) }

            // Stop scanning during connection
            bluetoothRepository.stopDiscovery()

            // Get the device name
            val deviceName = bluetoothRepository.getDeviceName(device) ?: "Unknown Device"

            // Registra o dispositivo no ViewModel para reconexões automáticas
            if (deviceName.contains("THERMAL_CAM", ignoreCase = true)) {
                setThermalCameraDevice(device)
                Log.d(TAG, "Dispositivo da câmera térmica registrado no ViewModel: ${device.name}")
            }

            // Set connected state directly
            _isConnected.value = true
            _connectionResult.value = ConnectionResult.Success(deviceName)

            _statusMessage.value = "Selected device: $deviceName"

        }
    }

    fun disconnectDevice() {
        _isConnected.value = false
        bluetoothRepository.clearSelection()
        _connectionResult.value = null

        // Clean up any pending transfers
        cleanupTransferState()
    }

    fun updateSsid(newSsid: String) {
        _ssid.value = newSsid
    }

    fun updatePassword(newPassword: String) {
        _password.value = newPassword
    }

    /**
     * sendWifiCredentialsAsFile - Modified method for reliable file transfer
     * This more reliable implementation uses a simpler approach and better error handling
     */
    fun sendWifiCredentialsAsFile() {
        // Validate inputs
        if (_ssid.value.isEmpty() || _password.value.isEmpty()) {
            _statusMessage.value = "Please enter both SSID and password"
            return
        }

        // Check if a transfer is already in progress
        if (_isTransferInProgress.value || _isCheckingStatus.value) {
            _statusMessage.value = "A transfer is already in progress"
            return
        }

        viewModelScope.launch {
            // Get the selected device
            val selectedDevice = bluetoothRepository.getSelectedDevice()
            if (selectedDevice == null) {
                _statusMessage.value = "No device selected"
                return@launch
            }

            try {
                // Mark transfer as started
                _isTransferInProgress.value = true
                _lastTransferTime = System.currentTimeMillis()

                // Get device address
                val deviceAddress = bluetoothRepository.getDeviceAddress(selectedDevice)
                if (deviceAddress == null) {
                    _statusMessage.value = "Unable to get device address"
                    cleanupTransferState()
                    return@launch
                }

                // Get this device's unique identifier
                val deviceId = getDeviceIdentifier()

                // Reset Bluetooth state - this makes transfers more reliable
                bluetoothRepository.stopDiscovery()
                delay(500)

                // Create file with the current timestamp to avoid conflicts
                val context = getApplication<Application>().applicationContext
                val timestamp = System.currentTimeMillis()
                val filename = "wifi_config_${timestamp}.txt"

                // Delete any old files
                context.cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("wifi_config_")) {
                        file.delete()
                    }
                }

                // Create a new file with timestamp to ensure uniqueness
                val file = File(context.cacheDir, filename)
                file.writeText("${_ssid.value},${_password.value},${deviceId}")

                Log.d(TAG, "Created file at: ${file.absolutePath}")

                // Create URI
                val fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                _currentFileUri = fileUri

                // Set status first before starting intent
                _statusMessage.value = "Sending WiFi credentials..."
                _wifiResult.value = WifiResult.Pending("File transfer in progress...")

                // Create a simple share intent
                val intent = Intent(Intent.ACTION_SEND).apply {
                    setPackage("com.android.bluetooth")
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                }

                // Start the activity
                context.startActivity(intent)

                // Delay to allow the share dialog to appear
                delay(500)

                // Agora, em vez de apenas assumir o sucesso, verificamos o status
                viewModelScope.launch {
                    try {
                        // Aguarde um tempo para o arquivo ser transferido e processado
                        delay(2000)

                        // Verificar o status da conexão no Raspberry Pi
                        checkConnectionStatus()
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao verificar status de conexão", e)
                        _wifiResult.value = WifiResult.Error("Error: ${e.message}")
                        cleanupTransferState()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending file", e)
                _statusMessage.value = "Error: ${e.message}"
                _wifiResult.value = WifiResult.Error("File transfer failed: ${e.message}")
                cleanupTransferState()
            }
        }
    }

    /**
     * Verifica o status da conexão Wi-Fi no Raspberry Pi após enviar as credenciais
     */
    fun checkConnectionStatus() {
        val selectedDevice = bluetoothRepository.getSelectedDevice()

        if (selectedDevice == null) {
            _statusMessage.value = "Nenhum dispositivo selecionado"
            cleanupTransferState()
            return
        }

        viewModelScope.launch {
            _isCheckingStatus.value = true
            _statusMessage.value = "Verificando status de conexão..."

            // Define um timeout para evitar espera infinita
            val timeoutJob = viewModelScope.launch {
                delay(30000) // 30 segundos de timeout
                if (_isCheckingStatus.value) {
                    Log.d(TAG, "Timeout ao verificar status de conexão")
                    _isCheckingStatus.value = false

                    // Não limpar isTransferInProgress se não tivermos resposta
                    // Em vez disso, presumir que pode estar funcionando mas não recebemos feedback
                    _statusMessage.value = "Não foi possível obter o status de conexão. O Raspberry Pi pode estar processando as credenciais ou já estar conectado."

                    // Não definir como erro, apenas como pendente
                    if (_wifiResult.value !is WifiResult.Success) {
                        _wifiResult.value = WifiResult.Pending(
                            "O Raspberry Pi pode estar conectado, mas não foi possível confirmar. Verifique manualmente."
                        )
                    }

                    // Não chamar cleanupTransferState() aqui para permitir nova tentativa
                }
            }

            try {
                // Primeiramente, damos tempo para o Raspberry Pi processar as credenciais
                delay(5000)

                // Tentamos obter o status via Bluetooth ou HTTP
                val status = bluetoothResponseHandler.getConnectionStatus(selectedDevice)

                if (status != null) {

                    if (status.isConnected) {
                        // Sucesso na conexão
                        Log.d(TAG, "Conexão bem-sucedida! IP: ${status.ipAddress}")
                        _wifiResult.value = WifiResult.Success(
                            status.ipAddress ?: "IP não disponível",
                            "Raspberry Pi conectado com sucesso à rede ${status.connectedNetwork}"
                        )
                    } else {
                        // Falha na conexão
                        Log.d(TAG, "Falha na conexão WiFi")
                        _wifiResult.value = WifiResult.Error(
                            "Falha ao conectar à rede Wi-Fi. Verifique as credenciais."
                        )
                    }
                } else {
                    // Não conseguimos obter status
                    _statusMessage.value = "Não foi possível obter status de conexão. O dispositivo está processando as credenciais."

                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao verificar status de conexão", e)
                _statusMessage.value = "Erro ao verificar status: ${e.message}"
                _wifiResult.value = WifiResult.Error("Erro ao verificar status: ${e.message}")
            } finally {
                timeoutJob.cancel() // Cancela o timeout se a operação completou normalmente
                _isCheckingStatus.value = false
                _isTransferInProgress.value = false
                cleanupTransferState()
            }
        }
    }

    private fun cleanupTransferState() {
        _isTransferInProgress.value = false  // Redefine claramente o estado para false

        // Clear any URI permissions
        _currentFileUri?.let { uri ->
            try {
                getApplication<Application>().revokeUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error revoking URI permission", e)
            }
        }
        _currentFileUri = null
    }

    private fun monitorFileTransfer(device: BluetoothDevice) {
        viewModelScope.launch {
            try {
                // Wait for transfer to complete (we don't have a way to directly monitor it)
                delay(3000)

                // Update UI
                _wifiResult.value = WifiResult.Success(
                    "Transfer Complete",
                    "WiFi credentials sent to Raspberry Pi for processing."
                )

                // Clean up transfer state
                delay(1000)
                cleanupTransferState()
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring file transfer", e)
                _wifiResult.value = WifiResult.Error("Transfer monitoring error: ${e.message}")
                cleanupTransferState()
            }
        }
    }

    // Adicione esta função ao BluetoothViewModel.kt
    /**
     * Reconhece que o usuário viu o resultado da conexão WiFi
     * Fecha o diálogo mas mantém as informações de status
     */
    fun acknowledgeWifiResult() {
        // Marca o resultado como "reconhecido" para fechar o diálogo
        // mas mantém os dados de status para exibição na tela

        // Esta variável substitui a antiga clearWifiResult()
        // Em vez de definir como null, apenas define uma flag
        _wifiResultAcknowledged.value = true

        // Reset no próximo ciclo de UI
        viewModelScope.launch {
            delay(100)
            _wifiResultAcknowledged.value = false
        }

        // Atualiza o estado do diálogo para não ser exibido
        // mas sem perder as informações de status
        val currentResult = _wifiResult.value
        if (currentResult is WifiResult.Success) {
            // Armazenar os dados de sucesso para uso na interface
            val successResult = currentResult

            // Limpar o resultado para fechar o diálogo
            _wifiResult.value = null

        } else {
            // Para outros tipos de resultado, apenas limpar
            _wifiResult.value = null
        }
    }

    private fun checkBluetoothPermissions(): Boolean {
        val context = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getDeviceIdentifier(): String {
        val context = getApplication<Application>().applicationContext
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearConnectionResult() {
        _connectionResult.value = null
    }

    // Substitua essa função no BluetoothViewModel.kt
    fun clearWifiResult() {
        // Não limpa o resultado se for Success, apenas se for Erro ou Pending
        val currentResult = _wifiResult.value
        if (currentResult !is WifiResult.Success) {
            _wifiResult.value = null
        }
    }

    fun updatePairedDevices() {
        bluetoothRepository.updatePairedDevices()
    }

    fun getDeviceName(device: BluetoothDevice): String? {
        return bluetoothRepository.getDeviceName(device)
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothRepository.unregisterReceiver()
    }

    companion object {
        private const val TAG = "BluetoothViewModel"
    }

    sealed class ConnectionResult {
        data class Success(val deviceName: String) : ConnectionResult()
        data class Error(val message: String) : ConnectionResult()
        data class PairingRequired(val device: BluetoothDevice) : ConnectionResult()
    }

    sealed class WifiResult {
        data class Success(val ipAddress: String, val message: String) : WifiResult()
        data class Error(val message: String) : WifiResult()
        data class Pending(val message: String) : WifiResult()
    }
    // Expor a temperatura média da câmera térmica
    val thermalTemperature: StateFlow<Float?> get() = bleManager.avgTemp
    val bpm: StateFlow<Int?> get() = bleManager.ppgHeartRate

}