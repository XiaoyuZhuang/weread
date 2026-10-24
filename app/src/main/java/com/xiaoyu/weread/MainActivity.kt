package com.xiaoyu.weread

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var config: SecureConfig
    private lateinit var colors: Palette
    private lateinit var keyInput: EditText
    private lateinit var endpointInput: EditText
    private lateinit var visionInput: EditText
    private lateinit var temperatureInput: EditText
    private lateinit var countInput: EditText
    private lateinit var themeInput: Spinner
    private lateinit var hapticToggle: CheckBox
    private lateinit var debugToggle: CheckBox
    private lateinit var status: TextView
    private lateinit var testStatus: TextView
    private lateinit var toggle: Button
    private lateinit var testButton: Button
    private val client = DeepSeekClient()
    private var waitingForOverlayPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = SecureConfig(this)
        AppLog.init(this)
        colors = Appearance.resolve(this)
        setTheme(if (colors.dark) android.R.style.Theme_Material_NoActionBar
                 else android.R.style.Theme_Material_Light_NoActionBar)
        window.statusBarColor = colors.background
        window.navigationBarColor = colors.background
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (colors.dark) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        buildUi()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun bg(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    private fun text(value: String, size: Float = 14f, muted: Boolean = false) =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(if (muted) colors.muted else colors.text)
        }
    private fun label(value: String) = text(value, 13f, true).apply {
        setPadding(0, dp(9), 0, dp(7))
    }
    private fun field(hint: String, value: String): EditText = EditText(this).apply {
        setSingleLine(true)
        setText(value)
        setHint(hint)
        textSize = 15f
        setTextColor(colors.text)
        setHintTextColor(colors.muted)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = bg(colors.surface, 11)
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) }
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(colors.background) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(44), dp(24), dp(24))
        }
        scroll.addView(content)
        content.addView(text("偏好设置", 25f).apply {
            setTextColor(colors.heading)
            typeface = Typeface.DEFAULT_BOLD
        })
        content.addView(text("外观、连接与诊断", 14f, true).apply {
            setPadding(0, dp(10), 0, dp(22))
        })

        val model = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        model.addView(text("模型设置", 21f).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(16), 0, dp(12)) })
        model.addView(label("DeepSeek 官方 API Key"))
        keyInput = field("输入 DeepSeek API Key", config.loadKey()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        model.addView(keyInput)

        val advanced = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        advanced.addView(text("软件设置", 21f).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(28), 0, dp(12)) })
        advanced.addView(label("外观主题"))
        val themes = listOf("跟随系统", "浅色模式", "深色模式")
        themeInput = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item, themes)
            setSelection(SecureConfig.THEME_OPTIONS.indexOf(config.themeMode).coerceAtLeast(0))
        }
        advanced.addView(themeInput)
        advanced.addView(label("点击反馈"))
        hapticToggle = CheckBox(this).apply {
            text = "按钮点击震动（默认开启）"
            setTextColor(colors.text)
            isChecked = config.hapticEnabled
            setOnCheckedChangeListener { _, enabled ->
                config.hapticEnabled = enabled
                if (enabled) Appearance.tap(this)
            }
        }
        advanced.addView(hapticToggle)
        advanced.addView(label("调试选项"))
        debugToggle = CheckBox(this).apply {
            text = "显示图片理解文本（调试用，默认关闭）"
            setTextColor(colors.text)
            isChecked = config.showImageDebug
            setOnCheckedChangeListener { _, enabled ->
                config.showImageDebug = enabled
                Appearance.tap(this)
                if (OverlayService.isRunning) {
                    startService(Intent(this@MainActivity, OverlayService::class.java)
                        .setAction(OverlayService.ACTION_APPEARANCE))
                }
            }
        }
        advanced.addView(debugToggle)
        model.addView(label("DeepSeek API 根地址（HTTPS）"))
        endpointInput = field(SecureConfig.DEFAULT_ENDPOINT, config.endpoint)
        model.addView(endpointInput)
        model.addView(label("Flash 模型"))
        visionInput = field(SecureConfig.DEFAULT_VISION_MODEL, config.visionModel)
        model.addView(visionInput)
        model.addView(label("搜索结果数量（3～8，默认5）"))
        countInput = field("5", config.searchCount.toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        model.addView(countInput)
        model.addView(label("模型温度（0～2，默认0.2）"))
        temperatureInput = field("0.2", config.temperature.toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        model.addView(temperatureInput)
        model.addView(text("联网搜索始终执行；搜索没有结果不会直接猜答案。", 12f, true))
        content.addView(model)
        content.addView(advanced)

        // A theme switch re-creates the activity, retaining the draft inputs.
        themeInput.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val chosen = SecureConfig.THEME_OPTIONS[position]
                if (chosen == config.themeMode) return
                storeDraft()
                config.themeMode = chosen
                Appearance.tap(themeInput)
                if (OverlayService.isRunning) {
                    startService(Intent(this@MainActivity, OverlayService::class.java)
                        .setAction(OverlayService.ACTION_APPEARANCE))
                }
                recreate()
            }
        }

        testButton = Button(this).apply {
            text = "测试 API（Flash 看图 + 搜索 + 答题）"
            isAllCaps = false
            setOnClickListener {
                Appearance.tap(it)
                testApi()
            }
        }
        model.addView(testButton)
        testStatus = text("测试结果将在这里显示", 13f, true).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = bg(colors.panel, 14)
            setTextIsSelectable(true)
        }
        model.addView(testStatus)
        toggle = Button(this).apply {
            isAllCaps = false
            textSize = 18f
            setTextColor(colors.accentText)
            background = bg(colors.accent, 14)
            layoutParams = LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(26) }
            setOnClickListener {
                Appearance.tap(it)
                if (OverlayService.isRunning) {
                    stopService(Intent(this@MainActivity, OverlayService::class.java))
                    Handler(Looper.getMainLooper()).postDelayed({ refreshState() }, 200)
                } else saveAndStart()
            }
        }
        content.addView(toggle)
        status = text("", 13f, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(14))
        }
        content.addView(status)
        content.addView(text(
            "首次开启需授权悬浮窗和录制整个屏幕。截取画面在本机压缩为 JPEG 后发送给 DeepSeek，" +
            "联网搜索使用同一密钥，无需另外配置。请勿在截图中暴露敏感信息。", 13f, true))

        // Separate existing controls into persistent pages so drafts survive tab switches.
        val home = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(48), dp(24), dp(24)) }
        home.addView(text("WEREAD  /  阅读助手", 12f, true).apply { setPadding(0, 0, 0, dp(12)) })
        home.addView(text("每日一答", 30f).apply { typeface = Typeface.DEFAULT_BOLD; setTextColor(colors.heading) })
        home.addView(text("读书有伴 · 答题轻松一点", 15f, true).apply { setPadding(0, dp(12), 0, dp(32)) })
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(24) }
        }
        content.removeView(toggle); hero.addView(toggle)
        content.removeView(status); hero.addView(status)
        home.addView(hero)
        home.addView(text("开启悬浮窗后，前往微信读书。\n点击获取答案，即可自动识题、搜索并判断。", 15f, true))
        home.addView(text("悬浮窗预览", 17f).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(36), 0, dp(14)) })
        val preview = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = bg(colors.surface, 20)
            elevation = dp(2).toFloat()
        }
        preview.addView(text("⠿", 24f, true))
        preview.addView(text("  第 2 个", 18f).apply { typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, -2, 1f))
        preview.addView(text("获取答案", 14f).apply {
            setTextColor(colors.accentText); background = bg(colors.accent, 14)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        })
        home.addView(preview)
        home.addView(text("拖动左侧手柄调整位置，点击右侧按钮开始答题。", 13f, true).apply { setPadding(0, dp(14), 0, dp(24)) })
        home.addView(text("01  截取题目    →    02  联网核对    →    03  查看答案", 12f, true))
        advanced.visibility = View.VISIBLE
        for (i in 0 until content.childCount) {
            val v = content.getChildAt(i)
            if (v is Button && v !== testButton) v.visibility = View.GONE
        }
        advanced.addView(CheckBox(this).apply {
            text = "开启日志"; setTextColor(colors.text); isChecked = AppLog.enabled
            setOnCheckedChangeListener { _, checked -> AppLog.enabled = checked; Appearance.tap(this) }
        })
        advanced.addView(text("仅开启后记录；保存在本机，最多约100KB。包含题目和模型文字，不保存截图或密钥。", 12f, true))
        model.addView(Button(this).apply {
            text = "保存模型设置"
            setOnClickListener { Appearance.tap(it); if (saveSettings()) android.widget.Toast.makeText(this@MainActivity, "已保存", 0).show()
                else android.widget.Toast.makeText(this@MainActivity, status.text, 1).show() }
        })
        val updateStatus = text("当前版本 " + packageManager.getPackageInfo(packageName, 0).versionName, 13f, true)
        advanced.addView(Button(this).apply {
            text = "检查更新"
            setOnClickListener { Appearance.tap(it); checkUpdate(this, updateStatus) }
        })
        advanced.addView(updateStatus)
        val logs = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(48), dp(24), dp(24)) }
        logs.addView(text("运行日志", 28f).apply { typeface = Typeface.DEFAULT_BOLD })
        val logText = text("", 13f).apply { setTextIsSelectable(true); typeface = Typeface.MONOSPACE }
        fun refreshLogs() { logText.text = AppLog.read().ifBlank { if (AppLog.enabled) "暂无日志，答题后点击刷新。" else "日志未开启，请到设置页开启后复现问题。" } }
        val actions = LinearLayout(this)
        listOf("刷新", "复制", "清空").forEachIndexed { index, title ->
            actions.addView(Button(this).apply { text = title; setOnClickListener {
                Appearance.tap(it)
                when(index) {
                    0 -> refreshLogs()
                    1 -> { val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("运行日志", AppLog.read())) }
                    2 -> { AppLog.clear(); refreshLogs() }
                }
            } }, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        logs.addView(actions); logs.addView(logText)
        val homeScroll = ScrollView(this).apply { addView(home) }
        val logScroll = ScrollView(this).apply { addView(logs) }
        val frame = android.widget.FrameLayout(this)
        listOf(homeScroll, scroll, logScroll).forEach { frame.addView(it); it.visibility = View.GONE }
        homeScroll.visibility = View.VISIBLE
        val shell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(colors.background) }
        shell.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { setPadding(dp(12), dp(8), dp(12), dp(12)); setBackgroundColor(colors.surface) }
        val pages = listOf(homeScroll, scroll, logScroll)
        val tabs = mutableListOf<Button>()
        listOf("主页", "设置", "日志").forEachIndexed { index, title ->
            val button = Button(this).apply {
                text = title; isAllCaps = false; setTextColor(if(index == 0) colors.accentText else colors.text); background = bg(if(index == 0) colors.accent else colors.surface, 14)
                setOnClickListener {
                    Appearance.tap(it)
                    pages.forEachIndexed { i, v -> v.visibility = if(i == index) View.VISIBLE else View.GONE }
                    tabs.forEachIndexed { i, b -> b.setTextColor(colors.accentText); b.alpha = if(i == index) 1f else 0.65f }
                    if(index == 2) refreshLogs()
                }
            }
            tabs.add(button); nav.addView(button, LinearLayout.LayoutParams(0, dp(50), 1f))
        }
        tabs.forEachIndexed { i, b -> b.alpha = if(i == 0) 1f else 0.65f }
        shell.addView(nav)
        shell.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        fun styleButtons(view: View) {
            if (view is Button && view !is CheckBox) {
                view.backgroundTintList = null
                view.background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                    bg(colors.accent, 14), null)
                view.setTextColor(colors.accentText)
                view.isAllCaps = false
                view.elevation = 0f
                val lp = view.layoutParams as? LinearLayout.LayoutParams
                lp?.let { it.setMargins(dp(4), dp(6), dp(4), dp(6)); view.layoutParams = it }
            }
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) styleButtons(view.getChildAt(i))
        }
        styleButtons(shell)
        setContentView(shell)
        shell.requestApplyInsets()
        refreshState()
    }

    private fun checkUpdate(button: Button, result: TextView) {
        button.isEnabled = false
        result.text = "正在检查新版本…"
        Thread {
            val outcome = runCatching {
                val conn = java.net.URL("https://api.github.com/repos/XiaoyuZhuang/weread/releases/latest").openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000; conn.readTimeout = 15000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "WeRead-Android")
                try {
                    check(conn.responseCode == 200) { "HTTP " + conn.responseCode }
                    val json = org.json.JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val tag = json.getString("tag_name")
                    val current = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
                    fun parts(v: String) = v.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
                    val a = parts(tag); val b = parts(current)
                    var newer = false
                    for (i in 0 until maxOf(a.size, b.size)) {
                        val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                        if (x != y) { newer = x > y; break }
                    }
                    tag to newer
                } finally { conn.disconnect() }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                button.isEnabled = true
                outcome.fold(onSuccess = { (tag, newer) ->
                    result.text = if (newer) "发现新版本 " + tag else "当前已是最新版本"
                    if (newer) android.app.AlertDialog.Builder(this)
                        .setTitle("发现新版本 " + tag)
                        .setMessage("是否打开 GitHub 发布页面，自行下载安装包？")
                        .setNegativeButton("暂不更新", null)
                        .setPositiveButton("前往下载") { _, _ ->
                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/XiaoyuZhuang/weread/releases/latest"))) }
                                .onFailure { result.text = "无法打开浏览器，请手动访问 GitHub 发布页" }
                        }.show().also { dialog ->
                            listOf(android.app.AlertDialog.BUTTON_POSITIVE, android.app.AlertDialog.BUTTON_NEGATIVE).forEach { which ->
                                dialog.getButton(which).apply { backgroundTintList = null; background = bg(colors.accent, 14); setTextColor(colors.accentText) }
                            }
                        }
                }, onFailure = { result.text = "检查失败，请稍后重试：" + it.message })
            }
        }.start()
    }

    private fun storeDraft() {
        try {
            if (keyInput.text.isNotBlank()) config.saveKey(keyInput.text.toString())
            config.endpoint = endpointInput.text.toString()
            config.visionModel = visionInput.text.toString()
            temperatureInput.text.toString().toDoubleOrNull()?.takeIf { it in 0.0..2.0 }
                ?.let { config.temperature = it }
            countInput.text.toString().toIntOrNull()?.takeIf { it in 3..8 }
                ?.let { config.searchCount = it }
        } catch (_: Exception) { /* Invalid draft is checked when the user starts. */ }
    }

    private fun saveSettings(): Boolean {
        if (keyInput.text.isNullOrBlank()) {
            status.text = "请先填写 DeepSeek API Key"
            return false
        }
        if (!endpointInput.text.toString().trim().startsWith("https://", true)) {
            status.text = "API 根地址必须使用 HTTPS"
            return false
        }
        if (visionInput.text.isNullOrBlank()) {
            status.text = "请填写 Flash 模型"
            return false
        }
        val count = countInput.text.toString().toIntOrNull()
        val temperature = temperatureInput.text.toString().toDoubleOrNull()
        if (count == null || count !in 3..8 || temperature == null || temperature !in 0.0..2.0) {
            status.text = "搜索结果数量需3～8，温度需0～2"
            return false
        }
        return try {
            config.saveKey(keyInput.text.toString())
            config.endpoint = endpointInput.text.toString()
            config.visionModel = visionInput.text.toString()
            config.searchCount = count
            config.temperature = temperature
            config.hapticEnabled = hapticToggle.isChecked
            config.showImageDebug = debugToggle.isChecked
            true
        } catch (_: Exception) {
            status.text = "密钥无法安全保存，请检查设备安全设置"
            false
        }
    }

    private fun testApi() {
        if (!saveSettings()) { testStatus.text = status.text; return }
        testButton.isEnabled = false
        testStatus.text = "测试中：上传测试图片 → 搜索 → Flash 判断……"
        val bitmap = Bitmap.createBitmap(780, 520, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(9, 38, 71))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
        }
        canvas.drawText("中国的首都是哪里？", 48f, 120f, paint)
        canvas.drawText("北京", 80f, 260f, paint)
        canvas.drawText("上海", 80f, 380f, paint)
        client.ask(bitmap, config.loadKey(), config.endpoint, config.visionModel,
            config.temperature, config.searchCount,
            onVision = { runOnUiThread { if (!isDestroyed) testStatus.text = "图片识别成功，联网搜索中……" } },
            onSearching = { runOnUiThread { if (!isDestroyed) testStatus.text = "搜索成功，Flash 判断中……" } }
        ) { result ->
            runOnUiThread {
                if (!isDestroyed) {
                    testButton.isEnabled = true
                    result.fold(
                        onSuccess = { answer ->
                            testStatus.text = if (answer.index == 1)
                                "测试通过 · 已搜索 · ${answer.elapsedMs} ms"
                            else "搜索成功，但测试题回答异常（第${answer.index}项）"
                        },
                        onFailure = { testStatus.text = "测试失败：${it.message ?: "未知错误"}" }
                    )
                }
            }
        }
    }

    private fun saveAndStart() {
        if (!saveSettings()) return
        if (!Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = true
            status.text = "请允许显示悬浮窗"
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return
        }
        requestScreenCapture()
    }

    override fun onResume() {
        super.onResume()
        Handler(Looper.getMainLooper()).postDelayed({
            refreshState()
            if (waitingForOverlayPermission && Settings.canDrawOverlays(this)) {
                waitingForOverlayPermission = false
                requestScreenCapture()
            }
        }, 250)
    }

    private fun requestScreenCapture() {
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        status.text = "请授权录制整个屏幕"
        startActivityForResult(mgr.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Deprecated("Compatible with Android 8+")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) {
            status.text = "未取得屏幕捕获授权"
            return
        }
        val service = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
            putExtra(OverlayService.EXTRA_RESULT_DATA, data)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service)
            else startService(service)
            status.text = "悬浮窗启动中……"
            Handler(Looper.getMainLooper()).postDelayed({ refreshState() }, 900)
        } catch (error: Exception) {
            status.text = "启动失败：${error.message ?: "系统限制"}"
        }
    }

    private fun refreshState() {
        toggle.text = if (OverlayService.isRunning) "关闭悬浮窗" else "开启悬浮窗"
        if (OverlayService.isRunning) status.text = "已开启，请切换到微信读书"
    }

    override fun onDestroy() {
        client.close()
        super.onDestroy()
    }
    companion object { private const val REQUEST_CAPTURE = 1027 }
}
