package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.pecimobileapp.ui.dialogs.PairingDialog
import com.example.pecimobileapp.ui.dialogs.WifiSuccessDialog
import com.example.pecimobileapp.viewmodels.BluetoothViewModel
import androidx.navigation.NavController

/**
 * Main screen of the application
 */
@Composable
fun MainScreen(navController: NavController, viewModel: BluetoothViewModel) {
    val context = LocalContext.current
    val isConnected by viewModel.isConnected.collectAsState()
    val connectionResult by viewModel.connectionResult.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val wifiResult by viewModel.wifiResult.collectAsState()

    // Handle status messages
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    // Handle connection result (for pairing dialog)
    connectionResult?.let { result ->
        when (result) {
            is BluetoothViewModel.ConnectionResult.PairingRequired -> {
                PairingDialog(
                    deviceName = viewModel.getDeviceName(result.device) ?: "Unknown Device",
                    onConfirm = {
                        viewModel.pairDevice(result.device)
                        // After pairing, attempt to connect
                        viewModel.connectToDevice(result.device)
                    },
                    onDismiss = { viewModel.clearConnectionResult() }
                )
            }
            is BluetoothViewModel.ConnectionResult.Error -> {
                LaunchedEffect(result) {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    viewModel.clearConnectionResult()
                }
            }
            // Success is handled by the UI state
            else -> {}
        }
    }

    // Handle WiFi configuration result
    wifiResult?.let { result ->
        when (result) {
            is BluetoothViewModel.WifiResult.Success -> {
                WifiSuccessDialog(
                    ipAddress = result.ipAddress,
                    message = result.message,
                    onDismiss = { viewModel.clearWifiResult() }
                )
            }
            // Error results are shown via status messages
            else -> {}
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Bluetooth WiFi Setup",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Botão para navegar até a tela do Grafana
            Button(onClick = { navController.navigate("GrafanaScreen") }) {
                Text("Abrir gráficos")
            }

            if (isConnected) {
                // If connected to a device, show WiFi credentials screen
                connectionResult?.let { result ->
                    if (result is BluetoothViewModel.ConnectionResult.Success) {
                        WifiCredentialsScreen(viewModel, result.deviceName)
                    }
                }
            } else {
                // If not connected, show device list
                DeviceListScreen(viewModel)
            }
        }
    }
}
