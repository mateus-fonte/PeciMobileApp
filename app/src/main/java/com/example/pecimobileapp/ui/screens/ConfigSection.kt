package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.pecimobileapp.viewmodels.RealTimeViewModel

@Composable
fun ConfigSection(realTimeModel: RealTimeViewModel) {
    // Gera valores automaticamente
    val timestamp = remember { System.currentTimeMillis() }
    val mode = 2
    val id   = remember { java.util.UUID.randomUUID().toString() }
    var sent by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        if (!sent) {
            Text("Timestamp: $timestamp")
            Text("Mode: $mode")
            Text("ID: $id")
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                realTimeModel.sendTimeConfig(timestamp)
                realTimeModel.sendModeConfig(mode)
                realTimeModel.sendIdConfig(id)
                sent = true
            }, Modifier.fillMaxWidth()) {
                Text("Enviar Configurações")
            }
        } else {
            Text("✓ Configurações enviadas!", color = Color(0xFF4CAF50))
        }
    }
}

