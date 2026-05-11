# 面试讲解稿

## 30 秒版本

我做了一个持仓复盘 Android Demo，目标是帮助个人投资者在收盘后快速更新持仓、查看行情异动、导入券商截图，并生成每日复盘文案。技术上使用 Kotlin、Jetpack Compose、Room、Hilt、Retrofit、WorkManager 和 ML Kit OCR。这个项目重点不是做交易，而是展示我能用现代 Android 架构完成一个端侧产品闭环。

## 2 分钟版本

这个 Demo 的用户场景是：用户每天收盘后想知道自己的持仓赚亏、哪些股票贡献或拖累最大、关注股有没有异动，并希望快速生成一段复盘文字。

架构上我用了单 Activity + Compose Navigation，页面包括首页、关注列表、截图导入、复盘和持仓编辑。每个页面通过 ViewModel 暴露 `StateFlow`，UI 只负责渲染状态和派发事件。

业务层拆成几个 UseCase：`CalculatePortfolioUseCase` 负责组合盈亏计算，`RefreshQuotesUseCase` 负责刷新行情，`AnalyzeMarketSignalsUseCase` 负责生成异动提示，`ParseOcrHoldingUseCase` 负责把 OCR 文本解析成持仓草稿，`GenerateDailyReviewUseCase` 负责生成复盘文案和 AI 润色 Prompt。

数据层用 Room 保存持仓、关注股票、行情快照和每日复盘。行情接口封装成 `QuoteRemoteDataSource`，第一版使用腾讯免费行情文本接口。OCR 用 ML Kit 中文识别，图片选择用 Android Photo Picker，避免自己申请相册权限。

AI/Vibe Coding 方面，我会用 AI 辅助需求拆解、代码生成和代码审查，但关键逻辑会人工检查，比如收益计算、OCR 识别错误、行情失败回退、空持仓状态和数据落库。

## 常见追问

### 为什么从 Java 改成 Kotlin + Compose？

因为这个岗位强调 AI 工具和端侧闭环，我希望 Demo 同时展示现代 Android 技术栈。Kotlin、Compose、Flow、Room、Hilt 是现在 Android 项目更常见的组合，也更适合面试展开。

### 腾讯免费行情接口可靠吗？

不把它当生产依赖。第一版只是 Demo 和学习用途，所以我把它封装在 `QuoteRemoteDataSource` 后面。后续如果接正式数据服务，只需要替换数据源，不影响 ViewModel 和 UI。

### OCR 识别错了怎么办？

OCR 结果不会直接写入正式持仓，而是生成 `OcrHoldingDraft`，进入确认页。用户可以修改代码、名称、数量、成本价、现价，确认后才保存。

### 为什么不直接接 AI API 生成复盘？

第一版先本地模板生成，保证面试演示不依赖 API Key、费用和网络。与此同时生成 AI Prompt，用户可以复制到 Codex、Cursor、Trae 或 ChatGPT 继续润色。
