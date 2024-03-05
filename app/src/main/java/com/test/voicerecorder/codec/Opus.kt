package com.test.voicerecorder.codec

import java.nio.ByteBuffer

object Opus {

    fun init(): Boolean {
        return SoLoader.loadLibrary()
    }

    external fun startRecord(path: String?): Int
    external fun writeFrame(frame: ByteBuffer?, len: Int): Int
    external fun stopRecord()

    external fun openOpusFile(path: String?): Int
    external fun seekOpusFile(position: Float): Int
    external fun isOpusFile(path: String?): Int
    external fun readOpusFile(buffer: ByteBuffer?, capacity: Int, args: IntArray?)
    external fun getTotalPcmDuration(): Long

    external fun getWaveform(path: String?): ByteArray
    external fun getWaveform2(array: ShortArray?, length: Int): ByteArray
}
