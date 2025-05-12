package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pecimobileapp.ui.ProfileViewModel
import com.example.pecimobileapp.viewmodels.ProfileViewModelFactory
import java.util.*

@Composable
fun ProfileSetupScreen(onSave: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory(context))

    var anoNascimentoInput by remember { mutableStateOf(viewModel.anoNascimento?.toString() ?: "") }
    var tentouValidar by remember { mutableStateOf(false) }

    val anoAtual = Calendar.getInstance().get(Calendar.YEAR)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Editar Perfil", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Campo Nome
                    OutlinedTextField(
                        value = viewModel.nome ?: "",
                        onValueChange = {
                            val cleaned = it.filter { c -> c.isLetter() }.take(20)
                            viewModel.updateNome(cleaned.ifBlank { null })
                        },
                        label = { Text("Nome") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Campo Sobrenome
                    OutlinedTextField(
                        value = viewModel.sobrenome ?: "",
                        onValueChange = {
                            val cleaned = it.filter { c -> c.isLetter() }.take(20)
                            viewModel.updateSobrenome(cleaned.ifBlank { null })
                        },
                        label = { Text("Apelido") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Campo Ano de Nascimento com validação automática
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = anoNascimentoInput,
                            onValueChange = {
                                anoNascimentoInput = it
                                tentouValidar = true // já tenta validar assim que digita
                                val ano = it.toIntOrNull()
                                viewModel.updateAnoNascimento(
                                    if (ano != null && ano in 1920..anoAtual) ano else null
                                )
                            },
                            label = { Text("Ano de Nascimento") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            isError = tentouValidar && viewModel.anoNascimento == null
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (tentouValidar) {
                            if (!viewModel.isProfileIncomplete) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Perfil válido",
                                    tint = Color(0xFF4CAF50) // verde
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Perfil incompleto",
                                    tint = Color.Red
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Campo FC Máxima
                    OutlinedTextField(
                        value = viewModel.fcMaxManual?.toString() ?: "",
                        onValueChange = {
                            viewModel.updateFcMaxManual(it.toIntOrNull())
                        },
                        label = { Text("FC Máxima (opcional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.generateUserIdIfNeeded()
                    onSave()
                },
                enabled = !viewModel.isProfileIncomplete,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Salvar Perfil")
            }
        }
    }
}
