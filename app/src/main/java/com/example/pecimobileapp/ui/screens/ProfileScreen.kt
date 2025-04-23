package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pecimobileapp.ui.*
import androidx.compose.ui.Alignment



@Composable
fun ProfileScreen(navToEdit: () -> Unit = {}) {
    val viewModel: ProfileViewModel = viewModel()

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
            Text("Perfil", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ProfileField("Nome", viewModel.nome)
                    ProfileField("Apelido", viewModel.apelido)
                    ProfileField("Peso", "${viewModel.peso} kg")
                    ProfileField("Ano de Nascimento", "${viewModel.anoNascimento}")
                    ProfileField("FC Máxima", "${viewModel.fcMax} bpm")
                    ProfileField("Idade", "${viewModel.idade} anos")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Zonas de Frequência Cardíaca", style = MaterialTheme.typography.titleMedium)
            viewModel.zonas.forEach { (nome, faixa) ->
                Text("$nome: ${faixa.first} - ${faixa.last} bpm", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = navToEdit,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Editar Perfil")
            }
        }
    }
}

@Composable
fun ProfileField(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
