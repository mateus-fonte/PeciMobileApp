package com.example.pecimobileapp.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.util.Log

@SuppressLint("MissingPermission")
@Composable
fun BleConnectionSection(
    title       : String,
    scanResults: List<ScanResult>,
    isConnected: Boolean,
    onScan: () -> Unit,
    onConnect: (BluetoothDevice) -> Unit,
    onAdvancedOptions: ((String, String, BluetoothDevice) -> Unit)? = null // Callback for advanced options
) {
    // Estado para controlar a exibição dos diálogos
    var showAdvancedOptionsDialog by remember { mutableStateOf(false) }
    var showWifiCredentialsDialog by remember { mutableStateOf(false) }
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    // Estado para armazenar o dispositivo selecionado para uso posterior
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    
    // Removemos o LaunchedEffect que mostrava o diálogo automaticamente

    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        if (!isConnected) {
            Button(onClick = onScan, Modifier.fillMaxWidth()) {
                Text("Escanear $title")
            }
            Spacer(Modifier.height(8.dp))
            scanResults.forEach { result ->
                Button(
                    onClick = { 
                        selectedDevice = result.device
                        onConnect(result.device) 
                    },
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(result.device.name ?: result.device.address)
                }
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
                
                // Só exibe o botão de opções avançadas para a câmera térmica
                if (title.contains("térmica", ignoreCase = true) && onAdvancedOptions != null) {
                    Button(
                        onClick = { 
                            Log.d("BleConnectionSection", "Botão de opções avançadas clicado")
                            showAdvancedOptionsDialog = true 
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Opções Avançadas")
                    }
                }
            }
        }
    }
    
    // Diálogo de opções avançadas
    if (showAdvancedOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showAdvancedOptionsDialog = false },
            title = { Text("Opções Avançadas") },
            text = { 
                Text("Deseja configurar a câmera térmica para usar o modo WebSocket? " +
                    "Isso requer que o Access Point do dispositivo esteja ativado.")
            },
            confirmButton = {
                Button(onClick = {
                    showAdvancedOptionsDialog = false
                    showWifiCredentialsDialog = true
                    Log.d("BleConnectionSection", "Usuário escolheu configurar opções avançadas da câmera térmica")
                }) {
                    Text("Configurar")
                }
            },
            dismissButton = {
                Button(onClick = { 
                    showAdvancedOptionsDialog = false
                    Log.d("BleConnectionSection", "Usuário optou por continuar sem configuração avançada")
                }) {
                    Text("Continuar no BLE")
                }
            }
        )
    }
    
    // Diálogo para inserir credenciais WiFi
    if (showWifiCredentialsDialog) {
        Dialog(onDismissRequest = { showWifiCredentialsDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Configuração WiFi",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        "Certifique-se que o Access Point (Hotspot) do seu dispositivo está ativado.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
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
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showWifiCredentialsDialog = false }
                        ) {
                            Text("Cancelar")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = {
                                selectedDevice?.let { device ->
                                    if (onAdvancedOptions != null && ssid.isNotEmpty() && password.isNotEmpty()) {
                                        Log.d("BleConnectionSection", "Enviando configurações WiFi: SSID=$ssid")
                                        onAdvancedOptions(ssid, password, device)
                                        showWifiCredentialsDialog = false
                                    } else {
                                        Log.d("BleConnectionSection", "SSID ou senha inválidos, ou callback não definido")
                                    }
                                }
                            }
                        ) {
                            Text("Configurar")
                        }
                    }
                }
            }
        }
    }
}
