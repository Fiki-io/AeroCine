package com.aerocine.camera.model

import android.util.Size

enum class VideoResolution(
    val title: String,
    val size: Size,
    val targetFps: Int
) {
    UHD_4K_30("4K 30", Size(3840, 2160), 30),
    FHD_1080P_60("1080p 60", Size(1920, 1080), 60),
    FHD_1080P_30("1080p 30", Size(1920, 1080), 30)
}

enum class VideoBitrate(
    val title: String,
    val bps: Int
) {
    CINEMA_100M("100 Mbps", 100_000_000),
    HIGH_60M("60 Mbps", 60_000_000),
    STANDARD_30M("30 Mbps", 30_000_000)
}

enum class StabilizationMode(
    val title: String
) {
    PREVIEW_AND_OPTICAL("Stabilisasi Penuh"),
    OPTICAL_ONLY("OIS Saja"),
    OFF("Mati")
}

data class VideoConfig(
    val resolution: VideoResolution = VideoResolution.UHD_4K_30,
    val bitrate: VideoBitrate = VideoBitrate.CINEMA_100M,
    val stabilization: StabilizationMode = StabilizationMode.PREVIEW_AND_OPTICAL,
    val useHevc: Boolean = true
)
