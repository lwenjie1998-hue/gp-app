# 大盘AI助手

Android 原生应用，实时跟踪 A 股三大指数（上证指数、深证成指、创业板指），并通过智谱 GLM-4 大模型进行大盘走势分析和操作建议。

## 核心功能

- **三大指数实时行情** — 30秒自动刷新上证/深证/创业板指数，显示点位、涨跌幅、成交额
- **AI大盘分析** — 每5分钟调用GLM-4分析市场情绪、趋势方向、风险等级，给出操作建议
- **市场新闻** — 抓取最新市场要闻，辅助判断
- **通知推送** — 高置信度分析结果自动推送通知

## 技术架构

```
MainActivity (UI层)
  ├── StockDataService   → MarketApi (新浪财经) → 三大指数数据
  ├── AIRecommendationService → GLM4Client → 大盘分析
  └── StockRepository    → SharedPreferences → 数据持久化
```

| 模块                      | 说明                   |
| ------------------------- | ---------------------- |
| `MarketIndex`             | 大盘指数数据模型       |
| `MarketAnalysis`          | AI分析结果模型         |
| `MarketApi`               | 新浪财经指数数据抓取   |
| `GLM4Client`              | 智谱GLM-4 API调用      |
| `StockDataService`        | 前台服务，循环抓取指数 |
| `AIRecommendationService` | 前台服务，定时AI分析   |

## 快速开始

### 1. 环境要求

- Android Studio / Gradle 8.2+
- JDK 17
- Android SDK 34 (minSdk 26)

### 2. 编译运行

```bash
./gradlew assembleDebug
```

### 3. 配置API密钥

1. 前往 [open.bigmodel.cn](https://open.bigmodel.cn/) 注册账号
2. 创建API密钥
3. 在APP右上角设置图标中输入密钥

### 4. 使用

1. 点击 **开始监控** 启动后台服务
2. 自动获取三大指数实时数据
3. AI每5分钟输出大盘分析
4. 高置信度分析会推送通知

## 数据源

| 数据     | 来源      | 说明           |
| -------- | --------- | -------------- |
| 指数行情 | 新浪财经  | 免费，30秒刷新 |
| AI分析   | 智谱GLM-4 | 需API密钥      |
| 市场新闻 | 可配置    | 需自行对接     |

## 依赖

- OkHttp 4.12 — 网络请求
- Gson 2.10 — JSON解析
- Material Components — UI组件
- MPAndroidChart — 图表（预留）

## License

MIT
