package com.gp.stockapp.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.gp.stockapp.MainActivity;
import com.gp.stockapp.api.GLM4Client;
import com.gp.stockapp.model.MarketAnalysis;
import com.gp.stockapp.model.MarketIndex;
import com.gp.stockapp.model.StockNews;
import com.gp.stockapp.repository.StockRepository;
import com.gp.stockapp.utils.PromptLoader;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI大盘分析服务
 * 使用GLM-4模型分析大盘走势，生成市场研判
 */
public class AIRecommendationService extends Service {
    private static final String TAG = "AIAnalysisService";
    private static final String CHANNEL_ID = "AIAnalysisChannel";
    private static final int NOTIFICATION_ID = 2;
    private static final long ANALYSIS_INTERVAL = 300000; // 5分钟分析一次

    private StockRepository stockRepository;
    private GLM4Client glm4Client;
    private PromptLoader promptLoader;
    private ExecutorService executorService;
    private Timer analysisTimer;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AIAnalysisService created");

        stockRepository = StockRepository.getInstance(getApplicationContext());
        glm4Client = GLM4Client.getInstance();
        promptLoader = new PromptLoader(getApplicationContext());
        executorService = Executors.newSingleThreadExecutor();

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "AIAnalysisService started");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, createNotification());
        }
        startAnalysis();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "AIAnalysisService destroyed");
        stopAnalysis();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "AI大盘分析",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("AI智能分析大盘走势");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AI大盘分析")
                .setContentText("正在分析大盘走势...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void startAnalysis() {
        if (isRunning) return;

        isRunning = true;
        analysisTimer = new Timer();

        // 延迟10秒后首次分析（等待数据就绪）
        analysisTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                analyzeMarket();
            }
        }, 10000, ANALYSIS_INTERVAL);

        Log.d(TAG, "Analysis started, interval: " + ANALYSIS_INTERVAL + "ms");
    }

    private void stopAnalysis() {
        isRunning = false;
        if (analysisTimer != null) {
            analysisTimer.cancel();
            analysisTimer = null;
        }
        Log.d(TAG, "Analysis stopped");
    }

    /**
     * 分析大盘走势
     */
    private void analyzeMarket() {
        Log.d(TAG, "Analyzing market...");

        executorService.execute(() -> {
            try {
                // 获取最新指数数据
                List<MarketIndex> indices = stockRepository.getMarketIndices();
                List<StockNews> newsList = stockRepository.getLatestNews(10);

                if (indices == null || indices.isEmpty()) {
                    Log.d(TAG, "No market data to analyze");
                    return;
                }

                // 加载提示词模板
                String promptTemplate = promptLoader.loadPrompt("market_analysis.txt");
                if (promptTemplate == null || promptTemplate.isEmpty()) {
                    promptTemplate = getDefaultPrompt();
                }

                // 构建分析输入
                String analysisInput = buildAnalysisInput(indices, newsList, promptTemplate);

                // 调用GLM-4进行分析
                String response = glm4Client.analyze(analysisInput);

                if (response != null && !response.isEmpty()) {
                    // 解析分析结果
                    MarketAnalysis analysis = parseAnalysis(response);
                    if (analysis != null) {
                        analysis.setTimestamp(System.currentTimeMillis());
                        stockRepository.saveMarketAnalysis(analysis);

                        // 通知UI更新
                        sendBroadcast(MainActivity.ACTION_ANALYSIS_UPDATED);

                        // 重要分析发送通知
                        if (analysis.getConfidence() >= 80) {
                            sendAnalysisNotification(analysis);
                        }

                        Log.d(TAG, "Market analysis completed: " + analysis.getSentimentText());
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Error analyzing market", e);
            }
        });
    }

    /**
     * 构建分析输入文本
     */
    private String buildAnalysisInput(List<MarketIndex> indices,
                                       List<StockNews> newsList,
                                       String promptTemplate) {
        StringBuilder input = new StringBuilder();
        input.append(promptTemplate).append("\n\n");

        // 添加指数数据
        input.append("## 大盘指数数据\n\n");
        for (MarketIndex index : indices) {
            input.append("### ").append(index.getIndexName()).append("\n");
            input.append("当前点位：").append(String.format("%.2f", index.getCurrentPoint())).append("\n");
            input.append("涨跌幅：").append(index.getFormattedChangePercent()).append("\n");
            input.append("涨跌点数：").append(index.getFormattedChangePoint()).append("\n");
            input.append("成交额：").append(index.getFormattedAmount());
            
            // 添加放量/缩量信息
            String volumeChange = index.getVolumeChangeText();
            if (!volumeChange.isEmpty()) {
                input.append(" (").append(volumeChange);
                double changePercent = index.getAmountChangePercent();
                if (changePercent != 0) {
                    input.append(String.format(" %.1f%%", Math.abs(changePercent)));
                }
                input.append(")");
            }
            input.append("\n");
            
            input.append("开盘：").append(String.format("%.2f", index.getOpen())).append("\n");
            input.append("最高：").append(String.format("%.2f", index.getHigh())).append("\n");
            input.append("最低：").append(String.format("%.2f", index.getLow())).append("\n");
            input.append("昨收：").append(String.format("%.2f", index.getPreClose())).append("\n");
            input.append("\n");
        }

        // 添加新闻
        if (newsList != null && !newsList.isEmpty()) {
            input.append("## 市场新闻\n\n");
            for (StockNews news : newsList) {
                if (news.getTitle() != null) {
                    input.append("- ").append(news.getTitle());
                    if (news.getSummary() != null) {
                        input.append("：").append(news.getSummary());
                    }
                    input.append("\n");
                }
            }
        }

        input.append("\n请根据以上数据进行大盘分析，输出JSON格式结果。");

        return input.toString();
    }

    /**
     * 解析AI分析结果
     */
    private MarketAnalysis parseAnalysis(String response) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            return gson.fromJson(response, MarketAnalysis.class);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing analysis result", e);

            // 如果JSON解析失败，创建一个文本结果
            MarketAnalysis analysis = new MarketAnalysis();
            analysis.setAnalysisText(response);
            analysis.setMarketSentiment("neutral");
            analysis.setTrendDirection("sideways");
            analysis.setRiskLevel("medium");
            analysis.setConfidence(50);
            analysis.setSuggestion("AI分析解析异常，请参考原始分析文本。");
            return analysis;
        }
    }

    /**
     * 发送分析通知
     */
    private void sendAnalysisNotification(MarketAnalysis analysis) {
        String content = String.format("市场情绪: %s | 趋势: %s | 置信度: %.0f%%",
                analysis.getSentimentText(),
                analysis.getTrendText(),
                analysis.getConfidence());

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AI大盘研判")
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(content + "\n" +
                                (analysis.getSuggestion() != null ? analysis.getSuggestion() : "")))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), notification);
        }
    }

    /**
     * 默认prompt（当文件加载失败时使用）
     */
    private String getDefaultPrompt() {
        return "你是一位专业的A股大盘分析师。\n" +
                "请根据以下大盘指数数据和市场新闻进行综合分析。\n" +
                "请输出JSON格式，包含以下字段：\n" +
                "market_sentiment(bullish/bearish/neutral), " +
                "trend_direction(up/down/sideways), " +
                "risk_level(low/medium/high), " +
                "confidence(0-100), " +
                "short_term_view, medium_term_view, " +
                "suggestion, key_factors[], analysis_text, " +
                "support_level, resistance_level";
    }

    private void sendBroadcast(String action) {
        Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }
}
