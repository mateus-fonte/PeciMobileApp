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
        value: Number
    ) {
        connect()
        val timestamp = Instant.now().toEpochMilli()

        val fullPayload = """
            {
                "group_id": "$groupId",
                "exercise_id": "$exerciseId",
                "user_uid": "$userId",
                "source": "$source",
                "value": $value,
                "timestamp": $timestamp
            }
        """.trimIndent()

        val rating = when (source) {
            "ppg" -> if (value.toInt() in 60..120) 10 else 5
            "avg_temp", "max_temp", "min_temp" -> if (value.toFloat() in 36.0..38.0) 10 else 6
            else -> 0
        }

        val groupPayload = """
            {
                "group_id": "$groupId",
                "exercise_id": "$exerciseId",
                "user_uid": "$userId",
                "rating": $rating,
                "timestamp": $timestamp
            }
        """.trimIndent()

        val groupTopic = "/group/$groupId/data"
        val userTopic = "/user/$userId/data"

        try {
            publish(userTopic, fullPayload)
            publish(groupTopic, groupPayload)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao publicar MQTT", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    object WorkoutSessionManager {
        private var elapsedSeconds = 0
        private var timeInZone = 0
        private var lastZone: Int = 0
        private var executionPercentage: Float = 0f
        private var positionCallback: ((Int) -> Unit)? = null

        fun resetSession() {
            elapsedSeconds = 0
            timeInZone = 0
            executionPercentage = 0f
            lastZone = 0
        }

        fun updateSession(
            heartRate: Int?,
            selectedZone: Int,
            zonas: List<Pair<String, IntRange>>,
            groupId: String,
            userId: String
        ): Float {
            elapsedSeconds++

            val currentZone = calculateCurrentZone(heartRate, zonas)
            lastZone = currentZone

            if (isHeartRateInZone(heartRate, selectedZone, zonas)) {
                timeInZone++
            }

            if (elapsedSeconds % 60 == 0) {
                executionPercentage = (timeInZone.toFloat() / elapsedSeconds * 100)
                publishExecution(groupId, userId, executionPercentage)
            }

            return executionPercentage
        }

        private fun publishExecution(groupId: String, userId: String, execPct: Float) {
            val payload = """
                {
                    "timestamp": ${Instant.now().epochSecond},
                    "id": "$userId",
                    "exec_pct": $execPct,
                    "grupo": "$groupId"
                }
            """.trimIndent()

            val topic = "grupo/execucao"
            try {
                connect()
                publish(topic, payload)
            } catch (e: Exception) {
                Log.e("WorkoutSession", "Erro ao publicar execucao", e)
            }
        }

        fun subscribePositionUpdates(groupId: String, userId: String, onPositionUpdate: (Int) -> Unit) {
            positionCallback = onPositionUpdate
            val topic = "grupo/posicao"

            connect()
            subscribe(topic) { message ->
                try {
                    val json = JSONObject(message)
                    if (json.getString("id") == userId) {
                        val position = json.getInt("position")
                        positionCallback?.invoke(position)
                    }
                } catch (e: Exception) {
                    Log.e("WorkoutSession", "Erro ao processar ranking", e)
                }
            }
        }

        private fun isHeartRateInZone(hr: Int?, selectedZone: Int, zonas: List<Pair<String, IntRange>>): Boolean {
            val faixa = zonas.getOrNull(selectedZone - 1)?.second
            return hr != null && faixa?.contains(hr) == true
        }

        fun calculateCurrentZone(hr: Int?, zonas: List<Pair<String, IntRange>>): Int {
            return hr?.let { bpm ->
                zonas.indexOfFirst { bpm in it.second }.let {
                    when {
                        it == -1 && bpm < zonas.first().second.first -> 0
                        it == -1 && bpm > zonas.last().second.last -> 5
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
