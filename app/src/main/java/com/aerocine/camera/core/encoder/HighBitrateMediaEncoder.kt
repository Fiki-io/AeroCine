package com.aerocine.camera.core.encoder

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.util.Log
import android.view.Surface
import com.aerocine.camera.model.VideoConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Perekam video dan audio berkecepatan tinggi (High-Bitrate Media Encoder).
 * Menggunakan akselerasi perangkat keras MediaCodec dengan profil HEVC (H.265) hingga 100 Mbps.
 */
class HighBitrateMediaEncoder {

    private var videoCodec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private var muxer: MediaMuxer? = null

    var inputSurface: Surface? = null
        private set

    private var videoTrackIndex: Int = -1
    private var audioTrackIndex: Int = -1
    private var isMuxerStarted: Boolean = false

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var isRecording: Boolean = false

    fun startRecording(outputFile: File, config: VideoConfig): Surface {
        val width = config.resolution.size.width
        val height = config.resolution.size.height
        val fps = config.resolution.targetFps
        val bitrate = config.bitrate.bps

        // Inisialisasi format video HEVC / H.264
        val mimeType = if (config.useHevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
        val videoFormat = MediaFormat.createVideoFormat(mimeType, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 detik per Key Frame
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
        }

        videoCodec = MediaCodec.createEncoderByType(mimeType).apply {
            configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = createInputSurface()
            start()
        }

        // Inisialisasi format audio AAC 320 kbps 48kHz
        val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 48000, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 320_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).apply {
            setOrientationHint(90)
        }
        isMuxerStarted = false
        videoTrackIndex = -1
        audioTrackIndex = -1
        isRecording = true

        startAudioCapture()
        startEncodingLoop()

        return inputSurface ?: throw IllegalStateException("Input surface tidak dapat dibuat")
    }

    private fun startAudioCapture() {
        val bufferSize = AudioRecord.getMinBufferSize(
            48000,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                48000,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            ).apply {
                startRecording()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Izin perekaman audio ditolak: ${e.message}")
        }
    }

    private fun startEncodingLoop() {
        recordingJob = scope.launch {
            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioBufferInfo = MediaCodec.BufferInfo()
            val audioPcmBuffer = ByteArray(4096)

            while (isActive && isRecording) {
                // 1. Kuras output video encoder
                drainVideoEncoder(videoBufferInfo)

                // 2. Baca audio PCM dan kirim ke audio encoder
                feedAudioEncoder(audioPcmBuffer)

                // 3. Kuras output audio encoder
                drainAudioEncoder(audioBufferInfo)
            }
        }
    }

    private fun drainVideoEncoder(bufferInfo: MediaCodec.BufferInfo) {
        val codec = videoCodec ?: return
        val m = muxer ?: return

        var status = codec.dequeueOutputBuffer(bufferInfo, 2000)
        while (status >= 0) {
            val outputBuffer = codec.getOutputBuffer(status) ?: continue

            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                bufferInfo.size = 0
            }

            if (bufferInfo.size > 0 && isMuxerStarted && videoTrackIndex >= 0) {
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                m.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
            }

            codec.releaseOutputBuffer(status, false)
            status = codec.dequeueOutputBuffer(bufferInfo, 0)
        }

        if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !isMuxerStarted) {
            videoTrackIndex = m.addTrack(codec.outputFormat)
            checkStartMuxer()
        }
    }

    private fun feedAudioEncoder(pcmBuffer: ByteArray) {
        val record = audioRecord ?: return
        val codec = audioCodec ?: return

        val readBytes = record.read(pcmBuffer, 0, pcmBuffer.size)
        if (readBytes > 0) {
            val inputIndex = codec.dequeueInputBuffer(1000)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(pcmBuffer, 0, readBytes)
                val ptsUs = System.nanoTime() / 1000
                codec.queueInputBuffer(inputIndex, 0, readBytes, ptsUs, 0)
            }
        }
    }

    private fun drainAudioEncoder(bufferInfo: MediaCodec.BufferInfo) {
        val codec = audioCodec ?: return
        val m = muxer ?: return

        var status = codec.dequeueOutputBuffer(bufferInfo, 2000)
        while (status >= 0) {
            val outputBuffer = codec.getOutputBuffer(status) ?: continue

            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                bufferInfo.size = 0
            }

            if (bufferInfo.size > 0 && isMuxerStarted && audioTrackIndex >= 0) {
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                m.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
            }

            codec.releaseOutputBuffer(status, false)
            status = codec.dequeueOutputBuffer(bufferInfo, 0)
        }

        if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !isMuxerStarted) {
            audioTrackIndex = m.addTrack(codec.outputFormat)
            checkStartMuxer()
        }
    }

    @Synchronized
    private fun checkStartMuxer() {
        val m = muxer ?: return
        if (!isMuxerStarted && videoTrackIndex >= 0 && audioTrackIndex >= 0) {
            m.start()
            isMuxerStarted = true
            Log.i(TAG, "MediaMuxer berhasil dimulai dengan track video dan audio.")
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menghentikan audio record: ${e.message}")
        } finally {
            audioRecord = null
        }

        try {
            videoCodec?.signalEndOfInputStream()
            videoCodec?.stop()
            videoCodec?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menghentikan video codec: ${e.message}")
        } finally {
            videoCodec = null
        }

        try {
            audioCodec?.stop()
            audioCodec?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menghentikan audio codec: ${e.message}")
        } finally {
            audioCodec = null
        }

        try {
            if (isMuxerStarted) {
                muxer?.stop()
            }
            muxer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menghentikan muxer: ${e.message}")
        } finally {
            muxer = null
            isMuxerStarted = false
            videoTrackIndex = -1
            audioTrackIndex = -1
            inputSurface = null
        }
    }

    companion object {
        private const val TAG = "HighBitrateMediaEncoder"
    }
}
