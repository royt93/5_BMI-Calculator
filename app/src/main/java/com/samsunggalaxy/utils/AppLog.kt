package com.samsunggalaxy.utils

import android.util.Log
import com.samsunggalaxy.BuildConfig

/** L-02 — centralizes the debug-log tag/gate so call sites can't forget BuildConfig.DEBUG. */
object AppLog {
    private const val TAG = "roy93~"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, message, throwable)
    }
}
