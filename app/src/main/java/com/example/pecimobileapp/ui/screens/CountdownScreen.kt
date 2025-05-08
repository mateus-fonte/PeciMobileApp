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
    
    // Read parameters once
    val selectedZone = remember { 
        navController.previousBackStackEntry?.savedStateHandle?.get<Int>("selectedZone") ?: 1 
    }
    val groupId = remember { 
        navController.previousBackStackEntry?.savedStateHandle?.get<String?>("groupId") 
    }
    val userId = remember { 
        navController.previousBackStackEntry?.savedStateHandle?.get<String>("userId") ?: "default_user" 
    }
    
    // Log parameters at start
    LaunchedEffect(Unit) {
        Log.d("CountdownScreen", "Starting countdown with params - selectedZone: $selectedZone, groupId: $groupId, userId: $userId")
        
        // Countdown
        while (counter > 0) {
            delay(1000)
            counter--
        }
        
        delay(500) // Small pause at 0
        
        // Use arguments when navigating to pass the parameters
        navController.navigate("workout?selectedZone=$selectedZone&groupId=${groupId ?: ""}&userId=$userId")
    }

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