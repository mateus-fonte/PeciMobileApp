package com.example.pecimobileapp.network

import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class MqttManagerImpl : MqttManager {

    private val serverUri = "tcp://broker.hivemq.com:1883"
    private val clientId = MqttClient.generateClientId()
    private val mqttClient = MqttClient(serverUri, clientId, MemoryPersistence())

    private val callbacks = ConcurrentHashMap<String, (String) -> Unit>()

    init {
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
        }

        mqttClient.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                println("⚠️ Conexão MQTT perdida: ${cause?.message}")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (topic != null && message != null) {
                    callbacks[topic]?.invoke(message.toString())
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // opcional
            }
        })

        thread {
            try {
                mqttClient.connect(options)
                println("✅ MQTT conectado com sucesso")
            } catch (e: Exception) {
                println("❌ Erro ao conectar MQTT: ${e.message}")
            }
        }
    }

    override fun publish(topic: String, message: String) {
        if (!mqttClient.isConnected) return
        val mqttMessage = MqttMessage(message.toByteArray())
        mqttClient.publish(topic, mqttMessage)
    }

    override fun subscribe(topic: String, callback: (String) -> Unit) {
        callbacks[topic] = callback
        if (mqttClient.isConnected) {
            mqttClient.subscribe(topic)
        } else {
            thread {
                while (!mqttClient.isConnected) Thread.sleep(100)
                mqttClient.subscribe(topic)
            }
        }
    }
}