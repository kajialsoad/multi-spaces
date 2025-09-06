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

// Top-level utility to deterministically clear global cookies and WebStorage with a short latch
fun clearGlobalWebArtifactsSync(ctx: android.content.Context) {
    try {
        val mainLooper = android.os.Looper.getMainLooper()
        val task = Runnable {
            try {
                val cm = android.webkit.CookieManager.getInstance()
                val inner = java.util.concurrent.CountDownLatch(1)
                cm.removeAllCookies { inner.countDown() }
                try { inner.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (_: Throwable) {}
                cm.flush()
                android.webkit.WebStorage.getInstance().deleteAllData()
                try {
                    val wv = android.webkit.WebView(ctx.applicationContext)
                    wv.clearCache(true)
                    wv.clearFormData()
                    wv.clearHistory()
                    wv.clearSslPreferences()
                    wv.destroy()
                } catch (_: Throwable) {}
                android.util.Log.d("MultiSpaceApp", "Global web artifacts cleared (cookies + WebStorage)")
            } catch (innerEx: Throwable) {
                android.util.Log.w("MultiSpaceApp", "Global web artifacts clear failed: ${innerEx.message}")
            }
        }
        if (android.os.Looper.myLooper() == mainLooper) {
            task.run()
        } else {
            val latch = java.util.concurrent.CountDownLatch(1)
            android.os.Handler(mainLooper).post {
                try { task.run() } finally { latch.countDown() }
            }
            try { latch.await(2, java.util.concurrent.TimeUnit.SECONDS) } catch (_: Throwable) {}
        }
    } catch (e: Throwable) {
        android.util.Log.w("MultiSpaceApp", "Failed to schedule global web artifacts clear: ${e.message}")
    }
}