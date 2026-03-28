package com.torx.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.text.SimpleDateFormat
import java.util.*

object CrashLogger {
    private const val PREFS_NAME = "crash_logs"
    private const val MAX_CRASHES = 50

    fun logCrash(context: Context, throwable: Throwable) {
        try {
            val prefs = getEncryptedPrefs(context)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val crashLog = buildCrashLog(throwable, timestamp)
            
            val crashCount = prefs.getInt("crash_count", 0)
            prefs.edit()
                .putString("crash_$crashCount", crashLog)
                .putInt("crash_count", (crashCount + 1) % MAX_CRASHES)
                .apply()
            
            Log.e("CrashLogger", crashLog)
        } catch (e: Exception) {
            Log.e("CrashLogger", "Failed to log crash", e)
        }
    }

    fun getCrashLogs(context: Context): List<String> {
        return try {
            val prefs = getEncryptedPrefs(context)
            val count = prefs.getInt("crash_count", 0)
            (0 until count).mapNotNull { prefs.getString("crash_$it", null) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun buildCrashLog(throwable: Throwable, timestamp: String): String {
        val sb = StringBuilder()
        sb.append("[$timestamp]\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        sb.append("OS: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        sb.append("Exception: ${throwable.javaClass.simpleName}\n")
        sb.append("Message: ${throwable.message}\n")
        sb.append("Stack Trace:\n")
        throwable.stackTrace.forEach { sb.append("  at $it\n") }
        return sb.toString()
    }
}
