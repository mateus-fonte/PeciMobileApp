package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    onStop: () -> Unit,
    realTimeViewModel: RealTimeViewModel
) {
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

    val zonas = profileViewModel.zonas
    val currentZone = remember(hr) {
        hr?.let { bpm ->
            zonas.indexOfFirst { bpm in it.second }.let {
                when {
                    it == -1 && bpm < zonas.first().second.first -> 0
                    it == -1 && bpm > zonas.last().second.last -> 5
                    else -> it + 1
                }
            }
        } ?: 0
    }

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

    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(8.dp)
        ) {
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

            // Barras todas na cor da zona atual
            val currentColor = zoneColors[currentZone] ?: NoZone
            repeat(5) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .padding(vertical = 2.dp)
                        .background(currentColor)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Pontuação de Execução 🎯", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${"%.0f".format(executionPercentage)}%",
                modifier = Modifier
                    .background(zoneColor)
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isPpgConnected) {
                CardInfo(title = "💗 Frequência Cardíaca: ", value = hr?.let { "$it BPM" } ?: "-- BPM")
            }
            if (isCamConnected) {
                CardInfo(title = "🌡️ Temperatura Média: ", value = avgTemp?.let { "%.1f°C".format(it) } ?: "--.-°C")
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
