package com.example.pecimobileapp.models

data class RaspberryPiStatus (
    val deviceName: String,
    val address: String,
    val isConnected: Boolean = false,
    val ipAddress: String? = null,
    val connectedNetwork: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()

)