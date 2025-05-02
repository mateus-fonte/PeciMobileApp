package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    val useBle        by viewModel.isCamConnected.collectAsState() // Use BLE for the condition
    val useWs         by wsViewModel.isWsConnected.collectAsState()
    val ready        by viewModel.readyToStart.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Seção para a conexão do PPG/Smartwatch
        BleConnectionSection(
            title       = "PPG / Smartwatch",
            scanResults = ppgResults,
            isConnected = ppgConnected,
            onScan      = { viewModel.startPpgScan() },
            onConnect   = { viewModel.connectPpg(it) }
        )

        Spacer(Modifier.height(24.dp))

        // Seção para a conexão da Câmera Térmica (não obrigatória para iniciar)
        BleConnectionSection(
            title       = "Câmera Térmica",
            scanResults = camResults,
            isConnected = useBle,
            onScan      = { viewModel.startCamScan() },
            onConnect   = { viewModel.connectCam(it) }
        )

        // Configurações habilitadas se a conexão com o PPG/Smartwatch ou WebSocket estiver ativa
        if (ppgConnected || useWs) { // Agora depende apenas do PPG/Smartwatch ou WebSocket
            ConfigSection(viewModel)
            if (useWs) {
                Text(
                    text = "Câmera térmica conectada por Wifi!",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(8.dp)
                )
            }
        } else {
            // Se o PPG/Smartwatch não estiver conectado
            Text(
                "Conecte o PPG/Smartwatch para habilitar configurações",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(32.dp))

        // Botão para iniciar a atividade física habilitado apenas se o PPG/Smartwatch estiver conectado
        Button(
            onClick = { navController.navigate("define_workout") },
            enabled = ppgConnected, // Agora só depende da conexão com o PPG/Smartwatch
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Iniciar Atividade Física")
        }
    }
}
