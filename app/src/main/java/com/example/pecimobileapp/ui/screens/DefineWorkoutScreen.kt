import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import com.example.pecimobileapp.ui.*

@Composable
fun DefineWorkoutScreen(navController: NavController) {
    val profileViewModel: ProfileViewModel = viewModel()

    var selectedZone by remember { mutableStateOf(1) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var tipoTreino by remember { mutableStateOf("Individual") }
    var nomeGrupo by remember { mutableStateOf("") }

    val zonas = profileViewModel.zonas
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Definir Treino", style = MaterialTheme.typography.headlineMedium)

            // Seletor Individual ou Grupo
            Text("Tipo de treino", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf("Individual", "Em grupo").forEach { tipo ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = tipoTreino == tipo,
                            onClick = { tipoTreino = tipo }
                        )
                        Text(tipo)
                    }
                }
            }

            // Campo para nome do grupo, só se for em grupo
            if (tipoTreino == "Em grupo") {
                OutlinedTextField(
                    value = nomeGrupo,
                    onValueChange = {
                        if (it.length <= 10) nomeGrupo = it
                    },
                    label = { Text("Nome do grupo (máx. 10 carateres)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Zona de BPM
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
                    if (tipoTreino == "Em grupo") {
                        profileViewModel.identificador = nomeGrupo
                    }
                    navController.navigate("workout/${selectedZone}/${profileViewModel.identificador}")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Iniciar Atividade")
            }
        }
    }
}
