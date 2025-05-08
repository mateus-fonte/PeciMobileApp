// MqttManager.kt
package com.example.pecimobileapp.mqtt

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    @Volatile
private var isConnecting = false

fun connect() {
    if (!isConnected && !isConnecting) {
        isConnecting = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                mqttClient.connect()
                isConnected = true
                Log.d(TAG, "Conectado ao MQTT Broker")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao conectar ao MQTT Broker", e)
                isConnected = false
            } finally {
                isConnecting = false
            }
        }
    }
}

fun publish(topic: String, message: String) {
    if (!isConnected && !isConnecting) {
        connect() // Tenta reconectar automaticamente
    }
    if (isConnected) {
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
    } else {
        Log.e(TAG, "Não foi possível publicar: MQTT não conectado")
    }
}

fun subscribe(topic: String, onMessageReceived: (String) -> Unit) {
    try {
        // Make sure we're connected
        if (!isConnected) connect()
        
        // Proper HiveMQ MQTT client subscription pattern
        mqttClient.toAsync().subscribeWith()
            .topicFilter(topic)
            .callback { publish ->
                try {
                    // Get the ByteBuffer safely
                    val byteBuffer = publish.getPayloadAsBytes()
                    if (byteBuffer != null) {
                        // Convert byte array to String
                        val message = String(byteBuffer, StandardCharsets.UTF_8)
                        onMessageReceived(message)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing MQTT message", e)
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
    CoroutineScope(Dispatchers.IO).launch {
        Log.d("MqttManager", "Publishing data -> groupId: $groupId, userId: $userId, exerciseId: $exerciseId, source: $source, value: $value, selectedZone: $selectedZone")

        connect()
        val timestamp = Instant.now().toEpochMilli()

        val sensorPayload = JSONObject().apply {
            put("group_id", groupId)
            put("exercise_id", exerciseId)
            put("user_uid", userId)
            put("source", source)
            put("value", value.toDouble())
            put("timestamp", timestamp)
        }

        val topicGroup = "/group/$groupId/data"
        val topicUser = "/user/$userId/data"

        try {
            publish(topicUser, sensorPayload.toString())
            publish(topicGroup, sensorPayload.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao publicar MQTT", e)
        }
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
