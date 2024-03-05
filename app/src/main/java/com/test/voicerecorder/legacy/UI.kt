package com.test.voicerecorder.legacy

import android.os.Handler
import android.os.Looper

object UI {

    private var appHandler: Handler? = null

    fun initApp() {
        appHandler = Handler(Looper.getMainLooper())
    }

    fun execute(r: Runnable) {
        if (inUiThread()) {
            r.run()
        } else {
            post(r)
        }
    }

    fun post(r: Runnable) {
        appHandler?.post(r)
    }

    fun post(r: Runnable, delay: Long) {
        appHandler?.postDelayed(r, delay)
    }

    fun cancel(r: Runnable) {
        appHandler?.removeCallbacks(r)
    }

    private fun inUiThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }
}
