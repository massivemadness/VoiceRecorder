package com.test.voicerecorder.io

import java.nio.ByteBuffer

class AudioBuffer(capacity: Int) {

    @JvmField
    var buffer: ByteBuffer = ByteBuffer.allocateDirect(capacity)
    @JvmField
    var bufferBytes = ByteArray(capacity)
    @JvmField
    var size = 0
    @JvmField
    var finished = 0
    @JvmField
    var pcmOffset = 0

    fun fill(args: IntArray) {
        size = args[0]
        pcmOffset = args[1]
        finished = args[2]
    }

    fun reset() {
        if (size != 0) {
            buffer.rewind()
        }
        size = 0
        finished = 0
        pcmOffset = 0
    }
}
