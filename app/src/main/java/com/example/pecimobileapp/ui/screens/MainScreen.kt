// MainScreen.kt
package com.example.pecimobileapp.ui.screens

import android.annotation.SuppressLint
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pecimobileapp.viewmodels.RealTimeViewModel

@Composable
fun MainScreen(
    realTimeModel: RealTimeViewModel
) {
    // ➊ coleta os dados do BLE/ESP32
    val sensorData by realTimeModel.realTimeData.collectAsState()
    val isConnected by realTimeModel.isConnected.collectAsState()

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
                        Text("Frequência Cardíaca", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = sensorData?.heartRate?.let { "$it BPM" } ?: "-- BPM",
                            style = MaterialTheme.typography.headlineLarge
                        )
                    }
                }
                /**
                // ➌ exibe a temperatura média (se tiver)
                sensorData?.averageTemperature?.let { temp ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Temperatura Média", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("%.1f °C".format(temp), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                **/

                // ➍ seção de envio de configurações ao ESP32
                ConfigSection(realTimeModel)

            } else {
                // se não estiver conectado
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

            Spacer(modifier = Modifier.height(24.dp))

            // você pode adicionar aqui outros cards ou resumos
        }
    }
}

@SuppressLint("HardwareIds")
@Composable
fun ConfigSection(
    realTimeModel: RealTimeViewModel
) {
    val sent by realTimeModel.configSent.collectAsState()

    val context = LocalContext.current
    // ID único do dispositivo
    val deviceId = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    )
    if (!sent) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Button(
                onClick = {
                    // timestamp atual
                    val now = System.currentTimeMillis()
                    realTimeModel.sendTimeConfig(now)
                    // modo fixo = 2
                    realTimeModel.sendModeConfig(2)
                    // envia o ID do aparelho
                    realTimeModel.sendIdConfig(deviceId)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Enviar Config",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }else {
        Text("Configuração enviada com sucesso!", style = MaterialTheme.typography.bodyLarge)
    }
}
