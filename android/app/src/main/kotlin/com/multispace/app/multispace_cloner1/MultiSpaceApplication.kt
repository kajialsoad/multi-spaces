package com.multispace.app.multispace_cloner

import android.app.Application
import android.os.Build
import android.os.Looper
import android.util.Log
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.CookieManager

class MultiSpaceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        setupGlobalCrashHandler()
        setupPerProcessWebViewIsolation()
        prewarmAndClearWebArtifactsIfNeeded()
        Log.d(TAG, "MultiSpaceApplication initialized")
    }

    private fun setupGlobalCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
            } catch (_: Exception) {
                // no-op
            } finally {
                // Let default handler (if any) continue
            }
        }
    }

    private fun setupPerProcessWebViewIsolation() {
        try {
            // Assign a unique data directory suffix per process (best-effort). This helps isolate WebView data per app process.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val suffix = "multispace_${android.os.Process.myPid()}"
                // setDataDirectorySuffix must be called before any WebView instance is created.
                WebView.setDataDirectorySuffix(suffix)
                Log.d(TAG, "WebView data directory suffix set: $suffix")
            } else {
                Log.d(TAG, "WebView data directory suffix not supported on API < 28")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to set WebView data directory suffix: ${e.message}")
        }
    }

    private fun prewarmAndClearWebArtifactsIfNeeded() {
        try {
            val mainLooper = Looper.getMainLooper()
            val task = Runnable {
                try {
                    // Best-effort global cookie/webstorage clear at app start to align with fresh sign-in policy
                    val cm = CookieManager.getInstance()
                    cm.removeAllCookies(null)
                    cm.flush()
                    WebStorage.getInstance().deleteAllData()

                    // Create then immediately destroy a WebView to ensure cache dirs are created then cleaned later if needed
                    val wv = WebView(applicationContext)
                    wv.clearCache(true)
                    wv.clearFormData()
                    wv.clearHistory()
                    wv.clearSslPreferences()
                    wv.destroy()

                    Log.d(TAG, "Prewarmed WebView and cleared cookies/storage")
                } catch (inner: Throwable) {
                    Log.w(TAG, "Prewarm/clear web artifacts failed: ${inner.message}")
                }
            }
            if (Looper.myLooper() == mainLooper) task.run() else android.os.Handler(mainLooper).post(task)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed scheduling web prewarm/clear: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "MultiSpaceApp"
    }
}