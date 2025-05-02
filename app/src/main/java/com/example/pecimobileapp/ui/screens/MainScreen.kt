package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pecimobileapp.viewmodels.RealTimeViewModel
import com.example.pecimobileapp.viewmodels.WebSocketViewModel

@Composable
fun MainScreen(
    viewModel: RealTimeViewModel,
    wsViewModel: WebSocketViewModel
) {
    // 1) Coleta dos valores
    val hr        by viewModel.ppgHeartRate.collectAsState()
    val isPpgConnected by viewModel.isPpgConnected.collectAsState()
    val useBle        by viewModel.isCamConnected.collectAsState()
    val useWs         by wsViewModel.isWsConnected.collectAsState()
    val avgTemp       by viewModel.avgTemp.collectAsState()
    val maxTemp       by viewModel.maxTemp.collectAsState()
    val minTemp       by viewModel.minTemp.collectAsState()
    val avgTempWs     by wsViewModel.avgThermalFlow.collectAsState()
    //val maxTempWs     by wsViewModel.maxThermalFlow.collectAsState()
    //val minTempWs     by wsViewModel.minThermalFlow.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Bom treino, ciclista!", style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(24.dp))

        // --- Card de Frequência Cardíaca ---
        if (isPpgConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Frequência Cardíaca", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = hr?.let { "$it BPM" } ?: "-- BPM",
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- Cards de Temperatura ---
        if (useBle) {
            // Média
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Temperatura Média", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = avgTemp?.let { "%.1f °C".format(it) } ?: "-- °C",
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Máxima
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Temperatura Máxima", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = maxTemp?.let { "%.1f °C".format(it) } ?: "-- °C",
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Mínima
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Temperatura Mínima", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = minTemp?.let { "%.1f °C".format(it) } ?: "-- °C",
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
            }
        } else if (useWs){
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Temperatura Média via WS", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = avgTempWs?.let { "%.1f °C".format(it) } ?: "-- °C",
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
            }
        }
    }
}
