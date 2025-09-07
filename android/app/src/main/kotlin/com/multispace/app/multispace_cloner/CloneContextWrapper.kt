package com.multispace.app.multispace_cloner

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.Log
import java.io.File

/**
 * CloneContextWrapper - Provides isolated storage for each cloned app instance
 * 
 * This wrapper ensures that each clone has its own:
 * - Files directory
 * - Cache directory  
 * - Database storage
 * - SharedPreferences
 * 
 * This prevents clones from sharing login data, cached tokens, and other sensitive information.
 */
class CloneContextWrapper(base: Context, private val cloneId: String) : ContextWrapper(base) {
    
    companion object {
        private const val TAG = "CloneContextWrapper"
    }
    
    /**
     * Creates and returns the root directory for this clone instance
     */
    private fun getCloneDir(): File {
        return try {
            // Try the safest way to resolve the app private data directory first
            val baseFiles: File? = try {
                baseContext.filesDir
            } catch (e: Exception) {
                Log.w(TAG, "filesDir unavailable while resolving clone dir for $cloneId", e)
                null
            }

            val parent: File = when {
                baseFiles?.parentFile != null -> baseFiles.parentFile
                else -> {
                    val cacheParent: File? = try {
                        baseContext.cacheDir?.parentFile
                    } catch (e: Exception) {
                        Log.w(TAG, "cacheDir unavailable while resolving clone dir for $cloneId", e)
                        null
                    }
                    cacheParent ?: File(baseContext.applicationInfo.dataDir)
                }
            }

            val dir = File(parent, "clone_$cloneId")
            if (!dir.exists()) {
                val created = dir.mkdirs()
                Log.d(TAG, "Created clone directory for $cloneId: $created at ${dir.absolutePath}")
            }
            dir
        } catch (e: Exception) {
            Log.e(TAG, "getCloneDir failed for $cloneId, falling back to app data dir", e)
            // Final fallback: use application data dir directly to avoid crash
            val fallbackParent = File(baseContext.applicationInfo.dataDir)
            val dir = File(fallbackParent, "clone_$cloneId")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            dir
        }
    }
    
    /**
     * Override files directory to provide isolated file storage per clone
     */
    override fun getFilesDir(): File {
        val files = File(getCloneDir(), "files")
        if (!files.exists()) {
            val created = files.mkdirs()
            Log.d(TAG, "Created files directory for clone $cloneId: $created")
        }
        return files
    }
    
    /**
     * Override cache directory to provide isolated cache storage per clone
     */
    override fun getCacheDir(): File {
        val cache = File(getCloneDir(), "cache")
        if (!cache.exists()) {
            val created = cache.mkdirs()
            Log.d(TAG, "Created cache directory for clone $cloneId: $created")
        }
        return cache
    }
    
    /**
     * Override database path to provide isolated database storage per clone
     */
    override fun getDatabasePath(name: String): File {
        val dbDir = File(getCloneDir(), "databases")
        if (!dbDir.exists()) {
            val created = dbDir.mkdirs()
            Log.d(TAG, "Created database directory for clone $cloneId: $created")
        }
        return File(dbDir, name)
    }
    
    /**
     * Override SharedPreferences to provide isolated preferences per clone
     * Each clone gets its own prefixed SharedPreferences
     */
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        val prefName = "${cloneId}_$name"
        Log.d(TAG, "Getting SharedPreferences for clone $cloneId: $prefName")
        return baseContext.getSharedPreferences(prefName, mode)
    }
    
    /**
     * Get the clone ID for this wrapper instance
     */
    fun getCloneId(): String = cloneId
    
    /**
     * Clear all data for this clone instance
     * Useful when removing a clone
     */
    fun clearCloneData(): Boolean {
        return try {
            val cloneDir = getCloneDir()
            val deleted = cloneDir.deleteRecursively()
            Log.i(TAG, "Cleared all data for clone $cloneId: $deleted")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear data for clone $cloneId", e)
            false
        }
    }
    
    /**
     * Get storage usage statistics for this clone
     */
    fun getStorageUsage(): Long {
        return try {
            val cloneDir = getCloneDir()
            calculateDirectorySize(cloneDir)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate storage usage for clone $cloneId", e)
            0L
        }
    }
    
    private fun calculateDirectorySize(dir: File): Long {
        var size = 0L
        if (dir.exists()) {
            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    size += file.length()
                }
            }
        }
        return size
    }
}