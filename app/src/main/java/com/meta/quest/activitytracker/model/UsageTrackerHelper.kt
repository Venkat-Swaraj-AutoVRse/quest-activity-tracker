package com.meta.quest.activitytracker.model

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.Calendar

enum class TimeRange {
    TODAY,
    WEEK,
    MONTH
}

object UsageTrackerHelper {
    private const val TAG = "UsageTrackerHelper"

    fun getUsageStats(context: Context, timeRange: TimeRange): List<AppUsageInfo> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val pm = context.packageManager
        val endTime = System.currentTimeMillis()
        val startTime = getStartTime(timeRange)

        val intervalType = when (timeRange) {
            TimeRange.TODAY -> UsageStatsManager.INTERVAL_DAILY
            TimeRange.WEEK -> UsageStatsManager.INTERVAL_WEEKLY
            TimeRange.MONTH -> UsageStatsManager.INTERVAL_MONTHLY
        }

        val rawStats = try {
            usageStatsManager.queryUsageStats(intervalType, startTime, endTime)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying usage stats", e)
            null
        }

        if (rawStats.isNullOrEmpty()) {
            Log.w(TAG, "No usage stats returned. Permission PACKAGE_USAGE_STATS might not be granted.")
            return emptyList()
        }

        // Aggregate by package name
        val timeMap = mutableMapOf<String, Long>()
        val visibleTimeMap = mutableMapOf<String, Long>()
        val launchMap = mutableMapOf<String, Int>()
        val lastUsedMap = mutableMapOf<String, Long>()

        for (stat in rawStats) {
            val pkg = stat.packageName
            val foreground = stat.totalTimeInForeground
            val visible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) stat.totalTimeVisible else stat.totalTimeInForeground
            val launches = try {
                val field = UsageStats::class.java.getDeclaredField("mLaunchCount")
                field.isAccessible = true
                field.getInt(stat)
            } catch (e1: Exception) {
                try {
                    val method = UsageStats::class.java.getMethod("getAppLaunchCount")
                    method.invoke(stat) as Int
                } catch (e2: Exception) {
                    0
                }
            }
            val lastUsed = stat.lastTimeUsed

            timeMap[pkg] = (timeMap[pkg] ?: 0L) + foreground
            visibleTimeMap[pkg] = (visibleTimeMap[pkg] ?: 0L) + visible
            launchMap[pkg] = (launchMap[pkg] ?: 0) + launches
            lastUsedMap[pkg] = maxOf(lastUsedMap[pkg] ?: 0L, lastUsed)
        }

        val resultList = mutableListOf<AppUsageInfo>()

        for ((pkg, time) in timeMap) {
            // Filter out system or background apps with 0 playtime
            if (time <= 0L) continue

            // Filter out standard android system packages that clutter the dashboard (optional but recommended)
            if (shouldIgnorePackage(pkg)) continue

            var appName = pkg
            var iconBitmap: androidx.compose.ui.graphics.ImageBitmap? = null

            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                appName = pm.getApplicationLabel(appInfo).toString()
                val drawable = pm.getApplicationIcon(appInfo)
                iconBitmap = drawable.toBitmap(width = 96, height = 96).asImageBitmap()
            } catch (e: PackageManager.NameNotFoundException) {
                appName = cleanPackageName(pkg)
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving app info for $pkg", e)
            }

            resultList.add(
                AppUsageInfo(
                    packageName = pkg,
                    appName = appName,
                    totalTimeInForeground = time,
                    totalTimeVisible = visibleTimeMap[pkg] ?: time,
                    launchCount = launchMap[pkg] ?: 0,
                    lastTimeUsed = lastUsedMap[pkg] ?: 0L,
                    icon = iconBitmap
                )
            )
        }

        // Sort by time in foreground descending
        return resultList.sortedByDescending { it.totalTimeInForeground }
    }

    private fun getStartTime(timeRange: TimeRange): Long {
        val calendar = Calendar.getInstance()
        when (timeRange) {
            TimeRange.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            TimeRange.WEEK -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7)
            }
            TimeRange.MONTH -> {
                calendar.add(Calendar.DAY_OF_YEAR, -30)
            }
        }
        return calendar.timeInMillis
    }

    private fun shouldIgnorePackage(packageName: String): Boolean {
        if (packageName == "android") return true

        val ignoredPrefixes = listOf(
            "com.android.",
            "android.ext.",
            "com.oculus.",
            "horizon.",
            "horizonos.",
            "com.facebook.horizon",
            "com.facebook.spatial_persistence_service",
            "com.facebook.wearable.",
            "com.meta.quest.activitytracker",
            "com.meta.systemui",
            "com.meta.pclinkservice",
            "com.meta.automation.",
            "com.meta.horizonadidservice",
            "com.meta.AccountsCenter",
            "com.meta.mqdh",
            "com.meta.federatedcomputing",
            "com.meta.environment",
            "com.meta.Hyperscape",
            "com.meta.framework",
            "com.meta.android",
            "com.meta.rl",
            "com.meta.transport",
            "com.meta.hpiassets",
            "com.meta.surfacetypingnux",
            "com.meta.shell",
            "com.meta.handseducation"
        )
        return ignoredPrefixes.any { packageName.startsWith(it) }
    }

    private fun cleanPackageName(packageName: String): String {
        if (packageName.startsWith("com.oculus.")) {
            val sub = packageName.substringAfter("com.oculus.")
            return sub.replace(".", " ").capitalizeWords()
        }
        if (packageName.startsWith("com.meta.")) {
            val sub = packageName.substringAfter("com.meta.")
            return "Meta " + sub.replace(".", " ").capitalizeWords()
        }
        val parts = packageName.split(".")
        if (parts.size >= 2) {
            return parts.last().capitalizeWords()
        }
        return packageName
    }

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}
