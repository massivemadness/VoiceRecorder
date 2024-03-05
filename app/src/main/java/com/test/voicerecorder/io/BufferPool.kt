package com.test.voicerecorder.io

object BufferPool {

    private val pool = arrayOfNulls<AudioBuffer>(4)

    fun obtain(capacity: Int): AudioBuffer {
        synchronized(pool) {
            for (i in (pool.size - 1)..0) {
                if (pool[i] != null) {
                    val cache = pool[i]
                    pool[i] = null
                    return cache!!
                }
            }
        }
        return AudioBuffer(capacity)
    }

    fun recycle(cached: AudioBuffer) {
        synchronized(pool) {
            for (i in 0..pool.size) {
                if (pool[i] == null) {
                    pool[i] = cached
                    cached.reset()
                    break
                }
            }
        }
    }
}