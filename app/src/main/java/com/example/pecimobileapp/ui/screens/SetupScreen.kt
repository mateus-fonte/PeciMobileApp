package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.pecimobileapp.viewmodels.RealTimeViewModel

@Composable
fun SetupScreen(
    realTimeModel: RealTimeViewModel,
    navController: NavController
) {
    val sensorData by realTimeModel.realTimeData.collectAsState()
    // val isHeartRateAvailable = sensorData?.heartRate != null

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                text = "Setup ESP32",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(16.dp))

            BleConnectionSection(
                viewModel = realTimeModel,
                onActivateBle = { realTimeModel.startBleScan() }
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { navController.navigate("define_workout") },
                // enabled = isHeartRateAvailable, // <-- Comentado
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Iniciar Atividade Física")
            }

            /*
            if (!isHeartRateAvailable) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Conecte-se ao sensor para iniciar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            */
        }
    }
}
