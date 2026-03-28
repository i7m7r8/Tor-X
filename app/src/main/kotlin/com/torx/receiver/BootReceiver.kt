package com.torx.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import com.torx.service.VPNService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            Log.i("BootReceiver", "Boot completed, checking auto-start preference")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vpnIntent = Intent(context, VPNService::class.java)
                    .setAction(VPNService.ACTION_CONNECT)
                context.startForegroundService(vpnIntent)
            }
        }
    }
}
