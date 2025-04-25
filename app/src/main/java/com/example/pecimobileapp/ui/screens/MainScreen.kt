// MainScreen.kt
package com.example.pecimobileapp.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.pecimobileapp.viewmodels.RealTimeViewModel

@Composable
fun MainScreen(
    realTimeModel: RealTimeViewModel
) {
    // ➊ coleta os fluxos do ViewModel
    val isConnected by realTimeModel.isConnected.collectAsState()
    val configSent  by realTimeModel.configSent.collectAsState()
    val heartRate   by realTimeModel.heartRate.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Bom treino, Ciclista!",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isConnected) {

                if (configSent) {
                    // ➋ exibe a frequência cardíaca
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Frequência Cardíaca",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = heartRate?.let { "$it BPM" } ?: "-- BPM",
                                style = MaterialTheme.typography.headlineLarge
                            )
                        }
                    }
                }
            } else {
                // Se não estiver conectado ainda
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Por favor, faça o setup dos sensores para iniciar o monitoramento.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("HardwareIds")
@Composable
fun ConfigSection(
    realTimeModel: RealTimeViewModel
) {
    // Estado de feedback de envio
    val configSent by realTimeModel.configSent.collectAsState()

    // Gera automaticamente
    val timestamp = remember { System.currentTimeMillis() }
    val mode = 2
    val id = remember { java.util.UUID.randomUUID().toString() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (!configSent) {
            // Mostra valores que a app envia (não editáveis)
            Text("Timestamp: $timestamp", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text("Mode: $mode",        style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text("ID: $id",            style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            // Botão para enviar tudo de uma vez
            Button(
                onClick = {
                    realTimeModel.sendTimeConfig(timestamp)
                    realTimeModel.sendModeConfig(mode)
                    realTimeModel.sendIdConfig(id)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enviar Configurações")
            }
        } else {
            // ✓ verde quando todas as writes retornarem sucesso
            Text(
                text = "✓ Configurações enviadas com sucesso!",
                color = Color(0xFF4CAF50),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
