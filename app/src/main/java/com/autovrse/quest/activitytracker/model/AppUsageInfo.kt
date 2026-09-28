package com.autovrse.quest.activitytracker.model

import androidx.compose.ui.graphics.ImageBitmap

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeInForeground: Long, // in milliseconds
    val totalTimeVisible: Long, // in milliseconds
    val launchCount: Int,
    val lastTimeUsed: Long, // timestamp
    val icon: ImageBitmap? = null
)
