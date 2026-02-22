package com.gp.stockapp.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.gp.stockapp.model.MarketAnalysis;
import com.gp.stockapp.model.MarketIndex;
import com.gp.stockapp.model.StockNews;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 大盘数据仓库
 * 负责存储和查询大盘指数、AI分析结果和新闻数据
 */
public class StockRepository {
    private static final String TAG = "StockRepository";
    private static StockRepository instance;

    private static final String PREF_NAME = "market_data_prefs";
    private static final String KEY_INDICES = "market_indices";
    private static final String KEY_NEWS = "market_news";
    private static final String KEY_ANALYSIS = "market_analysis";
    private static final String KEY_ANALYSIS_HISTORY = "analysis_history";

    private SharedPreferences preferences;
    private Gson gson;
    private ExecutorService executorService;

    private StockRepository(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        executorService = Executors.newSingleThreadExecutor();
    }

    public static synchronized StockRepository getInstance(Context context) {
        if (instance == null) {
            instance = new StockRepository(context.getApplicationContext());
        }
        return instance;
    }

    // ===== 指数数据管理 =====

    /**
     * 保存大盘指数数据
     */
    public void saveMarketIndices(List<MarketIndex> indices) {
        executorService.execute(() -> {
            // 读取之前的数据，用于对比放量/缩量
            List<MarketIndex> previousIndices = getMarketIndices();
            
            // 为新数据设置前日成交额
            for (MarketIndex newIndex : indices) {
                for (MarketIndex prevIndex : previousIndices) {
                    if (newIndex.getIndexCode() != null && 
                        newIndex.getIndexCode().equals(prevIndex.getIndexCode())) {
                        newIndex.setPrevAmount(prevIndex.getAmount());
                        break;
                    }
                }
            }
            
            String json = gson.toJson(indices);
            preferences.edit().putString(KEY_INDICES, json).apply();
            Log.d(TAG, "Saved " + indices.size() + " market indices");
        });
    }

    /**
     * 获取最新的大盘指数数据
     */
    public List<MarketIndex> getMarketIndices() {
        String json = preferences.getString(KEY_INDICES, "[]");
        Type type = new TypeToken<List<MarketIndex>>() {}.getType();
        List<MarketIndex> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    /**
     * 获取指定指数
     */
    public MarketIndex getMarketIndex(String indexCode) {
        List<MarketIndex> indices = getMarketIndices();
        for (MarketIndex index : indices) {
            if (indexCode.equals(index.getIndexCode())) {
                return index;
            }
        }
        return null;
    }

    // ===== AI分析结果管理 =====

    /**
     * 保存最新AI分析结果
     */
    public void saveMarketAnalysis(MarketAnalysis analysis) {
        executorService.execute(() -> {
            // 保存最新分析
            String json = gson.toJson(analysis);
            preferences.edit().putString(KEY_ANALYSIS, json).apply();

            // 追加到历史记录
            List<MarketAnalysis> history = getAnalysisHistory();
            history.add(0, analysis);
            if (history.size() > 20) {
                history = history.subList(0, 20);
            }
            String historyJson = gson.toJson(history);
            preferences.edit().putString(KEY_ANALYSIS_HISTORY, historyJson).apply();

            Log.d(TAG, "Saved market analysis, sentiment: " + analysis.getMarketSentiment());
        });
    }

    /**
     * 获取最新AI分析结果
     */
    public MarketAnalysis getLatestMarketAnalysis() {
        String json = preferences.getString(KEY_ANALYSIS, null);
        if (json == null || json.isEmpty()) return null;
        try {
            return gson.fromJson(json, MarketAnalysis.class);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing market analysis", e);
            return null;
        }
    }

    /**
     * 获取分析历史
     */
    public List<MarketAnalysis> getAnalysisHistory() {
        String json = preferences.getString(KEY_ANALYSIS_HISTORY, "[]");
        Type type = new TypeToken<List<MarketAnalysis>>() {}.getType();
        List<MarketAnalysis> list = gson.fromJson(json, type);
        return list != null ? new ArrayList<>(list) : new ArrayList<>();
    }

    // ===== 新闻数据管理 =====

    /**
     * 保存新闻数据
     */
    public void saveNewsData(List<StockNews> newsList) {
        executorService.execute(() -> {
            String json = gson.toJson(newsList);
            preferences.edit().putString(KEY_NEWS, json).apply();
            Log.d(TAG, "Saved " + newsList.size() + " news records");
        });
    }

    /**
     * 获取所有新闻
     */
    public List<StockNews> getAllNews() {
        String json = preferences.getString(KEY_NEWS, "[]");
        Type type = new TypeToken<List<StockNews>>() {}.getType();
        List<StockNews> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    /**
     * 获取最新新闻
     */
    public List<StockNews> getLatestNews(int limit) {
        List<StockNews> allNews = getAllNews();
        allNews.sort((n1, n2) -> Long.compare(n2.getPublishTime(), n1.getPublishTime()));
        if (allNews.size() > limit) {
            return allNews.subList(0, limit);
        }
        return allNews;
    }

    // ===== 清理 =====

    /**
     * 清除所有数据
     */
    public void clearAllData() {
        preferences.edit().clear().apply();
        Log.d(TAG, "All data cleared");
    }

    /**
     * 获取数据统计
     */
    public String getDataStatistics() {
        List<MarketIndex> indices = getMarketIndices();
        List<StockNews> news = getAllNews();
        List<MarketAnalysis> history = getAnalysisHistory();

        return String.format(
                "指数数据: %d条\n新闻数据: %d条\nAI分析: %d条",
                indices.size(), news.size(), history.size()
        );
    }
}
