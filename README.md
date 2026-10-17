# WeRead · 微信读书每日一答 v0.5.0

微信读书每日一答 Android 悬浮窗：点击「获取答案」→ 屏幕截图 → DeepSeek Flash 识别压缩截图 → **DeepSeek 官方 Search API** 联网检索 → Flash 根据真实搜索资料判断选项 → 显示答案编号。

不使用本地 OCR、不自动点击答案，也不需要自行部署搜索引擎。

## 安装和使用

1. 从 [GitHub Releases · 最新正式版](https://github.com/XiaoyuZhuang/weread/releases/latest) 下载固定签名的 `weread-release.apk`。
2. 输入你的 **DeepSeek 官方 API Key**；一个密钥同时用于 Flash 和官方搜索。
3. 点「开启悬浮窗」，授权悬浮窗和录制整个屏幕权限。
4. 打开微信读书的每日一答，每道题点击悬浮窗「获取答案」。点「查看图片理解文本」可以查看 Flash 实际读出的题干和选项。
5. 主界面「测试 API」可使用程序生成的模拟题目验证三阶段请求，实际占用模型及搜索额度。

## 三阶段 API 流程

默认根地址：`https://api.deepseek.com`（可在设置中更改；需要兼容下面两类端点）。
- **Flash 看图**：`POST /anthropic/v1/messages`，模型 `deepseek-flash`，上传最长边不超过 1600px、JPEG 质量 78 的压缩图片。
- **官方搜索**：`POST /v1/tools/search`，`Authorization: Bearer <API Key>`，带 `query`、`count`、`need_content:false`；默认获取 5 条结果。
- **Flash 最终答题**：再次 `POST /anthropic/v1/messages`，发送题干、选项及官方搜索实际返回的网页标题、URL、摘要。要求仅返回 `COUNT`、`ANSWER`。

搜索必需成功：若 Search API 没有返回非空有效资料，就报错，不会退回无搜索模式。通常两次 Flash 模型推理加一次搜索，实际速度受三次 API 耗时影响，需要真机测试。

- 官方搜索文档：https://api-docs.deepseek.com/zh-cn/guides/search/
- 官方图片文档：https://api-docs.deepseek.com/guides/vision/
- Anthropic 兼容接口：https://api-docs.deepseek.com/guides/anthropic_api/

## 深浅色模式与震动

进入首页「设置」：
- **外观**：默认跟随手机系统，也可以指定浅色或深色；主界面和悬浮窗均适配。
- **点击震动**：默认开启，支持关闭；调用 Android 系统触觉反馈，尊重系统触觉设置，不需要额外震动权限。
- 还可以设置 API 根地址、Flash 模型、每次搜索条数（3–8）、模型温度（0–2，默认 0.2）。

## 安装签名和升级

维持旧版 `applicationId=com.xiaoyu.weread` 和固定发布签名。版本号 `versionCode=7`，从之前的 v0.3.0/v0.4.0 固定签名版本可以正常覆盖升级。发布密钥来自 GitHub Actions Secrets：
- `WEREAD_SIGNING_KEYSTORE_BASE64`
- `WEREAD_SIGNING_PASSWORD`

每次向 `main` 分支推送代码并成功完成 GitHub Actions 构建后，会按 `versionName` 发布**正式版**（如 `v0.5.2`），不是预览版。通过 [GitHub Releases 最新正式版](https://github.com/XiaoyuZhuang/weread/releases/latest) 下载 `weread-release.apk`。如需发布下一个正式版本，请先递增 `versionCode` 和 `versionName`；若 Secrets 缺失，不发布未签名包。

## 注意

- Android 需要系统录屏授权才能截取其他应用画面；程序只在点击按钮后处理抓到的截图。
- 图像内容上传 DeepSeek 云端，图片包含的个人信息也可能被发送；尽量避免在敏感页面使用。
- Flash 识图、网站搜索与模型判断都有可能出错；程序只展示建议答案。
- 测试 API 及每日答题都会消耗官方额度；需要网络。
- 构建成功不等于使用你的密钥完成真实的联网端到端测试。

## v0.5.1：轻量化图标与两行悬浮窗

- 内置 Android 自适应矢量图标：保留书本、问号和对勾三个符号，只使用 XML VectorDrawable，不打包高清 PNG，几乎不增加 APK 体积。
- 默认悬浮窗只有两行：第一行可拖动符号和「等待获取答案」/识别、搜索进度/答案；第二行较小的「获取答案」按钮。
- 「查看图片理解文本」是调试功能，默认关闭。打开主界面「设置 → 显示图片理解文本（调试用）」可临时显示入口；关闭后自动恢复两行布局。
- 保留 Flash 看图、DeepSeek Search API 联网搜索、震动、深浅色模式和原来的固定签名。versionCode 升至 8，可覆盖已安装的固定签名版本。

## v0.5.2：极简单行悬浮窗

- 默认悬浮窗缩成**单行**：左侧拖动符号、中间状态或答案、右侧小号「获取答案」按钮。
- 状态文字改为「等待答题」「截图中」「识图中」「搜索中」「思考中」「第 N 个」，不再显示模型或供应商名称。响应时间仍保留在无障碍描述中。
- 调试选项保持默认关闭，仅开启后才出现图片理解文本入口。深浅色模式、震动、Flash+DeepSeek 搜索流程均不改变。
- versionCode 9，继续使用固定签名，支持覆盖更新。

## 正式版发布流程

`main` 是唯一开发主分支；推送后 Actions 使用 GitHub Secrets 中的原有固定证书签名。成功后按 Android `versionName` 自动创建/更新 `vX.Y.Z` **正式 Release**，并附带 `weread-release.apk`。旧的 `preview` 预览 Release 会在新正式版成功发布后清理，避免普通用户混淆。
