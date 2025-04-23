// BleConnectionSection.kt
package com.example.pecimobileapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.pecimobileapp.viewmodels.RealTimeViewModel

@Composable
fun BleConnectionSection(
    viewModel: RealTimeViewModel,
    onActivateBle: () -> Unit
) {
    val context = LocalContext.current
    // ➊ agora lê corretamente do ViewModel
    val scanResults by viewModel.scanResults.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

    val hasConnectPermission = remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)
    ) {
        if (!isConnected) {
            Button(
                onClick = onActivateBle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Escanear ESP32 (BLE)")
            }
            Spacer(modifier = Modifier.height(8.dp))
            scanResults.forEach { result ->
                val name = if (hasConnectPermission.value) {
                    result.device.name ?: "Sem nome"
                } else {
                    "Sem permissão"
                }
                val addr = if (hasConnectPermission.value) {
                    result.device.address
                } else {
                    "--:--:--"
                }

                Button(
                    onClick = { viewModel.connectToDevice(result.device) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text("$name • $addr")
                }
            }
        } else {
            Text(
                text = "ESP32 conectado!",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
