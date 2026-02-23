package com.gp.stockapp.api;

import android.util.Log;

import com.gp.stockapp.model.MarketIndex;
import com.gp.stockapp.model.StockNews;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 大盘指数数据API客户端
 * 抓取三大指数（上证/深证/创业板）实时数据和市场新闻
 */
public class MarketApi {
    private static final String TAG = "MarketApi";
    private static MarketApi instance;

    private OkHttpClient client;
    private Gson gson;

    // 新浪财经实时行情API
    private static final String SINA_API = "https://hq.sinajs.cn/list=";

    // ===== 新闻源 =====
    // 新浪财经滚动新闻（股市要闻）
    private static final String SINA_NEWS_API = "https://feed.mix.sina.com.cn/api/roll/get?pageid=153&lid=2516&k=&num=%d&page=1&r=0.1&callback=";
    // 东方财富财经快讯
    private static final String EASTMONEY_NEWS_API = "https://np-listapi.eastmoney.com/comm/web/getFastNewsList?client=web&biz=web_home_channel&fastColumn=102&sortEnd=&pageSize=%d&req_trace=";
    // 新浪财经全球要闻
    private static final String SINA_GLOBAL_NEWS_API = "https://feed.mix.sina.com.cn/api/roll/get?pageid=155&lid=2520&k=&num=%d&page=1&r=0.1&callback=";

    // 三大指数代码
    private static final String[] INDEX_CODES = {
            "sh000001",   // 上证指数
            "sz399001",   // 深证成指
            "sz399006"    // 创业板指
    };

    private MarketApi() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request request = original.newBuilder()
                            .header("Referer", "https://finance.sina.com.cn")
                            .header("User-Agent", "Mozilla/5.0")
                            .build();
                    return chain.proceed(request);
                })
                .build();
        gson = new Gson();
    }

    public static synchronized MarketApi getInstance() {
        if (instance == null) {
            instance = new MarketApi();
        }
        return instance;
    }

    /**
     * 抓取三大指数实时数据
     */
    public List<MarketIndex> fetchMarketIndices() {
        List<MarketIndex> indices = new ArrayList<>();
        Log.d(TAG, "正在抓取三大指数实时数据...");

        try {
            // 构建请求URL：同时请求三大指数
            String codes = String.join(",", INDEX_CODES);
            String url = SINA_API + codes;
            Log.d(TAG, "指数请求URL: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            Response response = client.newCall(request).execute();

            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                indices = parseSinaResponse(responseBody);
                Log.d(TAG, "成功抓取到 " + indices.size() + " 个指数数据");
            } else {
                Log.w(TAG, "指数抓取请求失败，状态码: " + (response != null ? response.code() : "unknown"));
            }

        } catch (IOException e) {
            Log.e(TAG, "抓取大盘指数时发生IO错误", e);
        }

        return indices;
    }

    /**
     * 解析新浪财经返回的指数数据
     *
     * 新浪格式示例：
     * var hq_str_sh000001="上证指数,3356.78,3340.56,3356.78,3370.12,3330.45,0,0,234567890,234567890000,...";
     *
     * 字段顺序（指数）：
     * 0-名称, 1-今开, 2-昨收, 3-当前点位, 4-最高, 5-最低,
     * 6-买入(无), 7-卖出(无), 8-成交量(手), 9-成交额(元), ...
     */
    private List<MarketIndex> parseSinaResponse(String response) {
        List<MarketIndex> indices = new ArrayList<>();

        if (response == null || response.isEmpty()) {
            return indices;
        }

        String[] lines = response.split("\n");
        for (String line : lines) {
            try {
                line = line.trim();
                if (line.isEmpty() || !line.startsWith("var hq_str_")) {
                    continue;
                }

                // 提取代码：var hq_str_sh000001="..."
                int codeStart = line.indexOf("hq_str_") + 7;
                int codeEnd = line.indexOf("=");
                if (codeStart < 0 || codeEnd < 0) continue;
                String code = line.substring(codeStart, codeEnd);

                // 提取数据
                int dataStart = line.indexOf("\"") + 1;
                int dataEnd = line.lastIndexOf("\"");
                if (dataStart <= 0 || dataEnd <= 0 || dataEnd <= dataStart) continue;
                String data = line.substring(dataStart, dataEnd);

                if (data.isEmpty()) continue;

                String[] fields = data.split(",");
                if (fields.length < 10) continue;

                MarketIndex index = new MarketIndex();
                index.setIndexCode(code);
                index.setIndexName(fields[0]);
                index.setOpen(parseDouble(fields[1]));
                index.setPreClose(parseDouble(fields[2]));
                index.setCurrentPoint(parseDouble(fields[3]));
                index.setHigh(parseDouble(fields[4]));
                index.setLow(parseDouble(fields[5]));
                index.setVolume(parseLong(fields[8]));

                // 成交额转换为亿元
                double amountYuan = parseDouble(fields[9]);
                index.setAmount(amountYuan / 100000000.0);

                // 计算涨跌点数和涨跌幅
                double preClose = index.getPreClose();
                if (preClose > 0) {
                    index.setChangePoint(index.getCurrentPoint() - preClose);
                    index.setChangePercent((index.getCurrentPoint() - preClose) / preClose * 100);
                }

                index.setTimestamp(System.currentTimeMillis());
                indices.add(index);

                Log.d(TAG, "Parsed index: " + index.getIndexName()
                        + " = " + index.getCurrentPoint()
                        + " (" + index.getFormattedChangePercent() + ")");

            } catch (Exception e) {
                Log.e(TAG, "Error parsing line: " + line, e);
            }
        }

        return indices;
    }

    /**
     * 抓取市场新闻（综合国内外新闻源）
     */
    public List<StockNews> fetchMarketNews() {
        return fetchMarketNews(10);
    }

    /**
     * 抓取指定数量的市场新闻
     * 从多个来源获取，合并去重后返回
     */
    public List<StockNews> fetchMarketNews(int limit) {
        List<StockNews> allNews = new ArrayList<>();

        // 1. 从新浪财经抓取国内股市要闻
        try {
            List<StockNews> sinaNews = fetchSinaNews(limit);
            allNews.addAll(sinaNews);
            Log.d(TAG, "Fetched " + sinaNews.size() + " news from Sina Finance");
        } catch (Exception e) {
            Log.e(TAG, "Error fetching Sina news", e);
        }

        // 2. 从新浪抓取全球财经要闻
        try {
            List<StockNews> globalNews = fetchSinaGlobalNews(limit / 2);
            allNews.addAll(globalNews);
            Log.d(TAG, "Fetched " + globalNews.size() + " global news from Sina");
        } catch (Exception e) {
            Log.e(TAG, "Error fetching Sina global news", e);
        }

        // 3. 从东方财富抓取快讯
        try {
            List<StockNews> eastmoneyNews = fetchEastMoneyNews(limit);
            allNews.addAll(eastmoneyNews);
            Log.d(TAG, "Fetched " + eastmoneyNews.size() + " news from EastMoney");
        } catch (Exception e) {
            Log.e(TAG, "Error fetching EastMoney news", e);
        }

        // 去重（按标题）并按时间降序排序
        List<StockNews> uniqueNews = deduplicateNews(allNews);
        
        // 过滤个股新闻，只保留影响大盘的宏观/政策/国际新闻
        List<StockNews> filteredNews = filterMarketWideNews(uniqueNews);
        filteredNews.sort((a, b) -> Long.compare(b.getPublishTime(), a.getPublishTime()));

        // 限制返回数量
        if (filteredNews.size() > limit) {
            filteredNews = filteredNews.subList(0, limit);
        }

        Log.d(TAG, "Total filtered market-wide news: " + filteredNews.size() + " (before filter: " + uniqueNews.size() + ")");
        return filteredNews;
    }

    /**
     * 从新浪财经滚动新闻API抓取国内股市要闻
     *
     * 接口返回JSON格式：
     * {"result":{"status":{"code":0},"data":{"feed":{"entry":[
     *   {"id":"...","title":"...","summary":"...","published_date":"...","source":"..."},
     *   ...
     * ]}}}}
     */
    private List<StockNews> fetchSinaNews(int limit) throws IOException {
        List<StockNews> newsList = new ArrayList<>();
        String url = String.format(SINA_NEWS_API, Math.min(limit, 20));
        Log.d(TAG, "正在从新浪抓取国内新闻: " + url);

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://finance.sina.com.cn")
                .get()
                .build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful() || response.body() == null) {
            Log.w(TAG, "新浪国内新闻请求失败，状态码: " + (response != null ? response.code() : "unknown"));
            return newsList;
        }

        String body = response.body().string();
        Log.d(TAG, "新浪国内新闻返回长度: " + body.length());
        if (body.length() > 0) {
            Log.v(TAG, "新浪响应内容(前200): " + (body.length() > 200 ? body.substring(0, 200) : body));
        }

        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonElement resultElem = root.get("result");
            
            if (resultElem == null || resultElem.isJsonNull()) {
                Log.w(TAG, "新浪国内新闻返回数据中没有 result 字段");
                return newsList;
            }

            JsonObject data = null;
            JsonArray entries = null;
            if (resultElem.isJsonObject()) {
                JsonElement dataElem = resultElem.getAsJsonObject().get("data");
                if (dataElem != null && dataElem.isJsonObject()) {
                    data = dataElem.getAsJsonObject();
                } else if (dataElem != null && dataElem.isJsonArray()) {
                    // data 直接就是数组
                    entries = dataElem.getAsJsonArray();
                }
            } else {
                Log.w(TAG, "新浪国内新闻 result 字段不是对象，而是: " + resultElem.getClass().getSimpleName());
                return newsList;
            }

            if (data == null && entries == null) {
                Log.w(TAG, "新浪国内新闻返回数据中 data 字段为空或不是对象");
                return newsList;
            }

            // 兼容不同返回格式
            if (entries == null && data != null) {
                if (data.has("feed")) {
                    JsonObject feed = data.getAsJsonObject("feed");
                    if (feed != null && feed.has("entry")) {
                        entries = feed.getAsJsonArray("entry");
                    }
                } else if (data.has("entry")) {
                    entries = data.getAsJsonArray("entry");
                }
            }

            if (entries == null) {
                Log.w(TAG, "无法在新浪响应中找到新闻列表(entries)");
                return newsList;
            }

            for (JsonElement element : entries) {
                try {
                    JsonObject entry = element.getAsJsonObject();
                    StockNews news = new StockNews();
                    news.setNewsId("sina_" + getJsonString(entry, "id"));
                    news.setTitle(cleanHtmlTags(getJsonString(entry, "title")));
                    news.setSummary(cleanHtmlTags(getJsonString(entry, "summary")));
                    news.setSource(getJsonString(entry, "source", "新浪财经"));
                    news.setNewsType("国内财经");

                    // 解析时间
                    String dateStr = getJsonString(entry, "published_date");
                    if (!dateStr.isEmpty()) {
                        news.setPublishTime(parseSinaDate(dateStr));
                    } else {
                        // 尝试用 ctime 字段
                        String ctime = getJsonString(entry, "ctime");
                        if (!ctime.isEmpty()) {
                            news.setPublishTime(parseSinaDate(ctime));
                        } else {
                            news.setPublishTime(System.currentTimeMillis());
                        }
                    }

                    if (news.getTitle() != null && !news.getTitle().isEmpty()) {
                        newsList.add(news);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "解析单条新浪国内新闻出错", e);
                }
            }
            Log.d(TAG, "成功从新浪抓取国内新闻: " + newsList.size() + " 条");
        } catch (Exception e) {
            Log.e(TAG, "解析新浪国内新闻响应数据时出错", e);
        }

        return newsList;
    }

    /**
     * 从新浪抓取全球财经要闻
     */
    private List<StockNews> fetchSinaGlobalNews(int limit) throws IOException {
        List<StockNews> newsList = new ArrayList<>();
        String url = String.format(SINA_GLOBAL_NEWS_API, Math.min(limit, 10));
        Log.d(TAG, "正在从新浪抓取全球新闻: " + url);

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://finance.sina.com.cn")
                .get()
                .build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful() || response.body() == null) {
            Log.w(TAG, "新浪全球新闻请求失败，状态码: " + (response != null ? response.code() : "unknown"));
            return newsList;
        }

        String body = response.body().string();
        Log.d(TAG, "新浪全球新闻返回长度: " + body.length());
        if (body.length() > 0) {
            Log.v(TAG, "新浪全球响应内容(前200): " + (body.length() > 200 ? body.substring(0, 200) : body));
        }

        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonElement resultElem = root.get("result");
            
            if (resultElem == null || resultElem.isJsonNull()) {
                Log.w(TAG, "新浪全球新闻返回数据中没有 result 字段");
                return newsList;
            }

            JsonObject data = null;
            JsonArray entries = null;
            if (resultElem.isJsonObject()) {
                JsonElement dataElem = resultElem.getAsJsonObject().get("data");
                if (dataElem != null && dataElem.isJsonObject()) {
                    data = dataElem.getAsJsonObject();
                } else if (dataElem != null && dataElem.isJsonArray()) {
                    // data 直接就是数组
                    entries = dataElem.getAsJsonArray();
                }
            } else {
                Log.w(TAG, "新浪全球新闻 result 字段不是对象，而是: " + resultElem.getClass().getSimpleName());
                return newsList;
            }

            if (data == null && entries == null) {
                Log.w(TAG, "新浪全球新闻 data 字段为空或不是对象");
                return newsList;
            }

            if (entries == null && data != null) {
                if (data.has("feed")) {
                    JsonObject feed = data.getAsJsonObject("feed");
                    if (feed != null && feed.has("entry")) {
                        entries = feed.getAsJsonArray("entry");
                    }
                } else if (data.has("entry")) {
                    entries = data.getAsJsonArray("entry");
                }
            }

            if (entries == null) {
                Log.w(TAG, "无法在新浪全球响应中找到新闻列表(entries)");
                return newsList;
            }

            for (JsonElement element : entries) {
                try {
                    JsonObject entry = element.getAsJsonObject();
                    StockNews news = new StockNews();
                    news.setNewsId("sina_global_" + getJsonString(entry, "id"));
                    news.setTitle(cleanHtmlTags(getJsonString(entry, "title")));
                    news.setSummary(cleanHtmlTags(getJsonString(entry, "summary")));
                    news.setSource(getJsonString(entry, "source", "新浪财经"));
                    news.setNewsType("全球财经");

                    String dateStr = getJsonString(entry, "published_date");
                    if (!dateStr.isEmpty()) {
                        news.setPublishTime(parseSinaDate(dateStr));
                    } else {
                        String ctime = getJsonString(entry, "ctime");
                        news.setPublishTime(!ctime.isEmpty() ? parseSinaDate(ctime) : System.currentTimeMillis());
                    }

                    if (news.getTitle() != null && !news.getTitle().isEmpty()) {
                        newsList.add(news);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "解析单条新浪全球新闻出错", e);
                }
            }
            Log.d(TAG, "成功从新浪抓取全球新闻: " + newsList.size() + " 条");
        } catch (Exception e) {
            Log.e(TAG, "解析新浪全球新闻响应数据时出错", e);
        }

        return newsList;
    }

    /**
     * 从东方财富抓取财经快讯
     *
     * 接口返回JSON格式：
     * {"data":{"fastNewsList":[
     *   {"digestType":"1","digest":[{"title":"xxx","content":"xxx","showTime":"xxx"}]},
     *   ...
     * ]}}
     */
    private List<StockNews> fetchEastMoneyNews(int limit) throws IOException {
        List<StockNews> newsList = new ArrayList<>();
        String url = String.format(EASTMONEY_NEWS_API, Math.min(limit, 20));
        Log.d(TAG, "正在从东方财富抓取快讯: " + url);

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://www.eastmoney.com")
                .get()
                .build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful() || response.body() == null) {
            Log.w(TAG, "东方财富快讯请求失败，状态码: " + (response != null ? response.code() : "unknown"));
            return newsList;
        }

        String body = response.body().string();
        Log.d(TAG, "东方财富快讯返回长度: " + body.length());
        if (body.length() > 0) {
            Log.v(TAG, "东方财富快讯响应内容(前200): " + (body.length() > 200 ? body.substring(0, 200) : body));
        }

        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonElement dataElem = root.get("data");
            if (dataElem == null || !dataElem.isJsonObject()) {
                Log.w(TAG, "东方财富快讯返回数据格式不正确: data 字段有误");
                return newsList;
            }

            JsonObject data = dataElem.getAsJsonObject();
            JsonArray fastNewsList = data.has("fastNewsList") ? data.getAsJsonArray("fastNewsList") : null;
            if (fastNewsList == null) {
                Log.w(TAG, "东方财富快讯列表为空 (fastNewsList == null)");
                return newsList;
            }

            for (JsonElement element : fastNewsList) {
                try {
                    JsonObject item = element.getAsJsonObject();
                    JsonArray digestArr = item.getAsJsonArray("digest");
                    if (digestArr == null || digestArr.size() == 0) continue;

                    JsonObject digest = digestArr.get(0).getAsJsonObject();

                    StockNews news = new StockNews();
                    news.setNewsId("em_" + getJsonString(item, "code"));

                    String title = getJsonString(digest, "title");
                    String content = getJsonString(digest, "content");

                    // 东方财富有时 title 为空，content 包含完整内容
                    if (title.isEmpty() && !content.isEmpty()) {
                        // 截取前50字符作为标题
                        title = content.length() > 50 ? content.substring(0, 50) + "..." : content;
                    }
                    news.setTitle(cleanHtmlTags(title));
                    news.setSummary(cleanHtmlTags(content));
                    news.setSource("东方财富");
                    news.setNewsType("财经快讯");

                    // 解析时间
                    String showTime = getJsonString(digest, "showTime");
                    if (!showTime.isEmpty()) {
                        news.setPublishTime(parseEastMoneyDate(showTime));
                    } else {
                        news.setPublishTime(System.currentTimeMillis());
                    }

                    if (news.getTitle() != null && !news.getTitle().isEmpty()) {
                        newsList.add(news);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "解析单条东方财富快讯出错", e);
                }
            }
            Log.d(TAG, "成功从东方财富抓取快讯: " + newsList.size() + " 条");
        } catch (Exception e) {
            Log.e(TAG, "解析东方财富响应数据时出错", e);
        }

        return newsList;
    }

    /**
     * 新闻去重（按标题相似度）
     */
    private List<StockNews> deduplicateNews(List<StockNews> newsList) {
        List<StockNews> unique = new ArrayList<>();
        for (StockNews news : newsList) {
            if (news.getTitle() == null || news.getTitle().isEmpty()) continue;
            boolean duplicate = false;
            for (StockNews existing : unique) {
                if (isSimilarTitle(news.getTitle(), existing.getTitle())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                unique.add(news);
            }
        }
        return unique;
    }

    /**
     * 判断两个标题是否相似（简单判断：前20个字是否相同）
     */
    private boolean isSimilarTitle(String title1, String title2) {
        if (title1.equals(title2)) return true;
        String s1 = title1.length() > 20 ? title1.substring(0, 20) : title1;
        String s2 = title2.length() > 20 ? title2.substring(0, 20) : title2;
        return s1.equals(s2);
    }

    // ===== 新闻过滤：只保留影响大盘的宏观新闻 =====

    // 个股新闻关键词（排除）
    private static final String[] INDIVIDUAL_STOCK_KEYWORDS = {
            "涨停", "跌停", "龙虎榜", "大宗交易",
            "年报", "季报", "半年报", "业绩预告", "业绩快报",
            "公告", "增持", "减持", "回购", "质押",
            "定增", "配股", "分红", "送转", "除权", "除息",
            "股东大会", "董事会", "监事会",
            "解禁", "限售股", "实控人", "控股股东",
            "研报", "评级", "目标价",
            "收购", "并购", "重组", "借壳", "上市申请",
            "IPO申购", "中签", "新股发行"
    };

    // 宏观/大盘新闻关键词（保留）
    private static final String[] MARKET_WIDE_KEYWORDS = {
            // 宏观政策
            "央行", "货币政策", "降准", "降息", "加息", "MLF", "LPR", "逆回购",
            "国务院", "证监会", "银保监", "财政部", "发改委", "商务部",
            "政策", "监管", "法规", "改革",
            // 大盘相关
            "大盘", "A股", "股市", "沪深", "沪指", "深指", "创业板",
            "上证", "深证", "北证", "科创板",
            "三大指数", "两市", "成交额", "成交量",
            "牛市", "熊市", "震荡", "反弹", "回调",
            "放量", "缩量", "突破", "支撑", "压力",
            // 板块/行业整体
            "板块", "行业", "赛道", "概念股", "题材",
            "半导体行业", "新能源行业", "人工智能行业",
            // 资金面
            "北向资金", "外资", "融资融券", "杠杆资金",
            "公募基金", "私募基金", "社保基金", "险资",
            "资金流入", "资金流出", "净流入", "净流出",
            // 国际
            "美联储", "美股", "纳斯达克", "道琼斯", "标普",
            "港股", "恒生", "日经", "欧股",
            "美元", "人民币", "汇率",
            "关税", "贸易战", "贸易摩擦", "制裁",
            "原油", "黄金", "大宗商品",
            "地缘", "战争", "冲突",
            // 宏观经济数据
            "GDP", "CPI", "PPI", "PMI", "社融",
            "就业", "失业率", "通胀", "通缩",
            "进出口", "贸易数据", "外贸",
            "房地产", "楼市",
            // 重大事件
            "两会", "中央经济工作会议", "政治局会议",
            "达沃斯", "G20", "APEC"
    };

    // 个股代码正则（6位数字代码如 600519、000858、300750）
    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile(
            "\\b[036]\\d{5}\\b|\\(\\d{6}\\)|（\\d{6}）|SH\\d{6}|SZ\\d{6}|sh\\d{6}|sz\\d{6}"
    );

    /**
     * 过滤新闻，只保留影响大盘的宏观/政策/国际新闻
     * 过滤逻辑：
     * 1. 标题含宏观关键词 → 保留
     * 2. 标题含个股代码/个股关键词且不含宏观关键词 → 排除
     * 3. 其他 → 保留（默认新闻源已经是财经要闻频道）
     */
    private List<StockNews> filterMarketWideNews(List<StockNews> newsList) {
        List<StockNews> filtered = new ArrayList<>();
        for (StockNews news : newsList) {
            if (isMarketWideNews(news)) {
                filtered.add(news);
            } else {
                Log.d(TAG, "过滤个股新闻: " + news.getTitle());
            }
        }
        return filtered;
    }

    /**
     * 判断是否为影响大盘的宏观类新闻
     */
    private boolean isMarketWideNews(StockNews news) {
        String title = news.getTitle();
        String summary = news.getSummary();
        if (title == null) return false;

        String combined = title + (summary != null ? summary : "");

        // 1. 检查是否包含宏观关键词（优先保留）
        boolean hasMarketKeyword = false;
        for (String keyword : MARKET_WIDE_KEYWORDS) {
            if (combined.contains(keyword)) {
                hasMarketKeyword = true;
                break;
            }
        }

        // 2. 检查是否包含个股代码
        boolean hasStockCode = STOCK_CODE_PATTERN.matcher(combined).find();

        // 3. 检查是否包含个股关键词
        boolean hasIndividualKeyword = false;
        for (String keyword : INDIVIDUAL_STOCK_KEYWORDS) {
            if (title.contains(keyword)) {
                hasIndividualKeyword = true;
                break;
            }
        }

        // 判断逻辑：
        // - 有宏观关键词：保留（即使提到个股代码，如"央行降准利好银行板块"）
        // - 有个股代码+个股关键词，无宏观关键词：排除
        // - 全球财经类型的新闻：保留
        if (hasMarketKeyword) {
            return true;
        }
        if (hasStockCode && hasIndividualKeyword) {
            return false;
        }
        if (hasIndividualKeyword && !hasMarketKeyword) {
            return false;
        }
        // 全球财经新闻默认保留
        if ("全球财经".equals(news.getNewsType())) {
            return true;
        }
        // 其余新闻保留（新闻源本身就是财经要闻频道）
        return true;
    }

    // ===== 工具方法 =====

    /**
     * 安全获取JSON字符串字段
     */
    private String getJsonString(JsonObject obj, String key) {
        return getJsonString(obj, key, "");
    }

    private String getJsonString(JsonObject obj, String key, String defaultVal) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString().trim();
        }
        return defaultVal;
    }

    /**
     * 清除HTML标签
     */
    private String cleanHtmlTags(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]+>", "").replaceAll("&[a-zA-Z]+;", " ").trim();
    }

    /**
     * 解析新浪日期格式
     * 格式样例："2026-02-22 13:30:00"  或  "02月22日 13:30"
     */
    private long parseSinaDate(String dateStr) {
        try {
            // 尝试标准格式
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA);
            return sdf.parse(dateStr).getTime();
        } catch (Exception e1) {
            try {
                // 尝试短格式（只有月日时分）
                java.text.SimpleDateFormat sdf2 = new java.text.SimpleDateFormat("MM月dd日 HH:mm", java.util.Locale.CHINA);
                java.util.Calendar cal = java.util.Calendar.getInstance();
                int year = cal.get(java.util.Calendar.YEAR);
                java.util.Date d = sdf2.parse(dateStr);
                if (d != null) {
                    cal.setTime(d);
                    cal.set(java.util.Calendar.YEAR, year);
                    return cal.getTimeInMillis();
                }
            } catch (Exception e2) {
                // ignore
            }
        }
        return System.currentTimeMillis();
    }

    /**
     * 解析东方财富日期格式
     * 格式样例："2026-02-22 13:30:00"
     */
    private long parseEastMoneyDate(String dateStr) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA);
            return sdf.parse(dateStr).getTime();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
