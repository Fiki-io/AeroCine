package com.aerocine.camera.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException

/**
 * Perekam telemetri sensorik inersia (IMU) frekuensi tinggi (200 Hz).
 * Mencatat vektor kecepatan sudut rotasi [gx, gy, gz] dengan stempel waktu nanodetik
 * untuk sinkronisasi mutlak dengan metadata frame kamera.
 */
class GyroTelemetryEngine(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var writer: BufferedWriter? = null
    private var isRecording: Boolean = false

    var currentAngularVelocity: FloatArray = floatArrayOf(0f, 0f, 0f)
        private set

    fun isGyroscopeAvailable(): Boolean = gyroscope != null

    fun startListening() {
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    fun startRecording(outputFile: File) {
        try {
            writer = BufferedWriter(FileWriter(outputFile))
            writer?.write("timestamp_ns,gyro_x,gyro_y,gyro_z\n")
            isRecording = true
        } catch (e: IOException) {
            Log.e(TAG, "Gagal membuka file telemetri giroskop: ${e.message}")
        }
    }

    fun stopRecording() {
        isRecording = false
        try {
            writer?.flush()
            writer?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Gagal menutup file telemetri giroskop: ${e.message}")
        } finally {
            writer = null
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        currentAngularVelocity[0] = event.values[0]
        currentAngularVelocity[1] = event.values[1]
        currentAngularVelocity[2] = event.values[2]

        if (isRecording && writer != null) {
            try {
                // Catat timestamp dalam nanodetik dan nilai rotasi rad/s
                writer?.write("${event.timestamp},${event.values[0]},${event.values[1]},${event.values[2]}\n")
            } catch (e: IOException) {
                Log.e(TAG, "Kesalahan penulisan data telemetri: ${e.message}")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Tidak memerlukan tindakan khusus untuk perubahan akurasi
    }

    companion object {
        private const val TAG = "GyroTelemetryEngine"
    }
}
