package com.example.pecimobileapp.ui.screens

import android.os.Build
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
import androidx.compose.material.icons.outlined.*
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
        modifier = modifier.size(30.dp).scale(scale)
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
    realTimeViewModel: RealTimeViewModel,
    wsViewModel: WebSocketViewModel
) {
    val zonasState by realTimeViewModel.zonas.collectAsState()
    val userId by realTimeViewModel.userId.collectAsState()
    val scrollState = rememberScrollState()

    val hr by realTimeViewModel.ppgHeartRate.collectAsState()
    val avgTemp by realTimeViewModel.avgTemp.collectAsState()
    val isCamConnected by realTimeViewModel.isCamConnected.collectAsState()
    val isPpgConnected by realTimeViewModel.isPpgConnected.collectAsState()
    val isWsConnected by wsViewModel.isWsConnected.collectAsState()
    val imageReceived by wsViewModel.imageReceived.collectAsState()
    var showThermalPreview by remember { mutableStateOf(false) }

    val zonaAlvo = selectedZone
    val tempoPorZona = remember { mutableStateListOf(0, 0, 0, 0, 0, 0) }
    var tempoTotal by remember { mutableStateOf(0) }
    var zonaAtual by remember { mutableStateOf(0) }
    var desempenhoPct by remember { mutableStateOf(0f) }
    var lastUpdateTime by remember { mutableStateOf(System.currentTimeMillis()) }

    fun calculateZoneFromRanges(heartRate: Int?, zonas: List<Pair<String, IntRange>>): Int {
        if (heartRate == null || heartRate <= 0) return 0
        val idx = zonas.indexOfFirst { heartRate in it.second }
        return when {
            idx >= 0 -> idx + 1
            zonas.isNotEmpty() && heartRate < zonas.first().second.first -> 0
            zonas.isNotEmpty() && heartRate > zonas.last().second.last -> zonas.size + 1
            else -> 0
        }
    }

    LaunchedEffect(hr, isPpgConnected, isCamConnected) {
        if (isPpgConnected && isCamConnected && hr != null && zonasState.isNotEmpty()) {
            val novaZona = calculateZoneFromRanges(hr, zonasState)
            zonaAtual = novaZona
            val now = System.currentTimeMillis()
            if (now - lastUpdateTime >= 1000) {
                tempoTotal++
                if (novaZona in 1..6) {
                    tempoPorZona[novaZona - 1] = tempoPorZona[novaZona - 1] + 1
                }
                lastUpdateTime = now
                if (tempoTotal % 10 == 0) {
                    val tempoNaZonaAlvo = if (zonaAlvo in 1..6) tempoPorZona[zonaAlvo - 1] else 0
                    desempenhoPct = if (tempoTotal > 0) (tempoNaZonaAlvo.toFloat() / tempoTotal) * 100f else 0f
                }
            }
        }
    }

    val zoneColor = zoneColors[zonaAlvo] ?: zoneColors[0]!!
    val currentZoneColor = zoneColors.getOrElse(zonaAtual) { Color.Gray }
    val formattedTime = String.format("%02d:%02d:%02d", tempoTotal / 3600, (tempoTotal % 3600) / 60, tempoTotal % 60)

    var isRunning by remember { mutableStateOf(true) }
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

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().height(52.dp)) {
                (0..5).forEach { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(zoneColors[i] ?: Color.Gray),
                        contentAlignment = Alignment.Center
                    ) {
                        if (i == zonaAlvo) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Zona Alvo",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(currentZoneColor),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PulsatingHeart()
                    Spacer(Modifier.width(28.dp))
                    Text(
                        text = "${hr ?: "--"}",
                        fontSize = 68.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "bpm",
                        fontSize = 24.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 18.dp)
                    )
                }
            }
        }

        if (isCamConnected) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp) // igual ao box do BPM
                    .background(Color(0xFF7451A6))
                    .padding(horizontal = 36.dp), // igual ao box do BPM
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = "Temperatura",
                        modifier = Modifier.size(30.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(28.dp)) // igual ao box do BPM
                    Text(
                        text = avgTemp?.let { "%.1fº".format(it) } ?: "--.-º",
                        fontSize = 50.sp, // igual ao BPM
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "Imagem termica",
                        tint = if (isWsConnected && imageReceived) Color.White else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                            .let { base ->
                                if (isWsConnected && imageReceived) base.clickable { showThermalPreview = true } else base
                            }
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Você",
                tint = Color.White,
                modifier = Modifier.size(40.dp).padding(end = 8.dp)
            )
            Slider(
                value = desempenhoPct.coerceIn(0f, 100f),
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF232323)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Diversity1,
                        contentDescription = "Grupo",
                        tint = Color.White,
                        modifier = Modifier.size(25.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Grupo:" + groupName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(8.dp))
                    val top3 = ratingsMap.entries.sortedByDescending { it.value }.take(3)
                    if (top3.isNotEmpty()) {
                        top3.forEach { (uid, pct) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = uid,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    modifier = Modifier.width(100.dp)
                                )
                                Slider(
                                    value = pct.coerceIn(0f, 100f),
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
                                Text(
                                    text = "${pct.toInt()}%",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (showThermalPreview) {
        Dialog(onDismissRequest = { showThermalPreview = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Box(Modifier.padding(12.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Imagem Térmica",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        ThermalCameraPreview(wsViewModel)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { showThermalPreview = false }, modifier = Modifier.align(Alignment.End)) {
                            Text("Fechar")
                        }
                    }
                }
            }
        }
    }
}
