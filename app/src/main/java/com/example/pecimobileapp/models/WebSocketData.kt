package com.example.pecimobileapp.models

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Classe para armazenar os dados recebidos via WebSocket do ESP32-CAM
 */
data class WebSocketData(
    val type: DataType,
    val timestamp: Long,          // Timestamp em segundos (epoch)
    val milliseconds: Int,        // Milissegundos (0-999)
    val data: Any                 // Bitmap para imagem ou FloatArray para dados térmicos
) {
    // Tipos de dados recebidos via WebSocket
    enum class DataType(val code: Byte) {
        CAMERA_IMAGE(0x01),
        THERMAL_DATA(0x02);
        
        companion object {
            fun fromByte(value: Byte): DataType {
                return values().find { it.code == value }
                    ?: throw IllegalArgumentException("Tipo de dados desconhecido: $value")
            }
        }
    }
    
    // Para formatação do timestamp completo
    fun getFormattedTimestamp(): String {
        return "${timestamp}.${milliseconds.toString().padStart(3, '0')}"
    }
    
    companion object {
        /**
         * Analisa os dados binários recebidos via WebSocket
         * Formato: [1 byte tipo][4 bytes timestamp][2 bytes millis][dados]
         */
        fun parseFromBinary(binaryData: ByteArray): WebSocketData? {
            if (binaryData.size < 7) return null  // Precisa de pelo menos 7 bytes para o cabeçalho
            
            val buffer = ByteBuffer.wrap(binaryData).order(ByteOrder.LITTLE_ENDIAN)
            
            // Lê o tipo de dados (1 byte)
            val typeCode = buffer.get()
            val type = try {
                DataType.fromByte(typeCode)
            } catch (e: Exception) {
                return null
            }
            
            // Lê o timestamp (4 bytes)
            val timestamp = (buffer.int.toLong() and 0xFFFFFFFFL)
            
            // Lê os milissegundos (2 bytes)
            val milliseconds = (buffer.short.toInt() and 0xFFFF)
            
            // Processa o restante com base no tipo
            val data = when (type) {
                DataType.CAMERA_IMAGE -> {
                    // O restante são dados da imagem JPEG
                    val imageBytes = ByteArray(binaryData.size - 7)
                    System.arraycopy(binaryData, 7, imageBytes, 0, imageBytes.size)
                    imageBytes
                }
                DataType.THERMAL_DATA -> {
                    // O restante são dados térmicos (32x24 floats)
                    val thermalData = FloatArray(32 * 24)
                    val floatBuffer = buffer.asFloatBuffer()
                    floatBuffer.position(0)
                    floatBuffer.get(thermalData)
                    thermalData
                }
            }
            
            return WebSocketData(type, timestamp, milliseconds, data)
        }
    }
}