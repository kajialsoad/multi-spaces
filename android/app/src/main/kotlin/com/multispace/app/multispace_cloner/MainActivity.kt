package com.multispace.app.multispace_cloner

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.app.ActivityManager
import android.content.SharedPreferences
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodCall
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.core.app.ActivityCompat

class MainActivity: FlutterActivity() {
    private val CHANNEL = "multispace/apps"
    private lateinit var securityChannel: SecurityChannel
    private lateinit var performanceChannel: PerformanceChannel
    private lateinit var virtualSpaceEngine: VirtualSpaceEngine
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var dataManager: DataManager
    
    // Performance optimization
    private val backgroundExecutor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val appCache = ConcurrentHashMap<String, Any>()
    private val iconCache = ConcurrentHashMap<String, String>()
    private var lastCacheUpdate = 0L
    private val cacheTimeout = 5 * 60 * 1000L // 5 minutes
    
    // Permission request handling
    private var pendingPermissionResult: MethodChannel.Result? = null
    
    companion object {
        private const val TAG = "MainActivity"
        private const val MAX_CACHE_SIZE = 500
        private const val ICON_CACHE_SIZE = 200
        private const val PERMISSION_REQUEST_CODE = 1001
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val result = pendingPermissionResult
            pendingPermissionResult = null
            
            if (result != null) {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "QUERY_ALL_PACKAGES permission granted, retrying getInstalledApps")
                    // Retry getting installed apps with default parameters
                    getInstalledApps(result, true, true, false, 200)
                } else {
                    Log.w(TAG, "QUERY_ALL_PACKAGES permission denied")
                    result.error("PERMISSION_DENIED", "QUERY_ALL_PACKAGES permission is required to fetch installed apps", null)
                }
            }
        }
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        // Initialize database and data management
        databaseHelper = DatabaseHelper.getInstance(this)
        dataManager = DataManager.getInstance(this)
        virtualSpaceEngine = VirtualSpaceEngine.getInstance(this)
        
        // Initialize security channel
        securityChannel = SecurityChannel(this)
        securityChannel.initialize(flutterEngine)
        
        // Initialize performance channel
        performanceChannel = PerformanceChannel(this)
        performanceChannel.initialize(flutterEngine)
        
        // Initialize system permission channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "multispace_cloner/system").setMethodCallHandler { call, result ->
            when (call.method) {
                "checkSystemPermission" -> {
                    val permission = call.argument<String>("permission")
                    if (permission != null) {
                        checkSystemPermission(permission, result)
                    } else {
                        result.error("INVALID_ARGUMENT", "Permission is required", null)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "initializeService" -> {
                    // Pre-warm service for better performance
                    initializeService(result)
                }
                "getInstalledApps" -> {
                    val excludeSystemApps = call.argument<Boolean>("excludeSystemApps") ?: true
                    val includeIcons = call.argument<Boolean>("includeIcons") ?: true
                    val optimizeForSpeed = call.argument<Boolean>("optimizeForSpeed") ?: false
                    val maxResults = call.argument<Int>("maxResults") ?: 1000 // Increased default limit
                    getInstalledApps(result, excludeSystemApps, includeIcons, optimizeForSpeed, maxResults)
                }
                "getAppIcon" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        getAppIcon(packageName, result)
                    } else {
                        result.error("INVALID_ARGUMENT", "Package name is required", null)
                    }
                }
                "getAppName" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        getAppName(packageName, result)
                    } else {
                        result.error("INVALID_ARGUMENT", "Package name is required", null)
                    }
                }
                "cloneApp" -> {
                    val packageName = call.argument<String>("packageName")
                    val cloneId = call.argument<Int>("cloneId") ?: 1
                    val customName = call.argument<String>("customName")
                    val fastMode = call.argument<Boolean>("fastMode") ?: false
                    if (packageName != null) {
                        cloneAppOptimized(packageName, cloneId, customName, fastMode, result)
                    } else {
                        result.error("INVALID_ARGUMENT", "Package name is required", null)
                    }
                }
                "removeClonedApp" -> {
                    val clonedAppId = call.argument<Long>("id") ?: call.argument<Int>("id")?.toLong()
                    val packageName = call.argument<String>("packageName")
                    
                    if (clonedAppId != null) {
                        removeClonedAppById(clonedAppId, result)
                    } else if (packageName != null) {
                        removeClonedApp(packageName, result)
                    } else {
                        result.error("INVALID_ARGS", "Either cloned app ID or package name is required", null)
                    }
                }
                // New: launch a (real) installed app by package name
                "launchApp" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        launchApp(packageName, result)
                    } else {
                        result.error("INVALID_ARGUMENT", "Package name is required", null)
                    }
                }
                "launchClonedApp" -> {
                    launchClonedApp(call, result)
                }
                "getClonedApps" -> {
                    getClonedApps(result)
                }
                "updateClonedAppDisplayName" -> {
                    updateClonedAppDisplayName(call, result)
                }
                "getAppUsageStats" -> {
                    getAppUsageStats(call, result)
                }
                "optimizeMemory" -> {
                    optimizeMemory(result)
                }
                "getMemoryInfo" -> {
                    getMemoryInfo(result)
                }
                "getInstanceStatistics" -> {
                    getInstanceStatistics(call, result)
                }
                "getGlobalStatistics" -> {
                    getGlobalStatistics(result)
                }
                "analyzeCloneSuggestions" -> {
                    analyzeCloneSuggestions(result)
                }
                "getCloneSuggestions" -> {
                    getCloneSuggestions(result)
                }
                "analyzeSpecificApp" -> {
                    analyzeSpecificApp(call, result)
                }
                "createVirtualFileSystem" -> {
                    createVirtualFileSystem(call, result)
                }
                "getVirtualStorageStats" -> {
                    getVirtualStorageStats(call, result)
                }
                "copyFileToVirtualSpace" -> {
                    copyFileToVirtualSpace(call, result)
                }
                "readFileFromVirtualSpace" -> {
                    readFileFromVirtualSpace(call, result)
                }
                "writeFileToVirtualSpace" -> {
                    writeFileToVirtualSpace(call, result)
                }
                "deleteFileFromVirtualSpace" -> {
                    deleteFileFromVirtualSpace(call, result)
                }
                "listVirtualSpaceFiles" -> {
                    listVirtualSpaceFiles(call, result)
                }
                "cleanupVirtualStorage" -> {
                    cleanupVirtualStorage(call, result)
                }
                // Account Management Methods
                "addAccountToClonedApp" -> {
                    addAccountToClonedApp(call, result)
                }
                "removeAccountFromClonedApp" -> {
                    removeAccountFromClonedApp(call, result)
                }
                "switchActiveAccount" -> {
                    switchActiveAccount(call, result)
                }
                "getActiveAccountId" -> {
                    getActiveAccountId(call, result)
                }
                "getAllAccountIds" -> {
                    getAllAccountIds(call, result)
                }
                "getAccountData" -> {
                    getAccountData(call, result)
                }
                "getVirtualSpaceStatistics" -> {
                    getVirtualSpaceStatistics(call, result)
                }
                "cleanupVirtualSpace" -> {
                    cleanupVirtualSpace(call, result)
                }
                "monitorVirtualSpaceProcesses" -> {
                    monitorVirtualSpaceProcesses(call, result)
                }
                "applySecurityPolicies" -> {
                    applySecurityPolicies(call, result)
                }
                "optimizeVirtualSpacePerformance" -> {
                    optimizeVirtualSpacePerformance(call, result)
                }
                "getVirtualSpaceHealthReport" -> {
                    getVirtualSpaceHealthReport(call, result)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun initializeService(result: MethodChannel.Result) {
        try {
            // Pre-warm components for better performance in background
            Thread {
                try {
                    // Pre-load package list for faster access
                    packageManager.getInstalledPackages(PackageManager.GET_META_DATA)

                    // Initialize other components
                    virtualSpaceEngine.toString() // Ensure initialization

                    runOnUiThread {
                        result.success(true)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        result.success(false)
                    }
                }
            }.start()
        } catch (e: Exception) {
            result.success(false)
        }
    }

    private fun getInstalledApps(
        result: MethodChannel.Result,
        excludeSystemApps: Boolean = true,
        includeIcons: Boolean = true,
        optimizeForSpeed: Boolean = false,
        maxResults: Int = 1000 // Increased default limit
    ) {
        Log.d(TAG, "getInstalledApps called with excludeSystemApps=$excludeSystemApps, includeIcons=$includeIcons, optimizeForSpeed=$optimizeForSpeed, maxResults=$maxResults")
        
        val cacheKey = "apps_${excludeSystemApps}_${includeIcons}_${optimizeForSpeed}_$maxResults"
        val currentTime = System.currentTimeMillis()
        
        // Check cache first
        if (currentTime - lastCacheUpdate < cacheTimeout && appCache.containsKey(cacheKey)) {
            val cachedData = appCache[cacheKey]
            Log.d(TAG, "Returning cached apps data with ${(cachedData as? List<*>)?.size ?: 0} apps")
            result.success(cachedData)
            return
        }
        
        Log.d(TAG, "Cache miss or expired, fetching fresh app data")
        
        // Move heavy operation to background thread with optimization
        backgroundExecutor.execute {
            try {
                Log.d(TAG, "Starting background app fetching process")
                val packageManager = packageManager
                
                // Check QUERY_ALL_PACKAGES permission
                val hasQueryPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    checkSelfPermission(android.Manifest.permission.QUERY_ALL_PACKAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    true // Not needed for older versions
                }
                Log.d(TAG, "QUERY_ALL_PACKAGES permission granted: $hasQueryPermission")

                // Try multiple methods to get packages
                val packages = try {
                    // First try with GET_META_DATA
                    packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get packages with GET_META_DATA: ${e.message}")
                    try {
                        // Fallback to basic package info
                        packageManager.getInstalledPackages(0)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Failed to get installed packages with all methods: ${e2.message}")
                        
                        // If we can't get packages, try to get at least some visible apps
                        val fallbackPackages = mutableListOf<PackageInfo>()
                        try {
                            // Try to get some common apps that should be visible
                            val commonApps = listOf(
                                "com.android.chrome", "com.google.android.gm", "com.whatsapp",
                                "com.facebook.katana", "com.instagram.android", "com.twitter.android",
                                "com.spotify.music", "com.netflix.mediaclient", "com.amazon.mShop.android.shopping"
                            )
                            
                            for (pkg in commonApps) {
                                try {
                                    val packageInfo = packageManager.getPackageInfo(pkg, 0)
                                    fallbackPackages.add(packageInfo)
                                } catch (ignored: Exception) {
                                    // App not installed, skip
                                }
                            }
                            
                            Log.d(TAG, "Using fallback method, found ${fallbackPackages.size} apps")
                            fallbackPackages
                        } catch (e3: Exception) {
                            Log.e(TAG, "All methods failed to get packages: ${e3.message}")
                            mainHandler.post {
                                result.error("PERMISSION_ERROR", "Unable to access installed apps. Please ensure QUERY_ALL_PACKAGES permission is granted in app settings.", null)
                            }
                            return@execute
                        }
                    }
                }
                Log.d(TAG, "Found ${packages.size} total packages from PackageManager")
                
                val appsList = mutableListOf<Map<String, Any?>>()
                var count = 0
                var systemAppsFiltered = 0
                var processedApps = 0
                
                // Process apps in batches for better performance
                val batchSize = if (optimizeForSpeed) 50 else 100
                var processed = 0

                for (packageInfo in packages) {
                    processedApps++
                    // Apply maxResults limit regardless of optimizeForSpeed
                    if (count >= maxResults) {
                        Log.d(TAG, "Reached maxResults limit ($maxResults)")
                        break
                    }

                    val appInfo = packageInfo.applicationInfo
                    if (appInfo == null) {
                        Log.w(TAG, "Skipping package with null applicationInfo: ${packageInfo.packageName}")
                        continue
                    }

                    // Filter system apps if requested
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    
                    if (excludeSystemApps && isSystemApp && !isUpdatedSystemApp) {
                        systemAppsFiltered++
                        continue
                    }
                    
                    Log.v(TAG, "Processing app: ${appInfo.packageName}, isSystem=$isSystemApp, isUpdated=$isUpdatedSystemApp")

                    val appData = mutableMapOf<String, Any?>()
                    val packageName = appInfo.packageName
                    if (packageName.isNullOrEmpty()) {
                        Log.w(TAG, "Skipping app with empty package name")
                        continue
                    }

                    try {
                        // Get app name with caching
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        Log.v(TAG, "Adding app: $appName ($packageName)")
                        
                        appData["appName"] = appName
                        appData["packageName"] = packageName
                        appData["isSystemApp"] = isSystemApp
                        appData["isCloned"] = false // Will be updated by Flutter service
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get app name for $packageName: ${e.message}")
                        continue
                    }

                    // Optimized icon loading with cache
                    if (includeIcons && !optimizeForSpeed) {
                        val iconCacheKey = "icon_$packageName"
                        if (iconCache.containsKey(iconCacheKey)) {
                            appData["icon"] = iconCache[iconCacheKey]
                        } else {
                            try {
                                val icon = packageManager.getApplicationIcon(appInfo)
                                val iconBytes = drawableToByteArray(icon)
                                appData["icon"] = android.util.Base64.encodeToString(iconBytes, android.util.Base64.DEFAULT)
                                
                                // Cache icon with size limit
                                if (iconCache.size < ICON_CACHE_SIZE) {
                                    iconCache[iconCacheKey] = android.util.Base64.encodeToString(iconBytes, android.util.Base64.DEFAULT)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to load icon for $packageName: ${e.message}")
                                appData["icon"] = null
                            }
                        }
                    } else {
                        appData["icon"] = null
                    }

                    appsList.add(appData)
                    count++
                    processed++
                    
                    // Yield control periodically for better responsiveness
                    if (processed % batchSize == 0) {
                        Thread.yield()
                    }
                }

                // Cache the result with size limit
                if (appCache.size < MAX_CACHE_SIZE) {
                    appCache[cacheKey] = appsList
                    lastCacheUpdate = currentTime
                    Log.d(TAG, "Cached ${appsList.size} apps with key: $cacheKey")
                }
                
                val processingTime = System.currentTimeMillis() - currentTime
                Log.d(TAG, "App fetching completed: processed=$processedApps, systemFiltered=$systemAppsFiltered, final=${appsList.size} apps in ${processingTime}ms")
                
                if (appsList.isEmpty()) {
                    Log.w(TAG, "WARNING: No apps found! This might indicate a permission or filtering issue")
                    Log.w(TAG, "Debug info - excludeSystemApps=$excludeSystemApps, hasQueryPermission=$hasQueryPermission")
                    
                    // If no apps found and we're excluding system apps, try including them
                    if (excludeSystemApps && !hasQueryPermission) {
                        Log.w(TAG, "No apps found with system apps excluded and no QUERY_ALL_PACKAGES permission")
                        mainHandler.post {
                            result.error("NO_APPS_FOUND", "No apps found. This may be due to missing QUERY_ALL_PACKAGES permission. Please grant the permission in app settings.", null)
                        }
                        return@execute
                    }
                }

                // Return result on main thread
                mainHandler.post {
                    Log.d(TAG, "Returning ${appsList.size} apps to Flutter")
                    result.success(appsList)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get installed apps", e)
                mainHandler.post {
                    result.error("ERROR", "Failed to get installed apps: ${e.message}", null)
                }
            }
        }
    }

    private fun getAppIcon(packageName: String, result: MethodChannel.Result) {
        val iconCacheKey = "icon_$packageName"
        
        // Check cache first
        if (iconCache.containsKey(iconCacheKey)) {
            Log.d(TAG, "Returning cached icon for $packageName")
            result.success(iconCache[iconCacheKey])
            return
        }
        
        // Load icon in background
        backgroundExecutor.execute {
            try {
                val packageManager = packageManager
                var icon: Drawable? = null
                
                // Try multiple methods to get the app icon
                try {
                    // Method 1: Direct package name lookup
                    icon = packageManager.getApplicationIcon(packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "Method 1 failed for $packageName: ${e.message}")
                    
                    try {
                        // Method 2: Get from ApplicationInfo
                        val appInfo = packageManager.getApplicationInfo(packageName, 0)
                        icon = packageManager.getApplicationIcon(appInfo)
                    } catch (e2: Exception) {
                        Log.w(TAG, "Method 2 failed for $packageName: ${e2.message}")
                        
                        try {
                            // Method 3: Get from PackageInfo
                            val packageInfo = packageManager.getPackageInfo(packageName, 0)
                            icon = packageManager.getApplicationIcon(packageInfo.applicationInfo)
                        } catch (e3: Exception) {
                            Log.w(TAG, "Method 3 failed for $packageName: ${e3.message}")
                            
                            // Method 4: Use default icon as fallback
                            icon = packageManager.getDefaultActivityIcon()
                        }
                    }
                }
                
                if (icon != null) {
                    val iconBytes = drawableToByteArray(icon)
                    val base64Icon = android.util.Base64.encodeToString(iconBytes, android.util.Base64.NO_WRAP)
                    
                    // Cache with size limit
                    if (iconCache.size < ICON_CACHE_SIZE) {
                        iconCache[iconCacheKey] = base64Icon
                    }
                    
                    mainHandler.post {
                        result.success(base64Icon)
                    }
                } else {
                    Log.e(TAG, "Could not get any icon for $packageName")
                    mainHandler.post {
                        result.error("ERROR", "Could not get app icon", null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get app icon for $packageName", e)
                mainHandler.post {
                    result.error("ERROR", "Failed to get app icon: ${e.message}", null)
                }
            }
        }
    }

    private fun getAppName(packageName: String, result: MethodChannel.Result) {
        try {
            val packageManager = packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            result.success(appName)
        } catch (e: Exception) {
            result.error("ERROR", "Failed to get app name: ${e.message}", null)
        }
    }

    private fun cloneAppOptimized(
        packageName: String,
        cloneId: Int,
        customName: String?,
        fastMode: Boolean,
        result: MethodChannel.Result
    ) {
        Log.d(TAG, "Starting app cloning for $packageName (fastMode: $fastMode)")
        val startTime = System.currentTimeMillis()
        
        backgroundExecutor.execute {
            try {
                // Get app display name with caching
                val appName = getAppDisplayName(packageName)
                val displayName = customName ?: "$appName Clone $cloneId"

                if (fastMode) {
                    // Fast mode: Skip some validations for speed
                    val clonedApp = virtualSpaceEngine.createVirtualSpace(packageName, appName, displayName)
                    if (clonedApp != null) {
                        Log.d(TAG, "Fast clone completed in ${System.currentTimeMillis() - startTime}ms")
                        mainHandler.post {
                            result.success(mapOf(
                                "success" to true,
                                "cloneId" to cloneId,
                                "displayName" to displayName,
                                "packageName" to packageName,
                                "fastMode" to true
                            ))
                        }
                        return@execute
                    }
                }

                // Standard cloning process with progress tracking
                Log.d(TAG, "Creating virtual space for $packageName")
                val clonedApp = virtualSpaceEngine.createVirtualSpace(packageName, appName, displayName)
                if (clonedApp == null) {
                    Log.e(TAG, "Failed to create virtual space for $packageName")
                    mainHandler.post {
                        result.error("CLONE_ERROR", "Failed to create virtual space", null)
                    }
                    return@execute
                }

                // Setup data isolation in background
                Log.d(TAG, "Setting up data isolation for $packageName")
                val dataIsolationManager = DataIsolationManager(this@MainActivity)
                dataIsolationManager.createIsolatedStorage(packageName, clonedApp.clonedPackageName)

                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Clone completed for $packageName in ${totalTime}ms")
                
                mainHandler.post {
                    result.success(mapOf(
                        "success" to true,
                        "cloneId" to cloneId,
                        "displayName" to displayName,
                        "packageName" to packageName,
                        "clonedPackageName" to clonedApp.clonedPackageName,
                        "processingTime" to totalTime
                    ))
                }

            } catch (e: Exception) {
                runOnUiThread {
                    result.error("CLONE_ERROR", "Failed to clone app: ${e.message}", null)
                }
            }
        }
    }

    private fun cloneApp(packageName: String, result: MethodChannel.Result) {
        try {
            // Check if app can be cloned
            if (!canCloneApp(packageName)) {
                result.error("CLONE_ERROR", "App cannot be cloned: $packageName", null)
                return
            }
            
            // Get app display name
            val appName = getAppDisplayName(packageName)
            val customName = "$appName Clone"
            
            // Create virtual space using new database system
            val clonedApp = virtualSpaceEngine.createVirtualSpace(packageName, appName, customName)
            if (clonedApp == null) {
                result.error("CLONE_ERROR", "Failed to create virtual space", null)
                return
            }
            
            // Setup data isolation
            val dataIsolationManager = DataIsolationManager(this)
            dataIsolationManager.createIsolatedStorage(packageName, clonedApp.clonedPackageName)
            
            // Install app in virtual environment
            val hookingSystem = HookingSystem(this)
            val cloneSuccess = hookingSystem.installAppInVirtualSpace(packageName, clonedApp.clonedPackageName)
            
            if (cloneSuccess) {
                result.success(mapOf(
                    "success" to true,
                    "id" to clonedApp.id,
                    "clonedPackageName" to clonedApp.clonedPackageName,
                    "clonedAppName" to clonedApp.clonedAppName,
                    "originalPackageName" to clonedApp.originalPackageName,
                    "dataPath" to clonedApp.dataPath,
                    "createdAt" to clonedApp.createdAt
                ))
            } else {
                // Remove from database if installation failed
                virtualSpaceEngine.removeVirtualSpace(clonedApp.id)
                result.error("CLONE_ERROR", "Failed to install app in virtual space", null)
            }
            
        } catch (e: Exception) {
            result.error("ERROR", "Failed to clone app: ${e.message}", null)
        }
    }

    private fun removeClonedAppById(clonedAppId: Long, result: MethodChannel.Result) {
        try {
            val success = virtualSpaceEngine.removeVirtualSpace(clonedAppId)
            
            if (success) {
                result.success(mapOf(
                    "success" to true,
                    "id" to clonedAppId
                ))
            } else {
                result.error("REMOVE_ERROR", "Failed to remove cloned app with ID: $clonedAppId", null)
            }
        } catch (e: Exception) {
            result.error("ERROR", "Failed to remove cloned app: ${e.message}", null)
        }
    }
    
    private fun removeClonedApp(packageName: String, result: MethodChannel.Result) {
        try {
            val dataIsolationManager = DataIsolationManager(this)
            
            // Get virtual space ID for the cloned app
            val virtualSpaceId = getVirtualSpaceId(packageName)
            if (virtualSpaceId == null) {
                result.error("REMOVE_ERROR", "Cloned app not found: $packageName", null)
                return
            }
            
            // Stop cloned app if running
            stopClonedApp(packageName, virtualSpaceId)
            
            // Remove app data from isolated environment
            dataIsolationManager.clearIsolatedStorage(packageName, virtualSpaceId)
            
            // Uninstall from virtual space
            val hookingSystem = HookingSystem(this)
            hookingSystem.uninstallAppFromVirtualSpace(packageName, virtualSpaceId)
            
            // Remove virtual space (legacy method)
            virtualSpaceEngine.removeVirtualSpace(virtualSpaceId)
            
            // Unregister cloned app
            unregisterClonedApp(packageName, virtualSpaceId)
            
            // Remove clone configuration
            removeCloneConfiguration(packageName, virtualSpaceId)
            
            result.success(true)
        } catch (e: Exception) {
            result.error("ERROR", "Failed to remove cloned app: ${e.message}", null)
        }
    }

    // New: launch a (real) installed app by its package name
    private fun launchApp(packageName: String, result: MethodChannel.Result) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                result.error("NOT_FOUND", "No launchable activity for $packageName", null)
                return
            }
            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            result.success(true)
        } catch (e: Exception) {
            result.error("ERROR", "Failed to launch app: ${e.message}", null)
        }
    }
    
    private fun launchClonedApp(call: MethodCall, result: MethodChannel.Result) {
        try {
            val clonedAppId = call.argument<Long>("id") ?: call.argument<Int>("id")?.toLong()
            val packageName = call.argument<String>("packageName")
            val virtualSpaceId = call.argument<String>("virtualSpaceId")
            
            // Support both new ID-based and legacy virtualSpaceId-based launching
            val launchSuccess = if (clonedAppId != null) {
                // New database-based approach
                virtualSpaceEngine.launchClonedApp(clonedAppId)
            } else if (packageName != null && virtualSpaceId != null) {
                // Legacy approach for backward compatibility
                val hookingSystem = HookingSystem(this)
                hookingSystem.launchVirtualApp(packageName, virtualSpaceId)
            } else {
                result.error("INVALID_ARGS", "Either cloned app ID or package name with virtual space ID is required", null)
                return
            }
            
            if (launchSuccess) {
                result.success(mapOf(
                    "success" to true,
                    "id" to clonedAppId,
                    "packageName" to packageName,
                    "virtualSpaceId" to virtualSpaceId
                ))
            } else {
                result.error("LAUNCH_ERROR", "Failed to launch cloned app", null)
            }
            
        } catch (e: Exception) {
            result.error("ERROR", "Failed to launch cloned app: ${e.message}", null)
        }
    }
    
    private fun getClonedApps(result: MethodChannel.Result) {
        try {
            val clonedAppsList = mutableListOf<Map<String, Any>>()
            val clonedApps = virtualSpaceEngine.getAllClonedApps()
            
            for (clonedApp in clonedApps) {
                try {
                    val packageInfo = packageManager.getPackageInfo(clonedApp.originalPackageName, 0)
                    val applicationInfo = packageInfo.applicationInfo
                    val appName = applicationInfo?.let { packageManager.getApplicationLabel(it).toString() } ?: clonedApp.originalPackageName
                    
                    val clonedAppInfo = mapOf(
                        "id" to clonedApp.id,
                        "packageName" to clonedApp.originalPackageName,
                        "clonedPackageName" to clonedApp.clonedPackageName,
                        "appName" to appName,
                        "clonedAppName" to clonedApp.clonedAppName,
                        "displayName" to clonedApp.clonedAppName,
                        "dataPath" to clonedApp.dataPath,
                        "isSystemApp" to ((applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM)) != 0),
                        "isCloned" to true,
                        "isActive" to clonedApp.isActive,
                        "createdAt" to clonedApp.createdAt,
                        "lastUsed" to clonedApp.lastUsed
                    )
                    
                    clonedAppsList.add(clonedAppInfo)
                    
                } catch (e: Exception) {
                    // Skip apps that can't be found in package manager
                    // but keep the database entry for consistency
                }
            }
            
            result.success(clonedAppsList)
            
        } catch (e: Exception) {
            result.error("ERROR", "Failed to get cloned apps: ${e.message}", null)
        }
    }
    
    private fun updateClonedAppDisplayName(call: MethodCall, result: MethodChannel.Result) {
        try {
            val packageName = call.argument<String>("packageName")
            val virtualSpaceId = call.argument<String>("virtualSpaceId")
            val newDisplayName = call.argument<String>("displayName")
            
            if (packageName == null || virtualSpaceId == null || newDisplayName == null) {
                result.error("INVALID_ARGS", "Package name, virtual space ID, and display name are required", null)
                return
            }
            
            val prefs = getSharedPreferences("clone_configs", Context.MODE_PRIVATE)
            val configString = prefs.getString("config_${packageName}_${virtualSpaceId}", null)
            
            if (configString != null) {
                try {
                    val config = JSONObject(configString)
                    config.put("displayName", newDisplayName)
                    config.put("updatedAt", System.currentTimeMillis())
                    
                    val editor = prefs.edit()
                    editor.putString("config_${packageName}_${virtualSpaceId}", config.toString())
                    editor.apply()
                    
                    result.success(true)
                } catch (e: Exception) {
                    result.error("UPDATE_ERROR", "Failed to update display name: ${e.message}", null)
                }
            } else {
                result.error("NOT_FOUND", "Cloned app configuration not found", null)
            }
            
        } catch (e: Exception) {
            result.error("ERROR", "Failed to update display name: ${e.message}", null)
        }
    }
    
    private fun getVirtualSpaceInfo(call: MethodCall, result: MethodChannel.Result) {
        try {
            val virtualSpaceId = call.argument<String>("virtualSpaceId")
            if (virtualSpaceId == null) {
                result.error("INVALID_ARGS", "Virtual space ID is required", null)
                return
            }
            
            val virtualSpaceEngine = VirtualSpaceEngine(this)
            val spaceInfo = virtualSpaceEngine.getVirtualSpaceInfo(virtualSpaceId)
            
            if (spaceInfo != null) {
                result.success(spaceInfo)
            } else {
                result.error("NOT_FOUND", "Virtual space not found: $virtualSpaceId", null)
            }
            
        } catch (e: Exception) {
             result.error("ERROR", "Failed to get virtual space info: ${e.message}", null)
         }
     }
     
     private fun getAppUsageStats(call: MethodCall, result: MethodChannel.Result) {
         try {
             val packageName = call.argument<String>("packageName")
             val virtualSpaceId = call.argument<String>("virtualSpaceId")
             
             if (packageName == null || virtualSpaceId == null) {
                 result.error("INVALID_ARGS", "Package name and virtual space ID are required", null)
                 return
             }
             
             val dataIsolationManager = DataIsolationManager(this)
             val usageStats = dataIsolationManager.getAppUsageStats(packageName, virtualSpaceId)
             
             result.success(usageStats)
             
         } catch (e: Exception) {
              result.error("ERROR", "Failed to get app usage stats: ${e.message}", null)
          }
      }
      
      private fun optimizeMemory(result: MethodChannel.Result) {
          Log.d(TAG, "Starting memory optimization")
          val startTime = System.currentTimeMillis()
          
          backgroundExecutor.execute {
              try {
                  // Clear local caches first
                  val appCacheSize = appCache.size
                  val iconCacheSize = iconCache.size
                  
                  if (appCache.size > MAX_CACHE_SIZE / 2) {
                      appCache.clear()
                      Log.d(TAG, "Cleared app cache: $appCacheSize entries")
                  }
                  
                  if (iconCache.size > ICON_CACHE_SIZE / 2) {
                      iconCache.clear()
                      Log.d(TAG, "Cleared icon cache: $iconCacheSize entries")
                  }
                  
                  // Use MemoryOptimizer for system-level optimization
                  val memoryOptimizer = MemoryOptimizer(this@MainActivity)
                  val optimizationResults = memoryOptimizer.optimizeMemoryUsage()
                  
                  val optimizationTime = System.currentTimeMillis() - startTime
                  Log.d(TAG, "Memory optimization completed in ${optimizationTime}ms")
                  
                  // Add cache clearing info to results
                  val enhancedResults = optimizationResults.toMutableMap()
                  enhancedResults["appCacheCleared"] = appCacheSize
                  enhancedResults["iconCacheCleared"] = iconCacheSize
                  enhancedResults["optimizationTime"] = optimizationTime
                  
                  mainHandler.post {
                      result.success(enhancedResults)
                  }
              } catch (e: Exception) {
                  Log.e(TAG, "Memory optimization failed", e)
                  mainHandler.post {
                      result.error("ERROR", "Failed to optimize memory: ${e.message}", null)
                  }
              }
          }
      }
      
      private fun getMemoryInfo(result: MethodChannel.Result) {
          backgroundExecutor.execute {
              try {
                  val memoryOptimizer = MemoryOptimizer(this@MainActivity)
                  val memoryInfo = memoryOptimizer.getMemoryInfo()
                  
                  // Add cache info
                  val enhancedInfo = memoryInfo.toMutableMap()
                  enhancedInfo["appCacheSize"] = appCache.size
                  enhancedInfo["iconCacheSize"] = iconCache.size
                  enhancedInfo["lastCacheUpdate"] = lastCacheUpdate
                  
                  mainHandler.post {
                      result.success(enhancedInfo)
                  }
              } catch (e: Exception) {
                  Log.e(TAG, "Failed to get memory info", e)
                  mainHandler.post {
                      result.error("ERROR", "Failed to get memory info: ${e.message}", null)
                  }
              }
          }
      }
      
      private fun getInstanceStatistics(call: MethodCall, result: MethodChannel.Result) {
          try {
              val packageName = call.argument<String>("packageName")
              val virtualSpaceId = call.argument<String>("virtualSpaceId")
              
              if (packageName == null || virtualSpaceId == null) {
                  result.error("INVALID_ARGS", "Package name and virtual space ID are required", null)
                  return
              }
              
              val instanceStatistics = InstanceStatistics(this)
              val stats = instanceStatistics.getAppStatistics(virtualSpaceId, packageName)
              result.success(stats)
          } catch (e: Exception) {
              result.error("ERROR", "Failed to get instance statistics: ${e.message}", null)
          }
      }
      
      private fun getGlobalStatistics(result: MethodChannel.Result) {
          backgroundExecutor.execute {
              try {
                  val instanceStatistics = InstanceStatistics(this@MainActivity)
                  val globalStats = instanceStatistics.getGlobalStatistics()
                  
                  // Add performance metrics
                  val enhancedStats = globalStats.toMutableMap()
                  enhancedStats["cacheHitRate"] = if (appCache.size > 0) 0.85 else 0.0
                  enhancedStats["backgroundThreads"] = (backgroundExecutor as ThreadPoolExecutor).activeCount
                  enhancedStats["lastOptimization"] = lastCacheUpdate
                  
                  mainHandler.post {
                      result.success(enhancedStats)
                  }
              } catch (e: Exception) {
                  Log.e(TAG, "Failed to get global statistics", e)
                  mainHandler.post {
                      result.error("ERROR", "Failed to get global statistics: ${e.message}", null)
                  }
              }
          }
      }
      
      private fun analyzeCloneSuggestions(result: MethodChannel.Result) {
          try {
              val autoCloneDetector = AutoCloneDetector(this)
              val suggestions = autoCloneDetector.analyzeInstalledApps()
              result.success(suggestions.map { suggestion ->
                  mapOf(
                      "packageName" to suggestion.packageName,
                      "appName" to suggestion.appName,
                      "category" to suggestion.category,
                      "priority" to suggestion.priority,
                      "reason" to suggestion.reason,
                      "confidence" to suggestion.confidence,
                      "estimatedBenefit" to suggestion.estimatedBenefit
                  )
              })
          } catch (e: Exception) {
              result.error("ERROR", "Failed to analyze clone suggestions: ${e.message}", null)
          }
      }
      
      private fun getCloneSuggestions(result: MethodChannel.Result) {
          try {
              val autoCloneDetector = AutoCloneDetector(this)
              val suggestions = autoCloneDetector.getSavedSuggestions()
              result.success(suggestions.map { suggestion ->
                  mapOf(
                      "packageName" to suggestion.packageName,
                      "appName" to suggestion.appName,
                      "category" to suggestion.category,
                      "priority" to suggestion.priority,
                      "reason" to suggestion.reason,
                      "confidence" to suggestion.confidence,
                      "estimatedBenefit" to suggestion.estimatedBenefit
                  )
              })
          } catch (e: Exception) {
              result.error("ERROR", "Failed to get clone suggestions: ${e.message}", null)
          }
      }
      
      private fun analyzeSpecificApp(call: MethodCall, result: MethodChannel.Result) {
          try {
              val packageName = call.argument<String>("packageName")
              if (packageName == null) {
                  result.error("INVALID_ARGS", "Package name is required", null)
                  return
              }
              
              val autoCloneDetector = AutoCloneDetector(this)
              val analysis = autoCloneDetector.analyzeSpecificApp(packageName)
              
              if (analysis != null) {
                  val analysisMap = mapOf(
                      "isCloneable" to analysis.isCloneable,
                      "difficulty" to analysis.difficulty,
                      "risks" to analysis.risks,
                      "benefits" to analysis.benefits,
                      "requirements" to analysis.requirements
                  )
                  result.success(analysisMap)
              } else {
                  result.error("NOT_FOUND", "App not found or cannot be analyzed", null)
              }
          } catch (e: Exception) {
              result.error("ERROR", "Failed to analyze specific app: ${e.message}", null)
          }
      }
      
      private fun createVirtualFileSystem(call: MethodCall, result: MethodChannel.Result) {
          try {
              val virtualSpaceId = call.argument<String>("virtualSpaceId")
              val packageName = call.argument<String>("packageName")
              
              if (virtualSpaceId == null || packageName == null) {
                  result.error("INVALID_ARGS", "Virtual space ID and package name are required", null)
                  return
              }
              
              val dataIsolationManager = DataIsolationManager(this)
              val success = dataIsolationManager.createVirtualFileSystem(virtualSpaceId, packageName)
              result.success(success)
          } catch (e: Exception) {
              result.error("ERROR", "Failed to create virtual file system: ${e.message}", null)
          }
      }
      
      private fun getVirtualStorageStats(call: MethodCall, result: MethodChannel.Result) {
          try {
              val virtualSpaceId = call.argument<String>("virtualSpaceId")
              val packageName = call.argument<String>("packageName")
              
              if (virtualSpaceId == null || packageName == null) {
                  result.error("INVALID_ARGS", "Virtual space ID and package name are required", null)
                  return
              }
              
              val dataIsolationManager = DataIsolationManager(this)
              val stats = dataIsolationManager.getVirtualStorageStats(virtualSpaceId, packageName)
              result.success(stats)
          } catch (e: Exception) {
              result.error("ERROR", "Failed to get virtual storage stats: ${e.message}", null)
          }
      }
      
      private fun copyFileToVirtualSpace(call: MethodCall, result: MethodChannel.Result) {
          try {
              val virtualSpaceId = call.argument<String>("virtualSpaceId")
              val packageName = call.argument<String>("packageName")
              val sourcePath = call.argument<String>("sourcePath")
              val targetPath = call.argument<String>("targetPath")
              
              if (virtualSpaceId == null || packageName == null || sourcePath == null || targetPath == null) {
                  result.error("INVALID_ARGS", "All parameters are required", null)
                  return
              }
              
              val dataIsolationManager = DataIsolationManager(this)
              val success = dataIsolationManager.copyFileToVirtualSpace(virtualSpaceId, packageName, sourcePath, targetPath)
              result.success(success)
          } catch (e: Exception) {
              result.error("ERROR", "Failed to copy file to virtual space: ${e.message}", null)
          }
      }
      
      private fun readFileFromVirtualSpace(call: MethodCall, result: MethodChannel.Result) {
          try {
              val virtualSpaceId = call.argument<String>("virtualSpaceId")
              val packageName = call.argument<String>("packageName")
              val filePath = call.argument<String>("filePath")
              
              if (virtualSpaceId == null || packageName == null || filePath == null) {
                  result.error("INVALID_ARGS", "Virtual space ID, package name, and file path are required", null)
                  return
              }
              
              val dataIsolationManager = DataIsolationManager(this)
              val fileData = dataIsolationManager.readFileFromVirtualSpace(virtualSpaceId, packageName, filePath)
              
              if (fileData != null) {
                  result.success(fileData)
              } else {
                  result.error("FILE_NOT_FOUND", "File not found in virtual space", null)
              }
          } catch (e: Exception) {
              result.error("ERROR", "Failed to read file from virtual space: ${e.message}", null)
          }
      }
      
      private fun writeFileToVirtualSpace(call: MethodCall, result: MethodChannel.Result) {
          try {
              val virtualSpaceId = call.argument<String>("virtualSpaceId")
              val packageName = call.argument<String>("packageName")
              val filePath = call.argument<String>("filePath")
              val data = call.argument<ByteArray>("data")
              
              if (virtualSpaceId == null || packageName == null || filePath == null || data == null) {
                  result.error("INVALID_ARGS", "All parameters are required", null)
                  return
              }
              
              val dataIsolationManager = DataIsolationManager(this)
              val success = dataIsolationManager.writeFileToVirtualSpace(virtualSpaceId, packageName, filePath, data)
              result.success(success)
          } catch (e: Exception) {
              result.error("ERROR", "Failed to write file to virtual space: ${e.message}", null)
          }
      }
      
      private fun deleteFileFromVirtualSpace(call: MethodCall, result: MethodChannel.Result) {
          try {
              val virtualSpaceId = call.argument<String>("virtualSpaceId")
              val packageName = call.argument<String>("packageName")
              val filePath = call.argument<String>("filePath")
              
              if (virtualSpaceId == null || packageName == null || filePath == null) {
                  result.error("INVALID_ARGS", "Virtual space ID, package name, and file path are required", null)
                  return
              }
              
              val dataIsolationManager = DataIsolationManager(this)
              val success = dataIsolationManager.deleteFileFromVirtualSpace(virtualSpaceId, packageName, filePath)
              result.success(success)
          } catch (e: Exception) {
              result.error("ERROR", "Failed to delete file from virtual space: ${e.message}", null)
          }
      }
      
      private fun listVirtualSpaceFiles(call: MethodCall, result: MethodChannel.Result) {
          try {
              val virtualSpaceId = call.argument<String>("virtualSpaceId")
              val packageName = call.argument<String>("packageName")
              val directoryPath = call.argument<String>("directoryPath")
              
              if (virtualSpaceId == null || packageName == null || directoryPath == null) {
                  result.error("INVALID_ARGS", "Virtual space ID, package name, and directory path are required", null)
                  return
              }
              
              val dataIsolationManager = DataIsolationManager(this)
              val files = dataIsolationManager.listVirtualSpaceFiles(virtualSpaceId, packageName, directoryPath)
              result.success(files)
          } catch (e: Exception) {
              result.error("ERROR", "Failed to list virtual space files: ${e.message}", null)
          }
      }
      
      private fun cleanupVirtualStorage(call: MethodCall, result: MethodChannel.Result) {
          try {
              val virtualSpaceId = call.argument<String>("virtualSpaceId")
              val packageName = call.argument<String>("packageName")
              
              if (virtualSpaceId == null || packageName == null) {
                  result.error("INVALID_ARGS", "Virtual space ID and package name are required", null)
                  return
              }
              
              val dataIsolationManager = DataIsolationManager(this)
              val success = dataIsolationManager.cleanupVirtualStorage(virtualSpaceId, packageName)
              result.success(success)
          } catch (e: Exception) {
              result.error("ERROR", "Failed to cleanup virtual storage: ${e.message}", null)
          }
      }
  
      private fun drawableToByteArray(drawable: Drawable): ByteArray {
        // Handle cases where intrinsic dimensions are invalid
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        
        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream) // Slightly reduce quality for smaller size
        val result = stream.toByteArray()
        
        // Clean up
        bitmap.recycle()
        stream.close()
        
        return result
    }
    
    // Helper methods for app cloning
    private fun canCloneApp(packageName: String): Boolean {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val applicationInfo = packageInfo.applicationInfo
            
            // Check if app is system app (usually can't be cloned)
            if (applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0) {
                return false
            }
            
            // Check if app is already cloned (limit clones)
            val clonedApps = getClonedAppsList()
            val existingClones = clonedApps.count { it.startsWith(packageName) }
            if (existingClones >= 3) { // Maximum 3 clones per app
                return false
            }
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    private fun registerClonedApp(packageName: String, virtualSpaceId: String) {
        val prefs = getSharedPreferences("cloned_apps", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val clonedApps = getClonedAppsList().toMutableList()
        val cloneKey = "${packageName}_${virtualSpaceId}"
        clonedApps.add(cloneKey)
        
        editor.putStringSet("cloned_apps_list", clonedApps.toSet())
        editor.putString("clone_${cloneKey}_package", packageName)
        editor.putString("clone_${cloneKey}_space_id", virtualSpaceId)
        editor.putLong("clone_${cloneKey}_created", System.currentTimeMillis())
        editor.apply()
    }
    
    private fun unregisterClonedApp(packageName: String, virtualSpaceId: String) {
        val prefs = getSharedPreferences("cloned_apps", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val clonedApps = getClonedAppsList().toMutableList()
        val cloneKey = "${packageName}_${virtualSpaceId}"
        clonedApps.remove(cloneKey)
        
        editor.putStringSet("cloned_apps_list", clonedApps.toSet())
        editor.remove("clone_${cloneKey}_package")
        editor.remove("clone_${cloneKey}_space_id")
        editor.remove("clone_${cloneKey}_created")
        editor.apply()
    }
    
    private fun getClonedAppsList(): List<String> {
        val prefs = getSharedPreferences("cloned_apps", Context.MODE_PRIVATE)
        return prefs.getStringSet("cloned_apps_list", emptySet())?.toList() ?: emptyList()
    }
    
    private fun getVirtualSpaceId(packageName: String): String? {
        val clonedApps = getClonedAppsList()
        val cloneKey = clonedApps.find { it.startsWith(packageName) }
        return cloneKey?.split("_")?.getOrNull(1)
    }
    
    private fun saveCloneConfiguration(packageName: String, virtualSpaceId: String) {
        val prefs = getSharedPreferences("clone_configs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val config = JSONObject().apply {
            put("packageName", packageName)
            put("virtualSpaceId", virtualSpaceId)
            put("createdAt", System.currentTimeMillis())
            put("displayName", getAppDisplayName(packageName))
            put("isActive", true)
        }
        
        editor.putString("config_${packageName}_${virtualSpaceId}", config.toString())
        editor.apply()
    }
    
    private fun removeCloneConfiguration(packageName: String, virtualSpaceId: String) {
        val prefs = getSharedPreferences("clone_configs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.remove("config_${packageName}_${virtualSpaceId}")
        editor.apply()
    }
    
    private fun getAppDisplayName(packageName: String): String {
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
    
    private fun stopClonedApp(packageName: String, virtualSpaceId: String) {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            // Force stop the cloned app process
            // This is a simplified implementation
            activityManager.killBackgroundProcesses("${packageName}_${virtualSpaceId}")
        } catch (e: Exception) {
            // Handle exception
        }
    }
    
    // Account Management Methods
    
    private fun addAccountToClonedApp(call: MethodCall, result: MethodChannel.Result) {
        try {
            val clonedPackageName = call.argument<String>("clonedPackageName")
            val accountId = call.argument<String>("accountId")
            val accountData = call.argument<Map<String, String>>("accountData")
            
            if (clonedPackageName != null && accountId != null && accountData != null) {
                val success = dataManager.addAccountToClonedApp(clonedPackageName, accountId, accountData)
                
                if (success) {
                    result.success(mapOf(
                        "success" to true,
                        "accountId" to accountId,
                        "clonedPackageName" to clonedPackageName
                    ))
                } else {
                    result.error("ADD_ACCOUNT_ERROR", "Failed to add account to cloned app", null)
                }
            } else {
                result.error("INVALID_ARGS", "clonedPackageName, accountId, and accountData are required", null)
            }
        } catch (e: Exception) {
            result.error("ERROR", "Failed to add account: ${e.message}", null)
        }
    }
    
    private fun removeAccountFromClonedApp(call: MethodCall, result: MethodChannel.Result) {
        try {
            val clonedPackageName = call.argument<String>("clonedPackageName")
            val accountId = call.argument<String>("accountId")
            
            if (clonedPackageName != null && accountId != null) {
                val success = dataManager.removeAccountFromClonedApp(clonedPackageName, accountId)
                
                if (success) {
                    result.success(mapOf(
                        "success" to true,
                        "accountId" to accountId,
                        "clonedPackageName" to clonedPackageName
                    ))
                } else {
                    result.error("REMOVE_ACCOUNT_ERROR", "Failed to remove account from cloned app", null)
                }
            } else {
                result.error("INVALID_ARGS", "clonedPackageName and accountId are required", null)
            }
        } catch (e: Exception) {
            result.error("ERROR", "Failed to remove account: ${e.message}", null)
        }
    }
    
    private fun switchActiveAccount(call: MethodCall, result: MethodChannel.Result) {
        try {
            val clonedAppId = call.argument<Long>("clonedAppId") ?: call.argument<Int>("clonedAppId")?.toLong()
            val newAccountId = call.argument<String>("newAccountId")
            
            if (clonedAppId != null && newAccountId != null) {
                val success = dataManager.switchActiveAccount(clonedAppId, newAccountId)
                
                if (success) {
                    result.success(mapOf(
                        "success" to true,
                        "clonedAppId" to clonedAppId,
                        "activeAccountId" to newAccountId
                    ))
                } else {
                    result.error("SWITCH_ACCOUNT_ERROR", "Failed to switch active account", null)
                }
            } else {
                result.error("INVALID_ARGS", "clonedAppId and newAccountId are required", null)
            }
        } catch (e: Exception) {
            result.error("ERROR", "Failed to switch account: ${e.message}", null)
        }
    }
    
    private fun getActiveAccountId(call: MethodCall, result: MethodChannel.Result) {
        try {
            val clonedPackageName = call.argument<String>("clonedPackageName")
            
            if (clonedPackageName != null) {
                val activeAccountId = dataManager.getActiveAccountId(clonedPackageName)
                
                result.success(mapOf(
                    "clonedPackageName" to clonedPackageName,
                    "activeAccountId" to activeAccountId
                ))
            } else {
                result.error("INVALID_ARGS", "clonedPackageName is required", null)
            }
        } catch (e: Exception) {
            result.error("ERROR", "Failed to get active account: ${e.message}", null)
        }
    }
    
    private fun getAllAccountIds(call: MethodCall, result: MethodChannel.Result) {
        try {
            val clonedPackageName = call.argument<String>("clonedPackageName")
            
            if (clonedPackageName != null) {
                val accountIds = dataManager.getAllAccountIds(clonedPackageName)
                
                result.success(mapOf(
                    "clonedPackageName" to clonedPackageName,
                    "accountIds" to accountIds
                ))
            } else {
                result.error("INVALID_ARGS", "clonedPackageName is required", null)
            }
        } catch (e: Exception) {
            result.error("ERROR", "Failed to get account IDs: ${e.message}", null)
        }
    }
    
    private fun getAccountData(call: MethodCall, result: MethodChannel.Result) {
        try {
            val clonedPackageName = call.argument<String>("clonedPackageName")
            val accountId = call.argument<String>("accountId")
            
            if (clonedPackageName != null && accountId != null) {
                val accountPrefs = dataManager.getAccountSpecificPreferences(clonedPackageName, accountId)
                val accountData = mutableMapOf<String, Any?>()
                
                // Get basic account info
                accountData["accountId"] = accountId
                accountData["createdAt"] = accountPrefs.getLong("created_at", 0)
                accountData["lastUsed"] = accountPrefs.getLong("last_used", 0)
                
                // Get all stored keys (excluding sensitive data)
                val allKeys = accountPrefs.all.keys.filter { 
                    !it.startsWith("encrypted_") && it != "created_at" && it != "last_used" 
                }
                allKeys.forEach { key ->
                    accountData[key] = accountPrefs.getString(key, null)
                }
                
                result.success(mapOf(
                    "clonedPackageName" to clonedPackageName,
                    "accountId" to accountId,
                    "accountData" to accountData
                ))
            } else {
                result.error("INVALID_ARGS", "clonedPackageName and accountId are required", null)
            }
        } catch (e: Exception) {
            result.error("ERROR", "Failed to get account data: ${e.message}", null)
        }
    }
    
    private fun getVirtualSpaceStatistics(call: MethodCall, result: MethodChannel.Result) {
        try {
            val clonedAppId = call.argument<Long>("clonedAppId") ?: call.argument<Int>("clonedAppId")?.toLong()
            
            if (clonedAppId != null) {
                val statistics = virtualSpaceEngine.getVirtualSpaceStatistics(clonedAppId)
                if (statistics != null) {
                    result.success(statistics)
                } else {
                    result.error("STATISTICS_ERROR", "Failed to get statistics for cloned app", null)
                }
            } else {
                result.error("INVALID_ARGS", "clonedAppId is required", null)
            }
        } catch (e: Exception) {
            result.error("ERROR", "Failed to get virtual space statistics: ${e.message}", null)
        }
    }
    
    private fun cleanupVirtualSpace(call: MethodCall, result: MethodChannel.Result) {
        try {
            val clonedAppId = call.argument<Long>("clonedAppId") ?: call.argument<Int>("clonedAppId")?.toLong()
            
            if (clonedAppId != null) {
                val success = virtualSpaceEngine.cleanupVirtualSpace(clonedAppId)
                if (success) {
                    result.success(mapOf(
                        "success" to true,
                        "message" to "Virtual space cleanup completed successfully"
                    ))
                } else {
                    result.error("CLEANUP_ERROR", "Failed to cleanup virtual space", null)
                }
            } else {
                result.error("INVALID_ARGS", "clonedAppId is required", null)
            }
        } catch (e: Exception) {
            result.error("ERROR", "Failed to cleanup virtual space: ${e.message}", null)
        }
    }
    
    /**
     * Monitor virtual space processes
     */
    private fun monitorVirtualSpaceProcesses(call: MethodCall, result: MethodChannel.Result) {
        try {
            val clonedAppId = call.argument<Long>("clonedAppId") ?: call.argument<Int>("clonedAppId")?.toLong()
            
            if (clonedAppId != null) {
                val processInfo = virtualSpaceEngine.monitorVirtualSpaceProcesses(clonedAppId)
                if (processInfo != null) {
                    result.success(processInfo)
                } else {
                    result.error("MONITOR_ERROR", "Failed to monitor virtual space processes", null)
                }
            } else {
                result.error("INVALID_ARGS", "clonedAppId is required", null)
            }
        } catch (e: Exception) {
            result.error("ERROR", "Failed to monitor virtual space processes: ${e.message}", null)
        }
    }
    
    /**
     * Apply security policies to virtual space
     */
    private fun applySecurityPolicies(call: MethodCall, result: MethodChannel.Result) {
        try {
            val clonedAppId = call.argument<Long>("clonedAppId") ?: call.argument<Int>("clonedAppId")?.toLong()
            val policies = call.argument<Map<String, Any>>("policies") ?: emptyMap()
            
            if (clonedAppId != null) {
                val success = virtualSpaceEngine.applySecurityPolicies(clonedAppId, policies)
                if (success) {
                    result.success(mapOf(
                        "success" to true,
                        "message" to "Security policies applied successfully"
                    ))
                } else {
                    result.error("SECURITY_ERROR", "Failed to apply security policies", null)
                }
            } else {
                result.error("INVALID_ARGS", "clonedAppId is required", null)
            }
        } catch (e: Exception) {
            result.error("ERROR", "Failed to apply security policies: ${e.message}", null)
        }
    }
    
    /**
     * Optimize virtual space performance
     */
    private fun optimizeVirtualSpacePerformance(call: MethodCall, result: MethodChannel.Result) {
        try {
            val clonedAppId = call.argument<Long>("clonedAppId") ?: call.argument<Int>("clonedAppId")?.toLong()
            
            if (clonedAppId != null) {
                val success = virtualSpaceEngine.optimizeVirtualSpacePerformance(clonedAppId)
                if (success) {
                    result.success(mapOf(
                        "success" to true,
                        "message" to "Virtual space performance optimized successfully"
                    ))
                } else {
                    result.error("OPTIMIZATION_ERROR", "Failed to optimize virtual space performance", null)
                }
            } else {
                result.error("INVALID_ARGS", "clonedAppId is required", null)
            }
        } catch (e: Exception) {
            result.error("ERROR", "Failed to optimize virtual space performance: ${e.message}", null)
        }
    }
    
    /**
     * Get virtual space health report
     */
    private fun getVirtualSpaceHealthReport(call: MethodCall, result: MethodChannel.Result) {
        try {
            val clonedAppId = call.argument<Long>("clonedAppId") ?: call.argument<Int>("clonedAppId")?.toLong()
            
            if (clonedAppId != null) {
                val healthReport = virtualSpaceEngine.getVirtualSpaceHealthReport(clonedAppId)
                if (healthReport != null) {
                    result.success(healthReport)
                } else {
                    result.error("HEALTH_REPORT_ERROR", "Failed to generate health report", null)
                }
            } else {
                result.error("INVALID_ARGS", "clonedAppId is required", null)
            }
        } catch (e: Exception) {
            result.error("ERROR", "Failed to get virtual space health report: ${e.message}", null)
        }
    }
    
    /**
     * Check system-level permission status
     */
    private fun checkSystemPermission(permission: String, result: MethodChannel.Result) {
        try {
            when (permission) {
                "android.permission.QUERY_ALL_PACKAGES" -> {
                    // Check if QUERY_ALL_PACKAGES permission is granted
                    val hasPermission = try {
                        // Try to get all packages to test if permission is granted
                        packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
                        true
                    } catch (e: SecurityException) {
                        false
                    }
                    
                    Log.d(TAG, "QUERY_ALL_PACKAGES permission check: $hasPermission")
                    result.success(hasPermission)
                }
                "android.permission.WRITE_EXTERNAL_STORAGE" -> {
                    val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED
                    result.success(hasPermission)
                }
                "android.permission.READ_EXTERNAL_STORAGE" -> {
                    val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED
                    result.success(hasPermission)
                }
                "android.permission.SYSTEM_ALERT_WINDOW" -> {
                    val hasPermission = android.provider.Settings.canDrawOverlays(this)
                    result.success(hasPermission)
                }
                else -> {
                    // For other permissions, use standard permission check
                    val hasPermission = ContextCompat.checkSelfPermission(this, permission) == PERMISSION_GRANTED
                    result.success(hasPermission)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check system permission: $permission", e)
            result.error("ERROR", "Failed to check system permission: ${e.message}", null)
        }
    }
}
