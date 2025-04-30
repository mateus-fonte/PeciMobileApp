package com.example.pecimobileapp.network

import android.content.Context
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class MqttManagerImpl(context: Context) : MqttManager {

    private val serverUri = "tcp://broker.hivemq.com:1883"
    private val clientId = MqttClient.generateClientId()
    private val mqttClient = MqttAndroidClient(context, serverUri, clientId)

    init {
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
        }

        mqttClient.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                println("✅ MQTT conectado com sucesso")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                println("❌ Erro ao conectar MQTT: ${exception?.message}")
            }
        })
    }

    override fun publish(topic: String, message: String) {
        if (!mqttClient.isConnected) return
        val mqttMessage = MqttMessage(message.toByteArray())
        mqttClient.publish(topic, mqttMessage)
    }

    override fun subscribe(topic: String, callback: (String) -> Unit) {
        mqttClient.subscribe(topic, 0) { _, msg ->
            callback(msg.toString())
        }
    }
}