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
                        onValueChange = { viewModel.updateNome(it.ifBlank { null }) },
                        label = { Text("Nome") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Campo Sobrenome
                    OutlinedTextField(
                        value = viewModel.sobrenome ?: "",
                        onValueChange = {
                            if (it.length <= 20) viewModel.updateSobrenome(it.ifBlank { null })
                        },
                        label = { Text("Sobrenome (máx. 20 caracteres)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Campo Ano de Nascimento com feedback
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = anoNascimentoInput,
                            onValueChange = {
                                anoNascimentoInput = it
                                tentouValidar = false
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
                                    tint = Color(0xFF4CAF50)
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

                    if (tentouValidar && viewModel.isProfileIncomplete) {
                        Text(
                            text = "Preencha todos os campos obrigatórios corretamente.",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            val ano = anoNascimentoInput.toIntOrNull()
                            tentouValidar = true
                            viewModel.updateAnoNascimento(
                                if (ano != null && ano in 1920..anoAtual) ano else null
                            )
                        },
                        enabled = anoNascimentoInput.toIntOrNull() != null,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Validar Ano")
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
