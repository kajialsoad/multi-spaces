package com.multispace.app.multispace_cloner

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey

/**
 * Data Manager class for handling data isolation and SharedPreferences
 * Multiple accounts এর জন্য data isolation manage করে
 */
class DataManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "DataManager"
        private const val PREFS_PREFIX = "multispace_"
        private const val MAIN_PREFS = "multispace_main"
        private const val CLONE_DATA_DIR = "clone_data"
        
        @Volatile
        private var INSTANCE: DataManager? = null
        
        fun getInstance(context: Context): DataManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DataManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val databaseHelper = DatabaseHelper.getInstance(context)
    private val securityManager = SecurityManager.getInstance(context)
    
    /**
     * Get SharedPreferences for a specific cloned app
     * প্রতিটি cloned app এর জন্য আলাদা SharedPreferences
     */
    fun getClonedAppPreferences(clonedPackageName: String): SharedPreferences {
        val prefsName = "${PREFS_PREFIX}${hashPackageName(clonedPackageName)}"
        return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }
    
    /**
     * Get main app SharedPreferences
     */
    fun getMainPreferences(): SharedPreferences {
        return context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)
    }
    
    /**
     * Get SharedPreferences for a specific user account within a cloned app
     * Multiple Facebook accounts এর জন্য আলাদা data isolation
     */
    fun getAccountSpecificPreferences(clonedPackageName: String, accountId: String): SharedPreferences {
        val prefsName = "${PREFS_PREFIX}${hashPackageName(clonedPackageName)}_account_${hashPackageName(accountId)}"
        return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }
    
    /**
     * Create isolated data directory for a specific account within cloned app
     */
    fun createAccountDataDirectory(clonedPackageName: String, accountId: String): String {
        val accountDataDir = File(context.filesDir, "$CLONE_DATA_DIR/${hashPackageName(clonedPackageName)}/accounts/${hashPackageName(accountId)}")
        
        return try {
            if (!accountDataDir.exists()) {
                accountDataDir.mkdirs()
            }
            
            // Create account-specific subdirectories
            val subDirs = listOf("cache", "databases", "shared_prefs", "files")
            subDirs.forEach { subDir ->
                File(accountDataDir, subDir).mkdirs()
            }
            
            Log.d(TAG, "Account data directory created: ${accountDataDir.absolutePath}")
            accountDataDir.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create account data directory", e)
            ""
        }
    }
    
    /**
     * Switch active account for a cloned app
     */
    fun switchActiveAccount(clonedAppId: Long, newAccountId: String): Boolean {
        return try {
            val clonedApp = databaseHelper.getClonedAppById(clonedAppId)
            if (clonedApp != null) {
                // Store current active account
                val prefs = getClonedAppPreferences(clonedApp.clonedPackageName)
                prefs.edit().putString("active_account_id", newAccountId).apply()
                
                // Update last used time for the account
                val accountPrefs = getAccountSpecificPreferences(clonedApp.clonedPackageName, newAccountId)
                accountPrefs.edit().putLong("last_used", System.currentTimeMillis()).apply()
                
                Log.d(TAG, "Switched active account for ${clonedApp.clonedPackageName} to $newAccountId")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch active account", e)
            false
        }
    }
    
    /**
     * Get active account ID for a cloned app
     */
    fun getActiveAccountId(clonedPackageName: String): String? {
        return try {
            val prefs = getClonedAppPreferences(clonedPackageName)
            prefs.getString("active_account_id", null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get active account ID", e)
            null
        }
    }
    
    /**
     * Get all account IDs for a cloned app
     */
    fun getAllAccountIds(clonedPackageName: String): List<String> {
        return try {
            val prefs = getClonedAppPreferences(clonedPackageName)
            val accountsJson = prefs.getString("account_ids", "[]")
            parseJsonToList(accountsJson ?: "[]")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all account IDs", e)
            emptyList()
        }
    }
    
    /**
     * Add new account to cloned app
     */
    fun addAccountToClonedApp(clonedPackageName: String, accountId: String, accountData: Map<String, String>): Boolean {
        return try {
            // Create account data directory
            createAccountDataDirectory(clonedPackageName, accountId)
            
            // Store account data
            val accountPrefs = getAccountSpecificPreferences(clonedPackageName, accountId)
            val editor = accountPrefs.edit()
            accountData.forEach { (key, value) ->
                val enc = encryptStringSecure(value) ?: value
                editor.putString(key, enc)
            }
            editor.putLong("created_at", System.currentTimeMillis())
            editor.apply()
            
            // Add to account list
            val prefs = getClonedAppPreferences(clonedPackageName)
            val currentAccounts = getAllAccountIds(clonedPackageName).toMutableList()
            if (!currentAccounts.contains(accountId)) {
                currentAccounts.add(accountId)
                prefs.edit().putString("account_ids", listToJson(currentAccounts)).apply()
            }
            
            Log.d(TAG, "Added account $accountId to $clonedPackageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add account to cloned app", e)
            false
        }
    }
    
    /**
     * Remove account from cloned app
     */
    fun removeAccountFromClonedApp(clonedPackageName: String, accountId: String): Boolean {
        return try {
            // Clear account preferences
            val accountPrefs = getAccountSpecificPreferences(clonedPackageName, accountId)
            accountPrefs.edit().clear().apply()
            
            // Remove account data directory
            val accountDataDir = File(context.filesDir, "$CLONE_DATA_DIR/${hashPackageName(clonedPackageName)}/accounts/${hashPackageName(accountId)}")
            if (accountDataDir.exists()) {
                accountDataDir.deleteRecursively()
            }
            
            // Remove from account list
            val prefs = getClonedAppPreferences(clonedPackageName)
            val currentAccounts = getAllAccountIds(clonedPackageName).toMutableList()
            currentAccounts.remove(accountId)
            prefs.edit().putString("account_ids", listToJson(currentAccounts)).apply()
            
            // If this was the active account, switch to another or clear
            val activeAccountId = getActiveAccountId(clonedPackageName)
            if (activeAccountId == accountId) {
                val newActiveAccount = currentAccounts.firstOrNull()
                if (newActiveAccount != null) {
                    prefs.edit().putString("active_account_id", newActiveAccount).apply()
                } else {
                    prefs.edit().remove("active_account_id").apply()
                }
            }
            
            Log.d(TAG, "Removed account $accountId from $clonedPackageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove account from cloned app", e)
            false
        }
    }
    
    /**
     * Create isolated data directory for cloned app
     * প্রতিটি cloned app এর জন্য আলাদা data directory
     */
    fun createClonedAppDataDirectory(clonedPackageName: String): String {
        try {
            val baseDir = File(context.filesDir, CLONE_DATA_DIR)
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
            
            val cloneDir = File(baseDir, hashPackageName(clonedPackageName))
            if (!cloneDir.exists()) {
                cloneDir.mkdirs()
                
                // Create subdirectories for different data types
                File(cloneDir, "shared_prefs").mkdirs()
                File(cloneDir, "databases").mkdirs()
                File(cloneDir, "cache").mkdirs()
                File(cloneDir, "files").mkdirs()
                File(cloneDir, "app_data").mkdirs()
                
                Log.d(TAG, "Created data directory for $clonedPackageName at ${cloneDir.absolutePath}")
            }
            
            return cloneDir.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error creating data directory for $clonedPackageName", e)
            return context.filesDir.absolutePath
        }
    }
    
    /**
     * Store account login data securely
     * Account login data encrypted করে store করে
     */
    fun storeAccountLoginData(clonedAppId: Long, accountName: String, loginData: Map<String, String>): Boolean {
        return try {
            val jsonData = loginData.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }
            val encryptedData = encryptStringSecure("{$jsonData}") ?: return false
            
            val account = UserAccount(
                clonedAppId = clonedAppId,
                accountName = accountName,
                accountType = extractAccountType(loginData),
                loginData = encryptedData,
                isActive = true,
                createdAt = System.currentTimeMillis(),
                lastLogin = System.currentTimeMillis()
            )
            
            val result = databaseHelper.insertUserAccount(account)
            Log.d(TAG, "Stored login data for account: $accountName, result: $result")
            result > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error storing account login data", e)
            false
        }
    }
    
    /**
     * Retrieve account login data
     * Account login data decrypt করে return করে
     */
    fun getAccountLoginData(clonedAppId: Long, accountName: String): Map<String, String>? {
        return try {
            val accounts = databaseHelper.getUserAccounts(clonedAppId)
            val account = accounts.find { it.accountName == accountName && it.isActive }
            
            account?.loginData?.let { encryptedData ->
                val decryptedData = decryptStringSecure(encryptedData)
                decryptedData?.let { parseJsonToMap(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving account login data", e)
            null
        }
    }
    
    /**
     * Store app-specific data in isolated storage
     * App specific data isolated storage এ store করে
     */
    fun storeAppData(clonedPackageName: String, key: String, value: String): Boolean {
        return try {
            val prefs = getClonedAppPreferences(clonedPackageName)
            prefs.edit().putString(key, value).apply()
            Log.d(TAG, "Stored app data for $clonedPackageName: $key")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error storing app data", e)
            false
        }
    }
    
    /**
     * Retrieve app-specific data from isolated storage
     */
    fun getAppData(clonedPackageName: String, key: String, defaultValue: String? = null): String? {
        return try {
            val prefs = getClonedAppPreferences(clonedPackageName)
            prefs.getString(key, defaultValue)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving app data", e)
            defaultValue
        }
    }
    
    /**
     * Clear all data for a cloned app
     * Cloned app এর সব data clear করে
     */
    fun clearClonedAppData(clonedPackageName: String): Boolean {
        return try {
            // Clear SharedPreferences
            val prefs = getClonedAppPreferences(clonedPackageName)
            prefs.edit().clear().apply()
            
            // Clear data directory
            val dataDir = File(context.filesDir, "$CLONE_DATA_DIR/${hashPackageName(clonedPackageName)}")
            if (dataDir.exists()) {
                dataDir.deleteRecursively()
            }
            
            Log.d(TAG, "Cleared all data for $clonedPackageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cloned app data", e)
            false
        }
    }
    
    /**
     * Get storage usage for a cloned app
     * Cloned app এর storage usage calculate করে
     */
    fun getStorageUsage(clonedPackageName: String): Long {
        return try {
            val dataDir = File(context.filesDir, "$CLONE_DATA_DIR/${hashPackageName(clonedPackageName)}")
            if (dataDir.exists()) {
                calculateDirectorySize(dataDir)
            } else {
                0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating storage usage", e)
            0L
        }
    }
    
    /**
     * Backup cloned app data
     * Cloned app data backup করে
     */
    fun backupClonedAppData(clonedPackageName: String, backupPath: String): Boolean {
        return try {
            val dataDir = File(context.filesDir, "$CLONE_DATA_DIR/${hashPackageName(clonedPackageName)}")
            val backupDir = File(backupPath)
            
            if (dataDir.exists()) {
                dataDir.copyRecursively(backupDir, overwrite = true)
                Log.d(TAG, "Backup completed for $clonedPackageName")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error backing up cloned app data", e)
            false
        }
    }
    
    /**
     * Restore cloned app data from backup
     * Backup থেকে cloned app data restore করে
     */
    fun restoreClonedAppData(clonedPackageName: String, backupPath: String): Boolean {
        return try {
            val dataDir = File(context.filesDir, "$CLONE_DATA_DIR/${hashPackageName(clonedPackageName)}")
            val backupDir = File(backupPath)
            
            if (backupDir.exists()) {
                // Clear existing data
                if (dataDir.exists()) {
                    dataDir.deleteRecursively()
                }
                
                // Restore from backup
                backupDir.copyRecursively(dataDir, overwrite = true)
                Log.d(TAG, "Restore completed for $clonedPackageName")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring cloned app data", e)
            false
        }
    }
    
    /**
     * Hash package name for directory/preference naming
     * Package name hash করে unique identifier তৈরি করে
     */
    private fun hashPackageName(packageName: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(packageName.toByteArray())
            hash.joinToString("") { "%02x".format(it) }.take(16)
        } catch (e: Exception) {
            packageName.replace(".", "_")
        }
    }
    
    /**
     * Extract account type from login data
     */
    private fun extractAccountType(loginData: Map<String, String>): String {
        return when {
            loginData.containsKey("facebook_token") -> "facebook"
            loginData.containsKey("whatsapp_number") -> "whatsapp"
            loginData.containsKey("instagram_username") -> "instagram"
            loginData.containsKey("telegram_phone") -> "telegram"
            else -> "unknown"
        }
    }
    
    /**
     * Parse JSON string to Map
     */
    private fun parseJsonToMap(jsonString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val cleanJson = jsonString.trim().removeSurrounding("{", "}")
            val pairs = cleanJson.split(",")
            
            for (pair in pairs) {
                val keyValue = pair.split(":")
                if (keyValue.size == 2) {
                    val key = keyValue[0].trim().removeSurrounding("\"")
                    val value = keyValue[1].trim().removeSurrounding("\"")
                    map[key] = value
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON to map", e)
        }
        return map
    }
    
    /**
     * Calculate directory size recursively
     */
    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        try {
            if (directory.isDirectory) {
                directory.listFiles()?.forEach { file ->
                    size += if (file.isDirectory) {
                        calculateDirectorySize(file)
                    } else {
                        file.length()
                    }
                }
            } else {
                size = directory.length()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating directory size", e)
        }
        return size
    }
    
    /**
     * Helper method to parse JSON string to List
     */
    private fun parseJsonToList(jsonString: String): List<String> {
        return try {
            if (jsonString.isBlank() || jsonString == "[]") {
                emptyList()
            } else {
                // Simple JSON array parsing for string list
                jsonString.trim()
                    .removePrefix("[")
                    .removeSuffix("]")
                    .split(",")
                    .map { it.trim().removePrefix("\"").removeSuffix("\"") }
                    .filter { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON to list: $jsonString", e)
            emptyList()
        }
    }
    
    /**
     * Helper method to convert List to JSON string
     */
    private fun listToJson(list: List<String>): String {
        return list.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    }
    
    // Add keystore alias resolved via ObfuscationEngine
    private val keystoreAlias: String by lazy {
        try {
            val constants = ObfuscationEngine.getObfuscatedConstants()
            val deobf = ObfuscationEngine.runtimeDeobfuscate(constants)
            deobf["KEYSTORE_ALIAS"] ?: "multispace_master_key"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve keystore alias from ObfuscationEngine, using default", e)
            "multispace_master_key"
        }
    }
    
    /**
     * Create isolated data directory (Google Incognito-like)
     * প্রতিটি clone এর জন্য সম্পূর্ণ আলাদা data directory
     */
    fun createCompletelyIsolatedDataDirectory(clonedPackageName: String): String {
        return try {
            val uniqueId = System.currentTimeMillis().toString()
            val randomId = (System.nanoTime() % 100000).toString()
            val isolatedDir = File(context.filesDir, "isolated_spaces/$clonedPackageName")
    
            if (!isolatedDir.exists()) {
                isolatedDir.mkdirs()
            }
    
            // Create complete isolated environment
            val isolatedSubDirs = listOf(
                "databases", "shared_prefs", "cache", "files", "temp",
                "cookies", "tokens", "accounts", "settings", "storage",
                "webview", "keystore", "logs", "media", "downloads",
                "sessions", "auth", "user_data"
            )
    
            isolatedSubDirs.forEach { dirName ->
                val subDir = File(isolatedDir, dirName)
                if (!subDir.exists()) {
                    subDir.mkdirs()
                }
            }
    
            // Create isolation config
            val configFile = File(isolatedDir, "isolation_config.json")
            val configData = """
                {
                    "isolation_id": "$uniqueId",
                    "random_id": "$randomId",
                    "package_name": "$clonedPackageName",
                    "created_at": ${System.currentTimeMillis()},
                    "isolation_level": "complete",
                    "features": {
                        "account_isolation": true,
                        "cookie_isolation": true,
                        "token_isolation": true,
                        "database_isolation": true,
                        "cache_isolation": true,
                        "settings_isolation": true,
                        "webview_isolation": true,
                        "keystore_isolation": true,
                        "session_isolation": true
                    }
                }
            """.trimIndent()
    
            configFile.writeText(configData)
    
            Log.d(TAG, "Created completely isolated data directory: ${isolatedDir.absolutePath}")
            isolatedDir.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create completely isolated data directory", e)
            // Fallback path
            val fallbackDir = File(context.filesDir, "isolated_spaces/$clonedPackageName")
            fallbackDir.absolutePath
        }
    }
    
    /**
     * Get isolated SharedPreferences for complete separation
     * প্রতিটি clone এর জন্য সম্পূর্ণ আলাদা preferences
     */
    fun getCompletelyIsolatedPreferences(clonedPackageName: String): SharedPreferences {
        val isolatedPrefsName = "isolated_${hashPackageName(clonedPackageName)}_${System.currentTimeMillis()}"
        return context.getSharedPreferences(isolatedPrefsName, Context.MODE_PRIVATE)
    }
    
    /**
     * Clear all isolated data for a specific clone
     * একটি clone এর সব data সম্পূর্ণ delete করে
     */
    fun clearCompletelyIsolatedData(clonedPackageName: String): Boolean {
        return try {
            val isolatedDir = File(context.filesDir, "isolated_spaces/$clonedPackageName")
            if (isolatedDir.exists()) {
                isolatedDir.deleteRecursively()
            }
    
            // Clear isolated preferences
            val prefsName = "isolated_${hashPackageName(clonedPackageName)}"
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
    
            Log.d(TAG, "Cleared completely isolated data for: $clonedPackageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear completely isolated data", e)
            false
        }
    }

    // Secure encryption helpers using Android Keystore + AES-GCM
    private fun getOrCreateAppMasterKey(): SecretKey? {
        // Try to fetch existing key; if missing, generate one in keystore
        CryptoUtils.getKeyFromKeystore(keystoreAlias)?.let { return it }
        return try {
            // storeKeyInKeystore generates a new AES key under the alias
            val ok = CryptoUtils.storeKeyInKeystore(keystoreAlias, CryptoUtils.generateAESKey())
            if (!ok) {
                Log.e(TAG, "Failed to create keystore key for alias=$keystoreAlias")
                null
            } else {
                CryptoUtils.getKeyFromKeystore(keystoreAlias)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating or retrieving keystore key", e)
            null
        }
    }

    private fun encryptStringSecure(plainText: String, associatedData: String? = null): String? {
        return try {
            val key = getOrCreateAppMasterKey() ?: return null
            val aad = associatedData?.toByteArray(StandardCharsets.UTF_8)
            val result = CryptoUtils.encryptAES(plainText.toByteArray(StandardCharsets.UTF_8), key, aad)
                ?: return null
            val ivB64 = CryptoUtils.encodeBase64(result.iv).trim()
            val dataB64 = CryptoUtils.encodeBase64(result.encryptedData).trim()
            val aadB64 = result.associatedData?.let { CryptoUtils.encodeBase64(it).trim() }
            if (aadB64 != null) {
                "v1:$ivB64:$dataB64:$aadB64"
            } else {
                "v1:$ivB64:$dataB64"
            }
        } catch (e: Exception) {
            Log.e(TAG, "encryptStringSecure failed", e)
            null
        }
    }

    private fun decryptStringSecure(cipherText: String): String? {
        return try {
            // Expected formats:
            // v1:<ivB64>:<dataB64> or v1:<ivB64>:<dataB64>:<aadB64>
            val parts = cipherText.split(":")
            if (parts.isEmpty() || parts[0] != "v1" || parts.size < 3) {
                Log.w(TAG, "Unsupported ciphertext format")
                return null
            }
            val iv = CryptoUtils.decodeBase64(parts[1])
            val enc = CryptoUtils.decodeBase64(parts[2])
            val aad = if (parts.size >= 4) CryptoUtils.decodeBase64(parts[3]) else null
            val key = CryptoUtils.getKeyFromKeystore(keystoreAlias) ?: return null
            val result = CryptoUtils.decryptAES(EncryptionResult(enc, iv, aad), key) ?: return null
            String(result, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "decryptStringSecure failed", e)
            null
        }
    }
}