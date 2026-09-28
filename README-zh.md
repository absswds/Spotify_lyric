# Lyrics Card

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Spotify](https://img.shields.io/badge/Spotify-Android%20Remote-green.svg)](https://developer.spotify.com/documentation/android)

**专为 Android 上的 Spotify 打造的歌词应用：通过标准的 Android 媒体会话（MediaSession）和 MediaStyle 通知，把实时同步歌词放进系统的实时媒体区域——媒体通知 / 控制中心卡片、锁屏，以及各厂商系统的"灵动岛"式胶囊 / 实时活动；另附一个 Apple Music / Lyricify 风格的精美歌词播放器。无需 Root，不修改 Spotify。**

[English](README.md) · **简体中文** · [繁體中文](README-zh-TW.md) · [日本語](README-ja.md)

> ⚠️ **版权提醒**：歌词版权归原作者和唱片公司所有。网易云、QQ 音乐、酷狗是非公开接口，使用和缓存它们的歌词**可能涉及版权和服务条款问题**。如果只是自己在自己手机上听歌时看，一般风险很低；**不要分享、导出、公开发布缓存的歌词，也不要拿来商用**。详见[致谢与引用的项目](#致谢与引用的项目) · [内容来源与合规](#内容来源与合规)（非法律意见）。

> 💳 **播放控制需要 Spotify Premium**：Spotify 官方文档写明，通过网络接口控制播放（暂停、切歌、拖动进度等）[只对 Premium 用户有效](https://developer.spotify.com/documentation/web-api/reference/skip-users-playback-to-next-track)；免费账号在手机上[始终是智能随机播放](https://support.spotify.com/us/article/shuffle-play/)，跳歌次数也[有限制](https://support.spotify.com/us/article/your-premium-benefits/)。所以免费账号用本应用的播放、切歌、进度、随机、循环按钮可能无效，但歌词显示不受影响。

[功能](#功能) · [快速开始](#快速开始) · [权限说明](#权限说明) · [更新日志](#更新日志) · [架构](#架构) · [致谢与引用的项目](#致谢与引用的项目) · [内容来源与合规](#内容来源与合规) · [免责声明](#免责声明)

---

## 功能

### 系统实时媒体区域

- 当前歌词行显示在原生 MediaStyle 通知、控制中心媒体卡片、锁屏，以及厂商系统中读取媒体会话的"岛" / 胶囊式实时活动里。
- Spotify 重新抢占媒体卡片首位时（切歌、恢复播放、播放从其他设备切回、其他应用播放音频之后、Spotify 重启之后），自动把首位夺回给歌词会话。

主要在 OPPO ColorOS 上测试，锁屏岛和流体云效果最好。

<!-- TODO: add screenshots (owner will supply the images), then move these lines out of the comment:
![ColorOS lock-screen island](docs/images/coloros-lockscreen-island.png)
![ColorOS Fluid Cloud](docs/images/coloros-fluid-cloud.png)
-->

> 作者手上只有有限的（ColorOS）设备，没法测试更多系统。如果你感兴趣，或者在其他厂商系统（MIUI/HyperOS、OriginOS、MagicOS、One UI 等）上表现异常，欢迎[提 Issue](https://github.com/absswds/Spotify_lyric/issues) 或发 PR。

### 播放器

- 从封面取色的网格渐变背景。
- 有真实逐字时间轴（TTML / YRC / QRC / KRC）时逐字扫光并上浮；否则整行高亮。
- 距离模糊与变暗、弹簧滚动。
- 前奏倒计时圆点，以及曲中间奏圆点（间隔 ≥ 6 秒）。
- 对唱行左右分列；翻译显示在每行下方。
- 滚动模糊的时间数字；过长的标题 / 专辑名走马灯。
- 布局：手机竖屏、横屏（类 Lyricify 左侧区块：封面、标题、翻译与菜单按钮、进度条、播放控制）、平板竖屏、分屏。

### 歌词来源

| 来源 | 说明 |
|------|------|
| AMLL TTML DB | 按 Spotify 曲目 ID 精确匹配；逐字时间轴、对唱 |
| 网易云音乐 | YRC 逐字时间轴 + 翻译 |
| QQ 音乐 | QRC 逐字时间轴，退回 LRC |
| 酷狗音乐 | KRC 逐字时间轴 |
| LRCLIB | 默认来源 |

- 所有来源并行搜索，结果足够好时提前结束。
- 候选按标题 / 歌手 / 时长打分，并在来源之间交叉比对时间轴：逐字歌词只有在时间轴与其他来源一致时才优先。
- 低置信度匹配不显示（同名不同歌手的歌词比没有歌词更糟）。
- 支持手动搜索 / 纠错，以及手动导入 `.lrc` 文件。

### 时间偏移

- 按歌曲、按歌词来源分别保存偏移（每个来源的时间轴单独调整）。
- 设置中另有全局偏移。

### 缓存与离线

- LRCLIB、AMLL 和手动歌词会缓存，供离线使用。
- 非官方来源（网易云、QQ 音乐、酷狗）的歌词**可能有版权问题**，默认只在内存里显示；你在提示中同意后才会缓存到本机，可在设置中更改。缓存仅供自己看，不要分享或导出。
- 离线模式：App Remote 连不上时，通过[通知使用权](#权限说明)读取 Spotify 自身的 MediaSession。

### 其他设备播放（Spotify Connect）

- 在其他设备上播放时，手机上的 Spotify 常会一直报告过时的暂停状态，因此应用改为跟随 Spotify Web API（`GET /v1/me/player`，scope `user-read-playback-state`，需要[额外授权](#权限说明)一次）。
- 其他设备播放时每 5 秒轮询一次；什么都没播放时逐步退避，最长 2 分钟。
- 菜单中显示"正在同步其他设备"。
- 令牌有效期 1 小时，打开应用时续期。

### 翻译与繁简

- ML Kit 设备端翻译；自带翻译（网易云）逐行显示。
- 中文界面下，可在引导页和设置中选择繁简转换方式：原地转换歌词，或在下方显示转换后的文字。

### 省电

- 暂停时释放唤醒锁；暂停 30 分钟后停止服务。
- Web API 轮询退避；界面副本在后台时停止轮询。

### 其他

- 首次使用引导。
- 4 种界面语言（简体中文、繁体中文、英语、日语）与主题设置。

### 已知限制

- ColorOS 专有：其 "Hans" 可能在 Spotify 暂停几秒后冻结本应用；冻结期间收不到恢复播放，也无法响应媒体卡片按钮，打开应用即可恢复。
- 通过 App Remote 控制播放（切歌等）需要 Spotify Premium。
- Web API 令牌每小时需要续期，续期需要打开应用。

---

## 快速开始

### 前置要求

- **JDK 17**
- **Android SDK**（compileSdk 35，minSdk 26）
- 已安装并登录 **Spotify App**
- 在 [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) 注册的应用 Client ID

### 获取 Client ID

1. 打开 [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)，用 Spotify 账号登录。
2. 点击 **Create App**，按下表填写：

   | 字段 | 填写内容 |
   |------|----------|
   | **App name** | 任意，例如 "Lyrics Card" |
   | **App description** | 例如 "Personal lyrics display app" |
   | **Website** | 留空 |
   | **Redirect URIs** | `spotifylyricsproxy://callback`（必须完全一致，末尾不能有斜杠或空格） |
   | **Android packages** | `com.example.spotifylyricsproxy` |
   | **Android SHA-1 fingerprint** | 用于签名 APK 的证书 SHA-1（`./gradlew :app:signingReport`） |
   | **Which API/SDKs** | 勾选 **Android** 和 **Web API**（其他设备同步和歌单读取用到 Web API） |

3. 点击 **Save**，复制页面顶部的 **Client ID**。

> Spotify 会校验包名和签名 SHA-1。在另一台机器上构建的 debug APK 签名不同：覆盖安装会报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，Spotify 也会拒绝连接。准备发布时还需登记 release 证书的 SHA-1。

> Client ID 会嵌入 APK，不算密钥，但不要提交到公开仓库。

### 从源码构建

```bash
git clone https://github.com/absswds/Spotify_lyric.git
cd Spotify_lyric
cp local.properties.example local.properties
```

编辑 `local.properties`（Windows 上 `sdk.dir` 用正斜杠）：

```properties
sdk.dir=C:/Users/YourUser/AppData/Local/Android/Sdk
spotify.client.id=你的_SPOTIFY_CLIENT_ID
```

构建并安装：

```bash
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> 未填 `spotify.client.id` 时构建仍会成功，但会写入 `MISSING_CLIENT_ID`，App Remote 和授权会静默失败。命令行构建前请关闭 Android Studio。

### 首次使用

1. 打开应用，跟随引导页完成设置（[通知权限](#权限说明)、[通知使用权](#权限说明)、繁简选项等）。
2. 点击**连接 Spotify**，在 Spotify 中[授权](#权限说明)。
3. 播放任意歌曲，歌词会出现在播放器、通知和媒体卡片中。

各项权限的用途见[权限说明](#权限说明)。

### 权限说明

| 权限 / 授权 | 用途 | 是否必需 |
|-------------|------|----------|
| 通知权限（Android 13+） | 在通知、媒体卡片、锁屏和胶囊中显示歌词 | 必需（Android 13+），否则通知栏不显示歌词 |
| Spotify 授权（App Remote） | 连接 Spotify，读取当前曲目和播放进度、控制播放 | 必需 |
| 通知使用权 | 离线模式：App Remote 连不上时读取 Spotify 自身的媒体会话（曲目、封面、播放控制），不读取通知内容 | 可选 |
| Spotify Web API 授权（`user-read-playback-state`） | 在其他设备上播放（Spotify Connect）时同步进度；第一次在其他设备播放时请求一次，令牌 1 小时有效，打开应用时续期 | 可选 |
| 忽略电池优化 | 减少系统在后台清理本应用，见[保持后台运行](#保持后台运行厂商系统) | 可选（推荐） |
| Spotify 自启动 / 关联启动（系统设置） | 让被强行停止的 Spotify 能被唤醒，见[保持后台运行](#保持后台运行厂商系统) | 可选，仅部分厂商系统 |

### 保持后台运行（厂商系统）

不少厂商系统（ColorOS、MIUI/HyperOS、OriginOS、MagicOS 等）会积极清理后台。若歌词过一会儿停止更新：

1. **[关闭电池优化](#权限说明)**：设置 → 电池 → 找到本应用 → 不优化 / 完全允许后台行为（各系统叫法不同）。
2. **[允许 Spotify 自启动 / 关联启动](#权限说明)**：否则被强行停止的 Spotify 无法被唤醒。厂商设置页受签名保护，应用只能跳转到应用详情页，需要你手动开启。
3. **锁定后台**：在最近任务中锁定本应用卡片。

> **ColorOS 专有**：即使做了以上设置，ColorOS 的 "Hans" 仍可能在 Spotify 暂停后 5 到 20 秒冻结本应用。可用 `adb logcat | grep OplusHans` 确认；打开应用即可解冻。

### 常见问题

| 现象 | 原因 | 解决 |
|------|------|------|
| 日志出现 `MISSING_CLIENT_ID` | 未配置 `local.properties` | 填写 `spotify.client.id` |
| 授权后立刻关闭 | Redirect URI 不一致 | 确认 Dashboard 中为 `spotifylyricsproxy://callback` |
| Spotify 拒绝连接 / `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 签名 SHA-1 与 Dashboard 不符 | 登记当前签名的 SHA-1，或卸载旧版后安装 |
| 通知栏无歌词 | 未授予[通知权限](#权限说明) | 在系统设置中开启 |
| 歌词过一会儿停止更新 | 系统杀后台（ColorOS 上也可能是 Hans 冻结） | 见[保持后台运行](#保持后台运行厂商系统)；打开应用恢复 |
| 在其他设备播放时歌词不动 | 未授予 [Web API 授权](#权限说明)，或令牌过期 | 打开应用完成授权 / 续期 |
| 切歌等控制无效 | 非 Premium 账号 | App Remote 播放控制需要 Premium |
| 离线时没有歌词 | 未授予[通知使用权](#权限说明)，或该歌曲未被缓存 | 开启通知使用权；离线只能显示已缓存的歌词 |
| 显示"低置信度"，没有歌词 | 所有候选都不够匹配 | 在菜单中手动搜索或导入 `.lrc` |

---

## 更新日志

- **2026-07**：项目初始化。App Remote 连接、LRCLIB 歌词、Room 缓存、MediaSession、前台通知、歌单预缓存、歌词纠错；手动 `.lrc` 导入、翻译目标语言、日语界面。
- **2026-08**：离线模式（通过通知使用权读取 Spotify 的 MediaSession）；默认来源改为 LRCLIB；Apple Music 风格播放器、逐字歌词、引导页、媒体卡片优先级。
- **2026-09**：
  - 歌词来源共识打分（跨来源时间轴比对），选择更快（并行搜索、提前结束）。
  - 新增 QQ 音乐 QRC 与酷狗 KRC 逐字来源。
  - Lyricify 风格播放器打磨：前奏倒计时 / 间奏圆点、模糊、滚动时间数字、横屏布局、走马灯。
  - 中文繁简转换方式可选。
  - 按来源分别保存的偏移。
  - 通过 Web API 同步其他设备的播放。
  - 媒体卡片夺回首位的改进。
  - 后台与省电修复。
  - 非官方来源的歌词改为经用户同意后才缓存。

---

## 架构

```
app/src/main/java/com/example/spotifylyricsproxy/
├── core/                设置与数据模型
├── database/            Room 数据库、DAO、实体
├── lyrics/              搜索、解析、匹配、共识、翻译
│   ├── amll/  lrclib/  netease/  qqmusic/   各歌词来源
├── mediasession/        MediaSession 与媒体按钮
├── notification/        前台服务与通知
├── playback/clock/      播放进度估算
├── spotify/
│   ├── remote/          App Remote、系统 MediaSession 兜底、其他设备同步
│   └── webapi/          OAuth 令牌、Web API
├── ui/                  Compose 界面（播放器、导航、引导、设置、歌单、缓存、主题）
├── util/                网络状态
└── worker/              歌单歌词预缓存
```

- **一条管线，两个使用方**：`LyricsForegroundService` 负责歌词同步（时钟、`updatePosition()`、切歌时搜索）并发布通知和媒体会话；`PlaybackViewModel` 读取同一个 `LyricsRepository` 用于界面显示，不驱动同步。
- **播放状态**：`SpotifyRemoteRepository` 封装 App Remote；连不上时退回 Spotify 的系统 MediaSession；其他设备播放时跟随 Web API。
- **歌词格式**：TTML、YRC、LRC 等都存放在同一个文本字段中，由 `LrcParser` 按内容识别，因此新增格式不需要改数据库。
- **媒体卡片优先级**：系统会把最近"转入播放"的会话排到最前。Spotify 切歌或恢复时，`MediaSessionController` 在约 1.2 秒后把歌词会话短暂切为暂停再切回播放，从而重新排到最前。

### 技术栈

| 组件 | |
|------|-|
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Repository |
| 数据库 | Room |
| 网络 | Retrofit + OkHttp + Gson |
| 异步 | Kotlin Coroutines + Flow |
| 后台 | 前台服务 + WorkManager |
| 图片 | Coil |
| 翻译 | ML Kit（设备端翻译与语言识别） |
| Spotify | Spotify Android SDK（App Remote + Auth）+ Web API |

---

## 致谢与引用的项目

本项目用到或参考了以下项目，感谢它们的作者：

| 项目 | 用在哪里 | 许可证 |
|---|---|---|
| [Lyricify-Lyrics-Helper](https://github.com/WXRIW/Lyricify-Lyrics-Helper) | 网易云、QQ 音乐、酷狗的接口参数，以及 QRC/KRC 的解密和解析：`QrcDecrypter.kt`、`QrcConverter.kt`、`KrcDecoder.kt` 由它移植而来，文件头注明了出处 | Apache-2.0 |
| [AMLL TTML DB](https://github.com/amll-dev/amll-ttml-db) | 按 Spotify 曲目 ID 查询逐字 TTML 歌词 | 贡献者自己的部分为 CC0-1.0；歌词文本归原权利人 |
| [LRCLIB](https://lrclib.net) | 默认的逐行同步歌词来源 | 公开免费 API |
| Apple Music、Lyricify | 播放器的视觉和动效设计参考（倒计时圆点、模糊、滚动时间等），未使用其代码 | — |
| [Spotify Android SDK](https://developer.spotify.com/documentation/android) / [Web API](https://developer.spotify.com/documentation/web-api) | 读取播放状态、控制播放、读取歌单 | Spotify 开发者条款 |
| [Google ML Kit](https://developers.google.com/ml-kit) | 本地语言识别与翻译 | ML Kit 条款 |
| AndroidX / Jetpack Compose、Room、WorkManager、OkHttp、Retrofit、Gson、Coil | 界面、数据库、网络等 | 各自的开源许可证（多为 Apache-2.0） |

更完整的说明见 [`docs/ATTRIBUTION_AND_COMPLIANCE.md`](docs/ATTRIBUTION_AND_COMPLIANCE.md)。

## 内容来源与合规

> 以下为项目说明，**不构成法律意见**。

本仓库的 [Apache-2.0](LICENSE) 许可证只覆盖代码、配置和文档。歌词（包括翻译）、封面和元数据仍归各自的权利人所有。

| 来源 | 性质 | 缓存策略 |
|------|------|----------|
| AMLL TTML DB | GitHub 上公开的社区数据库；贡献者的时间轴工作以 CC0-1.0 发布，歌词文本仍归权利人 | 缓存 |
| LRCLIB | 公开免费 API，无需密钥 | 缓存 |
| 网易云音乐 / QQ 音乐 / 酷狗 | 未公开文档的非公开接口；能访问不等于获得授权 | 仅在本次运行内存中，除非你同意本地缓存；绝不代理、镜像或导出 |
| Musixmatch | 未使用：完整 / 同步歌词需要付费商业许可，免费档只提供 30% 歌词 | — |
| Spotify 私有歌词接口 | 未使用 | — |

**个人学习与研究用途**：中国《著作权法》第二十四条第一款第（一）项允许"为个人学习、研究或者欣赏，使用他人已经发表的作品"，许多法域也有类似的私人使用 / 合理使用例外。这降低了个人使用的风险，但：

- **不**涵盖再分发歌词；
- **不**能取代各服务商的服务条款。

任何分发构建（公开 APK、上架、收费、服务端功能）的人，请先阅读 [Attribution, Content Sources, and Compliance](docs/ATTRIBUTION_AND_COMPLIANCE.md)。

---

## 免责声明

**本项目与 Spotify AB 无关，不是 Spotify 官方产品。**

- 本应用不播放音频，所有播放由 Spotify App 完成。
- 不修改 Spotify APK，不调用 Spotify 私有接口。
- 不收集或上传用户的播放历史；缓存只保存在你自己的设备上。
- 使用者需遵守 Spotify Developer Terms 及各歌词来源的条款。

---

## 贡献

1. Fork 本仓库
2. 创建特性分支（`git checkout -b feature/amazing-feature`）
3. 提交更改（`git commit -m 'Add amazing feature'`）
4. 推送分支（`git push origin feature/amazing-feature`）
5. 创建 Pull Request

新增界面文字需同时加入 4 种语言的 strings.xml；新增歌词来源或协议参考需在 [合规文档](docs/ATTRIBUTION_AND_COMPLIANCE.md) 中登记许可证。

---

## 许可证

代码采用 [Apache License 2.0](LICENSE)。
