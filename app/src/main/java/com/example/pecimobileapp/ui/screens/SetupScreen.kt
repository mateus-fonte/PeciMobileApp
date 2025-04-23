package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pecimobileapp.viewmodels.RealTimeViewModel

@Composable
fun SetupScreen(realTimeModel: RealTimeViewModel) {
    Card(Modifier.fillMaxSize().padding(16.dp), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Conecte seu ESP32", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            BleConnectionSection(
                viewModel = realTimeModel,
                onActivateBle  = { realTimeModel.startBleScan() }
            )
            Spacer(Modifier.height(16.dp))
            // só mostre ConfigSection se já estiver conectado
            val connected by realTimeModel.isConnected.collectAsState()
            if (connected) {
                ConfigSection(realTimeModel)
            }
        }
    }
}
