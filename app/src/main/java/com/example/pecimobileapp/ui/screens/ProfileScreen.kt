package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            Text("Perfil do Utilizador", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
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
                    ProfileField(" ID Único", viewModel.userId ?: "(não gerado)")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Zonas de Frequência Cardíaca",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Mapa de cores por zona
                    val zoneColors = mapOf(
                        0 to Color(0xFFBDBDBD),  // Z0 – Fora de Zona (<50%)
                        1 to Color(0xFF5E9ED6),  // Z1 – Recuperação
                        2 to Color(0xFF8AC7AB),  // Z2 – Resistência Aeróbica
                        3 to Color(0xFFFFD54F),  // Z3 – Limiar
                        4 to Color(0xFFFFB74D),  // Z4 – Anaeróbico Moderado
                        5 to Color(0xFFE53935)   // Z5 – Alta Intensidade
                    )

                    if (viewModel.zoneRange.isNotEmpty()) {
                        viewModel.zoneRange.forEachIndexed { index, (nome, faixa) ->
                            val cor = zoneColors[index + 1] ?: Color.LightGray

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = cor)
                            ) {
                                Text(
                                    text = "$nome: ${faixa.first} – ${faixa.last} bpm",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Black,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    } else {
                        Text("Zonas indisponíveis.", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = navToEdit,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = Color.White
                    )
                }

                Button(
                    onClick = { viewModel.clearProfile() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Limpar",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileField(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
