package com.example.pecimobileapp.models

import android.bluetooth.BluetoothDevice

/**
 * Data model representing a Bluetooth device with additional properties
 */
data class BluetoothDeviceModel(
    val device: BluetoothDevice,
    val name: String?,
    val address: String?,
    val isPaired: Boolean = false,
    val isSelected: Boolean = false
)