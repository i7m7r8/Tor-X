package com.torx.service

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.torx.R
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class VPNService : VpnService() {
    private var vpnServiceJob: Job? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var reader: FileInputStream? = null
    private var writer: FileOutputStream? = null
    private val isRunning = AtomicBoolean(false)
    private val notificationId = 1000

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                setupVPN()
            }
            ACTION_DISCONNECT -> {
                teardownVPN()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun setupVPN() {
        if (isRunning.getAndSet(true)) {
            return
        }

        startForeground(notificationId, createNotification(true))

        vpnServiceJob = scope.launch {
            try {
                val builder = Builder()
                    .setSession("TOR-X")
                    .setMtu(1500)
                    .addAddress("10.0.0.2", 24)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("1.0.0.1")
                    .addRoute("0.0.0.0", 0)

                // Block IPv6 if configured
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.addDisallowedApplication(packageName)
                }

                // Allow all apps by default (can be configured for split tunnel)
                vpnInterface = builder.establish()
                    ?: throw RuntimeException("Failed to establish VPN interface")

                reader = FileInputStream(vpnInterface!!.fileDescriptor)
                writer = FileOutputStream(vpnInterface!!.fileDescriptor)

                val packet = ByteArray(32767)
                while (isRunning.get() && isActive) {
                    try {
                        val length = reader!!.read(packet)
                        if (length > 0) {
                            processPacket(packet, length)
                        }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            Log.e("VPNService", "Packet read error", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VPNService", "VPN setup error", e)
                isRunning.set(false)
            }
        }
    }

    private fun processPacket(packet: ByteArray, length: Int) {
        try {
            val version = (packet[0].toInt() shr 4) and 0x0F
            
            when (version) {
                4 -> handleIPv4Packet(packet, length)
                6 -> handleIPv6Packet(packet, length)
            }
        } catch (e: Exception) {
            Log.d("VPNService", "Packet processing error: ${e.message}")
        }
    }

    private fun handleIPv4Packet(packet: ByteArray, length: Int) {
        if (length < 20) return

        val ihl = (packet[0].toInt() and 0x0F) * 4
        val protocol = packet[9].toInt() and 0xFF
        val srcIp = ByteBuffer.wrap(packet, 12, 4)
        val dstIp = ByteBuffer.wrap(packet, 16, 4)

        when (protocol) {
            6 -> handleTCPPacket(packet, length, ihl) // TCP
            17 -> handleUDPPacket(packet, length, ihl) // UDP
            1 -> handleICMPPacket(packet, length, ihl) // ICMP
        }
    }

    private fun handleIPv6Packet(packet: ByteArray, length: Int) {
        // IPv6 handling
        if (length < 40) return
        Log.d("VPNService", "IPv6 packet received (length: $length)")
    }

    private fun handleTCPPacket(packet: ByteArray, length: Int, ihl: Int) {
        if (length < ihl + 20) return
        Log.d("VPNService", "TCP packet: $length bytes")
    }

    private fun handleUDPPacket(packet: ByteArray, length: Int, ihl: Int) {
        if (length < ihl + 8) return
        
        try {
            val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or
                    (packet[ihl + 1].toInt() and 0xFF)
            val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or
                    (packet[ihl + 3].toInt() and 0xFF)

            // DNS interception (port 53)
            if (dstPort == 53) {
                handleDNSPacket(packet, length, ihl)
            }
        } catch (e: Exception) {
            Log.d("VPNService", "UDP processing error", e)
        }
    }

    private fun handleICMPPacket(packet: ByteArray, length: Int, ihl: Int) {
        if (length < ihl + 8) return
        Log.d("VPNService", "ICMP packet: $length bytes")
    }

    private fun handleDNSPacket(packet: ByteArray, length: Int, ihl: Int) {
        // DNS query handling
        Log.d("VPNService", "DNS query intercepted")
    }

    private fun teardownVPN() {
        isRunning.set(false)
        vpnServiceJob?.cancel()
        
        try {
            reader?.close()
            writer?.close()
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("VPNService", "Error closing VPN interface", e)
        }

        vpnInterface = null
        reader = null
        writer = null
    }

    private fun createNotification(isConnected: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("TOR-X")
            .setContentText(if (isConnected) "VPN Connected" else "VPN Disconnected")
            .setOngoing(isConnected)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

    override fun onDestroy() {
        super.onDestroy()
        teardownVPN()
        scope.cancel()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        const val ACTION_CONNECT = "com.torx.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.torx.ACTION_DISCONNECT"
        const val CHANNEL_ID = "tor_x_vpn"
    }
}
