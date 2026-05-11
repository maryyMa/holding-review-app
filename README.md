# 持仓复盘 App Demo

这是一个面向 Android 面试展示的现代化 Demo。它把“个人持仓记录 + 行情观察 + 截图 OCR 导入 + 每日复盘文案”做成一条完整闭环，重点展示 Kotlin 现代 Android 架构和 AI 辅助开发思路。

## 技术栈

- Kotlin
- Jetpack Compose + Material 3
- MVVM + StateFlow
- Room
- Hilt
- Retrofit + OkHttp
- WorkManager
- ML Kit Text Recognition Chinese
- Photo Picker
- JUnit 单元测试

## 核心功能

- 持仓列表：展示总市值、当日盈亏、累计盈亏、持仓明细。
- 持仓编辑：新增、编辑、删除 A 股持仓。
- 关注列表：收集关注股票，并和持仓一起刷新行情。
- 行情刷新：通过腾讯免费行情文本接口拉取 A 股报价，失败时保留本地缓存。
- 异动分析：根据涨跌幅、持仓贡献、拖累和仓位集中度生成可解释提示。
- 截图导入：使用系统 Photo Picker 选择券商持仓截图，ML Kit OCR 识别文本，再生成可确认的持仓草稿。
- 每日复盘：本地模板生成复盘文案，并生成 AI 润色 Prompt。

## 架构说明

表现层是单 Activity + Compose Navigation。首页、关注列表、截图导入、每日复盘、持仓编辑分别由 ViewModel 输出 `StateFlow` 状态。

业务层使用 UseCase 拆分：

- `CalculatePortfolioUseCase`
- `RefreshQuotesUseCase`
- `AnalyzeMarketSignalsUseCase`
- `ParseOcrHoldingUseCase`
- `GenerateDailyReviewUseCase`

数据层使用 Repository 协调 Room、腾讯行情数据源、OCR 解析结果和每日复盘记录。

## 运行方式

1. 安装 Android Studio。
2. 打开本目录：`D:\ai\find_a_job\holding-review-app`。
3. 等待 Gradle 同步依赖。
4. 运行到模拟器或真机。

当前 Codex 命令行环境没有检测到 `java`、`gradle`、`adb`，所以我无法在这里直接编译。安装 Android Studio 后如果 Gradle 同步报错，把完整报错贴给我继续修。

## 面试展示顺序

1. 首页展示组合概览和异动提示。
2. 点击刷新行情，说明腾讯行情接口和缓存策略。
3. 进入关注列表，展示持仓外的观察股票。
4. 进入截图导入，选择持仓截图，展示 OCR 草稿确认流程。
5. 进入每日复盘，保存或复制复盘文案和 AI Prompt。
6. 打开代码讲架构：ViewModel、UseCase、Repository、Room、RemoteDataSource。

## 边界声明

- 免费行情接口仅用于学习和 Demo，不保证稳定性。
- 复盘文案不构成投资建议。
- 第一版只支持 A 股，不做真实交易、登录、云同步和自动下单。
