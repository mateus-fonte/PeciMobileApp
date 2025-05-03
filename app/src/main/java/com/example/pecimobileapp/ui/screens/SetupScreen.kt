package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.pecimobileapp.viewmodels.RealTimeViewModel
import com.example.pecimobileapp.viewmodels.WebSocketViewModel

@Composable
fun SetupScreen(
    viewModel: RealTimeViewModel,
    navController: NavController,
    wsViewModel: WebSocketViewModel
) {
    val ppgResults   by viewModel.scanResultsPpg.collectAsState()
    val camResults   by viewModel.scanResultsCam.collectAsState()
    val ppgConnected by viewModel.isPpgConnected.collectAsState()
    val useBle       by viewModel.isCamConnected.collectAsState()
    val useWs        by wsViewModel.isWsConnected.collectAsState()
    val ready        by viewModel.readyToStart.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 🔌 Seção de conexão com o dispositivo PPG / Smartwatch via BLE
        BleConnectionSection(
            title       = "PPG / Smartwatch",
            scanResults = ppgResults,
            isConnected = ppgConnected,
            onScan      = { viewModel.startPpgScan() },
            onConnect   = { viewModel.connectPpg(it) }
        )

        Spacer(Modifier.height(24.dp))

        // 🔌 Seção de conexão com a câmera térmica via BLE
        BleConnectionSection(
            title       = "Câmera Térmica",
            scanResults = camResults,
            isConnected = useBle,
            onScan      = { viewModel.startCamScan() },
            onConnect   = { viewModel.connectCam(it) },
            onAdvancedOptions = { ssid, password, device -> 
                try {
                    // Log para depuração - verificar se esta parte está sendo executada
                    android.util.Log.d("SetupScreen", "CALLBACK ACIONADO - Configurando opções avançadas: SSID=$ssid")
                    
                    // Verificar se o BleManager está disponível
                    val bleManager = viewModel.getBleManager()
                    if (bleManager == null) {
                        android.util.Log.e("SetupScreen", "BleManager não disponível!")
                        return@BleConnectionSection
                    }
                    
                    android.util.Log.d("SetupScreen", "BleManager obtido com sucesso, enviando para configuração")
                    
                    // Verificar se o AP está ativo e enviar configurações para o ESP32
                    wsViewModel.configureEsp32AndStartServer(
                        bleManager = bleManager,
                        ssid = ssid,
                        password = password
                    )
                    
                    android.util.Log.d("SetupScreen", "Chamada de configuração iniciada com sucesso")
                } catch (e: Exception) {
                    android.util.Log.e("SetupScreen", "ERRO ao configurar opções avançadas: ${e.message}", e)
                    // Mostrar um toast ou alerta para o usuário seria útil aqui
                }
            }
        )

        Spacer(Modifier.height(24.dp))

        // ⚙️ Seção de configuração da câmera térmica (Wi-Fi), visível se PPG ou WebSocket estiverem ativos
        if (ppgConnected || useWs) {
            Text(
                text = "Configurar Wi-Fi da Câmera Térmica",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ConfigSection(viewModel)

            if (useWs) {
                Text(
                    text = "✓ Câmera térmica conectada por Wi-Fi!",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(8.dp)
                )
                
                // Exibir imagem da câmera térmica
                ThermalCameraPreview(wsViewModel)
            }
        } else {
            // 🚫 Caso o PPG ainda não esteja conectado
            Text(
                "Conecte o PPG/Smartwatch para habilitar configurações",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(32.dp))

        // ▶️ Botão para iniciar a atividade física (habilitado só se o PPG estiver conectado)
        Button(
            onClick = { navController.navigate("define_workout") },
            enabled = ppgConnected,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Iniciar Atividade Física")
        }
    }
}

/**
 * Componente que exibe a visualização da câmera térmica
 */
@Composable
fun ThermalCameraPreview(wsViewModel: WebSocketViewModel) {
    // Obter a imagem processada do WebSocketViewModel
    val processedImageData by wsViewModel.processedImage.collectAsState()
    val bitmap = processedImageData.first
    val faceData = processedImageData.second
    
    // Card para exibir a imagem
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Visualização da Câmera Térmica",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Se tiver imagem, exibir. Caso contrário, mostrar um placeholder
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Imagem da câmera térmica",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .padding(vertical = 8.dp)
                )
                
                // Mostrar informações sobre rostos detectados
                if (faceData.isNotEmpty()) {
                    val maxTemp = faceData.maxByOrNull { it.temperature }?.temperature ?: 0f
                    
                    // Mostrar a contagem de faces e a temperatura máxima detectada
                    Text(
                        text = "${faceData.size} ${if (faceData.size == 1) "pessoa detectada" else "pessoas detectadas"} " +
                              "(Temp. máx: ${String.format("%.1f°C", maxTemp)})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                // Placeholder quando não há imagem
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aguardando imagem da câmera...",
                        color = Color.DarkGray
                    )
                }
            }
        }
    }
    
    // Log para depuração
    LaunchedEffect(bitmap) {
        if (bitmap != null) {
            android.util.Log.d("ThermalCameraPreview", "Imagem recebida e exibida na tela de Setup")
        }
    }
}
