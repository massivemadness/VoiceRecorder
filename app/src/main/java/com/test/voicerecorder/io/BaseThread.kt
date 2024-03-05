package com.test.voicerecorder.io

import android.R.attr.process
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import java.util.concurrent.CountDownLatch

class BaseThread : Thread() {

    private val syncLatch = CountDownLatch(1)

    @Volatile
    private var handler: Handler? = null

    init {
        start()
    }

    override fun run() {
        Looper.prepare()
        handler = Handler(Looper.myLooper()!!)
        syncLatch.countDown()
        Looper.loop()
    }

    fun post(r: Runnable) {
        try {
            syncLatch.await()
        } catch (e: InterruptedException) {
            Log.e(TAG, e.message, e)
        }
        handler?.post(r)
    }

    fun post(r: Runnable, delay: Long) {
        try {
            syncLatch.await()
        } catch (e: InterruptedException) {
            Log.e(TAG, e.message, e)
        }
        handler?.postDelayed(r, delay)
    }

    fun cancel(r: Runnable) {
        try {
            syncLatch.await()
        } catch (e: InterruptedException) {
            Log.e(TAG, e.message, e)
        }
        handler?.removeCallbacks(r)
    }

    companion object {
        private const val TAG = "BaseThread"
    }
}