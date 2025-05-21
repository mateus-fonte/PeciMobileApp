package com.example.pecimobileapp.repositories

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.pecimobileapp.models.BluetoothDeviceModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Repository responsible for Bluetooth device discovery and management
 */
class BluetoothRepository(private val context: Context) {

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDeviceModel>> = _discoveredDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDeviceModel>> = _pairedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var selectedDeviceAddress: String? = null

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let {
                        val deviceModel = createBluetoothDeviceModel(it)

                        // Check if device already exists in the list
                        val currentList = _discoveredDevices.value
                        if (!currentList.any { it.address == deviceModel.address }) {
                            _discoveredDevices.update { it + deviceModel }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    updatePairedDevices()
                }
            }
        }
    }

    init {
        registerBluetoothReceiver()
        updatePairedDevices()
    }

    fun startDiscovery() {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth adapter is null")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "BLUETOOTH_SCAN permission not granted")
                return
            }
        }

        // Clear previous list
        _discoveredDevices.value = emptyList()

        // Cancel any ongoing discovery
        bluetoothAdapter.cancelDiscovery()

        // Start new discovery
        val started = bluetoothAdapter.startDiscovery()
        _isScanning.value = started

        if (started) {
            Log.d(TAG, "Bluetooth discovery started")
        } else {
            Log.e(TAG, "Failed to start Bluetooth discovery")
        }
    }

    fun stopDiscovery() {
        if (bluetoothAdapter == null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        bluetoothAdapter.cancelDiscovery()
        _isScanning.value = false
        Log.d(TAG, "Bluetooth discovery stopped")
    }

    fun updatePairedDevices() {
        if (bluetoothAdapter == null) return

        val pairedDevicesList = mutableListOf<BluetoothDeviceModel>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                _pairedDevices.value = emptyList()
                return
            }
        }

        bluetoothAdapter.bondedDevices?.forEach { device ->
            val deviceModel = createBluetoothDeviceModel(device, isPaired = true)
            pairedDevicesList.add(deviceModel)
        }

        _pairedDevices.value = pairedDevicesList
    }

    fun pairDevice(device: BluetoothDevice): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }

        // Stop discovery during pairing
        stopDiscovery()

        return try {
            device.createBond()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error pairing with device", e)
            false
        }
    }

    fun selectDevice(address: String) {
        selectedDeviceAddress = address

        // Update paired devices selection state
        _pairedDevices.update { currentList ->
            currentList.map { deviceModel ->
                deviceModel.copy(isSelected = deviceModel.address == address)
            }
        }

        // Update discovered devices selection state
        _discoveredDevices.update { currentList ->
            currentList.map { deviceModel ->
                deviceModel.copy(isSelected = deviceModel.address == address)
            }
        }
    }

    fun getSelectedDevice(): BluetoothDevice? {
        val address = selectedDeviceAddress ?: return null

        // First check paired devices
        _pairedDevices.value.firstOrNull { it.address == address }?.let {
            return it.device
        }

        // Then check discovered devices
        _discoveredDevices.value.firstOrNull { it.address == address }?.let {
            return it.device
        }

        return null
    }

    fun clearSelection() {
        selectedDeviceAddress = null

        // Clear selection in paired devices
        _pairedDevices.update { currentList ->
            currentList.map { deviceModel ->
                deviceModel.copy(isSelected = false)
            }
        }

        // Clear selection in discovered devices
        _discoveredDevices.update { currentList ->
            currentList.map { deviceModel ->
                deviceModel.copy(isSelected = false)
            }
        }
    }

    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun getDeviceName(device: BluetoothDevice): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                device.name
            } else {
                null
            }
        } else {
            @Suppress("DEPRECATION")
            device.name
        }
    }

    fun getDeviceAddress(device: BluetoothDevice): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                device.address
            } else {
                null
            }
        } else {
            @Suppress("DEPRECATION")
            device.address
        }
    }

    private fun createBluetoothDeviceModel(device: BluetoothDevice, isPaired: Boolean = false): BluetoothDeviceModel {
        val name = getDeviceName(device)
        val address = getDeviceAddress(device)
        val isSelected = address == selectedDeviceAddress

        return BluetoothDeviceModel(
            device = device,
            name = name,
            address = address,
            isPaired = isPaired,
            isSelected = isSelected
        )
    }

    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }

        context.registerReceiver(receiver, filter)
    }

    fun unregisterReceiver() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Receiver wasn't registered
            Log.e(TAG, "Receiver wasn't registered", e)
        }
    }

    companion object {
        private const val TAG = "BluetoothRepository"
    }


}