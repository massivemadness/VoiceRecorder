package com.test.voicerecorder.audiofx

import android.media.AudioRecord
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log

object AudioEnhancer {

    var FT_USE_SYSTEM_AGC = true
    var FT_USE_SYSTEM_NS = true
    var FT_USE_SYSTEM_AEC = true

    private const val TAG = "AudioEnhancer"

    private var agc: AutomaticGainControl? = null
    private var ns: NoiseSuppressor? = null
    private var aec: AcousticEchoCanceler? = null

    fun acquireEnhancers(audioRecord: AudioRecord) {
        try {
            Log.d(TAG, "agc = $FT_USE_SYSTEM_AGC")
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(audioRecord.audioSessionId).apply {
                    Log.d(TAG, "AGC effect impl = " + descriptor.implementor)
                    enabled = FT_USE_SYSTEM_AGC
                }
            } else {
                Log.w(TAG, "AutomaticGainControl is not available on this device")
            }
        } catch (x: Throwable) {
            Log.e(TAG, "Error creating AutomaticGainControl", x)
        }
        try {
            Log.d(TAG, "ns = $FT_USE_SYSTEM_NS")
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(audioRecord.audioSessionId).apply {
                    Log.d(TAG, "NS effect impl = " + descriptor.implementor)
                    enabled = FT_USE_SYSTEM_NS
                }
            } else {
                Log.w(TAG, "NoiseSuppressor is not available on this device")
            }
        } catch (x: Throwable) {
            Log.e(TAG, "Error creating NoiseSuppressor", x)
        }
        try {
            Log.d(TAG, "aec = $FT_USE_SYSTEM_AEC")
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(audioRecord.audioSessionId).apply {
                    Log.d(TAG, "AEC effect impl = " + descriptor.implementor)
                    enabled = FT_USE_SYSTEM_AEC
                }
            } else {
                Log.w(TAG, "AcousticEchoCanceler is not available on this device")
            }
        } catch (x: Throwable) {
            Log.e(TAG, "Error creating AcousticEchoCanceler", x)
        }
    }

    fun releaseEffects() {
        agc?.release()
        agc = null

        ns?.release()
        ns = null

        aec?.release()
        aec = null
    }
}