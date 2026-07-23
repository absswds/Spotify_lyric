# Spotify Lyrics Proxy

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Spotify](https://img.shields.io/badge/Spotify-Android%20Remote-green.svg)](https://developer.spotify.com/documentation/android)

**读取 Spotify 播放状态，在系统通知和媒体卡片上显示同步歌词——无需 Root。**

[English](README.md) · **简体中文** · [日本語](README-ja.md)

[快速开始](#快速开始) · [功能特性](#功能特性) · [架构](#架构) · [免责声明](#免责声明)

---

## 更新日志

- **2026-07** — 项目初始化。完成核心管线：App Remote 连接、LRCLIB 歌词获取、Room 缓存、MediaSession 显示、前台通知、歌单预缓存、歌词修正。
- **2026-07** — 新增手动歌词导入（通过 SAF 导入 .lrc 文件）、翻译目标语言选择器（中/英/日）、日语界面本地化。

---

## 概述

Spotify Lyrics Proxy 是一个 Android 应用，在 Spotify 播放状态与第三方歌词来源之间建立桥梁。本应用不播放音频，不修改 Spotify APK，不调用 Spotify 私有 API。

应用通过 Spotify App Remote 读取当前播放曲目，从 LRCLIB（或本地缓存）获取同步歌词，并在以下位置显示当前歌词行：

- 系统通知（前台服务）
- MediaSession 媒体卡片（锁屏、控制中心）

---

## 功能特性

### 播放同步

| 功能 | |
|------|-|
| 状态读取 | 从 Spotify 获取当前曲目、播放进度和播放状态 |
| 控制转发 | 将播放/暂停/切歌/进度拖动指令转发给 Spotify |
| 自动检测 | 响应曲目切换和播放状态变化 |

### 歌词显示

| 功能 | |
|------|-|
| 通知栏 | 前台服务通知显示当前歌词行 |
| 媒体卡片 | 锁屏和控制中心显示同步歌词 |
| 自定义 | 调节字体大小、加粗开关、暗色模式、对齐方式 |

### 缓存

| 功能 | |
|------|-|
| 自动缓存 | 首次播放自动从 LRCLIB 获取歌词并存入 Room |
| 歌单预缓存 | 后台 WorkManager 在 Wi-Fi/充电条件下预缓存歌单歌词 |
| 离线可用 | 已缓存歌词在无网络时正常显示 |

### 歌词管理

| 功能 | |
|------|-|
| 修正 | 标记错误匹配并重新搜索候选歌词 |
| 偏移 | 整行歌词时间提前或延迟调整 |
| 导入 | 通过系统文件选择器加载本地 `.lrc` 文件 |
| 手动优先 | 手动导入的歌词始终优先于 LRCLIB 匹配 |
| 清理 | 清除歌词缓存和封面缓存 |

### 歌词翻译

| 功能 | |
|------|-|
| 自动检测 | 自动检测歌词语言并实时翻译 |
| 目标语言 | 选择翻译目标：中文、英文或日文 |
| ML Kit | 本地离线翻译引擎，模型下载后无需网络 |

### 界面

| 功能 | |
|------|-|
| 设计 | Material 3，支持专辑封面动态主题色 |
| 主题 | 系统/浅色/深色模式 |
| 语言 | 中文、英文和日文 |

---

## 快速开始

### 前置要求

- **JDK 17** 或更高版本
- **Android SDK**（API 26+）
- 已安装 **Spotify App** 并登录账号
- 在 [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) 注册应用获取 Client ID

### 获取 Client ID

本应用通过 Spotify Android SDK 连接 Spotify，需要先在 Spotify Developer Dashboard 注册一个应用。

1. 打开 [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)，用你的 Spotify 账号登录
2. 点击 **Create App**
3. 按以下说明填写表单：

   | 字段 | 填写内容 |
   |------|----------|
   | **App name** | 随便填，只是给你自己看的（例如 "Lyrics Card"） |
   | **App description** | 例如 "Personal lyrics display app" |
   | **Website** | 留空 |
   | **Redirect URIs** | 添加以下地址（**必须完全一致**，不能多斜杠或空格）：`spotifylyricsproxy://callback` |
   | **Android packages** | **留空**（不需要填写，见下方说明） |
   | **iOS app bundles** | 留空 |
   | **Which API/SDKs are you planning to use?** | 勾选 **Web API** |

   > **Redirect URI** 是 App 授权后接收 OAuth token 的回调地址，必须**完全一致**——末尾不能有斜杠或空格。

   > **Android packages** — 不需要填写包名或 SHA 指纹。本应用使用 Spotify Auth SDK（浏览器 OAuth），只需配置 Redirect URI。包名/指纹字段仅在使用 Spotify App Remote SDK 深度链接时才需要，本应用不需要。

4. 点击页面底部的 **Save**
5. 从页面顶部复制 **Client ID**（32 位十六进制字符串，例如 `81a57006ff4a4d5d96cb72f180aa4ab5`）

> Client ID 不是密钥（会嵌入 APK），但不要提交到公开仓库。

### 从源码构建

克隆仓库：

```bash
git clone https://github.com/absswds/Spotify_lyric.git
cd Spotify_lyric
```

配置 Client ID：

```bash
cp local.properties.example local.properties
```

打开 `local.properties`，填入你从 Dashboard 复制的内容：

```properties
# Android SDK 路径
# Windows: C:\Users\<用户名>\AppData\Local\Android\Sdk
# macOS:   ~/Library/Android/sdk
# Linux:   ~/Android/Sdk
sdk.dir=C\:\\Users\\YourUser\\AppData\\Local\\Android\\Sdk

spotify.client.id=你的_SPOTIFY_CLIENT_ID
```

构建并安装：

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

> 如果你使用 **Android Studio**，直接打开项目即可——`local.properties` 会自动从 SDK 设置生成。

### 首次使用

1. 打开 App
2. 点击**连接 Spotify**——Spotify App 会自动打开进行授权
3. 在 Spotify 中**允许播放状态访问**
4. 播放任意歌曲——通知栏将显示当前歌词行

> **Android 13+** 需要授予**通知权限**，否则歌词不会出现在通知栏。

### 保持后台运行

本应用通过前台服务在通知栏显示歌词。部分 Android 系统（特别是 OPPO ColorOS、小米 MIUI、华为 HarmonyOS、vivo OriginOS）会积极杀后台应用以节省电量。如果通知栏歌词过一会儿消失，需要手动允许后台运行：

1. **关闭电池优化** — 设置 → 电池 → 电池优化 → 找到本应用 → 选择"不优化"
2. **开启自启动**（如支持）— 设置 → 应用管理 → 找到本应用 → 开启"自启动"
3. **锁定后台** — 打开最近任务，向下轻扫本应用的卡片将其锁定（防止系统杀掉）

> 如果不设置以上选项，系统可能会杀掉前台服务，歌词将停止更新。

### 常见问题

| 现象 | 原因 | 解决 |
|------|------|------|
| 日志显示 `MISSING_CLIENT_ID` | 未配置 `local.properties` | 设置 `spotify.client.id` |
| Gradle 报 JDK 错误 | 未安装 JDK 17+ | 安装 JDK 17，配置 `JAVA_HOME` |
| "Spotify not installed" | 未安装 Spotify | 从 Play Store 安装 |
| 点击连接后闪退 | Spotify 未登录 | 先打开 Spotify 登录账号 |
| 通知栏不显示歌词 | 未授予通知权限 | 在系统设置中开启通知权限 |
| 授权后瞬间关闭 | Dashboard 中 Redirect URI 配置错误 | 确认已添加 `spotifylyricsproxy://callback` |
| 通知栏歌词过一会儿消失 | 系统杀掉了后台服务 | 关闭电池优化、开启自启动、锁定后台（见[保持后台运行](#保持后台运行)） |

---

## 架构

```
app/src/main/java/com/example/spotifylyricsproxy/
├── core/model/          数据模型
├── database/            Room 数据库和 DAO
├── lyrics/              歌词获取、解析、匹配、同步
│   └── lrclib/         LRCLIB API 实现
├── mediasession/        MediaSession 控制
├── notification/        前台服务通知
├── playback/clock/      播放进度估算
├── spotify/             Spotify 集成
│   ├── remote/         App Remote 连接和状态订阅
│   └── webapi/         Web API（OAuth、歌单）
├── ui/                  Compose 界面
│   ├── cache/          缓存管理
│   ├── playback/       播放界面和 ViewModel
│   ├── playlist/       歌单预缓存
│   ├── precache/       预缓存管理
│   ├── settings/       设置界面
│   └── theme/          主题和语言配置
└── worker/              WorkManager 后台任务
```

### 数据流

```
Spotify PlayerState
  → SpotifyRemoteRepository
  → LyricsRepository
  → Room cache / LRCLIB
  → LrcParser
  → PlaybackClock
  → LyricSyncEngine
  → Notification / MediaSession / UI
```

### 技术栈

| 组件 | |
|------|-|
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Repository Pattern |
| 数据库 | Room（SQLite） |
| 网络 | Retrofit + OkHttp |
| 异步 | Kotlin Coroutines + Flow |
| 后台 | WorkManager |
| 图片加载 | Coil |
| 歌词翻译 | ML Kit（本地离线） |
| Spotify 集成 | Spotify Android SDK（App Remote + Auth） |

---

## 免责声明

**本项目与 Spotify AB 无关，不是 Spotify 官方产品。**

- 本应用不播放音频，不替代 Spotify 播放器
- 所有音频播放由 Spotify App 处理
- 本应用仅读取播放状态并显示歌词
- 歌词来自 LRCLIB（第三方）或用户提供的本地文件
- 本应用不调用 Spotify 私有 API，不修改 Spotify APK
- 本应用不收集、存储或传输用户播放历史
- 使用者需遵守 Spotify Developer Terms 和 LRCLIB 服务条款

---

## 贡献

1. Fork 本仓库
2. 创建特性分支（`git checkout -b feature/amazing-feature`）
3. 提交更改（`git commit -m 'Add amazing feature'`）
4. 推送到分支（`git push origin feature/amazing-feature`）
5. 创建 Pull Request

---

## 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。
