package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pecimobileapp.viewmodels.RealTimeViewModel

@Composable
fun SetupScreen(
    realTimeModel: RealTimeViewModel,
) {
    Card(
        Modifier.fillMaxSize().padding(16.dp),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Setup ESP32", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            BleConnectionSection(
                viewModel = realTimeModel,
                onActivateBle = { realTimeModel.startBleScan() }
            )

            Spacer(Modifier.height(16.dp))
            // aqui você pode exibir outras configurações (Wi‑Fi, etc)
        }
    }
}
