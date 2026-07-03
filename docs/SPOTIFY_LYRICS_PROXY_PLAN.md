# Spotify Lyrics Proxy 项目计划

## 1. 项目概述

Spotify Lyrics Proxy 是一个无需 Root 的 Android App，用来解决 Spotify 在 Android 系统控制中心、OPPO 系统媒体通知、未来 Android 16 原生实时活动 / Live Updates 等区域无法稳定显示实时歌词的问题。

App 本身不播放音乐，不替代 Spotify 播放器。实际音频仍由 Spotify App 播放，本项目只负责：

- 连接本机 Spotify App，读取当前播放状态。
- 将播放、暂停、上一首、下一首、seekTo 等控制转发给 Spotify。
- 从第三方 LRC 歌词源或本地缓存获取歌词。
- 根据 Spotify 当前播放进度同步当前歌词行。
- 创建自己的前台服务通知或 MediaSession 媒体卡片，把当前歌词行、歌名、歌手、封面、播放状态显示到系统区域。

第一阶段面向个人自用，优先在 OPPO 系列手机上验证标准前台服务通知和系统媒体卡片。厂商“流体云”和 Android 16 原生实时活动能力先作为后续扩展，不作为第一版硬依赖。

## 2. 目标与非目标

### 目标

- 不 Root 手机，不要求系统级权限。
- 不修改 Spotify APK。
- 不调用 Spotify 私有歌词接口。
- 不依赖 Musixmatch 悬浮窗。
- Spotify 只作为播放源、播放状态来源和控制目标。
- 歌词由 LRCLIB、本地 LRC 文件、本地缓存或后续第三方歌词源提供。
- 优先稳定、低耗电、可离线使用。
- 支持第一遍播放自动缓存歌词，第二遍播放离线可用。
- 支持固定歌单预缓存，贴近个人日常听歌习惯。
- 支持手动修正错配歌词、调整歌词偏移、导入本地 LRC。

### 非目标

- 不做音乐播放引擎。
- 不读取 Spotify 离线音乐文件。
- 不尝试访问 Spotify 加密缓存。
- 不隐藏或替换 Spotify 原生媒体卡片。
- 不追求第一版公开上架。
- 不把悬浮窗、无障碍、通知读取权限作为核心能力。
- 不承诺所有厂商系统的实时活动能力一致可用。

## 3. 用户场景

### 场景 A：日常听歌显示歌词

用户打开 Spotify 播放音乐，本 App 自动连接 Spotify，读取当前歌曲信息，查找本地歌词缓存。如果已缓存同步歌词，则系统通知标题显示当前歌词行，内容显示“歌名 - 歌手”，封面显示专辑图。

### 场景 B：第一次播放自动缓存

用户第一次播放一首歌时，本 App 使用 trackName、artistName、albumName、duration 查询 LRCLIB。若匹配到可信的 syncedLyrics，则写入 Room 缓存，并开始实时显示歌词。

### 场景 C：离线播放已有缓存歌曲

用户无网络时播放 Spotify 已下载歌曲。本 App 不读取 Spotify 音频文件，只根据 Spotify 播放状态识别 track ID。如果本地已有歌词和封面缓存，则继续正常显示；如果没有缓存，则显示“离线状态下暂无本地歌词”。

### 场景 D：固定歌单预缓存

用户选择一个常听 Spotify 歌单。App 通过 Spotify Web API 读取歌单曲目列表，在 Wi-Fi 或充电条件下用 WorkManager 后台补齐歌词缓存，并显示缓存进度、失败数、not_found 数。

### 场景 E：歌词错配后手动修正

App 自动匹配的歌词不正确时，用户可以标记当前歌词错误、重新匹配候选、导入本地 `.lrc` 文件或粘贴 LRC 文本，并将手动歌词绑定到当前 Spotify track ID。

## 4. 总体架构

推荐第一版使用单 `app` Gradle module，内部按 package 分层。这样初始化成本低，适合 MVP 快速推进。等功能稳定后，再按边界拆成多 Gradle module。

核心组件：

- `SpotifyRemoteRepository`：封装 Spotify App Remote 连接、状态订阅、控制转发。
- `SpotifyWebApiRepository`：封装 Spotify Web API OAuth、歌单列表、歌单曲目读取。
- `LyricsRepository`：统一歌词查询入口，先查缓存，再调歌词源。
- `LrclibLyricsSource`：LRCLIB 查询实现。
- `LocalLyricsSource`：本地 LRC 导入和读取实现。
- `LyricMatcher`：歌词候选评分和阈值判断。
- `LrcParser`：解析 LRC 文本为时间轴行。
- `PlaybackClock`：根据 Spotify PlayerState 估算当前播放进度。
- `LyricSyncEngine`：根据播放进度和 LRC 行计算当前歌词行。
- `NotificationController`：维护前台服务通知。
- `MediaSessionController`：维护 App 自己的媒体卡片，并将控制转发给 Spotify。
- `PlaylistPrecacheWorker`：后台预缓存歌单歌词。
- `SettingsRepository`：DataStore 设置读取和保存。
- `AppDatabase`：Room 数据库。

建议数据流：

```text
Spotify PlayerState
  -> SpotifyRemoteRepository
  -> CurrentTrack model
  -> LyricsRepository
  -> Room cache lookup
  -> LRCLIB / local source fetch if needed
  -> LyricMatcher
  -> LrcParser
  -> PlaybackClock
  -> LyricSyncEngine
  -> NotificationController / MediaSessionController / UI
```

## 5. 关键技术路线

### Spotify 播放状态与控制

- 使用 Spotify Android SDK / App Remote 连接本机 Spotify App。
- 获取当前播放信息：
  - Spotify track URI / track ID
  - 歌名
  - 歌手
  - 专辑名
  - 时长
  - 当前播放进度
  - 播放 / 暂停状态
  - 封面 image URI
- 支持控制：
  - 播放
  - 暂停
  - 继续播放
  - 下一首
  - 上一首
  - seekTo
- 必须处理：
  - Spotify 未安装
  - Spotify 未登录
  - App Remote 授权失败
  - 连接断开
  - 回调延迟或缺失

### 歌词获取

第一版只实现 LRCLIB + 本地缓存，预留扩展接口：

- `LyricsSource.search(request): List<LyricCandidate>`
- `LyricsSource.getById(id): LyricCandidate?`
- `LyricsSource.importLocal(trackId, lrcText)`

LRCLIB 查询参数优先使用：

- `trackName`
- `artistName`
- `albumName`
- `duration`

优先获取 `syncedLyrics`。如果只有 `plainLyrics`，可以缓存为 `plain_only`，但不作为实时歌词使用。

### 歌词同步

- LRC 解析支持：
  - `[00:36.20] 歌词文本`
  - `[01:02.100] 歌词文本`
- 解析结果：
  - `startMs`
  - `text`
- 当前歌词行查找必须使用二分查找。
- 播放进度使用 `PlaybackClock` 估算：
  - 收到 Spotify PlayerState 时记录 `basePositionMs` 和 `baseElapsedRealtime`。
  - 播放中用当前 elapsedRealtime 估算进度。
  - 暂停时固定进度。
  - seekTo 或切歌后重置基准。
- 播放中每 300ms 到 500ms 检查一次当前歌词行。
- 暂停时降频到 2s 到 5s。
- 只有歌词行变化时才更新通知或 MediaSession。

## 6. 数据流

### 播放中自动显示歌词

1. App 启动或前台服务启动。
2. `SpotifyRemoteRepository` 连接 Spotify App Remote。
3. 收到 PlayerState。
4. 提取当前 track ID。
5. `LyricsRepository` 查询 Room 缓存。
6. 如果缓存命中且有 synced lyrics，解析 LRC。
7. 如果缓存未命中，调用 LRCLIB 查询。
8. `LyricMatcher` 对候选歌词评分。
9. 高于阈值的候选写入 `LyricCache`。
10. `PlaybackClock` 估算当前播放进度。
11. `LyricSyncEngine` 计算当前歌词行。
12. UI、通知、MediaSession 根据当前歌词行刷新。

### 离线播放

1. App 仍可通过 Spotify App Remote 获得当前曲目信息。
2. 检查网络状态。
3. 如果无网络，只查本地 Room 和本地封面缓存。
4. 有缓存则显示歌词。
5. 无缓存则显示“离线状态下暂无本地歌词”。
6. 不访问 Spotify 离线音乐文件，不读取 Spotify 加密缓存。

### 歌单预缓存

1. 用户完成 Spotify Web API OAuth。
2. App 读取用户歌单列表。
3. 用户选择一个常听歌单。
4. 保存 `PlaylistCacheJob`。
5. WorkManager 在 Wi-Fi / 充电等约束满足时执行。
6. 读取歌单曲目。
7. 对未缓存曲目逐首查询歌词。
8. 成功、plain_only、not_found、failed 都写入缓存状态。
9. 页面展示缓存进度和失败列表。

## 7. 数据库设计

使用 Room。歌词缓存必须以 Spotify track ID 为主键，不能只用歌名和歌手。

### LyricCache

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| spotifyTrackId | String | 主键 |
| spotifyUri | String | Spotify URI |
| title | String | 歌名 |
| artist | String | 歌手，多个歌手可用分隔符或 JSON |
| album | String | 专辑名 |
| durationMs | Long | 歌曲时长 |
| source | String | `lrclib` / `local_file` / `manual_paste` 等 |
| syncedLyrics | String? | LRC 同步歌词 |
| plainLyrics | String? | 普通歌词 |
| offsetMs | Long | 单曲歌词偏移 |
| fetchStatus | String | `success` / `plain_only` / `not_found` / `failed` |
| confidenceScore | Int | 匹配置信度 |
| lastTriedAt | Long | 最近尝试查询时间 |
| nextRetryAt | Long? | 下次允许重试时间 |
| lastPlayedAt | Long | 最近播放时间 |
| playCount | Int | 播放次数 |
| updatedAt | Long | 更新时间 |

### TrackPlayHistory

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| spotifyTrackId | String | 主键 |
| spotifyUri | String | Spotify URI |
| title | String | 歌名 |
| artist | String | 歌手 |
| album | String | 专辑 |
| durationMs | Long | 时长 |
| firstSeenAt | Long | 首次发现时间 |
| lastPlayedAt | Long | 最近播放时间 |
| playCount | Int | 播放次数 |
| totalPlayedMs | Long | 累计播放时长 |

### PlaylistCacheJob

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| playlistId | String | 主键 |
| playlistName | String | 歌单名称 |
| enabled | Boolean | 是否启用后台预缓存 |
| lastScanAt | Long? | 最近扫描时间 |
| totalTracks | Int | 总歌曲数 |
| cachedTracks | Int | 已缓存同步歌词数 |
| failedTracks | Int | 失败数 |
| notFoundTracks | Int | 未找到数 |

### LyricCandidate

用于保存候选歌词，方便手动修正：

- `id`
- `spotifyTrackId`
- `source`
- `sourceLyricId`
- `title`
- `artist`
- `album`
- `durationMs`
- `syncedLyrics`
- `plainLyrics`
- `score`
- `createdAt`

### RejectedLyricMatch

用于错歌黑名单：

- `id`
- `spotifyTrackId`
- `source`
- `sourceLyricId`
- `reason`
- `createdAt`

## 8. 歌词匹配策略

### 查询流程

1. 先按 `spotifyTrackId` 查本地缓存。
2. 缓存没有，使用完整歌名 + 歌手 + 专辑 + 时长查询。
3. 找不到，再使用完整歌名 + 歌手查询。
4. 找不到，再使用清洗后的歌名 + 歌手查询。
5. 仍找不到，则标记为 `not_found`。
6. 对 `not_found` 和 `failed` 设置 `nextRetryAt`，默认 7 天后再重试。

### 歌名清洗

清洗只用于查询和评分，不覆盖原始展示字段。

建议规则：

- 去掉括号中的常见版本标记：`Remastered`、`Live`、`Acoustic`、`Explicit`、`Radio Edit`、`Instrumental`。
- 统一大小写。
- 去除多余空格、标点差异。
- 保留中文、日文、韩文主体字符。

### 评分建议

总分建议 100 分：

- 歌名完全匹配：+35
- 清洗后歌名匹配：+22
- 主歌手匹配：+20
- 其他歌手部分匹配：+8
- 时长差小于 2 秒：+18
- 时长差 2 到 5 秒：+8
- 专辑名匹配：+8
- 有 syncedLyrics：+12
- 只有 plainLyrics：-20
- 命中黑名单：直接排除

建议阈值：

- `>= 75`：自动采用。
- `60 - 74`：保存为候选，标记需要手动确认。
- `< 60`：不采用。

## 9. 通知与 MediaSession 设计

### 方案 A：前台服务通知

MVP 4 优先实现。

通知内容：

- 标题：当前歌词行。
- 内容：`歌名 - 歌手`。
- 大图标：专辑封面。
- 操作按钮：
  - 上一首
  - 播放 / 暂停
  - 下一首
- 点击通知：打开 App 播放页。

实现要求：

- Android 13+ 请求 `POST_NOTIFICATIONS`。
- Android 8+ 创建通知渠道。
- 服务只在 Spotify 播放或需要维持歌词卡片时活跃。
- 只有歌词行变化、播放状态变化或曲目变化时更新通知。
- 不要每 300ms 直接刷新通知。

### 方案 B：MediaSession 代理卡片

MVP 5 单独实验。

设计：

- 创建 App 自己的 MediaSession。
- 系统媒体卡片显示本 App，而不是强行隐藏 Spotify 卡片。
- `MediaMetadata.title` 填当前歌词行。
- `MediaMetadata.artist` 填 `歌名 - 歌手`。
- Album art 使用当前歌曲封面。
- PlaybackState 同步 Spotify 播放状态。
- 播放 / 暂停 / 下一首 / 上一首 / seekTo 操作转发给 Spotify。

注意：

- Spotify 原卡片可能同时存在，这是可接受状态。
- 不同厂商系统对媒体卡片排序、封面、按钮、标题更新频率处理不同。
- OPPO 真机验证结果要记录到开发日志。

### MediaSessionCompat 与 Media3 取舍

`MediaSessionCompat`：

- 优点：传统系统媒体卡片兼容经验多，适合简单代理卡片。
- 缺点：API 较旧，长期扩展性较弱。

`Media3`：

- 优点：新项目更现代，生态更新，适合未来扩展。
- 缺点：本 App 不播放音频，只做代理，需要谨慎实现 Player/session 抽象，避免系统误判。

建议：

- MVP 4 先不纠结 MediaSession，完成前台服务通知。
- MVP 5 建立实验分支，在 OPPO 真机分别验证 Media3 和 Compat。
- 如果 Media3 能稳定显示并转发控制，采用 Media3。
- 如果 Media3 代理模型过重或 OPPO 表现不稳定，使用 MediaSessionCompat 作为个人项目稳妥方案。

## 10. 缓存与离线策略

### 歌词缓存

- 每首歌以 Spotify track ID 为主键缓存。
- 成功、plain_only、not_found、failed 都要缓存状态。
- `failed` 和 `not_found` 必须有 `nextRetryAt`，避免每次播放重复请求。
- `plainLyrics` 可用于后续手动查看，但不参与实时同步。

### 封面缓存

- 获取 Spotify 当前封面后缓存本地缩略图。
- 建议尺寸：300x300 或 512x512。
- 缓存 key 优先使用 Spotify image URI 或 track ID。
- 离线时使用本地封面。
- 不频繁重复下载同一封面。

### 离线行为

- 有 synced lyrics 缓存：正常显示实时歌词。
- 只有 plain lyrics：显示当前歌曲信息，并提示无同步歌词。
- 无缓存：显示“离线状态下暂无本地歌词”。
- 不读取 Spotify 离线音乐文件。
- 不访问 Spotify 加密缓存。

## 11. 歌单预缓存设计

歌单预缓存需要 Spotify Web API OAuth。App Remote 不能可靠读取完整歌单曲目列表。

### 功能

- 用户选择一个常听 Spotify 歌单。
- App 读取歌单内曲目列表。
- 检查每首歌是否已有歌词缓存。
- 没有缓存的歌曲在 Wi-Fi 条件下后台补齐歌词。
- 支持手动立即补齐。

### 设置

- 仅 Wi-Fi 下载歌词。
- 充电时自动补齐。
- 失败项 7 天后再重试。
- 是否缓存封面。

### 页面指标

- 歌单总歌曲数。
- 已缓存同步歌词数。
- 无同步歌词数。
- 下载失败数。
- not_found 数量。
- 最近扫描时间。

### WorkManager 约束

- 默认要求网络连接。
- 用户开启“仅 Wi-Fi”时使用 unmetered network 约束。
- 用户开启“充电时预缓存”时添加 charging 约束。
- 单次任务限制最大处理数量，避免长时间后台运行。
- 网络请求去重，失败使用指数退避或 7 天重试策略。

## 12. 权限与隐私

### 必要权限

- `INTERNET`：下载歌词、封面、调用 Spotify Web API。
- `FOREGROUND_SERVICE`：前台服务。
- `POST_NOTIFICATIONS`：Android 13+ 通知。
- Spotify OAuth / App Remote 授权。

### 尽量避免

- 不强依赖通知读取权限。
- 不需要悬浮窗权限。
- 不需要 Root。
- 不需要无障碍权限。
- 不请求读取本地音频文件权限。

### 隐私原则

- 本地 Room 保存播放历史、歌词缓存、歌单缓存状态。
- 不上传用户播放历史到自有服务。
- Spotify OAuth token 存储在安全位置，不写入日志。
- Debug 日志不能打印完整 access token。

## 13. 省电与后台策略

### 服务生命周期

- 只有 Spotify 播放或用户明确启用歌词卡片时启动前台服务。
- Spotify 暂停时降低检查频率。
- Spotify 停止超过 5 分钟：降低服务活跃度，只保留必要订阅。
- Spotify 停止超过 30 分钟：停止服务。

### 刷新策略

- 播放中：300ms 到 500ms 检查歌词行。
- 暂停中：2s 到 5s 检查。
- 通知只在歌词行变化时更新。
- MediaSession metadata 也只在歌词行变化时更新。
- 网络请求去重。
- 封面下载去重。
- 歌词失败结果缓存。

### 后台限制

- OPPO 等系统可能限制后台服务。
- 第一版不要依赖隐式长期后台保活。
- 设置页可以提供“电池优化设置指引”入口，但不强迫用户操作。
- 真机验证时记录服务被杀、通知消失、媒体卡片不更新等情况。

## 14. UI 页面规划

第一阶段 UI 分成两层：先完成工程骨架，后续再做体验设计和视觉打磨。

### 工程骨架版：第一阶段先做

要求：

- 使用 Jetpack Compose。
- 接入 Compose Navigation。
- 每个页面有 ViewModel 状态绑定。
- 显示 loading、error、empty 状态。
- 提供调试信息，方便排查 Spotify、歌词、服务状态。
- 不追求最终视觉、复杂动效、品牌化配色。

页面：

1. 播放页
   - 当前封面占位。
   - 歌名 / 歌手。
   - 当前歌词。
   - 上一句 / 下一句。
   - 播放进度。
   - 歌词来源。
   - 匹配置信度。
   - 当前 offset。
   - 连接 Spotify、播放/暂停、下一首、重新匹配、偏移调整按钮。
   - 调试字段：track ID、fetchStatus、service state。

2. 缓存管理页
   - 已缓存歌曲列表。
   - 搜索框。
   - 状态筛选：success / plain_only / not_found / failed。
   - 删除歌词。
   - 重新下载。
   - 手动导入 LRC。

3. 歌单预缓存页
   - Spotify Web API 登录状态。
   - 选择歌单。
   - 缓存进度。
   - 立即补齐歌词按钮。
   - 失败歌曲列表。
   - 重新尝试失败项。

4. 设置页
   - 自动缓存歌词。
   - 仅 Wi-Fi 下载。
   - 充电时预缓存。
   - 缓存封面。
   - 优先歌词源。
   - 全局歌词偏移。
   - 通知显示格式。
   - MediaSession 代理卡片开关。
   - 清理缓存。

### 体验设计版：后续阶段负责

后续在代码基础稳定后设计：

- 页面信息架构。
- 视觉风格和色彩系统。
- OPPO 真机上的播放页和通知体验。
- 歌词展示层级、当前行强调、上下文歌词密度。
- 偏移调整交互。
- 候选歌词选择体验。
- 缓存管理和歌单预缓存的数据密度。
- 设置页分组、说明文字和风险提示。

## 15. 推荐技术栈

- Kotlin
- Jetpack Compose
- Compose Navigation
- Room
- WorkManager
- Retrofit 或 OkHttp
- Kotlin Coroutines / Flow
- Foreground Service
- MediaSessionCompat / Media3
- Spotify Android SDK / App Remote
- Spotify Web API
- Coil
- DataStore
- JUnit
- Turbine
- Robolectric
- Android instrumented tests

## 16. 模块划分

### MVP 推荐：单 Gradle module + package 分层

第一版建议只有 `app` module，内部结构：

```text
com.example.spotifylyricsproxy
  app
  core.model
  core.result
  spotify.remote
  spotify.webapi
  lyrics
  lyrics.lrclib
  lyrics.local
  playback.clock
  sync
  notification
  mediasession
  database
  settings
  worker
  ui.playback
  ui.cache
  ui.precache
  ui.settings
```

优点：

- 初始化快。
- Gradle 配置简单。
- 适合快速完成代码基础。
- 后续重构成本可控。

### 后续多 Gradle module 拆分

功能稳定后可拆分：

- `app`
- `core-model`
- `spotify-remote`
- `spotify-webapi`
- `lyrics`
- `lyrics-lrclib`
- `lyrics-local`
- `playback-clock`
- `notification`
- `mediasession`
- `database`
- `settings`
- `ui`

拆分时机：

- LRC parser、matcher、PlaybackClock 已有测试。
- Spotify、lyrics、database 边界稳定。
- 编译速度或包依赖开始影响开发效率。

## 17. MVP 路线图

### MVP 0：项目初始化

基础工程阶段：

- 创建 Kotlin + Compose Android 项目。
- 配置 Android 8+ minSdk。
- 配置基础页面导航。
- 配置权限声明。
- 创建设置页雏形。
- 建立包结构。
- 加入基础测试框架。

后续 UI 设计阶段：

- 设计页面信息架构和基础视觉方向。

验收：

- App 可安装启动。
- 四个页面可切换。
- Android 13+ 通知权限入口可用。

提交节点：

- `chore: initialize android project`

### MVP 1：Spotify 连接

基础工程阶段：

- 接入 Spotify Android SDK / App Remote。
- 连接本机 Spotify。
- 显示当前播放歌曲。
- 显示播放状态和进度。
- 支持播放 / 暂停 / 下一首。
- 处理未安装、未登录、授权失败、连接断开。

验收：

- OPPO 真机能连接 Spotify。
- 播放页能显示当前歌曲。
- 控制按钮能转发给 Spotify。

提交节点：

- `feat: connect spotify app remote`

### MVP 2：歌词获取

基础工程阶段：

- 接入 LRCLIB。
- 使用 trackName、artistName、albumName、duration 查询歌词。
- 实现 LRC parser。
- 实现 lyric matching score。
- App 页面显示当前歌词。
- 单测覆盖 parser 和 matcher。

验收：

- 可查询到 LRCLIB syncedLyrics。
- 当前歌词随播放进度变化。
- parser 支持 `[00:36.20]` 和 `[01:02.100]`。

提交节点：

- `feat: fetch and sync lrclib lyrics`

### MVP 3：本地缓存

基础工程阶段：

- 接入 Room。
- 实现 `LyricCache`、`TrackPlayHistory`。
- 以 Spotify track ID 为主键缓存歌词。
- 缓存 failed / not_found / plain_only。
- 离线读取歌词。

验收：

- 同一 track 第二次播放直接读缓存。
- 无网络且有缓存时正常显示。
- 无网络且无缓存时显示明确状态。

提交节点：

- `feat: cache lyrics with room`

### MVP 4：前台服务通知

基础工程阶段：

- 实现前台服务。
- 通知显示当前歌词。
- 通知按钮控制 Spotify。
- 只在歌词行变化时更新通知。
- 适配 Android 13+ 通知权限。

后续 UI 设计阶段：

- 调整通知文案、显示格式、播放页入口体验。

验收：

- OPPO 真机通知栏显示当前歌词。
- 按钮能控制 Spotify。
- 长时间播放通知不会高频闪动。

提交节点：

- `feat: show lyrics foreground notification`

### MVP 5：MediaSession 代理卡片

基础工程阶段：

- 在实验分支验证 Media3 和 MediaSessionCompat。
- 创建 App 自己的媒体卡片。
- title 显示当前歌词。
- artist 显示 `歌名 - 歌手`。
- 封面显示专辑图。
- 按钮转发给 Spotify。

验收：

- OPPO 系统媒体卡片能显示本 App 信息。
- Spotify 原卡片同时存在时 App 不崩溃、不抢占异常。
- 记录 Media3 / Compat 实测差异。

提交节点：

- `feat: add mediasession proxy card`

### MVP 6：歌单预缓存

基础工程阶段：

- 接入 Spotify Web API OAuth。
- 读取指定 Spotify 歌单。
- WorkManager 后台补齐歌词。
- 页面显示缓存进度。

验收：

- 能选择歌单。
- 能统计总数、已缓存、failed、not_found。
- Wi-Fi / 充电约束生效。

提交节点：

- `feat: precache playlist lyrics`

### MVP 7：手动修正

基础工程阶段：

- 单曲 offset 保存。
- 全局 offset。
- 手动重新匹配。
- 本地 LRC 导入。
- 粘贴 LRC。
- 错歌黑名单。

后续 UI 设计阶段：

- 设计候选歌词选择、偏移调整、错误修正体验。

验收：

- 手动导入歌词能绑定当前 Spotify track ID。
- 标记错误后不会再次自动采用同一候选。
- offset 调整实时生效。

提交节点：

- `feat: add manual lyric correction`

## 18. 风险与应对

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| Spotify App Remote 连接不稳定 | 无法持续拿到状态 | 实现重连、错误状态展示、本地 PlaybackClock 兜底 |
| Spotify 原生媒体卡片和本 App 卡片同时存在 | 用户看到两个卡片 | 明确接受共存，不尝试隐藏 Spotify 卡片 |
| OPPO / Android 16 实时活动支持不一致 | 流体云效果不稳定 | 第一版以标准通知和媒体卡片验收，实时活动后续适配 |
| LRCLIB 中文歌覆盖不足 | 找不到歌词 | 支持 plain_only、本地 LRC、后续网易云/QQ 源 |
| 歌词匹配错误 | 显示错歌 | 使用评分阈值、候选确认、黑名单 |
| 歌词时间轴偏移 | 当前行不准 | 支持全局 offset 和单曲 offset |
| 后台服务被系统杀 | 通知中断 | 前台服务、降低耗电、设置页提供电池优化指引 |
| 通知刷新过频耗电 | 电量下降、系统限制 | 只在歌词行变化时刷新 |
| Spotify 开发者模式用户数量限制 | 授权受限 | 个人自用优先，记录账号配置步骤 |
| 非官方歌词源失效或合规风险 | 歌词获取失败 | 第一版只用 LRCLIB，第三方源做可插拔并默认关闭 |

### 合规与版权风险边界

本项目第一阶段按个人自用工具设计。后续如果放到 GitHub、提供 APK 下载、公开宣传或尝试上架，必须先做一次合规审查，并至少完成以下调整：

- 项目公开名称不要以 `Spotify` 开头，避免暗示 Spotify 官方、合作或背书。内部代号可以保留，但公开 README、应用名、包名、图标和宣传文案应改成中性名称，例如 `Lyrics Card`、`Nowline` 或 `LRC Media Card`。
- README 明确声明本项目不是 Spotify 官方产品，也不与 Spotify 关联。
- App 不内置任何歌词数据，不提交歌词样本库到仓库。
- 不提供歌词数据库导出、批量分享、公共歌词 API、歌词镜像服务。
- 本地歌词缓存仅用于用户个人设备上的当前播放显示。
- LRCLIB 或其他歌词源必须显示来源，并提供清理本地歌词缓存的入口。
- 不宣传“解锁 Spotify 歌词”“免费 Spotify 歌词接口”“替换 Spotify 歌词服务”等容易触发平台或版权风险的卖点。
- Spotify metadata、封面和播放历史只为 App 运行所需本地保存，不做独立曲库产品。
- 如果显示 Spotify 封面、歌名、专辑或歌单信息，应提供跳回 Spotify 原内容的入口，并遵守 Spotify 品牌和归因要求。
- 用户断开 Spotify 授权时，应提供删除 Spotify 相关本地数据的操作。
- 不使用 Spotify Content 训练 AI/ML 模型，不把歌词、metadata、封面或播放历史上传给模型服务。
- 公开发布前重新检查 Spotify Developer Terms、Developer Policy、LRCLIB 条款、歌词源许可和目标平台上架规则。

## 19. 后续可扩展功能

- Android 16 Live Updates 专项适配。
- OPPO 流体云专项验证和适配记录。
- 多歌词源：
  - 网易云歌词源
  - QQ 音乐歌词源
  - 本地歌词目录扫描
- 歌词翻译显示。
- 罗马音显示。
- 自动调整 offset。
- 桌面小组件。
- Wear OS 简易歌词显示。
- 导出 / 导入缓存数据库。
- 歌词候选众包手动整理，但不默认上传。

## 20. 开发检查清单

### 每次开始开发前

- 阅读本计划文档。
- 确认当前 Git 分支。
- `git status` 检查是否有用户未提交改动。
- 明确当前 MVP 阶段。
- 不跨阶段提前实现大功能。

### 每个重要节点

- 完成可验证改动。
- 运行相关测试。
- 真机功能记录结果。
- 提交 Git。
- 必要时开新分支。

### 代码质量

- LRC parser 必须有单测。
- LyricMatcher 必须有单测。
- PlaybackClock 必须有单测。
- Room DAO 必须有测试。
- 网络层使用 mock 测试。
- WorkManager 约束逻辑必须可测。

### 安全边界

- 不 Root。
- 不修改 Spotify APK。
- 不调用 Spotify 私有歌词 API。
- 不访问 Spotify 加密缓存。
- 不把 OAuth token 写入日志。
- 不把密钥提交到仓库。

### GitHub / 公开发布前检查

- 改掉公开项目名中以 `Spotify` 开头的命名。
- README 增加非官方免责声明。
- 确认仓库没有歌词正文样本、token、client secret、真实用户播放历史。
- 确认没有提供歌词批量导出或歌词镜像服务。
- 确认 App 内有清理歌词缓存、封面缓存、Spotify 授权数据的入口。
- 确认文案没有暗示绕过 Spotify Premium、解锁 Spotify 歌词或替换 Spotify 官方服务。
- 重新检查当时最新的 Spotify、LRCLIB 和目标分发平台规则。

### UI 分工

- 第一阶段先完成可运行页面骨架和状态接线。
- 后续阶段负责交互、视觉、信息架构和 OPPO 真机体验打磨。
- 早期页面以调试和验证为主，不追求最终视觉。

### 验收记录

- 记录测试设备型号和系统版本。
- 记录 Spotify 版本。
- 记录通知表现。
- 记录 MediaSession 表现。
- 记录后台服务存活情况。
- 记录歌词匹配失败样例。
