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
    val hr             by viewModel.ppgHeartRate.collectAsState()
    val isPpgConnected by viewModel.isPpgConnected.collectAsState()
    val ppgLost        by viewModel.ppgConnectionLost.collectAsState()
    val useBle         by viewModel.isCamConnected.collectAsState()
    val camLost        by viewModel.camConnectionLost.collectAsState()
    val useWs          by wsViewModel.isWsConnected.collectAsState() // Alterado para isWsConnected para verificar clientes conectados
    val avgTemp        by viewModel.avgTemp.collectAsState()
    val maxTemp        by viewModel.maxTemp.collectAsState()
    val minTemp        by viewModel.minTemp.collectAsState()
    // Coletar a temperatura facial como estado observável
    val facialTemp     by wsViewModel.processedImage.collectAsState()

    // Para exibir snackbars:
    val snackbarHostState = remember { SnackbarHostState() }

    // Quando ppgLost virar true, mostramos snackbar:
    LaunchedEffect(ppgLost) {
        if (ppgLost) {
            snackbarHostState.showSnackbar("Conexão com o sensor de PPG perdida.")
        }
    }
    // Quando camLost virar true, mostramos outro:
    LaunchedEffect(camLost) {
        if (camLost) {
            snackbarHostState.showSnackbar("Conexão com a câmera térmica perdida.")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
            when {
                useBle -> {
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
                }
                useWs -> {
                    // Obtendo a temperatura do maior rosto detectado (principal)
                    // Calculamos a temperatura facial com base na imagem processada atual
                    val faceData = facialTemp.second
                    
                    // Obter a temperatura do maior rosto na imagem, se houver
                    val faceTemp = if (faceData.isNotEmpty()) {
                        // Encontrar o rosto com a maior área (provavelmente o mais próximo)
                        val largestFace = faceData.maxByOrNull { it.width * it.height }
                        largestFace?.temperature
                    } else {
                        null
                    }
                    
                    // Se não tiver rosto na imagem atual, usar o método do ViewModel que armazena 
                    // a última temperatura válida
                    val displayTemp = faceTemp ?: wsViewModel.getLargestFaceTemperature()
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Temperatura do Rosto Principal", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (displayTemp > 0) "%.1f °C".format(displayTemp) else "-- °C",
                                style = MaterialTheme.typography.headlineLarge
                            )
                            
                            // Adicionar texto de status se não houver rostos detectados
                            if (faceData.isEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Nenhum rosto detectado na imagem atual",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

