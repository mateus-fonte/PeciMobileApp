package com.example.pecimobileapp.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.example.pecimobileapp.ble.DeviceType
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pecimobileapp.utils.OpenCVUtils
import com.example.pecimobileapp.viewmodels.WebSocketViewModel
import com.example.pecimobileapp.services.WebSocketServerService
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// Função auxiliar para converter timestamp em data formatada
private fun formatTimestamp(timestamp: String): String? {
    if (timestamp.isEmpty()) return null
    
    val parts = timestamp.split(".")
    if (parts.size != 2) return null
    
    try {
        val epochSeconds = parts[0].toLongOrNull() ?: return null
        val millisPart = parts[1]
        val date = Date(epochSeconds * 1000)
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val formattedDate = sdf.format(date)
        return "$formattedDate.$millisPart"
    } catch (e: Exception) {
        return null
    }
}

@Composable
fun WebSocketScreen(
    viewModel: WebSocketViewModel = viewModel()
) {
    // Estados observáveis
    val isServerRunning by viewModel.isServerRunning.collectAsState()
    val cameraImageData by viewModel.latestCameraImage.collectAsState()
    val thermalData by viewModel.latestThermalData.collectAsState()
    val connectionStats by viewModel.connectionStats.collectAsState()
    val processedImageData by viewModel.processedImage.collectAsState()
    val serverState by viewModel.serverState.collectAsState()
    
    // Estados locais
    var port by remember { mutableStateOf("8080") }
    var showHotspotAlert by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()

    // Mostrar alerta se o hotspot não estiver ativo
    if (showHotspotAlert) {
        AlertDialog(
            onDismissRequest = { showHotspotAlert = false },
            title = { Text("Atenção") },
            text = { 
                Column {
                    Text("O hotspot do dispositivo não está ativo.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Por favor, ative o hotspot e tente novamente.")
                }
            },
            confirmButton = {
                Button(onClick = { showHotspotAlert = false }) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Título
        Text(
            text = "ESP32 WebSocket Server",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Informação de conexão
        ConnectionInfoSection(
            isServerRunning = isServerRunning, 
            connectionStats = connectionStats,
            port = port,
            onPortChange = { port = it },
            onStartServer = { 
                val result = viewModel.startServer(port.toIntOrNull() ?: 8080)
                if (!result.first && result.second is WebSocketServerService.ServerState.HotspotNotActive) {
                    showHotspotAlert = true
                }
            },
            onStopServer = { viewModel.stopServer() },
            serverState = serverState
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Imagem processada com detecção facial e dados térmicos
        ProcessedImageSection(
            processedImage = processedImageData.first,
            faceData = processedImageData.second,
            cameraTimestamp = cameraImageData.second,
            thermalTimestamp = thermalData.second,
        )
    }
}

@Composable
fun ConnectionInfoSection(
    isServerRunning: Boolean,
    connectionStats: com.example.pecimobileapp.services.WebSocketServerService.ConnectionStats,
    port: String,
    onPortChange: (String) -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    serverState: WebSocketServerService.ServerState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Aviso de Hotspot quando necessário
            if (serverState is WebSocketServerService.ServerState.HotspotNotActive) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF3E0)  // Cor de fundo laranja claro
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Warning,
                                contentDescription = "Aviso",
                                tint = Color(0xFFFF9800),  // Cor laranja
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Hotspot não ativo",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)  // Cor laranja escuro
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Por favor, ative o hotspot do seu dispositivo para continuar.",
                            color = Color(0xFF795548)  // Cor marrom
                        )
                    }
                }
            }
            
            // Status do servidor
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status do servidor:",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(if (isServerRunning) Color.Green else Color.Red)
                )
                
                Text(
                    text = if (isServerRunning) "Online" else "Offline",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Endereço do servidor
            if (isServerRunning && connectionStats.serverAddress.isNotEmpty()) {
                Text(
                    text = "Endereço do servidor: ${connectionStats.serverAddress}",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            
            // Clientes conectados e estatísticas
            if (isServerRunning) {
                Text(
                    text = "Clientes conectados: ${connectionStats.clientsCount}",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                
                Text(
                    text = "Mensagens recebidas: ${connectionStats.receivedMessages}",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                
                // Estatísticas de detecção facial
                Text(
                    text = "Rostos detectados: ${connectionStats.detectedFaces}",
                    modifier = Modifier.padding(vertical = 4.dp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Controles do servidor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Campo para a porta
                OutlinedTextField(
                    value = port,
                    onValueChange = { 
                        // Apenas aceitar números
                        if (it.all { char -> char.isDigit() }) {
                            onPortChange(it)
                        }
                    },
                    label = { Text("Porta") },
                    singleLine = true,
                    modifier = Modifier.width(120.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Botão iniciar/parar
                Button(
                    onClick = if (!isServerRunning) onStartServer else onStopServer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isServerRunning) MaterialTheme.colorScheme.primary else Color.Red
                    )
                ) {
                    Text(if (!isServerRunning) "Iniciar Servidor" else "Parar Servidor")
                }
            }
        }
    }
}

@Composable
fun ProcessedImageSection(
    processedImage: Bitmap?,
    faceData: List<OpenCVUtils.FaceData>,
    cameraTimestamp: String,
    thermalTimestamp: String
) {
    // Preparar as datas formatadas fora do contexto do Composable
    val formattedCameraDate = remember(cameraTimestamp) {
        formatTimestamp(cameraTimestamp)
    }
    
    val formattedThermalDate = remember(thermalTimestamp) {
        formatTimestamp(thermalTimestamp)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Processamento de Imagem - Detecção Facial e Térmica",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (processedImage != null) {
                Image(
                    bitmap = processedImage.asImageBitmap(),
                    contentDescription = "Imagem processada com detecção facial e térmica",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .padding(vertical = 8.dp)
                )
                
                // Exibir informações de rostos detectados
                if (faceData.isNotEmpty()) {
                    Text(
                        text = "${faceData.size} rosto(s) detectado(s)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    
                    // Listar temperaturas faciais
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        faceData.forEachIndexed { index, face ->
                            if (!face.temperature.isNaN()) {
                                Text(
                                    text = "Rosto ${index + 1}: ${String.format(Locale.getDefault(), "%.1f°C", face.temperature)}",
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Nenhum rosto detectado",
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                // Mostrar timestamps das imagens
                if (cameraTimestamp.isNotEmpty() || thermalTimestamp.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text(
                        text = "Informações de Timestamp:",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    if (cameraTimestamp.isNotEmpty()) {
                        formattedCameraDate?.let {
                            Text(
                                text = "Câmera: $it",
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                    
                    if (thermalTimestamp.isNotEmpty()) {
                        formattedThermalDate?.let {
                            Text(
                                text = "Térmica: $it",
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aguardando imagens para processamento...",
                        fontSize = 16.sp,
                        color = Color.DarkGray
                    )
                }
            }
        }
    }
}