# WeRead · 微信读书每日一答

Android 悬浮窗答题助手：**点击“获取答案” → MediaProjection 截屏 → ML Kit 中文 OCR（本地）→ Groq GPT-OSS + 服务器端网页搜索 → 显示答案序号**。不自动点击答案。

## 安装与使用

1. 前往 [预览版 Release](https://github.com/XiaoyuZhuang/weread/releases/tag/preview) 下载 `weread-release.apk`（仅在签名 Secrets 设置后构建并发布）。
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
- reasoning_effort: 默认首次 `low`，工具调用失败重试默认 `medium`；均可在高级设置中修改
- temperature: 默认首次 `0.2`、重试 `0.1`；均可在高级设置中修改

Groq 会在服务器端完成搜索、分析和回复，无须本地浏览器或搜索 API。遇到明确的“强制搜索但模型没有调用工具”HTTP 400，程序自动再请求一次，增强搜索指令、降低温度并将推理等级临时提高到 medium。**两次都必须使用搜索工具；不会通过关闭搜索来掩盖失败。** 其他 400、401、403、429 不自动重试；重试会多消耗一次请求额度并增加等待。关闭高级设置中的联网搜索选项可以跳过搜索，但正确率可能下降。

官方文档：https://console.groq.com/docs/tool-use/built-in-tools/browser-search

模型提示必须返回两行：`COUNT=2` 和 `ANSWER=1`（仅为示例）。程序校验答案编号不超过识别选项数量。**不能保证模型总能正确识别2或4个选项；也不能保证正确率。**

## 固定签名与覆盖更新（v0.3.0 起）

签名私钥**不要上传到公开仓库**。请在 GitHub 仓库 Settings → Secrets and variables → Actions 新增两个 Repository Secrets：

- `WEREAD_SIGNING_KEYSTORE_BASE64`：PKCS12 签名文件的 Base64 编码，完整单行。
- `WEREAD_SIGNING_PASSWORD`：PKCS12 签名密码（同时用于 keystore 与 key entry）。
- 固定别名：`weread-release`。

Actions 将使用这两个私密值进行 `assembleRelease`，检查签名并将 `weread-release.apk` 上传到 [preview Release](https://github.com/XiaoyuZhuang/weread/releases/tag/preview)。如果尚未配置两个 Secrets，Actions 仍会编译并测试 debug 版，但**不会覆盖现有 Release**。设置好后，到 Actions 运行一次 `Android APK → Run workflow`。

旧版 `app-debug.apk` 的签名证书与新证书不同，因此第一次切换到 `weread-release.apk` 仍需要卸载旧版一次。此后保持同一签名证书、相同 `applicationId` 和递增 `versionCode` 就能直接覆盖安装。注意旧版 App 设置/API 密钥可能因此被清空。

## OCR 填空横线识别（v0.3.0）

对 OCR 通常丢掉的长横线，尝试使用本机截图中的像素补充一个 `[填空]` 标记，例如 `做张[填空]智`。这是一种保守的图像检测启发式策略，**不能保证每条横线都识别正确**，尤其是横线与其他图形混杂时。可通过悬浮窗的「查看OCR识别文本」检查。

## 常见问题

- **HTTP 400（未调用搜索工具）**：仅对该报错自动重试 1 次；若仍失败会提示“联网搜索连续两次未调用成功”，不是截图或 OCR 失败。
- **HTTP 401 / 403**：API 密钥、账户权限或搜索权限限制。
- **HTTP 429**：触及 Groq 账户的频率或配额限制，稍后重试。
- **模型不支持 browser_search**：使用默认 GPT-OSS 120B，或关闭联网搜索。
- **请求耗时长**：真实网页搜索可能花费数秒乃至更久；已设置 45 秒读取超时。
- **API 测试失败**：先在主界面点击「测试 API」观察明确错误码，检查 API 密钥。
- **Android 无法截图**：检查是否授权了**整个屏幕**、是否被系统禁止录屏。
- **无法覆盖安装**：旧版 debug 和新版固定 release 签名不同，第一次迁移需卸载一次；之后固定 release 签名支持覆盖更新。
- **旧版配置**：原 Jev 密钥和接口参数不会自动迁移，避免误发到错误服务。

尚未在真实手机上完成整条流程的验证；GitHub Actions 仅验证编译。测试 API 会实际发起一次带搜索的请求并占用使用额度。

## 项目

Kotlin · Android 8.0+ · MediaProjection · TYPE_APPLICATION_OVERLAY · ML Kit 中文 OCR · Android Keystore · Groq API。

## 编译

JDK 17、Android SDK 35、Gradle 8.10.2：`gradle assembleDebug testDebugUnitTest`；有签名 Secrets 时 Actions 还会运行 `assembleRelease` 并发布固定签名版 APK。


## 签名提醒

建议在 GitHub Actions Secrets 保存 Android 签名所需的 keystore Base64 和密码，不要把签名私钥放入公开仓库。固定签名可用于后续覆盖安装；与旧调试签名不同的首个版本仍需迁移一次。
