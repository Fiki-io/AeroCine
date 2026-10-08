package com.aerocine.camera.ui

import android.app.Application
import android.graphics.SurfaceTexture
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aerocine.camera.core.camera.CameraEngine
import com.aerocine.camera.core.encoder.HighBitrateMediaEncoder
import com.aerocine.camera.core.gl.CameraRenderEngine
import com.aerocine.camera.core.sensor.GyroTelemetryEngine
import com.aerocine.camera.core.spring.ZoomSpringEngine
import com.aerocine.camera.model.CameraState
import com.aerocine.camera.model.StabilizationMode
import com.aerocine.camera.model.VideoBitrate
import com.aerocine.camera.model.VideoConfig
import com.aerocine.camera.model.VideoResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val cameraEngine = CameraEngine(application)
    private val mediaEncoder = HighBitrateMediaEncoder()
    private val gyroEngine = GyroTelemetryEngine(application)
    private val springEngine = ZoomSpringEngine(initialValue = 1.0f)
    private val renderEngine = CameraRenderEngine(gyroEngine)

    private val _uiState = MutableStateFlow(CameraState())
    val uiState: StateFlow<CameraState> = _uiState.asStateFlow()

    private var uiSurface: Surface? = null
    private var zoomAnimationJob: Job? = null
    private var timerJob: Job? = null

    private var currentVideoFile: File? = null
    private var currentGyroFile: File? = null

    init {
        cameraEngine.startBackgroundThread()
        gyroEngine.startListening()

        _uiState.update {
            it.copy(
                minZoom = cameraEngine.zoomRange.lower,
                maxZoom = cameraEngine.zoomRange.upper,
                exposureRange = Pair(cameraEngine.exposureRange.lower, cameraEngine.exposureRange.upper),
                isHardwareOisSupported = cameraEngine.isOisSupported,
                isPreviewStabilizationSupported = cameraEngine.isPreviewStabilizationSupported
            )
        }
    }

    fun onPreviewSurfaceAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        val config = _uiState.value.config
        surfaceTexture.setDefaultBufferSize(width, height)
        val surface = Surface(surfaceTexture)
        uiSurface = surface

        // Inisialisasi pipa render OpenGL ES 3.0 dengan shader AgX dan stabilisasi gyro
        renderEngine.start(
            previewSurface = surface,
            width = width,
            height = height,
            cameraWidth = config.resolution.size.width,
            cameraHeight = config.resolution.size.height,
            onReady = { cameraInputSurface ->
                cameraEngine.openCamera(
                    onOpened = {
                        cameraEngine.startSession(
                            surfaces = listOf(cameraInputSurface),
                            config = _uiState.value.config,
                            onConfigured = {
                                _uiState.update { it.copy(statusMessage = "Siap") }
                            },
                            onError = { error ->
                                _uiState.update { it.copy(statusMessage = error) }
                            }
                        )
                    },
                    onError = { error ->
                        _uiState.update { it.copy(statusMessage = error) }
                    }
                )
            }
        )
    }

    fun onPreviewSurfaceDestroyed() {
        uiSurface?.release()
        uiSurface = null
        renderEngine.release()
        cameraEngine.close()
    }

    fun setZoomTarget(target: Float) {
        val clampedTarget = target.coerceIn(_uiState.value.minZoom, _uiState.value.maxZoom)
        springEngine.setTarget(clampedTarget)

        if (zoomAnimationJob?.isActive != true) {
            zoomAnimationJob = viewModelScope.launch(Dispatchers.Main) {
                var lastTime = System.nanoTime()
                while (isActive && !springEngine.isAtRest()) {
                    val now = System.nanoTime()
                    val dt = (now - lastTime) / 1_000_000_000.0f
                    lastTime = now

                    springEngine.update(dt)
                    val currentVal = springEngine.currentValue

                    cameraEngine.setZoomRatio(currentVal)
                    _uiState.update { it.copy(currentZoom = currentVal) }

                    delay(16) // Siklus tick 60 FPS
                }
                val finalVal = springEngine.targetValue
                cameraEngine.setZoomRatio(finalVal)
                _uiState.update { it.copy(currentZoom = finalVal) }
            }
        }
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        val context = getApplication<Application>()
        val outputDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "AeroCine"
        ).apply { if (!exists()) mkdirs() }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        currentVideoFile = File(outputDir, "AEROCINE_$timestamp.mp4")
        currentGyroFile = File(outputDir, "AEROCINE_${timestamp}_gyro.csv")

        try {
            val config = _uiState.value.config
            val encoderSurface = mediaEncoder.startRecording(currentVideoFile!!, config)
            currentGyroFile?.let { gyroEngine.startRecording(it) }

            // Tautkan encoder surface ke pipa render OpenGL tanpa perlu mereset sesi Camera2
            renderEngine.attachEncoderSurface(
                encoderSurface = encoderSurface,
                width = config.resolution.size.width,
                height = config.resolution.size.height
            )

            _uiState.update { it.copy(isRecording = true, recordingDurationSec = 0L) }
            startTimer()
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan memulai perekaman: ${e.message}")
            _uiState.update { it.copy(statusMessage = "Kesalahan encoder: ${e.message}") }
        }
    }

    private fun stopRecording() {
        timerJob?.cancel()
        timerJob = null

        renderEngine.detachEncoderSurface()
        mediaEncoder.stopRecording()
        gyroEngine.stopRecording()

        // Pindai file ke MediaStore agar langsung terdaftar di Galeri ponsel
        currentVideoFile?.let { file ->
            MediaScannerConnection.scanFile(
                getApplication(),
                arrayOf(file.absolutePath),
                arrayOf("video/mp4"),
                null
            )
        }

        _uiState.update { it.copy(isRecording = false, recordingDurationSec = 0L) }
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive && _uiState.value.isRecording) {
                delay(1000)
                _uiState.update { it.copy(recordingDurationSec = it.recordingDurationSec + 1) }
            }
        }
    }

    fun toggleResolution() {
        if (_uiState.value.isRecording) return
        val current = _uiState.value.config.resolution
        val next = when (current) {
            VideoResolution.UHD_4K_30 -> VideoResolution.FHD_1080P_60
            VideoResolution.FHD_1080P_60 -> VideoResolution.FHD_1080P_30
            VideoResolution.FHD_1080P_30 -> VideoResolution.UHD_4K_30
        }
        updateConfig { it.copy(resolution = next) }
    }

    fun toggleBitrate() {
        if (_uiState.value.isRecording) return
        val current = _uiState.value.config.bitrate
        val next = when (current) {
            VideoBitrate.CINEMA_100M -> VideoBitrate.HIGH_60M
            VideoBitrate.HIGH_60M -> VideoBitrate.STANDARD_30M
            VideoBitrate.STANDARD_30M -> VideoBitrate.CINEMA_100M
        }
        updateConfig { it.copy(bitrate = next) }
    }

    fun toggleStabilization() {
        if (_uiState.value.isRecording) return
        val current = _uiState.value.config.stabilization
        val next = when (current) {
            StabilizationMode.PREVIEW_AND_OPTICAL -> StabilizationMode.OPTICAL_ONLY
            StabilizationMode.OPTICAL_ONLY -> StabilizationMode.OFF
            StabilizationMode.OFF -> StabilizationMode.PREVIEW_AND_OPTICAL
        }
        updateConfig { it.copy(stabilization = next) }
    }

    fun toggleExposureLock() {
        val newLock = !_uiState.value.isExposureLocked
        cameraEngine.setExposureLock(newLock)
        _uiState.update { it.copy(isExposureLocked = newLock) }
    }

    private fun updateConfig(update: (VideoConfig) -> VideoConfig) {
        val newConfig = update(_uiState.value.config)
        _uiState.update { it.copy(config = newConfig) }

        renderEngine.cameraInputSurface?.let { inputSurface ->
            cameraEngine.startSession(
                surfaces = listOf(inputSurface),
                config = newConfig,
                onConfigured = {
                    _uiState.update { it.copy(statusMessage = "Siap") }
                },
                onError = { error ->
                    _uiState.update { it.copy(statusMessage = error) }
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRecording()
        gyroEngine.stopListening()
        renderEngine.release()
        cameraEngine.close()
        cameraEngine.stopBackgroundThread()
    }

    companion object {
        private const val TAG = "CameraViewModel"
    }
}
