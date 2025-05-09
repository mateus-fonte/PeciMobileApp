package com.example.pecimobileapp.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.pecimobileapp.mqtt.MqttManager
import com.example.pecimobileapp.viewmodels.RealTimeViewModel
import kotlinx.coroutines.delay
import org.json.JSONObject

val zoneColors = mapOf(
    1 to Color(0xFF2196F3),
    2 to Color(0xFF4CAF50),
    3 to Color(0xFFFFEB3B),
    4 to Color(0xFFFF9800),
    5 to Color(0xFFF44336),
    6 to Color(0xFF9E9E9E)
)

@Composable
fun WorkoutScreen(
    navController: NavController,
    onStop: () -> Unit,
    realTimeViewModel: RealTimeViewModel,
    selectedZone: Int,
    groupId: String?,
    userId: String
) {
    // No need to get params from navController now, we receive them directly
    val isGroup = groupId != null
    val groupName = groupId
    
    LaunchedEffect(Unit) {
        Log.d("WorkoutScreen", "Setting workout parameters - selectedZone: $selectedZone, groupName: $groupName, userId: $userId")
        
        realTimeViewModel.setWorkoutParameters(
            zone = selectedZone,
            zonasList = realTimeViewModel.zonas.value,
            group = groupName,
            user = userId,
            exercise = "session-${System.currentTimeMillis()}"
        )
        realTimeViewModel.startActivity()
    }

    val hr by realTimeViewModel.ppgHeartRate.collectAsState()
    val avgTemp by realTimeViewModel.avgTemp.collectAsState()
    val isPpgConnected by realTimeViewModel.isPpgConnected.collectAsState()
    val isCamConnected by realTimeViewModel.isCamConnected.collectAsState()

    val zoneColor = zoneColors[selectedZone] ?: zoneColors[6]!!

    var isRunning by remember { mutableStateOf(true) }
    var executionPct by remember { mutableStateOf(0f) }

    val ratingsMap = remember { mutableStateMapOf<String, Float>() }
    var myPosition by remember { mutableStateOf<Int?>(null) }

    val scrollState = rememberScrollState()

    LaunchedEffect(groupName) {
        if (groupName != null) {
            try {
                MqttManager.subscribe("/group/$groupName/data") { rawMessage ->
                    try {
                        val jsonObj = JSONObject(rawMessage)
                        if (jsonObj.has("rating")) {
                            val uid = jsonObj.getString("user_uid")
                            val rating = jsonObj.getDouble("rating").toFloat()
                            ratingsMap[uid] = rating
    
                            val sorted = ratingsMap.entries
                                .sortedByDescending { it.value }
                                .map { it.key }
    
                            myPosition = sorted.indexOf(userId) + 1
                        }
                    } catch (e: Exception) {
                        Log.e("WorkoutScreen", "Error parsing JSON", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("WorkoutScreen", "Error subscribing to MQTT topic", e)
            }
        }
    }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            MqttManager.WorkoutSessionManager.resetSession()
            while (isRunning) {
                delay(1000L)
                executionPct = MqttManager.WorkoutSessionManager.getExecutionPercentage()
            }
        }
    }

    val ppgLost by realTimeViewModel.ppgConnectionLost.collectAsState()
    val camLost by realTimeViewModel.camConnectionLost.collectAsState()

    LaunchedEffect(ppgLost, camLost) {
        if (ppgLost) {
            Log.e("WorkoutScreen", "Conexão com o sensor de PPG perdida")
            isRunning = false
        }
        if (camLost) {
            Log.e("WorkoutScreen", "Conexão com a câmera térmica perdida")
            isRunning = false
        }
    }

    val elapsed = MqttManager.WorkoutSessionManager.getElapsedTime()
    val currentZone = MqttManager.WorkoutSessionManager.getLastZone()
    val formattedTime = String.format("%02d:%02d:%02d", elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60)

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(scrollState)
                .padding(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    IconButton(onClick = { isRunning = false }) {
                        Icon(Icons.Default.Pause, contentDescription = "Pausar")
                    }
                    IconButton(onClick = { isRunning = true }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Retomar")
                    }
                    IconButton(onClick = {
                        isRunning = false
                        onStop()
                    }) {
                        Icon(Icons.Default.Stop, contentDescription = "Parar")
                    }
                }
                Text(formattedTime, fontSize = 24.sp)
            }

            Spacer(Modifier.height(8.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .background(zoneColor)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("TREINO NA ZONA $selectedZone", color = Color.Black)
            }

            Spacer(Modifier.height(16.dp))

            Column(Modifier.fillMaxWidth()) {
                for (i in 5 downTo 1) {
                    val isLit = i <= currentZone
                    val color = if (isLit) zoneColors[i] ?: zoneColors[6]!! else zoneColors[6]!!
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .padding(vertical = 2.dp)
                            .background(color)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Pontuação de Execução 🎯", style = MaterialTheme.typography.titleMedium)
            Text(
                "${"%.0f".format(executionPct)}%",
                Modifier
                    .background(zoneColor)
                    .padding(8.dp)
            )

            if (groupName != null) {
                Spacer(Modifier.height(16.dp))
                Text("👥 Grupo: $groupName", style = MaterialTheme.typography.bodyLarge)
                myPosition?.let {
                    Text("🏆 Você está em ${it}º lugar", style = MaterialTheme.typography.headlineMedium)
                } ?: Text("⏳ Aguardando ranking...", color = Color.Gray)
            }

            Spacer(Modifier.height(16.dp))

            if (isPpgConnected) {
                CardInfo("💗 FC Instantânea:", hr?.let { "$it BPM" } ?: "-- BPM")
            }
            if (isCamConnected) {
                CardInfo("🌡️ Temperatura Média:", avgTemp?.let { "%.1f°C".format(it) } ?: "--.-°C")
            }
        }
    }
}

@Composable
fun CardInfo(title: String, value: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineLarge)
        }
    }
}