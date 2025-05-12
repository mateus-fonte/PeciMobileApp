package com.example.pecimobileapp

import android.app.Application
import com.example.pecimobileapp.ble.BleManagerProvider
import com.example.pecimobileapp.devices.DevicesManager

/**
 * Main application class
 */
class BluetoothApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize BleManagerProvider
        BleManagerProvider.initialize(this)
        
        // Initialize DevicesManager with the BleManager instance
        DevicesManager.initialize(this, BleManagerProvider.getInstance())
    }
}