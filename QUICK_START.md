# 快速开始指南

## 第一步：准备API密钥

### 获取智谱AI API密钥
1. 访问 [智谱AI开放平台](https://open.bigmodel.cn/)
2. 注册账号并完成实名认证
3. 进入控制台 → API密钥管理
4. 创建新的API密钥
5. 复制密钥（格式类似：`xxxxxxxx.xxxxxx.xxxxxxxxxxxxxxxx`）

⚠️ **重要**：
- 请妥善保管API密钥，不要泄露给他人
- 新用户通常有一定额度的免费调用
- 注意查看API使用量和额度

## 第二步：配置数据源

### 当前状态
项目目前使用的是示例API地址，需要配置真实的数据源才能抓取股票数据。

### 推荐的数据源

#### 方案1：使用Tushare（免费）
```java
// 编辑 app/src/main/java/com/gp/stockapp/api/StockApi.java
private static final String STOCK_DATA_API = "http://api.tushare.pro";
```

#### 方案2：使用东方财富API
```java
private static final String STOCK_DATA_API = "https://push2.eastmoney.com/api/qt/clist/get";
```

#### 方案3：使用新浪财经API
```java
private static final String STOCK_DATA_API = "https://hq.sinajs.cn";
```

#### 方案4：自建服务
- 使用Python搭建后端服务
- 调用Tushare、AKShare等数据源
- 提供RESTful API给Android端使用

### 配置新闻数据源

#### 方案1：使用东方财富新闻API
```java
private static final String NEWS_API = "https://np-anotice-stock.eastmoney.com/api/security/ann";
```

#### 方案2：使用新浪财经新闻API
```java
private static final String NEWS_API = "https://finance.sina.com.cn/roll/index.d.html";
```

## 第三步：编译运行

### 使用Android Studio
1. 打开Android Studio
2. 选择 "Open an Existing Project"
3. 选择项目目录 `D:\project\Android\gp-app`
4. 等待Gradle同步完成
5. 连接Android设备或启动模拟器
6. 点击运行按钮（▶️）

### 使用命令行
```bash
cd D:\project\Android\gp-app
./gradlew assembleDebug
./gradlew installDebug
```

## 第四步：使用APP

### 1. 设置API密钥
1. 打开APP
2. 在"GLM-4.7 API密钥"输入框中粘贴你的API密钥
3. 点击"测试API连接"
4. 确认显示"API连接成功"

### 2. 添加关注股票
1. 在"股票代码"输入框中输入股票代码：
   - 上海市场：6位数字，如 `600000`
   - 深圳市场：6位数字，如 `000001`
   - 创业板：3开头，如 `300001`
2. 点击"添加关注"
3. 重复以上步骤添加多只股票

### 3. 启动服务
1. 点击"启动服务"按钮
2. APP将开始：
   - 循环抓取股票数据（30秒/次）
   - 循环抓取新闻数据
   - 定期进行AI分析（5分钟/次）
   - 发送高置信度推荐通知

### 4. 查看推荐
- 高置信度推荐（≥80%）会通过系统通知推送
- 点击通知可以查看详细信息
- 数据统计会显示在APP底部

## 常见问题

### Q1: 显示"API连接失败"
**原因**：
- API密钥错误
- 网络连接问题
- API服务暂时不可用

**解决方法**：
1. 检查API密钥是否正确
2. 检查网络连接
3. 稍后重试

### Q2: 没有数据抓取
**原因**：
- 股票数据API未配置
- 股票代码错误
- API接口失效

**解决方法**：
1. 配置真实的数据源API
2. 检查股票代码是否正确
3. 查看日志了解详细错误

### Q3: 没有收到推荐通知
**原因**：
- 推荐置信度低于阈值（80%）
- 通知权限未授予
- 数据不足，无法分析

**解决方法**：
1. 等待更多数据积累
2. 检查通知权限设置
3. 降低置信度阈值（修改代码）

### Q4: 耗电量大
**原因**：
- 服务频繁运行
- 网络请求频繁

**解决方法**：
1. 增加抓取间隔（修改代码中的 `FETCH_INTERVAL`）
2. 使用WiFi时才抓取数据
3. 减少关注股票数量

### Q5: 如何查看日志
**方法1**：使用Android Studio Logcat
```bash
adb logcat | grep StockApp
```

**方法2**：使用adb命令
```bash
adb logcat -s StockDataService StockApi AIRecommendationService
```

## 高级配置

### 修改抓取频率

编辑 `StockDataService.java`：
```java
private static final long FETCH_INTERVAL = 60000; // 改为60秒
```

编辑 `AIRecommendationService.java`：
```java
private static final long ANALYSIS_INTERVAL = 600000; // 改为10分钟
```

### 修改分析模式

编辑 `AIRecommendationService.java`：
```java
// 量化分析模式
private String analysisMode = "quant";

// 游资分析模式
private String analysisMode = "hot_money";

// 综合分析模式（推荐）
private String analysisMode = "both";
```

### 修改置信度阈值

编辑 `AIRecommendationService.java`：
```java
if (recommendation.getConfidence() >= 70.0) {  // 改为70%
    sendRecommendationNotification(recommendation);
}
```

### 自定义提示词

编辑 `assets/prompts/` 目录下的文件：
- `quantitative_analysis.txt` - 量化分析提示词
- `hot_money_analysis.txt` - 游资分析提示词
- `combined_analysis.txt` - 综合分析提示词

## 性能优化建议

### 1. 减少关注股票数量
- 建议：5-10只股票
- 过多会增加网络请求和AI调用

### 2. 合理设置抓取频率
- 数据抓取：30-60秒
- AI分析：5-10分钟

### 3. 使用WiFi
- 在移动数据网络下可能产生流量费用
- 建议在WiFi环境下使用

### 4. 清理过期数据
- APP会自动清理24小时前的数据
- 可手动清理：在设置中添加"清除数据"按钮

## 风险提示

⚠️ **重要声明**

1. **仅供参考**：本应用提供的股票推荐仅供参考，不构成投资建议
2. **市场风险**：股市有风险，投资需谨慎
3. **自担风险**：投资者据此操作，风险自担
4. **不承担责任**：作者不对投资结果承担任何责任
5. **合法合规**：请遵守当地法律法规和证券监管要求

## 技术支持

### 遇到问题？
1. 查看 [README.md](README.md) 获取详细说明
2. 查看日志了解错误信息
3. 提交Issue描述问题

### 反馈建议
欢迎提交Issue或Pull Request来改进这个项目。

---

**祝你投资顺利！** 📈
