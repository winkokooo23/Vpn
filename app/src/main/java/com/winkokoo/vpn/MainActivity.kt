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
        val frame = FrameLayout(this).apply { setBackgroundColor(bgColor) }
        frame.addView(TechBackdropView(this), FrameLayout.LayoutParams(-1, -1))

        val scroll = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(24))
        }

        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(iconText("☰", 29f, text), LinearLayout.LayoutParams(dp(48), dp(48)))
        top.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(outlineButton("👑  PRO", Color.rgb(255, 184, 53), 120), LinearLayout.LayoutParams(dp(120), dp(46)))
        top.addView(iconText("↗", 28f, text), LinearLayout.LayoutParams(dp(46), dp(48)))
        top.addView(iconText("⚙", 28f, text), LinearLayout.LayoutParams(dp(46), dp(48)))
        root.addView(top, match().apply { bottomMargin = dp(8) })

        root.addView(HeroLogoView(this), LinearLayout.LayoutParams(-1, dp(190)).apply { bottomMargin = dp(2) })
        root.addView(textView("WinKoKo VPN", 34f, text, Typeface.DEFAULT_BOLD, Gravity.CENTER), match().apply { bottomMargin = dp(3) })
        root.addView(textView("F a s t   •   S e c u r e   •   U n l i m i t e d", 13f, Color.rgb(224, 233, 255), Typeface.DEFAULT_BOLD, Gravity.CENTER), match().apply { bottomMargin = dp(18) })

        val subscription = glassCard()
        val subscriptionRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        subscriptionRow.addView(iconTile("↗", Color.rgb(32, 106, 210)), LinearLayout.LayoutParams(dp(74), dp(74)))
        val urlColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(8), 0) }
        urlColumn.addView(textView("V2Ray Subscription URL", 15f, Color.rgb(206, 219, 246), Typeface.DEFAULT, Gravity.START), match().apply { bottomMargin = dp(2) })
        subscriptionUrl = EditText(this).apply {
            hint = "Paste your subscription URL here..."
            setHintTextColor(Color.rgb(133, 151, 192))
            setTextColor(this@MainActivity.text)
            textSize = 14f
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setPadding(0, dp(3), 0, dp(3))
            background = null
        }
        urlColumn.addView(subscriptionUrl, LinearLayout.LayoutParams(-1, dp(42)))
        subscriptionRow.addView(urlColumn, LinearLayout.LayoutParams(0, -2, 1f))
        subscriptionRow.addView(outlineButton("▣", Color.WHITE, 52), LinearLayout.LayoutParams(dp(52), dp(52)).apply { rightMargin = dp(10) })
        updateButton = gradientButton("☁  UPDATE", 15f)
        subscriptionRow.addView(updateButton, LinearLayout.LayoutParams(dp(170), dp(58)))
        subscription.addView(subscriptionRow)
        root.addView(subscription, match().apply { bottomMargin = dp(16) })

        val server = glassCard()
        val serverRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        serverRow.addView(iconTile("▤", Color.rgb(30, 91, 193)), LinearLayout.LayoutParams(dp(74), dp(74)))
        val serverColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(12), 0) }
        serverColumn.addView(textView("Server", 15f, Color.rgb(206, 219, 246), Typeface.DEFAULT, Gravity.START))
        nodeSpinner = Spinner(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        serverColumn.addView(nodeSpinner, LinearLayout.LayoutParams(-1, dp(46)))
        serverRow.addView(serverColumn, LinearLayout.LayoutParams(0, -2, 1f))
        serverRow.addView(outlineButton("☷  SERVER LIST", Color.WHITE, 185), LinearLayout.LayoutParams(dp(185), dp(58)))
        server.addView(serverRow)
        root.addView(server, match().apply { bottomMargin = dp(18) })

        connectButton = Button(this).apply {
            text = "⏻\nCONNECT"
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(13, 40, 111), Color.rgb(7, 104, 219))).apply { shape = GradientDrawable.OVAL; setStroke(dp(4), Color.rgb(26, 169, 255)) }
            elevation = dp(8).toFloat()
        }
        val connectFrame = FrameLayout(this).apply { addView(connectButton, FrameLayout.LayoutParams(dp(230), dp(230), Gravity.CENTER)) }
        root.addView(connectFrame, match().apply { height = dp(250); bottomMargin = dp(8) })

        val statusCard = pill()
        statusDot = TextView(this).apply { text = "●"; textSize = 18f; setTextColor(Color.rgb(255, 68, 92)) }
        statusText = textView("Disconnected", 16f, Color.rgb(255, 86, 108), Typeface.DEFAULT_BOLD, Gravity.START)
        statusCard.addView(statusDot, LinearLayout.LayoutParams(dp(30), -2))
        statusCard.addView(statusText, LinearLayout.LayoutParams(0, -2, 1f))
        statusCard.addView(textView("Not connected", 13f, Color.rgb(172, 190, 230), Typeface.DEFAULT, Gravity.END))
        root.addView(statusCard, match().apply { height = dp(58); bottomMargin = dp(16) })

        val stats = LinearLayout(this).apply { gravity = Gravity.CENTER; background = rounded(Color.argb(100, 7, 32, 82), 18); setPadding(dp(6), dp(12), dp(6), dp(12)) }
        stats.addView(stat("⬇", "Download", "0 B/s", Color.rgb(0, 246, 184)), weight(1f))
        stats.addView(stat("◷", "Duration", "00:00:00", Color.rgb(35, 190, 255)), weight(1f))
        stats.addView(stat("⬆", "Upload", "0 B/s", Color.rgb(255, 73, 233)), weight(1f))
        root.addView(stats, match().apply { bottomMargin = dp(14) })

        val info = LinearLayout(this).apply { gravity = Gravity.CENTER }
        info.addView(infoBox("◉", "Ping", "-- ms", Color.CYAN), weight(1f))
        info.addView(infoBox("▤", "P n r t o o l", "Auto", Color.rgb(40, 219, 181)), weight(1f))
        info.addView(infoBox("●", "Location", "--", Color.MAGENTA), weight(1f))
        info.addView(infoBox("ϟ", "IP Address", "--", Color.rgb(255, 183, 44)), weight(1f))
        root.addView(info, match().apply { bottomMargin = dp(16) })

        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        actions.addView(outlineButton("⚙  SETTINGS", Color.WHITE, 0), weight(1f).apply { rightMargin = dp(8) })
        actions.addView(outlineButton("⟳  RESET", Color.WHITE, 0), weight(1f).apply { rightMargin = dp(8) })
        actions.addView(Button(this).apply { text = "■  DISCONNECT"; textSize = 13f; setTextColor(Color.WHITE); background = rounded(Color.rgb(218, 43, 67), 14); isAllCaps = false }, weight(1f))
        root.addView(actions, match().apply { height = dp(54); bottomMargin = dp(22) })

        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        footer.addView(VpnLogoView(this), LinearLayout.LayoutParams(dp(48), dp(48)))
        footer.addView(textView("WinKoKo VPN\nv1.0.0", 13f, Color.rgb(198, 217, 247), Typeface.DEFAULT, Gravity.START).apply { setPadding(dp(8), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(textView("Powered by V2Ray/Xray  🚀", 14f, Color.rgb(169, 193, 235), Typeface.DEFAULT, Gravity.END))
        root.addView(footer, match())

        scroll.addView(root)
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        updateButton.setOnClickListener { updateSubscription() }
        connectButton.setOnClickListener { toggleVpn() }
    }

    private fun textView(value: String, size: Float, color: Int, face: Typeface, align: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = face
        gravity = align
        includeFontPadding = true
    }

    private fun glassCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.argb(175, 7, 37, 98), Color.argb(165, 9, 20, 63))).apply { cornerRadius = dp(22).toFloat(); setStroke(dp(1), Color.rgb(32, 113, 234)) }
    }

    private fun pill() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.argb(150, 8, 37, 93), Color.argb(150, 8, 18, 58))).apply { cornerRadius = dp(28).toFloat(); setStroke(dp(1), Color.rgb(30, 102, 210)) }
    }

    private fun iconText(value: String, size: Float, color: Int) = textView(value, size, color, Typeface.DEFAULT_BOLD, Gravity.CENTER)

    private fun iconTile(symbol: String, color: Int) = TextView(this).apply {
        text = symbol
        textSize = 31f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(color, Color.rgb(9, 52, 137))).apply { cornerRadius = dp(17).toFloat() }
    }

    private fun outlineButton(label: String, color: Int, width: Int): Button = Button(this).apply {
        text = label
        textSize = 13f
        setTextColor(color)
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.argb(90, 24, 86, 190), Color.argb(100, 12, 31, 86))).apply { cornerRadius = dp(14).toFloat(); setStroke(dp(1), Color.rgb(35, 131, 255)) }
        if (width > 0) minimumWidth = dp(width)
    }

    private fun gradientButton(label: String, size: Float) = Button(this).apply {
        text = label
        textSize = size
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.rgb(17, 207, 255), Color.rgb(121, 53, 238))).apply { cornerRadius = dp(14).toFloat() }
    }

    private fun stat(symbol: String, title: String, value: String, tint: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(textView(symbol, 25f, tint, Typeface.DEFAULT_BOLD, Gravity.CENTER))
        addView(textView(title, 12f, Color.rgb(180, 202, 240), Typeface.DEFAULT, Gravity.CENTER))
        addView(textView(value, 18f, Color.WHITE, Typeface.DEFAULT_BOLD, Gravity.CENTER))
    }

    private fun infoBox(symbol: String, title: String, value: String, tint: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(8), dp(4), dp(8))
        background = rounded(Color.argb(120, 8, 34, 88), 16)
        addView(textView(symbol, 22f, tint, Typeface.DEFAULT_BOLD, Gravity.CENTER))
        addView(textView(title, 11f, Color.rgb(173, 198, 238), Typeface.DEFAULT, Gravity.CENTER))
        addView(textView(value, 14f, Color.WHITE, Typeface.DEFAULT_BOLD, Gravity.CENTER))
    }

    private fun weight(value: Float) = LinearLayout.LayoutParams(0, -1, value)

    private fun loadSavedState() {
        subscriptionUrl.setText(prefs.getString("subscription_url", DEFAULT_SUBSCRIPTION_URL) ?: DEFAULT_SUBSCRIPTION_URL)
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
        private const val DEFAULT_SUBSCRIPTION_URL = "https://vip.winkokooolovekaykay.dpdns.org/sub?token=3ee3eb89b11162cd274044102a1c7ebd&b64"
    }

    class TechBackdropView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            paint.shader = LinearGradient(0f, 0f, w, h, Color.rgb(3, 10, 33), Color.rgb(4, 44, 105), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = Color.argb(75, 48, 153, 255)
            for (i in 1..8) {
                val y = h * (0.06f + i * 0.055f)
                canvas.drawLine(0f, y, w, y - h * .12f, paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(80, 0, 210, 255)
            canvas.drawCircle(w * .50f, h * .18f, w * .34f, paint)
            paint.color = Color.argb(45, 94, 64, 255)
            canvas.drawCircle(w * .14f, h * .52f, w * .25f, paint)
            canvas.drawCircle(w * .88f, h * .65f, w * .23f, paint)
        }
    }

    class HeroLogoView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height * .45f; val r = width * .22f
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(cx - r * .3f, cy - r * .35f, r * 1.6f, Color.rgb(36, 237, 255), Color.rgb(6, 45, 170), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(context, 7).toFloat()
            paint.color = Color.rgb(16, 202, 255)
            canvas.drawOval(cx - r * 1.65f, cy - r * .52f, cx + r * 1.65f, cy + r * .52f, paint)
            canvas.drawOval(cx - r * .62f, cy - r * 1.65f, cx + r * .62f, cy + r * 1.65f, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = r * 1.25f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("W", cx, cy + r * .43f, paint)
            paint.textSize = r * .30f
            paint.color = Color.rgb(8, 43, 143)
            canvas.drawText("VPN", cx + r * .72f, cy + r * .85f, paint)
            paint.textAlign = Paint.Align.LEFT
        }
        private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
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
