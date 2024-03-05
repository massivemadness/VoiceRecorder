package com.test.voicerecorder.codec

import android.os.SystemClock
import android.util.Log
import kotlin.concurrent.Volatile

object SoLoader {

    private const val TAG = "SoLoader"

    @Volatile
    private var loaded = false

    @Synchronized
    fun loadLibrary(): Boolean {
        if (!loaded) {
            loadLibraryImpl("c++_shared")
            loadLibraryImpl("tgxjni")
            loaded = true
        }
        return loaded
    }

    private fun loadLibraryImpl(library: String) {
        val ms = SystemClock.uptimeMillis()
        System.loadLibrary(library)
        Log.v(TAG, "Loaded " + library + " in " + (SystemClock.uptimeMillis() - ms) + "ms")
    }
}
