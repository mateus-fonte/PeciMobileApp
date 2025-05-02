package com.example.pecimobileapp.network

interface MqttManager {
    fun publish(topic: String, message: String)
    fun subscribe(topic: String, callback: (String) -> Unit)
}
