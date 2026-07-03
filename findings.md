# 研究发现 — MVP 0

## 项目当前状态

- 仓库仅有 `docs/SPOTIFY_LYRICS_PROXY_PLAN.md`、`AGENTS.md`、`CLAUDE.md` 和 `.gitignore`
- 无 Android 项目结构
- 无 Gradle wrapper
- 当前分支：`master`，仅一个提交 `docs: add project plan`

## 技术栈版本选择

| 组件 | 版本 | 说明 |
|------|------|------|
| AGP | 8.7.3 | Android Gradle Plugin 稳定版 |
| Kotlin | 2.0.21 | 与 Compose 编译器插件兼容 |
| Compose BOM | 2024.12.01 | 统一管理 Compose 库版本 |
| Compose Compiler | 内置（Kotlin 2.0+） | 不再需要单独指定 |
| Navigation | 2.8.5 | Compose Navigation |
| Room | 2.6.1 | 后续 MVP 3 使用 |
| Retrofit | 2.11.0 | 后续 MVP 2 使用 |
| Coil | 2.7.0 | 图片加载 |
| DataStore | 1.1.1 | 设置持久化 |
| WorkManager | 2.10.0 | 后续 MVP 6 使用 |

## Gradle 配置决策

- 使用 Kotlin DSL（`build.gradle.kts`）
- 使用 Version Catalog（`gradle/libs.versions.toml`）
- 不使用 Groovy DSL

## 包结构（单 module + package 分层）

```
com.example.spotifylyricsproxy/
├── SpotifyLyricsApp.kt          (Application)
├── MainActivity.kt
├── core/
│   └── model/                   (数据模型，后续)
├── ui/
│   ├── navigation/
│   │   └── AppNavigation.kt
│   ├── playback/
│   │   └── PlaybackScreen.kt
│   ├── cache/
│   │   └── CacheScreen.kt
│   ├── precache/
│   │   └── PrecacheScreen.kt
│   └── settings/
│       └── SettingsScreen.kt
└── (后续 MVP 按需添加 spotify/, lyrics/, database/, worker/ 等)
```
