package com.example.pecimobileapp.models

import java.util.Date
import kotlin.time.Duration

data class HistoryRecord(
    val date: String,
    val duration: Int,
    val avgHeartRate: Double,
    val avgTemperature: Double
)