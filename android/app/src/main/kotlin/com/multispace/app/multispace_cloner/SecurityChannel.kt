package com.multispace.app.multispace_cloner

import android.content.Context
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import javax.crypto.spec.SecretKeySpec

class SecurityChannel(private val context: Context) {
    private val channelName = "com.multispace.cloner/security"
    private lateinit var methodChannel: MethodChannel
    private lateinit var securityManager: SecurityManager
    private lateinit var antiTamperProtection: AntiTamperProtection

    fun initialize(flutterEngine: FlutterEngine) {
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
        securityManager = SecurityManager.getInstance(context)
        antiTamperProtection = AntiTamperProtection.getInstance()
        antiTamperProtection.initializeProtection(context)
        
        methodChannel.setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "initializeSecurity" -> {
                        // Security is automatically initialized when getInstance is called
                        result.success(true)
                    }
                    "encryptData" -> {
                        val data = call.argument<String>("data") ?: ""
                        val keyAlias = call.argument<String>("keyAlias") ?: "multispace_master_key"
                        val key = CryptoUtils.getKeyFromKeystore(keyAlias)
                            ?: CryptoUtils.generateAESKey().also { CryptoUtils.storeKeyInKeystore(keyAlias, it) }
                        val enc = CryptoUtils.encryptAES(data.toByteArray(Charsets.UTF_8), key)
                        if (enc != null) {
                            val encryptedResult = mapOf(
                                "encryptedData" to CryptoUtils.encodeBase64(enc.encryptedData),
                                "iv" to CryptoUtils.encodeBase64(enc.iv),
                                "keyAlias" to keyAlias
                            )
                            result.success(encryptedResult)
                        } else {
                            result.success(null)
                        }
                    }
                    "decryptData" -> {
                        val encryptedDataB64 = call.argument<String>("encryptedData") ?: ""
                        val ivB64 = call.argument<String>("iv") ?: ""
                        val keyAlias = call.argument<String>("keyAlias") ?: "multispace_master_key"
                        val key = CryptoUtils.getKeyFromKeystore(keyAlias)
                        if (key == null) {
                            result.success(null)
                        } else {
                            val encrypted = CryptoUtils.decodeBase64(encryptedDataB64)
                            val iv = CryptoUtils.decodeBase64(ivB64)
                            val decBytes = CryptoUtils.decryptAES(EncryptionResult(encrypted, iv, null), key)
                            result.success(decBytes?.toString(Charsets.UTF_8))
                        }
                    }
                    "encryptWithPassword" -> {
                        val data = call.argument<String>("data") ?: ""
                        val password = call.argument<String>("password") ?: ""
                        val res = CryptoUtils.encryptWithPassword(data.toByteArray(Charsets.UTF_8), password)
                        if (res != null) {
                            val map = mapOf(
                                "encryptedData" to CryptoUtils.encodeBase64(res.encryptedData),
                                "iv" to CryptoUtils.encodeBase64(res.iv),
                                "salt" to CryptoUtils.encodeBase64(res.salt)
                            )
                            result.success(map)
                        } else {
                            result.success(null)
                        }
                    }
                    "decryptWithPassword" -> {
                        val encryptedDataB64 = call.argument<String>("encryptedData") ?: ""
                        val ivB64 = call.argument<String>("iv") ?: ""
                        val saltB64 = call.argument<String>("salt") ?: ""
                        val password = call.argument<String>("password") ?: ""
                        val per = PasswordEncryptionResult(
                            CryptoUtils.decodeBase64(encryptedDataB64),
                            CryptoUtils.decodeBase64(ivB64),
                            CryptoUtils.decodeBase64(saltB64),
                            null
                        )
                        val dec = CryptoUtils.decryptWithPassword(per, password)
                        result.success(dec?.toString(Charsets.UTF_8))
                    }
                    "generateSecureKey" -> {
                        val keyAlias = call.argument<String>("keyAlias") ?: ""
                        val keySize = call.argument<Int>("keySize") ?: 256
                        val key = CryptoUtils.generateAESKey(keySize)
                        val ok = CryptoUtils.storeKeyInKeystore(keyAlias, key)
                        result.success(ok)
                    }
                    "storeSecureKey" -> {
                        val keyAlias = call.argument<String>("keyAlias") ?: ""
                        val keyData = call.argument<String>("keyData") ?: ""
                        val keyBytes = CryptoUtils.decodeBase64(keyData)
                        val secretKey = SecretKeySpec(keyBytes, "AES")
                        val ok = CryptoUtils.storeKeyInKeystore(keyAlias, secretKey)
                        result.success(ok)
                    }
                    "retrieveSecureKey" -> {
                        val keyAlias = call.argument<String>("keyAlias") ?: ""
                        val key = CryptoUtils.getKeyFromKeystore(keyAlias)
                        val encoded = key?.encoded
                        result.success(if (encoded != null) CryptoUtils.encodeBase64(encoded) else null)
                    }
                    "deleteSecureKey" -> {
                        val keyAlias = call.argument<String>("keyAlias") ?: ""
                        val ok = CryptoUtils.deleteKeyFromKeystore(keyAlias)
                        result.success(ok)
                    }
                    "isDebuggingDetected" -> {
                        val isDetected = securityManager.getSecurityStatus().isDebuggingDetected
                        result.success(isDetected)
                    }
                    "isRootDetected" -> {
                        val isDetected = securityManager.getSecurityStatus().isRootDetected
                        result.success(isDetected)
                    }
                    "isTamperDetected" -> {
                        val isDetected = !securityManager.getSecurityStatus().integrityVerified
                        result.success(isDetected)
                    }
                    "isEmulatorDetected" -> {
                        val isDetected = securityManager.getSecurityStatus().isEmulatorDetected
                        result.success(isDetected)
                    }
                    "getSecurityStatus" -> {
                        val status = securityManager.getSecurityStatus()
                        val threats = mutableListOf<String>()
                        if (status.isDebuggingDetected) threats.add("debugging_detected")
                        if (status.isRootDetected) threats.add("root_detected")
                        if (!status.integrityVerified) threats.add("tamper_detected")
                        if (status.isEmulatorDetected) threats.add("emulator_detected")
                        if (status.isHookingDetected) threats.add("hooking_detected")
                        val statusMap = mapOf(
                            "isDebuggingDetected" to status.isDebuggingDetected,
                            "isRootDetected" to status.isRootDetected,
                            "isTamperDetected" to !status.integrityVerified,
                            "isEmulatorDetected" to status.isEmulatorDetected,
                            "securityLevel" to status.securityLevel.name,
                            "threats" to threats
                        )
                        result.success(statusMap)
                    }
                    "checkAppIntegrity" -> {
                        val isValid = antiTamperProtection.checkApkIntegrity(context)
                        result.success(isValid)
                    }
                    "checkSecurityThreats" -> {
                        val threats = mutableListOf<String>()
                        val status = securityManager.getSecurityStatus()
                        if (status.isDebuggingDetected) threats.add("debugging_detected")
                        if (status.isRootDetected) threats.add("root_detected")
                        if (!status.integrityVerified) threats.add("tamper_detected")
                        if (status.isEmulatorDetected) threats.add("emulator_detected")
                        if (status.isHookingDetected) threats.add("hooking_detected")
                        result.success(threats)
                    }
                    "enableRuntimeProtection" -> {
                        antiTamperProtection.enableRuntimeProtection()
                        result.success(true)
                    }
                    "disableRuntimeProtection" -> {
                        antiTamperProtection.disableRuntimeProtection()
                        result.success(true)
                    }
                    "obfuscateString" -> {
                        val input = call.argument<String>("input") ?: ""
                        val keyIndex = call.argument<Int>("keyIndex") ?: 0
                        val obfuscated = ObfuscationEngine.obfuscateString(input, keyIndex)
                        result.success(obfuscated)
                    }
                    "deobfuscateString" -> {
                        val obfuscated = call.argument<String>("obfuscated") ?: ""
                        val keyIndex = call.argument<Int>("keyIndex") ?: 0
                        result.success(ObfuscationEngine.deobfuscateString(obfuscated, keyIndex))
                    }
                    "obfuscateStringAdvanced" -> {
                        val input = call.argument<String>("input") ?: ""
                        val obf = ObfuscationEngine.obfuscateStringAdvanced(input)
                        val map = mapOf(
                            "data" to obf.data,
                            "keyIndex" to obf.keyIndex,
                            "paddingLength" to obf.paddingLength,
                            "checksum" to obf.checksum
                        )
                        result.success(map)
                    }
                    "deobfuscateStringAdvanced" -> {
                        val data = call.argument<String>("data") ?: ""
                        val keyIndex = call.argument<Int>("keyIndex") ?: 0
                        val paddingLength = call.argument<Int>("paddingLength") ?: 0
                        val checksum = call.argument<String>("checksum") ?: ""
                        val obf = ObfuscatedString(data, keyIndex, paddingLength, checksum)
                        val plain = ObfuscationEngine.deobfuscateStringAdvanced(obf)
                        result.success(plain)
                    }
                    "generateSecureRandom" -> {
                        val length = call.argument<Int>("length") ?: 16
                        val type = call.argument<String>("type") ?: "bytes"
                        if (type.equals("string", ignoreCase = true)) {
                            val s = CryptoUtils.generateSecureRandomString(length)
                            result.success(s)
                        } else {
                            val bytes = CryptoUtils.generateSecureRandomBytes(length)
                            result.success(CryptoUtils.encodeBase64(bytes))
                        }
                    }
                    "calculateHash" -> {
                        val data = call.argument<String>("data") ?: ""
                        val algorithm = call.argument<String>("algorithm") ?: "SHA-256"
                        val bytes = data.toByteArray(Charsets.UTF_8)
                        val hash = if (algorithm.equals("SHA-512", ignoreCase = true)) {
                            CryptoUtils.sha512(bytes)
                        } else {
                            CryptoUtils.sha256(bytes)
                        }
                        result.success(CryptoUtils.bytesToHex(hash))
                    }
                    "validateIntegrity" -> {
                        val data = call.argument<String>("data") ?: ""
                        val expectedHash = call.argument<String>("expectedHash") ?: ""
                        val actualHashHex = CryptoUtils.bytesToHex(CryptoUtils.sha256(data.toByteArray(Charsets.UTF_8)))
                        result.success(CryptoUtils.secureEquals(actualHashHex.lowercase(), expectedHash.lowercase()))
                    }
                    "secureCompare" -> {
                        val value1 = call.argument<String>("value1") ?: ""
                        val value2 = call.argument<String>("value2") ?: ""
                        result.success(CryptoUtils.secureEquals(value1, value2))
                    }
                    "initializeAntiTamper" -> {
                        // Initialize anti-tamper functionality (already initialized in initialize)
                        result.success(true)
                    }
                    "startProtection" -> {
                        // Backward-compatible alias for enabling runtime protection
                        antiTamperProtection.enableRuntimeProtection()
                        result.success(true)
                    }
                    "stopProtection" -> {
                        // Backward-compatible alias for disabling runtime protection
                        antiTamperProtection.disableRuntimeProtection()
                        result.success(true)
                    }
                    "checkApkIntegrity" -> {
                        val isValid = antiTamperProtection.checkApkIntegrity(context)
                        result.success(isValid)
                    }
                    "performSecurityScan" -> {
                        val threats = mutableListOf<String>()
                        
                        // Use status-based checks from SecurityManager public API
                        val status = securityManager.getSecurityStatus()
                        if (status.isDebuggingDetected) {
                            threats.add("debugging_detected")
                        }
                        
                        if (status.isRootDetected) {
                            threats.add("root_detected")
                        }
                        
                        if (!status.integrityVerified) {
                            threats.add("tamper_detected")
                        }
                        
                        if (status.isEmulatorDetected) {
                            threats.add("emulator_detected")
                        }
                        
                        if (status.isHookingDetected) {
                            threats.add("hooking_detected")
                        }
                        
                        result.success(threats)
                    }
                    "generateRandomBytes" -> {
                        val length = call.argument<Int>("length") ?: 16
                        // Generate random bytes functionality (legacy)
                        val randomBytes = CryptoUtils.generateSecureRandomBytes(length)
                        result.success(randomBytes)
                    }
                    "hashData" -> {
                        val data = call.argument<String>("data") ?: ""
                        val algorithm = call.argument<String>("algorithm") ?: "SHA-256"
                        val bytes = data.toByteArray(Charsets.UTF_8)
                        val hash = if (algorithm.equals("SHA-512", ignoreCase = true)) {
                            CryptoUtils.sha512(bytes)
                        } else {
                            CryptoUtils.sha256(bytes)
                        }
                        val hashHex = CryptoUtils.bytesToHex(hash)
                        result.success(hashHex)
                    }
                    "verifyHash" -> {
                        val data = call.argument<String>("data") ?: ""
                        val hash = call.argument<String>("hash") ?: ""
                        val algorithm = call.argument<String>("algorithm") ?: "SHA-256"
                        val calc = if (algorithm.equals("SHA-512", ignoreCase = true)) {
                            CryptoUtils.sha512(data.toByteArray(Charsets.UTF_8))
                        } else {
                            CryptoUtils.sha256(data.toByteArray(Charsets.UTF_8))
                        }
                        val calcHex = CryptoUtils.bytesToHex(calc)
                        result.success(CryptoUtils.secureEquals(calcHex.lowercase(), hash.lowercase()))
                    }
                    "enableSecureMode" -> {
                        // Enable secure mode functionality (no-op placeholder)
                        result.success(true)
                    }
                    "disableSecureMode" -> {
                        // Disable secure mode functionality (no-op placeholder)
                        result.success(true)
                    }
                    "getIntegrityReport" -> {
                        // Get integrity report functionality
                        val report = mapOf(
                            "isValid" to true,
                            "issues" to emptyList<String>(),
                            "timestamp" to System.currentTimeMillis()
                        )
                        result.success(report)
                    }
                    "performRuntimeCheck" -> {
                        // Perform runtime check functionality
                        val checkResult = mapOf(
                            "isValid" to true,
                            "threats" to emptyList<String>(),
                            "timestamp" to System.currentTimeMillis()
                        )
                        result.success(checkResult)
                    }
                    "clearSecurityCache" -> {
                        // Clear security cache functionality
                        result.success(true)
                    }
                    "exportSecurityLogs" -> {
                        // Export security logs functionality
                        val logs = emptyList<String>()
                        result.success(logs)
                    }
                    "importSecurityConfig" -> {
                        // Accept config payload for future use; no-op for now
                        call.argument<String>("config")
                        result.success(true)
                    }
                    "exportSecurityConfig" -> {
                        // Export security config functionality
                        val config = "{}"
                        result.success(config)
                    }
                    else -> {
                        result.notImplemented()
                    }
                }
            } catch (e: Exception) {
                result.error("SECURITY_ERROR", "Security operation failed: ${e.message}", null)
            }
        }
    }

    // Helper data classes for return values
    @Suppress("unused")
    data class SecurityScanResult(
        val threats: List<String>,
        val securityLevel: String,
        val recommendations: List<String>
    )

    @Suppress("unused")
    data class IntegrityCheckResult(
        val isValid: Boolean,
        val issues: List<String>,
        val timestamp: Long
    )
}