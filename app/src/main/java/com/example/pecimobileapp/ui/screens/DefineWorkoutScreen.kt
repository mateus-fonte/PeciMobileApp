package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.pecimobileapp.viewmodels.ProfileViewModel

@Composable
fun DefineWorkoutScreen(navController: NavController) {
    val profileViewModel: ProfileViewModel = viewModel()

    var selectedZone by remember { mutableStateOf(1) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val zonas = profileViewModel.zonas

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Definir Treino", style = MaterialTheme.typography.headlineMedium)

            Text("Meta de BPM", style = MaterialTheme.typography.titleMedium)
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { dropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val faixa = zonas.getOrNull(selectedZone - 1)?.second
                    Text("Zona $selectedZone: ${faixa?.first}-${faixa?.last} bpm")
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    zonas.forEachIndexed { index, (nome, faixa) ->
                        DropdownMenuItem(
                            text = { Text("$nome: ${faixa.first}-${faixa.last} bpm") },
                            onClick = {
                                selectedZone = index + 1
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                   navController.navigate("workout/${selectedZone}/${profileViewModel.apelido}")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Iniciar Atividade")
            }
        }
    }
}