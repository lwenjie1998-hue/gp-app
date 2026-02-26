package com.gp.stockapp.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.gp.stockapp.db.AppDatabase;
import com.gp.stockapp.db.ContinuousLimitDao;
import com.gp.stockapp.db.ContinuousLimitEntity;
import com.gp.stockapp.db.DragonTigerDao;
import com.gp.stockapp.db.DragonTigerEntity;
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
 * 
 * 优化：添加内存缓存层，减少 SharedPreferences 读取次数
 */
public class StockRepository {
    private static final String TAG = "StockRepository";
    private static volatile StockRepository instance;

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
    private static final String KEY_PREV_DAY_HOT_STOCK_DATA = "prev_day_hot_stock_data";

    // 缓存过期时间（毫秒）
    private static final long CACHE_TTL = 60_000; // 1分钟

    private final SharedPreferences preferences;
    private final Gson gson;
    private final ExecutorService executorService;
    private final AppDatabase appDatabase;
    private final DragonTigerDao dragonTigerDao;
    private final ContinuousLimitDao continuousLimitDao;

    // ===== 内存缓存 =====
    private volatile List<MarketIndex> indicesCache;
    private volatile long indicesCacheTime = 0;
    
    private volatile List<StockNews> newsCache;
    private volatile long newsCacheTime = 0;
    
    private volatile MarketAnalysis analysisCache;
    private volatile long analysisCacheTime = 0;
    
    private volatile StrategyRecommendation sectorCache;
    private volatile long sectorCacheTime = 0;
    
    private volatile StrategyRecommendation auctionCache;
    private volatile long auctionCacheTime = 0;
    
    private volatile StrategyRecommendation closingCache;
    private volatile long closingCacheTime = 0;
    
    private volatile HotStockData hotStockCache;
    private volatile long hotStockCacheTime = 0;
    
    private volatile HotStockData prevDayHotStockCache;
    private volatile long prevDayHotStockCacheTime = 0;

    private StockRepository(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        executorService = Executors.newSingleThreadExecutor();
        appDatabase = AppDatabase.getInstance(context);
        dragonTigerDao = appDatabase.dragonTigerDao();
        continuousLimitDao = appDatabase.continuousLimitDao();
    }

    public static StockRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (StockRepository.class) {
                if (instance == null) {
                    instance = new StockRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * 检查缓存是否有效
     */
    private boolean isCacheValid(long cacheTime) {
        return System.currentTimeMillis() - cacheTime < CACHE_TTL;
    }

    /**
     * 清除所有内存缓存
     */
    public void clearMemoryCache() {
        indicesCache = null;
        indicesCacheTime = 0;
        newsCache = null;
        newsCacheTime = 0;
        analysisCache = null;
        analysisCacheTime = 0;
        sectorCache = null;
        sectorCacheTime = 0;
        auctionCache = null;
        auctionCacheTime = 0;
        closingCache = null;
        closingCacheTime = 0;
        hotStockCache = null;
        hotStockCacheTime = 0;
        prevDayHotStockCache = null;
        prevDayHotStockCacheTime = 0;
        Log.d(TAG, "Memory cache cleared");
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
     * 获取最新的大盘指数数据（带缓存）
     */
    public List<MarketIndex> getMarketIndices() {
        // 检查缓存
        if (indicesCache != null && isCacheValid(indicesCacheTime)) {
            return new ArrayList<>(indicesCache);
        }
        
        // 从持久化存储加载
        String json = preferences.getString(KEY_INDICES, "[]");
        Type type = new TypeToken<List<MarketIndex>>() {}.getType();
        List<MarketIndex> list = gson.fromJson(json, type);
        list = list != null ? list : new ArrayList<>();
        
        // 更新缓存
        indicesCache = new ArrayList<>(list);
        indicesCacheTime = System.currentTimeMillis();
        
        return list;
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
        // 更新内存缓存
        analysisCache = analysis;
        analysisCacheTime = System.currentTimeMillis();
        
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
     * 获取最新AI分析结果（带缓存）
     */
    public MarketAnalysis getLatestMarketAnalysis() {
        // 检查缓存
        if (analysisCache != null && isCacheValid(analysisCacheTime)) {
            return analysisCache;
        }
        
        String json = preferences.getString(KEY_ANALYSIS, null);
        if (json == null || json.isEmpty()) return null;
        try {
            analysisCache = gson.fromJson(json, MarketAnalysis.class);
            analysisCacheTime = System.currentTimeMillis();
            return analysisCache;
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
        // 更新内存缓存
        sectorCache = recommendation;
        sectorCacheTime = System.currentTimeMillis();
        
        executorService.execute(() -> {
            String json = gson.toJson(recommendation);
            preferences.edit().putString(KEY_SECTOR_RECOMMENDATION, json).apply();
            Log.d(TAG, "Saved sector recommendation");
        });
    }

    /**
     * 获取板块推荐（带缓存）
     */
    public StrategyRecommendation getSectorRecommendation() {
        if (sectorCache != null && isCacheValid(sectorCacheTime)) {
            return sectorCache;
        }
        
        String json = preferences.getString(KEY_SECTOR_RECOMMENDATION, null);
        if (json == null || json.isEmpty()) return null;
        try {
            sectorCache = gson.fromJson(json, StrategyRecommendation.class);
            sectorCacheTime = System.currentTimeMillis();
            return sectorCache;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing sector recommendation", e);
            return null;
        }
    }

    /**
     * 保存开盘竞价推荐（游资策略）
     */
    public void saveAuctionRecommendation(StrategyRecommendation recommendation) {
        // 更新内存缓存
        auctionCache = recommendation;
        auctionCacheTime = System.currentTimeMillis();
        
        executorService.execute(() -> {
            String json = gson.toJson(recommendation);
            preferences.edit().putString(KEY_AUCTION_RECOMMENDATION, json).apply();
            Log.d(TAG, "Saved auction recommendation");
        });
    }

    /**
     * 获取开盘竞价推荐（带缓存）
     */
    public StrategyRecommendation getAuctionRecommendation() {
        if (auctionCache != null && isCacheValid(auctionCacheTime)) {
            return auctionCache;
        }
        
        String json = preferences.getString(KEY_AUCTION_RECOMMENDATION, null);
        if (json == null || json.isEmpty()) return null;
        try {
            auctionCache = gson.fromJson(json, StrategyRecommendation.class);
            auctionCacheTime = System.currentTimeMillis();
            return auctionCache;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing auction recommendation", e);
            return null;
        }
    }

    /**
     * 保存尾盘推荐
     */
    public void saveClosingRecommendation(StrategyRecommendation recommendation) {
        // 更新内存缓存
        closingCache = recommendation;
        closingCacheTime = System.currentTimeMillis();
        
        executorService.execute(() -> {
            String json = gson.toJson(recommendation);
            preferences.edit().putString(KEY_CLOSING_RECOMMENDATION, json).apply();
            Log.d(TAG, "Saved closing recommendation");
        });
    }

    /**
     * 获取尾盘推荐（带缓存）
     */
    public StrategyRecommendation getClosingRecommendation() {
        if (closingCache != null && isCacheValid(closingCacheTime)) {
            return closingCache;
        }
        
        String json = preferences.getString(KEY_CLOSING_RECOMMENDATION, null);
        if (json == null || json.isEmpty()) return null;
        try {
            closingCache = gson.fromJson(json, StrategyRecommendation.class);
            closingCacheTime = System.currentTimeMillis();
            return closingCache;
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
        // 更新内存缓存
        hotStockCache = data;
        hotStockCacheTime = System.currentTimeMillis();
        
        executorService.execute(() -> {
            String json = gson.toJson(data);
            preferences.edit().putString(KEY_HOT_STOCK_DATA, json).apply();
            Log.d(TAG, "Saved hot stock data");
        });
    }

    /**
     * 保存前一个交易日的热门股票数据（竞价策略专用）
     */
    public void savePrevDayHotStockData(HotStockData data) {
        // 更新内存缓存
        prevDayHotStockCache = data;
        prevDayHotStockCacheTime = System.currentTimeMillis();
        
        executorService.execute(() -> {
            String json = gson.toJson(data);
            preferences.edit().putString(KEY_PREV_DAY_HOT_STOCK_DATA, json).apply();
            Log.d(TAG, "Saved previous day hot stock data");
        });
    }

    /**
     * 获取热门股票数据（当天，带缓存）
     */
    public HotStockData getHotStockData() {
        if (hotStockCache != null && isCacheValid(hotStockCacheTime)) {
            return hotStockCache;
        }
        
        String json = preferences.getString(KEY_HOT_STOCK_DATA, null);
        if (json == null || json.isEmpty()) return null;
        try {
            hotStockCache = gson.fromJson(json, HotStockData.class);
            hotStockCacheTime = System.currentTimeMillis();
            return hotStockCache;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing hot stock data", e);
            return null;
        }
    }

    /**
     * 获取前一个交易日的热门股票数据（竞价策略专用，带缓存）
     */
    public HotStockData getPrevDayHotStockData() {
        if (prevDayHotStockCache != null && isCacheValid(prevDayHotStockCacheTime)) {
            return prevDayHotStockCache;
        }
        
        String json = preferences.getString(KEY_PREV_DAY_HOT_STOCK_DATA, null);
        if (json == null || json.isEmpty()) return null;
        try {
            prevDayHotStockCache = gson.fromJson(json, HotStockData.class);
            prevDayHotStockCacheTime = System.currentTimeMillis();
            return prevDayHotStockCache;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing prev day hot stock data", e);
            return null;
        }
    }

    // ===== 新闻数据管理 =====

    /**
     * 保存新闻数据（直接覆盖）
     */
    public void saveNewsData(List<StockNews> newsList) {
        // 更新内存缓存
        newsCache = new ArrayList<>(newsList);
        newsCacheTime = System.currentTimeMillis();
        
        executorService.execute(() -> {
            String json = gson.toJson(newsList);
            preferences.edit().putString(KEY_NEWS, json).apply();
            Log.d(TAG, "Saved " + newsList.size() + " news records");
        });
    }

    /**
     * 合并保存新闻数据
     * 新获取的新闻放在最前面，与已有新闻合并去重，只保留指定条数
     * @param newNewsList 新获取的新闻列表
     * @param maxCount 最大保留条数
     */
    public void mergeAndSaveNews(List<StockNews> newNewsList, int maxCount) {
        // 更新内存缓存
        newsCache = null; // 清除缓存，强制重新加载
        
        executorService.execute(() -> {
            // 获取已有新闻
            List<StockNews> existingNews = getAllNewsInternal();
            
            // 合并：新的在前
            List<StockNews> merged = new ArrayList<>(newNewsList);
            
            // 添加旧新闻（去重，按标题判断）
            java.util.Set<String> newTitles = new java.util.HashSet<>();
            for (StockNews news : newNewsList) {
                if (news.getTitle() != null) {
                    newTitles.add(news.getTitle().trim());
                }
            }
            for (StockNews oldNews : existingNews) {
                if (oldNews.getTitle() != null && !newTitles.contains(oldNews.getTitle().trim())) {
                    merged.add(oldNews);
                }
            }
            
            // 按发布时间降序排序（最新的在前）
            merged.sort((a, b) -> Long.compare(b.getPublishTime(), a.getPublishTime()));
            
            // 只保留 maxCount 条
            if (merged.size() > maxCount) {
                merged = new ArrayList<>(merged.subList(0, maxCount));
            }
            
            // 更新内存缓存
            newsCache = new ArrayList<>(merged);
            newsCacheTime = System.currentTimeMillis();
            
            String json = gson.toJson(merged);
            preferences.edit().putString(KEY_NEWS, json).apply();
            Log.d(TAG, "Merged and saved " + merged.size() + " news records (new: " + newNewsList.size() + ", existing: " + existingNews.size() + ")");
        });
    }

    /**
     * 获取所有新闻（内部方法，不带缓存）
     */
    private List<StockNews> getAllNewsInternal() {
        String json = preferences.getString(KEY_NEWS, "[]");
        Type type = new TypeToken<List<StockNews>>() {}.getType();
        List<StockNews> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    /**
     * 获取所有新闻（带缓存）
     */
    public List<StockNews> getAllNews() {
        if (newsCache != null && isCacheValid(newsCacheTime)) {
            return new ArrayList<>(newsCache);
        }
        
        newsCache = getAllNewsInternal();
        newsCacheTime = System.currentTimeMillis();
        return new ArrayList<>(newsCache);
    }

    /**
     * 获取最新新闻（带缓存）
     */
    public List<StockNews> getLatestNews(int limit) {
        List<StockNews> allNews = getAllNews();
        allNews.sort((n1, n2) -> Long.compare(n2.getPublishTime(), n1.getPublishTime()));
        if (allNews.size() > limit) {
            return new ArrayList<>(allNews.subList(0, limit));
        }
        return allNews;
    }

    // ===== 清理 =====

    /**
     * 清除所有数据
     */
    public void clearAllData() {
        clearMemoryCache();
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
    
    // ===== 龙虎榜历史数据管理 =====
    
    /**
     * 获取指定日期的龙虎榜数据
     */
    public List<DragonTigerEntity> getDragonTigerByDate(String tradeDate) {
        return dragonTigerDao.getByDate(tradeDate);
    }
    
    /**
     * 获取最近N天的龙虎榜数据
     */
    public List<DragonTigerEntity> getDragonTigerRecentDays(String startDate) {
        return dragonTigerDao.getRecentDays(startDate);
    }
    
    /**
     * 获取指定股票的历史龙虎榜记录
     */
    public List<DragonTigerEntity> getDragonTigerByCode(String code) {
        return dragonTigerDao.getByCode(code);
    }
    
    /**
     * 获取所有有龙虎榜数据的交易日
     */
    public List<String> getAllDragonTigerDates() {
        return dragonTigerDao.getAllTradeDates();
    }
    
    /**
     * 获取最新的龙虎榜交易日期
     */
    public String getLatestDragonTigerDate() {
        return dragonTigerDao.getLatestTradeDate();
    }
    
    /**
     * 获取龙虎榜数据总条数
     */
    public int getDragonTigerCount() {
        return dragonTigerDao.getCount();
    }
    
    // ===== 连板股历史数据管理 =====
    
    /**
     * 获取指定日期的连板股数据
     */
    public List<ContinuousLimitEntity> getContinuousLimitByDate(String tradeDate) {
        return continuousLimitDao.getByDate(tradeDate);
    }
    
    /**
     * 获取最近N天的连板股数据
     */
    public List<ContinuousLimitEntity> getContinuousLimitRecentDays(String startDate) {
        return continuousLimitDao.getRecentDays(startDate);
    }
    
    /**
     * 获取指定股票的历史连板记录
     */
    public List<ContinuousLimitEntity> getContinuousLimitByCode(String code) {
        return continuousLimitDao.getByCode(code);
    }
    
    /**
     * 获取所有有连板股数据的交易日
     */
    public List<String> getAllContinuousLimitDates() {
        return continuousLimitDao.getAllTradeDates();
    }
    
    /**
     * 获取最新的连板股交易日期
     */
    public String getLatestContinuousLimitDate() {
        return continuousLimitDao.getLatestTradeDate();
    }
    
    /**
     * 获取连板股数据总条数
     */
    public int getContinuousLimitCount() {
        return continuousLimitDao.getCount();
    }
}
