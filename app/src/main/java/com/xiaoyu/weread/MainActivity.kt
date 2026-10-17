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
    private lateinit var countInput: EditText
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

        content.addView(label("Jev API 密钥"))
        keyInput = field("输入你的 API Key", config.loadKey()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        content.addView(keyInput)

        val advanced = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val advancedButton = Button(this).apply {
            text = "高级配置（接口 / 模型 / 选项数） ▾"
            isAllCaps = false
            setOnClickListener {
                advanced.visibility = if (advanced.visibility == View.GONE) View.VISIBLE else View.GONE
            }
        }
        content.addView(advancedButton)
        advanced.addView(label("Jev 接口地址（HTTPS）"))
        endpointInput = field("https://…/systemone/", config.endpoint)
        advanced.addView(endpointInput)
        advanced.addView(label("模型 ID"))
        modelInput = field("jev-1.13", config.model)
        advanced.addView(modelInput)
        advanced.addView(label("每题选项数量（2 到 4，默认 2）"))
        countInput = field("2", config.optionCount.toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        advanced.addView(countInput)
        content.addView(advanced)

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
                "屏幕内容只在本机进行 OCR，之后仅将识别出的文字发送给配置的 Jev 接口。\n" +
                "请在屏幕录制授权时选择“整个屏幕”。"
            textSize = 13f
            setLineSpacing(dp(4).toFloat(), 1f)
            setTextColor(Color.rgb(105, 122, 140))
        })
        setContentView(screen)
        refreshState()
    }

    private fun saveAndStart() {
        val key = keyInput.text.toString().trim()
        if (key.isBlank()) {
            status.text = "请先填写 API Key"
            return
        }
        val endpoint = endpointInput.text.toString().trim()
        if (!endpoint.startsWith("https://", true)) {
            status.text = "接口地址必须以 https:// 开头"
            return
        }
        val count = countInput.text.toString().toIntOrNull()
        if (count == null || count !in 2..4) {
            status.text = "选项数量请填写 2、3 或 4"
            return
        }
        try {
            config.saveKey(key)
            config.endpoint = endpoint
            config.model = modelInput.text.toString().trim().ifEmpty { "jev-1.13" }
            config.optionCount = count
        } catch (_: Exception) {
            status.text = "无法安全保存密钥，请检查设备锁屏安全设置"
            return
        }

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

    companion object { private const val REQUEST_CAPTURE = 1027 }
}
