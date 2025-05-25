package com.example.pecimobileapp.ui.screens

import android.annotation.SuppressLint
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.example.pecimobileapp.R
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

@SuppressLint("StateFlowValueCalledInComposition")
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
    onStop: () -> Unit,
    profileViewModel: com.example.pecimobileapp.ui.ProfileViewModel
) {
    val nomeParticipante = profileViewModel.nome
    val zonas by realTimeViewModel.zonas.collectAsState()
    val zonaAtual by realTimeViewModel.currentZone.collectAsState()
    val desempenhoPct by realTimeViewModel.desempenhoPct.collectAsState()
    val outrosParticipantes by realTimeViewModel.outrosParticipantes.collectAsState()
    val hr by realTimeViewModel.ppgHeartRate.collectAsState()
    val avgTemp by realTimeViewModel.avgTemp.collectAsState()
    val isCamConnected by realTimeViewModel.isCamConnected.collectAsState()
    val isWsConnected by wsViewModel.isWsConnected.collectAsState()
    val imageReceived by wsViewModel.imageReceived.collectAsState()

    var isRunning by remember { mutableStateOf(true) }
    var showStopDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showThermalPreview by remember { mutableStateOf(false) }

    var startTime by remember { mutableStateOf<Long?>(System.currentTimeMillis()) }
    var accumulatedTime by remember { mutableStateOf(0L) }
    var elapsed by remember { mutableStateOf(0) }
    val formattedTime = String.format("%02d:%02d:%02d", elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60)

    val zoneColor = zoneColors[selectedZone] ?: zoneColors[0]!!
    val currentZoneColor = zoneColors[zonaAtual] ?: zoneColors[0]!!

    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isInitialized) {
            try {
                // Configurar para modo térmico exclusivo
                com.example.pecimobileapp.services.ThermalViewUtils.setupThermalOnlyMode(wsViewModel)
        
                Log.d("WorkoutScreen", "Inicializando sessão com zone=$selectedZone, group=$groupId")
                realTimeViewModel.setWorkoutParameters(
                    zone = selectedZone,
                    zonasList = zonas,
                    group = groupId,
                    user = userId,
                    exercise = exerciseId,
                    nome = nomeParticipante
                )
                realTimeViewModel.startActivity()
                groupId?.let { realTimeViewModel.subscribeToGroup(it) }
                isInitialized = true
            } catch (e: Exception) {
                Log.e("WorkoutScreen", "Erro ao inicializar sessão", e)
            }
        }
    }

    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1000L)
            val now = System.currentTimeMillis()
            val currentElapsed = ((now - (startTime ?: now)) / 1000).toInt()
            elapsed = (accumulatedTime / 1000).toInt() + currentElapsed
        }
    }

        BackHandler {
        isRunning = false
        startTime?.let { accumulatedTime += System.currentTimeMillis() - it }
        startTime = null
    }

    DisposableEffect(Unit) {
        onDispose {
            isRunning = false
            realTimeViewModel.stopActivity()
            Log.d("WorkoutScreen", "Treino interrompido ao sair da tela")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header: controles e tempo
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = {
                    isRunning = false
                    startTime?.let { accumulatedTime += System.currentTimeMillis() - it }
                    startTime = null
                }) { Icon(Icons.Default.Pause, contentDescription = "Pause") }

                IconButton(onClick = {
                    isRunning = true
                    startTime = System.currentTimeMillis()
                }) { Icon(Icons.Default.PlayArrow, contentDescription = "Play") }

                IconButton(onClick = { showStopDialog = true }) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                }

                IconButton(onClick = { showResetDialog = true }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset")
                }
            }

            Text(formattedTime, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // Indicador de zonas
        Row(Modifier.fillMaxWidth().height(52.dp)) {
            (0..5).forEach { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(zoneColors[i] ?: Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    if (i == selectedZone) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }

        // Batimentos cardíacos
        /* COMMENT FOR TC DEBUG */
        Box(
            Modifier.fillMaxWidth().height(160.dp).background(currentZoneColor),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp),
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
                Spacer(Modifier.weight(1f))
                Text(
                    text = "bpm",
                    fontSize = 24.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
        }
        
        // Temperatura ble
        if (isCamConnected) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(Color(0xFF7451A6))
                    .padding(horizontal = 36.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Thermostat, contentDescription = "Temp", tint = Color.White)
                    Spacer(Modifier.width(28.dp))
                    Text(
                        text = avgTemp?.let { "%.1fº".format(it) } ?: "--.-º",
                        fontSize = 50.sp,
                        color = Color.White
                    )
                    /*Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "Imagem térmica",
                        tint = if (isWsConnected && imageReceived) Color.White else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp).let {
                            if (isWsConnected && imageReceived) it.clickable {
                                showThermalPreview = true
                            } else it
                        }
                    )*/
                }
            }
        }
        // Temperatura websocket (média + acesso à imagem térmica)
        
        // Obter a temperatura média dos dados térmicos
        val avgTempWS by wsViewModel.avgThermalFlow.collectAsState()
    
        if (isWsConnected) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(Color(0xFF7451A6))
                    .padding(horizontal = 36.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Thermostat, contentDescription = "Temp", tint = Color.White)
                    Spacer(Modifier.width(28.dp))
                    Text(
                        text = avgTempWS?.let { "%.1fº".format(it) } ?: "--.-º",
                        //text = "%.1fº".format(avgTemp),
                        fontSize = 50.sp,
                        color = Color.White
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "Imagem térmica",
                        tint = if (imageReceived) Color.White else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp).let {
                            if (isWsConnected && imageReceived) it.clickable {
                                showThermalPreview = true
                            } else it
                        }
                    )
                }
            }
        }

        // Slider do próprio usuário
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.heart_check),
                contentDescription = "Ícone de coração com check",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Slider(
                value = desempenhoPct.coerceIn(0f, 100f),
                onValueChange = {},
                enabled = false,
                valueRange = 0f..100f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = zoneColor,
                    inactiveTrackColor = zoneColor.copy(alpha = 0.3f),
                    disabledThumbColor = Color.White,
                    disabledActiveTrackColor = zoneColor,
                    disabledInactiveTrackColor = zoneColor.copy(alpha = 0.3f)
                )
            )
        }
            // Participantes do grupo (exceto o próprio)
        if (groupId != null) {
    Spacer(Modifier.height(16.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Icon(Icons.Default.Diversity1, contentDescription = "Grupo", tint = Color.White, modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(4.dp))
            Text("Grupo: $groupId", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))

            outrosParticipantes
                .toSortedMap(compareBy { it.lowercase() })
                .forEach { (nome, data) ->
                    val rating = data.rating.coerceIn(0f, 100f)
                    val zona = data.zonaAlvo
                    val corZona = zoneColors[zona] ?: Color.Gray
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(nome, color = Color.White, fontSize = 14.sp, modifier = Modifier.width(100.dp))
                        Slider(
                            value = rating,
                            onValueChange = {},
                            enabled = false,
                            valueRange = 0f..100f,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = corZona,
                                inactiveTrackColor = corZona.copy(alpha = 0.3f),
                                disabledThumbColor = Color.White,
                                disabledActiveTrackColor = corZona,
                                disabledInactiveTrackColor = corZona.copy(alpha = 0.3f)
                            )
                        )
                        Text("${rating.toInt()}%", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
        }
    }
}

        // Diálogo: Resetar treino
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reiniciar Treino") },
                text = { Text("O treino atual será perdido. Deseja continuar?") },
                confirmButton = {
                    isRunning = false
                    MqttManager.WorkoutSessionManager.resetSession()
                    navController.navigate("countdown") {
                        popUpTo("workout") { inclusive = true }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) { Text("Cancelar") }
                }
            )
        }

        // Diálogo: Parar treino
        if (showStopDialog) {
            AlertDialog(
                onDismissRequest = { showStopDialog = false },
                title = { Text("Parar Treino") },
                text = { Text("O treino será interrompido. Deseja continuar?") },
                confirmButton = {
                    isRunning = false
                    realTimeViewModel.stopActivity()
                    navController.popBackStack("define_workout", inclusive = false)
                },
                dismissButton = {
                    TextButton(onClick = { showStopDialog = false }) { Text("Cancelar") }
                }
            )
        }

        // Diálogo: Imagem térmica
        if (showThermalPreview) {
            Dialog(onDismissRequest = { showThermalPreview = false }) {
                Surface(shape = RoundedCornerShape(16.dp)) {
                    Column(
                        Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Imagem Térmica", style = MaterialTheme.typography.titleMedium)
                        ThermalCameraPreview(wsViewModel)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { showThermalPreview = false }) {
                            Text("Fechar")
                        }
                    }
                }
            }
        }
    }
}
