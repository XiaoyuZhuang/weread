# WeRead · 微信读书每日一答

Android 悬浮窗答题助手：**点击“获取答案” → MediaProjection 截屏 → ML Kit 中文 OCR（本地）→ Groq GPT-OSS + 服务器端网页搜索 → 显示答案序号**。不自动点击答案。

## 安装与使用

1. 前往 [预览版 Release](https://github.com/XiaoyuZhuang/weread/releases/tag/preview) 下载 `app-debug.apk`。
2. 前往 [Groq 控制台](https://console.groq.com/keys) 创建 API Key。回到软件填写密钥。**注意 Groq 不是 xAI 的 Grok。**
3. 默认已经配置了 `openai/gpt-oss-120b`、Groq 官方 Chat Completions API 地址，并开启联网搜索。
4. 点击开启悬浮窗，授权悬浮窗和「录制整个屏幕」权限。
5. 打开微信读书每日一答，每题点击悬浮窗「获取答案」，等待「第 1 个 / 第 2 个」等结果。程序自动区分2选1/4选1（不确定时按4个）。
6. **排查截图/OCR：点击悬浮窗底部的「查看OCR识别文本」即可展开滚动显示本次提取的全部文字，即使随后 Groq 返回 HTTP 400，OCR 文本仍保留；下一次点击「获取答案」会清空旧文本并显示本次状态。**
7. 可以先在主界面点击「测试 API」，验证密钥、模型以及联网搜索能力。

截图只在本机通过 ML Kit 做 OCR，向 Groq 上传的是 OCR **文字**，不是图片。确保画面中没有不希望传输的私人信息。API Key 由 Android Keystore AES-GCM 加密保存在设备上。升级后需重新填写 **Groq** 密钥：旧版的 Jev 密钥不复用。

## 服务器端浏览器搜索

默认调用：
- POST `https://api.groq.com/openai/v1/chat/completions`
- model: `openai/gpt-oss-120b`
- tools: `[{"type":"browser_search"}]`
- tool_choice: `required`（开启时强制使用搜索工具）
- reasoning_effort: `low`（加速知识问答）

Groq 会在服务器端完成搜索、分析和回复，无须本地浏览器或搜索 API。关闭高级设置中的联网搜索选项可以跳过搜索，但正确率可能下降。

官方文档：https://console.groq.com/docs/tool-use/built-in-tools/browser-search

模型提示必须返回两行：`COUNT=2` 和 `ANSWER=1`（仅为示例）。程序校验答案编号不超过识别选项数量。**不能保证模型总能正确识别2或4个选项；也不能保证正确率。**

## 常见问题

- **HTTP 401 / 403**：API 密钥、账户权限或搜索权限限制。
- **HTTP 429**：触及 Groq 账户的频率或配额限制，稍后重试。
- **模型不支持 browser_search**：使用默认 GPT-OSS 120B，或关闭联网搜索。
- **请求耗时长**：真实网页搜索可能花费数秒乃至更久；已设置 45 秒读取超时。
- **API 测试失败**：先在主界面点击「测试 API」观察明确错误码，检查 API 密钥。
- **Android 无法截图**：检查是否授权了**整个屏幕**、是否被系统禁止录屏。
- **无法覆盖安装**：预览版通过 GitHub Actions 默认 debug 签名构建，不同运行可能出现签名不同，请先备份自己的数据再卸载旧版。
- **旧版配置**：原 Jev 密钥和接口参数不会自动迁移，避免误发到错误服务。

尚未在真实手机上完成整条流程的验证；GitHub Actions 仅验证编译。测试 API 会实际发起一次带搜索的请求并占用使用额度。

## 项目

Kotlin · Android 8.0+ · MediaProjection · TYPE_APPLICATION_OVERLAY · ML Kit 中文 OCR · Android Keystore · Groq API。

## 编译

JDK 17、Android SDK 35、Gradle 8.10.2：`gradle assembleDebug`，生成 `app/build/outputs/apk/debug/app-debug.apk`。
