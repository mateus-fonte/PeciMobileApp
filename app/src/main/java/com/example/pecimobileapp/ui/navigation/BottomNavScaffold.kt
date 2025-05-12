package com.example.pecimobileapp.ui.navigation

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.pecimobileapp.mqtt.MqttManager
import com.example.pecimobileapp.ui.ProfileViewModel
import com.example.pecimobileapp.ui.screens.*
import com.example.pecimobileapp.viewmodels.*

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavScaffold(
    bluetoothViewModel: BluetoothViewModel,
    webSocketViewModel: WebSocketViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val realTimeViewModel: RealTimeViewModel = viewModel()
    val context = LocalContext.current
    val profileViewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory(context))

    var showLeaveWorkoutDialog by remember { mutableStateOf(false) }
    var pendingNavigationRoute by remember { mutableStateOf<String?>(null) }

    val isInWorkout = currentRoute?.startsWith("workout") == true

    // Sincroniza zonas do perfil com RealTimeViewModel
    LaunchedEffect(profileViewModel.zoneRange, profileViewModel.userId) {
        val zonasAtualizadas = profileViewModel.zoneRange
        val userId = profileViewModel.userId ?: "default_user"
        if (zonasAtualizadas.isNotEmpty()) {
            realTimeViewModel.setWorkoutParameters(
                zone = realTimeViewModel.selectedZone.value,
                zonasList = zonasAtualizadas,
                group = realTimeViewModel.groupId.value,
                user = userId,
                exercise = realTimeViewModel.exerciseId.value
            )
        }
    }

    fun handleNavigation(destination: String) {
        if (isInWorkout) {
            showLeaveWorkoutDialog = true
            pendingNavigationRoute = destination
        } else {
            navController.navigate(destination)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("The Heart Box", color = Color.White) },
                actions = {
                    IconButton(onClick = { handleNavigation("profile") }) {
                        Icon(Icons.Default.Person, contentDescription = "Perfil", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color(0xFF8A2BE2))
            )
        },
        bottomBar = {
            val isMainSelected = currentRoute == "main"

            Box {
                NavigationBar(
                    containerColor = Color(0xFF8A2BE2),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Setup") },
                        label = { Text("Setup") },
                        selected = currentRoute == "setup",
                        onClick = { handleNavigation("setup") },
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
                        onClick = { handleNavigation("historico") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            unselectedIconColor = Color.LightGray,
                            selectedTextColor = Color.White,
                            unselectedTextColor = Color.LightGray
                        )
                    )
                }

                FloatingActionButton(
                    onClick = {
                        if (isInWorkout) {
                            showLeaveWorkoutDialog = true
                            pendingNavigationRoute = "main"
                        } else {
                            navController.navigate("main") {
                                popUpTo("main") { inclusive = true }
                            }
                        }
                    },
                    containerColor = if (isMainSelected) Color.White else Color(0xFF8A2BE2),
                    contentColor = if (isMainSelected) Color(0xFF8A2BE2) else Color.White,
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.TopCenter)
                        .offset(y = (-24).dp)
                        .zIndex(1f)
                ) {
                    Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(32.dp))
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
                composable("setup") {
                    SetupScreen(realTimeViewModel, navController, webSocketViewModel)
                }
                composable("main") {
                    MainScreen(realTimeViewModel, webSocketViewModel, navController)
                }
                composable("websocket") {
                    WebSocketScreen(webSocketViewModel)
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
                        navController.popBackStack()
                    })
                }
                composable("define_workout") {
                    DefineWorkoutScreen(navController)
                }
                composable("countdown") {
                    CountdownScreen(
                        navController = navController,
                        viewModel = realTimeViewModel,
                        onCountdownFinished = { /* handled in screen */ }
                    )
                }
                composable(
                    route = "workout?selectedZone={selectedZone}&groupId={groupId}&userId={userId}&exerciseId={exerciseId}",
                    arguments = listOf(
                        navArgument("selectedZone") { type = NavType.IntType; defaultValue = 1 },
                        navArgument("groupId") { type = NavType.StringType; nullable = true },
                        navArgument("userId") { type = NavType.StringType; defaultValue = "default_user" },
                        navArgument("exerciseId") { type = NavType.StringType; defaultValue = "ex-teste" }
                    )
                ) { backStackEntry ->
                    val selectedZone = backStackEntry.arguments?.getInt("selectedZone") ?: 1
                    val groupId = backStackEntry.arguments?.getString("groupId")?.takeIf { it.isNotEmpty() }
                    val userId = backStackEntry.arguments?.getString("userId") ?: "default_user"
                    val exerciseId = backStackEntry.arguments?.getString("exerciseId") ?: "ex-teste"

                    Log.d("BottomNavScaffold", "WorkoutScreen args: zone=$selectedZone, group=$groupId, user=$userId, exercise=$exerciseId")

                    WorkoutScreen(
                        navController = navController,
                        selectedZone = selectedZone,
                        mqttManager = MqttManager,
                        groupId = groupId,
                        realTimeViewModel = realTimeViewModel,
                        wsViewModel = webSocketViewModel,
                        onStop = {
                            navController.popBackStack("define_workout", inclusive = false)
                        },
                        userId = userId,
                        exerciseId = exerciseId
                    )
                }
            }

            if (showLeaveWorkoutDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showLeaveWorkoutDialog = false
                        pendingNavigationRoute = null
                    },
                    title = { Text("Sair do Treino") },
                    text = { Text("Ao sair da tela de treino, o envio de dados será interrompido. Deseja continuar?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showLeaveWorkoutDialog = false
                            pendingNavigationRoute?.let {
                                realTimeViewModel.stopActivity()
                                navController.navigate(it) {
                                    popUpTo("main") { inclusive = false }
                                }
                            }
                            pendingNavigationRoute = null
                        }) {
                            Text("Sim")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showLeaveWorkoutDialog = false
                            pendingNavigationRoute = null
                        }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
        }
    }
}
