package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pecimobileapp.viewmodels.BluetoothViewModel

/**
 * Screen for entering WiFi credentials
 */
@Composable
fun WifiCredentialsScreen(viewModel: BluetoothViewModel, deviceName: String) {
    val ssid by viewModel.ssid.collectAsState()
    val password by viewModel.password.collectAsState()
    val isTransferInProgress by viewModel.isTransferInProgress.collectAsState()
    val isCheckingStatus by viewModel.isCheckingStatus.collectAsState()
    val piStatus by viewModel.piStatus.collectAsState()
    val wifiResult by viewModel.wifiResult.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Connected to: $deviceName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Enter WiFi Credentials",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = ssid,
                onValueChange = { viewModel.updateSsid(it) },
                label = { Text("WiFi SSID (Network Name)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.updatePassword(it) },
                label = { Text("WiFi Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { viewModel.sendWifiCredentialsAsFile() },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    enabled = ssid.isNotEmpty() && password.isNotEmpty() && !isTransferInProgress && !isCheckingStatus
                ) {
                    Text("Send to Raspberry Pi")
                }

                Button(
                    onClick = { viewModel.disconnectDevice() },
                    modifier = Modifier
                        .padding(start = 8.dp)
                ) {
                    Text("Disconnect")
                }
            }

            // Show transfer status if in progress
            if (isTransferInProgress) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Transfer in progress...")
                }
            }

            // Show status checking progress
            if (isCheckingStatus) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verificando status de conexão...")
                }
            }

            // NOVA SEÇÃO: Status permanente
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Status da Conexão",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    when {
                        isCheckingStatus -> {
                            Text(
                                text = "Verificando conexão...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        wifiResult is BluetoothViewModel.WifiResult.Success -> {
                            Text(
                                text = "Conectado com sucesso",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "IP: ${(wifiResult as BluetoothViewModel.WifiResult.Success).ipAddress}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (piStatus?.connectedNetwork != null) {
                                Text(
                                    text = "Rede: ${piStatus?.connectedNetwork}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        wifiResult is BluetoothViewModel.WifiResult.Error -> {
                            Text(
                                text = "Erro na conexão",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = (wifiResult as BluetoothViewModel.WifiResult.Error).message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        piStatus != null -> {
                            if (piStatus?.isConnected == true) {
                                Text(
                                    text = "Raspberry Pi conectado",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (piStatus?.ipAddress != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "IP: ${piStatus?.ipAddress}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (piStatus?.connectedNetwork != null) {
                                    Text(
                                        text = "Rede: ${piStatus?.connectedNetwork}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            } else {
                                Text(
                                    text = "Aguardando envio de credenciais",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = "Aguardando envio de credenciais",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Botão para verificar a conexão novamente
            if (!isCheckingStatus && !isTransferInProgress) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.checkConnectionStatus() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Verificar Conexão Novamente")
                }
            }
        }
    }
}