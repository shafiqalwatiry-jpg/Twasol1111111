package com.tawasol.app.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Production-ready Android MediaRecorder wrapper for voice notes.
 *
 * Configured with:
 * - AudioSource: MIC
 * - OutputFormat: MPEG-4
 * - AudioEncoder: AAC
 * - Bitrate: 128 kbps
 * - Sampling Rate: 44.1 kHz
 * - Saves into app-private cache directory: /cache/voice_notes/
 * - Validates file existence and minimum size (> 500 bytes) on stop.
 */
class AudioRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var isRecording: Boolean = false
    private var recordingStartTimeMs: Long = 0L

    fun isCurrentlyRecording(): Boolean = isRecording

    /**
     * Initializes and starts recording audio to an app-private file.
     */
    fun startRecording(): Result<File> {
        if (isRecording) {
            cancelRecording()
        }

        return try {
            val voiceDir = File(context.cacheDir, "voice_notes").apply {
                if (!exists()) mkdirs()
            }
            val outputFile = File(voiceDir, "voice_${UUID.randomUUID()}_${System.currentTimeMillis()}.m4a")
            currentOutputFile = outputFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isRecording = true
            recordingStartTimeMs = System.currentTimeMillis()
            Result.success(outputFile)
        } catch (e: Exception) {
            cleanUpRecorder()
            currentOutputFile?.delete()
            currentOutputFile = null
            isRecording = false
            Result.failure(IOException("تعذر بدء تسجيل الصوت: ${e.localizedMessage ?: "تأكد من صلاحيات الميكروفون"}", e))
        }
    }

    /**
     * Stops the recorder and validates the recorded file.
     * Returns the recorded [File] and its elapsed duration in seconds.
     */
    fun stopRecording(): Result<Pair<File, Int>> {
        if (!isRecording || mediaRecorder == null) {
            return Result.failure(IllegalStateException("لا يوجد تسجيل صوتي قيد التشغيل"))
        }

        val elapsedMs = System.currentTimeMillis() - recordingStartTimeMs
        val durationSeconds = ((elapsedMs + 500) / 1000).toInt().coerceAtLeast(1)

        return try {
            try {
                mediaRecorder?.stop()
            } catch (e: RuntimeException) {
                // stop() can throw RuntimeException if called immediately without any audio data captured
                cleanUpRecorder()
                currentOutputFile?.delete()
                currentOutputFile = null
                isRecording = false
                return Result.failure(IOException("التسجيل الصوتي قصير جداً"))
            }

            cleanUpRecorder()
            isRecording = false

            val file = currentOutputFile
            if (file == null || !file.exists() || file.length() < 500L) {
                file?.delete()
                currentOutputFile = null
                return Result.failure(IOException("ملف الصوت فارغ أو لم يكتمل تسجيله بنجاح"))
            }

            currentOutputFile = null
            Result.success(Pair(file, durationSeconds))
        } catch (e: Exception) {
            cleanUpRecorder()
            currentOutputFile?.delete()
            currentOutputFile = null
            isRecording = false
            Result.failure(IOException("فشل حفظ التسجيل الصوتي: ${e.localizedMessage}", e))
        }
    }

    /**
     * Cancels recording immediately and removes any partially written file.
     */
    fun cancelRecording() {
        try {
            if (isRecording) {
                try {
                    mediaRecorder?.stop()
                } catch (_: Exception) {}
            }
        } finally {
            cleanUpRecorder()
            currentOutputFile?.delete()
            currentOutputFile = null
            isRecording = false
        }
    }

    private fun cleanUpRecorder() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {
        } finally {
            mediaRecorder = null
        }
    }
}
