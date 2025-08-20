package com.multispace.app.multispace_cloner

import android.graphics.drawable.Drawable

/**
 * Data class representing a cloned app
 * এই class cloned app এর সব metadata store করে
 */
data class ClonedApp(
    val id: Long = 0,
    val originalPackageName: String,
    val clonedPackageName: String,
    val appName: String,
    val clonedAppName: String,
    val iconPath: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis(),
    val dataPath: String,
    val userId: Int = 0,
    val accountInfo: String? = null // JSON string for storing account details
) {
    companion object {
        const val TABLE_NAME = "cloned_apps"
        const val COLUMN_ID = "id"
        const val COLUMN_ORIGINAL_PACKAGE = "original_package_name"
        const val COLUMN_CLONED_PACKAGE = "cloned_package_name"
        const val COLUMN_APP_NAME = "app_name"
        const val COLUMN_CLONED_APP_NAME = "cloned_app_name"
        const val COLUMN_ICON_PATH = "icon_path"
        const val COLUMN_IS_ACTIVE = "is_active"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_LAST_USED = "last_used"
        const val COLUMN_DATA_PATH = "data_path"
        const val COLUMN_USER_ID = "user_id"
        const val COLUMN_ACCOUNT_INFO = "account_info"
    }
}

/**
 * Data class for app usage statistics
 * App এর usage tracking এর জন্য
 */
data class AppUsageStats(
    val id: Long = 0,
    val clonedAppId: Long,
    val sessionStart: Long,
    val sessionEnd: Long? = null,
    val sessionDuration: Long = 0,
    val memoryUsage: Long = 0,
    val cpuUsage: Float = 0f,
    val networkUsage: Long = 0
) {
    companion object {
        const val TABLE_NAME = "app_usage_stats"
        const val COLUMN_ID = "id"
        const val COLUMN_CLONED_APP_ID = "cloned_app_id"
        const val COLUMN_SESSION_START = "session_start"
        const val COLUMN_SESSION_END = "session_end"
        const val COLUMN_SESSION_DURATION = "session_duration"
        const val COLUMN_MEMORY_USAGE = "memory_usage"
        const val COLUMN_CPU_USAGE = "cpu_usage"
        const val COLUMN_NETWORK_USAGE = "network_usage"
    }
}

/**
 * Data class for user accounts in cloned apps
 * Multiple accounts management এর জন্য
 */
data class UserAccount(
    val id: Long = 0,
    val clonedAppId: Long,
    val accountName: String,
    val accountType: String, // facebook, whatsapp, etc.
    val loginData: String, // encrypted login information
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = System.currentTimeMillis()
) {
    companion object {
        const val TABLE_NAME = "user_accounts"
        const val COLUMN_ID = "id"
        const val COLUMN_CLONED_APP_ID = "cloned_app_id"
        const val COLUMN_ACCOUNT_NAME = "account_name"
        const val COLUMN_ACCOUNT_TYPE = "account_type"
        const val COLUMN_LOGIN_DATA = "login_data"
        const val COLUMN_IS_ACTIVE = "is_active"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_LAST_LOGIN = "last_login"
    }
}