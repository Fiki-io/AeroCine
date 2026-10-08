package com.aerocine.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerocine.camera.ui.components.ShutterTrigger
import com.aerocine.camera.ui.components.TopSettingsBar
import com.aerocine.camera.ui.components.ViewfinderSurface
import com.aerocine.camera.ui.components.ZoomControlDock
import com.aerocine.camera.ui.theme.MatteBlack
import com.aerocine.camera.ui.theme.NeutralGray

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MatteBlack)
    ) {
        // Lapisan 1: Viewfinder Kamera Fullscreen
        ViewfinderSurface(
            modifier = Modifier.fillMaxSize(),
            onSurfaceAvailable = { surfaceTexture, width, height ->
                viewModel.onPreviewSurfaceAvailable(surfaceTexture, width, height)
            },
            onSurfaceDestroyed = {
                viewModel.onPreviewSurfaceDestroyed()
            }
        )

        // Lapisan 2: Gradien Vignette Atas dan Bawah untuk Kontras Kontrol
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(MatteBlack.copy(alpha = 0.8f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
        ) {
            Column {
                TopSettingsBar(
                    config = state.config,
                    isExposureLocked = state.isExposureLocked,
                    isRecording = state.isRecording,
                    onResolutionToggle = { viewModel.toggleResolution() },
                    onBitrateToggle = { viewModel.toggleBitrate() },
                    onStabilizationToggle = { viewModel.toggleStabilization() },
                    onExposureLockToggle = { viewModel.toggleExposureLock() }
                )

                if (state.statusMessage != "Siap") {
                    Text(
                        text = state.statusMessage,
                        color = NeutralGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Lapisan 3: Dock Bawah (Zoom Physics Scrubber + Shutter Perekam)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MatteBlack.copy(alpha = 0.9f))
                    )
                )
                .navigationBarsPadding()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Pengontrol Zoom Pegas Bergaya iOS
                ZoomControlDock(
                    currentZoom = state.currentZoom,
                    minZoom = state.minZoom,
                    maxZoom = state.maxZoom,
                    onZoomTargetChanged = { target ->
                        viewModel.setZoomTarget(target)
                    }
                )

                Spacer(modifier = Modifier.padding(top = 16.dp))

                // Tombol Shutter Sinematik
                ShutterTrigger(
                    isRecording = state.isRecording,
                    recordingDurationSec = state.recordingDurationSec,
                    onRecordToggle = { viewModel.toggleRecording() }
                )
            }
        }
    }
}
