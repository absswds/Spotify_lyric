# Lyrics Card

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Spotify](https://img.shields.io/badge/Spotify-Android%20Remote-green.svg)](https://developer.spotify.com/documentation/android)

**專為 Android 上的 Spotify 打造的歌詞 App：透過標準的 Android 媒體工作階段（MediaSession）與 MediaStyle 通知，把即時同步歌詞放進系統的即時媒體區域——媒體通知／控制中心卡片、鎖定畫面，以及各廠商系統的「動態島」式膠囊／即時動態；另附一個 Apple Music／Lyricify 風格的精美歌詞播放器。免 Root，不修改 Spotify。**

[English](README.md) · [简体中文](README-zh.md) · **繁體中文** · [日本語](README-ja.md)

> ⚠️ **版權提醒**：歌詞版權歸原作者與唱片公司所有。網易雲、QQ 音樂、酷狗是非公開介面，使用與快取它們的歌詞**可能涉及版權與服務條款問題**。如果只是自己在自己手機上聽歌時看，一般風險很低；**不要分享、匯出、公開發布快取的歌詞，也不要拿來商用**。詳見[致謝與引用的專案](#致謝與引用的專案) · [內容來源與合規](#內容來源與合規)（非法律意見）。

> 💳 **播放控制需要 Spotify Premium**：Spotify 官方文件寫明，透過網路介面控制播放（暫停、切歌、拖動進度等）[只對 Premium 使用者有效](https://developer.spotify.com/documentation/web-api/reference/skip-users-playback-to-next-track)；免費帳號在手機上[始終是智慧隨機播放](https://support.spotify.com/us/article/shuffle-play/)，跳歌次數也[有限制](https://support.spotify.com/us/article/your-premium-benefits/)。所以免費帳號使用本應用程式的播放、切歌、進度、隨機、循環按鈕可能無效，但歌詞顯示不受影響。

[功能](#功能) · [快速開始](#快速開始) · [權限說明](#權限說明) · [更新紀錄](#更新紀錄) · [架構](#架構) · [致謝與引用的專案](#致謝與引用的專案) · [內容來源與合規](#內容來源與合規) · [免責聲明](#免責聲明)

---

## 功能

### 系統即時媒體區域

- 目前的歌詞行會顯示在原生 MediaStyle 通知、控制中心媒體卡片、鎖定畫面，以及廠商系統中讀取媒體工作階段的「島」／膠囊式即時動態裡。
- Spotify 重新搶占媒體卡片首位時（切歌、恢復播放、播放從其他裝置切回、其他 App 播放音訊之後、Spotify 重新啟動之後），會自動把首位搶回給歌詞工作階段。

主要在 OPPO ColorOS 上測試，鎖屏島（锁屏岛）與流體雲（流体云）效果最好。

<!-- TODO: add screenshots (owner will supply the images), then move these lines out of the comment:
![ColorOS lock-screen island](docs/images/coloros-lockscreen-island.png)
![ColorOS Fluid Cloud](docs/images/coloros-fluid-cloud.png)
-->

> 作者手邊只有有限的（ColorOS）裝置，無法測試更多系統。如果你有興趣，或在其他廠商系統（MIUI/HyperOS、OriginOS、MagicOS、One UI 等）上表現異常，歡迎[提 Issue](https://github.com/absswds/Spotify_lyric/issues) 或發 PR。

### 播放器

- 從封面取色的網格漸層背景。
- 有真實逐字時間軸（TTML／YRC／QRC／KRC）時逐字掃光並上浮；否則整行醒目提示。
- 距離模糊與變暗、彈簧捲動。
- 前奏倒數圓點，以及曲中間奏圓點（間隔 ≥ 6 秒）。
- 對唱行左右分列；翻譯顯示在每行下方。
- 捲動模糊的時間數字；過長的標題／專輯名稱跑馬燈。
- 版面：手機直向、橫向（類 Lyricify 左側區塊：封面、標題、翻譯與選單按鈕、進度列、播放控制）、平板直向、分割畫面。

### 歌詞來源

| 來源 | 說明 |
|------|------|
| AMLL TTML DB | 依 Spotify 曲目 ID 精確比對；逐字時間軸、對唱 |
| 網易雲音樂 | YRC 逐字時間軸 + 翻譯 |
| QQ 音樂 | QRC 逐字時間軸，退回 LRC |
| 酷狗音樂 | KRC 逐字時間軸 |
| LRCLIB | 預設來源 |

- 所有來源平行搜尋，結果夠好時提前結束。
- 候選依標題／歌手／長度評分，並在來源之間交叉比對時間軸：逐字歌詞只有在時間軸與其他來源一致時才優先。
- 低信心比對結果不顯示（同名不同歌手的歌詞比沒有歌詞更糟）。
- 支援手動搜尋／修正，以及手動匯入 `.lrc` 檔案。

### 時間偏移

- 依歌曲、依歌詞來源分別儲存偏移（每個來源的時間軸單獨調整）。
- 設定中另有全域偏移。

### 快取與離線

- LRCLIB、AMLL 與手動歌詞會快取，供離線使用。
- 非官方來源（網易雲、QQ 音樂、酷狗）的歌詞**可能有版權問題**，預設只在記憶體中顯示；你在提示中同意後才會快取到本機，可在設定中變更。快取僅供自己看，不要分享或匯出。
- 離線模式：App Remote 無法連線時，透過[通知存取權](#權限說明)讀取 Spotify 本身的 MediaSession。

### 其他裝置播放（Spotify Connect）

- 在其他裝置上播放時，手機上的 Spotify 常會一直回報過時的暫停狀態，因此 App 改為跟隨 Spotify Web API（`GET /v1/me/player`，scope `user-read-playback-state`，需要[額外授權](#權限說明)一次）。
- 其他裝置播放時每 5 秒輪詢一次；沒有任何播放時逐步退避，最長 2 分鐘。
- 選單中顯示「正在同步其他裝置」。
- 權杖效期 1 小時，開啟 App 時更新。

### 翻譯與繁簡

- ML Kit 裝置端翻譯；內建翻譯（網易雲）逐行顯示。
- 中文介面下，可在引導頁與設定中選擇繁簡轉換方式：直接轉換歌詞，或在下方顯示轉換後的文字。

### 省電

- 暫停時釋放喚醒鎖；暫停 30 分鐘後停止服務。
- Web API 輪詢退避；介面端在背景時停止輪詢。

### 其他

- 首次使用引導。
- 4 種介面語言（簡體中文、繁體中文、英文、日文）與主題設定。

### 已知限制

- ColorOS 專屬：其「Hans」可能在 Spotify 暫停幾秒後凍結本 App；凍結期間收不到恢復播放，也無法回應媒體卡片按鈕，開啟 App 即可恢復。
- 透過 App Remote 控制播放（切歌等）需要 Spotify Premium。
- Web API 權杖每小時需要更新，更新需要開啟 App。

---

## 快速開始

### 前置需求

- **JDK 17**
- **Android SDK**（compileSdk 35，minSdk 26）
- 已安裝並登入 **Spotify App**
- 在 [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) 註冊的應用程式 Client ID

### 取得 Client ID

1. 開啟 [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)，以 Spotify 帳號登入。
2. 點選 **Create App**，依下表填寫：

   | 欄位 | 填寫內容 |
   |------|----------|
   | **App name** | 任意，例如 "Lyrics Card" |
   | **App description** | 例如 "Personal lyrics display app" |
   | **Website** | 留空 |
   | **Redirect URIs** | `spotifylyricsproxy://callback`（必須完全一致，結尾不能有斜線或空格） |
   | **Android packages** | `com.example.spotifylyricsproxy` |
   | **Android SHA-1 fingerprint** | 用於簽署 APK 的憑證 SHA-1（`./gradlew :app:signingReport`） |
   | **Which API/SDKs** | 勾選 **Android** 與 **Web API**（其他裝置同步與播放清單讀取會用到 Web API） |

3. 點選 **Save**，複製頁面上方的 **Client ID**。

> Spotify 會驗證套件名稱與簽署 SHA-1。在另一台電腦上建置的 debug APK 簽署不同：覆蓋安裝會出現 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，Spotify 也會拒絕連線。準備發佈時還需登記 release 憑證的 SHA-1。

> Client ID 會嵌入 APK，不算金鑰，但不要提交到公開儲存庫。

### 從原始碼建置

```bash
git clone https://github.com/absswds/Spotify_lyric.git
cd Spotify_lyric
cp local.properties.example local.properties
```

編輯 `local.properties`（Windows 上 `sdk.dir` 請用正斜線）：

```properties
sdk.dir=C:/Users/YourUser/AppData/Local/Android/Sdk
spotify.client.id=你的_SPOTIFY_CLIENT_ID
```

建置並安裝：

```bash
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> 未填 `spotify.client.id` 時建置仍會成功，但會寫入 `MISSING_CLIENT_ID`，App Remote 與授權會無聲失敗。從命令列建置前請先關閉 Android Studio。

### 首次使用

1. 開啟 App，依引導頁完成設定（[通知權限](#權限說明)、[通知存取權](#權限說明)、繁簡選項等）。
2. 點選**連結 Spotify**，在 Spotify 中[授權](#權限說明)。
3. 播放任一首歌，歌詞會出現在播放器、通知與媒體卡片中。

各項權限的用途見[權限說明](#權限說明)。

### 權限說明

| 權限／授權 | 用途 | 是否必要 |
|------------|------|----------|
| 通知權限（Android 13+） | 在通知、媒體卡片、鎖定畫面與膠囊中顯示歌詞 | 必要（Android 13+），否則通知列不會顯示歌詞 |
| Spotify 授權（App Remote） | 連結 Spotify，讀取目前曲目與播放進度、控制播放 | 必要 |
| 通知存取權 | 離線模式：App Remote 無法連線時讀取 Spotify 本身的媒體工作階段（曲目、封面、播放控制），不讀取通知內容 | 選用 |
| Spotify Web API 授權（`user-read-playback-state`） | 在其他裝置上播放（Spotify Connect）時同步進度；第一次在其他裝置播放時要求一次，權杖 1 小時有效，開啟 App 時更新 | 選用 |
| 忽略電池最佳化 | 減少系統在背景清理本 App，見[保持背景執行](#保持背景執行廠商系統) | 選用（建議） |
| Spotify 自動啟動／關聯啟動（系統設定） | 讓被強制停止的 Spotify 能被喚醒，見[保持背景執行](#保持背景執行廠商系統) | 選用，僅部分廠商系統 |

### 保持背景執行（廠商系統）

不少廠商系統（ColorOS、MIUI/HyperOS、OriginOS、MagicOS 等）會積極清理背景。若歌詞過一陣子停止更新：

1. **[關閉電池最佳化](#權限說明)**：設定 → 電池 → 找到本 App → 不最佳化／完全允許背景行為（各系統名稱不同）。
2. **[允許 Spotify 自動啟動／關聯啟動](#權限說明)**：否則被強制停止的 Spotify 無法被喚醒。廠商設定頁受簽章保護，App 只能跳到應用程式資訊頁，需要你手動開啟。
3. **鎖定背景**：在最近使用的 App 中鎖定本 App 卡片。

> **ColorOS 專屬**：即使完成以上設定，ColorOS 的「Hans」仍可能在 Spotify 暫停後 5 到 20 秒凍結本 App。可用 `adb logcat | grep OplusHans` 確認；開啟 App 即可解凍。

### 常見問題

| 現象 | 原因 | 解決 |
|------|------|------|
| 日誌出現 `MISSING_CLIENT_ID` | 未設定 `local.properties` | 填寫 `spotify.client.id` |
| 授權後立刻關閉 | Redirect URI 不一致 | 確認 Dashboard 中為 `spotifylyricsproxy://callback` |
| Spotify 拒絕連線／`INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 簽署 SHA-1 與 Dashboard 不符 | 登記目前簽署的 SHA-1，或先解除安裝舊版 |
| 通知列沒有歌詞 | 未授予[通知權限](#權限說明) | 在系統設定中開啟 |
| 歌詞過一陣子停止更新 | 系統清理背景（ColorOS 上也可能是 Hans 凍結） | 見[保持背景執行](#保持背景執行廠商系統)；開啟 App 恢復 |
| 在其他裝置播放時歌詞不動 | 未授予 [Web API 授權](#權限說明)，或權杖過期 | 開啟 App 完成授權／更新 |
| 切歌等控制無效 | 非 Premium 帳號 | App Remote 播放控制需要 Premium |
| 離線時沒有歌詞 | 未授予[通知存取權](#權限說明)，或該歌曲未被快取 | 開啟通知存取權；離線只能顯示已快取的歌詞 |
| 顯示「低信心」，沒有歌詞 | 所有候選都不夠吻合 | 從選單手動搜尋或匯入 `.lrc` |

---

## 更新紀錄

- **2026-07**：專案初始化。App Remote 連線、LRCLIB 歌詞、Room 快取、MediaSession、前景通知、播放清單預先快取、歌詞修正；手動 `.lrc` 匯入、翻譯目標語言、日文介面。
- **2026-08**：離線模式（透過通知存取權讀取 Spotify 的 MediaSession）；預設來源改為 LRCLIB；Apple Music 風格播放器、逐字歌詞、引導頁、媒體卡片優先順序。
- **2026-09**：
  - 歌詞來源共識評分（跨來源時間軸比對），選擇更快（平行搜尋、提前結束）。
  - 新增 QQ 音樂 QRC 與酷狗 KRC 逐字來源。
  - Lyricify 風格播放器打磨：前奏倒數／間奏圓點、模糊、捲動時間數字、橫向版面、跑馬燈。
  - 中文繁簡轉換方式可選。
  - 依來源分別儲存的偏移。
  - 透過 Web API 同步其他裝置的播放。
  - 媒體卡片搶回首位的改進。
  - 背景與省電修正。
  - 非官方來源的歌詞改為經使用者同意後才快取。

---

## 架構

```
app/src/main/java/com/example/spotifylyricsproxy/
├── core/                設定與資料模型
├── database/            Room 資料庫、DAO、實體
├── lyrics/              搜尋、解析、比對、共識、翻譯
│   ├── amll/  lrclib/  netease/  qqmusic/   各歌詞來源
├── mediasession/        MediaSession 與媒體按鈕
├── notification/        前景服務與通知
├── playback/clock/      播放進度估算
├── spotify/
│   ├── remote/          App Remote、系統 MediaSession 備援、其他裝置同步
│   └── webapi/          OAuth 權杖、Web API
├── ui/                  Compose 介面（播放器、導覽、引導、設定、播放清單、快取、主題）
├── util/                網路狀態
└── worker/              播放清單歌詞預先快取
```

- **一條管線，兩個使用端**：`LyricsForegroundService` 負責歌詞同步（時鐘、`updatePosition()`、切歌時搜尋）並發佈通知與媒體工作階段；`PlaybackViewModel` 讀取同一個 `LyricsRepository` 供介面顯示，不驅動同步。
- **播放狀態**：`SpotifyRemoteRepository` 封裝 App Remote；無法連線時退回 Spotify 的系統 MediaSession；其他裝置播放時跟隨 Web API。
- **歌詞格式**：TTML、YRC、LRC 等都存放在同一個文字欄位，由 `LrcParser` 依內容判斷，因此新增格式不需要修改資料庫。
- **媒體卡片優先順序**：系統會把最近「轉為播放」的工作階段排到最前。Spotify 切歌或恢復時，`MediaSessionController` 約 1.2 秒後把歌詞工作階段短暫切為暫停再切回播放，重新排到最前。

### 技術堆疊

| 元件 | |
|------|-|
| UI | Jetpack Compose + Material 3 |
| 架構 | MVVM + Repository |
| 資料庫 | Room |
| 網路 | Retrofit + OkHttp + Gson |
| 非同步 | Kotlin Coroutines + Flow |
| 背景 | 前景服務 + WorkManager |
| 圖片 | Coil |
| 翻譯 | ML Kit（裝置端翻譯與語言辨識） |
| Spotify | Spotify Android SDK（App Remote + Auth）+ Web API |

---

## 致謝與引用的專案

本專案使用或參考了以下專案，感謝它們的作者：

| 專案 | 用在哪裡 | 授權 |
|---|---|---|
| [Lyricify-Lyrics-Helper](https://github.com/WXRIW/Lyricify-Lyrics-Helper) | 網易雲、QQ 音樂、酷狗的介面參數，以及 QRC/KRC 的解密與解析：`QrcDecrypter.kt`、`QrcConverter.kt`、`KrcDecoder.kt` 由它移植而來，檔案開頭註明了出處 | Apache-2.0 |
| [AMLL TTML DB](https://github.com/amll-dev/amll-ttml-db) | 依 Spotify 曲目 ID 查詢逐字 TTML 歌詞 | 貢獻者自己的部分為 CC0-1.0；歌詞文字歸原權利人 |
| [LRCLIB](https://lrclib.net) | 預設的逐行同步歌詞來源 | 公開免費 API |
| Apple Music、Lyricify | 播放器的視覺與動效設計參考（倒數圓點、模糊、滾動時間等），未使用其程式碼 | — |
| [Spotify Android SDK](https://developer.spotify.com/documentation/android) / [Web API](https://developer.spotify.com/documentation/web-api) | 讀取播放狀態、控制播放、讀取歌單 | Spotify 開發者條款 |
| [Google ML Kit](https://developers.google.com/ml-kit) | 本機語言識別與翻譯 | ML Kit 條款 |
| AndroidX / Jetpack Compose、Room、WorkManager、OkHttp、Retrofit、Gson、Coil | 介面、資料庫、網路等 | 各自的開源授權（多為 Apache-2.0） |

更完整的說明見 [`docs/ATTRIBUTION_AND_COMPLIANCE.md`](docs/ATTRIBUTION_AND_COMPLIANCE.md)。

## 內容來源與合規

> 以下為專案說明，**不構成法律意見**。

本儲存庫的 [Apache-2.0](LICENSE) 授權只涵蓋程式碼、設定與文件。歌詞（包括翻譯）、封面與中繼資料仍歸各自的權利人所有。

| 來源 | 性質 | 快取策略 |
|------|------|----------|
| AMLL TTML DB | GitHub 上公開的社群資料庫；貢獻者的時間軸成果以 CC0-1.0 發佈，歌詞文字仍歸權利人 | 快取 |
| LRCLIB | 公開免費 API，無需金鑰 | 快取 |
| 網易雲音樂／QQ 音樂／酷狗 | 未公開文件的非公開端點；能存取不等於取得授權 | 僅保存在本次執行的記憶體中，除非你同意本機快取；絕不代理、鏡像或匯出 |
| Musixmatch | 未使用：完整／同步歌詞需要付費商業授權，免費方案只提供 30% 歌詞 | — |
| Spotify 私有歌詞介面 | 未使用 | — |

**個人學習與研究用途**：中國大陸《著作權法》第二十四條第一款第（一）項允許「為個人學習、研究或者欣賞，使用他人已經發表的作品」，許多法域也有類似的私人使用／合理使用例外。這降低了個人使用的風險，但：

- **不**涵蓋再散布歌詞；
- **不**能取代各服務商的服務條款。

任何散布建置版本（公開 APK、上架、收費、伺服器功能）的人，請先閱讀 [Attribution, Content Sources, and Compliance](docs/ATTRIBUTION_AND_COMPLIANCE.md)。

---

## 免責聲明

**本專案與 Spotify AB 無關，不是 Spotify 官方產品。**

- 本 App 不播放音訊，所有播放由 Spotify App 完成。
- 不修改 Spotify APK，不呼叫 Spotify 私有介面。
- 不收集或上傳使用者的播放紀錄；快取只保存在你自己的裝置上。
- 使用者需遵守 Spotify Developer Terms 及各歌詞來源的條款。

---

## 貢獻

1. Fork 本儲存庫
2. 建立功能分支（`git checkout -b feature/amazing-feature`）
3. 提交變更（`git commit -m 'Add amazing feature'`）
4. 推送分支（`git push origin feature/amazing-feature`）
5. 建立 Pull Request

新增介面文字需同時加入 4 種語言的 strings.xml；新增歌詞來源或協定參考需在[合規文件](docs/ATTRIBUTION_AND_COMPLIANCE.md)中登記授權。

---

## 授權

程式碼採用 [Apache License 2.0](LICENSE)。
