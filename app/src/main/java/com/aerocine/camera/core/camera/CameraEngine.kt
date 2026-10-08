package com.aerocine.camera.core.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.view.Surface
import com.aerocine.camera.model.StabilizationMode
import com.aerocine.camera.model.VideoConfig

class CameraEngine(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    var cameraId: String = ""
        private set
    var zoomRange: Range<Float> = Range(1.0f, 10.0f)
        private set
    var exposureRange: Range<Int> = Range(-4, 4)
        private set
    var isOisSupported: Boolean = false
        private set
    var isPreviewStabilizationSupported: Boolean = false
        private set

    private var currentZoomRatio: Float = 1.0f
    private var currentExposureCompensation: Int = 0
    private var isAeLocked: Boolean = false

    private val activeSurfaces = mutableListOf<Surface>()

    init {
        detectCameraCapabilities()
    }

    private fun detectCameraCapabilities() {
        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)

                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id

                    // Cek rentang zoom ratio native (Android 11+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let {
                            zoomRange = it
                        }
                    }

                    // Cek kompensasi eksposur
                    chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.let {
                        exposureRange = it
                    }

                    // Cek dukungan hardware OIS
                    val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                    isOisSupported = oisModes?.contains(CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON) == true

                    // Cek dukungan Preview Stabilization (Android 13+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val stabModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
                        isPreviewStabilizationSupported = stabModes?.contains(
                            CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                        ) == true
                    }
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mendeteksi kapabilitas kamera: ${e.message}")
        }
    }

    fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackgroundThread").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interupsi penghentian background thread: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(onOpened: () -> Unit, onError: (String) -> Unit) {
        if (cameraId.isEmpty()) {
            onError("Kamera belakang tidak ditemukan")
            return
        }

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    onOpened()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    onError("Kesalahan inisialisasi kamera: kode $error")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            onError("Gagal membuka kamera: ${e.message}")
        }
    }

    fun startSession(
        surfaces: List<Surface>,
        config: VideoConfig,
        onConfigured: () -> Unit,
        onError: (String) -> Unit
    ) {
        val device = cameraDevice ?: run {
            onError("CameraDevice tidak aktif")
            return
        }

        activeSurfaces.clear()
        activeSurfaces.addAll(surfaces)

        try {
            previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                surfaces.forEach { addTarget(it) }

                // Konfigurasi 3A standar sinematik
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)

                // Eliminasi penajaman digital murahan dan efek cat air
                set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
                set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST)

                // Terapkan stabilisasi sesuai konfigurasi
                applyStabilization(this, config.stabilization)

                // Terapkan rasio zoom
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    set(CaptureRequest.CONTROL_ZOOM_RATIO, currentZoomRatio)
                }

                // Terapkan eksposur
                set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentExposureCompensation)
                set(CaptureRequest.CONTROL_AE_LOCK, isAeLocked)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val outputConfigs = surfaces.map { OutputConfiguration(it) }
                val sessionConfig = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputConfigs,
                    context.mainExecutor,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            updateRepeatingRequest()
                            onConfigured()
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            onError("Konfigurasi sesi kamera gagal")
                        }
                    }
                )

                // Atur parameter sesi untuk mengaktifkan stabilisasi tanpa jeda
                previewRequestBuilder?.build()?.let {
                    sessionConfig.sessionParameters = it
                }

                device.createCaptureSession(sessionConfig)
            } else {
                @Suppress("DEPRECATION")
                device.createCaptureSession(
                    surfaces,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            updateRepeatingRequest()
                            onConfigured()
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            onError("Konfigurasi sesi kamera lama gagal")
                        }
                    },
                    backgroundHandler
                )
            }

        } catch (e: Exception) {
            onError("Gagal membuat sesi rekaman: ${e.message}")
        }
    }

    private fun applyStabilization(builder: CaptureRequest.Builder, mode: StabilizationMode) {
        when (mode) {
            StabilizationMode.PREVIEW_AND_OPTICAL -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isPreviewStabilizationSupported) {
                    builder.set(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                    )
                } else {
                    builder.set(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                    )
                }
                if (isOisSupported) {
                    builder.set(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                    )
                }
            }
            StabilizationMode.OPTICAL_ONLY -> {
                builder.set(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                )
                if (isOisSupported) {
                    builder.set(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                    )
                }
            }
            StabilizationMode.OFF -> {
                builder.set(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                )
                builder.set(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
                )
            }
        }
    }

    fun setZoomRatio(ratio: Float) {
        currentZoomRatio = ratio.coerceIn(zoomRange.lower, zoomRange.upper)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            previewRequestBuilder?.set(CaptureRequest.CONTROL_ZOOM_RATIO, currentZoomRatio)
            updateRepeatingRequest()
        }
    }

    fun setExposureCompensation(value: Int) {
        currentExposureCompensation = value.coerceIn(exposureRange.lower, exposureRange.upper)
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentExposureCompensation)
        updateRepeatingRequest()
    }

    fun setExposureLock(locked: Boolean) {
        isAeLocked = locked
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_LOCK, isAeLocked)
        updateRepeatingRequest()
    }

    private fun updateRepeatingRequest() {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        try {
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memperbarui repeating request: ${e.message}")
        }
    }

    fun close() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            activeSurfaces.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan saat menutup kamera: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "CameraEngine"
    }
}
