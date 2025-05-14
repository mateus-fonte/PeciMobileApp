package com.example.pecimobileapp.ui.screens

import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.example.pecimobileapp.mqtt.MqttManager
import com.example.pecimobileapp.viewmodels.RealTimeViewModel
import com.example.pecimobileapp.viewmodels.WebSocketViewModel
import kotlinx.coroutines.delay
import org.json.JSONObject

val zoneColors = mapOf(
    0 to Color(0xFFBDBDBD),
    1 to Color(0xFF5E9ED6),
    2 to Color(0xFF8AC7AB),
    3 to Color(0xFFFFD54F),
    4 to Color(0xFFFFB74D),
    5 to Color(0xFFE53935)
)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun WorkoutScreen(
    navController: NavController,
    selectedZone: Int,
    mqttManager: MqttManager?,
    groupId: String?,
    realTimeViewModel: RealTimeViewModel,
    wsViewModel: WebSocketViewModel,
    userId: String,
    exerciseId: String,
    onStop: () -> Unit
) {
    val hr by realTimeViewModel.ppgHeartRate.collectAsState()
    val avgTemp by realTimeViewModel.avgTemp.collectAsState()
    val isCamConnected by realTimeViewModel.isCamConnected.collectAsState()
    val isPpgConnected by realTimeViewModel.isPpgConnected.collectAsState()
    val zonas by realTimeViewModel.zonas.collectAsState()
    val zonaAtual by realTimeViewModel.currentZone.collectAsState()

    var isRunning by remember { mutableStateOf(true) }
    var showStopDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    var tempoTotal by remember { mutableStateOf(0) }
    var lastUpdateTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Início do treino: configura sessão e ativa envios no BLE
    LaunchedEffect(Unit) {
        realTimeViewModel.setWorkoutParameters(
            zone = selectedZone,
            zonasList = zonas,
            group = groupId,
            user = userId,
            exercise = exerciseId
        )
        realTimeViewModel.startActivity()
        realTimeViewModel.bleManager?.startActivity() // ✅ Ativa envio no BLE
        Log.d("WorkoutScreen", "Treino iniciado com ID: $exerciseId")
    }

    // Envia PPG via MQTT a cada segundo
    LaunchedEffect(hr, isPpgConnected, isCamConnected) {
        if (isPpgConnected && isCamConnected && hr != null && zonas.isNotEmpty()) {
            val now = System.currentTimeMillis()
            if (zonaAtual in 1..6 && now - lastUpdateTime >= 1000) {
                tempoTotal++
                lastUpdateTime = now
                mqttManager?.publishSensorData(
                    groupId = groupId ?: "defaultGroup",
                    userId = userId,
                    exerciseId = exerciseId,
                    source = "ppg",
                    value = hr!!,
                    selectedZone = selectedZone,
                    zonas = zonas
                )
            }
        }
    }

    // Envia temperatura média (sem repetição contínua)
    LaunchedEffect(avgTemp, isCamConnected) {
        if (isCamConnected && avgTemp != null) {
            mqttManager?.publishSensorData(
                groupId = groupId ?: "defaultGroup",
                userId = userId,
                exerciseId = exerciseId,
                source = "temp",
                value = avgTemp!!,
                selectedZone = selectedZone,
                zonas = zonas
            )
        }
    }

    // Assina tópico do grupo (sem crash)
    LaunchedEffect(groupId) {
        if (groupId != null && mqttManager != null) {
            mqttManager.subscribe("/group/$groupId/data") { raw ->
                try {
                    val json = JSONObject(raw)
                    val uid = json.optString("user_uid", "")
                    val rating = json.optDouble("rating", -1.0)
                    if (uid.isNotBlank() && rating >= 0) {
                        Log.d("WorkoutScreen", "Rating recebido: $uid -> $rating")
                    }
                } catch (e: Exception) {
                    Log.e("WorkoutScreen", "Erro ao processar MQTT", e)
                }
            }
        }
    }

    // Ao sair da tela
    DisposableEffect(Unit) {
        onDispose {
            isRunning = false
            realTimeViewModel.stopActivity()
            realTimeViewModel.bleManager?.stopActivity() // ✅ Bloqueia envio no BLE
            Log.d("WorkoutScreen", "Treino encerrado ao sair")
        }
    }

    // UI mínima
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Treino em andamento...", color = Color.White)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { showStopDialog = true }) {
            Text("Parar Treino")
        }
    }

    // Diálogo para parar o treino
    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("Parar Treino") },
            text = { Text("Deseja parar o treino?") },
            confirmButton = {
                TextButton(onClick = {
                    isRunning = false
                    realTimeViewModel.stopActivity()
                    realTimeViewModel.bleManager?.stopActivity() // ✅ Bloqueia envio no BLE
                    navController.popBackStack("define_workout", inclusive = false)
                }) { Text("Sim") }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Diálogo para resetar o treino (caso use)
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reiniciar Treino") },
            text = { Text("Deseja reiniciar o treino atual?") },
            confirmButton = {
                TextButton(onClick = {
                    isRunning = false
                    MqttManager.WorkoutSessionManager.resetSession()
                    navController.navigate("countdown") {
                        popUpTo("workout") { inclusive = true }
                    }
                }) { Text("Sim") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancelar") }
            }
        )
    }
}