package com.example.pecimobileapp.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Dialog to prompt user to pair with a device
 */
@Composable
fun PairingDialog(
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Device Found") },
        text = { Text("Would you like to pair with $deviceName?") },
        confirmButton = {
            Button(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text("Yes")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("No")
            }
        }
    )
}

/**
 * Dialog to show WiFi configuration success
 */
@Composable
fun WifiSuccessDialog(
    ipAddress: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WiFi Configuration Success") },
        text = {
            Text("Your device has been successfully connected to WiFi.\n\nIP Address: $ipAddress\n\n$message")
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}