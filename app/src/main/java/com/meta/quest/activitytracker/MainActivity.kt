package com.meta.quest.activitytracker

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.meta.quest.activitytracker.ui.MainAppScreen

class MainActivity : ComponentActivity() {

    private val hasPermissionState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        setContent {
            val hasPermission by remember { hasPermissionState }

            MainAppScreen(
                hasPermission = hasPermission,
                onCheckPermission = {
                    checkUsagePermission()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        checkUsagePermission()
    }

    private fun checkUsagePermission() {
        val hasPerm = hasUsageStatsPermission(this)
        Log.d(TAG, "checkUsagePermission: hasPermission=$hasPerm")
        hasPermissionState.value = hasPerm
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        val result = mode == AppOpsManager.MODE_ALLOWED
        Log.d(TAG, "hasUsageStatsPermission: mode=$mode, result=$result")
        return result
    }

    companion object {
        private const val TAG = "ActivityTracker"
    }
}
