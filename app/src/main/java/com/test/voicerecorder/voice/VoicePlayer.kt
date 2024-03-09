package com.test.voicerecorder.voice

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.IOException

object VoicePlayer {

    private const val TAG = "Audio"

    private var mediaPlayer: MediaPlayer? = null

    fun playAudio(tgAudio: TGAudio) {
        mediaPlayer = MediaPlayer()
        mediaPlayer?.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        try {
            mediaPlayer?.setDataSource(tgAudio.filePath)
            mediaPlayer?.setOnCompletionListener {
                it.stop()
                it.release()
            }
            mediaPlayer?.prepare()
            mediaPlayer?.start()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}