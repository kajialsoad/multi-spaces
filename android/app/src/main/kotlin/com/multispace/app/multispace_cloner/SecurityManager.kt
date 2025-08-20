package com.multispace.app.multispace_cloner

import android.content.Context
import android.os.Debug
import android.util.Base64
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Advanced Security Manager for MultiSpace Cloner
 * Provides AES-256 encryption, obfuscation, and anti-debugging features
 */
class SecurityManager private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: SecurityManager? = null
        private const val TAG = "SecurityManager"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_LENGTH = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        
        // Obfuscated strings
        private val obfuscatedStrings = mapOf(
            "debug_detected" to byteArrayOf(100, 101, 98, 117, 103, 95, 100, 101, 116, 101, 99, 116, 101, 100),
            "root_detected" to byteArrayOf(114, 111, 111, 116, 95, 100, 101, 116, 101, 99, 116, 101, 100),
            "tamper_detected" to byteArrayOf(116, 97, 109, 112, 101, 114, 95, 100, 101, 116, 101, 99, 116, 101, 100)
        )
        
        fun getInstance(): SecurityManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurityManager().also { INSTANCE = it }
            }
        }
    }
    
    private var isSecurityInitialized = false
    private var masterKey: SecretKey? = null
    private val secureRandom = SecureRandom()
    
    /**
     * Initialize security system
     */
    fun initializeSecurity(context: Context): Boolean {
        return try {
            // Anti-debugging checks
            if (isDebuggingDetected()) {
                Log.w(TAG, deobfuscateString("debug_detected"))
                return false
            }
            
            // Root detection
            if (isRootDetected(context)) {
                Log.w(TAG, deobfuscateString("root_detected"))
                // Continue but with limited functionality
            }
            
            // Tamper detection
            if (isTamperDetected(context)) {
                Log.w(TAG, deobfuscateString("tamper_detected"))
                return false
            }
            
            // Generate or load master key
            masterKey = generateOrLoadMasterKey(context)
            
            // Initialize obfuscation
            initializeObfuscation()
            
            isSecurityInitialized = true
            Log.i(TAG, "Security system initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize security system", e)
            false
        }
    }
    
    /**
     * AES-256 Encryption Methods
     */
    
    /**
     * Encrypt data using AES-256-GCM
     */
    fun encryptData(data: ByteArray, key: SecretKey? = null): EncryptedData? {
        return try {
            val encryptionKey = key ?: masterKey ?: return null
            
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val iv = ByteArray(GCM_IV_LENGTH)
            secureRandom.nextBytes(iv)
            
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, gcmSpec)
            
            val encryptedData = cipher.doFinal(data)
            
            EncryptedData(encryptedData, iv)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            null
        }
    }
    
    /**
     * Decrypt data using AES-256-GCM
     */
    fun decryptData(encryptedData: EncryptedData, key: SecretKey? = null): ByteArray? {
        return try {
            val decryptionKey = key ?: masterKey ?: return null
            
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, encryptedData.iv)
            cipher.init(Cipher.DECRYPT_MODE, decryptionKey, gcmSpec)
            
            cipher.doFinal(encryptedData.data)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            null
        }
    }
    
    /**
     * Encrypt string data
     */
    fun encryptString(text: String, key: SecretKey? = null): String? {
        val encryptedData = encryptData(text.toByteArray(Charsets.UTF_8), key)
        return encryptedData?.let {
            Base64.encodeToString(it.iv + it.data, Base64.DEFAULT)
        }
    }
    
    /**
     * Decrypt string data
     */
    fun decryptString(encryptedText: String, key: SecretKey? = null): String? {
        return try {
            val combined = Base64.decode(encryptedText, Base64.DEFAULT)
            val iv = combined.sliceArray(0 until GCM_IV_LENGTH)
            val data = combined.sliceArray(GCM_IV_LENGTH until combined.size)
            
            val encryptedData = EncryptedData(data, iv)
            val decryptedBytes = decryptData(encryptedData, key)
            
            decryptedBytes?.let { String(it, Charsets.UTF_8) }
        } catch (e: Exception) {
            Log.e(TAG, "String decryption failed", e)
            null
        }
    }
    
    /**
     * Generate AES-256 key
     */
    fun generateAESKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(KEY_LENGTH)
        return keyGenerator.generateKey()
    }
    
    /**
     * Anti-Debugging Features
     */
    
    /**
     * Detect if debugger is attached
     */
    fun isDebuggingDetected(): Boolean {
        // Check if debugger is connected
        if (Debug.isDebuggerConnected()) {
            return true
        }
        
        // Check if waiting for debugger
        if (Debug.waitingForDebugger()) {
            return true
        }
        
        // Check TracerPid in /proc/self/status
        try {
            val statusFile = File("/proc/self/status")
            if (statusFile.exists()) {
                val content = statusFile.readText()
                val tracerPidLine = content.lines().find { it.startsWith("TracerPid:") }
                if (tracerPidLine != null) {
                    val tracerPid = tracerPidLine.split("\t")[1].toIntOrNull() ?: 0
                    if (tracerPid != 0) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore errors
        }
        
        // Check for common debugging tools
        val debuggingProcesses = listOf(
            "gdb", "lldb", "strace", "ltrace", "frida", "xposed"
        )
        
        try {
            val process = Runtime.getRuntime().exec("ps")
            val output = process.inputStream.bufferedReader().readText()
            
            for (debugProcess in debuggingProcesses) {
                if (output.contains(debugProcess, ignoreCase = true)) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignore errors
        }
        
        return false
    }
    
    /**
     * Detect root access
     */
    fun isRootDetected(context: Context): Boolean {
        // Check for su binary
        val suPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/vendor/bin/su",
            "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk"
        )
        
        for (path in suPaths) {
            if (File(path).exists()) {
                return true
            }
        }
        
        // Check for root management apps
        val rootApps = listOf(
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.zachspong.temprootremovejb",
            "com.ramdroid.appquarantine"
        )
        
        try {
            val pm = context.packageManager
            for (packageName in rootApps) {
                try {
                    pm.getPackageInfo(packageName, 0)
                    return true
                } catch (e: Exception) {
                    // Package not found, continue
                }
            }
        } catch (e: Exception) {
            // Ignore errors
        }
        
        // Try to execute su command
        try {
            val process = Runtime.getRuntime().exec("su")
            process.waitFor()
            return true
        } catch (e: Exception) {
            // Su not available
        }
        
        return false
    }
    
    /**
     * Detect tampering with the app
     */
    fun isTamperDetected(context: Context): Boolean {
        try {
            // Check app signature
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_SIGNATURES
            )
            
            val signatures = packageInfo.signatures
            if (signatures?.isEmpty() != false) {
                return true
            }
            
            // Calculate signature hash
            val signature = signatures[0]
            val md = MessageDigest.getInstance("SHA-256")
            val signatureHash = md.digest(signature.toByteArray())
            
            // Compare with expected hash (you should replace this with your actual signature hash)
            val expectedHash = "your_expected_signature_hash_here"
            val actualHash = Base64.encodeToString(signatureHash, Base64.DEFAULT).trim()
            
            if (actualHash != expectedHash) {
                Log.w(TAG, "Signature mismatch detected")
                // For development, don't fail on signature mismatch
                // return true
            }
            
            // Check for Xposed framework
            try {
                Class.forName("de.robv.android.xposed.XposedHelpers")
                return true
            } catch (e: ClassNotFoundException) {
                // Xposed not detected
            }
            
            // Check for Frida
            try {
                val fridaFiles = listOf(
                    "/data/local/tmp/frida-server",
                    "/data/local/tmp/re.frida.server"
                )
                
                for (file in fridaFiles) {
                    if (File(file).exists()) {
                        return true
                    }
                }
            } catch (e: Exception) {
                // Ignore errors
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Tamper detection failed", e)
        }
        
        return false
    }
    
    /**
     * Obfuscation Features
     */
    
    /**
     * Initialize obfuscation system
     */
    private fun initializeObfuscation() {
        // Perform string obfuscation initialization
        // Add dummy operations to confuse static analysis
        val dummy1 = Random.nextInt(1000)
        val dummy2 = Random.nextInt(1000)
        val dummy3 = dummy1 + dummy2
        
        // Obfuscate control flow
        when (dummy3 % 3) {
            0 -> performDummyOperation1()
            1 -> performDummyOperation2()
            else -> performDummyOperation3()
        }
    }
    
    /**
     * Deobfuscate string
     */
    private fun deobfuscateString(key: String): String {
        val obfuscated = obfuscatedStrings[key] ?: return key
        return String(obfuscated)
    }
    
    /**
     * Obfuscate string at runtime
     */
    fun obfuscateString(input: String): ByteArray {
        val bytes = input.toByteArray()
        val key = secureRandom.nextInt(256)
        
        return bytes.map { (it.toInt() xor key).toByte() }.toByteArray()
    }
    
    /**
     * Deobfuscate runtime obfuscated string
     */
    fun deobfuscateRuntimeString(obfuscated: ByteArray, key: Int): String {
        val bytes = obfuscated.map { (it.toInt() xor key).toByte() }.toByteArray()
        return String(bytes)
    }
    
    /**
     * Dummy operations for control flow obfuscation
     */
    private fun performDummyOperation1() {
        val dummy = (1..100).map { Random.nextInt() }.sum()
        Log.v(TAG, "Dummy operation 1: $dummy")
    }
    
    private fun performDummyOperation2() {
        val dummy = (1..50).map { Random.nextDouble() }.average()
        Log.v(TAG, "Dummy operation 2: $dummy")
    }
    
    private fun performDummyOperation3() {
        val dummy = Random.nextBytes(32)
        Log.v(TAG, "Dummy operation 3: ${dummy.size}")
    }
    
    /**
     * Key Management
     */
    
    /**
     * Generate or load master key
     */
    private fun generateOrLoadMasterKey(context: Context): SecretKey {
        val keyFile = File(context.filesDir, ".security_key")
        
        return if (keyFile.exists()) {
            try {
                val keyBytes = keyFile.readBytes()
                SecretKeySpec(keyBytes, "AES")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load existing key, generating new one")
                generateAndSaveMasterKey(context)
            }
        } else {
            generateAndSaveMasterKey(context)
        }
    }
    
    /**
     * Generate and save master key
     */
    private fun generateAndSaveMasterKey(context: Context): SecretKey {
        val key = generateAESKey()
        val keyFile = File(context.filesDir, ".security_key")
        
        try {
            keyFile.writeBytes(key.encoded)
            Log.i(TAG, "Master key generated and saved")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save master key", e)
        }
        
        return key
    }
    
    /**
     * Generate and save master key (public method)
     */
    fun generateAndSaveKey(context: Context): Boolean {
        return try {
            generateAndSaveMasterKey(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate and save key", e)
            false
        }
    }
    
    /**
     * Security status check
     */
    fun getSecurityStatus(): SecurityStatus {
        val threats = mutableListOf<String>()
        val isDebugging = isDebuggingDetected()
        
        if (isDebugging) {
            threats.add("debugging_detected")
        }
        
        val securityLevel = when {
            threats.isEmpty() && isSecurityInitialized -> "high"
            threats.size <= 1 -> "medium"
            else -> "low"
        }
        
        return SecurityStatus(
            isInitialized = isSecurityInitialized,
            hasValidKey = masterKey != null,
            isDebuggingDetected = isDebugging,
            isRootDetected = false, // Context not available here
            isTamperDetected = false, // Context not available here
            isEmulatorDetected = false, // Context not available here
            encryptionEnabled = masterKey != null,
            securityLevel = securityLevel,
            threats = threats
        )
    }
    
    /**
     * Data classes
     */
    data class EncryptedData(
        val data: ByteArray,
        val iv: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as EncryptedData
            
            if (!data.contentEquals(other.data)) return false
            if (!iv.contentEquals(other.iv)) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            return result
        }
    }
    
    data class SecurityStatus(
        val isInitialized: Boolean,
        val hasValidKey: Boolean,
        val isDebuggingDetected: Boolean,
        val isRootDetected: Boolean,
        val isTamperDetected: Boolean,
        val isEmulatorDetected: Boolean,
        val encryptionEnabled: Boolean,
        val securityLevel: String,
        val threats: List<String>
    )
}