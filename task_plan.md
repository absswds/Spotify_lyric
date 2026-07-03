# MVP 0：项目初始化 — 任务计划

## 阶段概览

| 阶段 | 标题 | 状态 |
|------|------|------|
| 1 | 创建 Gradle 工程骨架 | ⏳ 待执行 |
| 2 | 配置 Compose 与依赖 | ⏳ 待执行 |
| 3 | 建立包结构与核心类 | ⏳ 待执行 |
| 4 | 创建 4 个页面雏形与导航 | ⏳ 待执行 |
| 5 | 创建设置页雏形 | ⏳ 待执行 |
| 6 | 权限声明与 Application 类 | ⏳ 待执行 |
| 7 | 基础测试框架 | ⏳ 待执行 |
| 8 | 验证构建与 Git 提交 | ⏳ 待执行 |

## 关键决策

- **包名**：暂用 `com.example.spotifylyricsproxy`（公开前改为中性名称）
- **minSdk**：26（Android 8.0）
- **targetSdk / compileSdk**：35
- **模块结构**：单 `app` Gradle module + package 分层
- **Compose 版本**：使用 BOM 统一管理
- **导航**：Compose Navigation
- **构建工具**：Kotlin DSL（`.kts`）+ Version Catalog（`libs.versions.toml`）

## 验收标准

- [ ] App 可安装启动
- [ ] 四个页面可切换（播放页、缓存管理页、歌单预缓存页、设置页）
- [ ] Android 13+ 通知权限入口可用
- [ ] Gradle build 成功
- [ ] 基础测试可运行
