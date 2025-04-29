package com.example.pecimobileapp.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pecimobileapp.viewmodels.WebSocketViewModel
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
    
    // Estados locais
    var port by remember { mutableStateOf("8080") }
    
    val scrollState = rememberScrollState()

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
            onStartServer = { viewModel.startServer(port.toIntOrNull() ?: 8080) },
            onStopServer = { viewModel.stopServer() }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Imagens da câmera e térmica
        ImagesSection(
            cameraImage = cameraImageData.first,
            cameraTimestamp = cameraImageData.second,
            thermalData = thermalData.first,
            thermalTimestamp = thermalData.second,
            viewModel = viewModel
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
    onStopServer: () -> Unit
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
            
            // Todos os IPs do dispositivo
            Text(
                text = "Endereços IP disponíveis:",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            
            connectionStats.allNetworkIPs.forEach { ip ->
                Text(
                    text = "• $ip",
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            
            // Clientes conectados
            if (isServerRunning) {
                Text(
                    text = "Clientes conectados: ${connectionStats.clientsCount}",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                
                Text(
                    text = "Mensagens recebidas: ${connectionStats.receivedMessages}",
                    modifier = Modifier.padding(vertical = 4.dp)
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
fun ImagesSection(
    cameraImage: Bitmap?,
    cameraTimestamp: String,
    thermalData: FloatArray?,
    thermalTimestamp: String,
    viewModel: WebSocketViewModel
) {
    // Preparar as datas formatadas fora do contexto do Composable
    val formattedCameraDate = remember(cameraTimestamp) {
        formatTimestamp(cameraTimestamp)
    }
    
    val formattedThermalDate = remember(thermalTimestamp) {
        formatTimestamp(thermalTimestamp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        // Imagem da câmera
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
                    text = "Imagem da Câmera",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (cameraImage != null) {
                    Image(
                        bitmap = cameraImage.asImageBitmap(),
                        contentDescription = "Imagem da câmera",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(vertical = 8.dp)
                    )
                    
                    if (cameraTimestamp.isNotEmpty()) {
                        Text(
                            text = "Timestamp: $cameraTimestamp",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        
                        formattedCameraDate?.let {
                            Text(
                                text = "Data: $it",
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aguardando imagem...",
                            fontSize = 16.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }
        
        // Matriz térmica
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
                    text = "Matriz Térmica (32x24)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (thermalData != null) {
                    // Renderizar a matriz térmica como uma grade de cores
                    ThermalMatrix(thermalData = thermalData)
                    
                    // Mostrar estatísticas
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Mínimo
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Min", fontWeight = FontWeight.Bold)
                            Text(
                                text = "${viewModel.getThermalMinValue().roundToInt()}°C",
                                color = Color.Blue
                            )
                        }
                        
                        // Média
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Média", fontWeight = FontWeight.Bold)
                            Text(
                                text = "${viewModel.getThermalAvgValue().roundToInt()}°C"
                            )
                        }
                        
                        // Máximo
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Max", fontWeight = FontWeight.Bold)
                            Text(
                                text = "${viewModel.getThermalMaxValue().roundToInt()}°C",
                                color = Color.Red
                            )
                        }
                    }
                    
                    if (thermalTimestamp.isNotEmpty()) {
                        Text(
                            text = "Timestamp: $thermalTimestamp",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        
                        formattedThermalDate?.let {
                            Text(
                                text = "Data: $it",
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aguardando dados térmicos...",
                            fontSize = 16.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThermalMatrix(thermalData: FloatArray) {
    // Encontrar valores mínimo e máximo para normalização
    val minTemp = thermalData.minOrNull() ?: 0f
    val maxTemp = thermalData.maxOrNull() ?: 50f
    val range = maxTemp - minTemp
    
    // Criar grid 32x24
    Column(modifier = Modifier.padding(8.dp)) {
        for (y in 0 until 24) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (x in 0 until 32) {
                    val index = y * 32 + x
                    if (index < thermalData.size) {
                        val value = thermalData[index]
                        // Normalizar valor entre 0 e 1
                        val normalizedValue = if (range > 0) (value - minTemp) / range else 0.5f
                        
                        // Criar cor usando mapa de calor (azul para baixo, vermelho para alto)
                        val blue = (1 - normalizedValue).coerceIn(0f, 1f)
                        val red = normalizedValue.coerceIn(0f, 1f)
                        val green = (1 - Math.abs(2 * normalizedValue - 1)).coerceIn(0f, 1f) * 0.8f
                        
                        val color = Color(red, green, blue)
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(1.dp)
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}