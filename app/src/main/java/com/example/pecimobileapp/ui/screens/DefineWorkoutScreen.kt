package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun DefineWorkoutScreen(navController: NavController) {
    var mode by remember { mutableStateOf("individual") }
    var nickname by remember { mutableStateOf("") }
    var selectedZone by remember { mutableStateOf(1) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val zoneDescriptions = mapOf(
        1 to "Zona 1 AerobicEndurance: Exercício muito leve",
        2 to "Zona 2 AerobicPower: Exercício leve",
        3 to "Zona 3 Threshold: Exercício moderadamente extenuante",
        4 to "Zona 4 AnaerobicEndurance: Exercício pesado",
        5 to "Zona 5 AnaerobicPower: Exercício realmente difícil",
        6 to "Sem zona definida"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Adiciona rolamento
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Definir Treino", style = MaterialTheme.typography.headlineMedium)

            // Tipo de treino
            Text("Modo de Treino", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RadioButtonWithLabel("Individual", "individual", mode) { mode = it }
                RadioButtonWithLabel("Aula", "aula", mode) { mode = it }
            }

            if (mode == "aula") {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { if (it.length <= 10) nickname = it },
                    label = { Text("Apelido (máx 10 caracteres)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { /* lógica para salvar o apelido */ },
                    enabled = nickname.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Confirmar Apelido")
                }
            }

            // Seletor de Zona com dropdown
            Text("Meta de BPM", style = MaterialTheme.typography.titleMedium)
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { dropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(zoneDescriptions[selectedZone] ?: "Selecione uma zona: ")
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    zoneDescriptions.forEach { (zone, description) ->
                        DropdownMenuItem(
                            text = { Text(description) },
                            onClick = {
                                selectedZone = zone
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botão iniciar treino
            Button(
                onClick = { navController.navigate("countdown") },
                enabled = mode == "individual" || (mode == "aula" && nickname.isNotBlank()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Iniciar Atividade")
            }
        }
    }
}

@Composable
fun RadioButtonWithLabel(
    label: String,
    value: String,
    groupValue: String,
    onSelect: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 16.dp)
    ) {
        RadioButton(
            selected = groupValue == value,
            onClick = { onSelect(value) }
        )
        Text(label)
    }
}
