// MqttManager.kt
package com.example.pecimobileapp.mqtt

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import org.json.JSONObject
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

    fun publish(topic: String, message: String) {
        try {
            mqttClient.publishWith()
                .topic(topic)
                .payload(message.toByteArray(StandardCharsets.UTF_8))
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
            Log.d(TAG, "Publicado em $topic: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao publicar em $topic", e)
        }
    }

    fun subscribe(topic: String, onMessageReceived: (String) -> Unit) {
        try {
            mqttClient.toAsync().subscribeWith()
                .topicFilter(topic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .callback { publish ->
                    val payload = publish.payload.orElse(null)?.let {
                        String(it.array(), StandardCharsets.UTF_8)
                    }
                    if (payload != null) {
                        onMessageReceived(payload)
                    }
                }
                .send()
            Log.d(TAG, "Subscrito ao tópico: $topic")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao subscrever ao tópico $topic", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun publishSensorData(
        groupId: String,
        userId: String,
        exerciseId: String,
        source: String,
        value: Number,
        selectedZone: Int,
        zonas: List<Pair<String, IntRange>>
    ) {
        connect()
        val timestamp = Instant.now().toEpochMilli()

        val sensorPayload = JSONObject().apply {
            put("group_id", groupId)
            put("exercise_id", exerciseId)
            put("user_uid", userId)
            put("source", source)
            put("value", value.toDouble())  // <-- Corrigido aqui
            put("timestamp", timestamp)
        }

        val currentZone = WorkoutSessionManager.calculateCurrentZone(value.toInt(), zonas)
        WorkoutSessionManager.incrementTime(source, currentZone, selectedZone)

        val rating = WorkoutSessionManager.getExecutionPercentage()

        val ratingPayload = JSONObject().apply {
            put("group_id", groupId)
            put("exercise_id", exerciseId)
            put("user_uid", userId)
            put("rating", rating.toDouble())  // <-- Corrigido aqui
            put("timestamp", timestamp)
        }

        val topicGroup = "/group/$groupId/data"
        val topicUser = "/user/$userId/data"

        try {
            publish(topicUser, sensorPayload.toString())
            publish(topicGroup, ratingPayload.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao publicar MQTT", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    object WorkoutSessionManager {
        private var elapsedSeconds = 0
        private var timeInZone = 0
        private var lastZone: Int = 0
        private var executionPct: Float = 0f

        fun resetSession() {
            elapsedSeconds = 0
            timeInZone = 0
            executionPct = 0f
            lastZone = 0
        }

        fun incrementTime(source: String, currentZone: Int, selectedZone: Int) {
            elapsedSeconds++
            lastZone = currentZone
            if (currentZone == selectedZone) {
                timeInZone++
            }
            executionPct = (timeInZone.toFloat() / elapsedSeconds * 100)
        }

        fun getExecutionPercentage(): Float = executionPct

        fun calculateCurrentZone(hr: Int?, zonas: List<Pair<String, IntRange>>): Int {
            if (zonas.isEmpty()) return 0
            return hr?.let { bpm ->
                zonas.indexOfFirst { bpm in it.second }.let {
                    when {
                        it == -1 && bpm < zonas.first().second.first -> 0
                        it == -1 && bpm > zonas.last().second.last -> zonas.size + 1
                        else -> it + 1
                    }
                }
            } ?: 0
        }

        fun getElapsedTime(): Int = elapsedSeconds
        fun getTimeInZone(): Int = timeInZone
        fun getLastZone(): Int = lastZone
    }
}
