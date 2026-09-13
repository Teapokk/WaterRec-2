package com.waterrec.app

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecorderManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaProjection: MediaProjection? = null

    var isRecording = false
        private set
    var isPaused = false
        private set

    private var currentVideoFilePath: String = ""

    fun startRecording(
        projection: MediaProjection,
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        recordAudio: Boolean
    ) {
        if (isRecording) return

        mediaProjection = projection
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            if (recordAudio) {
                mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            mediaRecorder?.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            
            val videoDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "WaterRecVideos"
            )
            if (!videoDir.exists()) {
                videoDir.mkdirs()
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            currentVideoFilePath = File(videoDir, "REC_$timestamp.mp4").absolutePath
            
            mediaRecorder?.setOutputFile(currentVideoFilePath)
            mediaRecorder?.setVideoSize(width, height)
            mediaRecorder?.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (recordAudio) {
                mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mediaRecorder?.setAudioEncodingBitRate(128000)
                mediaRecorder?.setAudioSamplingRate(44100)
            }
            mediaRecorder?.setVideoEncodingBitRate(bitrate)
            mediaRecorder?.setVideoFrameRate(fps)

            mediaRecorder?.prepare()

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenRecorder",
                width,
                height,
                context.resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface,
                null,
                null
            )

            mediaRecorder?.start()
            isRecording = true
            isPaused = false
            Log.d("ScreenRecorder", "Started recording: $currentVideoFilePath")
        } catch (e: Exception) {
            Log.e("ScreenRecorder", "Failed to start recording", e)
            stopRecording()
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        
        try {
            mediaRecorder?.stop()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Gravação gravada com sucesso acesse documents/WaterRecVideos para poder pegar seus vídeos", android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("ScreenRecorder", "Failed to stop recording", e)
        } finally {
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
            
            virtualDisplay?.release()
            virtualDisplay = null
            
            mediaProjection?.stop()
            mediaProjection = null
            
            isRecording = false
            isPaused = false
        }
    }

    fun pauseRecording() {
        if (isRecording && !isPaused) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.pause()
                isPaused = true
            }
        }
    }

    fun resumeRecording() {
        if (isRecording && isPaused) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.resume()
                isPaused = false
            }
        }
    }
}
