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
import kotlinx.coroutines.launch

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
 * Componente que exibe o status da conexão da câmera térmica de forma sutil
 */
@Composable
fun CameraConnectionStatus(
    useBle: Boolean,
    useWs: Boolean,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true
) {
    val connectionState = when {
        useWs -> "WEBSOCKET"
        useBle -> "BLUETOOTH"
        else -> "DESCONECTADA"
    }
    
    val statusColor = when (connectionState) {
        "WEBSOCKET" -> Color(0xFF4CAF50) // Verde
        "BLUETOOTH" -> Color(0xFF2196F3) // Azul
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
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (showTitle) Arrangement.SpaceBetween else Arrangement.End
    ) {
        if (showTitle) {
            Text(
                text = "Status da câmera",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = "Status da câmera",
                tint = statusColor,
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Componente que exibe o status da conexão do PPG/Smartwatch de forma sutil
 */
@Composable
fun PPGConnectionStatus(
    isConnected: Boolean,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true
) {
    val statusColor = if (isConnected) {
        Color(0xFF2196F3) // Azul
    } else {
        Color(0xFFE91E63) // Rosa/Vermelho
    }
    
    val statusText = if (isConnected) {
        "Conectado via Bluetooth"
    } else {
        "Desconectado"
    }
    
    val statusIcon = if (isConnected) {
        Icons.Filled.Bluetooth
    } else {
        Icons.Filled.BluetoothDisabled
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (showTitle) Arrangement.SpaceBetween else Arrangement.End
    ) {
        if (showTitle) {
            Text(
                text = "Status do PPG/Smartwatch",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = "Status do PPG/Smartwatch",
                tint = statusColor,
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    
    // Sempre configurar para modo térmico exclusivo quando a tela é carregada
    LaunchedEffect(Unit) {
        com.example.pecimobileapp.services.ThermalViewUtils.setupThermalOnlyMode(wsViewModel)
    }
    
    // Coletar o progresso da configuração
    val setupProgress by wsViewModel.setupProgress.collectAsState()
    // Coletar o status da configuração WiFi
    val wifiConfigStatus by wsViewModel.wifiConfigStatus.collectAsState()
    
    // Estado para controlar a exibição do toast de configuração concluída
    var showConfigSuccessToast by remember { mutableStateOf(false) }
    // Estado para rastrear quando o progresso atingiu 100% pela primeira vez nesta sessão
    var progressCompletedTracked by remember { mutableStateOf(false) }
    // Estado para controlar se a tela já foi carregada para evitar toast na entrada
    var isScreenLoaded by remember { mutableStateOf(false) }

    // Definindo cores personalizadas
    val purpleButtonColor = Color(0xFF9C64A6) // Cor roxa clara como estava anteriormente
    val startButtonColor = MaterialTheme.colorScheme.primary
      // SnackbarHostState para mostrar mensagens temporárias
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Remember the coroutine scope for use in callbacks
    val scope = rememberCoroutineScope()

    // Inicialização - para marcar que a tela já foi carregada
    LaunchedEffect(Unit) {
        isScreenLoaded = true
    }

    // Efeito colateral para mostrar o toast quando a configuração for concluída
    // Só mostra o toast se a tela já estiver carregada e o progresso atingir 100% pela primeira vez
    LaunchedEffect(setupProgress) {
        if (setupProgress >= 1f && !progressCompletedTracked && isScreenLoaded) {
            progressCompletedTracked = true
            showConfigSuccessToast = true
            // Mostrar Snackbar/Toast
            snackbarHostState.showSnackbar(
                message = "Configuração WiFi concluída com sucesso!",
                actionLabel = "OK",
                duration = SnackbarDuration.Short
            )
            // Resetar o estado após alguns segundos para não mostrar o toast novamente
            kotlinx.coroutines.delay(3000)
            showConfigSuccessToast = false
        }
    }

    // Redefinir o rastreamento quando o progresso voltar a zero
    LaunchedEffect(key1 = setupProgress <= 0f) {
        if (setupProgress <= 0f) {
            progressCompletedTracked = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)  // Aplicando o paddingValues do Scaffold aqui
                .padding(bottom = 16.dp) // Padding adicional na parte inferior
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Título principal e status da seção PPG/Smartwatch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Smartwatch",
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    // Status da conexão do PPG/Smartwatch
                    PPGConnectionStatus(
                        isConnected = ppgConnected,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        showTitle = false
                    )
                }

                // 🔌 Seção de conexão com o dispositivo PPG / Smartwatch via BLE
                SimpleBleConnectionSection(
                    title = "PPG / Smartwatch",
                    scanResults = ppgResults,
                    isConnected = ppgConnected,
                    onScan = { viewModel.startPpgScan() },
                    onConnect = { viewModel.connectPpg(it) },
                    onDisconnect = { viewModel.disconnectPpg() },
                    allowedDeviceNames = listOf("sw", "ESP32_PPG"),
                    buttonColor = purpleButtonColor,
                    buttonIcon = { HeartEcgIcon() }
                )

                // Divisor entre seções
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                // Título e status da seção da câmera térmica
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Câmera Térmica",
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    // Status da conexão da câmera térmica
                    CameraConnectionStatus(
                        useBle = useBle,
                        useWs = useWs,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        showTitle = false
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ⚙️ Seção de conexão com a câmera térmica via BLE com funcionalidades específicas
                // Esta seção só será mostrada se a câmera não estiver conectada por WebSocket
                if (!useWs) {
                    ThermalCameraBleSection(
                        scanResults = camResults,
                        isConnected = useBle,
                        onScan = { viewModel.startCamScan() },
                        onConnect = { viewModel.connectCam(it) },
                        onDisconnect = { viewModel.disconnectCam() },
                        onAdvancedOptions = { ssid, password, device ->
                            android.util.Log.d("SetupScreen", "CALLBACK ACIONADO - Configurando WiFi: SSID=$ssid")
                            
                            scope.launch {
                                val success = wsViewModel.configureEsp32AndStartServer(
                                    ssid = ssid,
                                    password = password
                                )
                                if (success) {
                                    android.util.Log.d("SetupScreen", "Configuração WiFi iniciada com sucesso")
                                } else {
                                    android.util.Log.e("SetupScreen", "Falha ao iniciar configuração WiFi")
                                }
                            }
                        },
                        buttonColor = purpleButtonColor,
                        buttonIcon = { CameraThermometerIcon() },
                        wsViewModel = wsViewModel
                    )
                }

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
                            
                            // Debug log para verificar o valor atual do progresso
                            val progressPercentage = (setupProgress * 100).toInt()
                            android.util.Log.d("SetupScreen", "Progresso atual: $progressPercentage%")
                            
                            LinearProgressIndicator(
                                progress = setupProgress,
                                modifier = Modifier.fillMaxWidth().height(8.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Texto explicativo baseado no progresso atual
                            val statusText = when {
                                setupProgress < 0.4f -> "Enviando configurações WiFi... (${progressPercentage}%)"
                                setupProgress < 0.6f -> "Configurando rede WiFi... (${progressPercentage}%)"
                                setupProgress < 0.8f -> "Iniciando servidor WebSocket... (${progressPercentage}%)"
                                setupProgress < 1f -> "Aguardando conexão da câmera... (${progressPercentage}%)"
                                else -> "Configuração concluída! (100%)"
                            }
                            
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                
                // Mostrar mensagem de erro quando a configuração falhar
                if (wifiConfigStatus is WebSocketViewModel.WifiConfigStatus.Failed) {
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
                                    // Exibe diálogo para inserir SSID e senha novamente
                                    wsViewModel.prepareRetry()

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

                // ⚙️ Seção de visualização da câmera térmica
                Text(
                    text = "Visualização da Câmera Térmica",
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

            // Botão fixo na parte inferior para iniciar atividade
            /*Box(
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
                    // Layout horizontal com ícone de bicicleta e texto
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        BicycleIcon()
                        Spacer(Modifier.width(8.dp))
                        Text("Iniciar Atividade Física")
                    }
                }
            }*/
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