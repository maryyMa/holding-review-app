# 架构设计

## 分层

```text
UI 层
Compose Screen + ViewModel + StateFlow

业务层
UseCase：收益计算、行情刷新、异动分析、OCR 解析、复盘生成

数据层
PortfolioRepository：统一协调本地数据库、行情接口、OCR 草稿和复盘记录

基础设施层
Room、Retrofit/OkHttp、ML Kit、WorkManager、Hilt
```

## 数据流

```text
Room Flow / 行情刷新 / OCR 结果
        ↓
PortfolioRepository
        ↓
UseCase 计算组合、异动、复盘
        ↓
ViewModel 转成 UI StateFlow
        ↓
Compose 页面渲染
```

## 为什么这样设计

- Compose + StateFlow：符合当前 Android 主流声明式 UI 和响应式状态管理。
- Room：持仓、关注、行情快照、复盘都需要结构化本地保存。
- Hilt：让 Repository、UseCase、数据源、Worker 可替换、可测试。
- WorkManager：定时刷新行情，符合后台任务推荐方式。
- ML Kit OCR：截图导入减少每日手动录入成本。
- Repository 抽象行情源：第一版用腾讯免费接口，后续可替换为正式 API。

## 面试讲解重点

这个项目不是追求功能堆满，而是把“个人投资者每日复盘”拆成现代 Android 中常见的几类能力：本地数据库、网络请求、后台任务、OCR、业务计算、Compose UI 和 AI 文案生成。
