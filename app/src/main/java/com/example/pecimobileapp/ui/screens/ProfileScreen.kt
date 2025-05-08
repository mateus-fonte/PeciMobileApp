package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pecimobileapp.ui.ProfileViewModel
import com.example.pecimobileapp.viewmodels.*

@Composable
fun ProfileScreen(navToEdit: () -> Unit = {}) {
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
            Text("Perfil", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ProfileField("Nome", viewModel.nome ?: "(não preenchido)")
                    ProfileField("Sobrenome", viewModel.sobrenome ?: "(não preenchido)")
                    ProfileField(
                        "Ano de Nascimento",
                        viewModel.anoNascimento?.toString() ?: "(não preenchido)"
                    )
                    ProfileField(
                        "FC Máxima",
                        viewModel.fcMax?.toString()?.plus(" bpm") ?: "(indisponível)"
                    )
                    ProfileField(
                        "Idade",
                        viewModel.idade?.toString()?.plus(" anos") ?: "(indisponível)"
                    )
                    ProfileField("Identificador único (ID)", viewModel.userId ?: "(não gerado)")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Zonas de Frequência Cardíaca", style = MaterialTheme.typography.titleMedium)
            if (viewModel.zonas.isNotEmpty()) {
                viewModel.zonas.forEach { (nome, faixa) ->
                    Text(
                        "$nome: ${faixa.first} - ${faixa.last} bpm",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Text("Zonas indisponíveis.", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = navToEdit) {
                    Text("Editar Perfil")
                }

                Button(onClick = { viewModel.clearProfile() }) {
                    Text("Limpar Perfil")
                }
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
