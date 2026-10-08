package com.aerocine.camera.core.gl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.aerocine.camera.core.sensor.GyroTelemetryEngine

/**
 * Mesin pipa grafis utama (Master Render Engine).
 * Berjalan di utas GL terdedikasi, memproses frame kamera melalui shader AgX,
 * menerapkan stabilisasi warp giroskop real-time, dan menggandakan output ke UI & MediaCodec.
 */
class CameraRenderEngine(
    private val gyroEngine: GyroTelemetryEngine
) {

    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null

    private var eglCore: EglCore? = null
    private var renderer: CameraSurfaceRenderer? = null

    private var previewEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var encoderEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var previewWidth: Int = 1920
    private var previewHeight: Int = 1080
    private var encoderWidth: Int = 1920
    private var encoderHeight: Int = 1080

    var cameraInputSurface: Surface? = null
        private set

    @Volatile
    private var isRecording: Boolean = false

    private val warpMatrix = FloatArray(16)
    private var smoothedGyroX = 0f
    private var smoothedGyroY = 0f
    private var smoothedGyroZ = 0f

    fun start(
        previewSurface: Surface,
        width: Int,
        height: Int,
        onReady: (Surface) -> Unit
    ) {
        previewWidth = width
        previewHeight = height

        renderThread = HandlerThread("GLRenderThread").also { it.start() }
        renderHandler = Handler(renderThread!!.looper)

        renderHandler?.post {
            try {
                val core = EglCore()
                eglCore = core

                val surface = core.createWindowSurface(previewSurface)
                previewEglSurface = surface
                core.makeCurrent(surface)

                val rend = CameraSurfaceRenderer()
                rend.initializeGl()
                rend.updateViewport(width, height)
                renderer = rend

                val st = rend.surfaceTexture ?: throw IllegalStateException("SurfaceTexture null")
                cameraInputSurface = Surface(st)

                st.setOnFrameAvailableListener({
                    renderHandler?.post {
                        renderFrame()
                    }
                }, renderHandler)

                onReady(cameraInputSurface!!)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menginisialisasi pipa render GL: ${e.message}")
            }
        }
    }

    private fun renderFrame() {
        val core = eglCore ?: return
        val rend = renderer ?: return

        val timestampNs = System.nanoTime()

        // Kalkulasi matriks kompensasi stabilisasi giroskop real-time
        calculateStabilizationWarpMatrix()

        // 1. Render ke Viewfinder UI
        if (previewEglSurface != EGL14.EGL_NO_SURFACE) {
            core.makeCurrent(previewEglSurface)
            rend.updateViewport(previewWidth, previewHeight)
            rend.drawFrame(warpMatrix)
            core.swapBuffers(previewEglSurface)
        }

        // 2. Render ke Input Surface MediaCodec (Saat Merekam)
        if (isRecording && encoderEglSurface != EGL14.EGL_NO_SURFACE) {
            core.makeCurrent(encoderEglSurface)
            rend.updateViewport(encoderWidth, encoderHeight)
            rend.drawFrame(warpMatrix)
            core.setPresentationTime(encoderEglSurface, timestampNs)
            core.swapBuffers(encoderEglSurface)
        }
    }

    private fun calculateStabilizationWarpMatrix() {
        val angularVel = gyroEngine.currentAngularVelocity

        // Low-pass filter untuk memisahkan tremor getaran tangan dari panning disengaja
        val alpha = 0.25f
        smoothedGyroX += alpha * (angularVel[0] - smoothedGyroX)
        smoothedGyroY += alpha * (angularVel[1] - smoothedGyroY)
        smoothedGyroZ += alpha * (angularVel[2] - smoothedGyroZ)

        // Skala sudut kompensasi (derajat)
        val degX = -smoothedGyroX * 1.5f
        val degY = -smoothedGyroY * 1.5f
        val degZ = -smoothedGyroZ * 2.0f

        Matrix.setIdentityM(warpMatrix, 0)
        Matrix.rotateM(warpMatrix, 0, degZ, 0f, 0f, 1f)
        Matrix.rotateM(warpMatrix, 0, degY, 0f, 1f, 0f)
        Matrix.rotateM(warpMatrix, 0, degX, 1f, 0f, 0f)
    }

    fun attachEncoderSurface(encoderSurface: Surface, width: Int, height: Int) {
        encoderWidth = width
        encoderHeight = height

        renderHandler?.post {
            val core = eglCore ?: return@post
            try {
                if (encoderEglSurface != EGL14.EGL_NO_SURFACE) {
                    core.releaseSurface(encoderEglSurface)
                }
                encoderEglSurface = core.createWindowSurface(encoderSurface)
                isRecording = true
                Log.i(TAG, "Encoder surface berhasil ditautkan ke pipa render.")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menautkan encoder surface: ${e.message}")
            }
        }
    }

    fun detachEncoderSurface() {
        renderHandler?.post {
            isRecording = false
            val core = eglCore ?: return@post
            if (encoderEglSurface != EGL14.EGL_NO_SURFACE) {
                core.releaseSurface(encoderEglSurface)
                encoderEglSurface = EGL14.EGL_NO_SURFACE
            }
            Log.i(TAG, "Encoder surface dilepas dari pipa render.")
        }
    }

    fun release() {
        renderHandler?.post {
            val core = eglCore
            if (core != null) {
                if (previewEglSurface != EGL14.EGL_NO_SURFACE) {
                    core.releaseSurface(previewEglSurface)
                    previewEglSurface = EGL14.EGL_NO_SURFACE
                }
                if (encoderEglSurface != EGL14.EGL_NO_SURFACE) {
                    core.releaseSurface(encoderEglSurface)
                    encoderEglSurface = EGL14.EGL_NO_SURFACE
                }
                renderer?.release()
                renderer = null
                core.release()
                eglCore = null
            }
            cameraInputSurface?.release()
            cameraInputSurface = null
        }

        renderThread?.quitSafely()
        try {
            renderThread?.join()
            renderThread = null
            renderHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interupsi penghentian GLRenderThread: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "CameraRenderEngine"
    }
}
