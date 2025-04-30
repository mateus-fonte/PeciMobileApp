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

@Composable
fun SetupScreen(
    viewModel    : RealTimeViewModel,
    navController: NavController
) {
    val ppgResults   by viewModel.scanResultsPpg.collectAsState()
    val camResults   by viewModel.scanResultsCam.collectAsState()
    val ppgConnected by viewModel.isPpgConnected.collectAsState()
    val camConnected by viewModel.isCamConnected.collectAsState()
    val ready        by viewModel.readyToStart.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        BleConnectionSection(
            title       = "PPG / Smartwatch",
            scanResults = ppgResults,
            isConnected = ppgConnected,
            onScan      = { viewModel.startPpgScan() },
            onConnect   = { viewModel.connectPpg(it) }
        )

        Spacer(Modifier.height(24.dp))

        BleConnectionSection(
            title       = "Câmera Térmica",
            scanResults = camResults,
            isConnected = camConnected,
            onScan      = { viewModel.startCamScan() },
            onConnect   = { viewModel.connectCam(it) }
        )

        ConfigSection(viewModel)

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { navController.navigate("define_workout") },
            enabled = ready,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Iniciar Atividade Física")
        }

        if (!ready) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Conecte ambos os dispositivos para prosseguir",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}