package com.aerocine.camera.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerocine.camera.ui.theme.DarkSurface
import com.aerocine.camera.ui.theme.NeutralGray
import com.aerocine.camera.ui.theme.RecordCrimson
import com.aerocine.camera.ui.theme.SoftWhite
import java.util.Locale

@Composable
fun ShutterTrigger(
    isRecording: Boolean,
    recordingDurationSec: Long,
    onRecordToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Indikator durasi rekaman
        if (isRecording) {
            val minutes = recordingDurationSec / 60
            val seconds = recordingDurationSec % 60
            val durationFormatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(RecordCrimson.copy(alpha = 0.8f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = durationFormatted,
                    color = SoftWhite,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text(
                text = "STANDBY",
                color = NeutralGray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }

        // Tombol Shutter Perekaman
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .border(4.dp, SoftWhite, CircleShape)
                .background(DarkSurface.copy(alpha = 0.5f))
                .clickable { onRecordToggle() },
            contentAlignment = Alignment.Center
        ) {
            if (isRecording) {
                // Ikon Berhenti (Kotak)
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(RecordCrimson)
                )
            } else {
                // Ikon Mulai (Lingkaran Merah)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(RecordCrimson)
                )
            }
        }
    }
}
