package com.winkokoo.vpn

import android.app.Activity
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import android.util.Base64
import java.util.concurrent.Executors
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var subscriptionUrl: EditText
    private lateinit var nodeSpinner: Spinner
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var updateButton: Button
    private lateinit var statusDot: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("winkoko", MODE_PRIVATE) }
    private val nodes = mutableListOf<VpnNode>()

    private val vpnStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_VPN_STATE) return
            val running = intent.getBooleanExtra("running", false)
            val message = intent.getStringExtra("message")
                ?: if (running) "Connected" else "Disconnected"
            statusText.text = message
            statusDot.setTextColor(if (running) green else Color.rgb(235, 82, 82))
            connectButton.text = if (running) "DISCONNECT" else "CONNECT"
        }
    }

    private val bgColor = Color.rgb(8, 12, 18)
    private val cardColor = Color.rgb(18, 25, 34)
    private val green = Color.rgb(41, 220, 137)
    private val greenDark = Color.rgb(22, 70, 51)
    private val text = Color.WHITE
    private val secondary = Color.rgb(155, 166, 181)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor
        buildUi()
        loadSavedState()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ACTION_VPN_STATE)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(vpnStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(vpnStateReceiver, filter)
        }
        refreshConnectionState()
    }

    override fun onStop() {
        try {
            unregisterReceiver(vpnStateReceiver)
        } catch (_: IllegalArgumentException) {
        }
        super.onStop()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bgColor)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(24), dp(22), dp(28))
        }

        root.addView(
            VpnLogoView(this),
            LinearLayout.LayoutParams(dp(104), dp(104)).apply {
                bottomMargin = dp(12)
            }
        )

        root.addView(
            TextView(this).apply {
                this.text = "WinKoKo VPN"
                textSize = 30f
                setTextColor(this@MainActivity.text)
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
            },
            match().apply { bottomMargin = dp(3) }
        )

        root.addView(
            TextView(this).apply {
                this.text = "Fast • Secure • Private"
                textSize = 14f
                setTextColor(secondary)
                gravity = Gravity.CENTER
            },
            match().apply { bottomMargin = dp(24) }
        )

        val subscriptionCard = card()
        subscriptionCard.addView(label("SUBSCRIPTION URL"))
        subscriptionUrl = EditText(this).apply {
            hint = "Paste V2Ray / Xray subscription URL"
            setHintTextColor(Color.rgb(100, 112, 128))
            setTextColor(this@MainActivity.text)
            textSize = 14f
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_URI
            setPadding(dp(3), dp(10), dp(3), dp(10))
            background = null
        }
        subscriptionCard.addView(
            subscriptionUrl,
            LinearLayout.LayoutParams(-1, dp(52))
        )

        updateButton = Button(this).apply {
            this.text = "UPDATE SUBSCRIPTION"
            textSize = 13f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = rounded(Color.rgb(35, 49, 63), 14)
        }
        subscriptionCard.addView(
            updateButton,
            LinearLayout.LayoutParams(-1, dp(50))
        )
        root.addView(subscriptionCard, match().apply { bottomMargin = dp(16) })

        val nodeCard = card()
        nodeCard.addView(label("SERVER"))
        nodeSpinner = Spinner(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        nodeCard.addView(
            nodeSpinner,
            LinearLayout.LayoutParams(-1, dp(54))
        )
        root.addView(nodeCard, match().apply { bottomMargin = dp(16) })

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(cardColor, 16)
        }
        statusDot = TextView(this).apply {
            this.text = "●"
            textSize = 18f
            setTextColor(Color.rgb(235, 82, 82))
        }
        statusCard.addView(this.statusDot, LinearLayout.LayoutParams(dp(30), -2))
        statusText = TextView(this).apply {
            this.text = "Disconnected"
            textSize = 16f
            setTextColor(this@MainActivity.text)
            typeface = Typeface.DEFAULT_BOLD
        }
        statusCard.addView(statusText)
        root.addView(
            statusCard,
            match().apply { height = dp(58); bottomMargin = dp(18) }
        )

        connectButton = Button(this).apply {
            this.text = "CONNECT"
            textSize = 17f
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = rounded(green, 18)
            elevation = dp(4).toFloat()
        }
        root.addView(
            connectButton,
            match().apply { height = dp(62); bottomMargin = dp(18) }
        )

        root.addView(
            TextView(this).apply {
                this.text = "WinKoKo VPN  •  Xray powered"
                textSize = 12f
                setTextColor(Color.rgb(83, 95, 111))
                gravity = Gravity.CENTER
            },
            match()
        )

        scroll.addView(root)
        setContentView(scroll)

        updateButton.setOnClickListener { updateSubscription() }
        connectButton.setOnClickListener { toggleVpn() }
    }

    private fun loadSavedState() {
        subscriptionUrl.setText(prefs.getString("subscription_url", "") ?: "")
        val savedContent = prefs.getString("subscription_content", null)
        if (!savedContent.isNullOrBlank()) {
            try {
                nodes.clear()
                nodes.addAll(parseSubscription(savedContent))
            } catch (_: Exception) {
            }
        }
        refreshNodesSpinner()
        refreshConnectionState()
    }

    private fun decodedForStorage(raw: String): String = decodeSubscription(raw.trim())

    private fun refreshNodesSpinner() {
        val names = if (nodes.isEmpty()) {
            listOf("No servers — update subscription")
        } else {
            nodes.mapIndexed { index, node ->
                "${index + 1}. ${node.name}"
            }
        }
        nodeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            names
        )
        nodeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (nodes.isNotEmpty() && position in nodes.indices) {
                    prefs.edit()
                        .putInt("selected_node", position)
                        .putString("selected_config", nodes[position].configJson)
                        .putString("selected_name", nodes[position].name)
                        .apply()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        val saved = prefs.getInt("selected_node", 0)
        if (nodes.isNotEmpty()) {
            nodeSpinner.setSelection(saved.coerceIn(0, nodes.lastIndex))
        }
    }

    private fun updateSubscription() {
        val url = subscriptionUrl.text.toString().trim()
        if (url.isEmpty()) {
            toast("Paste a subscription URL first")
            return
        }

        updateButton.isEnabled = false
        updateButton.text = "UPDATING..."
        statusText.text = "Downloading subscription..."
        statusDot.setTextColor(Color.rgb(242, 183, 64))

        executor.execute {
            try {
                val body = download(url)
                val parsed = parseSubscription(body)
                if (parsed.isEmpty()) {
                    throw IllegalArgumentException(
                        "No supported VLESS / VMess / Trojan nodes found"
                    )
                }

                runOnUiThread {
                    nodes.clear()
                    nodes.addAll(parsed)
                    prefs.edit()
                        .putString("subscription_url", url)
                        .putString("subscription_content", decodedForStorage(body))
                        .putInt("selected_node", 0)
                        .apply()
                    refreshNodesSpinner()
                    statusText.text = "${parsed.size} servers ready"
                    statusDot.setTextColor(green)
                    updateButton.isEnabled = true
                    updateButton.text = "UPDATE SUBSCRIPTION"
                    toast("${parsed.size} servers imported")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Subscription update failed"
                    statusDot.setTextColor(Color.rgb(235, 82, 82))
                    updateButton.isEnabled = true
                    updateButton.text = "UPDATE SUBSCRIPTION"
                    toast(e.message ?: "Unable to update subscription")
                }
            }
        }
    }

    private fun download(urlString: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "WinKoKo-VPN/1.0")
            instanceFollowRedirects = true
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSubscription(raw: String): List<VpnNode> {
        val decoded = decodeSubscription(raw.trim())
        val result = mutableListOf<VpnNode>()

        decoded.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                try {
                    when {
                        line.startsWith("vless://", true) ->
                            parseVless(line)?.let(result::add)
                        line.startsWith("vmess://", true) ->
                            parseVmess(line)?.let(result::add)
                        line.startsWith("trojan://", true) ->
                            parseTrojan(line)?.let(result::add)
                    }
                } catch (_: Exception) {
                }
            }

        return result.distinctBy { it.configJson }
    }

    private fun decodeSubscription(value: String): String {
        if (value.contains("vless://", true) ||
            value.contains("vmess://", true) ||
            value.contains("trojan://", true)
        ) return value

        val compact = value
            .replace("\\s".toRegex(), "")
            .replace("-", "+")
            .replace("_", "/")

        return try {
            val padded = compact + "=".repeat((4 - compact.length % 4) % 4)
            String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) {
            value
        }
    }

    private fun parseVless(link: String): VpnNode? {
        val uri = java.net.URI(link)
        val user = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val query = parseQuery(uri.rawQuery)
        val name = decode(uri.rawFragment).ifBlank { host }

        val stream = JSONObject()
            .put("network", query["type"] ?: "tcp")
            .put("security", query["security"] ?: "none")

        val network = query["type"] ?: "tcp"
        if (network == "ws") {
            stream.put(
                "wsSettings",
                JSONObject()
                    .put("path", query["path"] ?: "/")
                    .put("headers", JSONObject().put("Host", query["host"] ?: host))
            )
        } else if (network == "grpc") {
            stream.put(
                "grpcSettings",
                JSONObject()
                    .put("serviceName", query["serviceName"] ?: "")
                    .put("multiMode", query["mode"] == "multi")
            )
        } else if (network == "http" || network == "h2") {
            stream.put(
                "httpSettings",
                JSONObject().put("path", query["path"] ?: "/")
            )
        }

        when (query["security"]?.lowercase()) {
            "tls" -> {
                val tls = JSONObject()
                    .put("serverName", query["sni"] ?: host)
                query["fp"]?.let { tls.put("fingerprint", it) }
                stream.put("tlsSettings", tls)
            }
            "reality" -> {
                val reality = JSONObject()
                    .put("serverName", query["sni"] ?: host)
                    .put("fingerprint", query["fp"] ?: "chrome")
                    .put("show", false)
                query["pbk"]?.let { reality.put("publicKey", it) }
                query["sid"]?.let { reality.put("shortId", it) }
                stream.put("realitySettings", reality)
            }
        }

        val userJson = JSONObject()
            .put("id", user)
            .put("encryption", query["encryption"] ?: "none")
        query["flow"]?.let { userJson.put("flow", it) }

        val outbound = JSONObject()
            .put("protocol", "vless")
            .put(
                "settings",
                JSONObject().put(
                    "vnext",
                    org.json.JSONArray().put(
                        JSONObject()
                            .put("address", host)
                            .put("port", port)
                            .put("users", org.json.JSONArray().put(userJson))
                    )
                )
            )
            .put("streamSettings", stream)

        return VpnNode(name, outboundConfig(outbound))
    }

    private fun parseVmess(link: String): VpnNode? {
        val encoded = link.removePrefix("vmess://")
        val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
        val json = JSONObject(
            String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
        )

        val host = json.optString("add").ifBlank { return null }
        val port = json.optInt("port", 443)
        val uuid = json.optString("id").ifBlank { return null }
        val name = json.optString("ps").ifBlank { host }
        val network = json.optString("net", "tcp")
        val tls = json.optString("tls", "")

        val stream = JSONObject()
            .put("network", network)
            .put("security", if (tls.isBlank()) "none" else "tls")

        if (network == "ws") {
            stream.put(
                "wsSettings",
                JSONObject()
                    .put("path", json.optString("path", "/"))
                    .put(
                        "headers",
                        JSONObject().put("Host", json.optString("host", host))
                    )
            )
        } else if (network == "grpc") {
            stream.put(
                "grpcSettings",
                JSONObject().put("serviceName", json.optString("path", ""))
            )
        }

        if (tls.isNotBlank()) {
            stream.put(
                "tlsSettings",
                JSONObject().put(
                    "serverName",
                    json.optString("sni").ifBlank {
                        json.optString("host").ifBlank { host }
                    }
                )
            )
        }

        val userJson = JSONObject()
            .put("id", uuid)
            .put("alterId", json.optInt("aid", 0))
            .put("security", json.optString("scy", "auto"))

        val outbound = JSONObject()
            .put("protocol", "vmess")
            .put(
                "settings",
                JSONObject().put(
                    "vnext",
                    org.json.JSONArray().put(
                        JSONObject()
                            .put("address", host)
                            .put("port", port)
                            .put("users", org.json.JSONArray().put(userJson))
                    )
                )
            )
            .put("streamSettings", stream)

        return VpnNode(name, outboundConfig(outbound))
    }

    private fun parseTrojan(link: String): VpnNode? {
        val uri = java.net.URI(link)
        val password = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val query = parseQuery(uri.rawQuery)
        val name = decode(uri.rawFragment).ifBlank { host }

        val stream = JSONObject()
            .put("network", query["type"] ?: "tcp")
            .put("security", query["security"] ?: "tls")
            .put(
                "tlsSettings",
                JSONObject().put("serverName", query["sni"] ?: host)
            )

        if ((query["type"] ?: "tcp") == "ws") {
            stream.put(
                "wsSettings",
                JSONObject()
                    .put("path", query["path"] ?: "/")
                    .put("headers", JSONObject().put("Host", query["host"] ?: host))
            )
        }

        val outbound = JSONObject()
            .put("protocol", "trojan")
            .put(
                "settings",
                JSONObject().put(
                    "servers",
                    org.json.JSONArray().put(
                        JSONObject()
                            .put("address", host)
                            .put("port", port)
                            .put("password", password)
                    )
                )
            )
            .put("streamSettings", stream)

        return VpnNode(name, outboundConfig(outbound))
    }

    private fun outboundConfig(outbound: JSONObject): String {
        val config = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put(
                "dns",
                JSONObject().put(
                    "servers",
                    org.json.JSONArray().put("1.1.1.1").put("8.8.8.8")
                )
            )
            .put(
                "inbounds",
                org.json.JSONArray().put(
                    JSONObject()
                        .put("listen", "127.0.0.1")
                        .put("port", 10808)
                        .put("protocol", "socks")
                        .put(
                            "settings",
                            JSONObject()
                                .put("auth", "noauth")
                                .put("udp", true)
                        )
                )
            )
            .put(
                "outbounds",
                org.json.JSONArray()
                    .put(outbound)
                    .put(JSONObject().put("protocol", "freedom").put("tag", "direct"))
            )
            .put(
                "routing",
                JSONObject().put(
                    "domainStrategy",
                    "AsIs"
                )
            )
        return config.toString()
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split("&").mapNotNull {
            val p = it.split("=", limit = 2)
            if (p.isEmpty()) null
            else p[0] to decode(p.getOrElse(1) { "" })
        }.toMap()
    }

    private fun decode(value: String): String =
        try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: Exception) {
            value
        }

    private fun toggleVpn() {
        if (prefs.getBoolean("vpn_running", false)) {
            stopService(Intent(this, WinKoKoVpnService::class.java))
            prefs.edit().putBoolean("vpn_running", false).apply()
            refreshConnectionState()
            return
        }

        if (nodes.isEmpty()) {
            toast("Update subscription and choose a server first")
            return
        }

        val selected = nodeSpinner.selectedItemPosition.coerceIn(0, nodes.lastIndex)
        prefs.edit()
            .putInt("selected_node", selected)
            .putString("selected_config", nodes[selected].configJson)
            .putString("selected_name", nodes[selected].name)
            .apply()

        val permission = VpnService.prepare(this)
        if (permission != null) {
            startActivityForResult(permission, VPN_REQUEST_CODE)
        } else {
            startVpnService()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                startVpnService()
            } else {
                statusText.text = "VPN permission denied"
                statusDot.setTextColor(Color.rgb(235, 82, 82))
            }
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, WinKoKoVpnService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        prefs.edit().putBoolean("vpn_running", false).apply()
        statusText.text = "Connecting..."
        statusDot.setTextColor(Color.rgb(242, 183, 64))
        connectButton.text = "DISCONNECT"
    }

    private fun refreshConnectionState() {
        val running = prefs.getBoolean("vpn_running", false)
        if (running) {
            statusText.text = "Connected"
            statusDot.setTextColor(green)
            connectButton.text = "DISCONNECT"
        } else {
            statusText.text = "Disconnected"
            statusDot.setTextColor(Color.rgb(235, 82, 82))
            connectButton.text = "CONNECT"
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 11f
        setTextColor(green)
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(17), dp(16), dp(17), dp(16))
        background = rounded(cardColor, 18)
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun match() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    data class VpnNode(
        val name: String,
        val configJson: String
    )

    companion object {
        private const val VPN_REQUEST_CODE = 1001
        private const val ACTION_VPN_STATE = "com.winkoko.vpn.STATE"
    }

    class VpnLogoView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(18, 62, 46)
            canvas.drawCircle(w / 2f, h / 2f, w * .46f, paint)

            val shield = Path().apply {
                moveTo(w * .5f, h * .12f)
                lineTo(w * .82f, h * .25f)
                lineTo(w * .76f, h * .60f)
                cubicTo(w * .72f, h * .78f, w * .58f, h * .88f, w * .5f, h * .92f)
                cubicTo(w * .42f, h * .88f, w * .28f, h * .78f, w * .24f, h * .60f)
                lineTo(w * .18f, h * .25f)
                close()
            }
            paint.color = Color.rgb(41, 220, 137)
            canvas.drawPath(shield, paint)

            val inner = Path().apply {
                moveTo(w * .5f, h * .22f)
                lineTo(w * .70f, h * .31f)
                lineTo(w * .66f, h * .56f)
                cubicTo(w * .63f, h * .68f, w * .55f, h * .75f, w * .5f, h * .78f)
                cubicTo(w * .45f, h * .75f, w * .37f, h * .68f, w * .34f, h * .56f)
                lineTo(w * .30f, h * .31f)
                close()
            }
            paint.color = Color.rgb(8, 25, 20)
            canvas.drawPath(inner, paint)

            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE
            paint.strokeWidth = w * .055f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(
                Path().apply {
                    moveTo(w * .37f, h * .50f)
                    lineTo(w * .47f, h * .61f)
                    lineTo(w * .66f, h * .39f)
                },
                paint
            )
            paint.style = Paint.Style.FILL
        }
    }
}
