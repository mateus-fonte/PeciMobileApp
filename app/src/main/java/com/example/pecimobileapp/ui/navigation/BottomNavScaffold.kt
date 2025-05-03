package com.example.pecimobileapp.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.pecimobileapp.mqtt.MqttManager
import com.example.pecimobileapp.ui.screens.*
import com.example.pecimobileapp.viewmodels.RealTimeViewModel
import com.example.pecimobileapp.viewmodels.WebSocketViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavScaffold() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val vm: RealTimeViewModel = viewModel()
    val wsViewModel: WebSocketViewModel = viewModel()

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
                    icon = { Icon(Icons.Default.WifiTethering, contentDescription = "ESP32") },
                    label = { Text("ESP32") },
                    selected = currentRoute == "websocket",
                    onClick = { navController.navigate("websocket") },
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
                composable("setup") { SetupScreen(vm, navController, wsViewModel) }
                composable("main") { MainScreen(vm, wsViewModel) }
                composable("websocket") { WebSocketScreen(wsViewModel) }
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
                        navController.popBackStack()
                    })
                }
                composable("define_workout") {
                    DefineWorkoutScreen(navController)
                }
                composable(
                    route = "workout/{zone}?group={group}",
                    arguments = listOf(
                        navArgument("zone") { type = NavType.IntType },
                        navArgument("group") { type = NavType.StringType; defaultValue = "false" }
                    )
                ) { backStackEntry ->
                    val zone = backStackEntry.arguments?.getInt("zone") ?: 1
                    val groupParam = backStackEntry.arguments?.getString("group")
                    val isGroup = groupParam != "false"
                    val groupName = if (isGroup) groupParam else null

                    val mqttManager = if (isGroup) MqttManager else null

                    WorkoutScreen(
                        navController = navController,
                        selectedZone = zone,
                        isGroup = isGroup,
                        mqttManager = mqttManager,
                        groupName = groupName,
                        onStop = { navController.popBackStack() },
                        realTimeViewModel = vm
                    )
                }
                composable(
                    route = "countdown?zone={zone}&group={group}",
                    arguments = listOf(
                        navArgument("zone") { type = NavType.IntType; defaultValue = 1 },
                        navArgument("group") { type = NavType.StringType; defaultValue = "false" }
                    )
                ) { backStackEntry ->
                    val zone = backStackEntry.arguments?.getInt("zone") ?: 1
                    val group = backStackEntry.arguments?.getString("group") ?: "false"

                    CountdownScreen(
                        navController = navController,
                        onCountdownFinished = {
                            navController.navigate("workout/$zone?group=$group") {
                                popUpTo("countdown") { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }
}
