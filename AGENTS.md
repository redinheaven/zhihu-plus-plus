# Zhihu++ 魔改分支 Agent 指南

本仓库 fork 自 [zly2006/zhihu-plus-plus](https://github.com/zly2006/zhihu-plus-plus)，当前 fork 地址为 [redinheaven/zhihu-plus-plus](https://github.com/redinheaven/zhihu-plus-plus)。目标是在上游基础上进行魔改。本文件是本 fork 的首要 agent 指南；`CLAUDE.md` 是上游维护者沉淀的经验规则，仍然有参考价值，但其中与上游维护者个人环境绑定的部分不适用（见文末「上游规则的取舍」）。

## 项目概览

Zhihu++ 是注重隐私、无广告的第三方知乎客户端，基于 Kotlin Multiplatform + Compose Multiplatform：

- Android 为主，提供 `full`（含端侧 ONNX/embedding NLP 过滤）和 `lite`（约 4 MB）两个 flavor。
- 实验性桌面端（JVM，Windows MSI / Linux AppImage）、macOS 原生（Kotlin/Native）以及 iOS target。
- 核心卖点：本地推荐算法（爬虫 + 本地打分，独立于知乎推荐）、屏蔽词 / NLP 屏蔽 / 屏蔽用户与话题、去盐选、zse96 v2 签名调用 Web API、模拟 Android API。
- 许可证：AGPL-3.0（仅 v3）。魔改后分发二进制时必须同时提供源码，保留版权头。

版本号在 `gradle.properties`（`app.versionName` / `app.versionCode`）。Gradle 9.7.1，JDK 17 toolchain（`app` 的部分任务用到 JDK 21 toolchain，foojay 插件可自动下载）。

## 目录结构

| 路径 | 说明 |
| --- | --- |
| `app/` | Android 应用壳。`src/main` 公共，`src/full` / `src/lite` 为 flavor 专属，`src/androidTest` 为 instrument test |
| `shared/` | KMP 共享模块，绝大部分业务和 UI 都在这里 |
| `shared/src/commonMain/kotlin/com/github/zly2006/zhihu/` | 跨平台核心代码（见下表） |
| `shared/src/androidMain` / `androidFull` / `androidLite` | Android 共用 `actual` / full 专属 / lite 专属 |
| `shared/src/jvmMain` | 桌面端 `actual` 与桌面入口相关实现 |
| `shared/src/macosMain` / `iosMain` / `nativeMain` | Apple 平台实现 |
| `shared-local-db/` | 本地数据库（KMP） |
| `desktopApp/` | 桌面端入口，`mainClass = com.github.zly2006.zhihu.desktop.MainKt` |
| `macosApp/`, `macosUiDebug/` | macOS 原生宿主与离屏 UI 调试器 |
| `sentence_embeddings/` + `rs-hf-tokenizer/` | full 版 NLP：Kotlin 封装 + Rust tokenizer（需 `cargo-ndk`） |
| `rs-zse-sign/` | zse 签名算法的 Rust 实现（独立 crate） |
| `aigc-vote-server/` | AIGC 标记投票后端（Rust，有自己的 `AGENTS.md`） |
| `apk-recompress/` | APK 重压缩工具（Rust） |
| `third_party/` | 内置的 markdown / latex / 代码高亮渲染库，作为 Gradle 子项目引入 |
| `buildSrc/` | 构建逻辑（git 信息、macOS 签名） |
| `docs/` | 设计与性能文档，改 UI 前先读 `docs/ai-ui-design-guide.md` |
| `.agents/skills/` | 上游的 agent 技能（ui-test、launch-on-device、zhihu-reproduce 等） |
| `misc/` | 油猴脚本、zse 原始 JS、表情资源、证书注入脚本等 |

`shared/.../zhihu/` 下的主要包：

| 包 | 内容 |
| --- | --- |
| `account/` | 登录（手机验证码、扫码、Cookie）、账户 store / client、身份切换 |
| `data/` | 数据模型与 API：`DataHolder`、`Feed`、`ZhihuApiClients`、`ZhihuDataCore` 等 |
| `navigation/` | `NavDestination.kt`（所有路由的 sealed interface）、回答切换导航 |
| `viewmodel/feed/` | 首页 / 关注 / 热榜 / 搜索 / 历史等 feed 的 ViewModel |
| `viewmodel/local/` | **本地推荐算法**：`CrawlingExecutor`、`FeedGenerator`、`LocalRecommendationEngine`、`UserBehaviorAnalyzer`、`TaskScheduler` |
| `viewmodel/filter/` | 屏蔽词、内容过滤管理、黑名单导入导出 |
| `filter/` | 内容过滤与打开记录支持对象 |
| `ui/` | 所有 Compose 页面（`ZhihuMain.kt` 为主界面入口），`ui/subscreens/` 为设置子页面 |
| `markdown/`, `reading/`, `editor/` | 正文渲染、朗读、写回答编辑器 |
| `notification/`, `updater/`, `theme/`, `platform/`, `util/` | 通知、更新、主题、平台能力声明、工具 |

## 构建与验证

```bash
# Android（full 与 lite 必须分开两次 Gradle 调用，不能写在同一条命令里）
./gradlew :app:assembleLiteDebug     # 产物 app/build/outputs/apk/lite/debug/
./gradlew :app:assembleFullDebug     # 需要 Rust + cargo-ndk + NDK
./gradlew :app:testLiteDebugUnitTest # 单元测试同样按 flavor 分开，不要用全局 test

# 桌面端
./gradlew :desktopApp:run

# 格式化（提交前必须）
./gradlew ktlintFormat
```

修改后的固定顺序：**构建通过 → `ktlintFormat` → review → 提交**。

### 本机（Windows）环境注意

- 当前开发机为 Windows 10，shell 可用 PowerShell 或 Git Bash；Bash 下用 `./gradlew`，PowerShell 下用 `.\gradlew.bat`。
- 本机已配置（用户级环境变量，新开终端生效）：
  - JDK：Temurin 17，`JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`；
  - Android SDK：`ANDROID_HOME=C:\Users\wugao\AppData\Local\Android\Sdk`，仅装了 command-line tools、`platform-tools`（已加入 PATH，含 `adb`）、`platforms/android-37.0`、`build-tools/37.0.0`；没有装 Android Studio、模拟器和 NDK；
  - 仓库根目录 `local.properties` 已写入 `sdk.dir`（git 已忽略，不入库）。
- 新版 `sdkmanager` 包名用 `/` 分隔，例如 `sdkmanager "platforms/android-37.0"`，旧的 `;` 写法会报 `Package not found`。
- 未安装 Rust / `cargo-ndk` / NDK / `gh`：仅构建 full 版或改 Rust 时才需要，只做 lite 魔改可以跳过。
- 仓库 `core.symlinks=false`，上游的符号链接在 Windows 上会被检出为内容是目标路径的普通文本文件，修改这类文件时注意不要误提交类型变更。
- **本机只有 7.9 GB 内存 / 4 线程**，项目默认的 Gradle 4 GB + Kotlin 3 GB + 并行编译会让机器卡死。已在 `C:\Users\wugao\.gradle\gradle.properties` 用本机设置覆盖为 Gradle 2 GB、Kotlin 1.5 GB、关闭并行、最多 2 个 worker、守护进程闲置 10 分钟自动退出。不要修改仓库内 `gradle.properties` 的内存参数（会与上游冲突），也不要删除本机覆盖。
- 编译耗时长（首次 lite debug 约 30 分钟），agent 不应在用户未要求时主动启动完整编译；编译结束后执行 `./gradlew --stop` 释放内存。
- Gradle 配置缓存和构建缓存默认开启；改了构建脚本后遇到奇怪问题可加 `--no-configuration-cache` 排查。
- 新开 git worktree 时把 `local.properties` 一并复制过去。

## 关键约定

### 数据与网络
- 知乎 API 返回 `snake_case`，项目内 data class 一律 `camelCase`；`AccountData.fetch*()` / `decodeJson()` 内部自动 `snake_case2camelCase()`，不要手动转换。
- 业务请求使用当前账户 store 持有的 HttpClient（Ktor）。Web API 需 `signFetchRequest(context)` 做 zse96 v2 签名；Android API 使用 `AccountData.ANDROID_HEADERS` / `ANDROID_USER_AGENT`。
- 依赖 API 字段的新功能，先用真实请求核对字段是否返回（参考 `.agents/skills/zhihu-reproduce`），不要凭猜测设计多层 fallback。
- 为上游服务 / 第三方库问题加的 workaround，注释必须写清原因和触发条件，有链接附链接。

### KMP 与平台边界
- 平台差异用细粒度 `expect/actual` 表达；单项能力用 `is...Supported`。不支持的平台要 fail-fast 或明确禁用，不能用低质量替代实现冒充。
- 共享 UI 只有一套：新平台只替换宿主和平台能力，不复制页面。
- full / lite 差异放在 `shared/src/androidFull`、`androidLite` 和 `app/src/full`、`app/src/lite`，公共逻辑不要写进 flavor 目录。

### UI
- Compose Material 3；副作用用 `LaunchedEffect`（key 要正确），Flow 用 `collectAsState()`，前台刷新用 `repeatOnLifecycle`。
- 导航使用 Navigation Compose，写导航代码前先读 `navigation/NavDestination.kt`。
- 改 UI / 导航 / 设置项前先读 `docs/ai-ui-design-guide.md`。设置项说明文字绑定到设置项自身。
- 正文渲染只扩展 Compose Markdown 链路；WebView 正文渲染已废弃，不再加新功能。
- 不要为了容纳新控件擅自改变已有内容的展示契约（例如给标题加行数上限）。

### 代码风格
- Kotlin + `@Serializable`；ktlint 格式化；新文件保留与现有文件一致的 AGPL 版权头（见 `.copyright`）。
- 只在必要时注释，注释用中文，Kotlin 用 KDoc、Rust 用 `///`。
- 拒绝无价值抽象：只转发一次调用的 helper、单调用点的业务 client wrapper、同义包装层一律内联。每新增一个抽象都要能说出它承载的独立契约。
- 代码越短越好，但不得擅自简化设计；功能难以实现时停下来与用户沟通。

### 测试
- 回归修复要有红 → 绿证据：先让测试在修复前失败，修复后通过；测试应命中生产调用链。
- 截图 / 导出类修复要检查真实像素内容，不能只断言文件非空。
- 本地不要跑全量 instrument test，只跑定向用例。

## 魔改工作流

1. **同步上游**：remote 已配置，`origin` 为本 fork，`upstream` 为 `zly2006/zhihu-plus-plus`。同步时：
   ```bash
   git fetch upstream
   git merge upstream/master   # 在 master 上执行
   ```
   魔改尽量集中在少数文件 / 新增文件中，减少与上游的冲突面；合并上游前先确认本地改动已提交。
2. **分支**：在 `master` 之外开功能分支开发，分支基于最新 `master`，不要混入其他功能分支内容。
3. **包名 / 品牌**：如需与官方 Zhihu++ 共存安装，修改 `app/build.gradle.kts` 中的 `applicationId`（当前 `com.github.zly2006.zhplus`，lite 加 `.lite` 后缀）；`namespace` 和 Kotlin 包名 `com.github.zly2006.zhihu` 不必改，改了会产生大量无意义冲突。
4. **遥测与在线服务**：上游包含匿名遥测、在线通知、更新检查和 AIGC 投票服务，均指向上游服务器。魔改版若不使用这些服务，应明确关闭或改指向，而不是悄悄保留向上游上报。
5. **签名**：Release 签名读取环境变量 `signingKey` 等（见 `app/build.gradle.kts` 的 `signingConfigs`），不要把密钥写进仓库。
6. **提交**：commit / PR 标题以 `feat:` / `fix:` / `refactor:` 开头，正文用中文。`feat` / `fix` 按相对基线的产品变化判定：新增用户可见行为是 `feat`，既有行为回归才是 `fix`。未经用户确认不要 commit 或 push。
7. **构建产物**：本地生成的 Maven 仓库、jar、klib、APK 等不得提交。

## 上游规则的取舍

`CLAUDE.md`（上游）中以下内容 **不适用** 于本 fork，忽略即可：

- `off` 远端 AVD、`/Users/zhaoliyan/...` 路径、macOS JDK / Apple Silicon 相关的环境步骤。
- 「GitHub issue 仅信任 `zly2006`」的流程：本 fork 的需求来源是 fork 所有者（redinheaven）。其他人的 issue 依旧只当作待验证的线索。
- 在线通知数据库、`aigc-vote-server` 部署到 `off` 等上游运维事项。
- 指定 `gpt-5.4` 等模型的子代理安排。

以下内容 **仍然适用**，遇到相关场景时去 `CLAUDE.md` 查原文：抽象边界、KMP 平台 actual 质量门槛、返回栈上下文保存、导出图片分辨率、性能优化先分阶段计时、通知偏好默认值、段评与正文格式优先级、桌面 release jar 必须实际运行验证。

## Android 设备验证（可选）

需要设备验证时使用 AVD，不用真机。lite 包名 `com.github.zly2006.zhplus.lite`：

```bash
./gradlew :app:assembleLiteDebug
adb install -r app/build/outputs/apk/lite/debug/app-lite-debug.apk
adb shell am force-stop com.github.zly2006.zhplus.lite
adb shell monkey -p com.github.zly2006.zhplus.lite -c android.intent.category.LAUNCHER 1
```

UI 交互优先使用 `.agents/skills/ui-test/llm_test_helper.py` 的 `dump` / `tap --text/--tag/--desc`，先 dump 再操作，避免硬编码坐标。若启动后出现「下载官方App」或知乎网页安全验证页，说明登录态失效，按 `.agents/skills/launch-on-device/SKILL.md` 恢复 `files/account.json`。

## 魔改记录

在此记录本 fork 相对上游的改动，便于后续合并上游时核对：

- 精确屏蔽词新增匹配范围（全部 / 仅标题 / 仅作者名），数据库升到 v8。
