package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import com.example.pecimobileapp.viewmodels.ProfileViewModel
import com.example.pecimobileapp.viewmodels.RealTimeViewModel

// Cores das zonas
val AerobicEndurance = Color(0xFF2196F3)
val AerobicPower = Color(0xFF4CAF50)
val Threshold = Color(0xFFFFEB3B)
val AnaerobicEndurance = Color(0xFFFF9800)
val AnaerobicPower = Color(0xFFF44336)
val NoZone = Color(0xFF9E9E9E)

val zoneColors = mapOf(
    1 to AerobicEndurance,
    2 to AerobicPower,
    3 to Threshold,
    4 to AnaerobicEndurance,
    5 to AnaerobicPower,
    6 to NoZone
)

@Composable
fun WorkoutScreen(
    navController: NavController,
    selectedZone: Int,
    nickname: String,
    onStop: () -> Unit
) {
    val realTimeViewModel: RealTimeViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()

    val hr by realTimeViewModel.ppgHeartRate.collectAsState()
    val avgTemp by realTimeViewModel.avgTemp.collectAsState()
    val isPpgConnected by realTimeViewModel.isPpgConnected.collectAsState()
    val isCamConnected by realTimeViewModel.isCamConnected.collectAsState()

    val zoneColor = zoneColors[selectedZone] ?: NoZone

    var isRunning by remember { mutableStateOf(true) }
    var timeInZone by remember { mutableStateOf(0) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var executionPercentage by remember { mutableStateOf(0f) }

    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1000L)
            elapsedSeconds++

            if (hr != null && isHeartRateInZone(hr!!, selectedZone, profileViewModel)) {
                timeInZone++
            }

            if (elapsedSeconds % 60 == 0) {
                executionPercentage = (timeInZone.toFloat() / elapsedSeconds * 100)
            }
        }
    }

    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    val formattedTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Controles superiores
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isRunning = false }) {
                        Icon(Icons.Default.Pause, contentDescription = "Pausar")
                    }
                    IconButton(onClick = { isRunning = true }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Retomar")
                    }
                    IconButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, contentDescription = "Parar")
                    }
                }
                Text(
                    text = formattedTime,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Zona Atual
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(zoneColor)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "TREINO NA ZONA $selectedZone",
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Temperatura e Frequência Cardíaca
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricCard(label = "🌡️", value = avgTemp?.let { "%.1f°".format(it) } ?: "--.-°", color = zoneColor)
                MetricCard(label = "❤️", value = hr?.let { "$it bpm" } ?: "--- bpm", color = zoneColor)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Indicador de Zona
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                for (i in 1..5) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .padding(vertical = 2.dp)
                            .background(if (selectedZone == i) zoneColors[i]!! else Color.LightGray)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Execução 🎯
            Text("Pontuação de Execução 🎯", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${"%.0f".format(executionPercentage)}%",
                modifier = Modifier.background(zoneColor).padding(8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Desempenho Individual
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Text("DESEMPENHO", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TU")
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(zoneColor, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dados ao Vivo (opcional)
            if (isPpgConnected) {
                CardInfo(title = "Frequência Cardíaca", value = hr?.let { "$it BPM" } ?: "-- BPM")
            }
            if (isCamConnected) {
                CardInfo(title = "Temperatura Média", value = avgTemp?.let { "%.1f°C".format(it) } ?: "--.-°C")
            }
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label)
        Text(
            value,
            modifier = Modifier
                .background(color)
                .padding(8.dp)
        )
    }
}

@Composable
fun CardInfo(title: String, value: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineLarge)
        }
    }
}

fun isHeartRateInZone(heartRate: Int, selectedZone: Int, profileViewModel: ProfileViewModel): Boolean {
    val zonas = profileViewModel.zonas
    val faixa = zonas.getOrNull(selectedZone - 1)?.second
    return faixa?.contains(heartRate) == true
}
