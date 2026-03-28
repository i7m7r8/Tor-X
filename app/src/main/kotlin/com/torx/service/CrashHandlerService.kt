package com.torx.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.work.*
import com.torx.utils.CrashLogger
import java.util.concurrent.TimeUnit

class CrashHandlerService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val throwable = intent?.getSerializableExtra("exception") as? Throwable
        throwable?.let {
            CrashLogger.logCrash(this, it)
            scheduleCrashNotification()
        }
        return START_NOT_STICKY
    }

    private fun scheduleCrashNotification() {
        val crashWork = OneTimeWorkRequestBuilder<CrashWorker>()
            .setInitialDelay(1, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "crash_notification",
            ExistingWorkPolicy.KEEP,
            crashWork
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class CrashWorker(context: android.content.Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            Log.i("CrashWorker", "Crash logged and notification sent")
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
