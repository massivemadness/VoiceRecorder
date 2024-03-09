package com.test.voicerecorder.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.Arrays
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

object VoiceRecorder {

    private const val TAG = "Recorder"

    private var timer: Timer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var listener: Listener? = null
    private var currentFile: String? = null
    private var recordStart: Long = 0L
    private var samplesCount = 0
    private val recordSamples = ShortArray(1024)

    @SuppressLint("MissingPermission", "NewApi")
    fun record(context: Context, listener: Listener) {
        samplesCount = 0
        Arrays.fill(recordSamples, 0)

        val id = UUID.randomUUID().toString().substring(0, 4)
        currentFile = "/data/data/com.test.voicerecorder/cache/record-$id.ogg"
        this.listener = listener
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.OGG)
        mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
        mediaRecorder?.setAudioSamplingRate(48000) // 48hz
        mediaRecorder?.setAudioEncodingBitRate(32000) // 320kbps

        val outputFile = File(currentFile)
        mediaRecorder?.setOutputFile(outputFile.absolutePath)

        try {
            timer = Timer()
            timer?.scheduleAtFixedRate(
                object : TimerTask() {
                    override fun run() {
                        val amplitude = (mediaRecorder?.maxAmplitude ?: 0).toShort()
                        Log.d(TAG, "maxAmplitude = $amplitude")
                        // recordSamples[samplesCount] = amplitude
                        // samplesCount += 1
                    }
                },
                0,
                100
            )
            recordStart = System.currentTimeMillis()
            mediaRecorder?.prepare()
            mediaRecorder?.start()
            Log.d(TAG, "Recording started")
        } catch (e: IOException) {
            Log.e(TAG, "Error preparing MediaRecorder: " + e.message)
        }
    }

    fun save() {
        timer?.cancel()
        mediaRecorder?.stop()
        mediaRecorder?.release()
        mediaRecorder = null
        val duration = System.currentTimeMillis() - recordStart
        listener?.onSave(currentFile.orEmpty(), duration.toInt(), recordSamples)
    }

    interface Listener {
        fun onAmplitude(amplitude: Float)
        fun onProgress(duration: Long)
        fun onFail()
        fun onSave(file: String, duration: Int, waveform: ShortArray)
    }
}
