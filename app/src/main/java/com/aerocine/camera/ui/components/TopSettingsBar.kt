package com.aerocine.camera.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerocine.camera.model.StabilizationMode
import com.aerocine.camera.model.VideoBitrate
import com.aerocine.camera.model.VideoConfig
import com.aerocine.camera.model.VideoResolution
import com.aerocine.camera.ui.theme.DarkSurface
import com.aerocine.camera.ui.theme.NeutralGray
import com.aerocine.camera.ui.theme.RecordCrimson
import com.aerocine.camera.ui.theme.SlateGray
import com.aerocine.camera.ui.theme.SoftWhite

@Composable
fun TopSettingsBar(
    config: VideoConfig,
    isExposureLocked: Boolean,
    isRecording: Boolean,
    onResolutionToggle: () -> Unit,
    onBitrateToggle: () -> Unit,
    onStabilizationToggle: () -> Unit,
    onExposureLockToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Toggle Resolusi (4K 30 / 1080p 60)
        SettingChip(
            label = config.resolution.title,
            isEnabled = !isRecording,
            onClick = onResolutionToggle
        )

        // Toggle Bitrate (100 Mbps / 60 Mbps)
        SettingChip(
            label = config.bitrate.title,
            isEnabled = !isRecording,
            onClick = onBitrateToggle
        )

        // Toggle Stabilisasi (Stabilisasi Penuh / OIS Saja / Mati)
        SettingChip(
            label = when (config.stabilization) {
                StabilizationMode.PREVIEW_AND_OPTICAL -> "EIS+OIS"
                StabilizationMode.OPTICAL_ONLY -> "OIS"
                StabilizationMode.OFF -> "STAB OFF"
            },
            isEnabled = !isRecording,
            onClick = onStabilizationToggle
        )

        // Kunci Eksposur (AE Lock)
        SettingChip(
            label = if (isExposureLocked) "AE KUNCI" else "AE OTOMATIS",
            isActive = isExposureLocked,
            onClick = onExposureLockToggle
        )
    }
}

@Composable
private fun SettingChip(
    label: String,
    isEnabled: Boolean = true,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = if (isActive) RecordCrimson.copy(alpha = 0.2f) else DarkSurface.copy(alpha = 0.7f)
    val borderColor = if (isActive) RecordCrimson else SlateGray
    val textColor = when {
        !isEnabled -> NeutralGray.copy(alpha = 0.5f)
        isActive -> SoftWhite
        else -> SoftWhite
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = isEnabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
