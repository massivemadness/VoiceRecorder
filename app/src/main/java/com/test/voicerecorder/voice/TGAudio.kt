package com.test.voicerecorder.voice

data class TGAudio(
    val filePath: String
) {

    private var seekProgress: Pair<Float, Int> = 0f to 0

    fun setSeekProgress(progress: Float, seconds: Int) {
        this.seekProgress = progress to seconds
    }

    fun getSeekProgress(): Float {
        return seekProgress.first
    }
}