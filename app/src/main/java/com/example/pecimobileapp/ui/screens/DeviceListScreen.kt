package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pecimobileapp.models.BluetoothDeviceModel
import com.example.pecimobileapp.ui.components.BluetoothDeviceItem
import com.example.pecimobileapp.viewmodels.BluetoothViewModel

/**
 * Screen for displaying the list of Bluetooth devices
 */
@Composable
fun DeviceListScreen(viewModel: BluetoothViewModel) {
    val isScanning by viewModel.isScanning.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Scan controls
        ScanControls(
            isScanning = isScanning,
            onStartScan = { viewModel.startScan() },
            onStopScan = { viewModel.stopScan() },
            onAutoDetect = { viewModel.autoDetectRaspberryPi() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Device list header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Available Devices",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Count of devices
            val totalDevices = pairedDevices.size + discoveredDevices.size
            if (totalDevices > 0) {
                Text(
                    text = "$totalDevices found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Device list
        if (pairedDevices.isEmpty() && discoveredDevices.isEmpty()) {
            Text(
                text = if (isScanning) "Scanning for devices..." else "Start scanning to discover devices",
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                // Header for paired devices
                if (pairedDevices.isNotEmpty()) {
                    item {
                        Text(
                            text = "Paired Devices",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    // List of paired devices
                    items(pairedDevices) { deviceModel ->
                        BluetoothDeviceItem(
                            deviceModel = deviceModel,
                            onPair = { /* Already paired */ },
                            onSelect = { viewModel.selectDevice(deviceModel) },
                            onConnect = {
                                // Make sure we select the device before connecting
                                viewModel.selectDevice(deviceModel)
                                viewModel.connectToDevice(deviceModel.device)
                            }
                        )
                    }
                }

                // Header for discovered devices
                val unpaired = discoveredDevices.filter { deviceModel ->
                    !pairedDevices.any { it.address == deviceModel.address }
                }

                if (unpaired.isNotEmpty()) {
                    item {
                        Text(
                            text = "Discovered Devices",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }

                    // List of discovered unpaired devices
                    items(unpaired) { deviceModel ->
                        BluetoothDeviceItem(
                            deviceModel = deviceModel,
                            onPair = { viewModel.pairDevice(deviceModel.device) },
                            onSelect = { viewModel.selectDevice(deviceModel) },
                            onConnect = {
                                // Make sure we select the device before connecting
                                viewModel.selectDevice(deviceModel)
                                viewModel.connectToDevice(deviceModel.device)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScanControls(
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onAutoDetect: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (isScanning) onStopScan() else onStartScan()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    // Um tom levemente transparente para se destacar no fundo escuro do gradiente
                    containerColor = Color.White.copy(alpha = 0.25f)
                )
            ) {
                Text(
                    text = if (isScanning) "Stop Scan" else "Scan for Devices",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
                )
            }
            if (isScanning) {
                Spacer(modifier = Modifier.width(16.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onAutoDetect,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text(
                text = "Auto-Detect Device",
                style = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
            )
        }
    }
}