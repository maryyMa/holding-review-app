# 架构设计

## 分层结构

```text
表现层
Compose Screen + Navigation + ViewModel + StateFlow

业务层
UseCase：组合计算、行情刷新、监控检查、技术指标、OCR 解析、复盘生成

数据层
Repository：统一协调本地数据库、行情接口、K 线接口、OCR 草稿和复盘记录

基础设施层
Room、Retrofit/OkHttp、ML Kit、WorkManager、Hilt
```

## 数据流

```text
Room Flow / 行情刷新 / K 线刷新 / OCR 结果 / 后台监控
        ↓
Repository
        ↓
UseCase 计算组合、关注涨幅、技术指标、预警和复盘
        ↓
ViewModel 聚合成 UI StateFlow
        ↓
Compose 页面渲染
```

## 主要模块

- `presentation`：Compose 页面、导航路由、页面状态和 ViewModel。
- `domain/model`：持仓、关注、行情、预警、交易操作等领域模型。
- `domain/usecase`：组合收益、行情刷新、股票监控、OCR 解析和复盘生成。
- `data/local`：Room 数据库、实体、DAO 和迁移。
- `data/remote`：腾讯行情和东方财富 K 线数据源。
- `data/repository`：组合数据仓库和股票监控仓库。
- `worker`：后台监控任务。
- `di`：Hilt 依赖注入配置。

## 本地数据

Room 当前版本为 v4，主要表：

- `holdings`：持仓记录。
- `watch_stocks`：关注股票，包含关注时间和关注基准收盘价。
- `quote_snapshots`：本地行情快照。
- `monitor_configs`：股票监控配置。
- `monitor_alerts`：已触发预警。
- `trade_operations`：买入/卖出操作记录。
- `daily_reviews`：每日复盘。
- `kline_cache`：日 K 线缓存。

数据库使用显式 migration 升级，不启用 destructive migration。新增字段或表时必须补 Room migration 和 schema，避免版本升级时清空个人持仓数据。

## 内置个人数据

应用启动时会尝试读取 `app/src/main/assets/personal_portfolio.local.json`。如果文件存在且持仓/关注表为空，则导入持仓、关注股和监控配置；如果本地已有数据，则跳过导入，避免覆盖用户在 App 内维护的数据。

真实个人数据文件已加入 `.gitignore`。可提交的模板是 `docs/personal_portfolio.example.json`。

## 通知策略

后台监控由 WorkManager 周期执行。新生成的严重和警告级别预警会通过 `StockMonitorNotifier` 发送系统通知，提示级别只保存到本地预警列表。通知点击后进入对应预警详情；如果预警已被清理，则回到首页。

## 设计取舍

- Compose + StateFlow：让 UI 只消费状态，业务逻辑放在 ViewModel 和 UseCase。
- Repository 抽象数据源：行情接口、K 线接口和本地缓存可以替换，不影响页面层。
- Room 持久化：持仓、关注、行情、预警和操作记录都需要离线可用。
- WorkManager：用于周期性监控，适合 Android 后台任务约束。
- OCR 草稿确认：识别结果不直接入库，先让用户校验，降低错误数据进入持仓的风险。
- 交易操作暂不支持编辑/删除：买卖操作会同步持仓，回滚逻辑需要更完整的账本设计，后续可扩展“撤销最近操作”。

## 后续方向

- 预警忽略、稍后提醒和自定义阈值模板。
- 交易计划价位和执行提醒。
- 已实现盈亏、胜率、持仓周期等统计。
- 行业和仓位风险总览。
- 数据备份、导入和导出。
