package com.example.pecimobileapp.mqtt

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*

object MqttManager {
    private const val TAG = "MqttManager"
    private const val SERVER_IP = "48.217.187.110"
    private const val PORT = 1883
    private val CLIENT_ID = "AndroidClient_${UUID.randomUUID()}"

    private val mqttClient = MqttClient.builder()
        .useMqttVersion5()
        .serverHost(SERVER_IP)
        .serverPort(PORT)
        .identifier(CLIENT_ID)
        .automaticReconnect()
        .initialDelay(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        .maxDelay(10_000, java.util.concurrent.TimeUnit.MILLISECONDS)
        .applyAutomaticReconnect()
        .buildBlocking()

    private var isConnected = false

    init {
        connect()
    }

    fun connect() {
        if (!isConnected) {
            try {
                mqttClient.connect()
                isConnected = true
                Log.d(TAG, "Conectado ao MQTT Broker")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao conectar ao MQTT Broker", e)
                isConnected = false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun publishSensorData(
        groupId: String,
        userId: String,
        exerciseId: String,
        source: String,
        value: Number
    ) {
        connect()
        val timestamp = Instant.now().toEpochMilli()
        val payload = """
            {
                "group_id": "$groupId",
                "exercise_id": "$exerciseId",
                "user_uid": "$userId",
                "source": "$source",
                "value": $value,
                "timestamp": $timestamp
            }
        """.trimIndent()

        val groupTopic = "/group/$groupId/data"
        val userTopic = "/user/$userId/data"

        try {
            mqttClient.publishWith().topic(groupTopic)
                .payload(payload.toByteArray(StandardCharsets.UTF_8))
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
            mqttClient.publishWith().topic(userTopic)
                .payload(payload.toByteArray(StandardCharsets.UTF_8))
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()

            Log.d(TAG, "Publicado em $groupTopic e $userTopic: $payload")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao publicar MQTT", e)
        }
    }
}
