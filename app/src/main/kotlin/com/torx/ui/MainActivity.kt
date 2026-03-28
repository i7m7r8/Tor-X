package com.torx.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.torx.R
import com.torx.core.VPNViewModel
import com.torx.service.VPNService
import com.torx.theme.TorXTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: VPNViewModel by viewModels()
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            viewModel.connect()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            TorXTheme {
                MainScreen(
                    viewModel = viewModel,
                    onRequestPermissions = { requestVPNPermissions() }
                )
            }
        }

        startService(Intent(this, VPNService::class.java))
    }

    private fun requestVPNPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.CHANGE_NETWORK_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.BIND_VPN_SERVICE
            )
        } else {
            arrayOf(
                Manifest.permission.CHANGE_NETWORK_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.BIND_VPN_SERVICE
            )
        }

        val needsRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needsRequest) {
            permissionLauncher.launch(permissions)
        } else {
            viewModel.connect()
        }
    }
}

@Composable
fun MainScreen(
    viewModel: VPNViewModel,
    onRequestPermissions: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(0xFF0F0F1E))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "TOR-X",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00D4FF)
                    )
                    IconButton(
                        onClick = { showSettings = !showSettings }
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFF00D4FF)
                        )
                    }
                }
            }

            // Connection Status Card
            item {
                ConnectionStatusCard(
                    isConnected = uiState.isConnected,
                    selectedProfile = uiState.selectedProfile,
                    onConnectClick = {
                        if (uiState.isConnected) {
                            viewModel.disconnect()
                        } else {
                            onRequestPermissions()
                        }
                    },
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            // Stats
            item {
                StatsRow(
                    bytesReceived = uiState.bytesReceived,
                    bytesSent = uiState.bytesSent,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            // Quick Settings
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "QUICK SETTINGS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7F8FA3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp)
                )
            }

            item {
                QuickSettingsPanel(viewModel, uiState)
            }

            // SNI Configuration
            item {
                Spacer(modifier = Modifier.height(24.dp))
                ExpandableSection(
                    title = "SNI CONFIGURATION",
                    isExpanded = showAdvanced,
                    onToggle = { showAdvanced = !showAdvanced }
                ) {
                    SNIConfigPanel(viewModel, uiState)
                }
            }

            // Server Selection
            item {
                Spacer(modifier = Modifier.height(24.dp))
                ServerSelectionPanel(viewModel, uiState)
            }

            // Advanced Options
            item {
                Spacer(modifier = Modifier.height(24.dp))
                AdvancedOptionsPanel(viewModel, uiState)
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Settings Panel
        if (showSettings) {
            SettingsPanel(
                viewModel = viewModel,
                onDismiss = { showSettings = false },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun ConnectionStatusCard(
    isConnected: Boolean,
    selectedProfile: String,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connection Toggle Button
            Button(
                onClick = onConnectClick,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected)
                        Color(0xFF00D4FF) else Color(0xFF4A5568)
                ),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isConnected)
                        Icons.Default.VpnLock else Icons.Default.VpnLockOutlined,
                    contentDescription = "Connect",
                    tint = if (isConnected) Color.Black else Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isConnected) "CONNECTED" else "DISCONNECTED",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isConnected) Color(0xFF00D4FF) else Color(0xFF7F8FA3)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = selectedProfile,
                fontSize = 14.sp,
                color = Color(0xFF7F8FA3)
            )
        }
    }
}

@Composable
fun StatsRow(
    bytesReceived: Long,
    bytesSent: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(
            label = "DOWNLOAD",
            value = formatBytes(bytesReceived),
            icon = Icons.Default.Download
        )
        StatItem(
            label = "UPLOAD",
            value = formatBytes(bytesSent),
            icon = Icons.Default.Upload
        )
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    icon: androidx.compose.material.icons.Icons.Filled
) {
    Card(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .weight(1f),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFF00D4FF),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color(0xFF7F8FA3),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun QuickSettingsPanel(viewModel: VPNViewModel, uiState: VPNUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        ToggleSetting(
            label = "Kill Switch",
            description = "Block all traffic if VPN disconnects",
            isChecked = uiState.killSwitchEnabled,
            onToggle = { viewModel.setKillSwitch(it) }
        )
        ToggleSetting(
            label = "Block IPv6",
            description = "Prevent IPv6 leaks",
            isChecked = uiState.blockIpv6,
            onToggle = { viewModel.setBlockIpv6(it) }
        )
        ToggleSetting(
            label = "Split Tunneling",
            description = "Route some apps outside VPN",
            isChecked = uiState.splitTunnelEnabled,
            onToggle = { viewModel.setSplitTunnel(it) }
        )
    }
}

@Composable
fun SNIConfigPanel(viewModel: VPNViewModel, uiState: VPNUiState) {
    var sniServer by remember { mutableStateOf(uiState.sniServer) }
    var sniPort by remember { mutableStateOf(uiState.sniPort.toString()) }
    var sniHostname by remember { mutableStateOf(uiState.sniHostname) }
    var sniEnabled by remember { mutableStateOf(uiState.sniEnabled) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        ToggleSetting(
            label = "Enable SNI",
            description = "Use SNI obfuscation",
            isChecked = sniEnabled,
            onToggle = { 
                sniEnabled = it
                viewModel.updateSNIConfig(
                    server = sniServer,
                    port = sniPort.toIntOrNull() ?: 443,
                    hostname = sniHostname,
                    enabled = it
                )
            }
        )

        TextFieldInput(
            label = "SNI Server",
            value = sniServer,
            onValueChange = { 
                sniServer = it
                viewModel.updateSNIConfig(
                    server = it,
                    port = sniPort.toIntOrNull() ?: 443,
                    hostname = sniHostname,
                    enabled = sniEnabled
                )
            }
        )

        TextFieldInput(
            label = "Port",
            value = sniPort,
            onValueChange = { 
                sniPort = it
                viewModel.updateSNIConfig(
                    server = sniServer,
                    port = it.toIntOrNull() ?: 443,
                    hostname = sniHostname,
                    enabled = sniEnabled
                )
            }
        )

        TextFieldInput(
            label = "SNI Hostname",
            value = sniHostname,
            onValueChange = { 
                sniHostname = it
                viewModel.updateSNIConfig(
                    server = sniServer,
                    port = sniPort.toIntOrNull() ?: 443,
                    hostname = it,
                    enabled = sniEnabled
                )
            }
        )
    }
}

@Composable
fun ServerSelectionPanel(viewModel: VPNViewModel, uiState: VPNUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            "SERVERS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF7F8FA3),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val servers = listOf(
            "Tor Over SNI (US)" to "🇺🇸",
            "Tor Over SNI (EU)" to "🇪🇺",
            "Tor Over SNI (JP)" to "🇯🇵",
            "Tor Only (Default)" to "🧅"
        )

        servers.forEach { (server, flag) ->
            ServerCard(
                name = server,
                flag = flag,
                isSelected = server == uiState.selectedProfile,
                onSelect = { viewModel.selectProfile(server) }
            )
        }
    }
}

@Composable
fun ServerCard(
    name: String,
    flag: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = true) { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                Color(0xFF2A3F5F) else Color(0xFF1A1A2E)
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00D4FF))
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = flag,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    text = name,
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color(0xFF00D4FF)
                )
            }
        }
    }
}

@Composable
fun AdvancedOptionsPanel(viewModel: VPNViewModel, uiState: VPNUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            "ADVANCED",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF7F8FA3),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        TextFieldInput(
            label = "Custom DNS (1.1.1.1 / 8.8.8.8)",
            value = uiState.customDns,
            onValueChange = { viewModel.setCustomDns(it) }
        )

        TextFieldInput(
            label = "Tor Bridge Lines",
            value = uiState.torBridges,
            onValueChange = { viewModel.setTorBridges(it) },
            singleLine = false
        )

        ToggleSetting(
            label = "Use Meek",
            description = "Meek bridge obfuscation",
            isChecked = uiState.useMeek,
            onToggle = { viewModel.setUseMeek(it) }
        )
    }
}

@Composable
fun ToggleSetting(
    label: String,
    description: String,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color(0xFF7F8FA3),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Switch(
                checked = isChecked,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(start = 16.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00D4FF),
                    checkedTrackColor = Color(0xFF2A3F5F)
                )
            )
        }
    }
}

@Composable
fun TextFieldInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, fontSize = 12.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1A1A2E),
                unfocusedContainerColor = Color(0xFF1A1A2E),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color(0xFF7F8FA3),
                focusedLabelColor = Color(0xFF00D4FF),
                unfocusedLabelColor = Color(0xFF7F8FA3),
                focusedIndicatorColor = Color(0xFF00D4FF),
                unfocusedIndicatorColor = Color(0xFF2A3F5F)
            ),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3
        )
    }
}

@Composable
fun ExpandableSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggle() },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A2E)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7F8FA3)
                )
                Icon(
                    imageVector = if (isExpanded)
                        Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = Color(0xFF7F8FA3)
                )
            }
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun SettingsPanel(
    viewModel: VPNViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                "Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close")
            }
        }
    }
}

data class VPNUiState(
    val isConnected: Boolean = false,
    val selectedProfile: String = "Tor Over SNI (US)",
    val bytesReceived: Long = 0L,
    val bytesSent: Long = 0L,
    val killSwitchEnabled: Boolean = true,
    val blockIpv6: Boolean = true,
    val splitTunnelEnabled: Boolean = false,
    val sniEnabled: Boolean = true,
    val sniServer: String = "example.com",
    val sniPort: Int = 443,
    val sniHostname: String = "example.com",
    val customDns: String = "1.1.1.1",
    val torBridges: String = "",
    val useMeek: Boolean = false,
    val connectionDuration: String = "00:00:00"
)

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
