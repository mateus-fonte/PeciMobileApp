package com.example.pecimobileapp.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.util.Log
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
                    text = "Nenhuma câmera térmica encontrada. Procurando por dispositivos com nome THERMAL_CAM.",
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
                    text = "Câmera Térmica conectada!",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                )
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
                    Text(
                        text = "Configurações WiFi",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        "Para usar a câmera térmica via WiFi, certifique-se que o Access Point (Hotspot) do seu dispositivo está ativado.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    OutlinedTextField(
                        value = ssid,
                        onValueChange = { ssid = it },
                        label = { Text("SSID do Access Point") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Senha do Access Point") },
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
                                        Log.d("ThermalCameraBleSection", "Enviando configurações WiFi: SSID=$ssid")
                                        onAdvancedOptions(ssid, password, device)
                                    } else {
                                        // Mostrar alerta se o AP não estiver ativo
                                        Log.d("ThermalCameraBleSection", "Access Point não ativo: $errorMessage")
                                        apAlertMessage = errorMessage
                                        showApAlert = true
                                    }
                                } else {
                                    Log.d("ThermalCameraBleSection", "SSID ou senha inválidos")
                                    apAlertMessage = "Por favor, preencha o SSID e senha do Access Point."
                                    showApAlert = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Configurar Câmera")
                    }
                }
            }
        }
    }
}
