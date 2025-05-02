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
            onConnect   = { viewModel.connectCam(it) }
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
