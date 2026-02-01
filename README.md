# 股票数据抓取与AI推荐APP

基于原生Android开发的股票实时数据抓取和AI智能推荐应用，融合量化分析和游资视角，为投资者提供精准的股票推荐。

## 功能特性

### 1. 实时数据抓取
- 循环抓取股票实时行情数据（30秒/次）
- 实时抓取股市新闻和资讯
- 自动识别量化信号和游资信号
- 智能过滤重要信号并发送通知

### 2. AI智能推荐
- 集成智谱GLM-4.7大模型
- 三种分析模式：
  - **量化分析**：基于技术指标、量化因子、统计分析
  - **游资分析**：基于题材热点、资金博弈、市场情绪
  - **综合分析**：融合量化和游资双重视角
- 自动生成买入/卖出建议
- 提供目标价格、止损位、止盈位

### 3. 智能信号识别
- 量化信号：趋势突破、量价配合、动量反转
- 游资信号：涨停潜力、主力资金、投机热度
- 高置信度推荐（≥80%）自动推送通知

### 4. 数据管理
- 支持自定义关注列表
- 本地存储股票数据、新闻、推荐
- 自动清理过期数据
- 数据统计展示

## 技术架构

### 核心模块

```
com.gp.stockapp
├── model/               # 数据模型
│   ├── StockData.java          # 股票数据模型
│   ├── StockNews.java          # 新闻数据模型
│   └── AIRecommendation.java   # AI推荐结果模型
├── service/            # 后台服务
│   ├── StockDataService.java         # 股票数据抓取服务
│   └── AIRecommendationService.java  # AI推荐分析服务
├── api/                # API客户端
│   ├── StockApi.java           # 股票数据API
│   └── GLM4Client.java         # GLM-4.7 API客户端
├── repository/         # 数据仓库
│   └── StockRepository.java    # 数据存储管理
├── utils/              # 工具类
│   └── PromptLoader.java       # 提示词加载器
└── MainActivity.java            # 主界面
```

### 技术栈

- **语言**：Java + Kotlin混合开发
- **网络请求**：OkHttp + Retrofit
- **数据存储**：SharedPreferences
- **后台任务**：Foreground Service + WorkManager
- **JSON解析**：Gson
- **图表库**：MPAndroidChart
- **协程**：Kotlin Coroutines

## 使用指南

### 1. 安装与配置

#### 获取API密钥
1. 访问[智谱AI开放平台](https://open.bigmodel.cn/)
2. 注册账号并申请API密钥
3. 复制API密钥备用

#### 编译运行
```bash
# 克隆项目
git clone <repository-url>
cd gp-app

# 使用Android Studio打开项目
# 配置API密钥后运行
```

### 2. 基本使用

#### 启动服务
1. 打开APP
2. 输入GLM-4.7 API密钥
3. 点击"测试API连接"验证
4. 点击"启动服务"

#### 添加关注股票
1. 在"股票代码"输入框输入股票代码（如：600000）
2. 点击"添加关注"
3. 重复以上步骤添加多只股票

#### 查看推荐
- APP会自动分析关注的股票
- 高置信度推荐（≥80%）会通过通知推送
- 推荐包含：
  - 推荐类型（量化/游资/综合）
  - 置信度
  - 目标价格
  - 止损位
  - 止盈位
  - 持有周期
  - 推荐理由

### 3. API数据源配置

当前项目使用示例API地址，需要配置真实的数据源：

#### 配置股票数据API
编辑 `StockApi.java`：
```java
private static final String STOCK_DATA_API = "https://your-stock-api.com/realtime";
```

#### 配置新闻API
编辑 `StockApi.java`：
```java
private static final String NEWS_API = "https://your-news-api.com/news";
```

### 4. 自定义分析模式

在 `AIRecommendationService.java` 中修改：
```java
// 量化模式
private String analysisMode = "quant";

// 游资模式
private String analysisMode = "hot_money";

// 综合模式（推荐）
private String analysisMode = "both";
```

## 提示词系统

### 提示词位置
```
assets/prompts/
├── quantitative_analysis.txt    # 量化分析提示词
├── hot_money_analysis.txt       # 游资分析提示词
└── combined_analysis.txt       # 综合分析提示词
```

### 量化分析提示词特点
- 专业量化分析师人设
- 技术指标计算
- 量化因子构建
- 统计分析方法
- 机器学习模型
- 风险收益评估

### 游资分析提示词特点
- 资深游资操盘手人设
- 题材热点研判
- 游资打板战法
- 资金博弈分析
- 市场情绪研判
- 短线技术分析

### 综合分析提示词特点
- 双重身份专家人设
- 融合量化和游资视角
- 多维度信号识别
- 一致性验证
- 策略制定

## 数据模型说明

### StockData（股票数据）
```json
{
  "stock_code": "600000",
  "stock_name": "浦发银行",
  "current_price": 12.50,
  "pre_close": 12.00,
  "change_percent": 4.17,
  "volume": 120000000,
  "amount": 1500000000.0,
  "high": 12.80,
  "low": 12.10,
  "open": 12.20,
  "turnover_rate": 8.5,
  "pe_ratio": 6.5,
  "market_cap": 1500.0,
  "main_inflow": 50000000.0,
  "retail_inflow": 30000000.0,
  "limit_up_times": 0,
  "consecutive_rise_days": 3
}
```

### AIRecommendation（AI推荐）
```json
{
  "stock_code": "600000",
  "stock_name": "浦发银行",
  "recommend_type": "both",
  "confidence": 85.5,
  "target_price": 14.00,
  "risk_level": "medium",
  "holding_period": "medium",
  "entry_price": 12.50,
  "stop_loss": 11.80,
  "take_profit": 15.50,
  "reasoning": "技术面和基本面共振...",
  "key_factors": ["因子1", "因子2", "因子3"],
  "quant_signals": {
    "momentum_score": 8.5,
    "volume_surge": true,
    "breakout_signal": true,
    "trend_strength": "strong_up",
    "technical_score": 85.0
  },
  "hot_money_signals": {
    "limit_up_potential": 70.0,
    "main_force_position": "strong_buy",
    "speculative_heat": 6,
    "leading_stock": false,
    "short_term_momentum": 7.5
  },
  "timestamp": 1706726400000
}
```

## 服务说明

### StockDataService
- **运行模式**：前台服务
- **抓取频率**：30秒/次
- **抓取内容**：
  - 关注列表股票的实时行情
  - 最新股市新闻
- **信号检测**：
  - 量化信号检测
  - 游资信号检测
  - 重要信号通知

### AIRecommendationService
- **运行模式**：前台服务
- **分析频率**：5分钟/次
- **分析内容**：
  - 关注列表股票
  - 相关新闻
  - 量化信号
  - 游资信号
- **推荐输出**：
  - JSON格式推荐结果
  - 高置信度通知

## 风险提示

⚠️ **重要声明**

1. 本应用提供的股票推荐仅供参考，不构成投资建议
2. 股市有风险，投资需谨慎
3. 请根据自身风险承受能力做出投资决策
4. 作者不对投资结果承担任何责任
5. 请遵守当地法律法规和证券监管要求

## 性能优化

1. **内存优化**：
   - 使用线程池控制并发
   - 及时释放资源
   - 限制数据存储量

2. **电量优化**：
   - 合理设置抓取频率
   - 使用前台服务保证稳定性
   - 网络请求优化

3. **网络优化**：
   - 使用HTTP/2
   - 连接池复用
   - 请求合并

## 后续计划

- [ ] 添加图表展示（K线图、分时图）
- [ ] 支持技术指标自定义
- [ ] 添加回测功能
- [ ] 支持多策略组合
- [ ] 云端数据同步
- [ ] 社区分享功能
- [ ] 策略回测和优化
- [ ] 更多AI模型支持

## 许可证

本项目仅供学习和研究使用。

## 联系方式

如有问题或建议，欢迎提交Issue。

---

**免责声明**：本应用提供的所有信息仅供参考，不构成任何投资建议。投资者据此操作，风险自担。
