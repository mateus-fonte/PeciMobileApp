package com.example.pecimobileapp.services

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Service for managing Bluetooth connections and data transfer
 */
class BluetoothService(private val applicationContext: android.content.Context) {
    private val TAG = "BluetoothService"

    // Standard SPP UUID - Most Raspberry Pi Bluetooth services use this
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    suspend fun connectToDevice(device: BluetoothDevice): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Close any existing connection
                disconnect()

                Log.d(TAG, "Attempting to connect to ${device.address}")

                // Get a BluetoothSocket for connection with the given BluetoothDevice
                val tmpSocket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.e(TAG, "Bluetooth connect permission not granted")
                        return@withContext false
                    }

                    // First try to create an RFCOMM socket to the SPP UUID
                    try {
                        device.createRfcommSocketToServiceRecord(SPP_UUID)
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to create RFCOMM socket, trying fallback method", e)
                        // Fallback for some devices
                        try {
                            // Using reflection as a fallback
                            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                            method.invoke(device, 1) as BluetoothSocket
                        } catch (e2: Exception) {
                            Log.e(TAG, "Fallback connection method failed", e2)
                            throw IOException("Could not establish Bluetooth connection", e2)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    try {
                        device.createRfcommSocketToServiceRecord(SPP_UUID)
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to create RFCOMM socket, trying fallback method", e)
                        // Fallback for some devices
                        try {
                            // Using reflection as a fallback
                            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                            method.invoke(device, 1) as BluetoothSocket
                        } catch (e2: Exception) {
                            Log.e(TAG, "Fallback connection method failed", e2)
                            throw IOException("Could not establish Bluetooth connection", e2)
                        }
                    }
                }

                socket = tmpSocket

                // Try to connect with timeout
                val connected = withTimeoutOrNull(5000L) {
                    try {
                        Log.d(TAG, "Attempting socket connection...")
                        socket?.connect()
                        true
                    } catch (e: IOException) {
                        Log.e(TAG, "Socket connect() failed", e)
                        false
                    }
                } ?: false

                if (!connected) {
                    Log.e(TAG, "Connection attempt timed out")
                    socket?.close()
                    socket = null
                    return@withContext false
                }

                // Get the BluetoothSocket input and output streams
                try {
                    inputStream = socket?.inputStream
                    outputStream = socket?.outputStream
                } catch (e: IOException) {
                    Log.e(TAG, "Error getting socket streams", e)
                    disconnect()
                    return@withContext false
                }

                Log.d(TAG, "Connected successfully to device: ${device.address}")
                true
            } catch (e: IOException) {
                // Close the socket
                try {
                    socket?.close()
                } catch (e2: IOException) {
                    Log.e(TAG, "Unable to close() socket during connection failure", e2)
                }

                socket = null
                inputStream = null
                outputStream = null

                Log.e(TAG, "Could not connect to device", e)
                false
            }
        }
    }

    fun disconnect() {
        try {
            inputStream?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Could not close the input stream", e)
        }

        try {
            outputStream?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Could not close the output stream", e)
        }

        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Could not close the client socket", e)
        }

        inputStream = null
        outputStream = null
        socket = null
    }

    suspend fun sendData(data: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (socket == null || outputStream == null || !socket!!.isConnected) {
                    Log.e(TAG, "Cannot send data - not connected")
                    return@withContext false
                }

                // Add a terminating newline for easier parsing on Raspberry Pi
                val dataWithNewline = "$data\n"
                Log.d(TAG, "Sending data: $data")

                outputStream?.write(dataWithNewline.toByteArray())
                outputStream?.flush()

                Log.d(TAG, "Data sent successfully")
                true
            } catch (e: IOException) {
                Log.e(TAG, "Error sending data", e)
                false
            }
        }
    }

    suspend fun receiveData(timeout: Long = 5000): String? {
        return withContext(Dispatchers.IO) {
            if (socket == null || inputStream == null || !socket!!.isConnected) {
                Log.e(TAG, "Cannot receive data - not connected")
                return@withContext null
            }

            try {
                // Check if data is available with timeout
                val result = withTimeoutOrNull(timeout) {
                    val buffer = ByteArray(1024)
                    val bytes = inputStream?.read(buffer)

                    if (bytes != null && bytes > 0) {
                        val receivedData = String(buffer, 0, bytes)
                        Log.d(TAG, "Received data: $receivedData")
                        receivedData
                    } else {
                        null
                    }
                }

                if (result == null) {
                    Log.d(TAG, "No data received within timeout")
                }

                result
            } catch (e: IOException) {
                Log.e(TAG, "Error receiving data", e)
                null
            }
        }
    }

    fun isConnected(): Boolean {
        return socket?.isConnected == true
    }
}