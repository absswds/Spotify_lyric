# Spotify Lyrics Proxy

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Spotify](https://img.shields.io/badge/Spotify-Android%20Remote-green.svg)](https://developer.spotify.com/documentation/android)

**讀取 Spotify 播放狀態，在系統通知與媒體卡片上顯示同步歌詞——無需 Root。**

[English](README.md) · [简体中文](README-zh.md) · **繁體中文** · [日本語](README-ja.md)

[快速開始](#快速開始) · [功能特性](#功能特性) · [架構](#架構) · [內容來源與合規](#內容來源與合規) · [免責聲明](#免責聲明)

---

## 更新日誌

- **2026-07** — 建立核心管線：App Remote 連線、Room 歌詞快取、MediaSession 顯示、前景通知、歌單預快取與歌詞校正。
- **2026-07** — 新增沉浸式播放 UI、右側歌詞設定抽屜、直向/橫向版面，以及字體大小、字重、對齊與非當前行模糊設定。
- **2026-07** — 新增 LRCLIB、網易雲音樂、QQ 音樂等可選歌詞來源；來源會並行搜尋、依 Spotify metadata 評分，並可在歌詞校正頁手動選擇。
- **2026-07** — 新增簡體中文、繁體中文、英文、日文介面在地化，以及翻譯啟用時的簡繁歌詞轉換。

---

## 概述

Spotify Lyrics Proxy 是一個 Android 應用，連接 Spotify 播放狀態與第三方歌詞來源。本應用不播放音訊、不修改 Spotify APK，也不呼叫 Spotify 私有 API。

應用透過 Spotify App Remote 讀取目前播放曲目，從本機快取與相容第三方來源查詢同步歌詞，並在以下位置顯示當前歌詞行：

- 系統通知（前景服務）
- MediaSession 媒體卡片（鎖定畫面、控制中心）

---

## 功能特性

### 播放同步

| 功能 | 說明 |
|------|------|
| 狀態讀取 | 從 Spotify 取得目前曲目、播放進度與播放狀態 |
| 控制轉發 | 將播放/暫停/切歌/進度拖曳指令轉發給 Spotify |
| 自動偵測 | 回應曲目切換與播放狀態變化 |

### 歌詞顯示

| 功能 | 說明 |
|------|------|
| 通知欄 | 前景服務通知顯示目前歌詞行 |
| 媒體卡片 | 鎖定畫面與控制中心顯示同步歌詞 |
| 自訂 | 沉浸式直向/橫向版面；可調字體大小、當前行字重、非當前行模糊、暗化與對齊 |
| 介面語言 | 簡體中文、繁體中文、英文與日文 |

### 快取

| 功能 | 說明 |
|------|------|
| 自動快取 | 首次播放時從相容第三方來源搜尋，將已接受歌詞存入 Room |
| 歌單預快取 | 在 Wi-Fi / 充電條件下由 WorkManager 背景預快取選定歌單的歌詞 |
| 離線可用 | 已快取歌詞在無網路時仍可顯示 |

### 歌詞管理

| 功能 | 說明 |
|------|------|
| 校正 | 檢視候選及其提供方、手動選擇、標記錯誤配對、重新搜尋 |
| 偏移 | 將歌詞時間提前或延遲調整 |
| 匯入 | 透過系統檔案選擇器載入本機 `.lrc` 檔 |
| 手動優先 | 手動匯入歌詞永遠優先於線上結果 |
| 清理 | 清除歌詞快取與封面快取 |

### 歌詞翻譯

| 功能 | 說明 |
|------|------|
| 自動偵測 | 自動偵測歌詞語言並即時翻譯 |
| 目標語言 | 可選簡體中文、繁體中文、英文或日文 |
| 簡繁轉換 | 啟用翻譯時，可在簡體與繁體之間轉換原始歌詞文字 |
| ML Kit | 本機離線翻譯引擎；模型下載後不需要網路 |

---

## 快速開始

### 前置需求

- **JDK 17** 或以上
- **Android SDK**（API 26+）
- 已安裝 **Spotify App** 並登入帳號
- 在 [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) 註冊應用取得 Client ID

### 取得 Client ID

1. 前往 [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)，用 Spotify 帳號登入。
2. 點擊 **Create App**。
3. 填寫應用資料，並加入以下設定：
   - **Redirect URIs**：`spotifylyricsproxy://callback`（必須完全一致）
   - **Android packages**：`com.example.spotifylyricsproxy`
   - **Android SHA-1 fingerprint**：加入你用來簽署 APK 的憑證 SHA-1 指紋
   - 勾選 **Android**（不是 Web API）後儲存。
4. 複製頁面頂部的 **Client ID**。

> Spotify Android SDK 官方文件建議同時登記 Android 包名和 SHA-1。公開 release 時，要登記 release 簽名憑證的 SHA-1；調試時則使用 debug 簽名憑證。Client ID 不是 client secret，但仍不要將個人設定檔或 token 提交到公開倉庫。

### 從原始碼建置

```bash
git clone https://github.com/absswds/Spotify_lyric.git
cd Spotify_lyric
cp local.properties.example local.properties
```

編輯 `local.properties`：

```properties
sdk.dir=C\:\\Users\\YourUser\\AppData\\Local\\Android\\Sdk
spotify.client.id=YOUR_SPOTIFY_CLIENT_ID
```

建置並安裝：

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 首次使用

1. 開啟 App。
2. 點擊連接 Spotify，依畫面完成授權。
3. Android 13+ 請授予通知權限。
4. 在 Spotify 播放任意歌曲。

### 保持背景運作

OPPO ColorOS、小米 MIUI、華為 HarmonyOS、vivo OriginOS 等系統可能積極停止背景應用。若通知歌詞停止更新，請關閉本應用的電池最佳化、開啟自動啟動（如有），並在最近任務中鎖定 App。

> 如果 App 播放器頁面變成空白（Spotify 原生播放器搶佔了畫面），或回到 App 後歌詞沒有及時更新，在最近任務中滑掉 App 重新打開即可恢復。

---

## 架構

```text
Spotify PlayerState
  → SpotifyRemoteRepository
  → LyricsRepository
  → Room cache / compatible on-device lyric sources
  → LrcParser
  → PlaybackClock
  → LyricSyncEngine
  → Notification / MediaSession / UI
```

主要程式碼目錄：

```text
app/src/main/java/com/example/spotifylyricsproxy/
├── core/model/          資料模型
├── database/            Room 資料庫與 DAO
├── lyrics/              歌詞取得、解析、比對、同步
│   ├── lrclib/          LRCLIB 來源
│   ├── netease/         網易雲音樂來源
│   └── qqmusic/         QQ 音樂來源
├── mediasession/        MediaSession 控制
├── notification/        前景服務通知
├── playback/clock/      播放進度估算
├── spotify/             Spotify 整合
├── ui/                  Compose 畫面
└── worker/              WorkManager 背景工作
```

---

## 內容來源與合規

### 倉庫授權範圍

本倉庫以 [Apache-2.0](LICENSE) 授權，僅涵蓋原始程式碼、專案設定與文件；**不**涵蓋歌詞、翻譯歌詞、專輯封面、Spotify 內容、第三方服務 metadata 或其使用權。

本專案僅提供在使用者裝置上搜尋、顯示的介面；**網易雲歌詞在目前版本只保留於本次 App 執行記憶體，不寫入本機資料庫，舊的網易雲快取會在歌詞倉庫啟動時清除。** 其他允許持久化的來源僅可保存在使用者自己的裝置上；倉庫、APK 與服務端均不包含歌詞資料庫、歌曲 metadata dump、封面檔案庫或代管歌詞 API。

### 第三方來源與風險

| 項目 | App 中的用途 | 重要界線 |
|------|-------------|----------|
| Spotify Android SDK / Web API | 在使用者授權後讀取播放狀態、轉發控制與取得歌單 metadata | 請遵守 [Spotify Developer Terms](https://developer.spotify.com/terms) 與 [Design Guidelines](https://developer.spotify.com/documentation/design)，不得暗示 Spotify 背書。 |
| LRCLIB | 可選的裝置端同步歌詞查詢 | 發布或商業使用前，應確認 LRCLIB 當前 API 政策與條款。 |
| 網易雲音樂 / QQ 音樂 | 可選的裝置端歌詞查詢 adapter | 端點可能未文件化且隨時變動；可用性不代表授權。若條款、著作權規則或適用法律不允許，應為公開版本停用或移除。 |
| Google ML Kit | 裝置端語言識別與翻譯 | 翻譯不會解除原始歌詞的著作權限制。 |

禁止把外部歌詞或封面做成 proxy、mirror、批次下載器、公開 API、雲端快取或預載資料庫。若要發布 APK、上架、收費、提供後端服務或面向公開用戶，應先取得法律審查並重新查核每個來源的最新條款。

### 參考與致謝

- [Lyricify-Lyrics-Helper](https://github.com/WXRIW/Lyricify-Lyrics-Helper)（Apache-2.0）— 建立網易雲 adapter 時，作為協定/實作參考。本專案未 vendor 其程式碼，也未複製或打包歌詞資料。
- Spotify Android SDK/Auth SDK、AndroidX、Jetpack Compose、Room、WorkManager、Coil、OkHttp、Retrofit、Gson 與 Google ML Kit 均為宣告依賴；其各自的授權與 notice 仍然適用。

更完整的公開發布檢查清單請看：[Attribution, Content Sources, and Compliance](docs/ATTRIBUTION_AND_COMPLIANCE.md)。

---

## 免責聲明

**本專案與 Spotify AB 無關，亦非 Spotify 官方產品。**

- 本應用不播放音訊，也不替代 Spotify 播放器。
- 所有音訊播放仍由 Spotify App 處理。
- 本應用僅讀取播放狀態並顯示歌詞。
- 本倉庫的 Apache-2.0 授權只涵蓋本專案程式碼，不授權第三方歌詞、封面或 metadata。
- 本專案預期供個人裝置端使用；不得代管、鏡像、批量匯出或重新發布第三方歌詞/封面。
- 本應用不呼叫私有 Spotify API，也不修改 Spotify APK。
- 本應用不會把使用者播放紀錄傳送到使用者裝置以外。
- 使用者必須遵守 Spotify Developer Terms、內容提供方條款及適用著作權法。

---

## 貢獻

1. Fork 本倉庫。
2. 建立功能分支（`git checkout -b feature/amazing-feature`）。
3. 提交變更。
4. 推送分支並建立 Pull Request。

## 授權

本專案採用 [Apache License 2.0](LICENSE) 授權。
