package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.pecimobileapp.viewmodels.RealTimeViewModel
import com.example.pecimobileapp.viewmodels.WebSocketViewModel

/**
 * Ícones customizados para representar funcionalidades específicas
 */
@Composable
fun HeartEcgIcon(tint: Color = LocalContentColor.current, size: Dp = 24.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "Coração",
            tint = tint,
            modifier = Modifier.size(size)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.ShowChart,
            contentDescription = "Eletrocardiograma",
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}

@Composable
fun CameraThermometerIcon(tint: Color = LocalContentColor.current, size: Dp = 24.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.CameraAlt,
            contentDescription = "Câmera",
            tint = tint,
            modifier = Modifier.size(size)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.Thermostat,
            contentDescription = "Termômetro",
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}

@Composable
fun BicycleIcon(tint: Color = LocalContentColor.current, size: Dp = 24.dp) {
    Icon(
        imageVector = Icons.Filled.DirectionsBike,
        contentDescription = "Pessoa andando de bicicleta",
        tint = tint,
        modifier = Modifier.size(size)
    )
}

/**
 * Componente que exibe o status da conexão da câmera térmica
 */
@Composable
fun CameraConnectionStatus(
    useBle: Boolean,
    useWs: Boolean,
    modifier: Modifier = Modifier
) {
    val connectionState = when {
        useWs -> "WEBSOCKET"
        useBle -> "BLUETOOTH"
        else -> "DESCONECTADA"
    }
    
    val statusColor = when (connectionState) {
        "WEBSOCKET" -> Color(0xFF2196F3) // Azul
        "BLUETOOTH" -> Color(0xFF4CAF50) // Verde
        else -> Color(0xFFE91E63) // Rosa/Vermelho
    }
    
    val statusText = when (connectionState) {
        "WEBSOCKET" -> "Conectada via WebSocket"
        "BLUETOOTH" -> "Conectada via Bluetooth"
        else -> "Desconectada"
    }
    
    val statusIcon = when (connectionState) {
        "WEBSOCKET" -> Icons.Filled.Wifi
        "BLUETOOTH" -> Icons.Filled.Bluetooth
        else -> Icons.Filled.SignalWifiOff
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicador de status (círculo colorido)
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(statusColor, CircleShape)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Texto do status
            Text(
                text = "Status da Câmera: $statusText",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Ícone do status
            Icon(
                imageVector = statusIcon,
                contentDescription = "Status da câmera",
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

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
    val imageReceived by wsViewModel.imageReceived.collectAsState()
    val connectionError by wsViewModel.connectionError.collectAsState()
    
    // Coletar o progresso da configuração
    val setupProgress by wsViewModel.setupProgress.collectAsState()
    // Coletar o status da configuração WiFi
    val wifiConfigStatus by wsViewModel.wifiConfigStatus.collectAsState()

    // Definindo cores personalizadas
    val purpleButtonColor = Color(0xFF9C64A6) // Cor roxa clara como estava anteriormente
    val startButtonColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp) // Reduzido para deixar menos espaço entre o conteúdo e o botão
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 🔌 Seção de conexão com o dispositivo PPG / Smartwatch via BLE
            SimpleBleConnectionSection(
                title = "PPG / Smartwatch",
                scanResults = ppgResults,
                isConnected = ppgConnected,
                onScan = { viewModel.startPpgScan() },
                onConnect = { viewModel.connectPpg(it) },
                allowedDeviceNames = listOf("sw"), // Apenas dispositivos com "sw" no nome
                buttonColor = purpleButtonColor,
                buttonIcon = { HeartEcgIcon() }
            )

            Spacer(Modifier.height(16.dp))
            
            // Status da conexão da câmera térmica
            CameraConnectionStatus(
                useBle = useBle,
                useWs = useWs,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Spacer(Modifier.height(16.dp))

            // 🔌 Seção de conexão com a câmera térmica via BLE com funcionalidades específicas
            ThermalCameraBleSection(
                scanResults = camResults,
                isConnected = useBle,
                onScan = { viewModel.startCamScan() },
                onConnect = { viewModel.connectCam(it) },
                buttonColor = purpleButtonColor,
                buttonIcon = { CameraThermometerIcon() },
                wsViewModel = wsViewModel, // WebSocketViewModel para verificação do AP
                onAdvancedOptions = { ssid, password, device -> 
                    try {
                        // Log para depuração - verificar se esta parte está sendo executada
                        android.util.Log.d("SetupScreen", "CALLBACK ACIONADO - Configurando opções avançadas: SSID=$ssid")
                        
                        // Verificar se o BleManager está disponível
                        val bleManager = viewModel.getBleManager()
                        if (bleManager == null) {
                            android.util.Log.e("SetupScreen", "BleManager não disponível!")
                            return@ThermalCameraBleSection
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

            // Adicionando a barra de progresso de configuração
            if (setupProgress > 0f && setupProgress < 1f) {
                Spacer(Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Configurando WiFi e Servidor",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        LinearProgressIndicator(
                            progress = setupProgress,
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Texto explicativo baseado no progresso atual
                        val statusText = when {
                            setupProgress < 0.4f -> "Enviando configurações WiFi..."
                            setupProgress < 0.6f -> "Configurando rede WiFi..."
                            setupProgress < 0.8f -> "Iniciando servidor WebSocket..."
                            else -> "Aguardando conexão da câmera..."
                        }
                        
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (setupProgress >= 1f) {
                Spacer(Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Configuração concluída",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "Configuração WiFi concluída com sucesso!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } 
            // Mostrar mensagem de erro quando a configuração falhar
            else if (wifiConfigStatus is WebSocketViewModel.WifiConfigStatus.Failed) {
                Spacer(Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = "Erro na configuração",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Column {
                            Text(
                                text = "Erro na configuração WiFi",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Mostrar a mensagem específica do erro
                            Text(
                                text = (wifiConfigStatus as WebSocketViewModel.WifiConfigStatus.Failed).reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Botão para tentar novamente
                    Button(
                        onClick = {
                            // Tentar configurar novamente com os mesmos parâmetros
                            val bleManager = viewModel.getBleManager()
                            if (bleManager != null) {
                                // Exibe diálogo para inserir SSID e senha novamente
                                wsViewModel.prepareRetry()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Tentar Novamente")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ⚙️ Seção de configuração da câmera térmica (Wi-Fi), visível se PPG estiver ativo
            // Título da seção
            Text(
                text = "Câmera Térmica - Visualização",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Exibir o diálogo de Configurações WiFi quando estiver conectada por Bluetooth mas não por WebSocket
            if (useBle && !useWs) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Configurações WiFi",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Text(
                            text = "Configure a câmera térmica via WiFi para visualizar imagens. Use o botão \"Configurar Câmera\" acima.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Text(
                            text = "Câmera conectada via Bluetooth. A pré-visualização estará disponível quando conectada via WebSocket.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // Exibir erro de conexão se houver algum
            val currentError = connectionError // Cria uma cópia local da propriedade delegada
            if (currentError != null && currentError.isNotEmpty() && useWs) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Aviso",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = currentError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
            }

            // Exibir a pré-visualização da câmera quando estiver conectada via WebSocket
            if (useWs && imageReceived) {
                ThermalCameraPreview(wsViewModel)
            } else if (useWs && !imageReceived) {
                // Mostra um estado de "carregando" quando está conectado via WebSocket mas ainda não recebeu imagens
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Aguardando imagens da câmera...",
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(64.dp)
                                .padding(8.dp)
                        )
                        
                        Text(
                            text = "A câmera está conectada via WebSocket, mas ainda não recebeu imagens.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else if (!useBle && !useWs) {
                // Mensagem quando não há conexão
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "Câmera",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Câmera térmica não conectada",
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Text(
                            text = "Conecte a câmera térmica via Bluetooth e configure o WiFi para visualizar as imagens.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }

        // Botão fixo na parte inferior
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 2.dp) // Padding vertical mínimo para ficar bem próximo da barra de navegação
        ) {
            Button(
                onClick = { navController.navigate("define_workout") },
                enabled = ppgConnected,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = purpleButtonColor,
                    disabledContainerColor = purpleButtonColor.copy(alpha = 0.5f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    BicycleIcon()
                    Spacer(Modifier.width(8.dp))
                    Text("Iniciar Atividade Física")
                }
            }
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
                    // Encontrar o rosto com a maior área (provavelmente o mais próximo)
                    val largestFace = faceData.maxByOrNull { it.width * it.height }
                    
                    // Obter a temperatura do maior rosto
                    val largestFaceTemp = largestFace?.temperature ?: 0f
                    
                    // Texto a ser exibido com informações sobre a detecção
                    val displayText = if (faceData.size == 1) {
                        "1 pessoa detectada (Temp: ${String.format("%.1f°C", largestFaceTemp)})"
                    } else {
                        "${faceData.size} pessoas detectadas (Temp. do rosto principal: ${String.format("%.1f°C", largestFaceTemp)})"
                    }
                    
                    Text(
                        text = displayText,
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
