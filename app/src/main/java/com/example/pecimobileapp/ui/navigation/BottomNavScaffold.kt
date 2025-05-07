package com.example.pecimobileapp.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.pecimobileapp.mqtt.MqttManager
import com.example.pecimobileapp.ui.screens.*
import com.example.pecimobileapp.viewmodels.RealTimeViewModel
import com.example.pecimobileapp.viewmodels.WebSocketViewModel
import com.example.pecimobileapp.viewmodels.BluetoothViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavScaffold(
    bluetoothViewModel: BluetoothViewModel,
    webSocketViewModel: WebSocketViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val vm: RealTimeViewModel = viewModel()

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
            val isMainSelected = currentRoute == "main"

            Box {
                NavigationBar(containerColor = Color(0xFF8A2BE2), modifier = Modifier.height(56.dp)) {
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

                    Spacer(modifier = Modifier.weight(1f, true))

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

                // Botão Home Central com estilo igual aos outros
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    color = if (isMainSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    navController.navigate("main") {
                                        popUpTo("main") { inclusive = true }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = "Home",
                                tint = if (isMainSelected) Color(0xFF8A2BE2) else Color.LightGray,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                    }
                }
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
                composable("setup") { SetupScreen(vm, navController, webSocketViewModel) }
                composable("main") { MainScreen(vm, webSocketViewModel, navController) }
                composable("websocket") { WebSocketScreen(webSocketViewModel) }
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
