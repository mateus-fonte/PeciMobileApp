package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pecimobileapp.viewmodels.ProfileViewModel
import com.example.pecimobileapp.viewmodels.ProfileViewModelFactory
import java.util.*

@Composable
fun ProfileSetupScreen(onSave: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory(context))

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
                    OutlinedTextField(
                        value = viewModel.nome,
                        onValueChange = { viewModel.updateNome(it) },
                        label = { Text("Nome") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = viewModel.identificador,
                        onValueChange = {
                            if (it.length <= 10) viewModel.identificador = it

                            if (it.length <= 10) viewModel.updateApelido(it)

                        },
                        label = { Text("Identificador (máx. 10 caracteres)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = viewModel.peso.toString(),
                        onValueChange = {
                            val peso = it.toFloatOrNull()
                            if (peso != null && peso in 30f..200f) {
                                viewModel.updatePeso(peso)
                            }
                        },
                        label = { Text("Peso (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = viewModel.anoNascimento.toString(),
                        onValueChange = {
                            val ano = it.toIntOrNull()
                            val anoAtual = Calendar.getInstance().get(Calendar.YEAR)
                            if (ano != null && ano in 1920..anoAtual) {
                                viewModel.updateAnoNascimento(ano)
                            }
                        },
                        label = { Text("Ano de Nascimento") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

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
                onClick = onSave,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Salvar Perfil")
            }
        }
    }
}
