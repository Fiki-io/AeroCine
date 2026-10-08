package com.aerocine.camera.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerocine.camera.ui.theme.DarkSurface
import com.aerocine.camera.ui.theme.NeutralGray
import com.aerocine.camera.ui.theme.SlateGray
import com.aerocine.camera.ui.theme.SoftWhite
import java.util.Locale

@Composable
fun ZoomControlDock(
    currentZoom: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoomTargetChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpandedSlider by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isExpandedSlider) {
            // Slider geser horizontal berbobot inersia
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(DarkSurface.copy(alpha = 0.85f))
                    .border(1.dp, SlateGray, RoundedCornerShape(22.dp))
                    .pointerInput(minZoom, maxZoom, currentZoom) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            val sensitivity = 0.02f
                            val delta = dragAmount * sensitivity
                            val newTarget = (currentZoom + delta).coerceIn(minZoom, maxZoom)
                            onZoomTargetChanged(newTarget)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format(Locale.US, "%.1fx", currentZoom),
                    color = SoftWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Tombol rasio fokal cepat ala iOS (0.6x, 1x, 2x)
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZoomPresetButton(
                label = "0.6x",
                targetRatio = 0.6f,
                currentZoom = currentZoom,
                onClick = {
                    onZoomTargetChanged(0.6f.coerceAtLeast(minZoom))
                    isExpandedSlider = false
                }
            )

            ZoomPresetButton(
                label = "1x",
                targetRatio = 1.0f,
                currentZoom = currentZoom,
                onClick = {
                    onZoomTargetChanged(1.0f)
                    isExpandedSlider = !isExpandedSlider
                }
            )

            ZoomPresetButton(
                label = "2x",
                targetRatio = 2.0f,
                currentZoom = currentZoom,
                onClick = {
                    onZoomTargetChanged(2.0f.coerceAtMost(maxZoom))
                    isExpandedSlider = false
                }
            )
        }
    }
}

@Composable
private fun ZoomPresetButton(
    label: String,
    targetRatio: Float,
    currentZoom: Float,
    onClick: () -> Unit
) {
    val isSelected = kotlin.math.abs(currentZoom - targetRatio) < 0.15f
    val bgColor = if (isSelected) SlateGray else DarkSurface.copy(alpha = 0.7f)
    val textColor = if (isSelected) SoftWhite else NeutralGray

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) SoftWhite else Color.Transparent,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
