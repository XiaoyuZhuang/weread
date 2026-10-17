# WeRead · 微信读书每日一答

Android 悬浮窗答题助手：**点击“获取答案” → MediaProjection 截屏 → ML Kit 中文 OCR（本地）→ Jev SystemOne Choice API → 显示第几个选项**。不自动点击答案。

## 安装

在 [Releases](https://github.com/XiaoyuZhuang/weread/releases/tag/preview) 下载 `app-debug.apk`。仓库每次推送都会使用 GitHub Actions 编译 Android APK，Actions 同时保存构建产物。当前为测试版，GitHub Actions 默认 debug 签名可能因构建环境不同而变化，遇到覆盖安装提示证书不同请先备份需要的数据再卸载旧版。

## 使用

1. 打开软件，输入 Jev API Key。如果所用 Jev API 不是默认服务，展开“高级配置”修改**接口地址、模型名、选项数量**（2–4，默认 2）。
2. 点击“开启悬浮窗”，授权“显示在其他应用上层”，再授权屏幕录制。Android 14/15 必须选择**整个屏幕**，否则切换到微信读书可能无法捕获画面。
3. 打开微信读书每日一答，看到题目点击悬浮窗的“获取答案”。
4. 等待显示 **第 1 个 / 第 2 个**（按截图从上到下），自行点击对应选项。
5. 拖动浮窗标题栏可以移动悬浮窗；回到主界面可关闭录屏和悬浮窗。

截图仅在设备上通过 ML Kit 进行 OCR。上传到模型的只有识别的文字（包含截图中被 OCR 识别出的任何敏感文字，务必确保屏幕没有个人信息），而非图片。API Key 由 Android Keystore AES-GCM 加密保存在本机，不上传到 GitHub。请仅使用你信任的 HTTPS 服务地址。

## Jev API

默认使用文档中提供的兼容实现：
`POST https://jev-ai.org/api/v1/systemone/`，模型 `jev-1.13`。

它是 Jev 的**决策**接口，不是 `/v1/chat/completions`。程序发送：

```json
{
  "model": "jev-1.13",
  "state": "OCR 逐行文本（保留从上到下顺序）...",
  "questions": {
    "answer": {
      "type": "choice",
      "instructions": "从题目和选项中选择正确答案",
      "criteria": {
        "1": "截图里从上到下第1个真正的答题选项",
        "2": "截图里从上到下第2个真正的答题选项"
      }
    }
  }
}
```

读取 `answers.answer.choice`。其他 Jev 服务可能使用 `/v1/systemone` 或其他 API 路径，需要在软件高级设置里匹配正确地址。**只有 API 与上述 JSON 协议相容才可以直接用**。

### 已知限制

- Jev 适合对选项作快速分类决策，但不是通用知识问答模型；对文学、历史等事实题的正确率并无保证。“置信度”不等于真实正确率。
- OCR 行顺序和题目布局可能误判，尤其是包含长选项、插图、或与常见模板不同的题目；不支持看图答题。
- 部分 App/安全页面禁止系统截屏，此时无法识别。
- 录屏权限仅在本次启动会话内有效。Android 14+ 每次重新开启需经系统授权；不会后台静默录屏。Android 的前台服务通知是必需的。
- 测试版尚未在真实手机上完成端到端验证；使用时请留意接口/系统版本兼容性。

## 技术栈

Kotlin · Android 8.0+ · MediaProjection · TYPE_APPLICATION_OVERLAY · ML Kit 中文 OCR · Android Keystore · Jev Choice API。没有无障碍点击操作。

## 构建

JDK 17 + Android SDK 35 + Gradle 8.10.2：

```bash
gradle assembleDebug
```

APK 生成位置：`app/build/outputs/apk/debug/app-debug.apk`。
