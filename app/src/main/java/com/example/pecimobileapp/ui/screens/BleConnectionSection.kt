package com.example.pecimobileapp.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@SuppressLint("MissingPermission")
@Composable
fun BleConnectionSection(
    title       : String,
    scanResults: List<ScanResult>,
    isConnected: Boolean,
    onScan: () -> Unit,
    onConnect: (BluetoothDevice) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        if (!isConnected) {
            Button(onClick = onScan, Modifier.fillMaxWidth()) {
                Text("Escanear $title")
            }
            Spacer(Modifier.height(8.dp))
            scanResults.forEach { result ->
                Button(
                    onClick = { onConnect(result.device) },
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(result.device.name ?: result.device.address)
                }
            }
        } else {
            Text(
                text = "$title conectado!",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
