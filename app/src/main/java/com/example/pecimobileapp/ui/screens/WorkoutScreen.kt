package com.example.pecimobileapp.ui.screens

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.pecimobileapp.network.MqttManagerImpl
import kotlinx.coroutines.delay
import org.json.JSONObject
import com.example.pecimobileapp.ui.ProfileViewModel
import com.example.pecimobileapp.viewmodels.*
import com.example.pecimobileapp.viewmodels.RealTimeViewModel

val zoneColors = mapOf(
    1 to Color(0xFF2196F3),
    2 to Color(0xFF4CAF50),
    3 to Color(0xFFFFEB3B),
    4 to Color(0xFFFF9800),
    5 to Color(0xFFF44336),
    6 to Color(0xFF9E9E9E)
)

interface MqttManager {
    fun publish(topic: String, message: String)
    fun subscribe(topic: String, callback: (String) -> Unit)
}

@Composable
fun WorkoutScreen(
    navController: NavController,
    groupName: String? = null,
    selectedZone: Int,
    onStop: () -> Unit,
    realTimeViewModel: RealTimeViewModel,
    isGroup: Boolean = false,
    mqttManager: MqttManagerImpl? = null
) {
    val context = LocalContext.current
    val factory = remember { ProfileViewModelFactory(context) }
    val profileViewModel: ProfileViewModel = viewModel(factory = factory)

    val identificador = profileViewModel.identificador

    val hr by realTimeViewModel.ppgHeartRate.collectAsState()
    val avgTemp by realTimeViewModel.avgTemp.collectAsState()
    val isPpgConnected by realTimeViewModel.isPpgConnected.collectAsState()
    val isCamConnected by realTimeViewModel.isCamConnected.collectAsState()

    val zoneColor = zoneColors[selectedZone] ?: zoneColors[6]!!

    var isRunning by remember { mutableStateOf(true) }
    var timeInZone by remember { mutableStateOf(0) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var executionPercentage by remember { mutableStateOf(0f) }
    var position by remember { mutableStateOf(-1) }

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

                if (isGroup && mqttManager != null) {
                    val json = JSONObject().apply {
                        put("timestamp", System.currentTimeMillis() / 1000)
                        put("id", identificador)
                        put("exec_pct", executionPercentage)
                        put("grupo", identificador) // grupo = identificador mesmo
                    }
                    mqttManager.publish("grupo/execucao", json.toString())
                }
            }
        }
    }

    if (isGroup && mqttManager != null) {
        LaunchedEffect(Unit) {
            mqttManager.subscribe("grupo/posicao") { message ->
                try {
                    val json = JSONObject(message)
                    if (json.getString("id") == identificador) {
                        position = json.getInt("position")
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    val formattedTime = String.format(
        "%02d:%02d:%02d",
        elapsedSeconds / 3600,
        (elapsedSeconds % 3600) / 60,
        elapsedSeconds % 60
    )

    val scrollState = rememberScrollState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
                Text(text = formattedTime, fontSize = 24.sp, fontWeight = FontWeight.Bold)
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

            Column(modifier = Modifier.fillMaxWidth()) {
                for (i in 5 downTo 1) {
                    val isLit = i <= currentZone
                    val color = if (isLit) zoneColors[i] ?: zoneColors[6]!! else zoneColors[6]!!

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .padding(vertical = 2.dp)
                            .background(color)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Pontuação de Execução 🎯", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${"%.0f".format(executionPercentage)}%",
                modifier = Modifier
                    .background(zoneColor)
                    .padding(8.dp)
            )

            // SOMENTE MOSTRAR SE FOR GRUPO
            if (isGroup) {
                Spacer(modifier = Modifier.height(16.dp))
                Column {
                    Text(
                        text = "👥 Grupo: ${groupName ?: "Desconhecido"}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (position > 0) {
                        Text(
                            text = "🏆 Estás em ${position}º lugar!",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "⏳ A aguardar resultados para o ranking...",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isPpgConnected) {
                CardInfo("💗 Frequência Cardíaca: ", hr?.let { "$it BPM" } ?: "-- BPM")
            }
            if (isCamConnected) {
                CardInfo("🌡️ Temperatura Média: ", avgTemp?.let { "%.1f°C".format(it) } ?: "--.-°C")
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
    val faixa = profileViewModel.zonas.getOrNull(selectedZone - 1)?.second
    return faixa?.contains(heartRate) == true
}
