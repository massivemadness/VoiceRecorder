package com.test.voicerecorder.io

import androidx.core.os.CancellationSignal

abstract class CancellableRunnable : Runnable {

    private val signal = CancellationSignal()

    val isPending: Boolean
        get() = !signal.isCanceled

    abstract fun act()

    override fun run() {
        if (!signal.isCanceled) {
            act()
        }
    }

    fun cancel() {
        signal.cancel()
    }
}