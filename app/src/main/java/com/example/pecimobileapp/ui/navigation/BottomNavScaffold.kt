package com.example.pecimobileapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pecimobileapp.ui.screens.HistoricoScreen
import com.example.pecimobileapp.ui.screens.MainScreen
import com.example.pecimobileapp.ui.screens.ProfileScreen
import com.example.pecimobileapp.ui.screens.SetupScreen
import com.example.pecimobileapp.viewmodels.BluetoothViewModel
import com.example.pecimobileapp.viewmodels.RealTimeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavScaffold(viewModel: BluetoothViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("The Heart Box", color = Color.White) },
                actions = {
                    // Navega para a tela de perfil
                    IconButton(onClick = { navController.navigate("profile") }) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Perfil",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color(0xFF8A2BE2))
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF8A2BE2)) {
                NavigationBarItem(
                    icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = currentRoute == "main",
                    onClick = {
                        navController.navigate("main") {
                            popUpTo("main") { inclusive = true }
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        unselectedIconColor = Color.LightGray,
                        selectedTextColor = Color.White,
                        unselectedTextColor = Color.LightGray
                    )
                )
                NavigationBarItem(
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Setup") },
                    label = { Text("Setup") },
                    selected = currentRoute == "setup",
                    onClick = { navController.navigate("setup") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        unselectedIconColor = Color.LightGray,
                        selectedTextColor = Color.White,
                        unselectedTextColor = Color.LightGray
                    )
                )
                NavigationBarItem(
                    icon = { Icon(imageVector = Icons.Default.Star, contentDescription = "Histórico") },
                    label = { Text("Histórico") },
                    selected = currentRoute == "historico",
                    onClick = { navController.navigate("historico") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        unselectedIconColor = Color.LightGray,
                        selectedTextColor = Color.White,
                        unselectedTextColor = Color.LightGray
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = "main",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("main") {
                    // Tela inicial: exibe explicação de como usar a app
                    MainScreen(realTimeModel = RealTimeViewModel())
                }
                composable("setup") {
                    // Tela de Setup: configurações atuais (antigo conteúdo da MainScreen)
                    SetupScreen(viewModel = viewModel)
                }
                composable("historico") {
                    HistoricoScreen(onBackClick = { navController.navigate("main") })
                }
                composable("profile") {
                    ProfileScreen()
                }
            }
        }
    }
}
