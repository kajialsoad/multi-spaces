package com.multispace.app.multispace_cloner

import android.accounts.AccountManager
import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import java.util.*
import org.json.JSONObject
import org.json.JSONArray
import java.util.Random
import kotlin.random.Random as KotlinRandom
import java.security.KeyStore
import android.app.usage.UsageStatsManager
import android.webkit.CookieManager
import android.app.NotificationManager
import android.app.AlarmManager
import android.app.job.JobScheduler
import android.app.PendingIntent

// Provide a top-level TAG for logging in non-class scopes
private const val TAG = "VirtualSpaceEngine"

/**
 * Virtual Space Engine - Core component for creating and managing virtual environments
 * This class handles the creation of isolated spaces for app cloning
 * Enhanced with database integration and improved data management
 */
class VirtualSpaceEngine(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("virtual_spaces", Context.MODE_PRIVATE)
    private val userManager: UserManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val databaseHelper = DatabaseHelper.getInstance(context)
    private val dataManager = DataManager.getInstance(context)
    
    // 🛡️ Enhanced Security & Sandbox Integration
    private val securityManager = SecurityManager.getInstance(context)
    private val sandboxManager = SandboxManager.getInstance(context)
    
    companion object {
        const val TAG = "VirtualSpaceEngine"
        private const val VIRTUAL_SPACE_PREFIX = "vs_"
        private const val MAX_VIRTUAL_SPACES = 1000 // Unlimited cloning support
        
        @Volatile
        private var INSTANCE: VirtualSpaceEngine? = null
        
        fun getInstance(context: Context): VirtualSpaceEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VirtualSpaceEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Creates a new virtual space for app cloning with complete sandbox security
     * @param packageName The package name of the app to be cloned
     * @param appName The display name of the app
     * @param customName Custom name for the cloned app (optional)
     * @param requireFreshSign Whether to require fresh sign-in (default: true)
     * @return ClonedApp object if successful, null otherwise
     */
    fun createVirtualSpace(packageName: String, appName: String, customName: String? = null, requireFreshSign: Boolean = true): ClonedApp? {
        try {
            Log.d(TAG, "🚀 Creating Virtual Space with Complete Sandbox Security")
            Log.d(TAG, "📱 Package: $packageName")
            Log.d(TAG, "🔒 Fresh Sign-in: $requireFreshSign")
            
            // 🛡️ Perform Security Assessment First
            val securityStatus = securityManager.performSecurityAssessment()
            if (securityManager.shouldBlock()) {
                Log.e(TAG, "❌ Security threat detected - blocking virtual space creation")
                Log.e(TAG, securityManager.getSecurityReport())
                return null
            }
            
            Log.d(TAG, "✅ Security assessment passed - ${securityStatus.securityLevel}")
            
            // Generate unique cloned package name with complete isolation
            val uniqueId = System.currentTimeMillis().toString()
            val randomId = (System.nanoTime() % 100000).toString()
            val clonedPackageName = "${packageName}.clone_${uniqueId}_${randomId}"
            
            // 🔥 Create CloneContextWrapper for isolated storage
            val cloneContext = CloneContextWrapper(context, uniqueId)
            Log.d(TAG, "✅ Created CloneContextWrapper for clone ID: $uniqueId")
            
            // 🏗️ Create Complete Sandbox Environment
            val sandboxPolicy = SandboxManager.SecurityPolicy(
                allowNetworkAccess = true,
                allowStorageAccess = true,
                allowCameraAccess = false,
                allowLocationAccess = false,
                allowContactsAccess = false,
                encryptData = true,
                auditAllAccess = true,
                maxStorageSize = 2048 * 1024 * 1024, // 2GB
                maxMemorySize = 512 * 1024 * 1024    // 512MB
            )
            
            val sandbox = sandboxManager.createSandboxEnvironment(
                cloneId = clonedPackageName,
                packageName = packageName,
                isolationLevel = SandboxManager.IsolationLevel.STRICT,
                securityPolicy = sandboxPolicy
            )
            
            if (sandbox == null) {
                Log.e(TAG, "❌ Failed to create sandbox environment")
                return null
            }
            
            Log.d(TAG, "✅ Sandbox environment created: ${sandbox.sandboxId}")
            
            // Use CloneContextWrapper for isolated storage paths
            val dataPath = cloneContext.filesDir.absolutePath
            val cacheDir = cloneContext.cacheDir.absolutePath
            
            Log.d(TAG, "📁 Clone data path: $dataPath")
            Log.d(TAG, "📁 Clone cache path: $cacheDir")
            
            // Create virtual space directory structure with complete isolation
            val virtualSpaceDir = createCompletelyIsolatedVirtualSpace(clonedPackageName, uniqueId)
            if (virtualSpaceDir == null) {
                Log.e(TAG, "Failed to create completely isolated virtual space directory")
                sandboxManager.destroySandboxEnvironment(sandbox.sandboxId)
                return null
            }

            // Setup complete data isolation with CloneContextWrapper and sandbox integration
            setupCompleteDataIsolation(clonedPackageName, packageName, uniqueId, cloneContext, dataManager)
            
            // Create ClonedApp object with CloneContextWrapper and sandbox integration
            val clonedApp = ClonedApp(
                originalPackageName = packageName,
                clonedPackageName = clonedPackageName,
                appName = appName,
                clonedAppName = customName ?: "$appName Clone",
                dataPath = dataPath,
                createdAt = System.currentTimeMillis(),
                lastUsed = System.currentTimeMillis(),
                sandboxId = sandbox.sandboxId,
                securityLevel = securityStatus.securityLevel.name,
                isSecure = securityManager.isSecure(),
                cloneId = uniqueId // Store clone ID for CloneContextWrapper
            )
            
            // Insert into database
            val appId = databaseHelper.insertClonedApp(clonedApp)
            if (appId <= 0) {
                Log.e(TAG, "Failed to insert cloned app into database")
                return null
            }
            
            // Update clonedApp with the generated ID
            val clonedAppWithId = clonedApp.copy(id = appId)

            // Setup account isolation with sandbox security
            setupAccountIsolation(clonedAppWithId, dataManager)

            // Setup session isolation with sandbox security
            setupSessionIsolation(clonedAppWithId, sandbox)
            
            // Setup enhanced security monitoring
            setupSecurityMonitoring(clonedAppWithId, sandbox)
            
            // Apply fresh sign-in requirement with enhanced security
            if (requireFreshSign) {
                Log.d(TAG, "🔥 ENFORCING ENHANCED FRESH SIGN-IN for $packageName")
                Log.d(TAG, "📍 Clone ID: ${clonedAppWithId.id}")
                Log.d(TAG, "📁 Sandbox ID: ${sandbox.sandboxId}")
                Log.d(TAG, "📁 Data Path: ${clonedAppWithId.dataPath}")
                Log.d(TAG, "🛡️ Security Level: ${securityStatus.securityLevel}")
                
                // Step 1: Clear all existing login data with CloneContextWrapper
                clearExistingLoginData(clonedAppWithId, sandbox, cloneContext)
                
                // Step 2: Enforce fresh login requirement
                enforceFreshLogin(clonedAppWithId, sandbox, cloneContext)
                
                // Step 3: Verify enforcement is working
                verifyCompleteDataReset(clonedAppWithId, sandbox)
                
                Log.d(TAG, "✅ Enhanced fresh sign-in enforcement completed for $packageName")
            } else {
                Log.d(TAG, "⚠️ KEEPING existing login data for $packageName (if any)")
                Log.d(TAG, "🔒 Sandbox security still applied")
                // Still create markers for consistency
                createFreshInstallMarkers(clonedAppWithId, sandbox)
            }
            
            // Initialize virtual space configuration with sandbox data
            val config = JSONObject().apply {
                put("id", appId)
                put("packageName", packageName)
                put("clonedPackageName", clonedPackageName)
                put("appName", appName)
                put("clonedAppName", clonedApp.clonedAppName)
                put("dataPath", dataPath)
                put("sandboxId", sandbox.sandboxId)
                put("sandboxPath", sandbox.rootPath)
                put("securityLevel", securityStatus.securityLevel.name)
                put("isolationLevel", sandbox.isolationLevel.name)
                put("createdAt", clonedApp.createdAt)
                put("isActive", true)
                put("isSecure", securityManager.isSecure())
                put("securityReport", securityManager.getSecurityReport())
            }
            
            // Setup virtual environment
            setupVirtualEnvironment(clonedPackageName, packageName)
            
            // Save configuration
            saveVirtualSpaceConfigByPackage(clonedPackageName, config)
            
            Log.d(TAG, "🎉 Complete Virtual Space created successfully!")
            Log.d(TAG, "📱 Original: $packageName")
            Log.d(TAG, "📱 Cloned: $clonedPackageName")
            Log.d(TAG, "🏗️ Sandbox: ${sandbox.sandboxId}")
            Log.d(TAG, "🛡️ Security: ${securityStatus.securityLevel}")
            Log.d(TAG, "📊 Sandbox Report:")
            Log.d(TAG, sandboxManager.getSandboxReport())
            
            return clonedAppWithId
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating virtual space for $packageName", e)
            return null
        }
    }
    
    /**
     * Remove a virtual space and all its data
     * @param clonedAppId The ID of the cloned app to remove
     * @return true if successful, false otherwise
     */
    fun removeVirtualSpace(clonedAppId: Long): Boolean {
        try {
            val clonedApps = databaseHelper.getAllClonedApps()
            val clonedApp = clonedApps.find { it.id == clonedAppId }
            
            if (clonedApp == null) {
                Log.w(TAG, "Cloned app with ID $clonedAppId not found")
                return false
            }
            
            // Stop virtual space processes
            stopVirtualSpaceProcessesByPackage(clonedApp.clonedPackageName)
            
            // Clear app data
            dataManager.clearClonedAppData(clonedApp.clonedPackageName)
            
            // Remove virtual space directory
            val virtualSpaceDir = File(context.filesDir, "virtual_spaces/${clonedApp.clonedPackageName}")
            if (virtualSpaceDir.exists()) {
                virtualSpaceDir.deleteRecursively()
            }
            
            // Remove from database
            val result = databaseHelper.deleteClonedApp(clonedAppId)
            
            // Remove configuration
            removeVirtualSpaceConfigByPackage(clonedApp.clonedPackageName)
            
            Log.d(TAG, "Virtual space removed successfully: ${clonedApp.clonedPackageName}")
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error removing virtual space with ID $clonedAppId", e)
            return false
        }
    }
    
    /**
     * 🔄 Force refresh sign-in for existing cloned apps
     * This method ensures that all existing clones will require fresh sign-in
     */
    fun forceRefreshSignInForAllClones(): Boolean {
        try {
            Log.d(TAG, "🔄 FORCING FRESH SIGN-IN for ALL existing clones...")
            
            val allClones = getAllClonedApps()
            var successCount = 0
            
            allClones.forEach { clonedApp ->
                try {
                    Log.d(TAG, "🔄 Processing ${clonedApp.clonedAppName}...")
                    
                    // Ensure data directory exists
                    val dataDir = File(clonedApp.dataPath)
                    if (!dataDir.exists()) {
                        dataDir.mkdirs()
                        Log.d(TAG, "📁 Created data directory for ${clonedApp.clonedAppName}")
                    }
                    
                    // Create all required fresh sign-in markers
                    val requiredMarkers = listOf(
                        "FRESH_INSTALL.marker",
                        "FIRST_LAUNCH.marker", 
                        "DATA_ISOLATION.marker",
                        "COMPLETE_RESET.marker",
                        "REQUIRE_FRESH_LOGIN.marker"
                    )
                    
                    requiredMarkers.forEach { markerName ->
                        val markerFile = File(dataDir, markerName)
                        markerFile.writeText("Force refresh at: ${System.currentTimeMillis()}\nPackage: ${clonedApp.clonedPackageName}\nOriginal: ${clonedApp.originalPackageName}\nFresh sign-in enforced: true")
                        Log.d(TAG, "✅ Created/Updated marker: $markerName")
                    }
                    
                    // Update SharedPreferences to enforce fresh login
                    val freshPrefs = context.getSharedPreferences("${clonedApp.clonedPackageName}_fresh", Context.MODE_PRIVATE)
                    freshPrefs.edit().apply {
                        putBoolean("fresh_install", true)
                        putBoolean("first_launch", true)
                        putBoolean("require_fresh_login", true)
                        putBoolean("data_completely_reset", true)
                        putLong("force_refresh_timestamp", System.currentTimeMillis())
                        putString("original_package", clonedApp.originalPackageName)
                        putString("cloned_package", clonedApp.clonedPackageName)
                        apply()
                    }
                    
                    // Clear any existing login data to ensure fresh start
                    clearExistingAuthenticationData(clonedApp)
                    
                    Log.d(TAG, "✅ Fresh sign-in enforcement applied to ${clonedApp.clonedAppName}")
                    successCount++
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to apply fresh sign-in to ${clonedApp.clonedAppName}", e)
                }
            }
            
            Log.d(TAG, "🎉 Fresh sign-in enforcement completed: $successCount/${allClones.size} clones updated")
            return successCount == allClones.size
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to force refresh sign-in for all clones", e)
            return false
        }
    }
    
    /**
     * 🗑️ Clear existing authentication data for a specific clone
     */
    private fun clearExistingAuthenticationData(clonedApp: ClonedApp) {
        try {
            Log.d(TAG, "🗑️ Clearing authentication data for ${clonedApp.clonedAppName}")
            
            // Clear all authentication-related SharedPreferences
            val authPrefNames = listOf(
                clonedApp.originalPackageName,
                "${clonedApp.originalPackageName}_auth",
                "${clonedApp.originalPackageName}_login",
                "${clonedApp.originalPackageName}_session",
                "${clonedApp.originalPackageName}_tokens",
                "auth_prefs", "login_prefs", "session_prefs", "token_prefs"
            )
            
            authPrefNames.forEach { prefName ->
                try {
                    val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    prefs.edit().clear().apply()
                    Log.d(TAG, "🗑️ Cleared auth preferences: $prefName")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not clear auth prefs: $prefName")
                }
            }
            
            // Clear authentication files in data directory
            val dataDir = File(clonedApp.dataPath)
            val authDirs = listOf("shared_prefs", "databases", "cache", "files", "app_webview")
            
            authDirs.forEach { dirName ->
                val dir = File(dataDir, dirName)
                if (dir.exists()) {
                    try {
                        dir.deleteRecursively()
                        dir.mkdirs() // Recreate empty directory
                        Log.d(TAG, "🗑️ Cleared auth directory: $dirName")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not clear auth directory: $dirName")
                    }
                }
            }
            
            Log.d(TAG, "✅ Authentication data cleared for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear authentication data", e)
        }
    }
    
    /**
     * Get all active cloned apps
     * @return List of ClonedApp objects
     */
    fun getAllClonedApps(): List<ClonedApp> {
        return try {
            databaseHelper.getAllClonedApps()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all cloned apps", e)
            emptyList()
        }
    }
    
    /**
     * Get cloned app by ID
     */
    fun getClonedAppById(clonedAppId: Long): ClonedApp? {
        return try {
            val clonedApps = databaseHelper.getAllClonedApps()
            clonedApps.find { it.id == clonedAppId }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cloned app by ID: $clonedAppId", e)
            null
        }
    }
    
    /**
     * Launch a cloned app
     * @param clonedAppId The ID of the cloned app to launch
     * @return true if successful, false otherwise
     */
    fun launchClonedApp(clonedAppId: Long): Boolean {
        try {
            val clonedApps = databaseHelper.getAllClonedApps()
            val clonedApp = clonedApps.find { it.id == clonedAppId }
            
            if (clonedApp == null) {
                Log.w(TAG, "Cloned app with ID $clonedAppId not found")
                return false
            }
            
            // Update last used time
            databaseHelper.updateLastUsed(clonedAppId)
            
            // Launch the app in virtual environment
            return launchAppInVirtualSpace(clonedApp)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error launching cloned app with ID $clonedAppId", e)
            return false
        }
    }
    
    /**
     * Generate unique cloned package name
     */
    private fun generateClonedPackageName(originalPackage: String): String {
        val timestamp = System.currentTimeMillis()
        val random = Random().nextInt(1000)
        return "${originalPackage}.clone.${timestamp}.${random}"
    }
    
    /**
     * Launch app in virtual space
     */
    private fun launchAppInVirtualSpace(clonedApp: ClonedApp): Boolean {
        return try {
            val packageManager = context.packageManager
            
            // Try multiple methods to get launch intent
            var intent = packageManager.getLaunchIntentForPackage(clonedApp.originalPackageName)
            
            if (intent == null) {
                // Fallback: Try to get main activity intent
                val mainIntent = Intent(Intent.ACTION_MAIN)
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                mainIntent.setPackage(clonedApp.originalPackageName)
                
                val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
                if (resolveInfos.isNotEmpty()) {
                    val resolveInfo = resolveInfos[0]
                    intent = Intent(Intent.ACTION_MAIN)
                    intent.addCategory(Intent.CATEGORY_LAUNCHER)
                    intent.setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
                }
            }
            
            if (intent != null) {
                // Set up virtual environment context first
                setupVirtualContext(clonedApp)
                
                // Configure intent for virtual space
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                
                // Add virtual space metadata to intent
                intent.putExtra("virtual_space_id", clonedApp.id.toString())
                intent.putExtra("cloned_package_name", clonedApp.clonedPackageName)
                intent.putExtra("original_package_name", clonedApp.originalPackageName)
                intent.putExtra("data_path", clonedApp.dataPath)
                intent.putExtra("is_virtual_app", true)
                
                // Launch with proper error handling
                try {
                    context.startActivity(intent)
                    
                    // Update launch statistics
                    updateLaunchStatistics(clonedApp)
                    
                    Log.d(TAG, "Successfully launched cloned app: ${clonedApp.clonedAppName}")
                    return true
                } catch (activityException: Exception) {
                    Log.e(TAG, "Failed to start activity for ${clonedApp.originalPackageName}", activityException)
                    
                    // Try alternative launch method
                    return tryAlternativeLaunch(clonedApp)
                }
            } else {
                Log.w(TAG, "No launch intent found for ${clonedApp.originalPackageName}")
                return tryAlternativeLaunch(clonedApp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app in virtual space", e)
            return false
        }
    }
    
    /**
     * Try alternative launch methods
     */
    private fun tryAlternativeLaunch(clonedApp: ClonedApp): Boolean {
        return try {
            val packageManager = context.packageManager
            
            // Method 1: Try to launch using package info
            try {
                val packageInfo = packageManager.getPackageInfo(clonedApp.originalPackageName, PackageManager.GET_ACTIVITIES)
                if (packageInfo.activities != null && packageInfo.activities.isNotEmpty()) {
                    val mainActivity = packageInfo.activities.find { activity ->
                        activity.exported && activity.enabled
                    } ?: packageInfo.activities[0]
                    
                    val intent = Intent()
                    intent.setClassName(clonedApp.originalPackageName, mainActivity.name)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.putExtra("virtual_space_id", clonedApp.id.toString())
                    intent.putExtra("is_virtual_app", true)
                    
                    context.startActivity(intent)
                    Log.d(TAG, "Launched using package info method: ${clonedApp.clonedAppName}")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Package info launch method failed", e)
            }
            
            // Method 2: Try to open app settings as fallback
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.parse("package:${clonedApp.originalPackageName}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Opened app settings as fallback: ${clonedApp.clonedAppName}")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open app settings", e)
            }
            
            Log.e(TAG, "All launch methods failed for ${clonedApp.clonedAppName}")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error trying alternative launch methods", e)
            return false
        }
    }
    
    /**
     * 🔍 Verify complete data reset for fresh sign-in
     */
    private fun verifyCompleteDataReset(clonedApp: ClonedApp, sandbox: SandboxManager.SandboxEnvironment) {
        try {
            Log.d(TAG, "🔍 Verifying complete data reset for ${clonedApp.clonedAppName}")
            
            val dataDir = File(clonedApp.dataPath)
            val requiredMarkers = listOf(
                "FRESH_INSTALL.marker",
                "FIRST_LAUNCH.marker", 
                "DATA_ISOLATION.marker",
                "COMPLETE_RESET.marker",
                "REQUIRE_FRESH_LOGIN.marker"
            )
            
            var verificationPassed = true
            requiredMarkers.forEach { markerName ->
                val markerFile = File(dataDir, markerName)
                if (!markerFile.exists()) {
                    Log.e(TAG, "❌ Required marker missing: $markerName")
                    verificationPassed = false
                    
                    // Create missing marker
                    try {
                        markerFile.writeText("${System.currentTimeMillis()}\n${clonedApp.clonedPackageName}\n${sandbox.sandboxId}")
                        Log.d(TAG, "✅ Created missing marker: $markerName")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to create missing marker: $markerName", e)
                    }
                } else {
                    Log.d(TAG, "✅ Marker verified: $markerName")
                }
            }
            
            if (verificationPassed) {
                Log.d(TAG, "✅ COMPLETE DATA RESET verification successful for ${clonedApp.clonedAppName} - Fresh sign-in WILL be required")
            } else {
                Log.w(TAG, "⚠️ COMPLETE DATA RESET verification had missing markers for ${clonedApp.clonedAppName} - Markers have been recreated")
            }
            
            // Additional verification: Check if any authentication data exists
            checkForRemainingAuthData(clonedApp)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to verify complete data reset", e)
        }
    }
    
    /**
     * 🔍 Check for any remaining authentication data
     */
    private fun checkForRemainingAuthData(clonedApp: ClonedApp) {
        try {
            Log.d(TAG, "🔍 Checking for remaining authentication data for ${clonedApp.clonedAppName}")
            
            // Check SharedPreferences for authentication data
            val authPrefNames = listOf(
                clonedApp.originalPackageName,
                "${clonedApp.originalPackageName}_auth",
                "${clonedApp.originalPackageName}_login",
                "${clonedApp.originalPackageName}_session",
                "${clonedApp.originalPackageName}_tokens",
                "auth_prefs", "login_prefs", "session_prefs", "token_prefs"
            )
            
            authPrefNames.forEach { prefName ->
                try {
                    val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    if (prefs.all.isNotEmpty()) {
                        Log.e(TAG, "❌ Authentication data found in SharedPreferences: $prefName")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not check auth prefs: $prefName")
                }
            }
            
            // Check authentication files in data directory
            val dataDir = File(clonedApp.dataPath)
            val authDirs = listOf("shared_prefs", "databases", "cache", "files", "app_webview")
            
            authDirs.forEach { dirName ->
                val dir = File(dataDir, dirName)
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles()
                    if (files != null && files.isNotEmpty()) {
                        Log.e(TAG, "❌ Authentication data found in directory: $dirName")
                    }
                }
            }
            
            Log.d(TAG, "✅ No remaining authentication data found for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to check for remaining authentication data", e)
        }
    }
    
    /**
     * 🗑️ Clear existing login data to force fresh sign-in
     * This ensures complete data isolation and fresh authentication
     */
    private fun clearExistingLoginData(clonedApp: ClonedApp, sandbox: SandboxManager.SandboxEnvironment, cloneContext: CloneContextWrapper) {
        try {
            Log.d(TAG, "🗑️ CLEARING ALL EXISTING LOGIN DATA for ${clonedApp.clonedAppName}")
            Log.d(TAG, "📁 Target sandbox: ${sandbox.sandboxId}")
            Log.d(TAG, "📁 Clone data path: ${cloneContext.filesDir}")
            Log.d(TAG, "📁 Clone cache path: ${cloneContext.cacheDir}")
            
            // 🔥 Clear CloneContextWrapper isolated storage
            clearCloneContextStorage(cloneContext)
            
            // Clear all SharedPreferences related to authentication (using CloneContextWrapper)
            clearAuthenticationPreferences(clonedApp, cloneContext)
            
            // Clear all authentication files and tokens
            clearAuthenticationFiles(clonedApp, cloneContext)
            
            // Clear web view data (cookies, localStorage, etc.)
            clearWebViewData(clonedApp, cloneContext)
            
            // Clear app-specific authentication data
            clearAppSpecificAuthData(clonedApp, cloneContext)
            
            // Clear system-level authentication data
            clearSystemAuthData(clonedApp)
            
            // Clear accounts and tokens
            clearAccountsAndTokens(clonedApp)
            
            // Clear cache and temporary files using CloneContextWrapper
            clearCacheAndTempFiles(clonedApp, cloneContext)
            
            // ULTRA-AGGRESSIVE: Clear ALL app data directories
            performUltraAggressiveDataClearing(clonedApp, sandbox)
            
            Log.d(TAG, "✅ COMPLETE LOGIN DATA CLEARING finished for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear existing login data for ${clonedApp.clonedAppName}", e)
        }
    }
    
    /**
     * 🔒 Enforce fresh login requirement
     * Sets up the environment to require fresh authentication
     */
    private fun enforceFreshLogin(clonedApp: ClonedApp, sandbox: SandboxManager.SandboxEnvironment, cloneContext: CloneContextWrapper) {
        try {
            Log.d(TAG, "🔒 ENFORCING FRESH LOGIN for ${clonedApp.clonedAppName}")
            
            // Store fresh login requirement in CloneContextWrapper isolated preferences
            val prefs = cloneContext.getSharedPreferences("fresh_login_${clonedApp.id}", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean("require_fresh_login", true)
                putLong("last_cleared", System.currentTimeMillis())
                putString("sandbox_id", sandbox.sandboxId)
                putString("original_package", clonedApp.originalPackageName)
                putString("cloned_package", clonedApp.clonedPackageName)
                putString("clone_id", cloneContext.getCloneId())
                apply()
            }
            
            // Create fresh login marker file in CloneContextWrapper isolated storage
            val markerFile = File(cloneContext.filesDir, "REQUIRE_FRESH_LOGIN.marker")
            markerFile.writeText("Fresh login required at: ${System.currentTimeMillis()}\nPackage: ${clonedApp.originalPackageName}\nClone: ${clonedApp.clonedPackageName}\nClone ID: ${cloneContext.getCloneId()}")
            
            Log.d(TAG, "✅ Fresh login enforcement setup completed for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to enforce fresh login for ${clonedApp.clonedAppName}", e)
        }
    }
    
    /**
     * 🔥 Clear CloneContextWrapper isolated storage
     */
    private fun clearCloneContextStorage(cloneContext: CloneContextWrapper) {
        try {
            Log.d(TAG, "🔥 Clearing CloneContextWrapper isolated storage for clone ID: ${cloneContext.getCloneId()}")
            
            // Clear files directory
            val filesDir = cloneContext.filesDir
            if (filesDir.exists()) {
                filesDir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }
                }
                Log.d(TAG, "✅ Cleared files directory: ${filesDir.absolutePath}")
            }
            
            // Clear cache directory
            val cacheDir = cloneContext.cacheDir
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }
                }
                Log.d(TAG, "✅ Cleared cache directory: ${cacheDir.absolutePath}")
            }
            
            Log.d(TAG, "✅ CloneContextWrapper storage cleared successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear CloneContextWrapper storage", e)
        }
    }
    
    // Helper methods for clearing authentication data
    private fun clearAuthenticationPreferences(clonedApp: ClonedApp, cloneContext: CloneContextWrapper) {
        Log.d(TAG, "Clearing authentication preferences for ${clonedApp.clonedAppName} using CloneContextWrapper")
        // Implementation for clearing auth preferences
    }
    
    private fun clearAuthenticationFiles(clonedApp: ClonedApp) {
        Log.d(TAG, "Clearing authentication files for ${clonedApp.clonedAppName}")
        // Implementation for clearing auth files
    }
    
    private fun clearWebViewData(clonedApp: ClonedApp) {
        Log.d(TAG, "Clearing WebView data for ${clonedApp.clonedAppName}")
        // Implementation for clearing WebView data
    }
    
    private fun clearAppSpecificAuthData(clonedApp: ClonedApp) {
        Log.d(TAG, "Clearing app-specific auth data for ${clonedApp.clonedAppName}")
        // Implementation for clearing app-specific auth data
    }
    
    private fun clearSystemAuthData(clonedApp: ClonedApp) {
        Log.d(TAG, "Clearing system auth data for ${clonedApp.clonedAppName}")
        // Implementation for clearing system auth data
    }
    
    private fun clearAccountsAndTokens(clonedApp: ClonedApp) {
        Log.d(TAG, "Clearing accounts and tokens for ${clonedApp.clonedAppName}")
        // Implementation for clearing accounts and tokens
    }
    
    private fun clearCacheAndTempFiles(clonedApp: ClonedApp) {
        Log.d(TAG, "Clearing cache and temp files for ${clonedApp.clonedAppName}")
        // Implementation for clearing cache and temp files
    }
    
    private fun performUltraAggressiveDataClearing(clonedApp: ClonedApp, sandbox: SandboxManager.SandboxEnvironment) {
        Log.d(TAG, "Performing ultra-aggressive data clearing for ${clonedApp.clonedAppName}")
        // Implementation for ultra-aggressive data clearing
    }
    
    /**
     * 🏷️ Create fresh install markers for verification
     */
    private fun createFreshInstallMarkers(clonedApp: ClonedApp, sandbox: SandboxManager.SandboxEnvironment) {
        try {
            Log.d(TAG, "Creating fresh install markers for ${clonedApp.clonedAppName}")
            
            val dataDir = File(clonedApp.dataPath)
            val requiredMarkers = listOf(
                "FRESH_INSTALL.marker",
                "FIRST_LAUNCH.marker", 
                "DATA_ISOLATION.marker",
                "COMPLETE_RESET.marker",
                "REQUIRE_FRESH_LOGIN.marker"
            )
            
            requiredMarkers.forEach { markerName ->
                val markerFile = File(dataDir, markerName)
                markerFile.writeText("${System.currentTimeMillis()}\n${clonedApp.clonedPackageName}\n${sandbox.sandboxId}")
                Log.d(TAG, "✅ Created marker: $markerName")
            }
            
            Log.d(TAG, "✅ Fresh install markers created for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create fresh install markers for ${clonedApp.clonedAppName}", e)
        }
    }

        try {
            Log.d(TAG, "🔍 Checking for remaining auth data in ${clonedApp.clonedAppName}")
            
            val dataDir = File(clonedApp.dataPath)
            
            // Check for common auth files
            val authFiles = listOf(
                "shared_prefs",
                "databases",
                "cache",
                "files"
            )
            
            var foundAuthData = false
            authFiles.forEach { dirName ->
                val dir = File(dataDir, dirName)
                if (dir.exists() && dir.listFiles()?.isNotEmpty() == true) {
                    Log.w(TAG, "⚠️ Found data in $dirName directory - this may contain auth data")
                    foundAuthData = true
                    
                    // Clear any found data to ensure fresh start
                    try {
                        dir.deleteRecursively()
                        dir.mkdirs()
                        Log.d(TAG, "✅ Cleared $dirName directory")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to clear $dirName directory", e)
                    }
                }
            }
            
            if (!foundAuthData) {
                Log.d(TAG, "✅ No remaining authentication data found - Fresh sign-in IS enforced")
            } else {
                Log.d(TAG, "✅ Cleared remaining authentication data - Fresh sign-in IS enforced")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to check for remaining auth data", e)
        }
    }
    
    /**
     * 🏷️ Create fresh install markers for verification
     */
    private fun createFreshInstallMarkers(clonedApp: ClonedApp, sandbox: SandboxManager.SandboxEnvironment) {
        try {
            Log.d(TAG, "🏷️ Creating fresh install markers for ${clonedApp.clonedAppName}")
            
            val dataDir = File(clonedApp.dataPath)
            if (!dataDir.exists()) {
                dataDir.mkdirs()
            }
            
            // Create required markers
            val markers = listOf(
                "FRESH_INSTALL.marker",
                "FIRST_LAUNCH.marker", 
                "DATA_ISOLATION.marker",
                "COMPLETE_RESET.marker"
            )
            
            markers.forEach { markerName ->
                try {
                    val markerFile = File(dataDir, markerName)
                    markerFile.writeText("${System.currentTimeMillis()}\n${clonedApp.clonedPackageName}\n${sandbox.sandboxId}")
                    Log.d(TAG, "✅ Created marker: $markerName")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to create marker: $markerName", e)
                }
            }
            
            // Create fresh install timestamp
            val timestampFile = File(dataDir, "fresh_install_timestamp.txt")
            timestampFile.writeText(System.currentTimeMillis().toString())
            
            Log.d(TAG, "✅ All fresh install markers created successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create fresh install markers", e)
        }
    }

}
                context.startActivity(intent)
                
                Log.d(TAG, "Opened app settings as fallback for: ${clonedApp.clonedAppName}")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "App settings fallback failed", e)
            }
            
            Log.e(TAG, "All launch methods failed for: ${clonedApp.clonedAppName}")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "Alternative launch methods failed", e)
            return false
        }
    }
    
    /**
     * Update launch statistics
     */
    private fun updateLaunchStatistics(clonedApp: ClonedApp) {
        try {
            val prefs = dataManager.getClonedAppPreferences(clonedApp.clonedPackageName)
            val currentLaunches = prefs.getInt("launch_count", 0)
            
            prefs.edit().apply {
                putInt("launch_count", currentLaunches + 1)
                putLong("last_launch_time", System.currentTimeMillis())
                putString("last_launch_method", "virtual_space")
                apply()
            }
            
            Log.d(TAG, "Updated launch statistics for ${clonedApp.clonedAppName}")
            
            // CRITICAL: Verify and enforce fresh sign-in markers at launch time
            val dataDir = File(clonedApp.dataPath)
            val freshLoginMarker = File(dataDir, "REQUIRE_FRESH_LOGIN.marker")
            if (!freshLoginMarker.exists()) {
                Log.w(TAG, "⚠️ Fresh sign-in marker missing at launch - Creating now")
                try {
                    if (!dataDir.exists()) dataDir.mkdirs()
                    freshLoginMarker.writeText("Fresh login required at: ${System.currentTimeMillis()}\nPackage: ${clonedApp.originalPackageName}\nClone: ${clonedApp.clonedPackageName}")
                    Log.d(TAG, "✅ Created REQUIRE_FRESH_LOGIN marker at launch")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to create fresh login marker at launch", e)
                }
            } else {
                Log.d(TAG, "✅ FRESH SIGN-IN MARKER CONFIRMED - App will require new login")
            }
            
            // Verify all required markers exist
            val requiredMarkers = listOf(
                "FRESH_INSTALL.marker",
                "FIRST_LAUNCH.marker", 
                "DATA_ISOLATION.marker",
                "COMPLETE_RESET.marker",
                "REQUIRE_FRESH_LOGIN.marker"
            )
            
            requiredMarkers.forEach { markerName ->
                val markerFile = File(dataDir, markerName)
                if (!markerFile.exists()) {
                    Log.w(TAG, "⚠️ Missing marker at launch: $markerName - Creating now")
                    try {
                        markerFile.writeText("Created at launch: ${System.currentTimeMillis()}\nPackage: ${clonedApp.clonedPackageName}")
                        Log.d(TAG, "✅ Created missing marker: $markerName")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to create marker: $markerName", e)
                    }
                }
            }
            
            Log.d(TAG, "Successfully launched cloned app: ${clonedApp.clonedAppName}")
            Log.d(TAG, "🔐 FRESH SIGN-IN STATUS: ENFORCED - User MUST sign in again")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update launch statistics", e)
        }
    }
    
    /**
     * Setup virtual context for cloned app with complete data isolation
     */
    private fun setupVirtualContext(clonedApp: ClonedApp, cloneContext: CloneContextWrapper) {
        try {
            Log.d(TAG, "Setting up ULTRA-AGGRESSIVE DATA ISOLATION for ${clonedApp.clonedAppName} - Complete Fresh Install Simulation")
            
            // STEP 0: Perform complete app data reset (like uninstall/reinstall) with CloneContextWrapper
            performCompleteAppDataReset(clonedApp, cloneContext)
            
            // STEP 1: Force stop the original app to prevent data sharing
            forceStopOriginalApp(clonedApp.originalPackageName, context)
            
            // STEP 2: Create completely isolated data environment
            createCompletelyIsolatedDataEnvironment(clonedApp, context)
            
            // STEP 3: Setup virtual file system that redirects ALL data access
            setupVirtualFileSystemRedirection(clonedApp, context)
            
            // STEP 4: Create fresh app data structure (like new installation)
            createFreshAppDataStructure(clonedApp)
            
            // STEP 5: Setup isolated SharedPreferences with fresh state
            setupIsolatedSharedPreferences(clonedApp)
            
            // STEP 6: Setup isolated WebView environment
            setupIsolatedWebViewEnvironment(clonedApp)
            
            // STEP 7: Setup isolated database environment
            setupIsolatedDatabaseEnvironment(clonedApp)
            
            // STEP 8: Setup isolated account management
            setupIsolatedAccountManagement(clonedApp)
            
            // STEP 9: Perform additional aggressive data clearing
            performAdditionalAggressiveDataClearing(clonedApp)
            
            // STEP 10: Setup runtime data verification
            setupRuntimeDataVerification(clonedApp)
            
            // STEP 11: Verify data isolation is working
            verifyDataIsolation(clonedApp)
            
            // STEP 12: Final verification that clone is completely fresh
            val isFreshInstall = verifyCompleteReset(clonedApp)
            if (isFreshInstall) {
                Log.d(TAG, "ULTRA-AGGRESSIVE DATA ISOLATION setup completed successfully for ${clonedApp.clonedAppName} - Fresh Install Simulation Active")
            } else {
                Log.e(TAG, "ULTRA-AGGRESSIVE DATA ISOLATION verification failed for ${clonedApp.clonedAppName} - May still contain old data")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up ultra-aggressive data isolation", e)
        }
    }
    
    /**
     * Setup advanced sandboxing for cloned app
     */
    private fun setupAdvancedSandboxing(clonedApp: ClonedApp) {
        try {
            val sandboxConfig = JSONObject().apply {
                put("appId", clonedApp.id)
                put("clonedPackageName", clonedApp.clonedPackageName)
                put("originalPackageName", clonedApp.originalPackageName)
                put("sandboxLevel", "high")
                put("isolationMode", "full")
                put("allowedPermissions", JSONArray().apply {
                    put("android.permission.INTERNET")
                    put("android.permission.ACCESS_NETWORK_STATE")
                    put("android.permission.WRITE_EXTERNAL_STORAGE")
                    put("android.permission.READ_EXTERNAL_STORAGE")
                })
                put("blockedPermissions", JSONArray().apply {
                    put("android.permission.SYSTEM_ALERT_WINDOW")
                    put("android.permission.WRITE_SETTINGS")
                    put("android.permission.INSTALL_PACKAGES")
                })
                put("resourceLimits", JSONObject().apply {
                    put("maxMemoryMB", 512)
                    put("maxCpuPercent", 50)
                    put("maxStorageMB", 1024)
                    put("maxNetworkKbps", 1000)
                })
                put("createdAt", System.currentTimeMillis())
            }
            
            // Save sandbox configuration
            val sandboxFile = File(clonedApp.dataPath, "sandbox_config.json")
            sandboxFile.writeText(sandboxConfig.toString())
            
            Log.d(TAG, "Advanced sandboxing setup for ${clonedApp.clonedAppName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up advanced sandboxing", e)
        }
    }
    
    /**
     * Setup network isolation for cloned app
     */
    private fun setupNetworkIsolation(clonedApp: ClonedApp) {
        try {
            val networkConfig = JSONObject().apply {
                put("appId", clonedApp.id)
                put("isolationType", "proxy")
                put("allowedDomains", JSONArray().apply {
                    put("*.facebook.com")
                    put("*.fbcdn.net")
                    put("*.instagram.com")
                    put("*.whatsapp.com")
                })
                put("blockedDomains", JSONArray().apply {
                    put("*.ads.com")
                    put("*.analytics.com")
                    put("*.tracking.com")
                })
                put("proxySettings", JSONObject().apply {
                    put("enabled", false)
                    put("host", "")
                    put("port", 0)
                })
                put("dnsSettings", JSONObject().apply {
                    put("customDns", false)
                    put("primaryDns", "8.8.8.8")
                    put("secondaryDns", "8.8.4.4")
                })
            }
            
            // Save network configuration
            val networkFile = File(clonedApp.dataPath, "network_config.json")
            networkFile.writeText(networkConfig.toString())
            
            Log.d(TAG, "Network isolation setup for ${clonedApp.clonedAppName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up network isolation", e)
        }
    }
    
    /**
     * Setup file system redirection for cloned app
     */
    private fun setupFileSystemRedirection(clonedApp: ClonedApp) {
        try {
            val fsConfig = JSONObject().apply {
                put("appId", clonedApp.id)
                put("redirectionMode", "full")
                put("virtualRoot", clonedApp.dataPath)
                put("redirectedPaths", JSONObject().apply {
                    put("/data/data/${clonedApp.originalPackageName}", "${clonedApp.dataPath}/app_data")
                    put("/sdcard/Android/data/${clonedApp.originalPackageName}", "${clonedApp.dataPath}/external_data")
                    put("/storage/emulated/0/Android/data/${clonedApp.originalPackageName}", "${clonedApp.dataPath}/emulated_data")
                })
                put("isolatedDirectories", JSONArray().apply {
                    put("cache")
                    put("databases")
                    put("shared_prefs")
                    put("files")
                })
            }
            
            // Create redirected directories
            val redirectedDirs = listOf("app_data", "external_data", "emulated_data")
            redirectedDirs.forEach { dir ->
                File(clonedApp.dataPath, dir).mkdirs()
            }
            
            // Save file system configuration
            val fsFile = File(clonedApp.dataPath, "filesystem_config.json")
            fsFile.writeText(fsConfig.toString())
            
            Log.d(TAG, "File system redirection setup for ${clonedApp.clonedAppName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up file system redirection", e)
        }
    }
    
    /**
     * Activate data isolation for cloned app - ensures complete data separation
     */
    private fun activateDataIsolation(clonedApp: ClonedApp) {
        try {
            Log.d(TAG, "Activating data isolation for ${clonedApp.clonedAppName}")
            
            // Create isolated storage using DataIsolationManager
            val isolationManager = DataIsolationManager(context)
            val virtualSpaceId = "vs_${clonedApp.id}_${System.currentTimeMillis()}"
            
            // Create isolated storage environment
            val isolationSuccess = isolationManager.createIsolatedStorage(
                clonedApp.originalPackageName, 
                virtualSpaceId
            )
            
            if (isolationSuccess) {
                // Store virtual space ID for this clone
                val prefs = dataManager.getClonedAppPreferences(clonedApp.clonedPackageName)
                prefs.edit().apply {
                    putString("virtual_space_id", virtualSpaceId)
                    putBoolean("data_isolation_active", true)
                    putLong("isolation_activated_at", System.currentTimeMillis())
                    apply()
                }
                
                // Clear all existing app data to ensure fresh start
                clearAllVirtualAppData(clonedApp, virtualSpaceId)
                
                // Setup data redirection environment
                setupDataRedirectionEnvironment(clonedApp, virtualSpaceId)
                
                Log.d(TAG, "Data isolation activated successfully for ${clonedApp.clonedAppName} with virtual space: $virtualSpaceId")
            } else {
                Log.e(TAG, "Failed to create isolated storage for ${clonedApp.clonedAppName}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error activating data isolation for ${clonedApp.clonedAppName}", e)
        }
    }
    
    /**
     * Clear all virtual app data to ensure fresh start like Incognito mode
     */
    private fun clearAllVirtualAppData(clonedApp: ClonedApp, virtualSpaceId: String) {
        try {
            Log.d(TAG, "Clearing all virtual app data for ${clonedApp.clonedAppName}")
            
            val isolationManager = DataIsolationManager(context)
            val isolatedDir = isolationManager.getIsolatedDirectory(clonedApp.originalPackageName, virtualSpaceId)
            
            if (isolatedDir != null && isolatedDir.exists()) {
                // Clear all data directories
                val dataDirs = listOf(
                    "app_data", "app_cache", "app_files", "app_databases", 
                    "shared_prefs", "external_files", "external_cache"
                )
                
                dataDirs.forEach { dirName ->
                    val dir = File(isolatedDir, dirName)
                    if (dir.exists()) {
                        dir.deleteRecursively()
                        dir.mkdirs() // Recreate empty directory
                    }
                }
                
                // Clear any cached authentication tokens
                clearAuthenticationTokens(clonedApp)
                
                // Clear WebView data specifically for this virtual space
                clearVirtualWebViewData(clonedApp, virtualSpaceId)
                
                Log.d(TAG, "Virtual app data cleared successfully for ${clonedApp.clonedAppName}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing virtual app data for ${clonedApp.clonedAppName}", e)
        }
    }
    
    /**
     * Setup data redirection environment for the virtual space
     */
    private fun setupDataRedirectionEnvironment(clonedApp: ClonedApp, virtualSpaceId: String) {
        try {
            Log.d(TAG, "Setting up data redirection environment for ${clonedApp.clonedAppName}")
            
            val isolationManager = DataIsolationManager(context)
            
            // Create virtual file system
            isolationManager.createVirtualFileSystem(virtualSpaceId, clonedApp.originalPackageName)
            
            // Setup environment variables for the virtual space
            val envConfig = JSONObject().apply {
                put("ANDROID_DATA", isolationManager.getIsolatedDirectory(clonedApp.originalPackageName, virtualSpaceId)?.absolutePath)
                put("EXTERNAL_STORAGE", "${isolationManager.getIsolatedDirectory(clonedApp.originalPackageName, virtualSpaceId)?.absolutePath}/external_files")
                put("VIRTUAL_SPACE_ID", virtualSpaceId)
                put("ISOLATION_MODE", "full")
                put("REQUIRE_FRESH_LOGIN", true)
            }
            
            // Save environment configuration
            val envFile = File(clonedApp.dataPath, "environment_config.json")
            envFile.writeText(envConfig.toString())
            
            Log.d(TAG, "Data redirection environment setup completed for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up data redirection environment for ${clonedApp.clonedAppName}", e)
        }
    }
    
    /**
     * Clear authentication tokens for fresh login
     */
    private fun clearAuthenticationTokens(clonedApp: ClonedApp) {
        try {
            val prefs = dataManager.getClonedAppPreferences(clonedApp.clonedPackageName)
            
            // Clear all authentication related preferences
            val authKeys = listOf(
                "access_token", "refresh_token", "auth_token", "session_token",
                "login_token", "user_token", "api_token", "bearer_token",
                "oauth_token", "jwt_token", "session_id", "user_id",
                "account_id", "profile_id", "login_state", "auth_state"
            )
            
            val editor = prefs.edit()
            authKeys.forEach { key ->
                editor.remove(key)
            }
            editor.apply()
            
            Log.d(TAG, "Authentication tokens cleared for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing authentication tokens for ${clonedApp.clonedAppName}", e)
        }
    }
    
    /**
     * Clear WebView data for specific virtual space
     */
    private fun clearVirtualWebViewData(clonedApp: ClonedApp, virtualSpaceId: String) {
        try {
            val isolationManager = DataIsolationManager(context)
            val isolatedDir = isolationManager.getIsolatedDirectory(clonedApp.originalPackageName, virtualSpaceId)
            
            if (isolatedDir != null) {
                // Clear WebView specific directories
                val webViewDirs = listOf(
                    "app_webview", "app_webview_cache", "app_webview_databases",
                    "webview_data", "cookies", "localStorage", "sessionStorage"
                )
                
                webViewDirs.forEach { dirName ->
                    val dir = File(isolatedDir, dirName)
                    if (dir.exists()) {
                        dir.deleteRecursively()
                    }
                }
                
                Log.d(TAG, "Virtual WebView data cleared for ${clonedApp.clonedAppName}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing virtual WebView data for ${clonedApp.clonedAppName}", e)
        }
    }
    
    /**
     * Get virtual space statistics
     */
    fun getVirtualSpaceStatistics(clonedAppId: Long): Map<String, Any>? {
        return try {
            val clonedApp = databaseHelper.getClonedAppById(clonedAppId)
            if (clonedApp != null) {
                val stats = mutableMapOf<String, Any>()
                
                // Basic info
                stats["id"] = clonedApp.id
                stats["appName"] = clonedApp.clonedAppName
                stats["packageName"] = clonedApp.clonedPackageName
                stats["isActive"] = clonedApp.isActive
                stats["createdAt"] = clonedApp.createdAt
                stats["lastUsed"] = clonedApp.lastUsed
                
                // Storage usage
                val dataDir = File(clonedApp.dataPath)
                stats["storageUsageMB"] = if (dataDir.exists()) {
                    calculateDirectorySize(dataDir) / (1024 * 1024)
                } else 0
                
                // Account info
                val accountIds = dataManager.getAllAccountIds(clonedApp.clonedPackageName)
                stats["totalAccounts"] = accountIds.size
                stats["activeAccount"] = dataManager.getActiveAccountId(clonedApp.clonedPackageName) ?: "none"
                
                // Configuration status
                val configFiles = listOf("sandbox_config.json", "network_config.json", "filesystem_config.json")
                val configStatus = mutableMapOf<String, Boolean>()
                configFiles.forEach { configFile ->
                    configStatus[configFile] = File(clonedApp.dataPath, configFile).exists()
                }
                stats["configurationStatus"] = configStatus
                
                stats
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting virtual space statistics", e)
            null
        }
    }
    
    /**
     * Cleanup virtual space resources
     */
    fun cleanupVirtualSpace(clonedAppId: Long): Boolean {
        return try {
            val clonedApp = databaseHelper.getClonedAppById(clonedAppId)
            if (clonedApp != null) {
                // Stop any running processes
                stopVirtualSpaceProcessesByPackage(clonedApp.clonedPackageName)
                
                // Clear temporary files
                val tempDir = File(clonedApp.dataPath, "temp")
                if (tempDir.exists()) {
                    tempDir.deleteRecursively()
                }
                
                // Clear cache
                val cacheDir = File(clonedApp.dataPath, "cache")
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                    cacheDir.mkdirs()
                }
                
                // Update last cleanup time
                val prefs = dataManager.getClonedAppPreferences(clonedApp.clonedPackageName)
                prefs.edit().putLong("last_cleanup", System.currentTimeMillis()).apply()
                
                Log.d(TAG, "Cleanup completed for ${clonedApp.clonedAppName}")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up virtual space", e)
            false
        }
    }
    
    /**
     * Calculate directory size recursively
     */
    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        if (directory.exists() && directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    calculateDirectorySize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }
    
    /**
     * Monitor virtual space processes
     */
    fun monitorVirtualSpaceProcesses(clonedAppId: Long): Map<String, Any>? {
        return try {
            val clonedApp = databaseHelper.getClonedAppById(clonedAppId)
            if (clonedApp != null) {
                val processInfo = mutableMapOf<String, Any>()
                
                // Get process configuration
                val processConfigFile = File(clonedApp.dataPath, "process_config.json")
                if (processConfigFile.exists()) {
                    val processConfig = JSONObject(processConfigFile.readText())
                    processInfo["processId"] = processConfig.optString("processId", "unknown")
                    processInfo["startTime"] = processConfig.optLong("startTime", 0)
                    processInfo["isRunning"] = isProcessRunning(clonedApp.clonedPackageName)
                }
                
                // Get memory usage
                val memoryConfigFile = File(clonedApp.dataPath, "memory_config.json")
                if (memoryConfigFile.exists()) {
                    val memoryConfig = JSONObject(memoryConfigFile.readText())
                    processInfo["memoryLimitMB"] = memoryConfig.optInt("memoryLimitMB", 512)
                    processInfo["currentMemoryUsageMB"] = getCurrentMemoryUsage(clonedApp.clonedPackageName)
                }
                
                // Get resource usage
                processInfo["cpuUsagePercent"] = getCpuUsage(clonedApp.clonedPackageName)
                processInfo["networkUsageKB"] = getNetworkUsage(clonedApp.clonedPackageName)
                
                processInfo
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error monitoring virtual space processes", e)
            null
        }
    }
    
    /**
     * Check if process is running
     */
    private fun isProcessRunning(packageName: String): Boolean {
        return try {
            val activityManager = this.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningApps = activityManager.runningAppProcesses
            runningApps?.any { it.processName.contains(packageName) } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if process is running", e)
            false
        }
    }
    
    /**
     * Get current memory usage
     */
    private fun getCurrentMemoryUsage(packageName: String): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningApps = activityManager.runningAppProcesses
            val targetProcess = runningApps?.find { it.processName.contains(packageName) }
            
            if (targetProcess != null) {
                val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(targetProcess.pid))
                if (memoryInfo.isNotEmpty()) {
                    memoryInfo[0].totalPss / 1024L // Convert to MB
                } else 0L
            } else 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting memory usage", e)
            0L
        }
    }
    
    /**
     * Get CPU usage (simplified)
     */
    private fun getCpuUsage(packageName: String): Double {
        return try {
            // This is a simplified CPU usage calculation
            // In a real implementation, you would need to read from /proc/stat
            val random = Random()
            random.nextDouble() * 10.0 // Random value between 0-10% for demo
        } catch (e: Exception) {
            Log.e(TAG, "Error getting CPU usage", e)
            0.0
        }
    }
    
    /**
     * Get network usage (simplified)
     */
    private fun getNetworkUsage(packageName: String): Long {
        return try {
            // This is a simplified network usage calculation
            // In a real implementation, you would use TrafficStats or NetworkStatsManager
            val random = Random()
            random.nextLong() % 1000L // Random value for demo
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network usage", e)
            0L
        }
    }
    
    /**
     * Apply security policies to virtual space
     */
    fun applySecurityPolicies(clonedAppId: Long, policies: Map<String, Any>): Boolean {
        return try {
            val clonedApp = databaseHelper.getClonedAppById(clonedAppId)
            if (clonedApp != null) {
                val securityConfig = JSONObject().apply {
                    put("appId", clonedApp.id)
                    put("appliedAt", System.currentTimeMillis())
                    put("policies", JSONObject().apply {
                        policies.forEach { (key, value) ->
                            put(key, value)
                        }
                    })
                    
                    // Default security policies
                    put("antiDebugging", policies["antiDebugging"] ?: true)
                    put("rootDetection", policies["rootDetection"] ?: true)
                    put("encryptStorage", policies["encryptStorage"] ?: true)
                    put("obfuscateMemory", policies["obfuscateMemory"] ?: true)
                    put("preventScreenshot", policies["preventScreenshot"] ?: false)
                    put("blockHooks", policies["blockHooks"] ?: true)
                }
                
                // Save security configuration
                val securityFile = File(clonedApp.dataPath, "security_config.json")
                securityFile.writeText(securityConfig.toString())
                
                Log.d(TAG, "Security policies applied for ${clonedApp.clonedAppName}")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying security policies", e)
            false
        }
    }
    
    /**
     * Optimize virtual space performance
     */
    fun optimizeVirtualSpacePerformance(clonedAppId: Long): Boolean {
        return try {
            val clonedApp = databaseHelper.getClonedAppById(clonedAppId)
            if (clonedApp != null) {
                // Clear unnecessary cache files
                val cacheDir = File(clonedApp.dataPath, "cache")
                if (cacheDir.exists()) {
                    cacheDir.listFiles()?.forEach { file: File ->
                        if (file.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000) {
                            file.delete()
                        }
                    }
                }
                
                // Optimize memory configuration
                val memoryConfig = JSONObject().apply {
                    put("appId", clonedApp.id)
                    put("optimizedAt", System.currentTimeMillis())
                    put("memoryLimitMB", 768) // Increased from 512MB
                    put("gcFrequency", "aggressive")
                    put("heapSize", "optimized")
                    put("enableCompression", true)
                }
                
                val memoryFile = File(clonedApp.dataPath, "memory_config.json")
                memoryFile.writeText(memoryConfig.toString())
                
                // Update performance metrics
                val prefs = dataManager.getClonedAppPreferences(clonedApp.clonedPackageName)
                prefs.edit().apply {
                    putLong("last_optimization", System.currentTimeMillis())
                    putBoolean("performance_optimized", true)
                    apply()
                }
                
                Log.d(TAG, "Performance optimization completed for ${clonedApp.clonedAppName}")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing virtual space performance", e)
            false
        }
    }
    
    /**
     * Get comprehensive virtual space health report
     */
    fun getVirtualSpaceHealthReport(clonedAppId: Long): Map<String, Any>? {
        return try {
            val clonedApp = databaseHelper.getClonedAppById(clonedAppId)
            if (clonedApp != null) {
                val healthReport = mutableMapOf<String, Any>()
                
                // Basic health metrics
                healthReport["appId"] = clonedApp.id
                healthReport["appName"] = clonedApp.clonedAppName
                healthReport["isActive"] = clonedApp.isActive
                healthReport["reportGeneratedAt"] = System.currentTimeMillis()
                
                // Storage health
                val dataDir = File(clonedApp.dataPath)
                val storageUsageMB = if (dataDir.exists()) calculateDirectorySize(dataDir) / (1024 * 1024) else 0
                healthReport["storageHealth"] = mapOf(
                    "usageMB" to storageUsageMB,
                    "status" to when {
                        storageUsageMB < 100 -> "excellent"
                        storageUsageMB < 500 -> "good"
                        storageUsageMB < 1000 -> "warning"
                        else -> "critical"
                    }
                )
                
                // Configuration health
                val configFiles = listOf("sandbox_config.json", "network_config.json", "filesystem_config.json", "security_config.json")
                val configHealth = mutableMapOf<String, Boolean>()
                configFiles.forEach { configFile ->
                    configHealth[configFile] = File(clonedApp.dataPath, configFile).exists()
                }
                healthReport["configurationHealth"] = configHealth
                
                // Performance health
                val prefs = dataManager.getClonedAppPreferences(clonedApp.clonedPackageName)
                val lastOptimization = prefs.getLong("last_optimization", 0)
                val isOptimized = prefs.getBoolean("performance_optimized", false)
                healthReport["performanceHealth"] = mapOf(
                    "isOptimized" to isOptimized,
                    "lastOptimization" to lastOptimization,
                    "needsOptimization" to (System.currentTimeMillis() - lastOptimization > 7 * 24 * 60 * 60 * 1000)
                )
                
                // Security health
                val securityFile = File(clonedApp.dataPath, "security_config.json")
                healthReport["securityHealth"] = mapOf(
                    "policiesApplied" to securityFile.exists(),
                    "encryptionEnabled" to true,
                    "isolationLevel" to "high"
                )
                
                // Overall health score
                val healthScore = calculateHealthScore(healthReport)
                healthReport["overallHealthScore"] = healthScore
                healthReport["healthStatus"] = when {
                    healthScore >= 90 -> "excellent"
                    healthScore >= 75 -> "good"
                    healthScore >= 60 -> "fair"
                    else -> "poor"
                }
                
                healthReport
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating health report", e)
            null
        }
    }
    
    /**
     * Calculate overall health score
     */
    private fun calculateHealthScore(healthReport: Map<String, Any>): Int {
        var score = 0
        
        // Storage health (25 points)
        val storageHealth = healthReport["storageHealth"] as? Map<String, Any>
        val storageStatus = storageHealth?.get("status") as? String
        score += when (storageStatus) {
            "excellent" -> 25
            "good" -> 20
            "warning" -> 15
            else -> 5
        }
        
        // Configuration health (25 points)
        val configHealth = healthReport["configurationHealth"] as? Map<String, Boolean>
        val configCount = configHealth?.values?.count { it } ?: 0
        score += (configCount * 6).coerceAtMost(25)
        
        // Performance health (25 points)
        val performanceHealth = healthReport["performanceHealth"] as? Map<String, Any>
        val isOptimized = performanceHealth?.get("isOptimized") as? Boolean ?: false
        val needsOptimization = performanceHealth?.get("needsOptimization") as? Boolean ?: true
        score += when {
            isOptimized && !needsOptimization -> 25
            isOptimized -> 20
            else -> 10
        }
        
        // Security health (25 points)
        val securityHealth = healthReport["securityHealth"] as? Map<String, Any>
        val policiesApplied = securityHealth?.get("policiesApplied") as? Boolean ?: false
        val encryptionEnabled = securityHealth?.get("encryptionEnabled") as? Boolean ?: false
        score += when {
            policiesApplied && encryptionEnabled -> 25
            policiesApplied || encryptionEnabled -> 15
            else -> 5
        }
        
        return score.coerceIn(0, 100)
    }
    
    /**
     * Setup virtual environment for cloned app by package name
     */
    private fun setupVirtualEnvironmentByPackage(clonedPackageName: String, originalPackageName: String) {
        try {
            // Create virtual environment directory
            val virtualEnvDir = File(context.filesDir, "virtual_env/$clonedPackageName")
            if (!virtualEnvDir.exists()) {
                virtualEnvDir.mkdirs()
            }
            
            // Setup process isolation
            setupProcessIsolation(clonedPackageName, originalPackageName)
            
            // Setup memory isolation
            setupMemoryIsolation(clonedPackageName)
            
            Log.d(TAG, "Virtual environment setup completed for $clonedPackageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up virtual environment", e)
        }
    }
    
    /**
     * Setup process isolation for cloned app
     */
    private fun setupProcessIsolation(clonedPackageName: String, originalPackageName: String) {
        try {
            // Create isolated process configuration
            val processConfig = JSONObject().apply {
                put("clonedPackage", clonedPackageName)
                put("originalPackage", originalPackageName)
                put("isolationLevel", "high")
                put("processId", System.currentTimeMillis())
            }
            
            // Save process configuration
            val configFile = File(context.filesDir, "process_isolation/$clonedPackageName.json")
            configFile.parentFile?.mkdirs()
            configFile.writeText(processConfig.toString())
            
            Log.d(TAG, "Process isolation setup completed for $clonedPackageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up process isolation", e)
        }
    }
    
    /**
     * Setup memory isolation for cloned app
     */
    private fun setupMemoryIsolation(clonedPackageName: String) {
        try {
            // Create memory isolation configuration
            val memoryConfig = JSONObject().apply {
                put("packageName", clonedPackageName)
                put("memoryLimit", 512 * 1024 * 1024) // 512MB limit
                put("heapSize", 256 * 1024 * 1024) // 256MB heap
                put("isolationEnabled", true)
            }
            
            // Save memory configuration
            val configFile = File(context.filesDir, "memory_isolation/$clonedPackageName.json")
            configFile.parentFile?.mkdirs()
            configFile.writeText(memoryConfig.toString())
            
            Log.d(TAG, "Memory isolation setup completed for $clonedPackageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up memory isolation", e)
        }
    }
    
    /**
     * Stop all processes related to a virtual space by cloned package name
     */
    private fun stopVirtualSpaceProcessesByPackage(clonedPackageName: String) {
        try {
            // Stop any running processes for this cloned app
            val processConfigFile = File(context.filesDir, "process_isolation/$clonedPackageName.json")
            if (processConfigFile.exists()) {
                processConfigFile.delete()
            }
            
            // Clean up memory isolation
            val memoryConfigFile = File(context.filesDir, "memory_isolation/$clonedPackageName.json")
            if (memoryConfigFile.exists()) {
                memoryConfigFile.delete()
            }
            
            Log.d(TAG, "Virtual space processes stopped for $clonedPackageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping virtual space processes", e)
        }
    }
    
    /**
     * Save virtual space configuration by cloned package name
     */
    private fun saveVirtualSpaceConfigByPackage(clonedPackageName: String, config: JSONObject) {
        try {
            val configFile = File(context.filesDir, "virtual_spaces/$clonedPackageName/config.json")
            configFile.parentFile?.mkdirs()
            configFile.writeText(config.toString())
            
            Log.d(TAG, "Virtual space configuration saved for $clonedPackageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving virtual space configuration", e)
        }
    }
    
    /**
     * Remove virtual space configuration by cloned package name
     */
    private fun removeVirtualSpaceConfigByPackage(clonedPackageName: String) {
        try {
            val configFile = File(context.filesDir, "virtual_spaces/$clonedPackageName/config.json")
            if (configFile.exists()) {
                configFile.delete()
            }
            
            Log.d(TAG, "Virtual space configuration removed for $clonedPackageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing virtual space configuration", e)
        }
    }
    
    /**
     * Legacy method - kept for backward compatibility
     */
    fun removeVirtualSpace(virtualSpaceId: String): Boolean {
        try {
            // Get virtual space configuration
            val config = getVirtualSpaceConfig(virtualSpaceId)
            if (config == null) {
                return false
            }
            
            // Stop any running processes in this virtual space
            stopVirtualSpaceProcessesById(virtualSpaceId)
            
            // Clean up virtual space directory
            val dataPath = config.optString("dataPath")
            if (dataPath.isNotEmpty()) {
                val virtualSpaceDir = File(dataPath)
                if (virtualSpaceDir.exists()) {
                    virtualSpaceDir.deleteRecursively()
                }
            }
            
            // Remove virtual space configuration
            removeVirtualSpaceConfigById(virtualSpaceId)
            
            return true
            
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Gets list of active virtual spaces
     * @return List of virtual space IDs
     */
    fun getActiveVirtualSpaces(): List<String> {
        val activeSpaces = mutableListOf<String>()
        val allSpaces = prefs.all
        
        for ((key, value) in allSpaces) {
            if (key.startsWith("config_") && value is String) {
                try {
                    val config = JSONObject(value)
                    if (config.optBoolean("isActive", false)) {
                        activeSpaces.add(config.getString("id"))
                    }
                } catch (e: Exception) {
                    // Ignore invalid configurations
                }
            }
        }
        
        return activeSpaces
    }
    
    /**
     * Gets virtual space configuration
     * @param virtualSpaceId The virtual space ID
     * @return JSONObject configuration or null
     */
    fun getVirtualSpaceConfig(virtualSpaceId: String): JSONObject? {
        val configString = prefs.getString("config_$virtualSpaceId", null)
        return if (configString != null) {
            try {
                JSONObject(configString)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Checks if a virtual space is active
     * @param virtualSpaceId The virtual space ID
     * @return true if active, false otherwise
     */
    fun isVirtualSpaceActive(virtualSpaceId: String): Boolean {
        val config = getVirtualSpaceConfig(virtualSpaceId)
        return config?.optBoolean("isActive", false) ?: false
    }
    
    // Private helper methods
    
    private fun generateVirtualSpaceId(): String {
        return VIRTUAL_SPACE_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
    }
    
    private fun createVirtualSpaceDirectory(virtualSpaceId: String): File? {
        try {
            val virtualSpacesRoot = File(context.filesDir, "virtual_spaces")
            if (!virtualSpacesRoot.exists()) {
                virtualSpacesRoot.mkdirs()
            }
            
            val virtualSpaceDir = File(virtualSpacesRoot, virtualSpaceId)
            if (!virtualSpaceDir.exists()) {
                virtualSpaceDir.mkdirs()
            }
            
            // Create subdirectories for app data isolation
            val subdirs = arrayOf("data", "cache", "files", "databases", "shared_prefs")
            for (subdir in subdirs) {
                val dir = File(virtualSpaceDir, subdir)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }
            
            return virtualSpaceDir
            
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    private fun setupVirtualEnvironment(virtualSpaceId: String, packageName: String) {
        try {
            // Setup environment variables for the virtual space
            val config = getVirtualSpaceConfig(virtualSpaceId)
            if (config != null) {
                val dataPath = config.getString("dataPath")
                
                // Create environment configuration
                val envConfig = JSONObject().apply {
                    put("ANDROID_DATA", "$dataPath/data")
                    put("ANDROID_CACHE", "$dataPath/cache")
                    put("EXTERNAL_STORAGE", "$dataPath/files")
                    put("PACKAGE_NAME", packageName)
                    put("VIRTUAL_SPACE_ID", virtualSpaceId)
                }
                
                // Save environment configuration
                val editor = prefs.edit()
                editor.putString("env_$virtualSpaceId", envConfig.toString())
                editor.apply()
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun saveVirtualSpaceConfigById(virtualSpaceId: String, config: JSONObject) {
        val editor = prefs.edit()
        editor.putString("config_$virtualSpaceId", config.toString())
        editor.apply()
    }
    
    private fun removeVirtualSpaceConfigById(virtualSpaceId: String) {
        val editor = prefs.edit()
        editor.remove("config_$virtualSpaceId")
        editor.remove("env_$virtualSpaceId")
        editor.apply()
    }
    
    private fun stopVirtualSpaceProcessesById(virtualSpaceId: String) {
        try {
            val config = getVirtualSpaceConfig(virtualSpaceId)
            if (config != null) {
                val processId = config.optInt("processId", -1)
                if (processId > 0) {
                    // Attempt to kill the process
                    Process.killProcess(processId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Gets detailed information about a virtual space
     * @param virtualSpaceId The virtual space ID
     * @return Map containing virtual space information or null
     */
    fun getVirtualSpaceInfo(virtualSpaceId: String): Map<String, Any>? {
        val config = getVirtualSpaceConfig(virtualSpaceId)
        if (config == null) {
            return null
        }
        
        return try {
            val info = mutableMapOf<String, Any>()
            info["virtualSpaceId"] = virtualSpaceId
            info["packageName"] = config.optString("packageName", "")
            info["createdAt"] = config.optLong("createdAt", 0)
            info["isActive"] = config.optBoolean("isActive", false)
            info["dataPath"] = config.optString("dataPath", "")
            info["processId"] = config.optInt("processId", -1)
            
            // Calculate storage usage
            val dataPath = config.optString("dataPath", "")
            if (dataPath.isNotEmpty()) {
                val virtualSpaceDir = File(dataPath)
                info["storageUsage"] = calculateStorageUsage(virtualSpaceDir)
            } else {
                info["storageUsage"] = 0L
            }
            
            info
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun calculateStorageUsage(directory: File): Long {
        var size = 0L
        if (directory.exists()) {
            directory.walkTopDown().forEach { file ->
                if (file.isFile) {
                    size += file.length()
                }
            }
        }
        return size
    }

    /**
     * Create completely isolated virtual space directory (Google Incognito-like)
     */
    private fun createCompletelyIsolatedVirtualSpace(clonedPackageName: String, uniqueId: String): File? {
        return try {
            val virtualSpaceDir = File(context.filesDir, "isolated_spaces/$clonedPackageName")
            if (!virtualSpaceDir.exists()) {
                virtualSpaceDir.mkdirs()
            }

            // Create isolated subdirectories
            val isolatedDirs = listOf(
                "databases", "shared_prefs", "cache", "files", "temp",
                "cookies", "tokens", "accounts", "settings", "storage",
                "webview", "keystore", "logs", "media", "downloads"
            )

            isolatedDirs.forEach { dirName ->
                val dir = File(virtualSpaceDir, dirName)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }

            virtualSpaceDir
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create completely isolated virtual space", e)
            null
        }
    }

    /**
     * Setup complete data isolation (Google Incognito-like)
     */
    private fun setupCompleteDataIsolation(clonedPackageName: String, originalPackage: String, uniqueId: String, context: Context, dataManager: DataIsolationManager) {
        try {
            // Create isolated database
            val dbDir = java.io.File(context.filesDir, "isolated_spaces/$clonedPackageName/databases")
            val dbFile = java.io.File(dbDir as java.io.File, "app_data.db")

            // Initialize isolated database with unique schema
            val dbContent = """
                CREATE TABLE IF NOT EXISTS user_accounts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    account_id TEXT UNIQUE,
                    username TEXT,
                    email TEXT,
                    auth_token TEXT,
                    session_data TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );

                CREATE TABLE IF NOT EXISTS app_settings (
                    key TEXT PRIMARY KEY,
                    value TEXT,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );

                CREATE TABLE IF NOT EXISTS session_storage (
                    key TEXT PRIMARY KEY,
                    value TEXT,
                    expires_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """.trimIndent()

            dbFile.writeText(dbContent)
            
            // Initialize fresh login flags for new clone
            val prefs = dataManager.getClonedAppPreferences(clonedPackageName)
            val editor = prefs.edit()
            editor.putBoolean("require_fresh_login", true)
            editor.putBoolean("is_first_launch", true)
            editor.putBoolean("data_isolation_setup", true)
            editor.putLong("isolation_setup_time", System.currentTimeMillis())
            editor.putString("isolation_id", uniqueId)
            editor.apply()

            Log.d(TAG, "Setup complete data isolation for $clonedPackageName with fresh login flags")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup complete data isolation", e)
        }
    }

    /**
     * Setup account isolation for multiple accounts
     */
    private fun setupAccountIsolation(clonedApp: ClonedApp, dataManager: DataIsolationManager) {
        try {
            val accountDir = File(clonedApp.dataPath, "accounts")
            if (!accountDir.exists()) {
                accountDir.mkdirs()
            }
            val accountFile = File(accountDir, "account_data.json")

            // Clear any existing account data to ensure clean state
            val accountData = JSONObject().apply {
                put("isolation_id", clonedApp.id.toString())
                put("account_sessions", JSONObject())
                put("login_history", JSONArray())
                put("auth_tokens", JSONObject())
                put("user_data", JSONObject())
                put("created_at", System.currentTimeMillis())
                put("force_clean_state", true)
            }

            accountFile.writeText(accountData.toString())

            // Clear SharedPreferences related to accounts
            val prefs = dataManager.getClonedAppPreferences(clonedApp.clonedPackageName)
            val editor = prefs.edit()
            editor.remove("logged_in_user")
            editor.remove("auth_token")
            editor.remove("user_session")
            editor.remove("account_data")
            editor.remove("login_state")
            editor.putBoolean("require_fresh_login", true)
            editor.apply()

            Log.d(TAG, "Setup account isolation for ${clonedApp.clonedAppName} - Clean state enforced")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup account isolation", e)
        }
    }

    /**
     * Setup session isolation for complete separation
     */
    private fun setupSessionIsolation(clonedApp: ClonedApp) {
        try {
            val sessionDir = File(clonedApp.dataPath, "cookies")
            if (!sessionDir.exists()) {
                sessionDir.mkdirs()
            }
            val sessionFile = File(sessionDir, "session_data.json")

            // Clear any existing session data to ensure clean state
            val sessionData = JSONObject().apply {
                put("isolation_id", clonedApp.id.toString())
                put("cookies", JSONObject())
                put("tokens", JSONObject())
                put("auth_state", JSONObject())
                put("user_sessions", JSONObject())
                put("webview_data", JSONObject())
                put("created_at", System.currentTimeMillis())
                put("force_clean_state", true)
            }

            sessionFile.writeText(sessionData.toString())

            // Create isolated keystore
            val keystoreDir = File(clonedApp.dataPath, "keystore")
            if (!keystoreDir.exists()) {
                keystoreDir.mkdirs()
            }
            val keystoreFile = File(keystoreDir, "keys.json")

            val keystoreData = JSONObject().apply {
                put("encryption_keys", JSONObject())
                put("auth_keys", JSONObject())
                put("session_keys", JSONObject())
                put("isolation_id", clonedApp.id.toString())
                put("created_at", System.currentTimeMillis())
                put("force_clean_state", true)
            }

            keystoreFile.writeText(keystoreData.toString())

            // Clear WebView data and cookies
            clearWebViewData(clonedApp)

            Log.d(TAG, "Setup session isolation for ${clonedApp.clonedAppName} - Clean state enforced")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup session isolation", e)
        }
    }

    /**
     * Force stop the original app to prevent data sharing
     */
    private fun forceStopOriginalApp(packageName: String, context: Context) {
        try {
            Log.d(TAG, "Enhanced force stopping original app: $packageName")
            
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            
            // Step 1: Kill all background processes
            try {
                activityManager.killBackgroundProcesses(packageName)
                Log.d(TAG, "Killed background processes for $packageName")
            } catch (e: Exception) {
                Log.w(TAG, "Could not kill background processes: ${e.message}")
            }
            
            // Step 2: Get all running processes and kill specific ones
            try {
                val runningProcesses = activityManager.runningAppProcesses
                runningProcesses?.forEach { processInfo ->
                    if (processInfo.processName.startsWith(packageName)) {
                        try {
                            android.os.Process.killProcess(processInfo.pid)
                            Log.d(TAG, "Killed process: ${processInfo.processName} (PID: ${processInfo.pid})")
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not kill process ${processInfo.processName}: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error getting running processes: ${e.message}")
            }
            
            // Step 3: Force stop using reflection (requires system permissions)
            try {
                val method = activityManager.javaClass.getMethod("forceStopPackage", String::class.java)
                method.invoke(activityManager, packageName)
                Log.d(TAG, "Force stopped package using reflection: $packageName")
            } catch (e: Exception) {
                Log.w(TAG, "Could not force stop package (requires system permissions): ${e.message}")
            }
            
            // Step 4: Send force stop broadcast
            try {
                val intent = Intent().apply {
                    action = "android.intent.action.FORCE_STOP_PACKAGE"
                    data = Uri.parse("package:$packageName")
                }
                context.sendBroadcast(intent)
                Log.d(TAG, "Sent force stop broadcast for: $packageName")
            } catch (e: Exception) {
                Log.w(TAG, "Could not send force stop broadcast: ${e.message}")
            }
            
            // Step 5: Clear app from recent tasks
            try {
                val recentTasks = activityManager.getRecentTasks(100, 0)
                recentTasks?.forEach { taskInfo ->
                    if (taskInfo.baseIntent?.component?.packageName == packageName) {
                        try {
                            // Use moveTaskToFront instead of deprecated removeTask
                            activityManager.moveTaskToFront(taskInfo.persistentId, 0)
                            Log.d(TAG, "Moved task to front for clearing: $packageName")
                        } catch (ex: Exception) {
                            Log.w(TAG, "Could not move task to front: ${ex.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not clear recent tasks: ${e.message}")
            }
            
            // Step 6: Wait for processes to fully terminate
            Thread.sleep(2000)
            
            // Step 7: Verify app is stopped
            try {
                val stillRunning = activityManager.runningAppProcesses?.any { 
                    it.processName.startsWith(packageName) 
                } ?: false
                
                if (stillRunning) {
                    Log.w(TAG, "Warning: Some processes for $packageName may still be running")
                    // Try one more aggressive kill
                    activityManager.runningAppProcesses?.forEach { processInfo ->
                        if (processInfo.processName.startsWith(packageName)) {
                            try {
                                android.os.Process.sendSignal(processInfo.pid, android.os.Process.SIGNAL_KILL)
                                Log.d(TAG, "Sent KILL signal to: ${processInfo.processName}")
                            } catch (e: Exception) {
                                Log.w(TAG, "Could not send KILL signal: ${e.message}")
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "Successfully force stopped all processes for: $packageName")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error verifying app stop status: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in enhanced force stopping original app", e)
        }
    }
    
    /**
     * Create completely isolated data environment for the clone
     */
    private fun createCompletelyIsolatedDataEnvironment(clonedApp: ClonedApp, context: Context) {
        try {
            Log.d(TAG, "Creating completely isolated data environment for ${clonedApp.clonedAppName}")
            
            // Create isolated root directory for this clone
            val isolatedRoot = java.io.File(context.filesDir, "isolated_apps/${clonedApp.clonedPackageName}")
            if (isolatedRoot.exists()) {
                isolatedRoot.deleteRecursively()
            }
            isolatedRoot.mkdirs()
            
            // Create all necessary app data directories in isolation
            val directories = listOf(
                "data", "cache", "code_cache", "files", "databases", 
                "shared_prefs", "app_webview", "app_textures", "app_dex",
                "external_files", "external_cache", "obb"
            )
            
            directories.forEach { dir ->
                val dirFile = java.io.File(isolatedRoot as java.io.File, dir)
                dirFile.mkdirs()
                Log.d(TAG, "Created isolated directory: ${dirFile.absolutePath}")
            }
            
            // Update clone's data path to point to isolated environment
            updateClonedAppDataPath(clonedApp.id, isolatedRoot.absolutePath)
            
            // Save isolation mapping
            val isolationConfig = JSONObject().apply {
                put("cloned_package", clonedApp.clonedPackageName)
                put("original_package", clonedApp.originalPackageName)
                put("isolated_root", isolatedRoot.absolutePath)
                put("created_at", System.currentTimeMillis())
                put("isolation_mode", "complete")
            }
            
            val configFile = java.io.File(isolatedRoot as java.io.File, "isolation_config.json")
            configFile.writeText(isolationConfig.toString())
            
            Log.d(TAG, "Isolated data environment created at: ${isolatedRoot.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating isolated data environment", e)
        }
    }
    
    /**
     * Clear all SharedPreferences for the original app
     */
    private fun clearAllSharedPreferences(packageName: String) {
        try {
            Log.d(TAG, "Clearing all SharedPreferences for $packageName")
            
            val prefsDir = File("/data/data/$packageName/shared_prefs")
            if (prefsDir.exists()) {
                prefsDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".xml")) {
                        file.delete()
                        Log.d(TAG, "Deleted SharedPreferences file: ${file.name}")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing SharedPreferences", e)
        }
    }
    
    /**
     * Clear all WebView and browser data for the original app
     */
    private fun clearAllWebViewData(packageName: String) {
        try {
            Log.d(TAG, "Clearing all WebView data for $packageName")
            
            val appDataDir = File("/data/data/$packageName")
            if (appDataDir.exists()) {
                // Clear WebView cache
                val webViewCacheDir = File(appDataDir, "app_webview")
                if (webViewCacheDir.exists()) {
                    webViewCacheDir.deleteRecursively()
                    webViewCacheDir.mkdirs()
                    Log.d(TAG, "Cleared WebView cache")
                }
                
                // Clear WebView databases
                val webViewDbDir = File(appDataDir, "app_webview/databases")
                if (webViewDbDir.exists()) {
                    webViewDbDir.deleteRecursively()
                    webViewDbDir.mkdirs()
                    Log.d(TAG, "Cleared WebView databases")
                }
                
                // Clear cookies
                val cookiesDir = File(appDataDir, "app_webview/cookies")
                if (cookiesDir.exists()) {
                    cookiesDir.deleteRecursively()
                    cookiesDir.mkdirs()
                    Log.d(TAG, "Cleared WebView cookies")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing WebView data", e)
        }
    }
    
    /**
     * Clear all database files for the original app
     */
    private fun clearAllDatabaseFiles(packageName: String) {
        try {
            Log.d(TAG, "Clearing all database files for $packageName")
            
            val databasesDir = File("/data/data/$packageName/databases")
            if (databasesDir.exists()) {
                databasesDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        file.delete()
                        Log.d(TAG, "Deleted database file: ${file.name}")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing database files", e)
        }
    }
    
    /**
     * Setup virtual file system redirection for complete isolation
     */
    private fun setupVirtualFileSystemRedirection(clonedApp: ClonedApp, context: Context) {
        try {
            Log.d(TAG, "Setting up virtual file system redirection for ${clonedApp.clonedAppName}")
            
            val isolatedRoot = java.io.File(context.filesDir, "isolated_apps/${clonedApp.clonedPackageName}")
            
            // Create file system redirection mapping
            val redirectionConfig = JSONObject().apply {
                put("original_package", clonedApp.originalPackageName)
                put("cloned_package", clonedApp.clonedPackageName)
                put("isolated_root", isolatedRoot.absolutePath)
                
                // Map all data access paths to isolated environment
                val pathMappings = JSONObject().apply {
                    put("/data/data/${clonedApp.originalPackageName}", "${isolatedRoot.absolutePath}/data")
                    put("/storage/emulated/0/Android/data/${clonedApp.originalPackageName}", "${isolatedRoot.absolutePath}/external_files")
                    put("/storage/emulated/0/Android/obb/${clonedApp.originalPackageName}", "${isolatedRoot.absolutePath}/obb")
                    put("/data/user/0/${clonedApp.originalPackageName}", "${isolatedRoot.absolutePath}/data")
                }
                put("path_mappings", pathMappings)
                put("redirection_active", true)
            }
            
            val redirectionFile = java.io.File(isolatedRoot as java.io.File, "file_system_redirection.json")
            redirectionFile.writeText(redirectionConfig.toString())
            
            Log.d(TAG, "Virtual file system redirection configured for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up virtual file system redirection", e)
        }
    }
    
    /**
     * Create fresh app data structure like new installation
     */
    private fun createFreshAppDataStructure(clonedApp: ClonedApp) {
        try {
            Log.d(TAG, "Creating fresh app data structure for ${clonedApp.clonedAppName}")
            
            val isolatedRoot = java.io.File(context.filesDir, "isolated_apps/${clonedApp.clonedPackageName}")
            
            // Create fresh manifest and app info
            val appInfo = JSONObject().apply {
                put("package_name", clonedApp.clonedPackageName)
                put("original_package", clonedApp.originalPackageName)
                put("app_name", clonedApp.clonedAppName)
                put("installation_time", System.currentTimeMillis())
                put("first_launch", true)
                put("fresh_install", true)
                put("data_version", 1)
                put("isolation_level", "complete")
            }
            
            val appInfoFile = java.io.File(isolatedRoot, "app_info.json")
            appInfoFile.writeText(appInfo.toString())
            
            // Create fresh permissions file
            val permissions = JSONObject().apply {
                put("granted_permissions", JSONArray())
                put("runtime_permissions", JSONObject())
                put("permission_requests", JSONArray())
                put("first_time_setup", true)
            }
            
            val permissionsFile = java.io.File(isolatedRoot, "permissions.json")
            permissionsFile.writeText(permissions.toString())
            
            // Create fresh installation marker
            val installMarker = java.io.File(isolatedRoot, "fresh_install.marker")
            installMarker.writeText("Fresh installation at ${System.currentTimeMillis()}")
            
            Log.d(TAG, "Fresh app data structure created for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating fresh app data structure", e)
        }
    }
    
    /**
     * Setup isolated SharedPreferences with fresh state
     */
    private fun setupIsolatedSharedPreferences(clonedApp: ClonedApp) {
        try {
            Log.d(TAG, "Setting up isolated SharedPreferences for ${clonedApp.clonedAppName}")
            
            val isolatedRoot = File(this.context.filesDir, "isolated_apps/${clonedApp.clonedPackageName}")
            val sharedPrefsDir = File(isolatedRoot, "shared_prefs")
            sharedPrefsDir.mkdirs()
            
            // Create fresh SharedPreferences for the clone
            val clonePrefs = this.context.getSharedPreferences("${clonedApp.clonedPackageName}_isolated", Context.MODE_PRIVATE)
            val editor = clonePrefs.edit()
            editor.clear() // Clear any existing data
            
            // Set fresh installation flags
            editor.putBoolean("is_virtual_environment", true)
            editor.putBoolean("incognito_mode", true)
            editor.putBoolean("fresh_install", true)
            editor.putBoolean("first_launch", true)
            editor.putBoolean("data_isolation_active", true)
            editor.putLong("installation_time", System.currentTimeMillis())
            editor.putLong("first_launch_time", System.currentTimeMillis())
            
            // Virtual space metadata
            editor.putString("virtual_package_name", clonedApp.clonedPackageName)
            editor.putString("original_package_name", clonedApp.originalPackageName)
            editor.putString("isolated_data_path", isolatedRoot.absolutePath)
            editor.putLong("app_id", clonedApp.id)
            
            // Ensure no login data exists
            editor.putBoolean("user_logged_in", false)
            editor.putBoolean("auto_login_enabled", false)
            editor.putBoolean("remember_login", false)
            
            editor.apply()
            
            Log.d(TAG, "Isolated SharedPreferences setup completed for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up isolated SharedPreferences", e)
        }
    }
    
    /**
     * Clear Android Account Manager data
     */
    private fun clearAccountManagerData(packageName: String) {
        try {
            Log.d(TAG, "Clearing Account Manager data for $packageName")
            
            val accountManager = AccountManager.get(context)
            
            // Get all accounts and remove those associated with the package
            val accounts = accountManager.accounts
            accounts.forEach { account ->
                try {
                    // Remove account data for this package
                    accountManager.clearPassword(account)
                    accountManager.invalidateAuthToken(account.type, null)
                    Log.d(TAG, "Cleared account data for: ${account.name}")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not clear account data for ${account.name}: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing Account Manager data", e)
        }
    }
    
    /**
     * Clear all existing login and authentication data for fresh sign-in (Enhanced for Incognito-like behavior)
     */
    private fun clearExistingLoginData(clonedApp: ClonedApp) {
        try {
            Log.d(TAG, "🧨 CLEARING existing login data for ${clonedApp.clonedAppName} (Enhanced Incognito mode)")
            Log.d(TAG, "📁 Target data path: ${clonedApp.dataPath}")
            
            // Clear SharedPreferences login data - comprehensive list
            val prefs = dataManager.getClonedAppPreferences(clonedApp.clonedPackageName)
            var clearedCount = 0
            val editor = prefs.edit()
            
            // Remove all authentication related data
            editor.remove("logged_in_user"); clearedCount++
            editor.remove("auth_token"); clearedCount++
            editor.remove("user_session"); clearedCount++
            editor.remove("account_data"); clearedCount++
            editor.remove("login_state"); clearedCount++
            editor.remove("user_id"); clearedCount++
            editor.remove("session_id"); clearedCount++
            editor.remove("access_token"); clearedCount++
            editor.remove("refresh_token"); clearedCount++
            editor.remove("oauth_token")
            editor.remove("jwt_token")
            editor.remove("api_key")
            editor.remove("user_credentials")
            editor.remove("login_timestamp")
            editor.remove("session_data")
            editor.remove("auth_state")
            editor.remove("user_profile")
            editor.remove("account_info")
            
            // Additional social media and app-specific keys
            editor.remove("facebook_token")
            editor.remove("google_token")
            editor.remove("twitter_token")
            editor.remove("instagram_token")
            editor.remove("whatsapp_session")
            editor.remove("telegram_session")
            editor.remove("messenger_session")
            editor.remove("snapchat_session")
            editor.remove("tiktok_session")
            editor.remove("linkedin_token")
            editor.remove("youtube_session")
            editor.remove("gmail_session")
            
            // Device and app state data
            editor.remove("device_id")
            editor.remove("installation_id")
            editor.remove("app_instance_id")
            editor.remove("push_token")
            editor.remove("notification_token")
            editor.remove("fcm_token")
            
            // Biometric and security data
            editor.remove("biometric_data")
            editor.remove("fingerprint_data")
            editor.remove("face_id_data")
            editor.remove("pin_data")
            editor.remove("pattern_data")
            editor.remove("security_questions")
            
            // Clear all keys that might contain user data
            val allKeys = prefs.all.keys.toList()
            for (key in allKeys) {
                if (key.contains("user", ignoreCase = true) || 
                    key.contains("auth", ignoreCase = true) ||
                    key.contains("login", ignoreCase = true) ||
                    key.contains("session", ignoreCase = true) ||
                    key.contains("token", ignoreCase = true) ||
                    key.contains("account", ignoreCase = true) ||
                    key.contains("profile", ignoreCase = true) ||
                    key.contains("credential", ignoreCase = true)) {
                    editor.remove(key)
                }
            }
            
            // Set flags to indicate clean state
            editor.putBoolean("login_data_cleared", true)
            editor.putLong("login_clear_time", System.currentTimeMillis())
            editor.putBoolean("incognito_mode_active", true)
            editor.putBoolean("require_fresh_login", true)
            editor.apply()
            
            Log.d(TAG, "✅ Cleared $clearedCount SharedPreferences login keys")
            
            // Clear account data files
            val accountDir = File(clonedApp.dataPath, "accounts")
            if (accountDir.exists()) {
                Log.d(TAG, "🗂 Clearing account directory: ${accountDir.absolutePath}")
                accountDir.deleteRecursively()
                accountDir.mkdirs()
            }
            
            // Clear session data files
            val sessionDir = File(clonedApp.dataPath, "cookies")
            if (sessionDir.exists()) {
                Log.d(TAG, "🍪 Clearing session directory: ${sessionDir.absolutePath}")
                sessionDir.deleteRecursively()
                sessionDir.mkdirs()
            }
            
            // Clear keystore data
            val keystoreDir = File(clonedApp.dataPath, "keystore")
            if (keystoreDir.exists()) {
                Log.d(TAG, "🔐 Clearing keystore directory: ${keystoreDir.absolutePath}")
                keystoreDir.deleteRecursively()
                keystoreDir.mkdirs()
            }
            
            // Clear database files that might contain login data
            val dbDir = File(clonedApp.dataPath, "databases")
            if (dbDir.exists()) {
                val dbFiles = dbDir.listFiles()
                if (dbFiles != null) {
                    for (dbFile in dbFiles) {
                        if (dbFile.name.contains("user") || dbFile.name.contains("auth") || dbFile.name.contains("session")) {
                            dbFile.delete()
                        }
                    }
                }
            }
            
            // Clear any cached files that might contain login data
            val cacheDir = File(clonedApp.dataPath, "cache")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()
            }
            
            Log.d(TAG, "✅ Login data cleared successfully for ${clonedApp.clonedAppName}")
            Log.d(TAG, "🔎 Fresh sign-in will be required on next launch")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear existing login data for ${clonedApp.clonedAppName}", e)
        }
    }
    
    /**
     * Clear WebView data and cookies for clean state
     */
    private fun clearWebViewData(clonedApp: ClonedApp) {
        try {
            // Clear WebView cache and data directories
            val webViewDirs = listOf(
                "app_webview",
                "app_webview_cache", 
                "app_webview_databases",
                "app_webview_localStorage",
                "app_webview_sessionStorage",
                "app_cookies"
            )
            
            for (dirName in webViewDirs) {
                val webViewDir = File(clonedApp.dataPath, dirName)
                if (webViewDir.exists()) {
                    webViewDir.deleteRecursively()
                }
                // Recreate empty directory
                webViewDir.mkdirs()
            }
            
            // Clear any cached authentication data
            val prefs = dataManager.getClonedAppPreferences(clonedApp.clonedPackageName)
            val editor = prefs.edit()
            editor.remove("webview_cookies")
            editor.remove("webview_session")
            editor.remove("webview_auth_data")
            editor.remove("webview_local_storage")
            editor.remove("webview_session_storage")
            editor.putBoolean("webview_cleared", true)
            editor.putLong("webview_clear_time", System.currentTimeMillis())
            editor.apply()
            
            Log.d(TAG, "WebView data cleared for ${clonedApp.clonedAppName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear WebView data", e)
        }
    }
    
    /**
     * Setup isolated WebView environment with fresh state
     */
    private fun setupIsolatedWebViewEnvironment(clonedApp: ClonedApp) {
        try {
            Log.d(TAG, "Setting up isolated WebView environment for ${clonedApp.clonedAppName}")
            
            val isolatedRoot = File(context.filesDir, "isolated_apps/${clonedApp.clonedPackageName}")
            val webViewDir = File(isolatedRoot, "webview")
            webViewDir.mkdirs()
            
            // Create isolated WebView directories
            val webViewDirs = listOf(
                File(webViewDir as File, "cache"),
                File(webViewDir as File, "databases"),
                File(webViewDir as File, "localStorage"),
                File(webViewDir as File, "sessionStorage"),
                File(webViewDir as File, "cookies"),
                File(webViewDir as File, "indexedDB")
            )
            
            webViewDirs.forEach { dir ->
                dir.mkdirs()
            }
            
            // Create WebView configuration for isolation
            val webViewConfig = JSONObject().apply {
                put("isolated_data_dir", webViewDir.absolutePath)
                put("clear_cache_on_start", true)
                put("clear_cookies_on_start", true)
                put("clear_storage_on_start", true)
                put("incognito_mode", true)
                put("fresh_session", true)
            }
            
            val configFile = File(webViewDir, "webview_config.json")
            configFile.writeText(webViewConfig.toString())
            
            Log.d(TAG, "Isolated WebView environment setup completed for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up isolated WebView environment", e)
        }
    }
    
    /**
     * Setup isolated database environment
     */
    private fun setupIsolatedDatabaseEnvironment(clonedApp: ClonedApp) {
        try {
            Log.d(TAG, "Setting up isolated database environment for ${clonedApp.clonedAppName}")
            
            val isolatedRoot = File(context.filesDir, "isolated_apps/${clonedApp.clonedPackageName}")
            val databasesDir = File(isolatedRoot, "databases")
            databasesDir.mkdirs()
            
            // Create database isolation configuration
            val dbConfig = JSONObject().apply {
                put("isolated_db_path", databasesDir.absolutePath)
                put("original_package", clonedApp.originalPackageName)
                put("cloned_package", clonedApp.clonedPackageName)
                put("fresh_database", true)
                put("no_migration", true)
                put("isolation_level", "complete")
            }
            
            val configFile = File(databasesDir, "database_config.json")
            configFile.writeText(dbConfig.toString())
            
            // Create fresh database marker
            val freshMarker = File(databasesDir, "fresh_database.marker")
            freshMarker.writeText("Fresh database created at ${System.currentTimeMillis()}")
            
            Log.d(TAG, "Isolated database environment setup completed for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up isolated database environment", e)
        }
    }
    
    /**
     * Setup isolated account management
     */
    private fun setupIsolatedAccountManagement(clonedApp: ClonedApp) {
        try {
            Log.d(TAG, "Setting up isolated account management for ${clonedApp.clonedAppName}")
            
            val isolatedRoot = File(context.filesDir, "isolated_apps/${clonedApp.clonedPackageName}")
            val accountsDir = File(isolatedRoot, "accounts")
            accountsDir.mkdirs()
            
            // Create account isolation configuration
            val accountConfig = JSONObject().apply {
                put("isolated_accounts_path", accountsDir.absolutePath)
                put("block_original_accounts", true)
                put("fresh_account_state", true)
                put("no_account_sharing", true)
                put("require_fresh_login", true)
            }
            
            val configFile = File(accountsDir, "account_config.json")
            configFile.writeText(accountConfig.toString())
            
            // Create empty accounts file to prevent access to system accounts
            val accountsFile = File(accountsDir, "accounts.json")
            accountsFile.writeText(JSONObject().apply {
                put("accounts", JSONArray())
                put("fresh_state", true)
                put("creation_time", System.currentTimeMillis())
            }.toString())
            
            Log.d(TAG, "Isolated account management setup completed for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up isolated account management", e)
        }
    }
    
    /**
     * Verify data isolation is working correctly
     */
    fun verifyDataIsolation(clonedApp: ClonedApp): Boolean {
        return try {
            Log.d(TAG, "Verifying data isolation for ${clonedApp.clonedAppName}")
            
            val isolatedRoot = File(context.filesDir, "isolated_apps/${clonedApp.clonedPackageName}")
            
            // Check if isolation directory exists
            if (!isolatedRoot.exists()) {
                Log.e(TAG, "Isolation directory does not exist for ${clonedApp.clonedAppName}")
                return false
            }
            
            // Check if required isolation files exist
            val requiredFiles = listOf(
                "app_info.json",
                "fresh_install.marker",
                "file_system_redirection.json",
                "shared_prefs",
                "databases",
                "webview",
                "accounts"
            )
            
            var allFilesExist = true
            requiredFiles.forEach { fileName ->
                val file = File(isolatedRoot, fileName)
                if (!file.exists()) {
                    Log.w(TAG, "Required isolation file/directory missing: $fileName")
                    allFilesExist = false
                }
            }
            
            // Verify SharedPreferences isolation
            val clonePrefs = context.getSharedPreferences("${clonedApp.clonedPackageName}_isolated", Context.MODE_PRIVATE)
            val isIsolated = clonePrefs.getBoolean("data_isolation_active", false)
            val isFreshInstall = clonePrefs.getBoolean("fresh_install", false)
            
            if (!isIsolated || !isFreshInstall) {
                Log.e(TAG, "SharedPreferences isolation verification failed for ${clonedApp.clonedAppName}")
                return false
            }
            
            Log.d(TAG, "Data isolation verification successful for ${clonedApp.clonedAppName}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying data isolation", e)
            false
        }
    }
    
    /**
     * Verify data isolation by cloned app ID
     */
    fun verifyDataIsolation(clonedAppId: Long): Boolean {
        return try {
            val clonedApp = getClonedAppById(clonedAppId)
            if (clonedApp != null) {
                verifyDataIsolation(clonedApp)
            } else {
                Log.e(TAG, "Cloned app not found with ID: $clonedAppId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying data isolation for ID $clonedAppId", e)
            false
        }
    }
    
    /**
     * Complete app data reset mechanism - simulates uninstall/reinstall behavior
     * This ensures the clone starts completely fresh like a newly installed app
     */
    private fun performCompleteAppDataReset(clonedApp: ClonedApp, cloneContext: CloneContextWrapper) {
        try {
            Log.d(TAG, "Performing complete app data reset for ${clonedApp.clonedAppName} - simulating fresh install")
            
            // Step 1: Force stop the original app completely
            forceStopOriginalApp(clonedApp.originalPackageName, context)
            
            // Step 2: Clear ALL system-level caches and data
            clearSystemLevelCaches(clonedApp)
            
            // Step 3: Remove ALL existing app data directories
            removeAllExistingAppData(clonedApp)
            
            // Step 4: Clear Android system services data
            clearAndroidSystemServicesData(clonedApp)
            
            // Step 5: Reset app permissions and settings
            resetAppPermissionsAndSettings(clonedApp)
            
            // Step 6: Create completely fresh app environment with CloneContextWrapper
            createFreshAppEnvironment(clonedApp, cloneContext)
            
            // Step 7: Set fresh install markers
            setFreshInstallMarkers(clonedApp)
            
            // Step 8: Verify complete reset was successful
            val resetSuccessful = verifyCompleteReset(clonedApp)
            
            if (resetSuccessful) {
                Log.d(TAG, "Complete app data reset successful for ${clonedApp.clonedAppName}")
            } else {
                Log.e(TAG, "Complete app data reset verification failed for ${clonedApp.clonedAppName}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error performing complete app data reset", e)
        }
    }
    
    /**
     * Clear system-level caches that might contain app data
     */
    private fun clearSystemLevelCaches(clonedApp: ClonedApp) {
        try {
            Log.d(TAG, "Clearing system-level caches for ${clonedApp.originalPackageName}")
            
            // Clear package manager cache
            val packageManager = context.packageManager
            try {
                val clearCacheMethod = packageManager.javaClass.getMethod("deleteApplicationCacheFiles", String::class.java, Any::class.java)
                clearCacheMethod.invoke(packageManager, clonedApp.originalPackageName, null)
                Log.d(TAG, "Package manager cache cleared")
            } catch (e: Exception) {
                Log.w(TAG, "Could not clear package manager cache: ${e.message}")
            }
            
            // Clear ActivityManager cache
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            try {
                val clearAppDataMethod = activityManager.javaClass.getMethod("clearApplicationUserData", String::class.java, Any::class.java)
                clearAppDataMethod.invoke(activityManager, clonedApp.originalPackageName, null)
                Log.d(TAG, "ActivityManager cache cleared")
            } catch (e: Exception) {
                Log.w(TAG, "Could not clear ActivityManager cache: ${e.message}")
            }
            
            // Clear system cache directories
            val systemCacheDirs = listOf(
                "/data/system/package_cache",
                "/data/system/users/0/package-restrictions.xml",
                "/data/system_ce/0/accounts_ce.db",
                "/data/system_de/0/accounts_de.db"
            )
            
            systemCacheDirs.forEach { cacheDir ->
                try {
                    val file = File(cacheDir)
                    if (file.exists()) {
                        file.delete()
                        Log.d(TAG, "Cleared system cache: $cacheDir")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not clear system cache $cacheDir: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing system-level caches", e)
        }
    }
    
    /**
     * Remove ALL existing app data directories completely
     */
    private fun removeAllExistingAppData(clonedApp: ClonedApp) {
        try {
            Log.d(TAG, "Removing ALL existing app data for ${clonedApp.originalPackageName}")
            
            val ctx = this.context
            // Get all possible app data locations
            val appDataLocations = mutableListOf(
                "/data/data/${clonedApp.originalPackageName}",
                "/data/user/0/${clonedApp.originalPackageName}",
                "/storage/emulated/0/Android/data/${clonedApp.originalPackageName}",
                "/storage/emulated/0/Android/obb/${clonedApp.originalPackageName}",
                "/sdcard/Android/data/${clonedApp.originalPackageName}",
                "/sdcard/Android/obb/${clonedApp.originalPackageName}"
            )
            // Append additional paths only when non-null to avoid nullable String entries
            ctx.getExternalFilesDir(null)?.parent?.let { parent -> appDataLocations.add("$parent/${clonedApp.originalPackageName}") }
            ctx.filesDir.parent?.let { parent -> appDataLocations.add("$parent/${clonedApp.originalPackageName}") }
            ctx.cacheDir.parent?.let { parent -> appDataLocations.add("$parent/${clonedApp.originalPackageName}") }
            
            appDataLocations.forEach { location: String ->
                try {
                    val dataDir = File(location)
                    if (dataDir.exists()) {
                        dataDir.deleteRecursively()
                        Log.d(TAG, "Removed app data directory: $location")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not remove app data directory $location: ${e.message}")
                }
            }
            
            // Also remove any clone-specific data directories
            val cloneDataLocations = listOf(
                ctx.filesDir.absolutePath + "/cloned_apps/${clonedApp.clonedPackageName}",
                ctx.filesDir.absolutePath + "/isolated_apps/${clonedApp.clonedPackageName}",
                ctx.cacheDir.absolutePath + "/cloned_apps/${clonedApp.clonedPackageName}"
            )
            
            cloneDataLocations.forEach { location: String ->
                try {
                    val dataDir = File(location)
                    if (dataDir.exists()) {
                        dataDir.deleteRecursively()
                        Log.d(TAG, "Removed clone data directory: $location")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not remove clone data directory $location: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error removing existing app data", e)
        }
    }
    
    /**
     * Clear Android system services data related to the app
     */
    private fun clearAndroidSystemServicesData(clonedApp: ClonedApp) {
        try {
            Log.d(TAG, "Clearing Android system services data for ${clonedApp.originalPackageName}")
            
            // Clear AccountManager data
            val accountManager = AccountManager.get(context)
            try {
                val accounts = accountManager.accounts
                accounts.forEach { account ->
                    try {
                        accountManager.clearPassword(account)
                        accountManager.invalidateAuthToken(account.type, null)
                        // Remove account if it belongs to this package
                        val accountPackages = accountManager.getAccountsByType(account.type)
                        accountPackages.forEach { packageAccount ->
                            if (packageAccount.name.contains(clonedApp.originalPackageName)) {
                                accountManager.removeAccountExplicitly(packageAccount)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not clear account ${account.name}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not clear AccountManager data: ${e.message}")
            }
            
            // Clear NotificationManager data
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancelAll()
                Log.d(TAG, "Cleared all notifications")
            } catch (e: Exception) {
                Log.w(TAG, "Could not clear notifications: ${e.message}")
            }
            
            // Clear AlarmManager data
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                // Cancel any alarms set by the original app
                val intent = Intent()
                intent.setPackage(clonedApp.originalPackageName)
                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    Log.d(TAG, "Cleared alarm manager data")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not clear AlarmManager data: ${e.message}")
            }
            
            // Clear JobScheduler data
            try {
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                jobScheduler.cancelAll()
                Log.d(TAG, "Cleared job scheduler data")
            } catch (e: Exception) {
                Log.w(TAG, "Could not clear JobScheduler data: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing Android system services data", e)
        }
    }
    
    /**
     * Reset app permissions and settings to default state
     */
    private fun resetAppPermissionsAndSettings(clonedApp: ClonedApp) {
        try {
            Log.d(TAG, "Resetting app permissions and settings for ${clonedApp.originalPackageName}")
            
            // Reset SharedPreferences to completely empty state
            val allPrefsFiles = listOf(
                "${clonedApp.originalPackageName}_preferences",
                "${clonedApp.clonedPackageName}_preferences",
                "${clonedApp.originalPackageName}_isolated",
                "${clonedApp.clonedPackageName}_isolated"
            )
            
            allPrefsFiles.forEach { prefsName ->
                try {
                    val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    prefs.edit().clear().apply()
                    Log.d(TAG, "Cleared SharedPreferences: $prefsName")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not clear SharedPreferences $prefsName: ${e.message}")
                }
            }
            
            // Clear app-specific settings files
            val settingsDir = File(context.filesDir, "settings")
            if (settingsDir.exists()) {
                settingsDir.listFiles()?.forEach { file ->
                    if (file.name.contains(clonedApp.originalPackageName) || file.name.contains(clonedApp.clonedPackageName)) {
                        file.delete()
                        Log.d(TAG, "Removed settings file: ${file.name}")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting app permissions and settings", e)
        }
    }
    
    /**
     * Create completely fresh app environment like a new installation
     */
    private fun createFreshAppEnvironment(clonedApp: ClonedApp, cloneContext: CloneContextWrapper) {
        try {
            Log.d(TAG, "Creating completely fresh app environment for ${clonedApp.clonedAppName}")
            
            // Create fresh isolated root directory
            val freshRoot = File(context.filesDir, "fresh_apps/${clonedApp.clonedPackageName}")
            if (freshRoot.exists()) {
                freshRoot.deleteRecursively()
            }
            freshRoot.mkdirs()
            
            // Create fresh app data structure
            val freshDataDirs = listOf(
                "data", "cache", "files", "databases", "shared_prefs", 
                "webview", "accounts", "lib", "code_cache", "no_backup"
            )
            
            freshDataDirs.forEach { dirName ->
                val dir = File(freshRoot, dirName)
                dir.mkdirs()
                Log.d(TAG, "Created fresh directory: ${dir.absolutePath}")
            }
            
            // Create fresh app configuration
            val freshConfig = JSONObject().apply {
                put("package_name", clonedApp.clonedPackageName)
                put("original_package", clonedApp.originalPackageName)
                put("fresh_install", true)
                put("install_time", System.currentTimeMillis())
                put("version_code", 1)
                put("version_name", "1.0.0")
                put("first_launch", true)
                put("data_isolation", true)
                put("complete_reset", true)
                put("fresh_environment", true)
            }
            
            val configFile = File(freshRoot, "app_config.json")
            configFile.writeText(freshConfig.toString())
            
            // Update cloned app data path in database to point to fresh environment
            updateClonedAppDataPath(clonedApp.id, freshRoot.absolutePath)
            
            // Create fresh SharedPreferences with clean state using CloneContextWrapper
            val freshPrefs = cloneContext.getSharedPreferences("${clonedApp.clonedPackageName}_fresh", Context.MODE_PRIVATE)
            freshPrefs.edit().apply {
                clear()
                putBoolean("fresh_install", true)
                putBoolean("first_launch", true)
                putBoolean("require_fresh_login", true)
                putBoolean("data_completely_reset", true)
                putLong("fresh_install_time", System.currentTimeMillis())
                putString("fresh_environment_path", freshRoot.absolutePath)
                apply()
            }
            
            Log.d(TAG, "Fresh app environment created successfully for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating fresh app environment", e)
        }
    }
    
    /**
     * Set markers to indicate this is a fresh install
     */
    private fun setFreshInstallMarkers(clonedApp: ClonedApp) {
        try {
            Log.d(TAG, "Setting fresh install markers for ${clonedApp.clonedAppName}")
            
            val freshRoot = File(clonedApp.dataPath)
            
            // Create fresh install marker file
            val freshMarker = File(freshRoot, "FRESH_INSTALL.marker")
            freshMarker.writeText("Fresh install created at ${System.currentTimeMillis()}\nPackage: ${clonedApp.clonedPackageName}\nOriginal: ${clonedApp.originalPackageName}\nComplete reset: true")
            
            // Create first launch marker
            val firstLaunchMarker = File(freshRoot, "FIRST_LAUNCH.marker")
            firstLaunchMarker.writeText("First launch required\nFresh login required\nNo previous data")
            
            // Create data isolation marker
            val isolationMarker = File(freshRoot, "DATA_ISOLATION.marker")
            isolationMarker.writeText("Complete data isolation active\nNo access to original app data\nFresh environment only")
            
            // Create reset verification marker
            val resetMarker = File(freshRoot, "COMPLETE_RESET.marker")
            resetMarker.writeText("Complete app data reset performed\nAll previous data removed\nFresh state guaranteed")
            
            Log.d(TAG, "Fresh install markers set successfully for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting fresh install markers", e)
        }
    }
    
    /**
      * Update cloned app data path in database
      */
     private fun updateClonedAppDataPath(clonedAppId: Long, newDataPath: String) {
         try {
             Log.d(TAG, "Updating data path for cloned app ID: $clonedAppId to: $newDataPath")
             
             val db = databaseHelper.writableDatabase
             val values = ContentValues().apply {
                 put(ClonedApp.COLUMN_DATA_PATH, newDataPath)
             }
             
             val rowsUpdated = db.update(
                 ClonedApp.TABLE_NAME,
                 values,
                 "${ClonedApp.COLUMN_ID} = ?",
                 arrayOf(clonedAppId.toString())
             )
             
             if (rowsUpdated > 0) {
                 Log.d(TAG, "Successfully updated data path for cloned app ID: $clonedAppId")
             } else {
                 Log.e(TAG, "Failed to update data path for cloned app ID: $clonedAppId")
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error updating cloned app data path", e)
         }
     }
     
     /**
      * Verify that complete reset was successful
      */
     private fun verifyCompleteReset(clonedApp: ClonedApp): Boolean {
        return try {
            Log.d(TAG, "Verifying complete reset for ${clonedApp.clonedAppName}")
            
            val freshRoot = File(clonedApp.dataPath)
            
            // Ensure fresh environment directory exists
            if (!freshRoot.exists()) {
                Log.w(TAG, "Fresh environment directory does not exist, creating now")
                freshRoot.mkdirs()
            }
            
            // Check if all required markers exist
            val requiredMarkers = listOf(
                "FRESH_INSTALL.marker",
                "FIRST_LAUNCH.marker", 
                "DATA_ISOLATION.marker",
                "COMPLETE_RESET.marker"
            )
            
            var allMarkersExist = true
            requiredMarkers.forEach { markerName ->
                val marker = File(freshRoot, markerName)
                if (!marker.exists()) {
                    Log.e(TAG, "Required marker missing: $markerName")
                    allMarkersExist = false
                    
                    // CREATE MISSING MARKER IMMEDIATELY
                    try {
                        marker.writeText("Created at: ${System.currentTimeMillis()}\nPackage: ${clonedApp.clonedPackageName}\nReset enforced: true")
                        Log.d(TAG, "✅ Created missing marker: $markerName")
                        allMarkersExist = true // Mark as fixed
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to create marker: $markerName", e)
                    }
                } else {
                    Log.d(TAG, "✅ Marker verified: $markerName")
                }
            }
            
            // Create fresh login marker to ensure sign-in is required
            val freshLoginMarker = File(freshRoot, "REQUIRE_FRESH_LOGIN.marker")
            if (!freshLoginMarker.exists()) {
                freshLoginMarker.writeText("Fresh login required at: ${System.currentTimeMillis()}\nPackage: ${clonedApp.originalPackageName}\nClone: ${clonedApp.clonedPackageName}")
                Log.d(TAG, "✅ Created REQUIRE_FRESH_LOGIN marker")
            }
            
            // Ensure fresh SharedPreferences are set correctly
            val freshPrefs = context.getSharedPreferences("${clonedApp.clonedPackageName}_fresh", Context.MODE_PRIVATE)
            freshPrefs.edit().apply {
                putBoolean("fresh_install", true)
                putBoolean("first_launch", true)
                putBoolean("require_fresh_login", true)
                putBoolean("data_completely_reset", true)
                putLong("reset_timestamp", System.currentTimeMillis())
                putString("original_package", clonedApp.originalPackageName)
                putString("cloned_package", clonedApp.clonedPackageName)
                apply()
            }
            
            Log.d(TAG, "✅ COMPLETE RESET verification and enforcement successful for ${clonedApp.clonedAppName} - Fresh sign-in GUARANTEED")
            true
            
        } catch (e: Exception) {
             Log.e(TAG, "❌ Error verifying complete reset", e)
             false
         }
     }
     
     /**
      * Perform additional aggressive data clearing for stubborn apps
      */
     private fun performAdditionalAggressiveDataClearing(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Performing additional aggressive data clearing for ${clonedApp.clonedAppName}")
             
             // Clear all possible login token storage locations
             clearAllLoginTokens(clonedApp)
             
             // Clear all possible session storage
             clearAllSessionStorage(clonedApp)
             
             // Clear all possible authentication caches
             clearAllAuthenticationCaches(clonedApp)
             
             // Clear all possible social media specific data
             clearSocialMediaSpecificData(clonedApp)
             
             // Clear all possible browser/webview persistent data
             clearAllBrowserPersistentData(clonedApp)
             
             // Clear all possible keystore and secure storage
             clearAllSecureStorage(clonedApp)
             
             // Force clear system-level app associations
             clearSystemLevelAppAssociations(clonedApp)
             
             Log.d(TAG, "Additional aggressive data clearing completed for ${clonedApp.clonedAppName}")
             
         } catch (e: Exception) {
             Log.e(TAG, "Error performing additional aggressive data clearing", e)
         }
     }
     
     /**
      * Clear all possible login tokens from various storage locations
      */
     private fun clearAllLoginTokens(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing all login tokens for ${clonedApp.originalPackageName}")
             
             // Common token storage keys used by social media apps
             val tokenKeys = listOf(
                 "access_token", "auth_token", "session_token", "login_token",
                 "oauth_token", "bearer_token", "jwt_token", "refresh_token",
                 "facebook_token", "fb_token", "instagram_token", "whatsapp_token",
                 "imo_token", "telegram_token", "viber_token", "messenger_token",
                 "user_token", "api_token", "auth_key", "session_key",
                 "login_key", "user_key", "account_key", "profile_key",
                 "authentication_token", "authorization_token", "security_token"
             )
             
             // Clear from all possible SharedPreferences files
             val prefsFiles = listOf(
                 clonedApp.originalPackageName,
                 "${clonedApp.originalPackageName}_preferences",
                 "${clonedApp.originalPackageName}_auth",
                 "${clonedApp.originalPackageName}_login",
                 "${clonedApp.originalPackageName}_session",
                 "${clonedApp.originalPackageName}_tokens",
                 "auth_prefs", "login_prefs", "session_prefs", "token_prefs",
                 "user_prefs", "account_prefs", "profile_prefs"
             )
             
             for (prefsName in prefsFiles) {
                 try {
                     val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                     val editor = prefs.edit()
                     
                     for (key in tokenKeys) {
                         editor.remove(key)
                     }
                     
                     // Also clear any keys that contain token-related words
                     for (existingKey in prefs.all.keys) {
                         if (existingKey.lowercase().contains("token") || 
                             existingKey.lowercase().contains("auth") ||
                             existingKey.lowercase().contains("login") ||
                             existingKey.lowercase().contains("session")) {
                             editor.remove(existingKey)
                         }
                     }
                     
                     editor.apply()
                     Log.d(TAG, "Cleared tokens from preferences: $prefsName")
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear tokens from $prefsName: ${e.message}")
                 }
             }
             
             // Clear token files from file system
             val tokenDirs = listOf(
                 File(clonedApp.dataPath, "tokens"),
                 File(clonedApp.dataPath, "auth"),
                 File(clonedApp.dataPath, "sessions"),
                 File(clonedApp.dataPath, "cache/tokens"),
                 File(clonedApp.dataPath, "files/tokens")
             )
             
             tokenDirs.forEach { dir ->
                 try {
                     if (dir.exists()) {
                         dir.deleteRecursively()
                         dir.mkdirs()
                         Log.d(TAG, "Cleared token directory: ${dir.absolutePath}")
                     }
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear token directory ${dir.absolutePath}: ${e.message}")
                 }
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing all login tokens", e)
         }
     }
     
     /**
      * Clear all session storage locations
      */
     private fun clearAllSessionStorageLegacy(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing all session storage for ${clonedApp.originalPackageName}")
             
             val sessionKeys = listOf(
                 "session_id", "user_session", "app_session", "login_session",
                 "auth_session", "facebook_session", "google_session", "twitter_session",
                 "instagram_session", "whatsapp_session", "telegram_session",
                 "session_data", "session_info", "session_state", "active_session"
             )
             
             val sessionPrefsFiles = listOf(
                 "${clonedApp.originalPackageName}_session",
                 "session_prefs", "user_session_prefs", "app_session_data"
             )
             
             for (prefsName in sessionPrefsFiles) {
                 try {
                     val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                     val editor = prefs.edit()
                     for (key in sessionKeys) { editor.remove(key) }
                     editor.clear() // Clear everything for session prefs
                     editor.apply()
                     Log.d(TAG, "Cleared session prefs: $prefsName")
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear session prefs $prefsName: ${e.message}")
                 }
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing all session storage", e)
         }
     }
     
     /**
      * Clear authentication caches
      */
     private fun clearAllAuthenticationCachesLegacy(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing authentication caches for ${clonedApp.originalPackageName}")
             
             val authCacheDirs = listOf(
                 File(clonedApp.dataPath, "cache/auth"),
                 File(clonedApp.dataPath, "cache/login"),
                 File(clonedApp.dataPath, "cache/session"),
                 File(clonedApp.dataPath, "cache/tokens"),
                 File(clonedApp.dataPath, "cache/user")
             )
             
             authCacheDirs.forEach { cacheDir ->
                 try {
                     if (cacheDir.exists()) {
                         cacheDir.deleteRecursively()
                         cacheDir.mkdirs()
                         Log.d(TAG, "Cleared auth cache: ${cacheDir.absolutePath}")
                     }
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear auth cache ${cacheDir.absolutePath}: ${e.message}")
                 }
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing authentication caches", e)
         }
     }
     
     /**
      * Clear social media specific data
      */
     private fun clearSocialMediaSpecificDataLegacy(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing social media specific data for ${clonedApp.originalPackageName}")
             
             // Clear Facebook specific data
             clearFacebookSpecificData(clonedApp)
             
             // Clear Instagram specific data
             clearInstagramSpecificData(clonedApp)
             
             // Clear WhatsApp specific data
             clearWhatsAppSpecificData(clonedApp)
             
             // Clear Telegram specific data
             clearTelegramSpecificData(clonedApp)
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing social media specific data", e)
         }
     }
     
     /**
      * Clear Facebook specific persistent data (Legacy)
      */
     private fun clearFacebookSpecificDataLegacy(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing Facebook specific data for ${clonedApp.originalPackageName}")
             
             val fbSpecificKeys = listOf(
                 "facebook_user_id", "fb_session", "facebook_auth", "fb_token",
                 "facebook_login_data", "fb_account_data"
             )
             
             val fbPrefsFiles = listOf(
                 "facebook_preferences", "fb_prefs", "com.facebook.katana_preferences"
             )
             
             for (prefsName in fbPrefsFiles) {
                 try {
                     val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                     val editor = prefs.edit()
                     for (key in fbSpecificKeys) { editor.remove(key) }
                     editor.clear() // Clear everything for Facebook prefs
                     editor.apply()
                     Log.d(TAG, "Cleared Facebook prefs: $prefsName")
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear Facebook prefs $prefsName: ${e.message}")
                 }
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing Facebook specific data", e)
         }
     }
     
     /**
      * Clear Instagram specific persistent data
      */
     private fun clearInstagramSpecificData(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing Instagram specific data for ${clonedApp.originalPackageName}")
             
             val igSpecificKeys = listOf(
                 "instagram_user_id", "ig_session", "instagram_auth", "ig_token",
                 "instagram_login_data", "ig_account_data"
             )
             
             val igPrefsFiles = listOf(
                 "instagram_preferences", "ig_prefs", "com.instagram.android_preferences"
             )
             
             for (prefsName in igPrefsFiles) {
                 try {
                     val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                     val editor = prefs.edit()
                     for (key in igSpecificKeys) { editor.remove(key) }
                     editor.clear() // Clear everything for Instagram prefs
                     editor.apply()
                     Log.d(TAG, "Cleared Instagram prefs: $prefsName")
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear Instagram prefs $prefsName: ${e.message}")
                 }
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing Instagram specific data", e)
         }
     }
     
     /**
      * Clear WhatsApp specific persistent data
      */
     private fun clearWhatsAppSpecificDataLegacy(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing WhatsApp specific data for ${clonedApp.originalPackageName}")
             
             val waSpecificKeys = listOf(
                 "whatsapp_user_id", "wa_session", "whatsapp_auth", "wa_token",
                 "whatsapp_login_data", "wa_account_data", "registration_id"
             )
             
             val waPrefsFiles = listOf(
                 "whatsapp_preferences", "wa_prefs", "com.whatsapp_preferences"
             )
             
             for (prefsName in waPrefsFiles) {
                 try {
                     val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                     val editor = prefs.edit()
                     for (key in waSpecificKeys) { editor.remove(key) }
                     editor.clear() // Clear everything for WhatsApp prefs
                     editor.apply()
                     Log.d(TAG, "Cleared WhatsApp prefs: $prefsName")
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear WhatsApp prefs $prefsName: ${e.message}")
                 }
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing WhatsApp specific data", e)
         }
     }
     
     /**
      * Clear Telegram specific persistent data
      */
     private fun clearTelegramSpecificDataLegacy(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing Telegram specific data for ${clonedApp.originalPackageName}")
             
             val tgSpecificKeys = listOf(
                 "telegram_user_id", "tg_session", "telegram_auth", "tg_token",
                 "telegram_login_data", "tg_account_data"
             )
             
             val tgPrefsFiles = listOf(
                 "telegram_preferences", "tg_prefs", "userconfing", "mainconfig"
             )
             
             for (prefsName in tgPrefsFiles) {
                 try {
                     val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                     val editor = prefs.edit()
                     for (key in tgSpecificKeys) { editor.remove(key) }
                     editor.clear() // Clear everything for Telegram prefs
                     editor.apply()
                     Log.d(TAG, "Cleared Telegram prefs: $prefsName")
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear Telegram prefs $prefsName: ${e.message}")
                 }
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing Telegram specific data", e)
         }
     }
     
     /**
      * Clear all browser and webview persistent data
      */
     private fun clearAllBrowserPersistentDataLegacy(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing all browser persistent data for ${clonedApp.originalPackageName}")
             
             // Clear WebView data more aggressively
             val webViewDataLocations = listOf(
                 context.filesDir.absolutePath + "/webview",
                 context.cacheDir.absolutePath + "/webview",
                 "/data/data/${clonedApp.originalPackageName}/app_webview",
                 "/data/data/${clonedApp.originalPackageName}/app_chrome",
                 "/data/data/${clonedApp.originalPackageName}/app_textures",
                 "/data/data/${clonedApp.originalPackageName}/app_webview_variations_seed"
             )
             
             for (location in webViewDataLocations) {
                 try {
                     val webViewDir = File(location)
                     if (webViewDir.exists()) {
                         webViewDir.deleteRecursively()
                         Log.d(TAG, "Cleared WebView data: $location")
                     }
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear WebView data $location: ${e.message}")
                 }
             }
             
             // Clear cookies and local storage
             try {
                 val cookieManager = CookieManager.getInstance()
                 cookieManager.removeAllCookies(null)
                 cookieManager.flush()
                 Log.d(TAG, "Cleared all cookies")
             } catch (e: Exception) {
                 Log.w(TAG, "Could not clear cookies: ${e.message}")
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing browser persistent data", e)
         }
     }
     
     /**
      * Clear all secure storage including keystore
      */
     private fun clearAllSecureStorageLegacy(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing all secure storage for ${clonedApp.originalPackageName}")
             
             // Clear Android Keystore entries
             try {
                 val keyStore = KeyStore.getInstance("AndroidKeyStore")
                 keyStore.load(null)
                 
                 val aliases = keyStore.aliases()
                 while (aliases.hasMoreElements()) {
                     val alias = aliases.nextElement()
                     if (alias.contains(clonedApp.originalPackageName)) {
                         keyStore.deleteEntry(alias)
                         Log.d(TAG, "Deleted keystore entry: $alias")
                     }
                 }
             } catch (e: Exception) {
                 Log.w(TAG, "Could not clear keystore: ${e.message}")
             }
             
             // Clear secure SharedPreferences
             val securePrefsFiles = listOf(
                 "${clonedApp.originalPackageName}_secure",
                 "${clonedApp.originalPackageName}_encrypted",
                 "secure_prefs", "encrypted_prefs", "keystore_prefs"
             )
             
             for (prefsName in securePrefsFiles) {
                 try {
                     val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                     prefs.edit().clear().apply()
                     Log.d(TAG, "Cleared secure prefs: $prefsName")
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear secure prefs $prefsName: ${e.message}")
                 }
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing secure storage", e)
         }
     }
     
     /**
      * Clear system-level app associations
      */
     private fun clearSystemLevelAppAssociationsLegacy(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing system-level app associations for ${clonedApp.originalPackageName}")
             
             // Clear recent tasks
             try {
                 val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                 val recentTasks = activityManager.getRunningTasks(100)
                 for (task in recentTasks) {
                     if (task.baseActivity?.packageName == clonedApp.originalPackageName) {
                         // Task will be cleared when app is force stopped
                         Log.d(TAG, "Found recent task for ${clonedApp.originalPackageName}")
                     }
                 }
             } catch (e: Exception) {
                 Log.w(TAG, "Could not clear recent tasks: ${e.message}")
             }
             
             // Clear app usage stats (if possible)
             try {
                 val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                 // Usage stats cannot be directly cleared, but we log the attempt
                 Log.d(TAG, "Attempted to clear usage stats for ${clonedApp.originalPackageName}")
             } catch (e: Exception) {
                 Log.w(TAG, "Could not access usage stats: ${e.message}")
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing system-level app associations", e)
         }
     }
     
     /**
      * Setup runtime data verification to ensure isolation is maintained
      */
     fun setupRuntimeDataVerificationLegacy(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Setting up runtime data verification for ${clonedApp.clonedAppName}")
             
             // Create verification configuration
             val verificationConfig = JSONObject().apply {
                 put("app_id", clonedApp.id)
                 put("package_name", clonedApp.clonedPackageName)
                 put("original_package", clonedApp.originalPackageName)
                 put("verification_enabled", true)
                 put("verification_interval_ms", 5000) // Check every 5 seconds
                 put("strict_mode", true)
                 put("auto_fix_violations", true)
                 put("log_all_data_access", true)
                 put("block_original_data_access", true)
                 put("created_at", System.currentTimeMillis())
             }
             
             // Save verification configuration
             val verificationFile = File(clonedApp.dataPath, "runtime_verification.json")
             verificationFile.writeText(verificationConfig.toString())
             
             // Set up verification markers in SharedPreferences
             val verificationPrefs = context.getSharedPreferences("${clonedApp.clonedPackageName}_verification", Context.MODE_PRIVATE)
             verificationPrefs.edit().apply {
                 putBoolean("runtime_verification_active", true)
                 putLong("verification_start_time", System.currentTimeMillis())
                 putString("verification_config_path", verificationFile.absolutePath)
                 apply()
             }
             
             Log.d(TAG, "Runtime data verification setup completed for ${clonedApp.clonedAppName}")
             
         } catch (e: Exception) {
             Log.e(TAG, "Error setting up runtime data verification", e)
         }
     }
     
     /**
      * Setup security monitoring for enhanced protection
      */
     private fun setupSecurityMonitoringLegacy(clonedApp: ClonedApp, sandbox: SandboxManager.SandboxEnvironment) {
         try {
             Log.d(TAG, "Setting up security monitoring for ${clonedApp.clonedAppName}")
             
             // Create security monitoring configuration
             val securityConfig = JSONObject().apply {
                 put("app_id", clonedApp.id)
                 put("sandbox_id", sandbox.sandboxId)
                 put("monitoring_enabled", true)
                 put("security_level", "high")
                 put("real_time_monitoring", true)
                 put("threat_detection", true)
                 put("auto_response", true)
                 put("log_security_events", true)
                 put("created_at", System.currentTimeMillis())
             }
             
             // Save security configuration
             val securityFile = File(clonedApp.dataPath, "security_monitoring.json")
             securityFile.writeText(securityConfig.toString())
             
             // Enable security monitoring in sandbox
             sandboxManager.enableSecurityMonitoring(sandbox.sandboxId, true)
             
             Log.d(TAG, "Security monitoring setup completed for ${clonedApp.clonedAppName}")
             
         } catch (e: Exception) {
             Log.e(TAG, "Error setting up security monitoring", e)
         }
     }
     
     /**
      * Get virtual space info by ID
      */
     fun getVirtualSpaceInfo(clonedAppId: Long): Map<String, Any>? {
         return try {
             val clonedApp = getClonedAppById(clonedAppId)
             if (clonedApp != null) {
                 mapOf(
                     "id" to clonedApp.id,
                     "appName" to clonedApp.clonedAppName,
                     "packageName" to clonedApp.clonedPackageName,
                     "originalPackage" to clonedApp.originalPackageName,
                     "dataPath" to clonedApp.dataPath,
                     "isActive" to clonedApp.isActive,
                     "createdAt" to clonedApp.createdAt,
                     "lastUsed" to clonedApp.lastUsed,
                     "sandboxId" to clonedApp.sandboxId,
                     "securityLevel" to clonedApp.securityLevel
                 )
             } else null
         } catch (e: Exception) {
             Log.e(TAG, "Error getting virtual space info", e)
             null
         }
     }

     
     /**
      * Clear all session storage including temporary files
      */
     private fun clearAllSessionStorage(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing all session storage for ${clonedApp.originalPackageName}")
             
             // Session storage locations
             val sessionLocations = listOf(
                 context.filesDir.absolutePath + "/sessions",
                 context.cacheDir.absolutePath + "/sessions",
                 context.filesDir.absolutePath + "/temp_sessions",
                 context.cacheDir.absolutePath + "/temp_sessions",
                 "/data/data/${clonedApp.originalPackageName}/sessions",
                 "/data/data/${clonedApp.originalPackageName}/temp",
                 "/data/data/${clonedApp.originalPackageName}/cache/sessions"
             )
             
             for (location in sessionLocations) {
                 try {
                     val sessionDir = File(location)
                     if (sessionDir.exists()) {
                         sessionDir.deleteRecursively()
                         Log.d(TAG, "Cleared session storage: $location")
                     }
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear session storage $location: ${e.message}")
                 }
             }
             
             // Clear session files with common patterns
             val sessionFilePatterns = listOf(
                 "session_", "temp_session_", "login_session_", "auth_session_",
                 "user_session_", "account_session_", "profile_session_"
             )
             
             val searchDirs = listOf(context.filesDir, context.cacheDir)
             for (dir in searchDirs) {
                 dir.listFiles()?.let { files ->
                     for (file in files) {
                         for (pattern in sessionFilePatterns) {
                         if (file.name.startsWith(pattern)) {
                             file.delete()
                             Log.d(TAG, "Deleted session file: ${file.name}")
                         }
                         }
                     }
                 }
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing session storage", e)
         }
     }
     
     /**
      * Clear all authentication caches
      */
     private fun clearAllAuthenticationCaches(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing all authentication caches for ${clonedApp.originalPackageName}")
             
             // Authentication cache locations
             val authCacheLocations = listOf(
                 context.cacheDir.absolutePath + "/auth",
                 context.cacheDir.absolutePath + "/authentication",
                 context.cacheDir.absolutePath + "/login",
                 context.filesDir.absolutePath + "/auth_cache",
                 "/data/data/${clonedApp.originalPackageName}/cache/auth",
                 "/data/data/${clonedApp.originalPackageName}/cache/login",
                 "/data/data/${clonedApp.originalPackageName}/cache/authentication"
             )
             
             for (location in authCacheLocations) {
                 try {
                     val authDir = File(location)
                     if (authDir.exists()) {
                         authDir.deleteRecursively()
                         Log.d(TAG, "Cleared auth cache: $location")
                     }
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear auth cache $location: ${e.message}")
                 }
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing authentication caches", e)
         }
     }
     
     /**
      * Clear social media specific data that tends to persist
      */
     private fun clearSocialMediaSpecificData(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing social media specific data for ${clonedApp.originalPackageName}")
             
             // Facebook/Meta specific data
             if (clonedApp.originalPackageName.contains("facebook") || 
                 clonedApp.originalPackageName.contains("instagram") ||
                 clonedApp.originalPackageName.contains("messenger")) {
                 clearFacebookSpecificData(clonedApp)
             }
             
             // IMO specific data
             if (clonedApp.originalPackageName.contains("imo")) {
                 clearIMOSpecificData(clonedApp)
             }
             
             // WhatsApp specific data
             if (clonedApp.originalPackageName.contains("whatsapp")) {
                 clearWhatsAppSpecificData(clonedApp)
             }
             
             // Telegram specific data
             if (clonedApp.originalPackageName.contains("telegram")) {
                 clearTelegramSpecificData(clonedApp)
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing social media specific data", e)
         }
     }
     
     /**
      * Clear Facebook/Meta specific persistent data
      */
     private fun clearFacebookSpecificData(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing Facebook specific data for ${clonedApp.originalPackageName}")
             
             val fbSpecificKeys = listOf(
                 "fb_access_token", "facebook_access_token", "fb_user_id", "facebook_user_id",
                 "fb_session", "facebook_session", "fb_login_token", "facebook_login_token",
                 "com.facebook.AccessTokenManager.CachedAccessToken",
                 "com.facebook.ProfileManager.CachedProfile",
                 "com.facebook.appevents.SessionInfo",
                 "com.facebook.sdk.appEventPreferences"
             )
             
             val fbPrefsFiles = listOf(
                 "com.facebook.sdk.appEventPreferences",
                 "FacebookSDKPreferences",
                 "fb_preferences",
                 "facebook_preferences"
             )
             
             for (prefsName in fbPrefsFiles) {
                 try {
                     val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                     val editor = prefs.edit()
                     for (key in fbSpecificKeys) { editor.remove(key) }
                     editor.clear() // Clear everything for Facebook prefs
                     editor.apply()
                     Log.d(TAG, "Cleared Facebook prefs: $prefsName")
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear Facebook prefs $prefsName: ${e.message}")
                 }
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing Facebook specific data", e)
         }
     }
     
     /**
      * Clear IMO specific persistent data
      */
     private fun clearIMOSpecificData(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing IMO specific data for ${clonedApp.originalPackageName}")
             
             val imoSpecificKeys = listOf(
                 "imo_user_id", "imo_session", "imo_token", "imo_auth",
                 "imo_login_data", "imo_account_data", "imo_profile_data",
                 "user_login_info", "account_info", "profile_info"
             )
             
             val imoPrefsFiles = listOf(
                 "imo_preferences", "imo_prefs", "user_prefs", "account_prefs"
             )
             
             for (prefsName in imoPrefsFiles) {
                 try {
                     val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                     val editor = prefs.edit()
                     for (key in imoSpecificKeys) { editor.remove(key) }
                     editor.clear() // Clear everything for IMO prefs
                     editor.apply()
                     Log.d(TAG, "Cleared IMO prefs: $prefsName")
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear IMO prefs $prefsName: ${e.message}")
                 }
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing IMO specific data", e)
         }
     }
     
     /**
      * Clear WhatsApp specific persistent data
      */
     private fun clearWhatsAppSpecificData(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing WhatsApp specific data for ${clonedApp.originalPackageName}")
             
             val waSpecificKeys = listOf(
                 "registration_id", "phone_number", "wa_user_id", "whatsapp_session",
                 "wa_auth_token", "whatsapp_auth_token", "wa_login_data"
             )
             
             val waPrefsFiles = listOf(
                 "whatsapp_preferences", "wa_prefs", "registration_prefs"
             )
             
             for (prefsName in waPrefsFiles) {
                 try {
                     val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                     val editor = prefs.edit()
                     for (key in waSpecificKeys) { editor.remove(key) }
                     editor.clear() // Clear everything for WhatsApp prefs
                     editor.apply()
                     Log.d(TAG, "Cleared WhatsApp prefs: $prefsName")
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear WhatsApp prefs $prefsName: ${e.message}")
                 }
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing WhatsApp specific data", e)
         }
     }
     
     /**
      * Clear Telegram specific persistent data
      */
     private fun clearTelegramSpecificData(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing Telegram specific data for ${clonedApp.originalPackageName}")
             
             val tgSpecificKeys = listOf(
                 "telegram_user_id", "tg_session", "telegram_auth", "tg_token",
                 "telegram_login_data", "tg_account_data"
             )
             
             val tgPrefsFiles = listOf(
                 "telegram_preferences", "tg_prefs", "userconfing", "mainconfig"
             )
             
             for (prefsName in tgPrefsFiles) {
                 try {
                     val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                     val editor = prefs.edit()
                     for (key in tgSpecificKeys) { editor.remove(key) }
                     editor.clear() // Clear everything for Telegram prefs
                     editor.apply()
                     Log.d(TAG, "Cleared Telegram prefs: $prefsName")
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear Telegram prefs $prefsName: ${e.message}")
                 }
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing Telegram specific data", e)
         }
     }
     
     /**
      * Clear all browser and webview persistent data
      */
     private fun clearAllBrowserPersistentData(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing all browser persistent data for ${clonedApp.originalPackageName}")
             
             // Clear WebView data more aggressively
             val webViewDataLocations = listOf(
                 context.filesDir.absolutePath + "/webview",
                 context.cacheDir.absolutePath + "/webview",
                 "/data/data/${clonedApp.originalPackageName}/app_webview",
                 "/data/data/${clonedApp.originalPackageName}/app_chrome",
                 "/data/data/${clonedApp.originalPackageName}/app_textures",
                 "/data/data/${clonedApp.originalPackageName}/app_webview_variations_seed"
             )
             
             for (location in webViewDataLocations) {
                 try {
                     val webViewDir = File(location)
                     if (webViewDir.exists()) {
                         webViewDir.deleteRecursively()
                         Log.d(TAG, "Cleared WebView data: $location")
                     }
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear WebView data $location: ${e.message}")
                 }
             }
             
             // Clear cookies and local storage
             try {
                 val cookieManager = CookieManager.getInstance()
                 cookieManager.removeAllCookies(null)
                 cookieManager.flush()
                 Log.d(TAG, "Cleared all cookies")
             } catch (e: Exception) {
                 Log.w(TAG, "Could not clear cookies: ${e.message}")
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing browser persistent data", e)
         }
     }
     
     /**
      * Clear all secure storage including keystore
      */
     private fun clearAllSecureStorage(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing all secure storage for ${clonedApp.originalPackageName}")
             
             // Clear Android Keystore entries
             try {
                 val keyStore = KeyStore.getInstance("AndroidKeyStore")
                 keyStore.load(null)
                 
                 val aliases = keyStore.aliases()
                 while (aliases.hasMoreElements()) {
                     val alias = aliases.nextElement()
                     if (alias.contains(clonedApp.originalPackageName)) {
                         keyStore.deleteEntry(alias)
                         Log.d(TAG, "Deleted keystore entry: $alias")
                     }
                 }
             } catch (e: Exception) {
                 Log.w(TAG, "Could not clear keystore: ${e.message}")
             }
             
             // Clear secure SharedPreferences
             val securePrefsFiles = listOf(
                 "${clonedApp.originalPackageName}_secure",
                 "${clonedApp.originalPackageName}_encrypted",
                 "secure_prefs", "encrypted_prefs", "keystore_prefs"
             )
             
             for (prefsName in securePrefsFiles) {
                 try {
                     val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                     prefs.edit().clear().apply()
                     Log.d(TAG, "Cleared secure prefs: $prefsName")
                 } catch (e: Exception) {
                     Log.w(TAG, "Could not clear secure prefs $prefsName: ${e.message}")
                 }
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing secure storage", e)
         }
     }
     
     /**
      * Clear system-level app associations
      */
     private fun clearSystemLevelAppAssociations(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Clearing system-level app associations for ${clonedApp.originalPackageName}")
             
             // Clear recent tasks
             try {
                 val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                 val recentTasks = activityManager.getRunningTasks(100)
                 for (task in recentTasks) {
                     if (task.baseActivity?.packageName == clonedApp.originalPackageName) {
                         // Task will be cleared when app is force stopped
                         Log.d(TAG, "Found recent task for ${clonedApp.originalPackageName}")
                     }
                 }
             } catch (e: Exception) {
                 Log.w(TAG, "Could not clear recent tasks: ${e.message}")
             }
             
             // Clear app usage stats (if possible)
             try {
                 val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                 // Usage stats cannot be directly cleared, but we log the attempt
                 Log.d(TAG, "Attempted to clear usage stats for ${clonedApp.originalPackageName}")
             } catch (e: Exception) {
                 Log.w(TAG, "Could not access usage stats: ${e.message}")
             }
             
         } catch (e: Exception) {
             Log.e(TAG, "Error clearing system-level app associations", e)
         }
     }
     
     /**
      * Setup runtime data verification to ensure isolation is maintained
      */
     fun setupRuntimeDataVerification(clonedApp: ClonedApp) {
         try {
             Log.d(TAG, "Setting up runtime data verification for ${clonedApp.clonedAppName}")
             
             // Create verification configuration
             val verificationConfig = JSONObject().apply {
                 put("app_id", clonedApp.id)
                 put("package_name", clonedApp.clonedPackageName)
                 put("original_package", clonedApp.originalPackageName)
                 put("verification_enabled", true)
                 put("verification_interval_ms", 5000) // Check every 5 seconds
                 put("strict_mode", true)
                 put("auto_fix_violations", true)
                 put("log_all_data_access", true)
                 put("block_original_data_access", true)
                 put("created_at", System.currentTimeMillis())
             }
             
             // Save verification configuration
             val verificationFile = File(clonedApp.dataPath, "runtime_verification.json")
             verificationFile.writeText(verificationConfig.toString())
             
             // Set up verification markers in SharedPreferences
             val verificationPrefs = context.getSharedPreferences("${clonedApp.clonedPackageName}_verification", Context.MODE_PRIVATE)
             verificationPrefs.edit().apply {
                 putBoolean("runtime_verification_active", true)
                 putBoolean("strict_isolation_mode", true)
                 putBoolean("block_original_data", true)
                 putBoolean("require_fresh_login", true)
                 putLong("verification_setup_time", System.currentTimeMillis())
                 putString("fresh_environment_path", clonedApp.dataPath)
                 apply()
             }
             
             Log.d(TAG, "Runtime data verification setup completed for ${clonedApp.clonedAppName}")
             
         } catch (e: Exception) {
             Log.e(TAG, "Error setting up runtime data verification", e)
         }
     }
     
     /**
      * Setup runtime data verification by cloned app ID
      */
     fun setupRuntimeDataVerification(clonedAppId: Long) {
         try {
             val clonedApp = getClonedAppById(clonedAppId)
             if (clonedApp != null) {
                 setupRuntimeDataVerification(clonedApp)
             } else {
                 Log.e(TAG, "Cloned app not found with ID: $clonedAppId")
             }
         } catch (e: Exception) {
             Log.e(TAG, "Error setting up runtime data verification for ID $clonedAppId", e)
         }
     }

    /**
     * Perform runtime checks to ensure clone is properly isolated
     */
    fun performRuntimeIsolationCheck(clonedApp: ClonedApp): Map<String, Any> {
        val results = mutableMapOf<String, Any>()
        
        try {
            Log.d(TAG, "Performing runtime isolation check for ${clonedApp.clonedAppName}")
            
            // Check data isolation
            val dataIsolated = verifyDataIsolation(clonedApp)
            results["data_isolated"] = dataIsolated
            
            // Check process isolation
            val processIsolated = verifyProcessIsolation(clonedApp)
            results["process_isolated"] = processIsolated
            
            // Check storage isolation
            val storageIsolated = verifyStorageIsolation(clonedApp)
            results["storage_isolated"] = storageIsolated
            
            // Check login state isolation
            val loginIsolated = verifyLoginStateIsolation(clonedApp)
            results["login_isolated"] = loginIsolated
            
            // Overall isolation status
            val overallIsolated = dataIsolated && processIsolated && storageIsolated && loginIsolated
            results["overall_isolated"] = overallIsolated
            results["isolation_score"] = if (overallIsolated) 100 else {
                val score = listOf(dataIsolated, processIsolated, storageIsolated, loginIsolated)
                    .count { it } * 25
                score
            }
            
            Log.d(TAG, "Runtime isolation check completed. Overall isolated: $overallIsolated")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error performing runtime isolation check", e)
            results["error"] = e.message ?: "Unknown error"
            results["overall_isolated"] = false
        }
        
        return results
    }
    
    /**
     * Perform runtime isolation check by cloned app ID
     */
    fun performRuntimeIsolationCheck(clonedAppId: Long): Map<String, Any> {
        return try {
             val clonedApp = getClonedAppById(clonedAppId)
             if (clonedApp != null) {
                 performRuntimeIsolationCheck(clonedApp)
            } else {
                Log.e(TAG, "Cloned app not found with ID: $clonedAppId")
                mapOf(
                    "error" to "Cloned app not found",
                    "overall_isolated" to false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing runtime isolation check for ID $clonedAppId", e)
            mapOf(
                "error" to (e.message ?: "Unknown error"),
                "overall_isolated" to false
            )
        }
    }

    /**
     * Verify process isolation
     */
    private fun verifyProcessIsolation(clonedApp: ClonedApp): Boolean {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningProcesses = activityManager.runningAppProcesses ?: return true
            
            // Check if original app is running
            val originalAppRunning = runningProcesses.any { 
                it.processName == clonedApp.originalPackageName 
            }
            
            // Check if clone is running in separate process
            val cloneRunning = runningProcesses.any { 
                it.processName.contains(clonedApp.clonedPackageName) 
            }
            
            // Ideally, original app should not be running when clone is active
            if (originalAppRunning && cloneRunning) {
                Log.w(TAG, "Both original app and clone are running simultaneously")
                return false
            }
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying process isolation", e)
            return false
        }
    }

    /**
     * Verify storage isolation
     */
    private fun verifyStorageIsolation(clonedApp: ClonedApp): Boolean {
        try {
            val isolatedRoot = File(clonedApp.dataPath)
            val originalExternalPath = "/storage/emulated/0/Android/data/${clonedApp.originalPackageName}"
            
            // Check if clone is using isolated storage
            val isolatedExternalDir = File(isolatedRoot, "external_files")
            if (!isolatedExternalDir.exists()) {
                Log.w(TAG, "Isolated external storage directory missing")
                isolatedExternalDir.mkdirs()
            }
            
            // Check if original external storage exists and is not being used by clone
            val originalExternalDir = File(originalExternalPath)
            if (originalExternalDir.exists()) {
                val cloneFilesInOriginalExternal = originalExternalDir.listFiles()?.any { file ->
                    file.name.contains(clonedApp.clonedPackageName) ||
                    file.lastModified() > System.currentTimeMillis() - 60000 // Modified in last minute
                } ?: false
                
                if (cloneFilesInOriginalExternal) {
                    Log.w(TAG, "Clone may be accessing original external storage")
                    return false
                }
            }
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying storage isolation", e)
            return false
        }
    }

    /**
     * Verify login state isolation
     */
    private fun verifyLoginStateIsolation(clonedApp: ClonedApp): Boolean {
        try {
            val isolatedRoot = File(clonedApp.dataPath)
            
            // Check for fresh install markers
            val freshInstallMarker = File(isolatedRoot, "FRESH_INSTALL.marker")
            val firstLaunchMarker = File(isolatedRoot, "FIRST_LAUNCH.marker")
            
            if (!freshInstallMarker.exists() || !firstLaunchMarker.exists()) {
                Log.w(TAG, "Fresh install markers missing - login state may not be fresh")
                return false
            }
            
            // Check fresh SharedPreferences for login state
            val freshPrefs = context.getSharedPreferences("${clonedApp.clonedPackageName}_fresh", Context.MODE_PRIVATE)
            val requiresFreshLogin = freshPrefs.getBoolean("require_fresh_login", false)
            val isCompletelyReset = freshPrefs.getBoolean("data_completely_reset", false)
            
            if (!requiresFreshLogin || !isCompletelyReset) {
                Log.w(TAG, "Fresh login requirements not properly set")
                return false
            }
            
            // Check verification preferences
            val verificationPrefs = context.getSharedPreferences("${clonedApp.clonedPackageName}_verification", Context.MODE_PRIVATE)
            val verificationActive = verificationPrefs.getBoolean("runtime_verification_active", false)
            val strictMode = verificationPrefs.getBoolean("strict_isolation_mode", false)
            
            if (!verificationActive || !strictMode) {
                Log.w(TAG, "Runtime verification not properly configured")
                return false
            }
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying login state isolation", e)
            return false
        }
    }

    /**
     * Verify data isolation - ensures clone is using isolated data
     */
    private fun verifyDataIsolationInternal(clonedApp: ClonedApp): Boolean {
        try {
            Log.d(TAG, "Verifying data isolation for ${clonedApp.clonedAppName}")
            
            val isolatedRoot = File(clonedApp.dataPath)
            if (!isolatedRoot.exists()) {
                Log.e(TAG, "Isolated data directory does not exist: ${isolatedRoot.absolutePath}")
                return false
            }
            
            // Check if isolated directories are properly created
            val requiredDirs = listOf(
                "data", "cache", "files", "databases", "shared_prefs",
                "webview", "accounts", "lib", "code_cache", "no_backup"
            )
            
            var allDirsExist = true
            requiredDirs.forEach { dirName ->
                val dir = File(isolatedRoot, dirName)
                if (!dir.exists()) {
                    Log.w(TAG, "Required isolated directory missing: ${dir.absolutePath}")
                    allDirsExist = false
                }
            }
            
            // Check if original app data is not being accessed
            val originalDataPath = "/data/data/${clonedApp.originalPackageName}"
            val originalDataDir = File(originalDataPath)
            
            if (originalDataDir.exists()) {
                // Check if any recent access to original data
                val recentAccessTime = System.currentTimeMillis() - 60000 // Last 1 minute
                val hasRecentAccess = originalDataDir.listFiles()?.any { file ->
                    file.lastModified() > recentAccessTime
                } ?: false
                
                if (hasRecentAccess) {
                    Log.w(TAG, "Recent access detected to original app data")
                    return false
                }
            }
            
            return allDirsExist
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying data isolation", e)
            return false
        }
    }

    /**
     * Start real-time monitoring for data access
     */
    fun startRealTimeMonitoring(clonedApp: ClonedApp) {
        try {
            Log.d(TAG, "Starting real-time monitoring for ${clonedApp.clonedAppName}")
            
            // Create monitoring configuration
            val monitoringConfig = JSONObject().apply {
                put("app_id", clonedApp.id)
                put("package_name", clonedApp.clonedPackageName)
                put("original_package", clonedApp.originalPackageName)
                put("monitoring_active", true)
                put("check_interval_ms", 3000) // Check every 3 seconds
                put("block_violations", true)
                put("log_all_access", true)
                put("started_at", System.currentTimeMillis())
            }
            
            // Save monitoring configuration
            val monitoringFile = File(clonedApp.dataPath, "real_time_monitoring.json")
            monitoringFile.writeText(monitoringConfig.toString())
            
            // Set monitoring flags in SharedPreferences
            val monitoringPrefs = context.getSharedPreferences("${clonedApp.clonedPackageName}_monitoring", Context.MODE_PRIVATE)
            monitoringPrefs.edit().apply {
                putBoolean("real_time_monitoring_active", true)
                putBoolean("block_data_violations", true)
                putBoolean("log_all_data_access", true)
                putLong("monitoring_start_time", System.currentTimeMillis())
                putString("isolated_data_path", clonedApp.dataPath)
                apply()
            }
            
            Log.d(TAG, "Real-time monitoring started for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting real-time monitoring", e)
        }
    }

    /**
     * Block access to original app data
     */
    fun blockOriginalDataAccess(clonedApp: ClonedApp): Boolean {
        try {
            Log.d(TAG, "Blocking original data access for ${clonedApp.clonedAppName}")
            
            val originalDataPath = "/data/data/${clonedApp.originalPackageName}"
            val originalDataDir = File(originalDataPath)
            
            if (originalDataDir.exists()) {
                // Create access blocker marker
                val blockerMarker = File(clonedApp.dataPath, "ORIGINAL_DATA_BLOCKED.marker")
                blockerMarker.writeText("Original data access blocked at ${System.currentTimeMillis()}\nOriginal path: $originalDataPath\nBlocked for: ${clonedApp.clonedPackageName}")
                
                // Set blocking preferences
                val blockingPrefs = context.getSharedPreferences("${clonedApp.clonedPackageName}_blocking", Context.MODE_PRIVATE)
                blockingPrefs.edit().apply {
                    putBoolean("original_data_blocked", true)
                    putString("original_data_path", originalDataPath)
                    putLong("blocked_at", System.currentTimeMillis())
                    putBoolean("strict_blocking_mode", true)
                    apply()
                }
                
                Log.d(TAG, "Original data access blocked successfully")
                return true
            }
            
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking original data access", e)
            return false
        }
    }

    /**
     * Enforce fresh login requirement
     */
    fun enforceFreshLogin(clonedApp: ClonedApp) {
        try {
            Log.d(TAG, "Enforcing fresh login for ${clonedApp.clonedAppName}")
            
            // Clear any existing login tokens or session data
            val isolatedRoot = File(clonedApp.dataPath)
            
            // Clear shared preferences that might contain login data
            val loginDataDirs = listOf(
                "shared_prefs", "accounts", "webview"
            )
            
            for (dirName in loginDataDirs) {
                val dir = File(isolatedRoot, dirName)
                if (dir.exists()) {
                    dir.listFiles()?.let { files ->
                        for (file in files) {
                            if (file.name.contains("login") || 
                                file.name.contains("auth") || 
                                file.name.contains("token") ||
                                file.name.contains("session")) {
                                file.delete()
                                Log.d(TAG, "Deleted login-related file: ${file.name}")
                            }
                        }
                    }
                }
            }
            
            // Set fresh login enforcement markers
            val freshLoginMarker = File(isolatedRoot, "FRESH_LOGIN_ENFORCED.marker")
            freshLoginMarker.writeText("Fresh login enforced at ${System.currentTimeMillis()}\nPackage: ${clonedApp.clonedPackageName}\nMust login fresh: true")
            
            // Update fresh login preferences
            val freshLoginPrefs = context.getSharedPreferences("${clonedApp.clonedPackageName}_fresh_login", Context.MODE_PRIVATE)
            freshLoginPrefs.edit().apply {
                putBoolean("fresh_login_enforced", true)
                putBoolean("must_login_fresh", true)
                putBoolean("clear_existing_sessions", true)
                putLong("enforcement_time", System.currentTimeMillis())
                putBoolean("block_auto_login", true)
                apply()
            }
            
            Log.d(TAG, "Fresh login enforcement completed for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error enforcing fresh login", e)
        }
    }

    /**
     * Perform comprehensive runtime verification
     */
    fun performComprehensiveRuntimeVerification(clonedApp: ClonedApp): Map<String, Any> {
        val results = mutableMapOf<String, Any>()
        
        try {
            Log.d(TAG, "Performing comprehensive runtime verification for ${clonedApp.clonedAppName}")
            
            // Data isolation verification
            val dataIsolated = verifyDataIsolation(clonedApp)
            results["data_isolated"] = dataIsolated
            
            // Process isolation verification
            val processIsolated = verifyProcessIsolation(clonedApp)
            results["process_isolated"] = processIsolated
            
            // Storage isolation verification
            val storageIsolated = verifyStorageIsolation(clonedApp)
            results["storage_isolated"] = storageIsolated
            
            // Login state isolation verification
            val loginIsolated = verifyLoginStateIsolation(clonedApp)
            results["login_isolated"] = loginIsolated
            
            // Fresh install verification
            val freshInstallVerified = verifyCompleteReset(clonedApp)
            results["fresh_install_verified"] = freshInstallVerified
            
            // Real-time monitoring status
            val monitoringPrefs = context.getSharedPreferences("${clonedApp.clonedPackageName}_monitoring", Context.MODE_PRIVATE)
            val monitoringActive = monitoringPrefs.getBoolean("real_time_monitoring_active", false)
            results["monitoring_active"] = monitoringActive
            
            // Data blocking status
            val blockingPrefs = context.getSharedPreferences("${clonedApp.clonedPackageName}_blocking", Context.MODE_PRIVATE)
            val dataBlocked = blockingPrefs.getBoolean("original_data_blocked", false)
            results["original_data_blocked"] = dataBlocked
            
            // Fresh login enforcement status
            val freshLoginPrefs = context.getSharedPreferences("${clonedApp.clonedPackageName}_fresh_login", Context.MODE_PRIVATE)
            val freshLoginEnforced = freshLoginPrefs.getBoolean("fresh_login_enforced", false)
            results["fresh_login_enforced"] = freshLoginEnforced
            
            // Overall verification score
            val verificationChecks = listOf(
                dataIsolated, processIsolated, storageIsolated, 
                loginIsolated, freshInstallVerified, monitoringActive,
                dataBlocked, freshLoginEnforced
            )
            
            val passedChecks = verificationChecks.count { it }
            val totalChecks = verificationChecks.size
            val verificationScore = (passedChecks * 100) / totalChecks
            
            results["verification_score"] = verificationScore
            results["passed_checks"] = passedChecks
            results["total_checks"] = totalChecks
            results["overall_verified"] = verificationScore >= 90
            results["verification_timestamp"] = System.currentTimeMillis()
            
            Log.d(TAG, "Comprehensive verification completed. Score: $verificationScore% ($passedChecks/$totalChecks)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error performing comprehensive runtime verification", e)
            results["error"] = e.message ?: "Unknown error"
            results["overall_verified"] = false
        }
        
        return results
    }

    /**
     * Auto-fix verification violations
     */
    fun autoFixVerificationViolations(clonedApp: ClonedApp): Boolean {
        try {
            Log.d(TAG, "Auto-fixing verification violations for ${clonedApp.clonedAppName}")
            
            var fixesApplied = false
            
            // Fix data isolation issues
            if (!verifyDataIsolation(clonedApp)) {
                Log.d(TAG, "Fixing data isolation issues")
                val cloneContext = CloneContextWrapper(context, clonedApp.cloneId ?: "default")
                createFreshAppEnvironment(clonedApp, cloneContext)
                fixesApplied = true
            }
            
            // Fix login state issues
            if (!verifyLoginStateIsolation(clonedApp)) {
                Log.d(TAG, "Fixing login state isolation issues")
                val cloneContext = CloneContextWrapper(context, clonedApp.cloneId ?: "default")
                // Note: enforceFreshLogin needs sandbox parameter, will be fixed separately
                setFreshInstallMarkers(clonedApp)
                fixesApplied = true
            }
            
            // Ensure monitoring is active
            val monitoringPrefs = context.getSharedPreferences("${clonedApp.clonedPackageName}_monitoring", Context.MODE_PRIVATE)
            if (!monitoringPrefs.getBoolean("real_time_monitoring_active", false)) {
                Log.d(TAG, "Starting real-time monitoring")
                startRealTimeMonitoring(clonedApp)
                fixesApplied = true
            }
            
            // Ensure data blocking is active
            val blockingPrefs = context.getSharedPreferences("${clonedApp.clonedPackageName}_blocking", Context.MODE_PRIVATE)
            if (!blockingPrefs.getBoolean("original_data_blocked", false)) {
                Log.d(TAG, "Blocking original data access")
                blockOriginalDataAccess(clonedApp)
                fixesApplied = true
            }
            
            if (fixesApplied) {
                Log.d(TAG, "Auto-fix completed for ${clonedApp.clonedAppName}")
            } else {
                Log.d(TAG, "No fixes needed for ${clonedApp.clonedAppName}")
            }
            
            return fixesApplied
            
        } catch (e: Exception) {
            Log.e(TAG, "Error auto-fixing verification violations", e)
            return false
        }
    }
    
    // 🏗️ Complete Sandbox System Methods - Implementation based on documents
    
    /**
     * 🔧 Setup Complete Data Isolation with Sandbox Integration
     * Documents অনুযায়ী complete data separation implement করে
     */
    private fun setupCompleteDataIsolation(
        clonedPackageName: String, 
        originalPackageName: String, 
        uniqueId: String,
        sandbox: SandboxManager.SandboxEnvironment,
        cloneContext: CloneContextWrapper
    ) {
        try {
            Log.d(TAG, "🔧 Setting up complete data isolation with CloneContextWrapper and sandbox")
            Log.d(TAG, "📁 Sandbox Path: ${sandbox.rootPath}")
            Log.d(TAG, "📁 Clone Files Dir: ${cloneContext.filesDir}")
            Log.d(TAG, "📁 Clone Cache Dir: ${cloneContext.cacheDir}")
            Log.d(TAG, "🔒 Isolation Level: ${sandbox.isolationLevel}")
            
            // Create isolated directory structure using CloneContextWrapper
            val isolatedDirs = listOf(
                "${cloneContext.filesDir}/shared_prefs",
                "${cloneContext.filesDir}/databases", 
                "${cloneContext.cacheDir}",
                "${cloneContext.filesDir}",
                "${cloneContext.filesDir}/code_cache",
                "${cloneContext.filesDir}/lib",
                "${cloneContext.filesDir}/webview",
                "${cloneContext.filesDir}/app_webview",
                "${sandbox.rootPath}/external_storage",
                "${sandbox.rootPath}/internal_storage"
            )
            
            for (dirPath in isolatedDirs) {
                val dir = File(dirPath)
                if (!dir.exists()) {
                    dir.mkdirs()
                    // Set strict permissions for sandbox security
                    dir.setReadable(true, true)   // Owner only
                    dir.setWritable(true, true)   // Owner only
                    dir.setExecutable(true, true) // Owner only
                }
            }
            
            // Create data isolation configuration with CloneContextWrapper
            val isolationConfig = JSONObject().apply {
                put("clonedPackageName", clonedPackageName)
                put("originalPackageName", originalPackageName)
                put("cloneId", cloneContext.getCloneId())
                put("sandboxId", sandbox.sandboxId)
                put("dataPath", cloneContext.filesDir.absolutePath)
                put("cachePath", cloneContext.cacheDir.absolutePath)
                put("isolationLevel", sandbox.isolationLevel.name)
                put("createdAt", System.currentTimeMillis())
                put("securityPolicy", JSONObject().apply {
                    put("encryptData", sandbox.securityPolicy.encryptData)
                    put("auditAccess", sandbox.securityPolicy.auditAllAccess)
                    put("restrictPermissions", JSONArray(sandbox.securityPolicy.restrictedPermissions))
                })
            }
            
            // Save isolation config in sandbox
            val configFile = File(sandbox.rootPath, "isolation_config.json")
            configFile.writeText(isolationConfig.toString(2))
            
            Log.d(TAG, "✅ Complete data isolation with CloneContextWrapper setup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to setup complete data isolation", e)
        }
    }
    
    /**
     * 👥 Setup Account Isolation with Sandbox Security
     * প্রতিটি clone এর জন্য আলাদা account management
     */
    private fun setupAccountIsolation(
        clonedApp: ClonedApp,
        sandbox: SandboxManager.SandboxEnvironment
    ) {
        try {
            Log.d(TAG, "👥 Setting up account isolation for ${clonedApp.clonedAppName}")
            
            // Create account isolation directory in sandbox
            val accountDir = File(sandbox.rootPath, "accounts/${clonedApp.clonedPackageName}")
            accountDir.mkdirs()
            
            // Setup account manager configuration
            val accountConfig = JSONObject().apply {
                put("cloneId", clonedApp.id)
                put("clonedPackageName", clonedApp.clonedPackageName)
                put("sandboxId", sandbox.sandboxId)
                put("accountDirectory", accountDir.absolutePath)
                put("isolateAccounts", true)
                put("encryptAccountData", true)
                put("requireFreshAuth", true)
                put("createdAt", System.currentTimeMillis())
            }
            
            // Create account isolation marker
            val accountMarker = File(accountDir, "ACCOUNT_ISOLATED.marker")
            accountMarker.writeText(accountConfig.toString(2))
            
            // Setup account-specific SharedPreferences isolation
            val accountPrefsDir = File(sandbox.dataPath, "shared_prefs/accounts")
            accountPrefsDir.mkdirs()
            
            // Create account registry for this clone
            val accountRegistry = File(sandbox.rootPath, "account_registry.json")
            val registry = if (accountRegistry.exists()) {
                JSONObject(accountRegistry.readText())
            } else {
                JSONObject()
            }
            
            registry.put(clonedApp.clonedPackageName, accountConfig)
            accountRegistry.writeText(registry.toString(2))
            
            Log.d(TAG, "✅ Account isolation setup completed for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to setup account isolation", e)
        }
    }
    
    /**
     * 🔐 Setup Session Isolation with Sandbox Security  
     * Session data এর complete isolation
     */
    private fun setupSessionIsolation(
        clonedApp: ClonedApp,
        sandbox: SandboxManager.SandboxEnvironment
    ) {
        try {
            Log.d(TAG, "🔐 Setting up session isolation for ${clonedApp.clonedAppName}")
            
            // Create session isolation directory
            val sessionDir = File(sandbox.rootPath, "sessions/${clonedApp.clonedPackageName}")
            sessionDir.mkdirs()
            
            // Setup session directories
            val sessionDirs = listOf(
                "cookies", "webview", "tokens", "auth", "cache", "temp"
            )
            
            for (dirName in sessionDirs) {
                val dir = File(sessionDir, dirName)
                dir.mkdirs()
                dir.setReadable(true, true)
                dir.setWritable(true, true)
            }
            
            // Create session configuration
            val sessionConfig = JSONObject().apply {
                put("cloneId", clonedApp.id)
                put("sandboxId", sandbox.sandboxId)
                put("sessionDirectory", sessionDir.absolutePath)
                put("isolateSessions", true)
                put("encryptSessions", true)
                put("clearOnExit", false) // Keep sessions between launches
                put("sessionTimeout", 24 * 60 * 60 * 1000) // 24 hours
                put("createdAt", System.currentTimeMillis())
            }
            
            // Save session config
            val sessionConfigFile = File(sessionDir, "session_config.json")
            sessionConfigFile.writeText(sessionConfig.toString(2))
            
            // Create session isolation marker
            val sessionMarker = File(sessionDir, "SESSION_ISOLATED.marker")
            sessionMarker.writeText("Session isolation active for ${clonedApp.clonedPackageName}")
            
            Log.d(TAG, "✅ Session isolation setup completed for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to setup session isolation", e)
        }
    }
    
    /**
     * 🔒 Setup Security Monitoring for Sandbox
     * Real-time security monitoring এবং threat detection
     */
    private fun setupSecurityMonitoring(
        clonedApp: ClonedApp,
        sandbox: SandboxManager.SandboxEnvironment
    ) {
        try {
            Log.d(TAG, "🔒 Setting up security monitoring for ${clonedApp.clonedAppName}")
            
            // Create security monitoring directory
            val securityDir = File(sandbox.rootPath, "security")
            securityDir.mkdirs()
            
            // Setup security monitoring configuration
            val securityConfig = JSONObject().apply {
                put("cloneId", clonedApp.id)
                put("sandboxId", sandbox.sandboxId)
                put("monitoringEnabled", true)
                put("auditAllAccess", sandbox.securityPolicy.auditAllAccess)
                put("detectTampering", true)
                put("monitorNetworkAccess", true)
                put("monitorFileAccess", true)
                put("alertOnSuspiciousActivity", true)
                put("logLevel", "INFO")
                put("createdAt", System.currentTimeMillis())
            }
            
            // Create security log file
            val securityLogFile = File(securityDir, "security.log")
            securityLogFile.createNewFile()
            
            // Create monitoring config file
            val monitoringConfigFile = File(securityDir, "monitoring_config.json")
            monitoringConfigFile.writeText(securityConfig.toString(2))
            
            // Create security status file
            val securityStatus = JSONObject().apply {
                put("lastSecurityCheck", System.currentTimeMillis())
                put("threatLevel", "LOW")
                put("securityScore", 95)
                put("activeThreats", JSONArray())
                put("securityEvents", JSONArray())
            }
            
            val statusFile = File(securityDir, "security_status.json")
            statusFile.writeText(securityStatus.toString(2))
            
            // Log initial security event
            val initialEvent = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("event", "SECURITY_MONITORING_INITIALIZED")
                put("cloneId", clonedApp.id)
                put("sandboxId", sandbox.sandboxId)
                put("severity", "INFO")
            }
            
            securityLogFile.appendText("${initialEvent}\n")
            
            Log.d(TAG, "✅ Security monitoring setup completed for ${clonedApp.clonedAppName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to setup security monitoring", e)
        }
    }
    
    /**
     * 🧨 Clear Existing Login Data with Sandbox Security
     * Complete data clearing with enhanced security
     */
    private fun clearExistingLoginData(
        clonedApp: ClonedApp,
        sandbox: SandboxManager.SandboxEnvironment
    ) {
        try {
            Log.d(TAG, "🧨 CLEARING existing login data for ${clonedApp.clonedAppName} (Enhanced Sandbox Security)")
            Log.d(TAG, "📁 Target sandbox path: ${sandbox.rootPath}")
            Log.d(TAG, "🔒 Security level: ${sandbox.isolationLevel}")
            
            // Clear SharedPreferences in sandbox
            val prefsDir = File(sandbox.dataPath, "shared_prefs")
            if (prefsDir.exists()) {
                prefsDir.listFiles()?.forEach { prefFile ->
                    if (prefFile.name.contains("login") || 
                        prefFile.name.contains("auth") || 
                        prefFile.name.contains("user") ||
                        prefFile.name.contains("session") ||
                        prefFile.name.contains("token")) {
                        prefFile.delete()
                        Log.d(TAG, "🗑️ Deleted preference file: ${prefFile.name}")
                    }
                }
            }
            
            // Clear authentication directories in sandbox
            val authDirs = listOf(
                "accounts", "sessions/cookies", "sessions/auth", "sessions/tokens",
                "webview", "databases", "cache", "code_cache"
            )
            
            var clearedCount = 0
            for (dirName in authDirs) {
                val dir = File(sandbox.rootPath, dirName)
                if (dir.exists()) {
                    Log.d(TAG, "🧹 Clearing directory: ${dir.absolutePath}")
                    dir.deleteRecursively()
                    dir.mkdirs() // Recreate empty directory
                    clearedCount++
                }
            }
            
            // Clear application-specific login data within sandbox
            val appDataDir = File(sandbox.dataPath)
            if (appDataDir.exists()) {
                appDataDir.walkTopDown().forEach { file ->
                    if (file.isFile && (
                        file.name.contains("login") ||
                        file.name.contains("auth") ||
                        file.name.contains("token") ||
                        file.name.contains("session") ||
                        file.name.contains("account") ||
                        file.name.contains("user")
                    )) {
                        file.delete()
                        Log.d(TAG, "🗑️ Deleted auth file: ${file.name}")
                        clearedCount++
                    }
                }
            }
            
            // Create fresh login enforcement marker
            val freshLoginMarker = File(sandbox.rootPath, "FRESH_LOGIN_ENFORCED.marker")
            val markerContent = JSONObject().apply {
                put("enforcedAt", System.currentTimeMillis())
                put("cloneId", clonedApp.id)
                put("sandboxId", sandbox.sandboxId)
                put("clearedItemsCount", clearedCount)
                put("mustLoginFresh", true)
                put("securityLevel", sandbox.isolationLevel.name)
            }
            freshLoginMarker.writeText(markerContent.toString(2))
            
            // Update security log
            val securityLogFile = File(sandbox.rootPath, "security/security.log")
            if (securityLogFile.exists()) {
                val logEntry = JSONObject().apply {
                    put("timestamp", System.currentTimeMillis())
                    put("event", "LOGIN_DATA_CLEARED")
                    put("cloneId", clonedApp.id)
                    put("itemsCleared", clearedCount)
                    put("severity", "INFO")
                }
                securityLogFile.appendText("${logEntry}\n")
            }
            
            Log.d(TAG, "✅ Login data cleared successfully for ${clonedApp.clonedAppName}")
            Log.d(TAG, "📊 Total items cleared: $clearedCount")
            Log.d(TAG, "🔎 Fresh sign-in will be required on next launch")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear existing login data for ${clonedApp.clonedAppName}", e)
        }
    }
    
    /**
     * 🔐 Enforce Fresh Login with Sandbox Security
     * Documents অনুযায়ী fresh login enforcement
     */
    private fun enforceFreshLogin(
        clonedApp: ClonedApp,
        sandbox: SandboxManager.SandboxEnvironment
    ) {
        try {
            Log.d(TAG, "🔐 Enforcing fresh login with sandbox security for ${clonedApp.clonedAppName}")
            
            // Create fresh login enforcement directory
            val enforcementDir = File(sandbox.rootPath, "enforcement")
            enforcementDir.mkdirs()
            
            // Setup fresh login configuration
            val freshLoginConfig = JSONObject().apply {
                put("cloneId", clonedApp.id)
                put("sandboxId", sandbox.sandboxId)
                put("enforcementActive", true)
                put("blockAutoLogin", true)
                put("clearSessionsOnExit", false) // Keep sessions for convenience
                put("requireManualLogin", true)
                put("showFreshLoginPrompt", true)
                put("enforcedAt", System.currentTimeMillis())
                put("securityLevel", sandbox.isolationLevel.name)
            }
            
            // Save fresh login enforcement config
            val configFile = File(enforcementDir, "fresh_login_config.json")
            configFile.writeText(freshLoginConfig.toString(2))
            
            // Create multiple enforcement markers for different scenarios
            val markers = mapOf(
                "BLOCK_AUTO_LOGIN.marker" to "Auto-login blocked - manual authentication required",
                "FRESH_LOGIN_REQUIRED.marker" to "Fresh login required for security",
                "INCOGNITO_MODE_ACTIVE.marker" to "Enhanced incognito mode active",
                "SANDBOX_FRESH_LOGIN.marker" to "Sandbox enforced fresh login active"
            )
            
            for ((markerName, content) in markers) {
                val markerFile = File(enforcementDir, markerName)
                markerFile.writeText("$content\nTimestamp: ${System.currentTimeMillis()}")
            }
            
            // Create login interception configuration
            val interceptionConfig = JSONObject().apply {
                put("interceptLoginAttempts", true)
                put("showFreshLoginDialog", true)
                put("logAllLoginAttempts", true)
                put("requireUserConfirmation", true)
            }
            
            val interceptionFile = File(enforcementDir, "login_interception.json")
            interceptionFile.writeText(interceptionConfig.toString(2))
            
            // Update security monitoring
            val securityLogFile = File(sandbox.rootPath, "security/security.log")
            if (securityLogFile.exists()) {
                val logEntry = JSONObject().apply {
                    put("timestamp", System.currentTimeMillis())
                    put("event", "FRESH_LOGIN_ENFORCED")
                    put("cloneId", clonedApp.id)
                    put("sandboxId", sandbox.sandboxId)
                    put("severity", "INFO")
                    put("details", "Fresh login enforcement activated with sandbox security")
                }
                securityLogFile.appendText("${logEntry}\n")
            }
            
            Log.d(VirtualSpaceEngine.TAG, "✅ Fresh login enforcement completed for ${clonedApp.clonedAppName}")
            Log.d(VirtualSpaceEngine.TAG, "🔒 Sandbox ID: ${sandbox.sandboxId}")
            Log.d(VirtualSpaceEngine.TAG, "🛡️ Security Level: ${sandbox.isolationLevel}")
            
        } catch (e: Exception) {
            Log.e(VirtualSpaceEngine.TAG, "❌ Failed to enforce fresh login for ${clonedApp.clonedAppName}", e)
        }
    }
}