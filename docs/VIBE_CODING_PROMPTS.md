# 开发提示词记录

这些提示词用于记录项目开发过程中的需求拆解、架构设计、代码生成和代码审查思路，便于后续继续迭代。

## 需求拆解

```text
我要做一个 Android 持仓复盘 App，目标用户是个人投资者。功能包括持仓记录、关注股票、A 股行情刷新、监控预警、上传券商截图 OCR 导入持仓、买卖操作记录、每日复盘文案生成和复制导出。请帮我拆 MVP、页面、数据模型和边界。
```

## 架构设计

```text
请基于现代 Android 技术栈设计架构：Kotlin、Jetpack Compose、MVVM、StateFlow、Room、Hilt、Retrofit/OkHttp、WorkManager、ML Kit OCR。要求说明 UI 层、业务层、数据层、依赖注入、后台任务和测试策略。
```

## 代码生成

```text
请生成一个 Kotlin Android 项目的核心代码结构，包括 Room 实体和 DAO、Repository、行情 RemoteDataSource、OCR 解析 UseCase、收益计算 UseCase、股票监控 UseCase、复盘生成 UseCase、Compose 页面和 ViewModel。要求每层职责清晰，避免把业务逻辑写在 UI 里。
```

## 代码审查

```text
请审查这个项目，重点检查 Compose 状态管理、Room 数据设计、数据库迁移、网络失败回退、OCR 识别确认流程、收益计算边界、交易操作同步持仓、Hilt 注入和可测试性。
```

## 产品迭代

```text
请从个人投资者每日复盘的使用场景出发，评估这个持仓复盘 App 还需要补充哪些功能。重点考虑预警处理、交易计划、数据更新时间、行业风险、仓位风险、操作统计和数据备份。
```
