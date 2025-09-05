# 🚀 সম্পূর্ণ App Cloning System Implementation Guide

## 📋 সূচিপত্র
1. [প্রজেক্ট ওভারভিউ](#প্রজেক্ট-ওভারভিউ)
2. [VirtualCore Framework Setup](#virtualcore-framework-setup)
3. [Wi-Fi Network Isolation](#wi-fi-network-isolation)
4. [Authentication System](#authentication-system)
5. [Crash Prevention Strategy](#crash-prevention-strategy)
6. [Code Implementation](#code-implementation)
7. [Build & Deployment](#build--deployment)
8. [Testing & Debugging](#testing--debugging)

---

## 🎯 প্রজেক্ট ওভারভিউ

### মূল ফিচারসমূহ
- **App Cloning System**: VirtualCore Framework দিয়ে virtual environment তৈরি
- **Wi-Fi Network Isolation**: প্রতিটি cloned app এর জন্য আলাদা network configuration
- **Google Authentication**: Secure login system with virtual environment integration
- **Crash Prevention**: Comprehensive error handling এবং stability management

### Architecture Pattern
```
MVVM + Clean Architecture + VirtualCore Framework
├── UI Layer (Activities, Fragments, ViewModels)
├── Domain Layer (UseCases, Business Logic)
├── Data Layer (Repository, API, Database)
└── VirtualCore Layer (Virtual Environment Management)
```

---

## ⚙️ VirtualCore Framework Setup

### 1. Dependencies Configuration

**build.gradle (Module: app)**
```kotlin
dependencies {
    // VirtualCore Framework
    implementation 'com.lody.virtual:core:1.0.0'
    
    // Essential Libraries
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
    implementation 'androidx.activity:activity-compose:1.8.2'
    
    // Network & Security
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.google.android.gms:play-services-auth:20.7.0'
    
    // Crash Prevention
    implementation 'com.google.firebase:firebase-crashlytics:18.6.1'
}
```

### 2. VirtualCore Initialization

**VirtualCoreManager.kt**
```kotlin
class VirtualCoreManager private constructor() {
    companion object {
        private const val TAG = "VirtualCoreManager"
        
        @Volatile
        private var INSTANCE: VirtualCoreManager? = null
        
        fun getInstance(): VirtualCoreManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VirtualCoreManager().also { INSTANCE = it }
            }
        }
    }
    
    fun initializeVirtualCore(context: Context): Boolean {
        return try {
            // VirtualCore initialization with error handling
            VirtualCore.get().startup(context)
            Log.d(TAG, "VirtualCore initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "VirtualCore initialization failed", e)
            CrashHandler.handleNonFatalError(e)
            false
        }
    }
    
    fun installVirtualApp(context: Context, apkPath: String): InstallResult {
        return try {
            val installResult = VirtualCore.get().installPackage(apkPath, InstallStrategy.COMPARE_VERSION)
            
            when {
                installResult.isSuccess -> {
                    Log.d(TAG, "Virtual app installed: ${installResult.packageName}")
                    InstallResult.Success(installResult.packageName)
                }
                else -> {
                    Log.e(TAG, "Installation failed: ${installResult.error}")
                    InstallResult.Failed(installResult.error ?: "Unknown error")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during installation", e)
            InstallResult.Failed(e.message ?: "Installation exception")
        }
    }
    
    fun launchVirtualApp(context: Context, packageName: String, userId: Int = 0): Boolean {
        return try {
            val intent = VirtualCore.get().getLaunchIntent(packageName, userId)
            if (intent != null) {
                VirtualCore.get().launchApp(packageName, userId)
                true
            } else {
                Log.e(TAG, "Launch intent not found for $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch virtual app", e)
            CrashHandler.handleNonFatalError(e)
            false
        }
    }
}

sealed class InstallResult {
    data class Success(val packageName: String) : InstallResult()
    data class Failed(val error: String) : InstallResult()
}
```

---

## 🌐 Wi-Fi Network Isolation

### 1. Network Management Stub

**NetworkIsolationManager.kt**
```kotlin
class NetworkIsolationManager {
    companion object {
        private const val TAG = "NetworkIsolation"
    }
    
    fun setupNetworkIsolation(context: Context, virtualUserId: Int) {
        try {
            // Create isolated network configuration for virtual app
            val networkConfig = createIsolatedNetworkConfig(virtualUserId)
            
            // Apply network policies
            applyNetworkPolicies(context, virtualUserId, networkConfig)
            
            Log.d(TAG, "Network isolation setup completed for user: $virtualUserId")
        } catch (e: Exception) {
            Log.e(TAG, "Network isolation setup failed", e)
            CrashHandler.handleNonFatalError(e)
        }
    }
    
    private fun createIsolatedNetworkConfig(userId: Int): NetworkConfig {
        return NetworkConfig(
            userId = userId,
            allowedNetworks = listOf("wifi", "mobile"),
            blockedDomains = emptyList(),
            vpnEnabled = false,
            proxySettings = null
        )
    }
    
    private fun applyNetworkPolicies(context: Context, userId: Int, config: NetworkConfig) {
        // Apply UID-based network policies
        val virtualUid = VirtualCore.get().getUidForUserId(userId)
        
        // Set network access permissions
        setNetworkAccessPolicy(virtualUid, config.allowedNetworks)
        
        // Configure Wi-Fi isolation
        configureWifiIsolation(context, userId)
    }
    
    private fun setNetworkAccessPolicy(uid: Int, allowedNetworks: List<String>) {
        try {
            // Implementation for network access control
            allowedNetworks.forEach { networkType ->
                when (networkType) {
                    "wifi" -> enableWifiAccess(uid)
                    "mobile" -> enableMobileAccess(uid)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set network policy", e)
        }
    }
    
    private fun configureWifiIsolation(context: Context, userId: Int) {
        // Create virtual Wi-Fi configuration
        val virtualWifiConfig = VirtualWifiConfig(
            ssid = "Virtual_${userId}",
            security = WifiSecurity.WPA2,
            isolated = true
        )
        
        // Apply virtual Wi-Fi settings
        VirtualCore.get().setWifiConfiguration(userId, virtualWifiConfig)
    }
    
    private fun enableWifiAccess(uid: Int) {
        // Enable Wi-Fi access for specific UID
    }
    
    private fun enableMobileAccess(uid: Int) {
        // Enable mobile data access for specific UID
    }
}

data class NetworkConfig(
    val userId: Int,
    val allowedNetworks: List<String>,
    val blockedDomains: List<String>,
    val vpnEnabled: Boolean,
    val proxySettings: ProxySettings?
)

data class VirtualWifiConfig(
    val ssid: String,
    val security: WifiSecurity,
    val isolated: Boolean
)

enum class WifiSecurity {
    OPEN, WEP, WPA, WPA2, WPA3
}
```

### 2. Wi-Fi Manager Stub Implementation

**VirtualWifiManager.kt**
```kotlin
class VirtualWifiManager(private val context: Context) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    fun getVirtualWifiInfo(userId: Int): VirtualWifiInfo? {
        return try {
            val realWifiInfo = wifiManager.connectionInfo
            
            // Create virtualized Wi-Fi information
            VirtualWifiInfo(
                ssid = "Virtual_${userId}_${realWifiInfo.ssid}",
                bssid = generateVirtualBssid(userId),
                networkId = userId,
                rssi = realWifiInfo.rssi,
                linkSpeed = realWifiInfo.linkSpeed,
                frequency = realWifiInfo.frequency,
                ipAddress = generateVirtualIpAddress(userId)
            )
        } catch (e: Exception) {
            Log.e("VirtualWifiManager", "Failed to get virtual wifi info", e)
            null
        }
    }
    
    fun getVirtualScanResults(userId: Int): List<VirtualScanResult> {
        return try {
            if (shouldHideScanResults(userId)) {
                emptyList()
            } else {
                val realResults = wifiManager.scanResults
                realResults.map { result ->
                    VirtualScanResult(
                        ssid = "Virtual_${result.SSID}",
                        bssid = generateVirtualBssid(userId),
                        level = result.level,
                        frequency = result.frequency,
                        capabilities = result.capabilities
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("VirtualWifiManager", "Failed to get scan results", e)
            emptyList()
        }
    }
    
    private fun generateVirtualBssid(userId: Int): String {
        return "02:00:00:00:${String.format("%02x", userId)}:00"
    }
    
    private fun generateVirtualIpAddress(userId: Int): Int {
        // Generate virtual IP address based on user ID
        return (192 shl 24) or (168 shl 16) or (userId shl 8) or 1
    }
    
    private fun shouldHideScanResults(userId: Int): Boolean {
        // Logic to determine if scan results should be hidden
        return VirtualCore.get().isAppRunning("com.sensitive.app", userId)
    }
}

data class VirtualWifiInfo(
    val ssid: String,
    val bssid: String,
    val networkId: Int,
    val rssi: Int,
    val linkSpeed: Int,
    val frequency: Int,
    val ipAddress: Int
)

data class VirtualScanResult(
    val ssid: String,
    val bssid: String,
    val level: Int,
    val frequency: Int,
    val capabilities: String
)
```

---

## 🔐 Authentication System

### 1. Google Authentication Integration

**GoogleAuthManager.kt**
```kotlin
class GoogleAuthManager(private val context: Context) {
    private val googleSignInClient: GoogleSignInClient
    
    companion object {
        private const val TAG = "GoogleAuthManager"
        private const val RC_SIGN_IN = 9001
    }
    
    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }
    
    suspend fun signInWithGoogle(): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val signInIntent = googleSignInClient.signInIntent
                
                // This should be called from Activity
                AuthResult.IntentRequired(signInIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Google sign-in failed", e)
                AuthResult.Failed(e.message ?: "Sign-in failed")
            }
        }
    }
    
    suspend fun handleSignInResult(data: Intent?): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                
                if (account != null) {
                    val userProfile = UserProfile(
                        id = account.id ?: "",
                        email = account.email ?: "",
                        displayName = account.displayName ?: "",
                        photoUrl = account.photoUrl?.toString(),
                        idToken = account.idToken
                    )
                    
                    // Store user session
                    SessionManager.getInstance().saveUserSession(userProfile)
                    
                    AuthResult.Success(userProfile)
                } else {
                    AuthResult.Failed("Account is null")
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Sign-in failed with code: ${e.statusCode}", e)
                AuthResult.Failed("Sign-in failed: ${e.statusCode}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during sign-in", e)
                AuthResult.Failed(e.message ?: "Unexpected error")
            }
        }
    }
    
    suspend fun signOut(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                googleSignInClient.signOut().await()
                SessionManager.getInstance().clearUserSession()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Sign-out failed", e)
                false
            }
        }
    }
    
    fun getCurrentUser(): UserProfile? {
        return SessionManager.getInstance().getCurrentUser()
    }
}

sealed class AuthResult {
    data class Success(val userProfile: UserProfile) : AuthResult()
    data class Failed(val error: String) : AuthResult()
    data class IntentRequired(val intent: Intent) : AuthResult()
}

data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?,
    val idToken: String?
)
```

### 2. Session Management

**SessionManager.kt**
```kotlin
class SessionManager private constructor(private val context: Context) {
    private val sharedPreferences = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val TAG = "SessionManager"
        private const val KEY_USER_PROFILE = "user_profile"
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_VIRTUAL_USER_ID = "virtual_user_id"
        
        @Volatile
        private var INSTANCE: SessionManager? = null
        
        fun getInstance(context: Context? = null): SessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionManager(context!!).also { INSTANCE = it }
            }
        }
    }
    
    fun saveUserSession(userProfile: UserProfile) {
        try {
            val editor = sharedPreferences.edit()
            editor.putString(KEY_USER_PROFILE, gson.toJson(userProfile))
            editor.putString(KEY_SESSION_TOKEN, generateSessionToken())
            editor.putLong("session_timestamp", System.currentTimeMillis())
            editor.apply()
            
            Log.d(TAG, "User session saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user session", e)
            CrashHandler.handleNonFatalError(e)
        }
    }
    
    fun getCurrentUser(): UserProfile? {
        return try {
            val userJson = sharedPreferences.getString(KEY_USER_PROFILE, null)
            if (userJson != null) {
                gson.fromJson(userJson, UserProfile::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current user", e)
            null
        }
    }
    
    fun isSessionValid(): Boolean {
        val sessionTimestamp = sharedPreferences.getLong("session_timestamp", 0)
        val currentTime = System.currentTimeMillis()
        val sessionDuration = currentTime - sessionTimestamp
        
        // Session valid for 7 days
        return sessionDuration < (7 * 24 * 60 * 60 * 1000)
    }
    
    fun clearUserSession() {
        try {
            sharedPreferences.edit().clear().apply()
            Log.d(TAG, "User session cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear user session", e)
        }
    }
    
    fun getVirtualUserId(): Int {
        return sharedPreferences.getInt(KEY_VIRTUAL_USER_ID, 0)
    }
    
    fun setVirtualUserId(userId: Int) {
        sharedPreferences.edit().putInt(KEY_VIRTUAL_USER_ID, userId).apply()
    }
    
    private fun generateSessionToken(): String {
        return UUID.randomUUID().toString()
    }
}
```

---

## 🛡️ Crash Prevention Strategy

### 1. Global Crash Handler

**CrashHandler.kt**
```kotlin
class CrashHandler private constructor() : Thread.UncaughtExceptionHandler {
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    
    companion object {
        private const val TAG = "CrashHandler"
        private const val CRASH_LOG_FILE = "crash_logs.txt"
        
        @Volatile
        private var INSTANCE: CrashHandler? = null
        
        fun getInstance(): CrashHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CrashHandler().also { INSTANCE = it }
            }
        }
        
        fun initialize(context: Context) {
            val crashHandler = getInstance()
            crashHandler.defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(crashHandler)
            
            // Initialize Firebase Crashlytics
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        }
        
        fun handleNonFatalError(throwable: Throwable) {
            try {
                Log.e(TAG, "Non-fatal error occurred", throwable)
                FirebaseCrashlytics.getInstance().recordException(throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record non-fatal error", e)
            }
        }
    }
    
    override fun uncaughtException(thread: Thread, exception: Throwable) {
        try {
            // Log crash details
            logCrashDetails(thread, exception)
            
            // Save crash to local file
            saveCrashToFile(exception)
            
            // Send to Firebase Crashlytics
            FirebaseCrashlytics.getInstance().recordException(exception)
            
            // Attempt graceful recovery
            attemptGracefulRecovery(exception)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in crash handler", e)
        } finally {
            // Call default handler
            defaultHandler?.uncaughtException(thread, exception)
        }
    }
    
    private fun logCrashDetails(thread: Thread, exception: Throwable) {
        Log.e(TAG, "=== CRASH DETECTED ===")
        Log.e(TAG, "Thread: ${thread.name}")
        Log.e(TAG, "Exception: ${exception.javaClass.simpleName}")
        Log.e(TAG, "Message: ${exception.message}")
        Log.e(TAG, "Stack trace:", exception)
        Log.e(TAG, "=== END CRASH DETAILS ===")
    }
    
    private fun saveCrashToFile(exception: Throwable) {
        try {
            val crashInfo = buildString {
                appendLine("Crash Time: ${Date()}")
                appendLine("Exception: ${exception.javaClass.simpleName}")
                appendLine("Message: ${exception.message}")
                appendLine("Stack Trace:")
                exception.stackTrace.forEach { element ->
                    appendLine("  at $element")
                }
                appendLine("\n" + "=".repeat(50) + "\n")
            }
            
            // Save to internal storage
            val file = File(getInternalStorageDir(), CRASH_LOG_FILE)
            file.appendText(crashInfo)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash to file", e)
        }
    }
    
    private fun attemptGracefulRecovery(exception: Throwable) {
        when {
            isVirtualCoreException(exception) -> {
                Log.w(TAG, "VirtualCore exception detected, attempting recovery")
                recoverVirtualCore()
            }
            isDatabaseException(exception) -> {
                Log.w(TAG, "Database exception detected, attempting recovery")
                recoverDatabase()
            }
            isNetworkException(exception) -> {
                Log.w(TAG, "Network exception detected, clearing cache")
                clearNetworkCache()
            }
        }
    }
    
    private fun isVirtualCoreException(exception: Throwable): Boolean {
        return exception.stackTrace.any { 
            it.className.contains("virtual", ignoreCase = true) ||
            it.className.contains("lody", ignoreCase = true)
        }
    }
    
    private fun isDatabaseException(exception: Throwable): Boolean {
        return exception is SQLException ||
               exception.message?.contains("database", ignoreCase = true) == true
    }
    
    private fun isNetworkException(exception: Throwable): Boolean {
        return exception is IOException ||
               exception.message?.contains("network", ignoreCase = true) == true
    }
    
    private fun recoverVirtualCore() {
        try {
            // Restart VirtualCore
            VirtualCoreManager.getInstance().initializeVirtualCore(getApplicationContext())
        } catch (e: Exception) {
            Log.e(TAG, "VirtualCore recovery failed", e)
        }
    }
    
    private fun recoverDatabase() {
        try {
            // Clear corrupted database files
            DatabaseRecoveryManager.getInstance().recoverDatabase()
        } catch (e: Exception) {
            Log.e(TAG, "Database recovery failed", e)
        }
    }
    
    private fun clearNetworkCache() {
        try {
            // Clear network cache
            NetworkCacheManager.getInstance().clearCache()
        } catch (e: Exception) {
            Log.e(TAG, "Network cache clear failed", e)
        }
    }
    
    private fun getInternalStorageDir(): File {
        // Return internal storage directory
        return File("/data/data/com.yourapp.package/files")
    }
    
    private fun getApplicationContext(): Context {
        // Return application context
        return MyApplication.getInstance()
    }
}
```

### 2. Database Recovery Manager

**DatabaseRecoveryManager.kt**
```kotlin
class DatabaseRecoveryManager private constructor() {
    companion object {
        private const val TAG = "DatabaseRecovery"
        
        @Volatile
        private var INSTANCE: DatabaseRecoveryManager? = null
        
        fun getInstance(): DatabaseRecoveryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseRecoveryManager().also { INSTANCE = it }
            }
        }
    }
    
    fun recoverDatabase() {
        try {
            Log.d(TAG, "Starting database recovery")
            
            // 1. Backup current database
            backupCorruptedDatabase()
            
            // 2. Delete corrupted database files
            deleteCorruptedFiles()
            
            // 3. Recreate database with default schema
            recreateDatabase()
            
            // 4. Restore essential data from backup
            restoreEssentialData()
            
            Log.d(TAG, "Database recovery completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Database recovery failed", e)
            CrashHandler.handleNonFatalError(e)
        }
    }
    
    private fun backupCorruptedDatabase() {
        try {
            val dbPath = getDatabasePath()
            val backupPath = "${dbPath}_corrupted_${System.currentTimeMillis()}"
            
            val dbFile = File(dbPath)
            val backupFile = File(backupPath)
            
            if (dbFile.exists()) {
                dbFile.copyTo(backupFile, overwrite = true)
                Log.d(TAG, "Corrupted database backed up to: $backupPath")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to backup corrupted database", e)
        }
    }
    
    private fun deleteCorruptedFiles() {
        try {
            val dbPath = getDatabasePath()
            val dbFile = File(dbPath)
            val walFile = File("$dbPath-wal")
            val shmFile = File("$dbPath-shm")
            
            listOf(dbFile, walFile, shmFile).forEach { file ->
                if (file.exists() && file.delete()) {
                    Log.d(TAG, "Deleted corrupted file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete corrupted files", e)
        }
    }
    
    private fun recreateDatabase() {
        try {
            // Recreate database with Room or SQLite
            val database = AppDatabase.getInstance(getApplicationContext())
            
            // Force database creation
            database.openHelper.writableDatabase
            
            Log.d(TAG, "Database recreated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recreate database", e)
            throw e
        }
    }
    
    private fun restoreEssentialData() {
        try {
            // Restore user session if available
            val sessionManager = SessionManager.getInstance()
            if (sessionManager.isSessionValid()) {
                Log.d(TAG, "User session is still valid")
            }
            
            // Restore virtual app configurations
            restoreVirtualAppConfigs()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore essential data", e)
        }
    }
    
    private fun restoreVirtualAppConfigs() {
        try {
            // Restore virtual app configurations from SharedPreferences
            val prefs = getApplicationContext().getSharedPreferences("virtual_apps", Context.MODE_PRIVATE)
            val installedApps = prefs.getStringSet("installed_apps", emptySet()) ?: emptySet()
            
            Log.d(TAG, "Found ${installedApps.size} virtual apps to restore")
            
            // Re-register virtual apps with VirtualCore
            installedApps.forEach { packageName ->
                try {
                    VirtualCore.get().registerApp(packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore virtual app: $packageName", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore virtual app configs", e)
        }
    }
    
    private fun getDatabasePath(): String {
        return "/data/data/com.yourapp.package/databases/app_database"
    }
    
    private fun getApplicationContext(): Context {
        return MyApplication.getInstance()
    }
}
```

---

## 🏗️ Build & Deployment

### 1. Gradle Configuration

**build.gradle (Project level)**
```kotlin
buildscript {
    ext.kotlin_version = '1.9.10'
    ext.gradle_version = '8.2.2'
    
    dependencies {
        classpath "com.android.tools.build:gradle:$gradle_version"
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
        classpath 'com.google.gms:google-services:4.4.0'
        classpath 'com.google.firebase:firebase-crashlytics-gradle:2.9.9'
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

**build.gradle (App level)**
```kotlin
apply plugin: 'com.android.application'
apply plugin: 'kotlin-android'
apply plugin: 'kotlin-kapt'
apply plugin: 'com.google.gms.google-services'
apply plugin: 'com.google.firebase.crashlytics'

android {
    compileSdk 34
    
    defaultConfig {
        applicationId "com.munna.appcloner"
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName "1.0.0"
        
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        
        // VirtualCore configuration
        ndk {
            abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'
        }
    }
    
    buildTypes {
        debug {
            debuggable true
            minifyEnabled false
            shrinkResources false
            
            buildConfigField "boolean", "DEBUG_MODE", "true"
            buildConfigField "String", "API_BASE_URL", '"https://api-dev.example.com"'
        }
        
        release {
            debuggable false
            minifyEnabled true
            shrinkResources true
            
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            
            buildConfigField "boolean", "DEBUG_MODE", "false"
            buildConfigField "String", "API_BASE_URL", '"https://api.example.com"'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = '1.8'
    }
    
    buildFeatures {
        viewBinding true
        buildConfig true
    }
    
    packagingOptions {
        pickFirst '**/libc++_shared.so'
        pickFirst '**/libjsc.so'
    }
}

dependencies {
    // Core Android
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    
    // Lifecycle & ViewModel
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
    implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.7.0'
    implementation 'androidx.activity:activity-ktx:1.8.2'
    implementation 'androidx.fragment:fragment-ktx:1.6.2'
    
    // VirtualCore Framework
    implementation 'com.lody.virtual:core:1.0.0'
    
    // Google Services
    implementation 'com.google.android.gms:play-services-auth:20.7.0'
    implementation 'com.google.firebase:firebase-crashlytics:18.6.1'
    implementation 'com.google.firebase:firebase-analytics:21.5.0'
    
    // Network
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    
    // Database
    implementation 'androidx.room:room-runtime:2.6.1'
    implementation 'androidx.room:room-ktx:2.6.1'
    kapt 'androidx.room:room-compiler:2.6.1'
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    
    // JSON
    implementation 'com.google.code.gson:gson:2.10.1'
    
    // Testing
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.mockito:mockito-core:5.7.0'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
```

### 2. ProGuard Rules

**proguard-rules.pro**
```proguard
# Keep VirtualCore classes
-keep class com.lody.virtual.** { *; }
-keep class mirror.** { *; }
-dontwarn com.lody.virtual.**

# Keep Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Keep Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data classes
-keep class com.munna.appcloner.data.** { *; }
-keep class com.munna.appcloner.domain.** { *; }

# Keep Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# General Android
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
```

### 3. Deployment Script

**deploy.sh**
```bash
#!/bin/bash

echo "🚀 Starting App Cloner deployment process..."

# Clean previous builds
echo "🧹 Cleaning previous builds..."
./gradlew clean

# Run tests
echo "🧪 Running tests..."
./gradlew test
if [ $? -ne 0 ]; then
    echo "❌ Tests failed. Deployment aborted."
    exit 1
fi

# Build release APK
echo "🔨 Building release APK..."
./gradlew assembleRelease
if [ $? -ne 0 ]; then
    echo "❌ Build failed. Deployment aborted."
    exit 1
fi

# Sign APK (if keystore is configured)
echo "✍️ Signing APK..."
APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
SIGNED_APK_PATH="app/build/outputs/apk/release/app-release-signed.apk"

if [ -f "keystore.jks" ]; then
    jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore keystore.jks $APK_PATH alias_name
    zipalign -v 4 $APK_PATH $SIGNED_APK_PATH
    echo "✅ APK signed successfully"
else
    echo "⚠️ Keystore not found. Using unsigned APK."
    SIGNED_APK_PATH=$APK_PATH
fi

# Verify APK
echo "🔍 Verifying APK..."
aapt dump badging $SIGNED_APK_PATH

echo "✅ Deployment completed successfully!"
echo "📱 APK location: $SIGNED_APK_PATH"
```

---

## 🧪 Testing & Debugging

### 1. Unit Testing

**VirtualCoreManagerTest.kt**
```kotlin
@RunWith(MockitoJUnitRunner::class)
class VirtualCoreManagerTest {
    
    @Mock
    private lateinit var context: Context
    
    @Mock
    private lateinit var virtualCore: VirtualCore
    
    private lateinit var virtualCoreManager: VirtualCoreManager
    
    @Before
    fun setup() {
        virtualCoreManager = VirtualCoreManager.getInstance()
        MockitoAnnotations.openMocks(this)
    }
    
    @Test
    fun `test virtual core initialization success`() {
        // Given
        `when`(virtualCore.startup(context)).thenReturn(true)
        
        // When
        val result = virtualCoreManager.initializeVirtualCore(context)
        
        // Then
        assertTrue(result)
    }
    
    @Test
    fun `test virtual core initialization failure`() {
        // Given
        `when`(virtualCore.startup(context)).thenThrow(RuntimeException("Initialization failed"))
        
        // When
        val result = virtualCoreManager.initializeVirtualCore(context)
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `test virtual app installation success`() {
        // Given
        val apkPath = "/path/to/app.apk"
        val installResult = mock(InstallResult::class.java)
        `when`(installResult.isSuccess).thenReturn(true)
        `when`(installResult.packageName).thenReturn("com.example.app")
        `when`(virtualCore.installPackage(apkPath, InstallStrategy.COMPARE_VERSION)).thenReturn(installResult)
        
        // When
        val result = virtualCoreManager.installVirtualApp(context, apkPath)
        
        // Then
        assertTrue(result is InstallResult.Success)
        assertEquals("com.example.app", (result as InstallResult.Success).packageName)
    }
    
    @Test
    fun `test virtual app launch success`() {
        // Given
        val packageName = "com.example.app"
        val userId = 0
        val launchIntent = Intent()
        `when`(virtualCore.getLaunchIntent(packageName, userId)).thenReturn(launchIntent)
        
        // When
        val result = virtualCoreManager.launchVirtualApp(context, packageName, userId)
        
        // Then
        assertTrue(result)
    }
}
```

### 2. Integration Testing

**AuthenticationIntegrationTest.kt**
```kotlin
@RunWith(AndroidJUnit4::class)
class AuthenticationIntegrationTest {
    
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)
    
    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var sessionManager: SessionManager
    
    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        googleAuthManager = GoogleAuthManager(context)
        sessionManager = SessionManager.getInstance(context)
    }
    
    @Test
    fun testCompleteAuthenticationFlow() {
        // Test Google Sign-In flow
        onView(withId(R.id.btn_google_signin))
            .perform(click())
        
        // Wait for sign-in process
        Thread.sleep(3000)
        
        // Verify user is signed in
        val currentUser = sessionManager.getCurrentUser()
        assertNotNull(currentUser)
        assertTrue(sessionManager.isSessionValid())
    }
    
    @Test
    fun testVirtualAppLaunchAfterAuth() {
        // Ensure user is authenticated
        val userProfile = UserProfile(
            id = "test_id",
            email = "test@example.com",
            displayName = "Test User",
            photoUrl = null,
            idToken = "test_token"
        )
        sessionManager.saveUserSession(userProfile)
        
        // Test virtual app launch
        onView(withId(R.id.btn_launch_virtual_app))
            .perform(click())
        
        // Verify virtual app is launched
        onView(withText("Virtual App Launched"))
            .check(matches(isDisplayed()))
    }
}
```

### 3. Debugging Tools

**DebugUtils.kt**
```kotlin
object DebugUtils {
    private const val TAG = "DebugUtils"
    
    fun logVirtualCoreState() {
        if (BuildConfig.DEBUG) {
            try {
                val virtualCore = VirtualCore.get()
                Log.d(TAG, "=== VirtualCore State ===")
                Log.d(TAG, "Is VirtualCore running: ${virtualCore.isRunning}")
                Log.d(TAG, "Installed packages: ${virtualCore.installedPackages.size}")
                
                virtualCore.installedPackages.forEach { pkg ->
                    Log.d(TAG, "Package: ${pkg.packageName}, Version: ${pkg.versionName}")
                }
                
                Log.d(TAG, "=== End VirtualCore State ===")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log VirtualCore state", e)
            }
        }
    }
    
    fun logNetworkState(context: Context) {
        if (BuildConfig.DEBUG) {
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork = connectivityManager.activeNetworkInfo
                
                Log.d(TAG, "=== Network State ===")
                Log.d(TAG, "Is connected: ${activeNetwork?.isConnected}")
                Log.d(TAG, "Network type: ${activeNetwork?.typeName}")
                Log.d(TAG, "Network subtype: ${activeNetwork?.subtypeName}")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val network = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    Log.d(TAG, "Has WiFi: ${capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}")
                    Log.d(TAG, "Has Cellular: ${capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}")
                }
                
                Log.d(TAG, "=== End Network State ===")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log network state", e)
            }
        }
    }
    
    fun logMemoryUsage() {
        if (BuildConfig.DEBUG) {
            try {
                val runtime = Runtime.getRuntime()
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                val maxMemory = runtime.maxMemory()
                
                Log.d(TAG, "=== Memory Usage ===")
                Log.d(TAG, "Used Memory: ${usedMemory / 1024 / 1024} MB")
                Log.d(TAG, "Max Memory: ${maxMemory / 1024 / 1024} MB")
                Log.d(TAG, "Memory Usage: ${(usedMemory * 100 / maxMemory)}%")
                Log.d(TAG, "=== End Memory Usage ===")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log memory usage", e)
            }
        }
    }
    
    fun exportDebugLogs(context: Context): File? {
        return try {
            val logsDir = File(context.getExternalFilesDir(null), "debug_logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }
            
            val logFile = File(logsDir, "debug_${System.currentTimeMillis()}.txt")
            
            val debugInfo = buildString {
                appendLine("=== Debug Information ===")
                appendLine("Timestamp: ${Date()}")
                appendLine("App Version: ${BuildConfig.VERSION_NAME}")
                appendLine("Build Type: ${BuildConfig.BUILD_TYPE}")
                appendLine("Device Model: ${Build.MODEL}")
                appendLine("Android Version: ${Build.VERSION.RELEASE}")
                appendLine("SDK Version: ${Build.VERSION.SDK_INT}")
                appendLine()
                
                // Add VirtualCore state
                appendLine("=== VirtualCore Information ===")
                try {
                    val virtualCore = VirtualCore.get()
                    appendLine("VirtualCore Running: ${virtualCore.isRunning}")
                    appendLine("Installed Packages: ${virtualCore.installedPackages.size}")
                } catch (e: Exception) {
                    appendLine("VirtualCore Error: ${e.message}")
                }
                appendLine()
                
                // Add memory information
                appendLine("=== Memory Information ===")
                val runtime = Runtime.getRuntime()
                appendLine("Used Memory: ${(runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024} MB")
                appendLine("Max Memory: ${runtime.maxMemory() / 1024 / 1024} MB")
                appendLine()
                
                appendLine("=== End Debug Information ===")
            }
            
            logFile.writeText(debugInfo)
            Log.d(TAG, "Debug logs exported to: ${logFile.absolutePath}")
            
            logFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export debug logs", e)
            null
        }
    }
}
```

---

## 📝 সারসংক্ষেপ

### ✅ Implementation Checklist

- [ ] **VirtualCore Framework Setup**
  - [ ] Dependencies configuration
  - [ ] VirtualCore initialization
  - [ ] Virtual app installation & launch

- [ ] **Wi-Fi Network Isolation**
  - [ ] Network management stub
  - [ ] Virtual Wi-Fi configuration
  - [ ] UID-based network policies

- [ ] **Authentication System**
  - [ ] Google Sign-In integration
  - [ ] Session management
  - [ ] Virtual user mapping

- [ ] **Crash Prevention**
  - [ ] Global crash handler
  - [ ] Database recovery
  - [ ] Error logging & reporting

- [ ] **Build & Deployment**
  - [ ] Gradle configuration
  - [ ] ProGuard rules
  - [ ] Signing & deployment

- [ ] **Testing & Debugging**
  - [ ] Unit tests
  - [ ] Integration tests
  - [ ] Debug utilities

### 🎯 Key Success Factors

1. **Proper Error Handling**: সব critical operation এ try-catch ব্যবহার করুন
2. **Memory Management**: Virtual apps এর জন্য memory leak prevention
3. **Network Security**: Isolated network configuration maintain করুন
4. **User Experience**: Smooth authentication flow এবং app launching
5. **Stability**: Comprehensive crash prevention এবং recovery mechanism

### 🚨 Important Notes

- **VirtualCore License**: Commercial use এর জন্য proper license নিশ্চিত করুন
- **Google Play Policy**: App cloning policy compliance check করুন
- **Security**: Sensitive data encryption এবং secure storage implement করুন
- **Performance**: Regular memory cleanup এবং optimization করুন
- **Testing**: সব device এবং Android version এ thorough testing করুন

---

**🎉 এই guide follow করে আপনি একটি stable এবং secure App Cloning System তৈরি করতে পারবেন যা crash-free এবং feature-rich হবে।**