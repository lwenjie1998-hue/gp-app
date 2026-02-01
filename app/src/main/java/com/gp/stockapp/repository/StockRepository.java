package com.gp.stockapp.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.gp.stockapp.model.AIRecommendation;
import com.gp.stockapp.model.StockData;
import com.gp.stockapp.model.StockNews;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 股票数据仓库
 * 负责数据的存储和查询
 */
public class StockRepository {
    private static final String TAG = "StockRepository";
    private static StockRepository instance;
    
    private static final String PREF_NAME = "stock_data_prefs";
    private static final String KEY_WATCH_LIST = "watch_list";
    private static final String KEY_STOCK_DATA = "stock_data";
    private static final String KEY_NEWS_DATA = "news_data";
    private static final String KEY_RECOMMENDATIONS = "recommendations";
    
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
            instance = new StockRepository(context);
        }
        return instance;
    }
    
    // ===== 关注列表管理 =====
    
    /**
     * 获取关注列表
     */
    public List<String> getWatchList() {
        String json = preferences.getString(KEY_WATCH_LIST, "[]");
        Type type = new TypeToken<List<String>>(){}.getType();
        List<String> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }
    
    /**
     * 添加到关注列表
     */
    public void addToWatchList(String stockCode) {
        List<String> watchList = getWatchList();
        if (!watchList.contains(stockCode)) {
            watchList.add(stockCode);
            saveWatchList(watchList);
            Log.d(TAG, "Added to watch list: " + stockCode);
        }
    }
    
    /**
     * 从关注列表移除
     */
    public void removeFromWatchList(String stockCode) {
        List<String> watchList = getWatchList();
        watchList.remove(stockCode);
        saveWatchList(watchList);
        Log.d(TAG, "Removed from watch list: " + stockCode);
    }
    
    /**
     * 更新关注列表
     */
    public void updateWatchList(List<String> newWatchList) {
        saveWatchList(newWatchList);
        Log.d(TAG, "Updated watch list: " + newWatchList.size() + " stocks");
    }
    
    /**
     * 保存关注列表
     */
    private void saveWatchList(List<String> watchList) {
        String json = gson.toJson(watchList);
        preferences.edit().putString(KEY_WATCH_LIST, json).apply();
    }
    
    // ===== 股票数据管理 =====
    
    /**
     * 保存股票数据
     */
    public void saveStockData(List<StockData> stockDataList) {
        executorService.execute(() -> {
            String json = gson.toJson(stockDataList);
            preferences.edit().putString(KEY_STOCK_DATA, json).apply();
            Log.d(TAG, "Saved " + stockDataList.size() + " stock data records");
        });
    }
    
    /**
     * 获取所有股票数据
     */
    public List<StockData> getAllStockData() {
        String json = preferences.getString(KEY_STOCK_DATA, "[]");
        Type type = new TypeToken<List<StockData>>(){}.getType();
        List<StockData> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }
    
    /**
     * 获取指定股票的数据
     */
    public StockData getStockData(String stockCode) {
        List<StockData> allData = getAllStockData();
        for (StockData data : allData) {
            if (stockCode.equals(data.getStockCode())) {
                return data;
            }
        }
        return null;
    }
    
    /**
     * 获取最新股票数据（按时间戳排序）
     */
    public List<StockData> getLatestStockData(List<String> stockCodes) {
        List<StockData> allData = getAllStockData();
        List<StockData> result = new ArrayList<>();
        
        if (stockCodes != null && !stockCodes.isEmpty()) {
            for (StockData data : allData) {
                if (stockCodes.contains(data.getStockCode())) {
                    result.add(data);
                }
            }
        } else {
            result = allData;
        }
        
        // 按时间戳排序（最新的在前）
        result.sort((d1, d2) -> Long.compare(d2.getTimestamp(), d1.getTimestamp()));
        
        return result;
    }
    
    // ===== 新闻数据管理 =====
    
    /**
     * 保存新闻数据
     */
    public void saveNewsData(List<StockNews> newsList) {
        executorService.execute(() -> {
            String json = gson.toJson(newsList);
            preferences.edit().putString(KEY_NEWS_DATA, json).apply();
            Log.d(TAG, "Saved " + newsList.size() + " news records");
        });
    }
    
    /**
     * 获取所有新闻
     */
    public List<StockNews> getAllNews() {
        String json = preferences.getString(KEY_NEWS_DATA, "[]");
        Type type = new TypeToken<List<StockNews>>(){}.getType();
        List<StockNews> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }
    
    /**
     * 获取最新新闻
     */
    public List<StockNews> getLatestNews(int limit) {
        List<StockNews> allNews = getAllNews();
        
        // 按发布时间排序
        allNews.sort((n1, n2) -> Long.compare(n2.getPublishTime(), n1.getPublishTime()));
        
        // 返回前N条
        if (allNews.size() > limit) {
            return allNews.subList(0, limit);
        }
        return allNews;
    }
    
    /**
     * 获取高影响力新闻
     */
    public List<StockNews> getHighImpactNews() {
        List<StockNews> allNews = getAllNews();
        List<StockNews> highImpact = new ArrayList<>();
        
        for (StockNews news : allNews) {
            if (news.isHighImpact()) {
                highImpact.add(news);
            }
        }
        
        return highImpact;
    }
    
    // ===== 推荐数据管理 =====
    
    /**
     * 保存推荐结果
     */
    public void saveRecommendation(AIRecommendation recommendation) {
        List<AIRecommendation> recommendations = getAllRecommendations();
        
        // 移除同一股票的旧推荐
        recommendations.removeIf(r -> r.getStockCode().equals(recommendation.getStockCode()));
        
        // 添加新推荐
        recommendations.add(0, recommendation);
        
        // 只保留最近50条推荐
        if (recommendations.size() > 50) {
            recommendations = recommendations.subList(0, 50);
        }
        
        String json = gson.toJson(recommendations);
        preferences.edit().putString(KEY_RECOMMENDATIONS, json).apply();
        
        Log.d(TAG, "Saved recommendation: " + recommendation.getStockCode());
    }
    
    /**
     * 获取所有推荐
     */
    public List<AIRecommendation> getAllRecommendations() {
        String json = preferences.getString(KEY_RECOMMENDATIONS, "[]");
        Type type = new TypeToken<List<AIRecommendation>>(){}.getType();
        List<AIRecommendation> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }
    
    /**
     * 获取高置信度推荐（置信度>=80%）
     */
    public List<AIRecommendation> getHighConfidenceRecommendations() {
        List<AIRecommendation> all = getAllRecommendations();
        List<AIRecommendation> highConfidence = new ArrayList<>();
        
        for (AIRecommendation rec : all) {
            if (rec.getConfidence() >= 80.0) {
                highConfidence.add(rec);
            }
        }
        
        return highConfidence;
    }
    
    /**
     * 获取指定类型的推荐
     */
    public List<AIRecommendation> getRecommendationsByType(String type) {
        List<AIRecommendation> all = getAllRecommendations();
        List<AIRecommendation> filtered = new ArrayList<>();
        
        for (AIRecommendation rec : all) {
            if (type.equals(rec.getRecommendType())) {
                filtered.add(rec);
            }
        }
        
        return filtered;
    }
    
    // ===== 其他实用方法 =====
    
    /**
     * 清除所有数据
     */
    public void clearAllData() {
        preferences.edit().clear().apply();
        Log.d(TAG, "Cleared all data");
    }
    
    /**
     * 清除过期数据（24小时前）
     */
    public void clearExpiredData() {
        long expiryTime = System.currentTimeMillis() - 24 * 60 * 60 * 1000; // 24小时前
        
        // 清除过期的新闻
        List<StockNews> news = getAllNews();
        news.removeIf(n -> n.getPublishTime() < expiryTime);
        
        // 清除过期的推荐
        List<AIRecommendation> recs = getAllRecommendations();
        recs.removeIf(r -> r.getTimestamp() < expiryTime);
        
        saveNewsData(news);
        // 重新保存推荐
        String json = gson.toJson(recs);
        preferences.edit().putString(KEY_RECOMMENDATIONS, json).apply();
        
        Log.d(TAG, "Cleared expired data");
    }
    
    /**
     * 获取数据统计
     */
    public String getDataStatistics() {
        List<String> watchList = getWatchList();
        List<StockData> stockData = getAllStockData();
        List<StockNews> news = getAllNews();
        List<AIRecommendation> recommendations = getAllRecommendations();
        
        return String.format(
            "关注股票: %d只\n" +
            "股票数据: %d条\n" +
            "新闻数据: %d条\n" +
            "AI推荐: %d条",
            watchList.size(),
            stockData.size(),
            news.size(),
            recommendations.size()
        );
    }
}
