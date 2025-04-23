package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.navigation.NavController
import kotlinx.coroutines.delay

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
    heartRate: Int?,
    temperature: Float?,
    execution: Float?,
    onStop: () -> Unit
) {
    val zoneColor = zoneColors[selectedZone] ?: NoZone
    var isRunning by remember { mutableStateOf(true) }
    var elapsedSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1000L)
            elapsedSeconds++
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
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
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
                Text(text = formattedTime, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Zona atual
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(zoneColor)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "ZONE $selectedZone WORKOUT", fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Temperatura e FC
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌡️")
                    Text("${temperature?.let { "%.1f°".format(it) } ?: "--.-°"}",
                        modifier = Modifier.background(zoneColor).padding(8.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("❤️")
                    Text("${heartRate?.let { "$it bpm" } ?: "--- bpm"}",
                        modifier = Modifier.background(zoneColor).padding(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Indicador de zona (barras)
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                for (i in 1..6) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .padding(vertical = 2.dp)
                            .background(if (selectedZone == i) zoneColors[i]!! else Color.LightGray)
                    ) {}
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Execução
            Text("Execução:", style = MaterialTheme.typography.titleMedium)
            Text("${execution?.let { "%.0f%%".format(it) } ?: "--- %"}",
                modifier = Modifier.background(zoneColor).padding(8.dp))

            Spacer(modifier = Modifier.height(16.dp))

            // Indicador pessoal
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Text("DESEMPENHO", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("VOCÊ")
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
        }
    }
}
