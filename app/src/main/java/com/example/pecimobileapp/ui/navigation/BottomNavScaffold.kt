package com.example.pecimobileapp.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pecimobileapp.ui.screens.*
import com.example.pecimobileapp.viewmodels.RealTimeViewModel
import com.example.pecimobileapp.ui.screens.WorkoutScreen



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavScaffold() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val realTimeModel: RealTimeViewModel = viewModel()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("The Heart Box", color = Color.White) },
                actions = {
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
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
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
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Setup") },
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
                    icon = { Icon(Icons.Default.Star, contentDescription = "Histórico") },
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
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = "main",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("main") {
                    MainScreen(realTimeModel = realTimeModel)
                }
                composable("setup") {
                    SetupScreen(
                        realTimeModel = realTimeModel,
                        navController = navController
                    )
                }
                composable("historico") {
                    HistoricoScreen(onBackClick = { navController.popBackStack() })
                }
                composable("profile") {
                    ProfileScreen(navToEdit = {
                        navController.navigate("profile_setup")
                    })
                }
                composable("profile_setup") {
                    ProfileSetupScreen(onSave = {
                        navController.popBackStack()  // volta para o ProfileScreen
                    })
                }

                composable("define_workout") {
                    DefineWorkoutScreen(navController)
                }
                composable("workout/{zone}/{nickname}") { backStackEntry ->
                    val zone = backStackEntry.arguments?.getString("zone")?.toIntOrNull() ?: 1
                    val nickname = backStackEntry.arguments?.getString("nickname") ?: "YOU"

                    WorkoutScreen(
                        navController = navController,
                        selectedZone = zone,
                        nickname = nickname,
                        heartRate = 145,
                        temperature = 36.7f,
                        execution = 92.5f,
                        onStop = { navController.popBackStack() }
                    )
                }


                composable("countdown") {
                    CountdownScreen(navController = navController)
                }





            }
        }
    }
}
