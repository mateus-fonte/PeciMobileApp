package com.example.pecimobileapp.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.util.Log
import com.example.pecimobileapp.ble.BleManager  // Adicionando importação necessária
import com.example.pecimobileapp.viewmodels.WebSocketViewModel

/**
 * Componente simples para conexão BLE, sem funcionalidades de configuração WiFi
 * Usado para dispositivos como PPG/Smartwatch que não precisam de configuração adicional
 */
@SuppressLint("MissingPermission")
@Composable
fun SimpleBleConnectionSection(
    title: String,
    scanResults: List<ScanResult>,
    isConnected: Boolean,
    onScan: () -> Unit,
    onConnect: (BluetoothDevice) -> Unit,
    allowedDeviceNames: List<String>, // Lista de nomes de dispositivos permitidos
    buttonColor: Color = MaterialTheme.colorScheme.primary, // Cor personalizada para o botão
    buttonIcon: @Composable () -> Unit = {} // Ícone personalizado para o botão
) {
    // Filtrar apenas dispositivos com nomes na lista permitida
    val filteredResults = scanResults.filter { result ->
        val deviceName = result.device.name ?: ""
        allowedDeviceNames.any { allowed -> deviceName.contains(allowed, ignoreCase = true) }
    }

    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        if (!isConnected) {
            Button(
                onClick = onScan, 
                Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = contentColorFor(buttonColor)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Adicionar o ícone personalizado para o botão de escaneamento
                    buttonIcon()
                    Spacer(Modifier.width(8.dp))
                    Text("Escanear $title")
                }
            }
            Spacer(Modifier.height(8.dp))
            filteredResults.forEach { result ->
                Button(
                    onClick = { onConnect(result.device) },
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor.copy(alpha = 0.8f)
                    )
                ) {
                    // Mostrar o nome do dispositivo
                    Text(result.device.name ?: result.device.address)
                }
            }
            
            // Mostrar mensagem se não houver dispositivos relevantes
            if (scanResults.isNotEmpty() && filteredResults.isEmpty()) {
                Text(
                    text = "Nenhum dispositivo relevante encontrado. Procurando por dispositivos com nomes: ${allowedDeviceNames.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$title conectado!",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                )
            }
        }
    }
}

/**
 * Componente avançado para conexão BLE com configuração WiFi para a câmera térmica
 * Inclui funcionalidades para verificar o Access Point e configurar o dispositivo
 */
@SuppressLint("MissingPermission")
@Composable
fun ThermalCameraBleSection(
    scanResults: List<ScanResult>,
    isConnected: Boolean,
    onScan: () -> Unit,
    onConnect: (BluetoothDevice) -> Unit,
    onAdvancedOptions: (String, String, BluetoothDevice) -> Unit,
    buttonColor: Color = MaterialTheme.colorScheme.primary,
    buttonIcon: @Composable () -> Unit = {},
    wsViewModel: WebSocketViewModel
) {
    // Estado para armazenar o dispositivo selecionado para uso posterior
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    
    // Estado para os campos de SSID e senha
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    // Estado para alerta do Access Point
    var showApAlert by remember { mutableStateOf(false) }
    var apAlertMessage by remember { mutableStateOf("") }
    
    // Estado para verificar se está em processo de configuração
    var isConfiguring by remember { mutableStateOf(false) }
    
    // Obtém o bleManager através do wsViewModel para acessar a conexão
    val bleManager = wsViewModel.getBleManager()
    
    // Observe a flag de conexão real do BleManager
    val isBleConnected by wsViewModel.isCameraConnected.collectAsState()
    
    // Efeito para registrar o dispositivo no ViewModel quando conectado pela primeira vez
    LaunchedEffect(selectedDevice) {
        if (selectedDevice != null) {
            wsViewModel.setThermalCameraDevice(selectedDevice!!)
            Log.d("ThermalCameraBleSection", "Dispositivo registrado no ViewModel: ${selectedDevice?.name}")
        }
    }
    
    // Efeito para atualizar o dispositivo selecionado quando a conexão BLE muda
    LaunchedEffect(isBleConnected) {
        if (isBleConnected) {
            // Tenta recuperar o dispositivo do ViewModel
            val deviceFromViewModel = wsViewModel.getThermalCameraDevice()
            if (deviceFromViewModel != null && (selectedDevice == null || selectedDevice?.address != deviceFromViewModel.address)) {
                Log.d("ThermalCameraBleSection", "Dispositivo recuperado após reconexão: ${deviceFromViewModel.name}")
                selectedDevice = deviceFromViewModel
            }
        }
    }
    
    // Verifica toda vez que isConnected mudar se temos um dispositivo válido
    LaunchedEffect(isConnected) {
        if (isConnected) {
            // Se estamos conectados e ainda não temos um dispositivo, tente recuperar do ViewModel
            if (selectedDevice == null) {
                val deviceFromViewModel = wsViewModel.getThermalCameraDevice()
                if (deviceFromViewModel != null) {
                    Log.d("ThermalCameraBleSection", "Dispositivo restaurado do ViewModel: ${deviceFromViewModel.name}")
                    selectedDevice = deviceFromViewModel
                }
            }
        } else {
            // Se desconectado, reseta o estado de configuração
            isConfiguring = false
        }
    }
    
    // Estado para controlar a animação de piscar no aviso do AP
    var shouldFlashWarning by remember { mutableStateOf(false) }
    
    // Definição da animação para o cartão de aviso
    val warningCardColor by animateColorAsState(
        targetValue = if (shouldFlashWarning) 
            MaterialTheme.colorScheme.errorContainer 
        else 
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        animationSpec = repeatable(
            iterations = 3,
            animation = tween(durationMillis = 300),
            repeatMode = RepeatMode.Reverse
        ),
        finishedListener = {
            // Resetar a animação após ela terminar
            shouldFlashWarning = false
        },
        label = "WarningColorAnimation"
    )
    
    // Animação para escala do cartão
    val warningCardScale by animateFloatAsState(
        targetValue = if (shouldFlashWarning) 1.05f else 1f,
        animationSpec = repeatable(
            iterations = 3,
            animation = tween(durationMillis = 300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WarningScaleAnimation"
    )

    // Filtrar apenas dispositivos THERMAL_CAM
    val filteredResults = scanResults.filter { result ->
        val deviceName = result.device.name ?: ""
        deviceName.contains("THERMAL_CAM", ignoreCase = true)
    }
    
    // Diálogo de alerta para o AP inativo
    if (showApAlert) {
        AlertDialog(
            onDismissRequest = { showApAlert = false },
            title = { Text("Atenção") },
            text = { Text(apAlertMessage) },
            confirmButton = {
                Button(onClick = { showApAlert = false }) {
                    Text("OK")
                }
            }
        )
    }

    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        if (!isConnected) {
            Button(
                onClick = onScan, 
                Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = contentColorFor(buttonColor)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    buttonIcon()
                    Spacer(Modifier.width(8.dp))
                    Text("Escanear Câmera Térmica")
                }
            }
            Spacer(Modifier.height(8.dp))
            filteredResults.forEach { result ->
                Button(
                    onClick = { 
                        selectedDevice = result.device
                        onConnect(result.device) 
                    },
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor.copy(alpha = 0.8f)
                    )
                ) {
                    Text(result.device.name ?: result.device.address)
                }
            }
            
            // Mostrar mensagem se não houver dispositivos relevantes
            if (scanResults.isNotEmpty() && filteredResults.isEmpty()) {
                Text(
                    text = "Nenhuma câmera térmica encontrada. Procurando por dispositivos com nome THERMAL_CAM. Verifique se o dispositivo encontra-se ligado ou tente fazer reset.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Texto removido conforme solicitado
            }
            
            // Configurações WiFi para a câmera térmica
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Título da seção com ícone
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Wifi,
                            contentDescription = "WiFi",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "Configurações WiFi",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    
                    // Destaque para o requisito do Access Point com animação
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = warningCardColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .graphicsLayer(scaleX = warningCardScale, scaleY = warningCardScale)
                    ) {
                        Text(
                            text = "⚠️ Importante: Certifique-se que o Access Point (Hotspot) do seu dispositivo está ativado.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp),
                            color = if (shouldFlashWarning) 
                                MaterialTheme.colorScheme.onErrorContainer 
                            else 
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    // Formulário para SSID e senha com descrições melhores
                    Text(
                        text = "Insira as informações do hotspot do seu celular:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    OutlinedTextField(
                        value = ssid,
                        onValueChange = { ssid = it },
                        label = { Text("Nome da rede (SSID)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Senha da rede") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            selectedDevice?.let { device -> 
                                if (ssid.isNotEmpty() && password.isNotEmpty()) {
                                    // Verificar o Access Point com o ViewModel
                                    val (canProceed, errorMessage) = wsViewModel.checkBeforeCameraConfig()
                                    
                                    if (canProceed) {
                                        // Se o AP está ativo, prosseguir com a configuração
                                        isConfiguring = true
                                        Log.d("ThermalCameraBleSection", "Enviando configurações WiFi: SSID=$ssid")
                                        onAdvancedOptions(ssid, password, device)
                                    } else {
                                        // Se o AP não está ativo, iniciar a animação de alerta
                                        Log.d("ThermalCameraBleSection", "Access Point não ativo: $errorMessage")
                                        shouldFlashWarning = true
                                        apAlertMessage = errorMessage
                                        showApAlert = true
                                    }
                                } else {
                                    Log.d("ThermalCameraBleSection", "SSID ou senha inválidos")
                                    apAlertMessage = "Por favor, preencha o SSID e senha do Access Point."
                                    showApAlert = true
                                }
                            } ?: run {
                                // Se selectedDevice for nulo, mostrar alerta
                                Log.e("ThermalCameraBleSection", "Dispositivo não selecionado!")
                                apAlertMessage = "Erro: Dispositivo não selecionado. Por favor, reconecte a câmera."
                                showApAlert = true
                            }
                        },
                        enabled = !isConfiguring && ssid.isNotEmpty() && password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isConfiguring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Configurar Câmera via WiFi")
                    }
                    
                    // Texto explicativo sobre o processo
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Ao clicar em \"Configurar Câmera via WiFi\", a câmera será configurada para se conectar ao hotspot do seu celular e enviar imagens por WiFi, permitindo a visualização em tempo real.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
