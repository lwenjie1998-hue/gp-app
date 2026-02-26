package com.gp.stockapp.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.gp.stockapp.MainActivity;
import com.gp.stockapp.R;
import com.gp.stockapp.api.GLM4Client;
import com.gp.stockapp.api.HotStockApi;
import com.gp.stockapp.api.MarketApi;
import com.gp.stockapp.db.AppDatabase;
import com.gp.stockapp.db.ContinuousLimitDao;
import com.gp.stockapp.db.ContinuousLimitEntity;
import com.gp.stockapp.db.DragonTigerDao;
import com.gp.stockapp.db.DragonTigerEntity;
import com.gp.stockapp.model.HotStockData;
import com.gp.stockapp.model.MarketIndex;
import com.gp.stockapp.model.StockNews;
import com.gp.stockapp.repository.StockRepository;
import com.gp.stockapp.utils.TradingDayHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 大盘数据抓取服务
 * 循环抓取三大指数实时数据和市场新闻
 * 
 * 优化：使用 ScheduledExecutorService 替代 Timer，提高稳定性和性能
 */
public class StockDataService extends Service {
    private static final String TAG = "StockDataService";
    private static final String CHANNEL_ID = "MarketDataChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final long FETCH_INTERVAL = 60000; // 1分钟刷新一次

    private StockRepository stockRepository;
    private MarketApi marketApi;
    private HotStockApi hotStockApi;
    private AppDatabase appDatabase;
    private DragonTigerDao dragonTigerDao;
    private ContinuousLimitDao continuousLimitDao;
    private ScheduledExecutorService scheduler;
    private long lastHotDataFetchTime = 0;
    private static final long HOT_DATA_FETCH_INTERVAL = 300000; // 热门数据5分钟抓取一次
    private volatile boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "StockDataService created");

        stockRepository = StockRepository.getInstance(getApplicationContext());
        marketApi = MarketApi.getInstance();
        hotStockApi = HotStockApi.getInstance();
        appDatabase = AppDatabase.getInstance(getApplicationContext());
        dragonTigerDao = appDatabase.dragonTigerDao();
        continuousLimitDao = appDatabase.continuousLimitDao();
        // 使用单线程调度器，更稳定可靠
        scheduler = Executors.newSingleThreadScheduledExecutor();

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "StockDataService started");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification("正在获取大盘数据..."),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, createNotification("正在获取大盘数据..."));
        }
        startDataFetching();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "StockDataService destroyed");
        
        // 先停止数据抓取
        stopDataFetching();
        
        // 关闭调度器
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // 停止前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        
        Log.d(TAG, "StockDataService stopped completely");
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "大盘数据监控",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("实时抓取大盘指数数据");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_gp_tool_logo)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void startDataFetching() {
        if (isRunning) return;

        isRunning = true;

        // 立即在后台线程执行一次（避免主线程网络操作）
        scheduler.submit(() -> {
            try {
                fetchMarketData();
            } catch (Exception e) {
                Log.e(TAG, "Error in initial fetch task", e);
            }
        });

        // 使用 ScheduledExecutorService 定时执行
        scheduler.scheduleAtFixedRate(() -> {
            try {
                fetchMarketData();
            } catch (Exception e) {
                Log.e(TAG, "Error in scheduled fetch task", e);
            }
        }, FETCH_INTERVAL, FETCH_INTERVAL, TimeUnit.MILLISECONDS);

        Log.d(TAG, "Data fetching started, interval: " + FETCH_INTERVAL + "ms");
    }

    private void stopDataFetching() {
        isRunning = false;
        Log.d(TAG, "Data fetching stopped");
    }

    /**
     * 抓取大盘数据
     */
    private void fetchMarketData() {
        Log.d(TAG, "==== 开始执行定时数据抓取任务 ====");
        // 直接在调度器线程中执行，无需额外的线程池
        try {
            // 抓取三大指数数据
            Log.d(TAG, "正在抓取大盘指数...");
            List<MarketIndex> indices = marketApi.fetchMarketIndices();
            if (indices != null && !indices.isEmpty()) {
                stockRepository.saveMarketIndices(indices);

                // 更新通知
                updateNotification(indices);

                // 通知UI刷新
                sendBroadcast(MainActivity.ACTION_DATA_UPDATED);

                Log.d(TAG, "成功更新大盘指数: " + indices.size() + " 条记录");
            } else {
                Log.w(TAG, "抓取大盘指数失败或列表为空");
            }

            // 抓取市场新闻
            Log.d(TAG, "正在抓取市场要闻...");
            List<StockNews> newsList = marketApi.fetchMarketNews();
            if (newsList != null && !newsList.isEmpty()) {
                // 用AI为重大新闻推荐相关A股股票并标记重要性
                enrichNewsWithStockRecommendations(newsList);
                
                // 只保留AI判定为重大新闻的（importance >= 3）
                List<StockNews> majorNews = new ArrayList<>();
                for (StockNews news : newsList) {
                    if (news.getImportance() >= 3) {
                        majorNews.add(news);
                    }
                }
                Log.d(TAG, "AI筛选重大新闻: " + majorNews.size() + "/" + newsList.size() + " 条");
                
                if (!majorNews.isEmpty()) {
                    // 合并保存（新的在前，保留最多10条）
                    stockRepository.mergeAndSaveNews(majorNews, 10);
                    Log.d(TAG, "成功更新市场要闻: 筛选并保存了 " + majorNews.size() + " 条重大新闻");
                } else {
                    Log.d(TAG, "本次无重大新闻");
                }
            } else {
                Log.w(TAG, "市场要闻抓取结果为空（可能所有源都失败了）");
            }

            // 抓取热门股票数据（龙虎榜、涨停板、连板股、活跃股）
            // 每5分钟抓取一次，避免频繁请求
            long now = System.currentTimeMillis();
            if (now - lastHotDataFetchTime >= HOT_DATA_FETCH_INTERVAL) {
                Log.d(TAG, "正在抓取热门股票数据（龙虎榜/涨停板/连板/活跃股）...");
                try {
                    // 当天数据（尾盘策略用）
                    // 龙虎榜每天16点更新，16点前需要用前一天的日期
                    Calendar cal = Calendar.getInstance();
                    int hour = cal.get(Calendar.HOUR_OF_DAY);
                    String todayStr = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.CHINA)
                            .format(cal.getTime());
                    String dateStr;
                    
                    if (hour < 16) {
                        // 16点前，龙虎榜数据还未更新，使用前一个交易日
                        dateStr = TradingDayHelper.getPreviousTradingDayStr(cal.getTime());
                        Log.d(TAG, "当前时间: " + hour + "点, 今日: " + todayStr + ", 龙虎榜日期: " + dateStr);
                    } else {
                        dateStr = todayStr;
                        Log.d(TAG, "当前时间: " + hour + "点, 使用当日龙虎榜日期: " + dateStr);
                    }
                    
                    HotStockData hotData = hotStockApi.fetchAllHotData(dateStr);
                    
                    // 如果当天数据为空，尝试往前找有数据的日子
                    if (hotData == null || (hotData.getDragonTigerList() != null && hotData.getDragonTigerList().isEmpty())) {
                        Log.w(TAG, "龙虎榜数据为空，尝试查找更早的交易日...");
                        for (int i = 0; i < 5; i++) {  // 最多往前找5天
                            String olderDate = TradingDayHelper.getPreviousTradingDayStr(
                                    TradingDayHelper.parseDate(dateStr));
                            if (olderDate != null && !olderDate.isEmpty()) {
                                Log.d(TAG, "尝试日期: " + olderDate);
                                HotStockData olderData = hotStockApi.fetchAllHotData(olderDate);
                                if (olderData != null && olderData.getDragonTigerList() != null 
                                        && !olderData.getDragonTigerList().isEmpty()) {
                                    hotData = olderData;
                                    dateStr = olderDate;
                                    Log.d(TAG, "找到有效龙虎榜数据: " + olderDate);
                                    break;
                                }
                                dateStr = olderDate;
                            }
                        }
                    }
                    
                    if (hotData != null) {
                        stockRepository.saveHotStockData(hotData);
                        // 保存龙虎榜和连板股数据到数据库
                        saveDragonTigerToDatabase(hotData, dateStr);
                        saveContinuousLimitToDatabase(hotData, dateStr);
                        Log.d(TAG, "成功更新当天热门股票数据, 龙虎榜: " + 
                                (hotData.getDragonTigerList() != null ? hotData.getDragonTigerList().size() : 0) + " 条");
                    }
                    
                    // 前一个交易日数据（竞价策略用）
                    String prevDateStr = TradingDayHelper.getPreviousTradingDayStr(
                            TradingDayHelper.parseDate(dateStr));
                    if (prevDateStr != null && !prevDateStr.isEmpty()) {
                        HotStockData prevHotData = hotStockApi.fetchAllHotData(prevDateStr);
                        if (prevHotData != null) {
                            stockRepository.savePrevDayHotStockData(prevHotData);
                            // 同时保存前一日的龙虎榜和连板股到数据库
                            saveDragonTigerToDatabase(prevHotData, prevDateStr);
                            saveContinuousLimitToDatabase(prevHotData, prevDateStr);
                            Log.d(TAG, "成功更新前一交易日热门股票数据: " + prevDateStr);
                        }
                    }
                    
                    lastHotDataFetchTime = now;
                } catch (Exception e) {
                    Log.e(TAG, "抓取热门股票数据失败", e);
                }
            }

            Log.d(TAG, "==== 定时数据抓取任务完成 ====");
        } catch (Exception e) {
            Log.e(TAG, "定时抓取大盘数据时发生严重错误", e);
        }
    }

    /**
     * 用AI为重大新闻推荐相关A股股票
     * 将所有新闻标题批量发送给GLM-4，由AI判断哪些是重大新闻并推荐相关股票
     */
    private void enrichNewsWithStockRecommendations(List<StockNews> newsList) {
        GLM4Client glm4Client = GLM4Client.getInstance();
        if (glm4Client == null) {
            Log.w(TAG, "GLM4Client未初始化，跳过新闻股票推荐");
            return;
        }

        try {
            // 构建新闻列表prompt
            StringBuilder newsText = new StringBuilder();
            for (int i = 0; i < newsList.size(); i++) {
                StockNews news = newsList.get(i);
                newsText.append(i + 1).append(". ")
                        .append(news.getTitle());
                if (news.getSummary() != null && !news.getSummary().isEmpty()) {
                    String summary = news.getSummary().length() > 80 
                            ? news.getSummary().substring(0, 80) + "..." 
                            : news.getSummary();
                    newsText.append(" — ").append(summary);
                }
                newsText.append("\n");
            }

            String prompt = "你是一位专业的A股投资分析师。以下是最新的财经新闻列表：\n\n" +
                    newsText.toString() +
                    "\n请分析以上新闻，为每条新闻评估其对A股市场的影响程度（importance 1-5），" +
                    "并为重大新闻（importance >= 3）推荐最直接受益或受影响的A股股票（1-3只）。\n\n" +
                    "**重大新闻的判定标准（importance >= 3）**：\n" +
                    "- 5分：重大政策变化（降准降息、监管新规）、国际重大事件（贸易战、地缘冲突升级）\n" +
                    "- 4分：行业重大变动、大额资金流向变化、重要经济数据发布\n" +
                    "- 3分：板块级别利好利空、市场情绪重大转变、重要人物发言\n" +
                    "- 2分：普通行业资讯、常规数据更新\n" +
                    "- 1分：个股新闻、无关紧要的消息\n\n" +
                    "请严格按以下JSON格式返回：\n" +
                    "{\n" +
                    "  \"recommendations\": [\n" +
                    "    {\n" +
                    "      \"news_index\": 1,\n" +
                    "      \"stocks\": \"股票名称(代码)、股票名称(代码)\",\n" +
                    "      \"importance\": 4\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}\n\n" +
                    "说明：\n" +
                    "- news_index: 新闻编号（从1开始）\n" +
                    "- stocks: 推荐的A股股票（仅importance>=3时需要填写），格式如\"贵州茅台(600519)、宁德时代(300750)\"\n" +
                    "- importance: 每条新闻都必须评分（1-5），importance>=3的才算重大新闻\n" +
                    "- 所有新闻都必须返回（包括不重要的），以便客户端过滤\n" +
                    "- stocks字段：importance<3的新闻可以不填stocks\n" +
                    "- 推荐的股票必须是A股上市公司，确保代码准确\n" +
                    "- 只返回JSON，不要其他文字";

            Log.d(TAG, "正在用AI分析新闻并推荐相关股票...");
            String response = glm4Client.analyze(prompt);

            if (response != null && !response.isEmpty()) {
                parseAndApplyRecommendations(response, newsList);
            } else {
                Log.w(TAG, "AI新闻分析返回为空");
            }
        } catch (Exception e) {
            Log.e(TAG, "AI新闻股票推荐出错", e);
        }
    }

    /**
     * 解析AI返回的推荐结果并应用到对应新闻
     */
    private void parseAndApplyRecommendations(String response, List<StockNews> newsList) {
        try {
            // 提取JSON部分
            String jsonStr = response;
            int jsonStart = response.indexOf("{");
            int jsonEnd = response.lastIndexOf("}");
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                jsonStr = response.substring(jsonStart, jsonEnd + 1);
            }

            JSONObject json = new JSONObject(jsonStr);
            JSONArray recommendations = json.optJSONArray("recommendations");
            if (recommendations == null) {
                Log.w(TAG, "AI返回中没有recommendations字段");
                return;
            }

            int appliedCount = 0;
            for (int i = 0; i < recommendations.length(); i++) {
                JSONObject rec = recommendations.getJSONObject(i);
                int newsIndex = rec.optInt("news_index", -1) - 1; // 转为0-based
                String stocks = rec.optString("stocks", "");
                int importance = rec.optInt("importance", 1);

                if (newsIndex >= 0 && newsIndex < newsList.size()) {
                    StockNews news = newsList.get(newsIndex);
                    // 设置重要性评分（所有新闻都设置）
                    news.setImportance(importance);
                    if (importance >= 4) {
                        news.setImpactLevel("high");
                    } else if (importance >= 3) {
                        news.setImpactLevel("medium");
                    } else {
                        news.setImpactLevel("low");
                    }
                    // 仅为重大新闻设置推荐股票
                    if (!stocks.isEmpty() && importance >= 3) {
                        news.setRecommendedStocks(stocks);
                        appliedCount++;
                        Log.d(TAG, "新闻[" + (newsIndex + 1) + "] importance=" + importance + " 推荐股票: " + stocks);
                    } else {
                        Log.d(TAG, "新闻[" + (newsIndex + 1) + "] importance=" + importance + " (非重大)");
                    }
                }
            }
            Log.d(TAG, "成功为 " + appliedCount + " 条重大新闻添加了股票推荐");
        } catch (Exception e) {
            Log.e(TAG, "解析AI新闻推荐结果出错: " + response, e);
        }
    }

    /**
     * 保存龙虎榜数据到数据库
     */
    private void saveDragonTigerToDatabase(HotStockData hotData, String tradeDate) {
        if (hotData == null || hotData.getDragonTigerList() == null || hotData.getDragonTigerList().isEmpty()) {
            Log.d(TAG, "龙虎榜数据为空，跳过保存");
            return;
        }
        
        try {
            List<DragonTigerEntity> entities = new ArrayList<>();
            long fetchTime = System.currentTimeMillis();
            
            for (HotStockData.DragonTigerItem item : hotData.getDragonTigerList()) {
                DragonTigerEntity entity = new DragonTigerEntity();
                entity.setTradeDate(tradeDate);
                entity.setCode(item.getCode());
                entity.setName(item.getName());
                entity.setClosePrice(item.getClose());
                entity.setChangePercent(item.getChangePercent());
                entity.setTurnoverRate(item.getTurnoverRate());
                entity.setNetBuy(item.getNetBuy());
                entity.setBuyAmount(item.getBuyAmount());
                entity.setSellAmount(item.getSellAmount());
                entity.setReason(item.getReason());
                entity.setMarketCap(item.getMarketCap());
                entity.setFetchTime(fetchTime);
                entities.add(entity);
            }
            
            dragonTigerDao.insertAll(entities);
            Log.d(TAG, "成功保存龙虎榜历史数据: " + tradeDate + " 共 " + entities.size() + " 条");
        } catch (Exception e) {
            Log.e(TAG, "保存龙虎榜历史数据失败", e);
        }
    }

    /**
     * 保存连板股数据到数据库
     */
    private void saveContinuousLimitToDatabase(HotStockData hotData, String tradeDate) {
        if (hotData == null || hotData.getContinuousLimitList() == null || hotData.getContinuousLimitList().isEmpty()) {
            Log.d(TAG, "连板股数据为空，跳过保存");
            return;
        }
        
        try {
            List<ContinuousLimitEntity> entities = new ArrayList<>();
            long fetchTime = System.currentTimeMillis();
            
            for (HotStockData.ContinuousLimitItem item : hotData.getContinuousLimitList()) {
                ContinuousLimitEntity entity = new ContinuousLimitEntity();
                entity.setTradeDate(tradeDate);
                entity.setCode(item.getCode());
                entity.setName(item.getName());
                entity.setContinuousCount(item.getContinuousCount());
                entity.setChangePercent(item.getChangePercent());
                entity.setTurnoverRate(item.getTurnoverRate());
                entity.setMarketCap(item.getMarketCap());
                entity.setConcept(item.getConcept());
                entity.setFetchTime(fetchTime);
                entities.add(entity);
            }
            
            continuousLimitDao.insertAll(entities);
            Log.d(TAG, "成功保存连板股历史数据: " + tradeDate + " 共 " + entities.size() + " 条");
        } catch (Exception e) {
            Log.e(TAG, "保存连板股历史数据失败", e);
        }
    }

    /**
     * 更新通知栏显示
     */
    private void updateNotification(List<MarketIndex> indices) {
        StringBuilder text = new StringBuilder();
        for (MarketIndex index : indices) {
            if (text.length() > 0) text.append(" | ");
            text.append(index.getIndexName())
                    .append(" ")
                    .append(String.format("%.0f", index.getCurrentPoint()))
                    .append(" ")
                    .append(index.getFormattedChangePercent());
        }

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(text.toString()));
        }
    }

    /**
     * 发送广播通知UI更新
     */
    private void sendBroadcast(String action) {
        Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }
}
