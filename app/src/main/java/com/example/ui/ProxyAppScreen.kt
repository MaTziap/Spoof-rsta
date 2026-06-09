package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.ActivityLogEntity
import com.example.data.entity.SettingsEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyAppScreen(
    viewModel: ProxyViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val logs by viewModel.logsState.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()

    val totalConns by viewModel.liveTotalConnections.collectAsStateWithLifecycle()
    val totalC2S by viewModel.liveC2SBytes.collectAsStateWithLifecycle()
    val totalS2C by viewModel.liveS2CBytes.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(0) } // 0: Dashboard, 1: History, 2: Settings

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianBlack)
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "RSTA Spoof",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            color = CyberTeal,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "v3.5.0 • BYPASS ACTIVE",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.5.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SurfaceSlate)
                            .clickable { currentTab = 2 },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = SurfaceSlate)
                NavigationBar(
                    containerColor = DeepSlate,
                    tonalElevation = 0.dp,
                    modifier = Modifier.height(80.dp)
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Dashboard") },
                        label = { Text("داشبورد", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberTeal,
                            selectedTextColor = CyberTeal,
                            indicatorColor = Color(0xFF3D4758),
                            unselectedIconColor = TextMuted.copy(alpha = 0.6f),
                            unselectedTextColor = TextMuted.copy(alpha = 0.6f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.List, contentDescription = "History") },
                        label = { Text("فعالیت‌ها", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberTeal,
                            selectedTextColor = CyberTeal,
                            indicatorColor = Color(0xFF3D4758),
                            unselectedIconColor = TextMuted.copy(alpha = 0.6f),
                            unselectedTextColor = TextMuted.copy(alpha = 0.6f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("تنظیمات", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberTeal,
                            selectedTextColor = CyberTeal,
                            indicatorColor = Color(0xFF3D4758),
                            unselectedIconColor = TextMuted.copy(alpha = 0.6f),
                            unselectedTextColor = TextMuted.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        },
        containerColor = ObsidianBlack
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> DashboardTab(
                    isRunning = isRunning,
                    settings = settings,
                    totalConns = totalConns,
                    totalC2S = totalC2S,
                    totalS2C = totalS2C,
                    logs = logs,
                    onToggleService = { viewModel.toggleProxyService() }
                )
                1 -> HistoryTab(
                    logs = logs,
                    onClearLogs = { viewModel.clearHistory() }
                )
                2 -> SettingsTab(
                    settings = settings,
                    onSaveSettings = { updated ->
                        viewModel.saveSettings(
                            listenHost = updated.listenHost,
                            listenPort = updated.listenPort,
                            connectIp = updated.connectIp,
                            connectPort = updated.connectPort,
                            fakeSni = updated.fakeSni,
                            bypassMethod = updated.bypassMethod,
                            fragmentStrategy = updated.fragmentStrategy,
                            fragmentDelay = updated.fragmentDelay,
                            useTtlTrick = updated.useTtlTrick
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun DashboardTab(
    isRunning: Boolean,
    settings: SettingsEntity,
    totalConns: Long,
    totalC2S: Long,
    totalS2C: Long,
    logs: List<ActivityLogEntity>,
    onToggleService: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DeepSlate),
                shape = RoundedCornerShape(32.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // Top Right Action State Badge
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) SuccessEmerald else TextMuted)
                        )
                        Text(
                            text = if (isRunning) "ACTIVE" else "STOPPED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) SuccessEmerald else TextMuted,
                            letterSpacing = 1.sp
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "وضعیت پروکسی",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        // Sophisticated Connection Visualization Loop Ring
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(128.dp)
                                .clip(CircleShape)
                                .background(ObsidianBlack.copy(alpha = 0.5f))
                                .clickable(onClick = onToggleService)
                                .testTag("toggle_proxy_button")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(116.dp)
                                    .clip(CircleShape)
                                    .background(ObsidianBlack)
                                    .border(4.dp, Color(0xFF3D4758), CircleShape)
                                    .padding(8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isRunning) SuccessEmerald else CyberTeal,
                                    modifier = Modifier.fillMaxSize(),
                                    tonalElevation = 8.dp
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = if (isRunning) Icons.Filled.Check else Icons.Filled.PlayArrow,
                                            contentDescription = "Toggle",
                                            tint = Color(0xFF00315D),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = if (isRunning) "فعال / RUNNING" else "غیرفعال / STOPPED",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) SuccessEmerald else TextLight
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = SurfaceSlate, modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "آدرس شنود (LOCAL)",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${settings.listenHost}:${settings.listenPort}",
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = CyberTeal,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "سرور مقصد (REMOTE)",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${settings.connectIp}:${settings.connectPort}",
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = CyberTeal,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live stats grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "مجموع اتصالات",
                    subtitle = "Total Conns",
                    value = totalConns.toString(),
                    icon = Icons.Filled.Share,
                    accentColor = CyberBlue,
                    modifier = Modifier.weight(1f)
                )

                val successRate = remember(logs) {
                    val completed = logs.filter { it.status == "closed" || it.status == "active" }
                    val ok = completed.count { it.serverReplied }
                    if (completed.isNotEmpty()) (ok * 100 / completed.size) else 100
                }

                StatCard(
                    title = "نرخ موفقیت",
                    subtitle = "Spoof Success",
                    value = "$successRate%",
                    icon = Icons.Filled.Check,
                    accentColor = SuccessEmerald,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Traffic metrics
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DeepSlate),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceSlate)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    TrafficColumn(
                        title = "ارسال (C→S)",
                        bytes = totalC2S,
                        icon = Icons.Filled.KeyboardArrowUp,
                        iconTint = CyberTeal
                    )
                    Box(
                        modifier = Modifier
                            .height(50.dp)
                            .width(1.dp)
                            .background(SurfaceSlate)
                            .align(Alignment.CenterVertically)
                    )
                    TrafficColumn(
                        title = "دریافت (S→C)",
                        bytes = totalS2C,
                        icon = Icons.Filled.KeyboardArrowDown,
                        iconTint = CyberBlue
                    )
                }
            }
        }

        // Quick active conns status
        item {
            Text(
                text = "اتصالات اخیر در حال شنود (Live Connections)",
                fontSize = 14.sp,
                color = TextLight,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
        }

        val activeLogs = logs.take(6)
        if (activeLogs.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceSlate.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "هیچ اتصالی رصد نشده است. پروکسی را فعال کنید.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(activeLogs) { log ->
                ConnectionRow(logItem = log)
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    subtitle: String,
    value: String,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceSlate),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = TextMuted.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextLight,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun TrafficColumn(
    title: String,
    bytes: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = title, fontSize = 11.sp, color = TextMuted)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatBytes(bytes),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextLight,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ConnectionRow(logItem: ActivityLogEntity) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (logItem.status) {
                "blocked" -> BlockedCrimson.copy(alpha = 0.08f)
                "error" -> BlockedCrimson.copy(alpha = 0.08f)
                "connecting" -> WarningAmber.copy(alpha = 0.08f)
                else -> SurfaceSlate.copy(alpha = 0.5f)
            }
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("connection_log_row")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusText = when (logItem.status) {
                        "connecting" -> "در حال اتصال"
                        "active" -> "فعال"
                        "blocked" -> "مسدود شده"
                        "closed" -> "پایان یافته"
                        "error" -> "خطا"
                        else -> logItem.status
                    }
                    val statusColor = when (logItem.status) {
                        "connecting" -> WarningAmber
                        "active" -> SuccessEmerald
                        "blocked" -> BlockedCrimson
                        "error" -> BlockedCrimson
                        else -> TextMuted
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${logItem.connId} - $statusText",
                        fontSize = 11.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(logItem.timestamp)),
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        text = "SNI برداشتی: ${logItem.realSni.ifBlank { "(پروتکل غیر TLS یا نامشخص)" }}",
                        fontSize = 12.sp,
                        color = if (logItem.realSni.isNotBlank()) CyberTeal else TextMuted
                    )
                    Text(
                        text = "سرور مقصد: ${logItem.serverAddr}",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "↑${formatBytes(logItem.bytesC2S)}  ↓${formatBytes(logItem.bytesS2C)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextLight,
                        fontFamily = FontFamily.Monospace
                    )
                    if (logItem.errorMessage != null) {
                        Text(
                            text = logItem.errorMessage,
                            fontSize = 9.sp,
                            color = BlockedCrimson,
                            maxLines = 1
                        )
                    } else if (logItem.serverReplied) {
                        Text(
                            text = "دور زدن فعال ✓",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessEmerald
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTab(
    logs: List<ActivityLogEntity>,
    onClearLogs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "تاریخچه رصد فعالیت‌ها",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextLight
                )
                Text(
                    text = "نمایش لیست کامل اتصالات روی پروکسی",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            
            Button(
                onClick = onClearLogs,
                colors = ButtonDefaults.buttonColors(containerColor = BlockedCrimson.copy(alpha = 0.2f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("clear_logs_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear",
                    tint = BlockedCrimson,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("پاک‌سازی هاب", color = BlockedCrimson, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (logs.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.List,
                        contentDescription = null,
                        tint = TextMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "هیچ لاگی ثبت نشده است.",
                        fontSize = 14.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "پس از تراکنش‌های موفق یا ناموفق، اتصالات در اینجا ذخیره می‌شوند.",
                        fontSize = 11.sp,
                        color = TextMuted.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs) { log ->
                    ConnectionRow(logItem = log)
                }
            }
        }
    }
}

@Composable
fun SettingsTab(
    settings: SettingsEntity,
    onSaveSettings: (SettingsEntity) -> Unit
) {
    var listenHost by remember(settings) { mutableStateOf(settings.listenHost) }
    var listenPort by remember(settings) { mutableStateOf(settings.listenPort.toString()) }
    var connectIp by remember(settings) { mutableStateOf(settings.connectIp) }
    var connectPort by remember(settings) { mutableStateOf(settings.connectPort.toString()) }
    var fakeSni by remember(settings) { mutableStateOf(settings.fakeSni) }
    var bypassMethod by remember(settings) { mutableStateOf(settings.bypassMethod) }
    var fragmentStrategy by remember(settings) { mutableStateOf(settings.fragmentStrategy) }
    var fragmentDelay by remember(settings) { mutableStateOf(settings.fragmentDelay.toString()) }
    var useTtlTrick by remember(settings) { mutableStateOf(settings.useTtlTrick) }

    var expandedMethod by remember { mutableStateOf(false) }
    var expandedFrag by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "پیکربندی پروکسی",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextLight
            )
            Text(
                text = "ویرایش مقادیر کلیدی عملکرد پروکسی. مقادیر تغییر یافته پس از ذخیره دائمی خواهند شد.",
                fontSize = 12.sp,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Section 1: Network Core settings
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DeepSlate),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceSlate)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "آدرس‌دهی و پورت‌ها (Network Core)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberTeal
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = listenHost,
                            onValueChange = { listenHost = it },
                            label = { Text("آی‌پی محلی") },
                            placeholder = { Text("127.0.0.1") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberTeal,
                                focusedLabelColor = CyberTeal,
                                unfocusedBorderColor = SurfaceSlate
                            ),
                            modifier = Modifier.weight(1.5f).testTag("listen_host_input")
                        )

                        OutlinedTextField(
                            value = listenPort,
                            onValueChange = { listenPort = it },
                            label = { Text("پورت محلی") },
                            placeholder = { Text("40443") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberTeal,
                                focusedLabelColor = CyberTeal,
                                unfocusedBorderColor = SurfaceSlate
                            ),
                            modifier = Modifier.weight(1f).testTag("listen_port_input")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = connectIp,
                            onValueChange = { connectIp = it },
                            label = { Text("سرور مقصد / Server IP") },
                            placeholder = { Text("104.18.38.202") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberTeal,
                                focusedLabelColor = CyberTeal,
                                unfocusedBorderColor = SurfaceSlate
                            ),
                            modifier = Modifier.weight(1.5f).testTag("connect_ip_input")
                        )

                        OutlinedTextField(
                            value = connectPort,
                            onValueChange = { connectPort = it },
                            label = { Text("پورت مقصد") },
                            placeholder = { Text("443") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberTeal,
                                focusedLabelColor = CyberTeal,
                                unfocusedBorderColor = SurfaceSlate
                            ),
                            modifier = Modifier.weight(1f).testTag("connect_port_input")
                        )
                    }
                }
            }
        }

        // Section 2: Spoof Bypass details
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DeepSlate),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceSlate)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "تنظیمات دور زدن DPI (Spoof Methods)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberBlue
                    )

                    OutlinedTextField(
                        value = fakeSni,
                        onValueChange = { fakeSni = it },
                        label = { Text("نام دامنه فریب دهنده / Fake SNI") },
                        placeholder = { Text("cdnjs.cloudflare.com") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberTeal,
                            focusedLabelColor = CyberTeal,
                            unfocusedBorderColor = SurfaceSlate
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("fake_sni_input")
                    )

                    // Bypass Method Selector
                    Box {
                        OutlinedTextField(
                            value = when (bypassMethod.lowercase()) {
                                "fragment" -> "تکه‌تکه کردن (fragment)"
                                "fake_sni" -> "ارسال دامنه فیک (fake_sni)"
                                "combined" -> "ترکیبی فیک و تکه (combined)"
                                else -> bypassMethod
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("متد دور زدن DPI") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberTeal,
                                focusedLabelColor = CyberTeal,
                                unfocusedBorderColor = SurfaceSlate
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedMethod = true }
                                .testTag("bypass_method_selector")
                        )
                        DropdownMenu(
                            expanded = expandedMethod,
                            onDismissRequest = { expandedMethod = false },
                            modifier = Modifier.background(DeepSlate)
                        ) {
                            DropdownMenuItem(
                                text = { Text("تکه‌تکه کردن (fragment)", color = TextLight) },
                                onClick = {
                                    bypassMethod = "fragment"
                                    expandedMethod = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("دامنه فریب‌دهنده (fake_sni)", color = TextLight) },
                                onClick = {
                                    bypassMethod = "fake_sni"
                                    expandedMethod = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("ترکیبی - بهترین نتیجه (combined)", color = TextLight) },
                                onClick = {
                                    bypassMethod = "combined"
                                    expandedMethod = false
                                }
                            )
                        }
                    }

                    // Fragment Strategy Selector
                    Box {
                        OutlinedTextField(
                            value = when (fragmentStrategy.lowercase()) {
                                "sni_split" -> "شکست روی SNI"
                                "half" -> "شکست در وسط کلید"
                                "multi" -> "تکه های کوچک 24 بایتی"
                                "tls_record_frag" -> "شکستن رکورد TLS"
                                "none" -> "بدون تکه کردن"
                                else -> fragmentStrategy
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("استراتژی تکه‌تکه کردن (Fragment Strategy)") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberTeal,
                                focusedLabelColor = CyberTeal,
                                unfocusedBorderColor = SurfaceSlate
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedFrag = true }
                                .testTag("fragment_strategy_selector")
                        )
                        DropdownMenu(
                            expanded = expandedFrag,
                            onDismissRequest = { expandedFrag = false },
                            modifier = Modifier.background(DeepSlate)
                        ) {
                            DropdownMenuItem(
                                text = { Text("شکست روی دامنه (sni_split) - پیشنهادی", color = TextLight) },
                                onClick = {
                                    fragmentStrategy = "sni_split"
                                    expandedFrag = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("شکست در وسط (half)", color = TextLight) },
                                onClick = {
                                    fragmentStrategy = "half"
                                    expandedFrag = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("تکه‌های فوق کوچک (multi)", color = TextLight) },
                                onClick = {
                                    fragmentStrategy = "multi"
                                    expandedFrag = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("در سطح رکورد (tls_record_frag)", color = TextLight) },
                                onClick = {
                                    fragmentStrategy = "tls_record_frag"
                                    expandedFrag = false
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = fragmentDelay,
                            onValueChange = { fragmentDelay = it },
                            label = { Text("تاخیر ارسال تکه‌ها (ثانیه)") },
                            placeholder = { Text("0.1") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberTeal,
                                focusedLabelColor = CyberTeal,
                                unfocusedBorderColor = SurfaceSlate
                            ),
                            modifier = Modifier.weight(1f).testTag("fragment_delay_input")
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(
                                checked = useTtlTrick,
                                onCheckedChange = { useTtlTrick = it },
                                colors = CheckboxDefaults.colors(checkedColor = CyberTeal),
                                modifier = Modifier.testTag("ttl_trick_checkbox")
                            )
                            Text(
                                text = "تکنیک تله (TTL/Probe)",
                                fontSize = 12.sp,
                                color = TextLight
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Save actions
        item {
            Button(
                onClick = {
                    val listenPortInt = listenPort.toIntOrNull() ?: settings.listenPort
                    val connectPortInt = connectPort.toIntOrNull() ?: settings.connectPort
                    val delayDouble = fragmentDelay.toDoubleOrNull() ?: settings.fragmentDelay

                    onSaveSettings(
                        SettingsEntity(
                            id = 1,
                            listenHost = listenHost,
                            listenPort = listenPortInt,
                            connectIp = connectIp,
                            connectPort = connectPortInt,
                            fakeSni = fakeSni,
                            bypassMethod = bypassMethod,
                            fragmentStrategy = fragmentStrategy,
                            fragmentDelay = delayDouble,
                            useTtlTrick = useTtlTrick
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberTeal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Save",
                    tint = ObsidianBlack
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ذخیره تغییرات و اعمال (Save & Sync)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ObsidianBlack
                )
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    }
}
