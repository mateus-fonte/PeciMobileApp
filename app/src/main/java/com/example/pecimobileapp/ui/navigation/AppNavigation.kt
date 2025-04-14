package com.example.pecimobileapp.ui.navigation

import androidx.compose.runtime.Composable
import com.example.pecimobileapp.viewmodels.BluetoothViewModel

@Composable
fun AppNavigation(viewModel: BluetoothViewModel) {
    // Usa o BottomNavScaffold que já contém todo o shell de navegação
    BottomNavScaffold(viewModel = viewModel)
}
