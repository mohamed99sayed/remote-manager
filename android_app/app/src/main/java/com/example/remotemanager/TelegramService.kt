package com.example.remotemanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject

class TelegramService : Service() {

    private var serviceJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var commandExecutor: CommandExecutor
    private val client = OkHttpClient()
    
    private var chatId: String = ""
    private var botToken: String = ""
    private var lastUpdateId = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        chatId = intent?.getStringExtra("CHAT_ID") ?: ""
        botToken = intent?.getStringExtra("BOT_TOKEN") ?: ""

        if (chatId.isEmpty() || botToken.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        commandExecutor = CommandExecutor(this)
        startForegroundService()
        startPolling()

        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Background Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Remote Manager")
            .setContentText("Service is running...")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .build()

        startForeground(1, notification)
    }

    private fun startPolling() {
        serviceJob = scope.launch {
            while (isActive) {
                try {
                    pollTelegramUpdates()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(2000) // Poll every 2 seconds
            }
        }
    }

    private fun pollTelegramUpdates() {
        val url = "https://api.telegram.org/bot$botToken/getUpdates?offset=${lastUpdateId + 1}"
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return

            val json = JSONObject(response.body?.string() ?: "")
            if (json.getBoolean("ok")) {
                val result = json.getJSONArray("result")
                for (i in 0 until result.length()) {
                    val update = result.getJSONObject(i)
                    lastUpdateId = update.getInt("update_id")
                    
                    if (update.has("message")) {
                        val message = update.getJSONObject("message")
                        val text = message.optString("text", "")
                        val senderId = message.getJSONObject("chat").getString("id")
                        
                        // Only accept commands from the configured admin chat
                        if (senderId == chatId) {
                            handleCommand(text)
                        }
                    }
                }
            }
        }
    }

    private fun handleCommand(text: String) {
        // Format: /command [args]
        val parts = text.split(" ", limit = 2)
        val command = parts[0].removePrefix("/")
        val args = if (parts.size > 1) parts[1] else ""

        when (command) {
            "ping" -> sendMessage("Pong! Device is online.")
            "info" -> {
                val bat = commandExecutor.getBatteryLevel()
                val storage = commandExecutor.getStorageInfo()
                sendMessage("🔋 Battery: $bat%\n💾 Storage: $storage")
            }
            "apps" -> {
                val apps = commandExecutor.getInstalledApps().take(20).joinToString("\n") // Limit to 20 for demo
                sendMessage("📱 Installed Apps (First 20):\n$apps")
            }
            else -> commandExecutor.execute(command, args)
        }
    }

    private fun sendMessage(text: String) {
        scope.launch {
            try {
                val url = "https://api.telegram.org/bot$botToken/sendMessage"
                val json = JSONObject()
                json.put("chat_id", chatId)
                json.put("text", text)
                
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(body).build()
                client.newCall(request).execute()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob?.cancel()
        super.onDestroy()
    }
}
