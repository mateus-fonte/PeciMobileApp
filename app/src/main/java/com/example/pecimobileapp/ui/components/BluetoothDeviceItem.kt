package com.example.pecimobileapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pecimobileapp.models.BluetoothDeviceModel

/**
 * UI component for displaying a Bluetooth device
 */
@Composable
fun BluetoothDeviceItem(
    deviceModel: BluetoothDeviceModel,
    onPair: () -> Unit,
    onSelect: () -> Unit,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Device name
                    Text(
                        text = deviceModel.name ?: "Unknown Device",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )

                    // Device status
                    Text(
                        text = "Status: ${if (deviceModel.isPaired) "Paired" else "Not Paired"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (deviceModel.isPaired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )

                    // Show when device is selected
                    if (deviceModel.isSelected) {
                        Text(
                            text = "Selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row {
                if (!deviceModel.isPaired) {
                    // Show pair button only for unpaired devices
                    Button(
                        onClick = onPair,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Pair")
                    }

                    // Connect button (enabled only when selected)
                    Button(
                        onClick = onConnect,
                        enabled = deviceModel.isSelected
                    ) {
                        Text("Connect")
                    }
                } else {
                    // For paired devices, connect directly
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect")
                    }
                }
            }
        }
    }

    Divider()
}