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

@SuppressLint("MissingPermission")
@Composable
fun BleConnectionSection(
    title: String,
    scanResults: List<ScanResult>,
    isConnected: Boolean,
    onScan: () -> Unit,
    onConnect: (BluetoothDevice) -> Unit,
    onAdvancedOptions: ((String, String, BluetoothDevice) -> Unit)? = null, // Callback for advanced options
    allowedDeviceNames: List<String> = listOf("THERMAL_CAM", "sw"), // Lista de nomes de dispositivos permitidos
    buttonColor: Color = MaterialTheme.colorScheme.primary, // Cor personalizada para o botão
    buttonIcon: @Composable () -> Unit = {} // Ícone personalizado para o botão
) {
    // Estado para armazenar o dispositivo selecionado para uso posterior
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null)
    }
    
    // Estado para os campos de SSID e senha
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

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
                    // Adicionar o ícone personalizado apenas no botão de escaneamento
                    buttonIcon()
                    Spacer(Modifier.width(8.dp))
                    Text("Escanear $title")
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
                    // Remover o ícone aqui, mostrar apenas o nome do dispositivo
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
                // Remover o ícone da mensagem de status
                Text(
                    text = "$title conectado!",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                )
            }
            
            // Se for a câmera térmica e estiver conectada, exibir o card de configurações WiFi
            if (title.contains("térmica", ignoreCase = true) && isConnected && onAdvancedOptions != null) {
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
                                        Log.d("BleConnectionSection", "Enviando configurações WiFi: SSID=$ssid")
                                        onAdvancedOptions(ssid, password, device)
                                    } else {
                                        Log.d("BleConnectionSection", "SSID ou senha inválidos")
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
}
