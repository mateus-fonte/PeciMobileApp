package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pecimobileapp.viewmodels.BluetoothViewModel

@Composable
fun SetupScreen(viewModel: BluetoothViewModel) {
    val isConnected by viewModel.isConnected.collectAsState()
    val connectionResult by viewModel.connectionResult.collectAsState()

    // Utiliza um Card para dar destaque sem o fundo roxo
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                text = "Setup",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (isConnected) {
                connectionResult?.let { result ->
                    if (result is BluetoothViewModel.ConnectionResult.Success) {
                        WifiCredentialsScreen(viewModel, result.deviceName)
                    }
                }
            } else {
                DeviceListScreen(viewModel)
            }
        }
    }
}
