package com.xiaoyu.weread

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** API key and one tap to start: everything else stays out of the way. */
class MainActivity : Activity() {
    private lateinit var config: SecureConfig
    private lateinit var keyInput: EditText
    private lateinit var endpointInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var searchToggle: CheckBox
    private lateinit var testButton: Button
    private val groqClient = GroqClient()
    private lateinit var status: TextView
    private lateinit var toggle: Button
    private var waitingForOverlayPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = SecureConfig(this)
        buildUi()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun background(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun field(hint: String, value: String): EditText = EditText(this).apply {
        setSingleLine(true)
        setText(value)
        setHint(hint)
        textSize = 15f
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = background(Color.WHITE, 11)
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) }
    }

    private fun label(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(Color.rgb(85, 99, 114))
        setPadding(0, dp(9), 0, dp(7))
    }

    private fun buildUi() {
        val screen = ScrollView(this).apply { setBackgroundColor(Color.rgb(244, 248, 252)) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(44), dp(24), dp(24))
        }
        screen.addView(content)
        val heading = TextView(this).apply {
            text = "微信读书 · 每日一答"
            setTextColor(Color.rgb(22, 45, 68))
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
        }
        content.addView(heading)
        content.addView(TextView(this).apply {
            text = "点一下悬浮窗，截图识别并显示正确选项"
            textSize = 14f
            setTextColor(Color.rgb(100, 121, 139))
            setPadding(0, dp(10), 0, dp(22))
        })

        content.addView(label("Groq API 密钥"))
        keyInput = field("输入你的 API Key", config.loadKey()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        content.addView(keyInput)

        val advanced = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val advancedButton = Button(this).apply {
            text = "高级配置（接口 / 模型 / 联网搜索） ▾"
            isAllCaps = false
            setOnClickListener {
                advanced.visibility = if (advanced.visibility == View.GONE) View.VISIBLE else View.GONE
            }
        }
        content.addView(advancedButton)
        advanced.addView(label("Groq 接口地址（HTTPS）"))
        endpointInput = field(SecureConfig.DEFAULT_ENDPOINT, config.endpoint)
        advanced.addView(endpointInput)
        advanced.addView(label("模型 ID"))
        modelInput = field(SecureConfig.DEFAULT_MODEL, config.model)
        advanced.addView(modelInput)
        searchToggle = CheckBox(this).apply {
            text = "开启联网搜索（默认开启）"
            isChecked = config.browserSearch
            textSize = 15f
            setTextColor(Color.rgb(37, 59, 80))
        }
        advanced.addView(searchToggle)
        content.addView(advanced)

        testButton = Button(this).apply {
            text = "测试 API（验证密钥、模型和联网搜索）"
            isAllCaps = false
            setOnClickListener { testApi() }
        }
        content.addView(testButton)

        toggle = Button(this).apply {
            isAllCaps = false
            textSize = 18f
            setTextColor(Color.WHITE)
            background = background(Color.rgb(20, 129, 231), 14)
            layoutParams = LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(26) }
            setOnClickListener {
                if (OverlayService.isRunning) {
                    stopService(Intent(this@MainActivity, OverlayService::class.java))
                    Handler(Looper.getMainLooper()).postDelayed({ refreshState() }, 200)
                } else {
                    saveAndStart()
                }
            }
        }
        content.addView(toggle)
        status = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(95, 115, 131))
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(14))
        }
        content.addView(status)
        content.addView(TextView(this).apply {
            text = "首次开启需要授权“显示在其他应用上层”和“录制屏幕”。\n" +
                "屏幕内容只在本机进行 OCR，之后仅将识别出的文字发送给配置的 Groq 接口。\n" +
                "请在屏幕录制授权时选择“整个屏幕”。"
            textSize = 13f
            setLineSpacing(dp(4).toFloat(), 1f)
            setTextColor(Color.rgb(105, 122, 140))
        })
        setContentView(screen)
        refreshState()
    }

    private fun testApi() {
        if (!saveSettings()) return
        testButton.isEnabled = false
        status.text = "正在测试 Groq API（会使用一次请求额度）…"
        val sample = "题目：中国的首都是哪座城市？\n选项：\n1. 北京\n2. 上海"
        groqClient.ask(sample, config.loadKey(), config.endpoint, config.model, config.browserSearch) { result ->
            runOnUiThread {
                if (!isDestroyed) {
                    testButton.isEnabled = true
                    result.fold(
                        onSuccess = { answer ->
                            status.text = if (answer.index == 1)
                                "测试成功 · 第1项 · ${answer.elapsedMs} ms"
                            else "API 连通，但测试题回答异常（第${answer.index}项）"
                        },
                        onFailure = { status.text = "测试失败：${it.message ?: "未知错误"}" }
                    )
                }
            }
        }
    }

    private fun saveSettings(): Boolean {
        val key = keyInput.text.toString().trim()
        if (key.isBlank()) {
            status.text = "请先填写 Groq API Key"
            return false
        }
        val endpoint = endpointInput.text.toString().trim()
        if (!endpoint.startsWith("https://", true)) {
            status.text = "接口地址必须以 https:// 开头"
            return false
        }
        if (modelInput.text.isNullOrBlank()) {
            status.text = "请填写模型 ID"
            return false
        }
        try {
            config.saveKey(key)
            config.endpoint = endpoint
            config.model = modelInput.text.toString().trim()
            config.browserSearch = searchToggle.isChecked
        } catch (_: Exception) {
            status.text = "无法安全保存密钥，请检查设备锁屏安全设置"
            return false
        }
        return true
    }

    private fun saveAndStart() {
        if (!saveSettings()) return

        if (!Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = true
            status.text = "请允许显示在其他应用上层"
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
        status.text = "请授权屏幕录制（选择整个屏幕）"
        startActivityForResult(mgr.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Deprecated("Old Activity result API works from API 26 onward.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) {
            status.text = "未获得录屏权限，无法截图"
            return
        }
        val service = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
            putExtra(OverlayService.EXTRA_RESULT_DATA, data)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service)
            else startService(service)
            status.text = "悬浮窗启动中，切换到微信读书即可使用"
            Handler(Looper.getMainLooper()).postDelayed({ refreshState() }, 900)
        } catch (error: Exception) {
            status.text = "启动失败：${error.message ?: "系统限制"}"
        }
    }

    private fun refreshState() {
        toggle.text = if (OverlayService.isRunning) "关闭悬浮窗" else "开启悬浮窗"
        if (OverlayService.isRunning) status.text = "已开启 · 请切换到微信读书"
    }

    override fun onDestroy() {
        groqClient.close()
        super.onDestroy()
    }

    companion object { private const val REQUEST_CAPTURE = 1027 }
}
