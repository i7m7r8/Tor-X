package com.torx.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torx.ui.VPNUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.delay

class VPNViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(VPNUiState())
    val uiState: StateFlow<VPNUiState> = _uiState.asStateFlow()

    private var vpnCore: VPNCore? = null
    private var connectionStartTime: Long = 0L
    private var isConnectionActive = false

    init {
        viewModelScope.launch {
            vpnCore = VPNCore()
            startStatsUpdater()
        }
    }

    fun connect() {
        viewModelScope.launch {
            try {
                vpnCore?.let {
                    if (it.connect()) {
                        connectionStartTime = System.currentTimeMillis()
                        isConnectionActive = true
                        _uiState.value = _uiState.value.copy(isConnected = true)
                        Log.i("VPN", "Connected successfully")
                    }
                }
            } catch (e: Exception) {
                Log.e("VPN", "Connection failed", e)
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            try {
                vpnCore?.let {
                    if (it.disconnect()) {
                        isConnectionActive = false
                        _uiState.value = _uiState.value.copy(
                            isConnected = false,
                            bytesReceived = 0L,
                            bytesSent = 0L
                        )
                        Log.i("VPN", "Disconnected successfully")
                    }
                }
            } catch (e: Exception) {
                Log.e("VPN", "Disconnection failed", e)
            }
        }
    }

    fun selectProfile(profile: String) {
        _uiState.value = _uiState.value.copy(selectedProfile = profile)
    }

    fun setKillSwitch(enabled: Boolean) {
        viewModelScope.launch {
            vpnCore?.setKillSwitch(enabled)
            _uiState.value = _uiState.value.copy(killSwitchEnabled = enabled)
        }
    }

    fun setBlockIpv6(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(blockIpv6 = enabled)
    }

    fun setSplitTunnel(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(splitTunnelEnabled = enabled)
    }

    fun updateSNIConfig(
        server: String,
        port: Int,
        hostname: String,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            vpnCore?.setSNIServer(server, port, hostname)
            _uiState.value = _uiState.value.copy(
                sniServer = server,
                sniPort = port,
                sniHostname = hostname,
                sniEnabled = enabled
            )
        }
    }

    fun setCustomDns(dns: String) {
        _uiState.value = _uiState.value.copy(customDns = dns)
    }

    fun setTorBridges(bridges: String) {
        viewModelScope.launch {
            vpnCore?.setTorBridges(bridges, false)
            _uiState.value = _uiState.value.copy(torBridges = bridges)
        }
    }

    fun setUseMeek(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(useMeek = enabled)
    }

    private fun startStatsUpdater() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (isConnectionActive) {
                    vpnCore?.let { core ->
                        try {
                            val stats = core.getStats()
                            val duration = (System.currentTimeMillis() - connectionStartTime) / 1000
                            val hours = duration / 3600
                            val minutes = (duration % 3600) / 60
                            val seconds = duration % 60
                            val durationStr = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                            _uiState.value = _uiState.value.copy(
                                bytesReceived = stats.first,
                                bytesSent = stats.second,
                                connectionDuration = durationStr
                            )
                        } catch (e: Exception) {
                            Log.e("VPN", "Stats update failed", e)
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}

class VPNCore {
    private var nativeHandle: Long = 0

    init {
        System.loadLibrary("torxjni")
        nativeHandle = initNative()
    }

    external fun initNative(): Long
    external fun connect(handle: Long): Boolean
    external fun disconnect(handle: Long): Boolean
    external fun isConnected(handle: Long): Boolean
    external fun getStats(handle: Long): Pair<Long, Long>
    external fun setSNIServer(handle: Long, server: String, port: Int, hostname: String): Boolean
    external fun setTorBridges(handle: Long, bridges: String, useMeek: Boolean): Boolean
    external fun setKillSwitch(handle: Long, enabled: Boolean): Boolean
    external fun setSplitTunnel(handle: Long, enabled: Boolean, apps: String): Boolean

    fun connect(): Boolean = connect(nativeHandle)
    fun disconnect(): Boolean = disconnect(nativeHandle)
    fun isConnected(): Boolean = isConnected(nativeHandle)
    fun getStats(): Pair<Long, Long> = getStats(nativeHandle)
    fun setSNIServer(server: String, port: Int, hostname: String): Boolean =
        setSNIServer(nativeHandle, server, port, hostname)
    fun setTorBridges(bridges: String, useMeek: Boolean): Boolean =
        setTorBridges(nativeHandle, bridges, useMeek)
    fun setKillSwitch(enabled: Boolean): Boolean = setKillSwitch(nativeHandle, enabled)
    fun setSplitTunnel(enabled: Boolean, apps: String): Boolean =
        setSplitTunnel(nativeHandle, enabled, apps)
}
