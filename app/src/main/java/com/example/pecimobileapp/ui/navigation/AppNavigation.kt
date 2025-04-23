package com.example.pecimobileapp.ui.navigation

import androidx.compose.runtime.Composable
import com.example.pecimobileapp.viewmodels.BluetoothViewModel

@Composable
fun AppNavigation(bluetoothViewModel: BluetoothViewModel) {
    // Usa o BottomNavScaffold que já contém toda a navegação de abas
    BottomNavScaffold()
}
