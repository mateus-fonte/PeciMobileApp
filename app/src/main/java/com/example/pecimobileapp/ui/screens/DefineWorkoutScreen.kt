package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.pecimobileapp.ui.ProfileViewModel
import com.example.pecimobileapp.viewmodels.ProfileViewModelFactory
import com.example.pecimobileapp.ui.theme.*

fun encodeBase62(number: Long): String {
    val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    var num = number
    val result = StringBuilder()
    while (num > 0) {
        result.append(chars[(num % 62).toInt()])
        num /= 62
    }
    return result.reverse().toString().padStart(5, '0').takeLast(5)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefineWorkoutScreen(navController: NavController) {
    val context = LocalContext.current
    val profileViewModel: ProfileViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = ProfileViewModelFactory(context))

    var selectedZone by remember { mutableStateOf(1) }
    var workoutMode by remember { mutableStateOf("") } // "", "individual", "criar", "entrar"
    var groupNameInput by remember { mutableStateOf("") }
    var groupFullName by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val zones = listOf(
        Triple(1, "Z1 (Recuperação)", AerobicEndurance),
        Triple(2, "Z2 (Resistência)", AerobicPower),
        Triple(3, "Z3 (Tempo)", Threshold),
        Triple(4, "Z4 (Limiar anaeróbico)", AnaerobicEndurance),
        Triple(5, "Z5 (Capacidade máxima)", AnaerobicPower)
    )

    var expanded by remember { mutableStateOf(false) }
    val selectedZoneText = zones.firstOrNull { it.first == selectedZone }?.second ?: "Selecione uma zona"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text("Escolha a zona de treino:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = selectedZoneText,
                onValueChange = {},
                readOnly = true,
                label = { Text("Zona de treino") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                zones.forEach { (id, title, color) ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(14.dp).background(color))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(title)
                            }
                        },
                        onClick = {
                            selectedZone = id
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Modo de treino:", style = MaterialTheme.typography.titleMedium)

        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = workoutMode == "individual", onClick = {
                workoutMode = "individual"
                groupFullName = ""
            })
            Text("Individual")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = workoutMode == "criar", onClick = {
                workoutMode = "criar"
                groupFullName = ""
            })
            Text("Criar grupo")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = workoutMode == "entrar", onClick = {
                workoutMode = "entrar"
                groupFullName = ""
            })
            Text("Entrar em grupo")
        }

        if (workoutMode == "criar" || workoutMode == "entrar") {
            OutlinedTextField(
                value = groupNameInput,
                onValueChange = {
                    if (workoutMode == "criar") {
                        if (it.length <= 10 && it.all { c -> c.isLetter() }) {
                            groupNameInput = it
                            showError = false
                        } else {
                            showError = true
                        }
                    } else {
                        groupNameInput = it
                        showError = false
                    }
                },
                label = {
                    Text(if (workoutMode == "criar") "Nome do grupo (máx. 10 letras)" else "Grupo: ")
                },
                isError = showError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (workoutMode == "entrar" && groupNameInput.isNotEmpty() && !showError) {
                        groupFullName = groupNameInput.trim()
                        focusManager.clearFocus()
                    }
                }),
                modifier = Modifier.fillMaxWidth()
            )

            if (showError && workoutMode == "criar") {
                Text("Somente letras, até 10 caracteres.", color = MaterialTheme.colorScheme.error)
            }

            if (workoutMode == "criar") {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val timestamp = System.currentTimeMillis()
                        val code = encodeBase62(timestamp)
                        groupFullName = "${groupNameInput.trim()}$code"
                    },
                    enabled = groupNameInput.isNotEmpty() && !showError,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Criar grupo")
                }

                if (groupFullName.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nome oficial do grupo: $groupFullName",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val canStart = when (workoutMode) {
            "individual" -> true
            "criar", "entrar" -> groupFullName.isNotEmpty()
            else -> false
        }

        Button(
            onClick = {
                val groupParam = if (workoutMode == "individual") "false" else groupFullName
                navController.navigate("countdown?zone=$selectedZone&group=$groupParam")
            },
            enabled = canStart,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Iniciar treino")
        }
    }
}
