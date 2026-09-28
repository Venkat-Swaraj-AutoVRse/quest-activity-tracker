package com.autovrse.quest.activitytracker.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.wifi.WifiManager
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autovrse.quest.activitytracker.model.AppUsageInfo
import com.autovrse.quest.activitytracker.model.TimeRange
import com.autovrse.quest.activitytracker.model.UsageTrackerHelper
import com.autovrse.quest.activitytracker.service.UsageServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.NetworkInterface
import java.util.Collections

private const val TAG = "ActivityTracker"

// Curated Sleek HSL-styled dark theme colors
val BackgroundColor = Color(0xFF10131E)
val SurfaceColor = Color(0xFF181C2A)
val SurfaceBorderColor = Color(0xFF282E42)
val TextPrimary = Color(0xFFF5F6FA)
val TextSecondary = Color(0xFF8A95A5)
val AccentPurple = Color(0xFF7C4DFF)
val AccentCyan = Color(0xFF00E5FF)
val AccentPink = Color(0xFFFF4081)
val DangerColor = Color(0xFFFF5252)

@Composable
fun MainAppScreen(
    hasPermission: Boolean,
    onCheckPermission: () -> Unit
) {
    val context = LocalContext.current
    var currentRange by remember { mutableStateOf(TimeRange.TODAY) }
    var selectedScreen by remember { mutableStateOf("dashboard") } // dashboard or limits
    var usageStats by remember { mutableStateOf(emptyList<AppUsageInfo>()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Reload function
    val reloadStats = {
        isLoading = true
        Log.d(TAG, "reloadStats: starting load for range=$currentRange")
        scope.launch {
            try {
                delay(300) // brief delay for smooth transition
                val stats = withContext(Dispatchers.IO) {
                    UsageTrackerHelper.getUsageStats(context, currentRange)
                }
                Log.d(TAG, "reloadStats: loaded ${stats.size} apps")
                usageStats = stats
            } catch (e: Exception) {
                Log.e(TAG, "reloadStats: FAILED", e)
                usageStats = emptyList()
            } finally {
                isLoading = false
                Log.d(TAG, "reloadStats: isLoading set to false")
            }
        }
    }

    // Trigger reload when range or screen changes
    LaunchedEffect(currentRange, selectedScreen, hasPermission) {
        Log.d(TAG, "LaunchedEffect: hasPermission=$hasPermission, range=$currentRange, screen=$selectedScreen")
        if (hasPermission) {
            reloadStats()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        if (!hasPermission) {
            PermissionOnboarding(onCheckPermission)
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                // Sidebar Navigation
                Sidebar(
                    selectedScreen = selectedScreen,
                    onScreenSelected = { selectedScreen = it },
                    currentRange = currentRange,
                    onRangeSelected = { currentRange = it }
                )

                // Main Content Area
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .padding(24.dp)
                ) {
                    AnimatedContent(
                        targetState = selectedScreen,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "content_transition"
                    ) { screen ->
                        when (screen) {
                            "dashboard" -> DashboardScreen(
                                usageStats = usageStats,
                                timeRange = currentRange,
                                isLoading = isLoading,
                                onRefresh = { reloadStats() }
                            )
                            "limits" -> ScreenLimitsScreen(
                                usageStats = usageStats,
                                onLimitsChanged = { reloadStats() }
                            )
                            "web" -> WebCompanionScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Sidebar(
    selectedScreen: String,
    onScreenSelected: (String) -> Unit,
    currentRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(260.dp)
            .background(SurfaceColor)
            .padding(24.dp)
    ) {
        // App Title/Logo
        Text(
            text = "HORIZON",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AccentCyan,
            letterSpacing = 2.sp
        )
        Text(
            text = "Activity Tracker",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Divider(color = SurfaceBorderColor, modifier = Modifier.padding(bottom = 24.dp))

        // Navigation Menu
        SidebarMenuItem(
            label = "Dashboard",
            iconText = "📊",
            isSelected = selectedScreen == "dashboard",
            onClick = { onScreenSelected("dashboard") }
        )
        SidebarMenuItem(
            label = "Screen Limits",
            iconText = "⏱️",
            isSelected = selectedScreen == "limits",
            onClick = { onScreenSelected("limits") }
        )
        SidebarMenuItem(
            label = "Web Companion",
            iconText = "🌐",
            isSelected = selectedScreen == "web",
            onClick = { onScreenSelected("web") }
        )

        Spacer(modifier = Modifier.weight(1f))

        // Filter / Time Range Selection (Only show if dashboard selected)
        if (selectedScreen == "dashboard") {
            Divider(color = SurfaceBorderColor, modifier = Modifier.padding(bottom = 16.dp))
            Text(
                text = "TIME RANGE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            TimeRangeButton("Today", isSelected = currentRange == TimeRange.TODAY) {
                onRangeSelected(TimeRange.TODAY)
            }
            TimeRangeButton("This Week", isSelected = currentRange == TimeRange.WEEK) {
                onRangeSelected(TimeRange.WEEK)
            }
            TimeRangeButton("This Month", isSelected = currentRange == TimeRange.MONTH) {
                onRangeSelected(TimeRange.MONTH)
            }
        }
    }
}

@Composable
fun SidebarMenuItem(
    label: String,
    iconText: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AccentPurple.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(iconText, fontSize = 18.sp, modifier = Modifier.padding(end = 12.dp))
        Text(
            text = label,
            color = if (isSelected) TextPrimary else TextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 15.sp
        )
    }
}

@Composable
fun TimeRangeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AccentCyan.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) AccentCyan else TextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

@Composable
fun DashboardScreen(
    usageStats: List<AppUsageInfo>,
    timeRange: TimeRange,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top Summary Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = when (timeRange) {
                        TimeRange.TODAY -> "Today's Activity"
                        TimeRange.WEEK -> "Weekly Activity"
                        TimeRange.MONTH -> "Monthly Activity"
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Historical app screen time and play details",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }

            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Refresh", color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan)
            }
        } else if (usageStats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceColor)
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎮", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No usage data found for this range.",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Play some games or launch virtual environments on your Quest, then refresh!",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Screen Time Summary Info
            val totalPlaytime = usageStats.sumOf { it.totalTimeInForeground }
            val activeApps = usageStats.size
            val mostUsedApp = usageStats.firstOrNull()?.appName ?: "None"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SummaryCard(
                    title = "Total Screen Time",
                    value = formatDuration(totalPlaytime),
                    accentColor = AccentPurple,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    title = "Active Apps",
                    value = "$activeApps Apps",
                    accentColor = AccentCyan,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    title = "Most Used App",
                    value = mostUsedApp,
                    accentColor = AccentPink,
                    modifier = Modifier.weight(1.2f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Dashboard Split View: Chart + App List
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Chart Panel (Left side)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceColor)
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Time Distribution",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Left
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        UsageDistributionChart(usageStats = usageStats)
                    }
                }

                // Scrollable App list (Right side)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1.5f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceColor)
                        .padding(20.dp)
                ) {
                    Text(
                        text = "App Leaderboard",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(usageStats.take(15)) { info ->
                            AppLeaderboardItem(info = info, totalPlaytime = totalPlaytime)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceColor)
            .padding(16.dp)
    ) {
        Column {
            Text(title, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Small accent bar at the bottom
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(accentColor)
            )
        }
    }
}

@Composable
fun UsageDistributionChart(usageStats: List<AppUsageInfo>) {
    val totalTime = usageStats.sumOf { it.totalTimeInForeground }
    if (totalTime == 0L) return

    val topApps = usageStats.take(3)
    val otherTime = totalTime - topApps.sumOf { it.totalTimeInForeground }

    // Colors mapping
    val chartColors = listOf(AccentPurple, AccentCyan, AccentPink, TextSecondary)

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Circular ring chart drawn via canvas
        Canvas(modifier = Modifier.size(160.dp)) {
            val strokeWidth = 18.dp.toPx()

            var startAngle = -90f

            topApps.forEachIndexed { index, app ->
                val sweepAngle = (app.totalTimeInForeground.toFloat() / totalTime) * 360f
                drawArc(
                    color = chartColors[index],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                startAngle += sweepAngle
            }

            if (otherTime > 0) {
                val sweepAngle = (otherTime.toFloat() / totalTime) * 360f
                drawArc(
                    color = chartColors[3],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Legend
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            topApps.forEachIndexed { index, app ->
                val pct = (app.totalTimeInForeground.toFloat() / totalTime) * 100f
                LegendItem(
                    color = chartColors[index],
                    name = app.appName,
                    pct = String.format("%.1f%%", pct)
                )
            }

            if (otherTime > 0) {
                val pct = (otherTime.toFloat() / totalTime) * 100f
                LegendItem(
                    color = chartColors[3],
                    name = "Others",
                    pct = String.format("%.1f%%", pct)
                )
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, name: String, pct: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = name,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(text = pct, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
fun AppLeaderboardItem(info: AppUsageInfo, totalPlaytime: Long) {
    val context = LocalContext.current
    val limitMinutes = remember(info.packageName) { getLimit(context, info.packageName) }
    val playtimeMinutes = (info.totalTimeInForeground / 1000 / 60).toInt()
    val isLimitExceeded = limitMinutes in 1..playtimeMinutes

    val progress = if (totalPlaytime > 0) info.totalTimeInForeground.toFloat() / totalPlaytime else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BackgroundColor.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Icon or Placeholder
        if (info.icon != null) {
            androidx.compose.foundation.Image(
                bitmap = info.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentPurple.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = info.appName.take(1).uppercase(),
                    color = AccentPurple,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Name, Playtime, Launch counts
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = info.appName,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (isLimitExceeded) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DangerColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Limit Exceeded!", color = DangerColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = formatDuration(info.totalTimeInForeground),
                    color = AccentCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Play progress bar and launches
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Mini Play ratio bar
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape),
                    color = if (isLimitExceeded) DangerColor else AccentPurple,
                    trackColor = SurfaceBorderColor
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "${info.launchCount} Launches",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun ScreenLimitsScreen(
    usageStats: List<AppUsageInfo>,
    onLimitsChanged: () -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Screen Time Limits",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Configure healthy playtime limits for VR apps",
            color = TextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (usageStats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceColor),
                contentAlignment = Alignment.Center
            ) {
                Text("Run apps first to configure limits.", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceColor)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(usageStats) { info ->
                    LimitConfigureItem(
                        info = info,
                        onLimitSet = { minutes ->
                            saveLimit(context, info.packageName, minutes)
                            onLimitsChanged()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LimitConfigureItem(
    info: AppUsageInfo,
    onLimitSet: (Int) -> Unit
) {
    val context = LocalContext.current
    var limitMinutes by remember(info.packageName) {
        mutableStateOf(getLimit(context, info.packageName))
    }

    var isEnabled by remember(info.packageName) {
        mutableStateOf(limitMinutes > 0)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BackgroundColor.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon
        if (info.icon != null) {
            androidx.compose.foundation.Image(
                bitmap = info.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentPurple.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(info.appName.take(1).uppercase(), color = AccentPurple, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Info & Slider
        Column(modifier = Modifier.weight(1f)) {
            Text(info.appName, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            if (isEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Slider(
                        value = limitMinutes.toFloat(),
                        onValueChange = { limitMinutes = it.toInt() },
                        onValueChangeFinished = { onLimitSet(limitMinutes) },
                        valueRange = 15f..240f,
                        steps = 14, // 15 min steps up to 4 hours
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCyan,
                            activeTrackColor = AccentCyan,
                            inactiveTrackColor = SurfaceBorderColor
                        )
                    )
                    Text(
                        text = "${limitMinutes} min",
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.width(60.dp),
                        textAlign = TextAlign.End
                    )
                }
            } else {
                Text("No limit active", color = TextSecondary, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Limit Toggle Switch
        Switch(
            checked = isEnabled,
            onCheckedChange = { checked ->
                isEnabled = checked
                if (checked) {
                    limitMinutes = 60 // default 1 hour
                    onLimitSet(60)
                } else {
                    limitMinutes = 0
                    onLimitSet(0)
                }
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = AccentCyan,
                checkedTrackColor = AccentCyan.copy(alpha = 0.5f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceBorderColor
            )
        )
    }
}

@Composable
fun PermissionOnboarding(onCheckPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App title
        Text(
            text = "HORIZON",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AccentCyan,
            letterSpacing = 4.sp
        )
        Text(
            text = "Activity Tracker",
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        // Onboarding card
        Card(
            modifier = Modifier
                .width(550.dp)
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(listOf(AccentPurple, AccentCyan))
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Screen Time Access Required",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "To track how much time is spent in each VR application, the tracker requires Android's package usage access statistics permission.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Steps to grant
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InstructionStep("1️⃣", "Put on your Quest 3 headset and enable Developer Mode.")
                    InstructionStep("2️⃣", "Connect your Quest 3 to your developer PC.")
                    InstructionStep(
                        "3️⃣",
                        "Run the following ADB command from your terminal to grant access:"
                    )

                    // ADB Command Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BackgroundColor)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "adb shell pm grant com.autovrse.quest.activitytracker android.permission.PACKAGE_USAGE_STATS",
                            color = AccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onCheckPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        "Check Permission Again",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun InstructionStep(num: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(num, fontSize = 16.sp, modifier = Modifier.padding(end = 12.dp))
        Text(text, color = TextPrimary, fontSize = 14.sp)
    }
}

// SharePreferences Helper Functions
fun saveLimit(context: Context, packageName: String, minutes: Int) {
    val prefs = context.getSharedPreferences("limits_prefs", Context.MODE_PRIVATE)
    prefs.edit().putInt(packageName, minutes).apply()
}

fun getLimit(context: Context, packageName: String): Int {
    val prefs = context.getSharedPreferences("limits_prefs", Context.MODE_PRIVATE)
    return prefs.getInt(packageName, 0)
}

// Playtime format utility
fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

@Composable
fun WebCompanionScreen() {
    val context = LocalContext.current
    var isServerRunning by remember { mutableStateOf(UsageServerService.isRunning) }
    val serverIp = remember { getLocalIpAddress(context) }
    val serverUrl = "http://$serverIp:8080"

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Web Companion Server",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "View and export screen time statistics from any browser",
            color = TextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceColor)
                .padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceColor)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isServerRunning) "Web Server Running" else "Web Server Stopped",
                            color = if (isServerRunning) AccentCyan else TextSecondary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Host port is set to 8080",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    Switch(
                        checked = isServerRunning,
                        onCheckedChange = { checked ->
                            val intent = Intent(context, UsageServerService::class.java)
                            if (checked) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                            } else {
                                context.stopService(intent)
                            }
                            isServerRunning = checked
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentCyan,
                            checkedTrackColor = AccentCyan.copy(alpha = 0.5f),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = SurfaceBorderColor
                        )
                    )
                }

                if (isServerRunning) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Divider(color = SurfaceBorderColor)
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "LOCAL ACCESS URL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BackgroundColor)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = serverUrl,
                            color = AccentCyan,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Open this link in any browser on the same Wi-Fi network to view your playtime statistics dashboard.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceColor)
                .padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceColor)
        ) {
            Column {
                Text(
                    text = "PC One-Click Launcher",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You can bypass local network isolation and access the web companion directly by connecting the Quest to your PC via USB and running start-companion.bat in the project directory.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

fun getLocalIpAddress(context: Context): String {
    try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ipAddress = wifiManager?.connectionInfo?.ipAddress ?: 0
        if (ipAddress != 0) {
            val ipString = String.format(
                "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )
            if (ipString != "0.0.0.0") return ipString
        }
    } catch (e: Exception) {
        Log.e("ActivityTracker", "Failed to get IP from WifiManager, trying NetworkInterfaces", e)
    }

    try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (intf in interfaces) {
            val addrs = Collections.list(intf.inetAddresses)
            for (addr in addrs) {
                if (!addr.isLoopbackAddress) {
                    val sAddr = addr.hostAddress ?: ""
                    val isIPv4 = sAddr.indexOf(':') < 0
                    if (isIPv4) return sAddr
                }
            }
        }
    } catch (ex: Exception) {
        Log.e("ActivityTracker", "Error getting IP Address", ex)
    }
    return "127.0.0.1"
}
