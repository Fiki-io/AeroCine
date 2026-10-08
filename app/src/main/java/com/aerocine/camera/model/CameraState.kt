package com.aerocine.camera.model

data class CameraState(
    val isRecording: Boolean = false,
    val recordingDurationSec: Long = 0L,
    val currentZoom: Float = 1.0f,
    val minZoom: Float = 1.0f,
    val maxZoom: Float = 10.0f,
    val isExposureLocked: Boolean = false,
    val exposureCompensation: Int = 0,
    val exposureRange: Pair<Int, Int> = Pair(-4, 4),
    val config: VideoConfig = VideoConfig(),
    val isHardwareOisSupported: Boolean = false,
    val isPreviewStabilizationSupported: Boolean = false,
    val statusMessage: String = "Siap"
)
