package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.pecimobileapp.viewmodels.RealTimeViewModel

/**
 * Seção de configuração da REDE WI-FI da CÂMERA TÉRMICA.
 * Envia SSID e senha via BLE para que a câmera possa conectar via WebSocket.
 */
@Composable
fun ConfigSection(viewModel: RealTimeViewModel) {
    val apIp by viewModel.accessPointIp.collectAsState()
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        if (!sent) {
            Text("IP da Câmera Térmica (modo AP):", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                apIp.ifEmpty { "-- sem IP encontrado --" },
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it },
                label = { Text("SSID da Rede Wi-Fi da Câmera") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha da Rede Wi-Fi") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.sendAllConfigs(ssid, password)
                    sent = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enviar Configurações para a Câmera")
            }
        } else {
            Text(
                "✓ Configurações de rede da câmera enviadas com sucesso!",
                color = Color(0xFF4CAF50),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
