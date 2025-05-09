package com.example.pecimobileapp.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.pecimobileapp.mqtt.MqttManager
import com.example.pecimobileapp.viewmodels.RealTimeViewModel
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

@Composable
fun PulsatingHeart(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Icon(
        Icons.Default.Favorite,
        contentDescription = "Heart",
        tint = Color.Red,
        modifier = modifier.size(48.dp).scale(scale)
    )
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun WorkoutScreen(
    navController: NavController,
    selectedZone: Int,
    isGroup: Boolean,
    mqttManager: MqttManager?,
    groupName: String?,
    onStop: () -> Unit,
    realTimeViewModel: RealTimeViewModel
) {
    val zonasState by realTimeViewModel.zonas.collectAsState()
    val userId by realTimeViewModel.userId.collectAsState()
    val scrollState = rememberScrollState()

    val hr by realTimeViewModel.ppgHeartRate.collectAsState()
    val avgTemp by realTimeViewModel.avgTemp.collectAsState()
    val isCamConnected by realTimeViewModel.isCamConnected.collectAsState()
    val isPpgConnected by realTimeViewModel.isPpgConnected.collectAsState()

    val zoneColor = zoneColors[selectedZone] ?: zoneColors[0]!!
    val currentZone = MqttManager.WorkoutSessionManager.getLastZone()
    val elapsed = MqttManager.WorkoutSessionManager.getElapsedTime()
    val formattedTime = String.format("%02d:%02d:%02d", elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60)

    var isRunning by remember { mutableStateOf(true) }
    var executionPct by remember { mutableStateOf(0f) }
    val ratingsMap = remember { mutableStateMapOf<String, Float>() }
    var myPosition by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(selectedZone, zonasState, groupName, userId) {
        if (zonasState.isNotEmpty() && userId.isNotEmpty()) {
            realTimeViewModel.setWorkoutParameters(
                zone = selectedZone,
                zonasList = zonasState,
                group = groupName,
                user = userId,
                exercise = "session-${System.currentTimeMillis()}"
            )
        }
    }

    LaunchedEffect(groupName) {
        if (groupName != null && mqttManager != null) {
            mqttManager.subscribe("/group/$groupName/data") { raw ->
                try {
                    val json = JSONObject(raw)
                    if (json.has("rating")) {
                        val uid = json.getString("user_uid")
                        val rating = json.getDouble("rating").toFloat()
                        ratingsMap[uid] = rating
                        val sorted = ratingsMap.entries.sortedByDescending { it.value }.map { it.key }
                        myPosition = sorted.indexOf(userId) + 1
                    }
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(isRunning) {
        MqttManager.WorkoutSessionManager.resetSession()
        while (isRunning) {
            delay(1000L)
            executionPct = MqttManager.WorkoutSessionManager.getExecutionPercentage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { isRunning = false }) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                }
                IconButton(onClick = { isRunning = true }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
                IconButton(onClick = {
                    isRunning = false
                    onStop()
                }) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                }
            }
            Text(formattedTime, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Row(
            Modifier.fillMaxWidth().height(52.dp)
        ) {
            (0..5).forEach { i ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(zoneColors[i]!!),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Z$i",
                        fontSize = 16.sp,
                        fontWeight = if (i == selectedZone) FontWeight.ExtraBold else FontWeight.Bold,
                        color = Color.Black,
                        modifier = if (i == selectedZone)
                            Modifier.background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        else Modifier
                    )
                }
            }
        }

        Box(
            Modifier.fillMaxWidth().height(160.dp).background(zoneColors[currentZone] ?: Color.Gray),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                PulsatingHeart()
                Spacer(Modifier.width(28.dp))
                Text(
                    text = "${hr ?: "--"}",
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.weight(1f))
                Text("bpm", fontSize = 24.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 18.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(
                text = "Tu 🎯",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.padding(end = 8.dp)
            )

            Slider(
                value = executionPct.coerceIn(0f, 100f),
                onValueChange = {},
                enabled = false,
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = zoneColor,
                    inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                ),
                modifier = Modifier.weight(1f)
            )
        }

        if (groupName != null) {
            Spacer(Modifier.height(16.dp))
            Text("👥 Grupo: $groupName", color = Color.White)
            myPosition?.let {
                Text("🏆 Você está em ${it}º lugar", fontSize = 20.sp, color = Color.White)
            } ?: Text("⏳ Aguardando ranking...", color = Color.Gray)
        }

        if (isPpgConnected) {
            CardInfo("💗 FC Instantânea:", hr?.let { "$it BPM" } ?: "-- BPM")
        }
        if (isCamConnected) {
            CardInfo("🌡️ Temperatura Média:", avgTemp?.let { "%.1f°C".format(it) } ?: "--.-°C")
        }
    }
}

@Composable
fun CardInfo(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineLarge)
        }
    }
}