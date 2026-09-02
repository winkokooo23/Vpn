package com.winkokoo.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.util.Log
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.json.JSONObject
import java.util.concurrent.Executors

class WinKoKoVpnService : VpnService() {

    companion object {
        private const val TAG = "WinKoKoVPN"
        private const val CHANNEL_ID = "winkoko_vpn"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS = "winkoko"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private lateinit var vpnInterface: android.os.ParcelFileDescriptor

    private val callback = object : CoreCallbackHandler {
        override fun startup(): Long {
            setState(true, "Connected")
            return 0L
        }

        override fun shutdown(): Long {
            closeInterface()
            setState(false, "Disconnected")
            return 0L
        }

        override fun onEmitStatus(code: Long, message: String?): Long {
            Log.d(TAG, "Xray status $code: $message")
            val status = message.orEmpty().lowercase()
            when {
                status.contains("start") || status == "running" -> setState(true, "Connected")
                status.contains("stop") || status == "closed" -> setState(false, "Disconnected")
            }
            return 0L
        }
    }

    private val core: CoreController = Libv2ray.newCoreController(callback)

    override fun onCreate() {
        super.onCreate()
        // Required by AndroidLibXrayLite for certificates/assets and config file access.
        Libv2ray.initCoreEnv(filesDir.absolutePath, "")
        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = prefs.getString("selected_config", null)
        if (config.isNullOrBlank()) {
            setState(false, "No server selected")
            stopSelf()
            return START_NOT_STICKY
        }

        executor.execute {
            try {
                if (!core.getIsRunning()) {
                    setState(false, "Starting Xray...")
                    establishVpnInterface()
                    val fd = vpnInterface.fd
                    if (fd < 0) throw IllegalStateException("Invalid VPN file descriptor")
                    core.startLoop(config, fd)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Xray start failed", e)
                setState(false, "Connection failed: ${e.message ?: "Xray could not start"}")
                closeInterface()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        executor.execute {
            try {
                if (core.getIsRunning()) core.stopLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Stop error", e)
            } finally {
                closeInterface()
                setState(false, "Disconnected")
            }
        }
        executor.shutdownNow()
        @Suppress("DEPRECATION")
        stopForeground(true)
        super.onDestroy()
    }

    override fun onRevoke() {
        stopSelf()
        super.onRevoke()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun establishVpnInterface() {
        closeInterface()
        vpnInterface = Builder()
            .setSession("WinKoKo VPN")
            .addAddress("10.0.0.2", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .establish()
            ?: throw IllegalStateException("Android could not establish VPN interface")
    }

    private fun closeInterface() {
        try {
            if (::vpnInterface.isInitialized && !vpnInterface.fileDescriptor.valid()) return
            if (::vpnInterface.isInitialized) vpnInterface.close()
        } catch (_: Exception) {
        }
    }

    private fun setState(running: Boolean, message: String) {
        prefs.edit().putBoolean("vpn_running", running).apply()
        sendBroadcast(Intent("com.winkokoo.vpn.STATE").apply {
            setPackage(packageName)
            putExtra("running", running)
            putExtra("message", message)
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WinKoKo VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("WinKoKo VPN")
                .setContentText("VPN is running")
                .setSmallIcon(R.drawable.ic_vpn)
                .setOngoing(true)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("WinKoKo VPN")
                .setContentText("VPN is running")
                .setSmallIcon(R.drawable.ic_vpn)
                .setOngoing(true)
                .build()
        }
}
