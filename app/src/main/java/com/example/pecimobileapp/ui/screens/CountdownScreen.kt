package com.example.pecimobileapp.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.pecimobileapp.viewmodels.RealTimeViewModel
import kotlinx.coroutines.delay

@Composable
fun CountdownScreen(
    navController: NavController,
    viewModel: RealTimeViewModel,
    onCountdownFinished: () -> Unit = {}
) {
    var counter by remember { mutableStateOf(3) }

    val selectedZone = remember {
        navController.previousBackStackEntry?.savedStateHandle?.get<Int>("selectedZone") ?: 1
    }
    val groupId = remember {
        navController.previousBackStackEntry?.savedStateHandle?.get<String?>("groupId")
    }
    val zonas = remember {
        navController.previousBackStackEntry?.savedStateHandle?.get<List<Pair<String, IntRange>>>("zonas") ?: emptyList()
    }

    val userId = remember {
        navController.previousBackStackEntry?.savedStateHandle?.get<String>("userId") ?: "default_user"
    }

    // ✅ Gerar novo exerciseId aqui
    val exerciseId = remember {
        "ex-${System.currentTimeMillis()}"
    }

    // Log para debug
    LaunchedEffect(Unit) {
        Log.d("CountdownScreen", "Starting countdown with params - selectedZone: $selectedZone, groupId: $groupId, userId: $userId, exerciseId: $exerciseId")

        // Contagem regressiva
        while (counter > 0) {
            delay(1000)
            counter--
        }

        delay(500) // pequena pausa visual

        // ✅ Navegar passando todos os parâmetros
        navController.currentBackStackEntry?.savedStateHandle?.set("zonas", zonas)
        Log.d("CountdownScreen", "Navegando para WorkoutScreen com selectedZone=$selectedZone, groupId=$groupId, userId=$userId, exerciseId=$exerciseId")
        navController.navigate("workout?selectedZone=$selectedZone&groupId=${groupId ?: ""}&userId=$userId&exerciseId=$exerciseId")
    }

    // UI da contagem
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Preparar...",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "$counter",
                fontSize = 96.sp,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}
