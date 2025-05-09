package com.example.pecimobileapp.ui.screens

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun WorkoutScreen(
    navController: NavController,
    selectedZone: Int,
    groupId: String?,
    userId: String,
    exerciseId: String,
    realTimeViewModel: RealTimeViewModel,
    onStop: () -> Unit
) {
    val zonas by realTimeViewModel.zonas.collectAsState()
    val hr by realTimeViewModel.ppgHeartRate.collectAsState()
    val avgTemp by realTimeViewModel.avgTemp.collectAsState()
    val isPpgConnected by realTimeViewModel.isPpgConnected.collectAsState()
    val isCamConnected by realTimeViewModel.isCamConnected.collectAsState()
    val ppgLost by realTimeViewModel.ppgConnectionLost.collectAsState()
    val camLost by realTimeViewModel.camConnectionLost.collectAsState()

    val scrollState = rememberScrollState()
    val zoneColor = zoneColors[selectedZone] ?: zoneColors[6]!!

    var isRunning by remember { mutableStateOf(true) }
    var executionPct by remember { mutableStateOf(0f) }

    var elapsed by remember { mutableStateOf(0) }
    var accumulatedTime by remember { mutableStateOf(0L) }
    var startTime by remember { mutableStateOf<Long?>(System.currentTimeMillis()) }

    var showResetDialog by remember { mutableStateOf(false) }
    var showStopDialog by remember { mutableStateOf(false) }

    var myPosition by remember { mutableStateOf<Int?>(null) }
    val ratingsMap = remember { mutableStateMapOf<String, Float>() }

    // Início da sessão
    LaunchedEffect(Unit) {
        realTimeViewModel.setWorkoutParameters(
            zone = selectedZone,
            zonasList = zonas,
            group = groupId,
            user = userId,
            exercise = exerciseId
        )
        realTimeViewModel.startActivity()
        Log.d("WorkoutScreen", "Início do treino com ID: $exerciseId")
    }

    // Atualizador de tempo
    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1000L)
            val now = System.currentTimeMillis()
            val currentElapsed = ((now - (startTime ?: now)) / 1000).toInt()
            elapsed = (accumulatedTime / 1000).toInt() + currentElapsed
            executionPct = MqttManager.WorkoutSessionManager.getExecutionPercentage()
        }
    }

    // Verifica desconexão de sensores
    LaunchedEffect(ppgLost, camLost) {
        if (ppgLost || camLost) {
            isRunning = false
            Log.e("WorkoutScreen", "Sensor desconectado. PPG: $ppgLost, Cam: $camLost")
        }
    }

    // Subscrição MQTT para ranking
    LaunchedEffect(groupId) {
        groupId?.let { group ->
            MqttManager.subscribe("/group/$group/data") { rawMessage ->
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
                } catch (_: Exception) {}
            }
        }
    }

    val currentZone = MqttManager.WorkoutSessionManager.getLastZone()
    val formattedTime = String.format("%02d:%02d:%02d", elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60)

    // Botão físico de voltar (apenas pausa o treino)
    BackHandler {
        isRunning = false
        startTime?.let { accumulatedTime += System.currentTimeMillis() - it }
        startTime = null
    }

    // Encerra sessão se tela for destruída (fallback)
    DisposableEffect(Unit) {
        onDispose {
            isRunning = false
            realTimeViewModel.stopActivity()
            Log.d("WorkoutScreen", "Treino interrompido ao sair da tela")
        }
    }

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
                    IconButton(onClick = {
                        isRunning = false
                        startTime?.let { accumulatedTime += System.currentTimeMillis() - it }
                        startTime = null
                    }) {
                        Icon(Icons.Default.Pause, contentDescription = "Pausar")
                    }
                    IconButton(onClick = {
                        isRunning = true
                        startTime = System.currentTimeMillis()
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Retomar")
                    }
                    IconButton(onClick = { showStopDialog = true }) {
                        Icon(Icons.Default.Stop, contentDescription = "Parar")
                    }
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reiniciar")
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

            if (groupId != null) {
                Spacer(Modifier.height(16.dp))
                Text("👥 Grupo: $groupId", style = MaterialTheme.typography.bodyLarge)
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

        // Diálogo de reinício
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reiniciar Treino") },
                text = { Text("O treino atual será perdido. Deseja continuar?") },
                confirmButton = {
                    TextButton(onClick = {
                        isRunning = false
                        MqttManager.WorkoutSessionManager.resetSession()
                        navController.currentBackStackEntry?.savedStateHandle?.set("selectedZone", selectedZone)
                        navController.currentBackStackEntry?.savedStateHandle?.set("groupId", groupId)
                        navController.currentBackStackEntry?.savedStateHandle?.set("userId", userId)
                        navController.navigate("countdown")
                        showResetDialog = false
                    }) {
                        Text("Sim")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Diálogo de parada
        if (showStopDialog) {
            AlertDialog(
                onDismissRequest = { showStopDialog = false },
                title = { Text("Parar Treino") },
                text = { Text("O treino será interrompido e não poderá ser retomado. Deseja continuar?") },
                confirmButton = {
                    TextButton(onClick = {
                        isRunning = false
                        realTimeViewModel.stopActivity()
                        Log.d("WorkoutScreen", "Treino encerrado: $exerciseId")
                        navController.popBackStack("define_workout", inclusive = false)
                    }) {
                        Text("Sim")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStopDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
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
