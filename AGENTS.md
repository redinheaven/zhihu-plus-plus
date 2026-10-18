# Zhihu++ 魔改分支 Agent 指南

本仓库 fork 自 [zly2006/zhihu-plus-plus](https://github.com/zly2006/zhihu-plus-plus)，当前 fork 地址为 [redinheaven/zhihu-plus-plus](https://github.com/redinheaven/zhihu-plus-plus)。目标是在上游基础上进行魔改。本文件是本 fork 的首要 agent 指南；`CLAUDE.md` 是上游维护者沉淀的经验规则，仍然有参考价值，但其中与上游维护者个人环境绑定的部分不适用（见文末「上游规则的取舍」）。

## 项目概览

Zhihu++ 是注重隐私、无广告的第三方知乎客户端，基于 Kotlin Multiplatform + Compose Multiplatform：

- Android 为主。上游分 `full`（端侧 text2vec 模型）和 `lite` 两个 flavor；**本 fork 已合并为单一的「Jev 版」**，AI 屏蔽和关键词提取改为调用 TypeSafe Jev，见「魔改记录」。
- 实验性桌面端（JVM，Windows MSI / Linux AppImage）、macOS 原生（Kotlin/Native）以及 iOS target。
- 核心卖点：本地推荐算法（爬虫 + 本地打分，独立于知乎推荐）、屏蔽词 / AI 主题屏蔽 / 屏蔽用户、话题与问题、去盐选、离线阅读、zse96 v2 签名调用 Web API、模拟 Android API。
- 本 fork 的 App 只与知乎通信，外加 AI 屏蔽用的 TypeSafe Jev / Cloudflare Workers AI；上游的遥测、在线通知、更新检查、AIGC 投票等连上游服务器的功能已全部删除。
- 许可证：AGPL-3.0（仅 v3）。魔改后分发二进制时必须同时提供源码，保留版权头。

版本号在 `gradle.properties`（`app.versionName` / `app.versionCode`）。Gradle 9.7.1，JDK 17 toolchain（`app` 的部分任务用到 JDK 21 toolchain，foojay 插件可自动下载）。

## 目录结构

| 路径 | 说明 |
| --- | --- |
| `app/` | Android 应用壳。`src/main` 为全部代码（无 flavor），`src/androidTest` 为 instrument test |
| `shared/` | KMP 共享模块，绝大部分业务和 UI 都在这里 |
| `shared/src/commonMain/kotlin/com/github/zly2006/zhihu/` | 跨平台核心代码（见下表） |
| `shared/src/androidMain` | Android `actual` 实现（含 `tiqianMarkdownMain` 渲染器） |
| `shared/src/jvmMain` | 桌面端 `actual` 与桌面入口相关实现 |
| `shared/src/macosMain` / `iosMain` / `nativeMain` | Apple 平台实现 |
| `shared-local-db/` | 本地数据库（KMP） |
| `desktopApp/` | 桌面端入口，`mainClass = com.github.zly2006.zhihu.desktop.MainKt` |
| `macosApp/`, `macosUiDebug/` | macOS 原生宿主与离屏 UI 调试器 |
| `rs-zse-sign/` | zse 签名算法的 Rust 实现（独立 crate） |
| `aigc-vote-server/` | 上游的 AIGC 标记投票后端（Rust），本 fork 的 App 已不再使用，目录保留以减少合并冲突 |
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
| `markdown/`, `editor/` | 正文渲染、写回答编辑器（上游的 `reading/` 朗读已删除） |
| `notification/`, `theme/`, `platform/`, `util/` | 知乎消息通知、主题、平台能力声明、工具（上游的 `updater/` 已删除） |

## 构建与验证

```bash
# Android（单一版本，无 flavor）
./gradlew :app:assembleDebug         # 产物 app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease       # R8 压缩，产物 app/build/outputs/apk/release/app-release-unsigned.apk，签名见「魔改工作流」第 8 条
./gradlew :app:testDebugUnitTest

# 桌面端
./gradlew :desktopApp:run

# 格式化（提交前必须）
./gradlew :app:ktlintFormat :shared:ktlintFormat
```

Windows 上直接跑全量 `./gradlew ktlintFormat` 会在 `shared-local-db` 的 KSP 生成代码上报错（排除规则只认 `/` 分隔的路径），所以只格式化 `app` 和 `shared`；改了 `shared-local-db` 时再加 `:shared-local-db:ktlintCommonMainSourceSetFormat :shared-local-db:ktlintNativeMainSourceSetFormat`。

修改后的固定顺序：**构建通过 → `ktlintFormat` → review → 提交**。

### 本机（Windows）环境注意

当前开发机：Windows 10，Xeon E5-2697 v2（12 核 24 线程）、32 GB 内存、GTX 1660，用户名 `Administrator`。shell 可用 PowerShell 或 Git Bash；Bash 下用 `./gradlew`，PowerShell 下用 `.\gradlew.bat`。

- 已配置（用户级环境变量，新开终端生效）：
  - JDK：Temurin 17，`JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`；
  - Android SDK：`ANDROID_HOME=C:\Users\Administrator\AppData\Local\Android\Sdk`，装有 command-line tools、`platform-tools`、`platforms/android-37.0`、`build-tools/36.0.0` 与 `37.0.0`、`emulator`、系统镜像 `android-30` / `android-35`（google_apis x86_64）；没有 Android Studio 和 NDK；
  - 仓库根目录 `local.properties` 写有 `sdk.dir`、`typesafe.apiKey`、`cloudflare.accountId`、`cloudflare.apiToken`（git 已忽略，不入库；提交和推送前要确认这些值没进 diff）。
- 新版 `sdkmanager` 包名用 `/` 分隔，例如 `sdkmanager "platforms/android-37.0"`，旧的 `;` 写法会报 `Package not found`。在 agent 会话里用管道喂 `y` 接受许可时，sdkmanager 偶尔会卡住不下载；中途杀掉会留下解压一半的镜像（缺 `vendor.img` / `userdata.img`），要删掉目录重装。
- 已安装 GitHub CLI（`C:\Program Files\GitHub CLI\gh.exe`，登录账号 redinheaven，Git Bash 里不在 PATH 时用完整路径），可改仓库简介等；未安装 Rust / `cargo-ndk` / NDK：Android 构建已不需要 Rust，仅改其他 Rust crate 时才需要。
- AI 屏蔽的 Key 优先用用户在「AI 屏蔽主题」页填写的（偏好 `typesafeApiKey` / `cloudflareAccountId` / `cloudflareApiToken`，见 `Jev.kt` 的 `JevKeys`），留空时用构建时注入的默认值：Jev 的 API Key 从 `local.properties` 的 `typesafe.apiKey`（或环境变量 `TYPESAFE_API_KEY`）读取并打进 APK；回退用的 Cloudflare 账号 ID 和 Token 同样来自 `cloudflare.accountId` / `cloudflare.apiToken`（或 `CF_ACCOUNT_ID` / `CF_API_TOKEN`）。
- 仓库 `core.symlinks=false`，上游的符号链接在 Windows 上会被检出为内容是目标路径的普通文本文件，修改这类文件时注意不要误提交类型变更（`AGENTS.md` 曾被误存为符号链接模式，已改回普通文件）。
- 编译耗时：首次 debug 约 20 分钟，之后增量编译 1～3 分钟。项目默认的 Gradle / Kotlin 内存参数在本机够用，不需要本机覆盖；不要修改仓库内 `gradle.properties` 的内存参数（会与上游冲突）。
- Gradle 配置缓存和构建缓存默认开启；改了构建脚本后遇到奇怪问题可加 `--no-configuration-cache` 排查。
- 新开 git worktree 时把 `local.properties` 一并复制过去。
- 另一台旧开发机（用户名 `wugao`，7.9 GB 内存 / 4 线程）在 `C:\Users\wugao\.gradle\gradle.properties` 里把 Gradle 压到 2 GB、Kotlin 1.5 GB、关闭并行；在那台机器上不要删除这份覆盖。

## 关键约定

### 数据与网络
- 知乎 API 返回 `snake_case`，项目内 data class 一律 `camelCase`；`AccountData.fetch*()` / `decodeJson()` 内部自动 `snake_case2camelCase()`，不要手动转换。
- 业务请求使用当前账户 store 持有的 HttpClient（Ktor）。Web API 需 `signFetchRequest(context)` 做 zse96 v2 签名；Android API 使用 `AccountData.ANDROID_HEADERS` / `ANDROID_USER_AGENT`。
- 依赖 API 字段的新功能，先用真实请求核对字段是否返回（参考 `.agents/skills/zhihu-reproduce`），不要凭猜测设计多层 fallback。
- 为上游服务 / 第三方库问题加的 workaround，注释必须写清原因和触发条件，有链接附链接。

### KMP 与平台边界
- 平台差异用细粒度 `expect/actual` 表达；单项能力用 `is...Supported`。不支持的平台要 fail-fast 或明确禁用，不能用低质量替代实现冒充。
- 共享 UI 只有一套：新平台只替换宿主和平台能力，不复制页面。
- 本 fork 不再有 full / lite flavor，不要重新引入 flavor 目录。

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
3. **包名 / 品牌**：如需与官方 Zhihu++ 共存安装，修改 `app/build.gradle.kts` 中的 `applicationId`（本 fork 已改为 `com.github.zly2006.zhplus.jev`，应用名「知乎++ Jev」）；`namespace` 和 Kotlin 包名 `com.github.zly2006.zhihu` 不必改，改了会产生大量无意义冲突。
4. **遥测与在线服务**：上游的匿名遥测、首页在线通知、更新检查、AIGC 投票和崩溃上报（均指向上游服务器）已在本 fork 整体删除；合并上游时不要把这些代码带回来。App 现在只连知乎，外加 Jev / Cloudflare（AI 屏蔽）。公式字体 Latin Modern Math（GUST 字体许可，可再分发）打包在 `shared/src/commonMain/composeResources/font/`，原各平台下载器已删除；首页「知乎++新动态 / 投票」公告卡（拉上游作者的知乎想法）也已删除。
5. **签名**：Release 签名读取环境变量 `signingKey` 等（见 `app/build.gradle.kts` 的 `signingConfigs`），不要把密钥写进仓库。
6. **提交**：commit / PR 标题以 `feat:` / `fix:` / `refactor:` 开头，正文用中文。`feat` / `fix` 按相对基线的产品变化判定：新增用户可见行为是 `feat`，既有行为回归才是 `fix`。未经用户确认不要 commit 或 push。
7. **构建产物**：本地生成的 Maven 仓库、jar、klib、APK 等不得提交。
8. **自用安装包**：给用户手机用的 APK 复制到仓库根目录 `=release/`，命名 `zhihu++jev-v<版本>.apk`（`*.apk` 已被 git 忽略）。从 v1.3 起是 release 构建（R8 压缩，约 6 MB，比 debug 包流畅）：`./gradlew :app:assembleRelease` 得到未签名包，再用 `build-tools/36.0.0` 的 `zipalign -p 4` 和 `apksigner sign --ks C:\Users\Administrator\.android\debug.keystore --ks-key-alias androiddebugkey`（密码 `android`）签名；`apksigner.bat` 会把路径里的 `=` 当分隔符，先输出到别处再复制进 `=release/`。release 包不可调试，`run-as` 读不了数据库，要查数据时临时装 debug 包。v1.0、v1.2 是 debug 构建，同样用这个 keystore 签名，版本号显示为「上游 `app.versionName` + 本 fork 后缀」，后缀在 `app/build.gradle.kts` 的 `versionNameSuffix`（如 `-jev2.6`，显示 `0.31-jev2.6`），发新版时改这里；`gradle.properties` 不动，避免合并冲突。这个 keystore 要妥善保留：换签名后不能覆盖安装。APK 内含构建时注入的 Jev / Cloudflare Key，不要分发；别人自行构建时可不配 Key，在 App 内「AI 屏蔽主题」页填写自己的。

   | 版本 | 日期 | 内容 |
   | --- | --- | --- |
   | v1.0 | 2026-10-16 | Jev 版 + 离线阅读、刷新两页、已读标记、屏蔽入口改版（不含云端备份） |
   | v1.2 | 2026-10-16 | v1.0 + 系统云端备份 + 设置页「数据同步」（完整数据合并写入用户选的文件、立刻同步、从文件恢复） |
   | v1.3 | 2026-10-17 | 改为 release 构建；性能（已读标记一次读入内存、卡片标题摘要解析缓存）；推荐页广告卡过滤、离线预下载作者头像、首次读到回答底部提示上拉切换、同步失败在设置入口标红、下拉刷新第一页到了就收起刷新圈；质量屏蔽默认隐藏；界面精简（去段评划线、去创作按钮、话题菜单折叠等，见魔改记录）；精简：删除系统云端备份（改为数据同步每日自动同步）、列表自带正文保存、打开回答时的上次阅读提示；Jev 关键词建议改为点击触发；遥测默认关闭 |
   | v1.4 | 2026-10-17 | 版本号显示 `0.31-jev1.4`；文章页顶栏随正文同步折叠、去掉松手吸附，折叠时不再每帧重排正文；首页 Web 端推荐每页按发布时间从新到旧（可关）；去掉上游交流群入口，许可证 / issue / 导出页脚链接改指向本 fork |
   | v1.5 | 2026-10-17 | 卡片菜单「屏蔽问题」（按问题 ID，屏蔽列表新增标签页，数据库 v10）；作者备注（作者页「备注」按钮，名字右侧显示）；回答页去掉「XX 人赞同了该回答」、开头的「谢邀 @XX」段落和「收录」徽章；断网时不再反复弹出网络错误，网络恢复后首页自动刷新 |
   | v1.6 | 2026-10-17 | Jev 3 秒无响应或失败时回退到 Cloudflare Workers AI 的 clef-flash；点卡片作者名进入作者主页 |
   | v1.7 | 2026-10-17 | 删除所有连上游服务器的功能（遥测、在线通知、更新检查、AIGC 标记、崩溃上报、首页「知乎++新动态」）和防沉迷；公式字体打包进 APK，App 只连知乎和 Jev / Cloudflare；护眼配色（米白背景、降低文字反差）；卡片菜单去掉「屏蔽提问者」；屏蔽用户后只移除该作者卡片不整页刷新 |
   | v1.8 | 2026-10-17 | 新图标（青墨绿玻璃高光底 + 米白「知」）；回答页不显示任何徽章；评论字号缩到与首页摘要一致（14sp）；屏蔽带「认证信息」的作者（关注的人除外）；回答卡片显示收藏数 |
   | v1.9 | 2026-10-18 | AI 屏蔽 Key 可在 App 内填写（没填时明确提示）；联网时域名解析偶发失败不再误报「网络不可用」（静默重试两次，提示带出失败的域名），首页 api.zhihu.com 解析失败时改用 www.zhihu.com；评论超过 500 字折叠；评论左滑改为屏蔽评论者（需确认）；README 改为大白话 |
   | v2.0 | 2026-10-18 | 性能（首页加载过滤移到后台线程、未打开内容不预下载图片、Jev 结果缓存、正则预编译、批量查库写库等）；流畅度（打开详情先用缓存、可见卡片延后移除、下拉刷新不清空、固定缩略图尺寸、提前加载下一页、250ms 视差转场）；阅读时预取下一轮刷新，冷启动 / 进程被杀后原样恢复，手动刷新前不自动刷新 |
   | v2.1 | 2026-10-18 | 正文去掉右侧进度条；评论长按复制、双击收藏到 `saved_comments.json`；回答卡片显示字数；卡片菜单去掉「外观设置」；卡片滚进屏幕才记「已展示」并上报 touch |
   | v2.2 | 2026-10-18 | 字数靠右；删除朗读与播放；收藏夹一行两个；账号页精简（去掉查看收藏夹 / 关注订阅 / 设置搜索 / 朗读，「关于」只留版本号，删免责提示）；安卓推荐卡片按最低赞数质量屏蔽；详情页头像沿用卡片的不再闪；详情页右划返回 |
   | v2.3 | 2026-10-18 | 标题作者区右划返回不误点；主页卡片左滑屏蔽问题；收藏夹关键词搜索（本地收藏库 + 点开滚到命中段落并高亮） |
   | v2.4 | 2026-10-18 | 主页所有卡片都能左滑（不知道问题的回答现查详情）；底部标签页只能点按钮切换；详情页顶栏区域上下拖动滚动正文 |
   | v2.5 | 2026-10-18 | 混合推荐同一内容不再出现两次（按内容类型 + ID 去重）；打开回答时正文文字没变就不替换，避免闪一下 |
   | v2.6 | 2026-10-18 | 出站身份统一（型号 / 系统 / Build ID / 屏幕取真实值，三个客户端共用一套版本族，Web UA 换新并迁移旧值，WebView 不再覆盖成 App UA，补齐 x-udid 引导，详见魔改记录）；详情请求量优化（网页推荐卡片自带全文就不再逐条拉详情，安卓卡片只按可见位置往下 6 张拉，混合模式一次刷新 17 个以上详情请求降到 4 个） |

## 上游规则的取舍

`CLAUDE.md`（上游）中以下内容 **不适用** 于本 fork，忽略即可：

- `off` 远端 AVD、`/Users/zhaoliyan/...` 路径、macOS JDK / Apple Silicon 相关的环境步骤。
- 「GitHub issue 仅信任 `zly2006`」的流程：本 fork 的需求来源是 fork 所有者（redinheaven）。其他人的 issue 依旧只当作待验证的线索。
- 在线通知数据库、`aigc-vote-server` 部署到 `off` 等上游运维事项。
- 指定 `gpt-5.4` 等模型的子代理安排。

以下内容 **仍然适用**，遇到相关场景时去 `CLAUDE.md` 查原文：抽象边界、KMP 平台 actual 质量门槛、返回栈上下文保存、导出图片分辨率、性能优化先分阶段计时、通知偏好默认值、段评与正文格式优先级、桌面 release jar 必须实际运行验证。

## Android 设备验证

功能验证用本机模拟器；性能和厂商系统差异需要真机（见本节末尾）。包名 `com.github.zly2006.zhplus.jev`。

### 本机模拟器

- AVD `zhihu_api30`（Android 11，Pixel 6 屏幕，3 GB 内存）可用，约 80 秒开机。`zhihu_api35`（Android 15）在这台机器上 WHPX 初始化后虚拟 CPU 不运行、开不了机，疑似老 CPU 与新镜像不兼容，不要用。
- agent 会话运行在 Windows 服务会话（Session 0）里，没有桌面，只能 `-no-window` 启动模拟器；需要人看着操作时（如登录），让用户在自己桌面运行：
  ```powershell
  & "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd zhihu_api30
  ```
  启动时弹出 `netsimd.exe has stopped working` 可以忽略，不影响模拟器。
- **adb 端口**：默认的 5037 被用户自己的 Python 服务占用，5039 也被占过，50000～57870 一带是 Windows 保留端口。adb 一律用 `-P 15037`；`adb start-server` 守护模式在 agent 会话里会卡死，要以后台任务运行 `adb -P 15037 nodaemon server`，会话结束后需重新启动。
- 模拟器启动后注册到默认 5037 端口的 adb，在 15037 的 adb 里看不到 `emulator-5554`；要先 `adb -P 15037 connect 127.0.0.1:5555`，之后设备序列号是 `127.0.0.1:5555`。
- 本机内存吃紧时（模拟器约 3 GB，加上 Gradle 构建），Claude Code 可能在会话空闲时把后台的模拟器和 adb server 杀掉；再用时重新启动即可。
- 用 SDK 自带的 `platform-tools\adb.exe`；PATH 里另有一个旧版 r34 adb（`D:\=Playground\...`），不要混用。
- Git Bash 里调用 adb 前先 `export MSYS_NO_PATHCONV=1`，否则 `/sdcard/...` 会被改写成 Windows 路径。

```bash
export MSYS_NO_PATHCONV=1
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -P 15037 connect 127.0.0.1:5555
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe -P 15037 -s 127.0.0.1:5555"
./gradlew :app:assembleDebug
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell am force-stop com.github.zly2006.zhplus.jev
$ADB shell monkey -p com.github.zly2006.zhplus.jev -c android.intent.category.LAUNCHER 1
$ADB exec-out screencap -p > shot.png
$ADB shell svc wifi disable; $ADB shell svc data disable   # 模拟断网，测完记得 enable
```

- 首次启动有两页声明，「确认」按钮中心在 y≈2243（1080x2400），底边与导航栏重叠，点边缘会点不中；尽量先 `uiautomator dump` 再按 bounds 点击。
- 模拟器默认上网走 Wi-Fi（`wlan0`，不计流量）；关掉 Wi-Fi 后走模拟移动数据（计流量）。
- 数据库在 `databases/content_filter_database`（含 `-wal`），可用 `run-as com.github.zly2006.zhplus.jev cat` 拉回电脑用 sqlite 查看（只对 debug 包有效，release 包不可调试）。
- **登录**：知乎会识别模拟器，在模拟器里登录常提示「网络环境存在异常」。`zhihu_api30` 里已有登录态，能用就别清数据；失效时按 `.agents/skills/launch-on-device/SKILL.md` 恢复 `files/account.json`，或用浏览器的 Cookie 构造登录文件导入。
- `.agents/skills/` 下的上游技能（`ui-test`、`launch-on-device` 等）仍写着旧的 lite 包名 `com.github.zly2006.zhplus.lite` 和 `assembleLiteDebug`，使用时替换成上面的包名和命令；`llm_test_helper.py` 直接调用 PATH 里的 `adb`，用前要调整 PATH 和 adb 端口。

### 只能真机验证的

- 流畅度、卡顿、耗电、发热（模拟器跑在服务器 CPU 上、软件渲染，没有参考价值）。
- 国产系统差异：杀后台、通知限制、Toast 样式等。
- 后台长时间运行：本地推荐爬虫、App 被杀后的恢复、Doze 省电。
- Android 12 及以上的新行为：通知权限弹窗、预测性返回手势、Android 15 强制全屏等（本机只有 Android 11 模拟器）。
- 摄像头扫码、震动反馈、分享到其他 App。

## 魔改记录

在此记录本 fork 相对上游的改动，便于后续合并上游时核对：

- 精确屏蔽词新增匹配范围（全部 / 仅标题 / 仅作者名），数据库升到 v8。
- **Jev 版**：删除 full / lite flavor、`sentence_embeddings`、`rs-hf-tokenizer`、HanLP 与 text2vec 模型下载；`app/src/main/.../nlp/Jev.kt` 用 TypeSafe Jev 的 noul 判断题实现 AI 主题屏蔽和「按关键词屏蔽」的关键词挑选（候选词由系统 ICU 分词生成）。原 full 专属的提椠 Markdown 渲染器和日报日期选择器保留。
- applicationId 改为 `com.github.zly2006.zhplus.jev`，应用名「知乎++ Jev」，可与官方版共存。
- 删除更新检查：`updater/` 包、`UpdateManager`、各平台 `SystemUpdate*` 能力、首页「发现新版本」卡片和 `REQUEST_INSTALL_PACKAGES` 权限。
- 离线阅读：`fetchZhihuContentDetail` 把联网时下载的回答 / 文章 / 问题 / 想法详情原始 JSON 存进 `offline_contents` 表（数据库 v9，保留 7 天、最多 2000 条），请求失败时回退到本地版本；首页加载失败且为空时列出离线保存的回答和文章（按当前屏蔽设置过滤）；保存正文时在不按流量计费的网络下用 Coil 预下载其中图片，磁盘缓存调到 300 MB；首页（网页 / 安卓 / 混合模式）每次刷新连续加载 2 页（`pagesPerRefresh`）。正文图片只在用户真正打开内容时预下载（`prefetchImages`），首页过滤和收藏夹建索引拉的详情只存 JSON 和作者头像；超期清理每个进程只跑一次。
- 已读标记：`FeedCard` 用 `content_open_events`（本地打开 + 同步的知乎浏览历史）在详情行前显示「3 天前读过」。
- 屏蔽入口：账号页新增「屏蔽列表」直达入口；首页卡片「按关键词屏蔽」弹窗支持手动输入，可选「普通屏蔽词」（带匹配范围，每个词一条规则）或「AI 主题（Jev）」，点「Jev 建议」才请求 Jev 提取关键词，结果只作为可点选的建议（默认不选），弹窗底部可跳到屏蔽列表；屏蔽列表的 NLP 标签页改名「AI 屏蔽主题」。
- 数据同步：`shared/src/androidMain/.../ui/subscreens/AndroidSubscreenRuntimes.kt` 的 `CloudFileSyncSettings`（设置 → 系统与更新）。用 SAF 选一个文件，写入两个 SharedPreferences、`account.json`、`history.json` 和两个 Room 数据库的全部表（排除离线正文和本地推荐的爬虫缓存三表）；同步 = 本机与文件合并后写回文件（冲突以本机为准），恢复 = 文件合并进本机（冲突以文件为准）后重启应用。`MainActivity` 启动时距上次成功同步超过一天会自动同步（`syncCloudFileIfDue`）。系统自动备份配置保持上游原样，曾经的 `ConfigBackupAgent` 已删除。
- 版本号加后缀 `-jev<版本>`（当前 `-jev2.6`）；去掉设置页「交流 & 闲聊」（上游 Discord / Telegram / issue）；实验性 UI 页脚的 issue 链接、文章导出页脚的 GitHub 地址改指向本 fork（导出署名改为「知乎++ Jev」）。
- 文章页顶栏：`TwoRowsTopAppBar.kt` 的 `rememberContentLinkedScrollBehavior` 让折叠量等于正文滚动距离（不先消费滚动、不吸附），`ArticleScreen` 正文顶部边距固定为展开高度，顶栏覆盖在正文上折叠；原 `PreferCollapsedExitUntilCollapsedScrollBehavior` 与吸附代码已删除。很短的回答可能收不完顶栏。
- 首页排序：设置「推荐系统与内容过滤 → 按发布时间排序」（`homeFeedSortByPublishTime`，默认开），`HomeFeedViewModel.processResponse` 把每页按 `target.created_time` 从新到旧重排（外层 `created_time` 是请求时刻，不能用）。安卓端推荐卡片实测不带任何时间字段，所以安卓端推荐不受影响、混合推荐只排网页端那部分。
- 删除遥测（`telemetry()`、登录前第三页遥测声明、设置项）、首页在线通知（`OnlineHomeNotification`）、AIGC 标记（`AigcVoteModels`、`AigcVoteEnvironment`、文章页标记面板与菜单项、设置项）、未接入的 `CrashReporter`、防沉迷提醒（`ContinuousUsageReminderManager` 与设置项；原 `ZhihuPolicy.kt` 只剩 `expect object Log`，改名 `Log.kt`）。
- 界面精简：回答 / 文章不注入段评划线（`ArticleViewModel` 直接用原文，上游段评代码保留未调用）；回答页底部操作栏加 surface 底色；首页去掉创作「+」按钮（`SHOW_HOME_CREATE_FAB`）；Android 推荐卡片去掉「手机版推荐」来源标签；卡片菜单的话题改为可展开的「屏蔽话题（N）」（`BlockTopicMenuItems`，首页和关注页共用）；默认值改为：刷新浮动按钮关、「下一个回答」按钮关、回答页底栏自动隐藏开。
- 质量屏蔽：默认模式由「屏蔽规则」（留「已屏蔽」占位卡）改为「隐藏」；「隐藏」原先只在首页 `HomeFeedViewModel` 生效，现在 `BaseFeedViewModel.processResponse` 也会去掉占位，关注、热榜、搜索、问题回答列表等都不再留占位（`QualityFilterModeTest` 覆盖）。
- 性能：卡片的「读过」标签不再每张卡各查一次数据库，`ContentOpenEventSupport.lastOpenedAt` 首次用到时整表读入（`getLastOpenedTimes`），之后随打开记录和远端历史同步增量更新；`parseEmphasizedHtmlTextWithTheme` 用 `remember` 缓存解析结果。
- 交互：安卓推荐接口（`parseMobileHomeFeedDisplayItem`）丢掉作者位置以「广告」开头的卡片；离线保存正文时把作者头像一并预下载；第一次读到回答底部（有下一个回答时）提示一次「继续上拉切换」（偏好 `answerSwitchHintShown`）；数据同步上次失败时，账号页「系统与更新」副标题显示红字「数据同步失败」（`CLOUD_SYNC_LAST_ERROR_KEY` 移到 common）；下拉刷新时第一页到了就收起刷新圈，第二页在列表底部继续加载。
- 屏蔽问题：`blocked_questions` 表（数据库 v10，`BlockedQuestion` / `BlockedQuestionDao`），`FeedContentFilterPipeline` 按 `FilterableContent.questionId` 过滤（回答取所在问题，问题卡片取自身）；首页 / 关注页卡片菜单「屏蔽问题」（`BaseFeedViewModel.handleBlockQuestion`、`FeedDisplayItem.blockableQuestion`），屏蔽列表新增「屏蔽问题」标签页（标签栏改为可滑动）。屏蔽规则 JSON 导入导出未包含屏蔽问题（数据同步会带上）。
- 作者备注：`ui/components/UserNotes.kt`，按用户 ID 存在偏好 `userNotes`（JSON），最多 9 字；作者页按钮行末尾「备注」，首页卡片和作者页名字右侧显示，安卓推荐卡片在详情加载前按作者名回退匹配。
- 回答页精简：不显示「XX 人赞同了该回答」（同时不再请求赞同关系和赞同者列表，上游代码保留未调用）；正文第一段以「谢邀 / 谢谢邀请 / 感谢邀请」开头且不超过 40 字时删掉（回答徽章后来改为全部不显示，见下文「回答徽章」）。
- 断网处理：`PaginationViewModel.networkUnavailable`，域名解析失败（`isHostResolutionFailure`）且最终放弃后只提示一次，不再自动加载下一页（原来列表底部每 50ms 重试、不停弹错误）；`MainActivity` 监听系统网络，恢复且验证可上网时发 `networkReconnected`，离线状态的首页自动刷新。联网时的偶发解析失败见下文「网络误报」。
- Jev 回退：`Jev.kt` 的 `askJev` 给 Jev 请求 3 秒超时（正常 0.3～0.6 秒），超时或失败时用同样的 noul 请求体（model 改为 `clef-flash`）调用 Cloudflare 官方 API `api.cloudflare.com/client/v4/accounts/<id>/ai/run/@cf/cloudflare/clef-flash`（workers.dev 在国内被墙，响应包在 `result` 里）。Key 的来源见下文「AI 屏蔽 Key 可在 App 内填写」。
- 卡片作者名：`FeedCard` 作者行原来挂着空的 `clickable {}` 吞掉点击，改为跳转 `Person`；安卓推荐卡片在详情加载前没有作者 ID，此时点作者名等同点卡片。
- 护眼配色：默认背景浅色 `#F6F4EF`、深色 `#1A1A1C`（`ThemeManager.DEFAULT_BACKGROUND_*`，用户选过的保留）；`ZhihuTheme` 把正文文字向背景靠 12%、次要文字 6%、各级容器色 50%。
- 卡片菜单去掉「屏蔽提问者」（屏蔽列表和作者页「屏蔽其提问」仍在）；屏蔽用户确认后调用 `BaseFeedViewModel.removeItemsByAuthor` 只移除该作者卡片，不再整页刷新。
- 图标：`app/src/main/res/drawable/ic_launcher_background.xml`（青墨绿渐变 + 顶部高光 + 底部反射光）与 `ic_launcher_foreground.xml` / `ic_launcher_monochrome.xml`（思源黑体 Bold「知」轮廓，外框约 40dp），应用内用的 `composeResources/drawable/ic_launcher_foreground.xml` 只画中间 72dp。
- 回答徽章：`DataHolder.Answer.endorsementItems` 直接返回空列表，回答页、切换回答预加载和预览都不显示徽章（解析代码保留）。
- 评论正文与作者名由 16sp 改为 14sp（`CommentScreen.kt`），与首页卡片摘要一致。
- 屏蔽认证用户：`hasIdentityCertification()`（`OfficialBadge.kt`，徽章标题「认证」/「已认证的个人」，即作者页「认证信息」）；首页 `FeedContentFilterPipeline`（原因「屏蔽认证用户」，受屏蔽用户总开关控制）、问题回答列表、搜索结果过滤，关注的人除外。
- 回答收藏数：`Feed.AnswerTarget.favoriteCount` 改为可空并兼容 `favlists_count`；首页网页推荐叫 `favorite_count`，关注推荐和问题回答列表叫 `favlists_count`，关注动态不返回（不显示）。文章卡片未核实，暂不显示。
- AI 屏蔽 Key 可在 App 内填写：「AI 屏蔽主题」页的 API Key 卡片（`NLPKeywordManagementScreen.kt` 的 `JevApiKeyCard`），只存本机偏好，留空用构建时内置的；没 Key 时 AI 屏蔽静默不生效，「Jev 建议」提示去哪填（`KeywordAnalyzerCore.extractFromFeedWithWeight` 不再吞异常）。
- 网络误报：`networkAvailable`（`ContentDetailCache.kt`，由 `MainActivity` 的网络回调维护）区分真断网与联网时解析失败；`PaginationViewModel.errorHandle` 在联网时先静默重试两次（1s、2s），再提示「连接 <域名> 失败」，真断网才提示「网络不可用」。子类可覆盖 `switchHostAfterResolutionFailure`：`HomeFeedViewModel` 在 api.zhihu.com 解析失败后改用 `www.zhihu.com/api/v3/feed/topstory/recommend`（实测结构一致，只少卡片缩略图）。
- 评论：超过 500 字折叠，「展开全文 / 收起」；左滑由回复改为屏蔽评论者（确认后写入屏蔽用户，并从当前评论区移除）。
- 性能（v2.0）：首页 `fetchFeeds` 的请求解码和 `processResponse` 的排序过滤放到 `Dispatchers.Default`，只有改列表时回主线程；安卓推荐的后台过滤单独起协程，不挡下一页。`ContentFilterExtensions` 屏蔽词每批读一次库、正则预编译（`BlockedKeyword.matcher`）、没有 AI 主题时跳过 NLP、只解析 HTML 前 8000 字给 Jev；话题批量查库；屏蔽记录 `BlockedFeedRecordDao.insertAll` 批量写。`Jev.kt` 的 `JevKeywordSemanticMatcher` 按正文 LRU 缓存每个主题的得分（500 条）。`ContentDetailCache` 内存缓存 2 小时、200 条。卡片「读过」只订阅本卡那一项；`debugData` 只留最近 100 条（`DEBUG_DATA_MAX`）。
- 流畅度（v2.0）：`ArticleViewModel.loadArticle` 先用 `cachedContentDetail`（内存或离线正文）显示，联网后再更新；联网拿到的正文文字没变时不替换（`updateContent`，避免整篇重排闪一下）。后台过滤命中时还在屏幕上的卡片记进 `FeedListSync.pendingRemovalKeys`，滑出屏幕再移除；下拉刷新不清空列表（`replaceOnNextAdd`，新的第一页到了再整体替换）；卡片缩略图固定尺寸；离底 8 张就加载下一页；页面转场 250ms 带视差（`PAGE_TRANSITION_MS`）。
- 刷新预取（v2.0）：`HomeScreen.kt` 的 `HomeScreenState`。回到列表时新建同类 ViewModel 在后台照常加载过滤（`prefetch`），手动刷新时直接换上（`swapInPrefetched`），没预取好才正常刷新；不设有效期、不限 Wi-Fi。预取整批存 `home_feed_prefetch_<模式>.json`：全新启动直接显示这批（用后删除）；切出时当前列表存 `home_feed_current_<模式>.json`，进程在后台被杀、切回时原样恢复，预取的放回内存。每种模式本次运行只自动加载一次，之后屏蔽、切出等都不触发刷新（唯一例外：断网后恢复时的自动刷新）。加屏蔽词后只按新规则重过滤当前列表（`refilter`），不刷新。
- 已展示按可见记录（v2.1）：前台过滤（`ForegroundReadFilterPipeline.filter`）只过滤不记录；卡片滚进屏幕才由 `HomeScreenState.markSeen` 调 `recordHomeFeedViews` 记为已展示，网页 / 混合推荐的 `lastread/touch` 也只报划到过的（攒 3 秒一起报）。没划到就被刷新掉的卡片以后还能被推回来。
- 请求量（v2.6）：网页推荐的回答卡片自带全文、作者和所属问题（实测 12 条与详情接口逐字一致，最长 3517 字），`FeedDisplayFilterPipeline` 不再为它们拉详情，只在开了话题屏蔽且有屏蔽话题时拉（卡片里的 `bound_topic_ids` 和话题屏蔽用的 ID 是两套编号）。安卓推荐卡片不带全文，由 `BaseFeedViewModel.ensureLazyDetailFilter` 只拉列表里可见位置往下 6 张以内的（`FeedListSync.maxVisibleIndex`，`DETAIL_LOOKAHEAD`），没显示的预取列表只拉开头几张；`refilter` 只重过滤已过滤过的卡片。卡片菜单的「屏蔽话题」在打开菜单时现查详情。实测混合模式一次刷新（17 张）的详情请求由 17 个以上降到 4 个。
- 首页去重（v2.5）：`BaseFeedViewModel.addDisplayItems` 除 `stableKey` 外再按内容类型 + ID 去重。安卓推荐卡片的 `stableKey` 是整段导航 JSON（含标题、作者、头像地址），和网页推荐的「answer:123」对不上，混合模式下同一条会出现两次。
- 卡片：字数（`FeedDisplayItem.wordCount`，后台过滤时按全文统计不含空白的字符数，网页卡片用自带全文），显示在作者行最右；卡片菜单去掉「外观设置」；主页卡片左滑屏蔽所在问题（复用评论区的 `SwipeToReplyContainer`，所有卡片都接住横滑；不知道问题的回答现查详情，文章、想法提示不属于任何问题）。安卓推荐卡片也按最低赞数质量屏蔽（`parseMobileHomeFeedDisplayItem` 的 `qualityFilter`，卡片不带粉丝数和是否关注，只按赞数）。
- 底部标签页（主页 / 关注 / 日报 / 历史 / 账号）只能点按钮切换，`ZhihuMain` 的 `HorizontalPager` 关掉 `userScrollEnabled`。
- 详情页：去掉右侧阅读进度条（上游 `VerticalReadingProgressBar` 保留未调用）；作者头像先用卡片带过来的（`Article.avatarSrc`），不换详情里尺寸不同的地址；手指从左往右划返回上一页（`ArticleScreen` 的 `swipeBack`：正文用 Main 阶段，让横向滚动的代码块、表格先消费；顶栏用 Initial 阶段抢先识别，避免划完被当成点击标题 / 作者），左右切换回答模式下不启用；顶栏盖在正文上，上下拖动顶栏转给正文滚动（含惯性）。
- 评论：长按复制整条（去掉了正文文字选择）；双击把评论文字和所在问题（文章、想法）标题追加到 `saved_comments.json`（`rememberUserFileDirectory()`，Android 为 `Android/data/<包名>/files/`，去重，读不出来时报错不覆盖）。评论排序（热门 / 最新）原来存在朗读偏好里，改为单独的偏好 `commentSortOrder`。
- 删除朗读与播放：`reading/` 包（各平台播放器、`ContentReadingService`）、播放条与队列、「朗读与播放」设置页、文章菜单和想法页的朗读入口、开发者选项的语音引擎信息、`Article` / `Pin` / `Question` 路由的 `readingQueueSourceId`、`FOREGROUND_SERVICE*` 与 `WAKE_LOCK` 权限、`androidx.media` 依赖及相关测试。`AnswerNavigator.remainingAnswersSnapshot` 原来只给朗读队列用，现在只剩测试调用，保留未删。
- 账号页精简：去掉「查看收藏夹」「查看关注订阅」「搜索设置项」「朗读与播放」；「关于」只留版本号（GitHub 地址、项目协议、开源许可入口和免责提示删除，设置搜索页和开源许可页本身还在）。
- 收藏夹：「我的收藏夹」一行两个；顶部关键词搜索（`viewmodel/CollectionSearchViewModel.kt`）。第一次搜索时列出所有收藏夹的回答和文章，本地没有正文的逐条拉详情（联网的间隔 1.5 秒），抽成按段落的纯文本存进 `collection_search_index.json`（App 私有目录，永久保留，之后只补新收藏、去掉取消收藏的）；结果列出命中的句子，点开时 `Article.searchKeyword` / `searchSentence` 让正文给关键词包上 `<mark>`（`markCollectionSearchKeyword`）并滚到那段。滚动靠内置渲染库新加的 `LocalMarkdownHighlightRequester`（`third_party/markdown/markdown-renderer` 的 `RendererContext.kt`、`BlockRenderer.kt`：含高亮的段落和标题挂上 `bringIntoViewRequester`）。
- 出站身份统一（`account/ZhihuAndroidAppIdentity.kt`）：feed / 身份切换 / 手机号登录三个客户端曾各写一套模拟器型号（`sdk_gphone64_arm64`、`Android SDK built for arm64`）和三个 App 版本（10.61.0 / 11.2.0 / 11.4.0），现在共用一套版本族（11.4.0 / 40408 / `x-api-version` 3.0.93 / `x-zse-93` 101_1_1.0 / flavor zhihuwap64），UA 与 `x-app-za` 由 `ZhihuPhoneLoginDeviceInfo` 的设备字段拼接（Android 取 `Build.*` + 真实屏幕尺寸；桌面 / Apple 平台虚构一套自洽的 Pixel 6）；`x-app-za` 省略 Installer（原来是写死的「灰度 / Market / 荣耀商店」）。Web 默认 UA（`DEFAULT_ZHIHU_USER_AGENT`）由 Ubuntu Chrome 9 换成 `ZHIHU_DESKTOP_USER_AGENT`（Chrome 145），读取账号时自动迁移旧值（`LEGACY_ZHIHU_USER_AGENT`），Web 客户端补 `sec-ch-ua` 客户端提示头。WebView 不再覆盖成 App UA（`WebviewActivity` 两处、登录页 `configureWebLogin` 一处；风控页桌面 UA 保留）。udid：手机登录返回的 udid 存进 session（`mobileUdid`），网页 / Cookie 登录的账号由 `MainActivity` 启动时走游客初始化（`ZhihuAccountStore.ensureMobileUdid`，复用 `ZhihuPhoneLoginClient.fetchDeviceUdid`）补齐，安卓各接口带 `x-udid`。
- 上游 CI（`.github/workflows`）仍按 full / lite 构建并发布到上游 release，本 fork 未改，不要直接启用。

## 已知问题与待办

- 首页广告卡片：安卓推荐模式按作者位置的「广告」字样过滤（依据界面上看到的现象，没抓到原始 JSON 核对）；网页推荐模式如再出现漏网需另查。
- 离线头像：现在会随正文预下载作者头像，但列表卡片用的头像 URL 可能和详情里的尺寸不同，未在断网下实测。
- 「数据同步」（`AndroidSubscreenRuntimes.kt` 的 `CloudFileSyncSettings`）只编译通过，未在设备上实测写入、合并与恢复。
- 网络误报（v1.9）：联网时偶发解析失败的静默重试、首页 api → www 切换只能在真实网络里出现，模拟器上无法复现，待手机实测；若仍提示，看提示里的域名再决定下一步。安卓推荐 / 混合推荐用的是安卓客户端接口，没有 www 等价接口，只重试不切换。
- 未实测：评论超过 500 字折叠（模拟器上没遇到长评论）、屏蔽认证用户、文章卡片的收藏数（未核实接口是否返回，暂不显示）。
- macOS / iOS 代码在 Windows 上无法编译，v1.7 起的删改（更新检查、字体下载器、AIGC 等）只保证 Android 与桌面 JVM 编译通过。
- README 截图（`fastlane/metadata/android/zh-CN/images/phoneScreenshots/`）为 v1.9 在模拟器上的实拍（关注页的关注者头像一栏已打码），图标 `fastlane/.../images/icon.png` 为新图标。v2.x 改了账号页、卡片（字数）等，截图已过时，需要重拍。
- 已实测通过（`zhihu_api30` 模拟器）：离线阅读、图片预下载、刷新两页、已读标记、屏蔽弹窗手动输入、新图标（桌面与「关于」页）、断网显示离线内容与恢复后自动刷新、回答卡片收藏数、评论左滑屏蔽评论者、AI Key 卡片显示；v2.0 的打开详情命中缓存、可见卡片延后移除、下拉刷新替换、预取换上（约 8ms）与换上后继续翻页、预取时不记已展示、进程被杀后恢复、冷启动显示预取批次、切出切回不刷新；v2.5 之后的请求量（详情请求数、预取与换上、屏蔽词过滤网页和安卓卡片、有屏蔽话题时恢复拉详情、菜单话题现查、字数显示）。
- 只编译通过、未在设备上实测：评论长按复制 / 双击收藏、收藏夹搜索整条流程（建索引、搜索、打开后滚动高亮）、详情页右划返回和顶栏拖动、主页卡片左滑屏蔽问题、安卓推荐的赞数质量屏蔽、正文闪烁修复（原因是推断的：缓存版和联网版正文有细微差异导致整篇重排）、混合推荐去重、删除朗读后的各页面。
- 出站身份统一已实测（`zhihu_api30` 模拟器）：临时 logcat 核对实际出站头为真实机型 / 系统 / Build ID + 11.4.0 / 40408 / x-api-version 3.0.93 / 无 Installer / 真实屏幕与网络类型；udid 引导成功并落盘（`account.json` 的 `mobileUdid`，安卓接口带 `x-udid`）；Web UA 迁移成 Chrome 145 落盘；混合推荐（安卓 + 网页卡片）、文章详情、通知角标正常，无请求失败日志。未验证：身份切换接口（需子账号）、手机登录完整流程、网页登录去掉 UA 覆盖后的登录（模拟器风控受限）、WebviewActivity 页面、关注 / 热榜等其他 feed——需真机确认。
- 出站身份的已知残留：风控 WebView（`AndroidLoginUi.configureRiskControlWebView`）仍用桌面 Chrome UA，与移动端 client hints 不一致；游客态不带 `x-udid`、多账号各自持一份 udid；官方实验分组字段未补齐（无可靠依据）；TLS 指纹与 UA 不符（网页接口自称 Chrome 但握手是平台 TLS，Ktor 层无解）。
- 混合推荐模式下同一个登录凭证同时以网页浏览器和安卓客户端两种身份请求，这是混合模式本身的做法，不改模式就消除不了；固定用网页推荐时额外请求最少。
- 安卓推荐卡片按滑动位置过滤：快速往下甩很远时，偶尔会看到一张本该被屏蔽的卡片，滑出屏幕后才消失。
- 首次添加屏蔽话题时，当前列表里的网页推荐卡片没拉过详情、不知道话题，不会立刻被移除，下次刷新才按话题过滤。
- 收藏夹搜索：关键词被加粗、链接等格式拆开时能搜到但不高亮、不滚动；开了提椠渲染器时只打开不滚动；「数据同步」不包含收藏索引，换手机后首次搜索重建。
- 一直失败的两个 JVM 单元测试（与本 fork 改动无关，未修）：`MobileHomeFeedParserTest`（期望卡片末尾有已删除的「手机版推荐」标签）、`ArticleExportHtmlTest`（期望上游的 GitHub 地址）。
- release 包验证：v1.3 做过基本验证（首页、正文、切换回答、下拉刷新、账号页无崩溃）；v2.6 在 `zhihu_api30` 模拟器冒烟通过（混合推荐首页、文章详情、通知角标、出站身份生效）；v2.0～v2.5 的 release 包未在设备上装过；公式、代码块、图片预览等没逐项测。
