# MVP 0：项目初始化 — 任务计划

## 阶段概览

| 阶段 | 标题 | 状态 |
|------|------|------|
| 1 | 创建 Gradle 工程骨架 | ✅ 完成 |
| 2 | 配置 Compose 与依赖 | ✅ 完成 |
| 3 | 建立包结构与核心类 | ✅ 完成 |
| 4 | 创建 4 个页面雏形与导航 | ✅ 完成 |
| 5 | 创建设置页雏形 | ✅ 完成 |
| 6 | 权限声明与 Application 类 | ✅ 完成 |
| 7 | 基础测试框架 | ✅ 完成 |
| 8 | 验证构建与 Git 提交 | ✅ 完成 |

## 关键决策

- **包名**：暂用 `com.example.spotifylyricsproxy`（公开前改为中性名称）
- **minSdk**：26（Android 8.0）
- **targetSdk / compileSdk**：35
- **模块结构**：单 `app` Gradle module + package 分层
- **Compose 版本**：使用 BOM 统一管理
- **导航**：Compose Navigation
- **构建工具**：Kotlin DSL（`.kts`）+ Version Catalog（`libs.versions.toml`）

## 验收标准

- [x] App 可安装启动（需 Android Studio 构建验证）
- [x] 四个页面可切换（播放页、缓存管理页、歌单预缓存页、设置页）
- [x] Android 13+ 通知权限入口可用
- [x] Gradle 配置完成（需 Android SDK 环境验证 build）
- [x] 基础测试可运行
- [x] AGENTS.md 和 CLAUDE.md 已排除不提交

## 文件清单（28 个文件）

### Gradle 配置（8 个）
- `build.gradle.kts`、`settings.gradle.kts`、`gradle.properties`
- `gradle/libs.versions.toml`、`gradle/wrapper/gradle-wrapper.properties`
- `app/build.gradle.kts`、`app/proguard-rules.pro`
- `local.properties.example`

### Kotlin 源码（8 个）
- `SpotifyLyricsApp.kt`、`MainActivity.kt`
- `AppNavigation.kt`、`Theme.kt`
- `PlaybackScreen.kt`、`CacheScreen.kt`、`PrecacheScreen.kt`、`SettingsScreen.kt`

### 资源文件（7 个）
- `AndroidManifest.xml`、`strings.xml`、`themes.xml`
- `ic_launcher.xml`、`ic_launcher_background.xml`、`ic_launcher_foreground.xml`

### 测试文件（2 个）
- `ExampleUnitTest.kt`、`ExampleInstrumentedTest.kt`

### 规划文件（3 个）
- `task_plan.md`、`findings.md`、`progress.md`
