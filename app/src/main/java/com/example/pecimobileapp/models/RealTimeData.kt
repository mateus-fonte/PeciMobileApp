package com.example.pecimobileapp.models

data class RealTimeData(
    val timestamp: Long,
    val value: Float,
    val averageTemperature: Float,
    val heartRate: Int?
)
