# API配置说明

## 智谱AI GLM-4.7 API

### 基本信息
- **API地址**: `https://open.bigmodel.cn/api/paas/v4/chat/completions`
- **模型名称**: `glm-4`
- **认证方式**: Bearer Token
- **请求方式**: POST

### 获取API密钥
1. 访问 [智谱AI开放平台](https://open.bigmodel.cn/)
2. 注册账号并完成实名认证
3. 进入控制台
4. 点击"API密钥管理"
5. 创建新的API密钥
6. 复制密钥（格式：`xxxxxxxx.xxxxxx.xxxxxxxxxxxxxxxx`）

### 使用限制
- 新用户有一定额度的免费调用
- 注意查看API使用量和余额
- 根据需求选择合适的套餐

### 费用参考（2026年）
- GLM-4 Plus: ¥0.05/千tokens
- GLM-4: ¥0.01/千tokens
- 具体价格以官方为准

## 股票数据API

### 推荐数据源

#### 1. Tushare（推荐）
- **官网**: https://tushare.pro/
- **特点**: 免费开源，数据全面
- **注册**: 需要注册账号获取Token
- **接口文档**: https://tushare.pro/document/2

**API示例**:
```python
import tushare as ts

# 设置token
ts.set_token('your_token_here')
pro = ts.pro_api()

# 获取实时行情
df = pro.daily(ts_code='600000.SH', start_date='20260101', end_date='20260201')
```

#### 2. AKShare
- **官网**: https://akshare.akfamily.xyz/
- **特点**: 免费开源，数据源丰富
- **无需注册**: 直接使用
- **接口文档**: https://akshare.akfamily.xyz/data/stock/stock.html

**API示例**:
```python
import akshare as ak

# 获取实时行情
df = ak.stock_zh_a_spot_em()
```

#### 3. 东方财富
- **官网**: http://data.eastmoney.com/
- **特点**: 免费，数据实时
- **无需注册**: 直接使用
- **格式**: JSON

**API示例**:
```
GET https://push2.eastmoney.com/api/qt/clist/get
参数: fltt=2&pn=1&pz=20&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fields=f12,f13,f14,f2,f3,f5,f6,f7,f8,f9,f10,f15,f16,f17,f18,f20,f21,f23,f24,f25,f26,f27,f28,f33,f34,f35,f36,f37,f38,f39,f40,f41,f42,f43,f44,f45,f46,f47,f48,f49,f50,f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61,f62,f63,f64,f65,f66,f67,f68,f69,f70,f71,f72,f73,f74,f75,f76,f77,f78,f79,f80,f81,f82,f84,f85,f86,f87,f88,f89,f90,f91,f92,f93,f94,f95,f96,f97,f98,f99,f100&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23&fid=f62&_=1706726400000
```

#### 4. 新浪财经
- **官网**: https://finance.sina.com.cn/
- **特点**: 免费，数据实时
- **无需注册**: 直接使用
- **格式**: JavaScript变量

**API示例**:
```
GET https://hq.sinajs.cn/list=sh600000
返回: var hq_str_sh600000="12.50,12.45,12.55,12.60,12.40,12.55,12.56,120000000,1500000000.00,..."
```

### 自建API服务方案

如果需要更稳定的数据服务，建议自建后端服务：

#### 方案1：Python Flask服务
```python
from flask import Flask, jsonify
import tushare as ts

app = Flask(__name__)

# 设置Tushare token
ts.set_token('your_token_here')
pro = ts.pro_api()

@app.route('/api/stock/<code>')
def get_stock_data(code):
    df = pro.daily(ts_code=code)
    return jsonify(df.to_dict('records'))

@app.route('/api/news')
def get_news():
    # 获取新闻数据
    news = pro.news()
    return jsonify(news.to_dict('records'))

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
```

#### 方案2：Python FastAPI服务
```python
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import tushare as ts

app = FastAPI()

# 允许跨域
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

ts.set_token('your_token_here')
pro = ts.pro_api()

@app.get("/api/stock/{code}")
async def get_stock_data(code: str):
    df = pro.daily(ts_code=code)
    return df.to_dict('records')

@app.get("/api/news")
async def get_news():
    news = pro.news()
    return news.to_dict('records')

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5000)
```

## 新闻API

### 推荐数据源

#### 1. 东方财富新闻
```
GET https://np-anotice-stock.eastmoney.com/api/security/ann
参数: sr=-1&page_size=50&page_index=1&ann_type=A&client_source=web&f_node=0
```

#### 2. 新浪财经新闻
```
GET https://finance.sina.com.cn/roll/index.d.html
```

#### 3. 财联社
```
GET https://api.cls.cn/nodeapi/telegraphs?app=Cailianpress&os=web&rn=20
```

#### 4. 同花顺
```
GET https://news.10jqka.com.cn/realtimenews.json
```

## 数据格式要求

### 股票数据格式（JSON）
```json
[
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
    "timestamp": 1706726400000,
    "turnover_rate": 8.5,
    "pe_ratio": 6.5,
    "market_cap": 1500.0,
    "main_inflow": 50000000.0,
    "retail_inflow": 30000000.0,
    "limit_up_times": 0,
    "consecutive_rise_days": 3
  }
]
```

### 新闻数据格式（JSON）
```json
[
  {
    "news_id": "123456",
    "title": "标题",
    "content": "新闻内容",
    "summary": "摘要",
    "source": "来源",
    "publish_time": 1706726400000,
    "stock_codes": ["600000", "000001"],
    "importance": 5,
    "news_type": "政策",
    "sentiment": "positive",
    "impact_level": "high"
  }
]
```

## 配置示例

### 修改StockApi.java

```java
// 使用东方财富API
private static final String STOCK_DATA_API = "https://push2.eastmoney.com/api/qt/clist/get";

// 使用自建服务
private static final String STOCK_DATA_API = "http://your-server.com:5000/api/stock";

// 新闻API
private static final String NEWS_API = "https://np-anotice-stock.eastmoney.com/api/security/ann";
```

### 数据转换

如果API返回的数据格式与预期不符，需要在 `StockApi.java` 中进行数据转换：

```java
// 假设API返回的数据格式不同
private StockData convertToStockData(JSONObject rawData) {
    StockData data = new StockData();
    // 映射字段
    data.setStockCode(rawData.optString("f12"));  // 东方财富字段名
    data.setStockName(rawData.optString("f14"));
    data.setCurrentPrice(rawData.optDouble("f2"));
    data.setChangePercent(rawData.optDouble("f3"));
    // ... 其他字段
    return data;
}
```

## 注意事项

1. **数据来源可靠性**：选择可靠的数据源，确保数据准确
2. **请求频率限制**：遵守API的请求频率限制，避免被封
3. **数据延迟**：注意实时数据的延迟情况
4. **数据完整性**：确保所需字段都能获取到
5. **错误处理**：添加适当的错误处理机制

## 测试API

### 测试股票数据API
```bash
curl "https://push2.eastmoney.com/api/qt/clist/get?fltt=2&pn=1&pz=10&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fields=f12,f13,f14,f2,f3,f5,f6,f7,f8,f9,f10,f15,f16,f17,f18,f20,f21,f23,f24,f25,f26,f27,f28,f33,f34,f35,f36,f37,f38,f39,f40,f41,f42,f43,f44,f45,f46,f47,f48,f49,f50,f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61,f62,f63,f64,f65,f66,f67,f68,f69,f70,f71,f72,f73,f74,f75,f76,f77,f78,f79,f80,f81,f82,f84,f85,f86,f87,f88,f89,f90,f91,f92,f93,f94,f95,f96,f97,f98,f99,f100&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23&fid=f62&_=$(date +%s)000"
```

### 测试新闻API
```bash
curl "https://np-anotice-stock.eastmoney.com/api/security/ann?sr=-1&page_size=10&page_index=1&ann_type=A&client_source=web&f_node=0"
```

---

**建议**：初学者可以使用东方财富或新浪的免费API，如果需要更稳定的数据，建议使用Tushare并自建后端服务。
