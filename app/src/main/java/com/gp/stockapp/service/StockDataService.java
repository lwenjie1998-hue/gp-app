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
import com.gp.stockapp.api.GLM4Client;
import com.gp.stockapp.api.MarketApi;
import com.gp.stockapp.model.MarketIndex;
import com.gp.stockapp.model.StockNews;
import com.gp.stockapp.repository.StockRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 大盘数据抓取服务
 * 循环抓取三大指数实时数据和市场新闻
 */
public class StockDataService extends Service {
    private static final String TAG = "StockDataService";
    private static final String CHANNEL_ID = "MarketDataChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final long FETCH_INTERVAL = 30000; // 30秒刷新一次

    private StockRepository stockRepository;
    private MarketApi marketApi;
    private ExecutorService executorService;
    private Timer fetchTimer;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "StockDataService created");

        stockRepository = StockRepository.getInstance(getApplicationContext());
        marketApi = MarketApi.getInstance();
        executorService = Executors.newFixedThreadPool(2);

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
        stopDataFetching();
        if (executorService != null) {
            executorService.shutdown();
        }
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
                .setContentTitle("大盘AI助手")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void startDataFetching() {
        if (isRunning) return;

        isRunning = true;
        fetchTimer = new Timer();

        // 立即执行一次
        fetchMarketData();

        // 定时执行
        fetchTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                fetchMarketData();
            }
        }, FETCH_INTERVAL, FETCH_INTERVAL);

        Log.d(TAG, "Data fetching started, interval: " + FETCH_INTERVAL + "ms");
    }

    private void stopDataFetching() {
        isRunning = false;
        if (fetchTimer != null) {
            fetchTimer.cancel();
            fetchTimer = null;
        }
        Log.d(TAG, "Data fetching stopped");
    }

    /**
     * 抓取大盘数据
     */
    private void fetchMarketData() {
        Log.d(TAG, "==== 开始执行定时数据抓取任务 ====");
        executorService.execute(() -> {
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
                    // 用AI为重大新闻推荐相关A股股票
                    enrichNewsWithStockRecommendations(newsList);
                    stockRepository.saveNewsData(newsList);
                    Log.d(TAG, "成功更新市场要闻: 抓取并保存了 " + newsList.size() + " 条新闻");
                } else {
                    Log.w(TAG, "市场要闻抓取结果为空（可能所有源都失败了）");
                }

                Log.d(TAG, "==== 定时数据抓取任务完成 ====");
            } catch (Exception e) {
                Log.e(TAG, "定时抓取大盘数据时发生严重错误", e);
            }
        });
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
                    "\n请分析以上新闻，找出对A股市场有重大影响的新闻（如政策变化、国际事件、行业重大变动等），" +
                    "并为这些重大新闻推荐最直接受益或受影响的A股股票（1-3只）。\n\n" +
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
                    "- stocks: 推荐的A股股票，格式如\"贵州茅台(600519)、宁德时代(300750)\"\n" +
                    "- importance: 重要程度1-5（5最重要）\n" +
                    "- 只返回有重大影响的新闻，普通新闻不需要返回\n" +
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
                int importance = rec.optInt("importance", 3);

                if (newsIndex >= 0 && newsIndex < newsList.size() && !stocks.isEmpty()) {
                    StockNews news = newsList.get(newsIndex);
                    news.setRecommendedStocks(stocks);
                    news.setImportance(importance);
                    if (importance >= 4) {
                        news.setImpactLevel("high");
                    } else if (importance >= 3) {
                        news.setImpactLevel("medium");
                    }
                    appliedCount++;
                    Log.d(TAG, "新闻[" + (newsIndex + 1) + "] 推荐股票: " + stocks);
                }
            }
            Log.d(TAG, "成功为 " + appliedCount + " 条重大新闻添加了股票推荐");
        } catch (Exception e) {
            Log.e(TAG, "解析AI新闻推荐结果出错: " + response, e);
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
