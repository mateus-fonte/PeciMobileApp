package com.example.pecimobileapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pecimobileapp.ui.screens.MainScreen
import com.example.pecimobileapp.ui.screens.GrafanaScreen
import com.example.pecimobileapp.ui.theme.PeciMobileAppTheme
import com.example.pecimobileapp.viewmodels.BluetoothViewModel

class MainActivity : ComponentActivity() {
    private val bluetoothViewModel: BluetoothViewModel by viewModels()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val requestBluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // All necessary permissions granted, check if Bluetooth is enabled
            ensureBluetoothIsEnabled()
        } else {
            // Some permissions were denied
            Toast.makeText(
                this,
                "Bluetooth permissions are required for this app to function properly",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Bluetooth was enabled by the user
            bluetoothViewModel.updatePairedDevices()
        } else {
            // User refused to enable Bluetooth
            Toast.makeText(
                this,
                "Bluetooth is required for this app to function properly",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PeciMobileAppTheme {
                // Crie o NavController
                val navController = rememberNavController()

                // Defina o NavHost
                NavHost(
                    navController = navController,
                    startDestination = "main_screen", // Tela inicial
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Defina as telas e suas rotas
                    composable("main_screen") {
                        MainScreen(navController, bluetoothViewModel) // Passa o navController para a MainScreen
                    }
                    composable("grafana_screen") {
                        GrafanaScreen() // Aqui você pode adicionar o conteúdo do Grafana
                    }
                }
            }
        }

        // Check if Bluetooth is available
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Toast.makeText(this, "Bluetooth is not available on this device", Toast.LENGTH_LONG).show()
            return
        }

        // Check and request Bluetooth permissions
        checkBluetoothPermissions()
    }

    private fun checkBluetoothPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Check necessary permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires BLUETOOTH_SCAN and BLUETOOTH_CONNECT
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            // Older versions need BLUETOOTH_ADMIN and location permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        // Request permissions if needed
        if (permissionsToRequest.isNotEmpty()) {
            requestBluetoothPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Permissions already granted, check if Bluetooth is enabled
            ensureBluetoothIsEnabled()
        }
    }

    private fun ensureBluetoothIsEnabled() {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            // Bluetooth is already enabled, update paired devices
            bluetoothViewModel.updatePairedDevices()
        }
    }
}
