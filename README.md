# WeRead · 微信读书每日一答 v0.4.0

打开微信读书答题界面，点击悬浮窗「获取答案」，截取**当前屏幕**，压缩 JPEG 后上传给 DeepSeek Flash 视觉模型。视觉模型直接读取题目和选项（包括填空横线）；随后 DeepSeek V4 Pro 通过 **Responses API 自带的服务器端 web_search** 联网核对事实，仅当返回结果含有已完成的 `web_search_call` 才显示选项编号。

## 使用

1. 安装 [固定签名 Release APK](https://github.com/XiaoyuZhuang/weread/releases/tag/preview)。
2. 在 [DeepSeek 官方 API](https://platform.deepseek.com/) 获取 API Key；软件只需要填写这个密钥。
3. 点击「开启悬浮窗」，授予显示悬浮窗和录制整个屏幕权限。
4. 在微信读书中点击「获取答案」，等待图片识别和服务器端联网搜索完成。
5. 下方「查看图片理解文本」可以检查视觉模型实际识别出的题干、选项。

**不再使用手机本地 OCR、ML Kit，亦不将图片传给搜索服务**。服务器端搜索收到的是 DeepSeek Flash 识别出的文本。手机上传的 JPEG 会按照最长边不超过 1600 像素、质量 78 压缩；如图片内容较细或因压缩失真，可能需要进一步调整。照片里可能包含通知、时间、个人信息，请先确认画面内容。

## 为什么是两次请求而不是一次

官方目前明确提供的是：
- 图片理解：`deepseek-flash`，Anthropic Messages API，`POST /anthropic/v1/messages`。
- 服务器端网络搜索：`deepseek-v4-pro`，Responses API，`POST /responses`，`tools:[{"type":"web_search"}]`，`tool_choice:"required"`。

`deepseek-flash` 的 Responses `web_search` 被官方标为忽略，而 `deepseek-v4-pro` 当前未被官方列为图片输入模型。所以采用**两阶段一次点击**，两次 API 请求共用同一个密钥，不需要本地搜索、爬虫或其他服务器。接口根地址默认 `https://api.deepseek.com`，高级设置可更改根地址，以及两个模型名。后端如果没有支持两个必要端点，就无法完成整个流程。

搜索执行需通过服务器返回的 `web_search_call` 项确认为 `completed`，否则**不会伪装成已搜索并显示答案**。正常返回也不代表一定答对。

官方参考：
- https://api-docs.deepseek.com/guides/vision/
- https://api-docs.deepseek.com/guides/anthropic_api/
- https://api-docs.deepseek.com/guides/responses_api/

## API 测试

首页「测试 API（看图 + 联网搜索）」会生成一张小型题目图片，执行视觉读取和搜索两次请求，实际消耗额度。模型的回复必须有 `QUESTION/OPTION` 以及最终 `COUNT/ANSWER`，搜索结果必须包含已完成的 web_search_call。

## 固定签名升级

沿用 v0.3.0 的固定签名和 `applicationId`，`versionCode` 已递增到 6，理论上可从 v0.3.0 **直接覆盖安装**，不需要卸载。签名私钥存于 GitHub Actions Secrets `WEREAD_SIGNING_KEYSTORE_BASE64`、`WEREAD_SIGNING_PASSWORD`，从不存储在公开仓库。构建工作流会编译、运行测试、签名并上传 `weread-release.apk` 到 preview Release。

## 已知限制

- 目前采用 Android MediaProjection 获取屏幕画面，需要用户授权；软件不自动点击答案。
- 图片处理与搜索分开会增加两次 API 调用的耗时，搜索速度取决于实际网络与服务器。
- 一定强制搜索。DeepSeek 不支持工具或工具未执行时直接报错，不会退回无搜索模式。
- 没有官方 API 密钥的端到端实测；GitHub Actions 通过只代表编译和单元测试通过。
- 当前图片压缩不能保证所有小字都清晰；字体过小的题目可后续提高最长边上限。
