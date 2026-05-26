package com.meta.quest.activitytracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.meta.quest.activitytracker.model.TimeRange
import com.meta.quest.activitytracker.model.UsageTrackerHelper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.gson.gson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.IOException

class UsageServerService : Service() {

    private var server: NettyApplicationEngine? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: starting service")
        isRunning = true
        startForegroundServiceNotification()
        startWebServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: stopping web server")
        isRunning = false
        try {
            server?.stop(1000, 2000)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
        super.onDestroy()
    }

    private fun startWebServer() {
        server = embeddedServer(Netty, port = PORT) {
            install(ContentNegotiation) {
                gson {
                    setPrettyPrinting()
                }
            }
            routing {
                // API Route returning usage statistics
                get("/api/usage") {
                    val rangeParam = call.request.queryParameters["range"]?.lowercase() ?: "today"
                    val timeRange = when (rangeParam) {
                        "week" -> TimeRange.WEEK
                        "month" -> TimeRange.MONTH
                        else -> TimeRange.TODAY
                    }

                    try {
                        val stats = UsageTrackerHelper.getUsageStats(applicationContext, timeRange)
                        val dtoList = stats.map {
                            AppUsageInfoDTO(
                                packageName = it.packageName,
                                appName = it.appName,
                                totalTimeInForeground = it.totalTimeInForeground,
                                totalTimeVisible = it.totalTimeVisible,
                                launchCount = it.launchCount,
                                lastTimeUsed = it.lastTimeUsed
                            )
                        }
                        call.respond(dtoList)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error compiling usage stats JSON", e)
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                    }
                }

                // Serve index.html on root
                get("/") {
                    try {
                        val html = readAssetFile(applicationContext, "web/index.html")
                        call.respondText(html, ContentType.Text.Html)
                    } catch (e: IOException) {
                        Log.e(TAG, "Error loading web/index.html", e)
                        call.respond(HttpStatusCode.NotFound, "Static dashboard resources not found in assets.")
                    }
                }

                // Serve other assets dynamically
                get("/{staticPath...}") {
                    val staticPath = call.parameters.getAll("staticPath")?.joinToString("/") ?: ""
                    val assetPath = "web/$staticPath"
                    try {
                        val bytes = readAssetFileBytes(applicationContext, assetPath)
                        val contentType = getContentType(staticPath)
                        call.respondBytes(bytes, contentType)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }.apply {
            start(wait = false)
        }
        Log.i(TAG, "Web server started on port $PORT")
    }

    private fun startForegroundServiceNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Web Companion Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background Web Companion server for Quest Activity Tracker"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Web Companion Service")
            .setContentText("Serving screen time stats on port $PORT")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun readAssetFile(context: Context, path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }

    private fun readAssetFileBytes(context: Context, path: String): ByteArray {
        return context.assets.open(path).use { it.readBytes() }
    }

    private fun getContentType(path: String): ContentType {
        return when {
            path.endsWith(".html") -> ContentType.Text.Html
            path.endsWith(".css") -> ContentType.Text.CSS
            path.endsWith(".js") -> ContentType.Application.JavaScript
            path.endsWith(".json") -> ContentType.Application.Json
            path.endsWith(".png") -> ContentType.Image.PNG
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> ContentType.Image.JPEG
            path.endsWith(".svg") -> ContentType.Image.SVG
            else -> ContentType.Application.OctetStream
        }
    }

    // DTO mapping to prevent circular reference/crash while serializing Compose ImageBitmap
    data class AppUsageInfoDTO(
        val packageName: String,
        val appName: String,
        val totalTimeInForeground: Long,
        val totalTimeVisible: Long,
        val launchCount: Int,
        val lastTimeUsed: Long
    )

    companion object {
        var isRunning = false
            private set

        private const val TAG = "UsageServerService"
        private const val CHANNEL_ID = "UsageServerChannel"
        private const val PORT = 8080
        private const val NOTIFICATION_ID = 2211
    }
}
