package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pecimobileapp.ui.*


@Composable
fun ProfileScreen() {
    val viewModel: ProfileViewModel = viewModel()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Perfil", style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = viewModel.nome,
                onValueChange = { viewModel.nome = it },
                label = { Text("Nome") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = viewModel.apelido,
                onValueChange = { viewModel.apelido = it },
                label = { Text("Apelido") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = viewModel.peso.toString(),
                onValueChange = {
                    val peso = it.toFloatOrNull()
                    if (peso != null) viewModel.peso = peso
                },
                label = { Text("Peso (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = viewModel.anoNascimento.toString(),
                onValueChange = {
                    val ano = it.toIntOrNull()
                    if (ano != null) viewModel.anoNascimento = ano
                },
                label = { Text("Ano de Nascimento") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = viewModel.fcMaxManual?.toString() ?: "",
                onValueChange = {
                    viewModel.fcMaxManual = it.toIntOrNull()
                },
                label = { Text("FC Máxima (opcional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Idade: ${viewModel.idade} anos", style = MaterialTheme.typography.bodyLarge)
            Text("FC Máxima: ${viewModel.fcMax} bpm", style = MaterialTheme.typography.bodyLarge)

            Spacer(modifier = Modifier.height(16.dp))
            Text("Zonas:", style = MaterialTheme.typography.titleMedium)
            viewModel.zonas.forEach { (nome, faixa) ->
                Text("$nome: ${faixa.first} - ${faixa.last} bpm", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
