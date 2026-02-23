package com.gp.stockapp.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.gp.stockapp.model.HotStockData;
import com.gp.stockapp.model.MarketAnalysis;
import com.gp.stockapp.model.MarketIndex;
import com.gp.stockapp.model.StockNews;
import com.gp.stockapp.model.StrategyRecommendation;
import com.gp.stockapp.utils.TradingDayHelper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final String KEY_PREV_DAY_AMOUNTS = "prev_day_amounts";
    private static final String KEY_LAST_TRADING_DATE = "last_trading_date";
    private static final String KEY_NEWS = "market_news";
    private static final String KEY_ANALYSIS = "market_analysis";
    private static final String KEY_ANALYSIS_HISTORY = "analysis_history";
    private static final String KEY_SECTOR_RECOMMENDATION = "sector_recommendation";
    private static final String KEY_AUCTION_RECOMMENDATION = "auction_recommendation";
    private static final String KEY_CLOSING_RECOMMENDATION = "closing_recommendation";
    private static final String KEY_HOT_STOCK_DATA = "hot_stock_data";

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
     * 自动处理前日成交额对比（用于放量/缩量判断）
     * 
     * 核心逻辑：
     * 1. 判断当前数据属于哪个交易日（非交易日时API返回的是最后一个交易日的数据）
     * 2. 如果是新的交易日，将旧交易日的成交额存为"前日成交额"
     * 3. prevAmount 始终为上一个交易日的最终成交额
     */
    public void saveMarketIndices(List<MarketIndex> indices) {
        executorService.execute(() -> {
            String lastSavedTradingDay = preferences.getString(KEY_LAST_TRADING_DATE, "");
            
            // 确定当前数据属于哪个交易日
            // 非交易日（周末/节假日）API返回的数据仍然是最后一个交易日的
            String currentTradingDay = TradingDayHelper.getLatestTradingDayStr();
            
            // 获取存储的前一交易日成交额
            Map<String, Double> prevDayAmounts = getPrevDayAmounts();
            
            if (!currentTradingDay.equals(lastSavedTradingDay) && !lastSavedTradingDay.isEmpty()) {
                // 交易日变化了 → 把上一个交易日的成交额存为"前日成交额"
                List<MarketIndex> lastIndices = getMarketIndices();
                for (MarketIndex lastIndex : lastIndices) {
                    if (lastIndex.getIndexCode() != null && lastIndex.getAmount() > 0) {
                        prevDayAmounts.put(lastIndex.getIndexCode(), lastIndex.getAmount());
                    }
                }
                savePrevDayAmounts(prevDayAmounts);
                Log.d(TAG, "Trading day changed: " + lastSavedTradingDay + " -> " + currentTradingDay 
                        + ", saved prev amounts: " + prevDayAmounts);
            }
            
            // 为新数据设置前日成交额（用于放量/缩量显示）
            for (MarketIndex newIndex : indices) {
                if (newIndex.getIndexCode() != null && prevDayAmounts.containsKey(newIndex.getIndexCode())) {
                    newIndex.setPrevAmount(prevDayAmounts.get(newIndex.getIndexCode()));
                }
            }
            
            // 保存当前数据和交易日标记
            String json = gson.toJson(indices);
            preferences.edit()
                    .putString(KEY_INDICES, json)
                    .putString(KEY_LAST_TRADING_DATE, currentTradingDay)
                    .apply();
            Log.d(TAG, "Saved " + indices.size() + " market indices for trading day: " + currentTradingDay);
        });
    }

    /**
     * 获取存储的前日各指数成交额
     */
    private Map<String, Double> getPrevDayAmounts() {
        String json = preferences.getString(KEY_PREV_DAY_AMOUNTS, "{}");
        try {
            Type type = new TypeToken<Map<String, Double>>() {}.getType();
            Map<String, Double> map = gson.fromJson(json, type);
            return map != null ? map : new HashMap<>();
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * 保存前日各指数成交额
     */
    private void savePrevDayAmounts(Map<String, Double> amounts) {
        String json = gson.toJson(amounts);
        preferences.edit().putString(KEY_PREV_DAY_AMOUNTS, json).apply();
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

    // ===== 策略推荐管理 =====

    /**
     * 保存板块推荐
     */
    public void saveSectorRecommendation(StrategyRecommendation recommendation) {
        executorService.execute(() -> {
            String json = gson.toJson(recommendation);
            preferences.edit().putString(KEY_SECTOR_RECOMMENDATION, json).apply();
            Log.d(TAG, "Saved sector recommendation");
        });
    }

    /**
     * 获取板块推荐
     */
    public StrategyRecommendation getSectorRecommendation() {
        String json = preferences.getString(KEY_SECTOR_RECOMMENDATION, null);
        if (json == null || json.isEmpty()) return null;
        try {
            return gson.fromJson(json, StrategyRecommendation.class);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing sector recommendation", e);
            return null;
        }
    }

    /**
     * 保存开盘竞价推荐（游资策略）
     */
    public void saveAuctionRecommendation(StrategyRecommendation recommendation) {
        executorService.execute(() -> {
            String json = gson.toJson(recommendation);
            preferences.edit().putString(KEY_AUCTION_RECOMMENDATION, json).apply();
            Log.d(TAG, "Saved auction recommendation");
        });
    }

    /**
     * 获取开盘竞价推荐
     */
    public StrategyRecommendation getAuctionRecommendation() {
        String json = preferences.getString(KEY_AUCTION_RECOMMENDATION, null);
        if (json == null || json.isEmpty()) return null;
        try {
            return gson.fromJson(json, StrategyRecommendation.class);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing auction recommendation", e);
            return null;
        }
    }

    /**
     * 保存尾盘推荐
     */
    public void saveClosingRecommendation(StrategyRecommendation recommendation) {
        executorService.execute(() -> {
            String json = gson.toJson(recommendation);
            preferences.edit().putString(KEY_CLOSING_RECOMMENDATION, json).apply();
            Log.d(TAG, "Saved closing recommendation");
        });
    }

    /**
     * 获取尾盘推荐
     */
    public StrategyRecommendation getClosingRecommendation() {
        String json = preferences.getString(KEY_CLOSING_RECOMMENDATION, null);
        if (json == null || json.isEmpty()) return null;
        try {
            return gson.fromJson(json, StrategyRecommendation.class);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing closing recommendation", e);
            return null;
        }
    }

    // ===== 热门股票数据管理 =====

    /**
     * 保存热门股票数据（龙虎榜、涨停板等）
     */
    public void saveHotStockData(HotStockData data) {
        executorService.execute(() -> {
            String json = gson.toJson(data);
            preferences.edit().putString(KEY_HOT_STOCK_DATA, json).apply();
            Log.d(TAG, "Saved hot stock data");
        });
    }

    /**
     * 获取热门股票数据
     */
    public HotStockData getHotStockData() {
        String json = preferences.getString(KEY_HOT_STOCK_DATA, null);
        if (json == null || json.isEmpty()) return null;
        try {
            return gson.fromJson(json, HotStockData.class);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing hot stock data", e);
            return null;
        }
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
