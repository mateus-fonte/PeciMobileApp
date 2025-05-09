package com.example.pecimobileapp.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pecimobileapp.viewmodels.RealTimeViewModel
import com.example.pecimobileapp.viewmodels.WebSocketViewModel
import androidx.navigation.NavHostController
import com.example.pecimobileapp.R
import com.example.pecimobileapp.ui.ProfileViewModel
import com.example.pecimobileapp.viewmodels.ProfileViewModelFactory


@Composable
fun MainScreen(
    viewModel: RealTimeViewModel,
    wsViewModel: WebSocketViewModel,
    navController: NavHostController // Adicione o NavController aqui
) {

    val context = LocalContext.current
    val profileViewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory(context))
    val userId = profileViewModel.userId
    // 1) Coleta dos valores
    val hr by viewModel.ppgHeartRate.collectAsState()
    val isPpgConnected by viewModel.isPpgConnected.collectAsState()
    val ppgLost by viewModel.ppgConnectionLost.collectAsState()
    val useBle by viewModel.isCamConnected.collectAsState()
    val camLost by viewModel.camConnectionLost.collectAsState()
    val useWs by wsViewModel.isWsConnected.collectAsState() // Alterado para isWsConnected para verificar clientes conectados
    val avgTemp by viewModel.avgTemp.collectAsState()
    val maxTemp by viewModel.maxTemp.collectAsState()
    val minTemp by viewModel.minTemp.collectAsState()
    // Coletar a temperatura facial como estado observável
    val facialTemp by wsViewModel.processedImage.collectAsState()

    val perfilVisitado = rememberSaveable { mutableStateOf(false) }

    // Para exibir snackbars:
    val snackbarHostState = remember { SnackbarHostState() }

    // Quando ppgLost virar true, mostramos snackbar:
    LaunchedEffect(ppgLost) {
        if (ppgLost) {
            snackbarHostState.showSnackbar("Conexão com o sensor de PPG perdida.")
        }
    }
    // Quando camLost virar true, mostramos outro:
    LaunchedEffect(camLost) {
        if (camLost) {
            snackbarHostState.showSnackbar("Conexão com a câmera térmica perdida.")
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Texto visível antes da conexão dos sensores
            if (!isPpgConnected && !useBle && !useWs) {
                Text(
                    "Bem-vindo, ciclista!",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                if (!isPpgConnected && !useBle && !useWs) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            InstructionCard()
                        }

                        Column {
                            if (userId.isNullOrBlank()) {
                                Button(
                                    onClick = { navController.navigate("profile") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Completar perfil", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = { navController.navigate("setup") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Configurar sensores", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else if (!userId.isNullOrBlank() && isPpgConnected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        // Imagem como plano de fundo
                        Image(
                            painter = painterResource(id = R.drawable.bike_illustration),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(0.4f)
                        )

                        // Texto por cima da imagem
                        Text(
                            text = "Preparado para pedalar?",
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 15.dp) // distância do topo
                        )

                        // Botão no final (também sobre a imagem)
                        Button(
                            onClick = { navController.navigate("define_workout") },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(24.dp)
                                .graphicsLayer(scaleX = pulse, scaleY = pulse),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.DirectionsBike, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Iniciar Atividade Física")
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun BicycleIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.DirectionsBike, // ou o que você estiver usando
        contentDescription = "Ícone de bicicleta",
        modifier = modifier
    )
}

@Composable
fun InstructionCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Como usar ",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Divider()
            Spacer(Modifier.height(8.dp))

            // Lista de passos
            val steps = listOf(
                "Completar Perfil.",
                "Entrar na aba Setup.",
                "Escanear e conectar seu sensor de batimento cardíaco.",
                "Escanear e conectar a câmera térmica.",
                "Se conectar a câmera térmica por Wi-Fi 👉 envie o nome da rede e a senha.",
                "Só então inicie a atividade desejada, individual ou em grupo."
            )

            steps.forEachIndexed { index, text ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    // número dentro de círculo colorido
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}