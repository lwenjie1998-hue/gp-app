package com.gp.stockapp.api;

import android.util.Log;

import com.gp.stockapp.model.MarketIndex;
import com.gp.stockapp.model.StockNews;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    // 市场新闻API（可配置）
    private static final String NEWS_API = "https://api.example.com/market/news";

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

        try {
            // 构建请求URL：同时请求三大指数
            String codes = String.join(",", INDEX_CODES);
            String url = SINA_API + codes;

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            Response response = client.newCall(request).execute();

            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                indices = parseSinaResponse(responseBody);
                Log.d(TAG, "Fetched " + indices.size() + " market indices");
            }

        } catch (IOException e) {
            Log.e(TAG, "Error fetching market indices", e);
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
     * 抓取市场新闻
     */
    public List<StockNews> fetchMarketNews() {
        return fetchMarketNews(10);
    }

    /**
     * 抓取指定数量的市场新闻
     */
    public List<StockNews> fetchMarketNews(int limit) {
        try {
            String url = NEWS_API + "?limit=" + limit;

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            Response response = client.newCall(request).execute();

            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                Type listType = new TypeToken<List<StockNews>>() {}.getType();
                List<StockNews> newsList = gson.fromJson(responseBody, listType);
                Log.d(TAG, "Fetched " + (newsList != null ? newsList.size() : 0) + " news");
                return newsList != null ? newsList : new ArrayList<>();
            }

        } catch (IOException e) {
            Log.e(TAG, "Error fetching market news", e);
        }

        return new ArrayList<>();
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
