package com.example.pecimobileapp.ui.navigation

import androidx.compose.runtime.Composable
import com.example.pecimobileapp.viewmodels.BluetoothViewModel
import com.example.pecimobileapp.viewmodels.WebSocketViewModel

@Composable
fun AppNavigation(bluetoothViewModel: BluetoothViewModel, webSocketViewModel: WebSocketViewModel) {
    // Usa o BottomNavScaffold que já contém toda a navegação de abas
    BottomNavScaffold(bluetoothViewModel, webSocketViewModel)
}
