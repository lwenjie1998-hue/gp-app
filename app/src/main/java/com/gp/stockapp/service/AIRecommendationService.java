package com.gp.stockapp.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.gp.stockapp.MainActivity;
import com.gp.stockapp.api.GLM4Client;
import com.gp.stockapp.model.AIRecommendation;
import com.gp.stockapp.model.StockData;
import com.gp.stockapp.model.StockNews;
import com.gp.stockapp.repository.StockRepository;
import com.gp.stockapp.utils.PromptLoader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Timer;
import java.util.TimerTask;

/**
 * AI推荐服务
 * 负责使用GLM-4.7分析股票数据和新闻，生成推荐
 */
public class AIRecommendationService extends Service {
    private static final String TAG = "AIRecommendationService";
    private static final String CHANNEL_ID = "AIRecommendationChannel";
    private static final int NOTIFICATION_ID = 2;
    private static final long ANALYSIS_INTERVAL = 300000; // 5分钟分析一次
    
    private StockRepository stockRepository;
    private GLM4Client glm4Client;
    private PromptLoader promptLoader;
    private ExecutorService executorService;
    private Timer analysisTimer;
    private boolean isRunning = false;
    
    // 分析模式：quant（量化）、hot_money（游资）、both（两者）
    private String analysisMode = "both";
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AIRecommendationService created");
        
        stockRepository = new StockRepository(getApplicationContext());
        glm4Client = GLM4Client.getInstance();
        promptLoader = new PromptLoader(getApplicationContext());
        executorService = Executors.newFixedThreadPool(2);
        
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "AIRecommendationService started");
        
        startForeground(NOTIFICATION_ID, createNotification());
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
        Log.d(TAG, "AIRecommendationService destroyed");
        
        stopAnalysis();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
    
    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "AI推荐服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("AI智能分析股票推荐");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * 创建前台服务通知
     */
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI推荐分析中")
            .setContentText("正在使用AI分析股票数据")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }
    
    /**
     * 开始分析
     */
    private void startAnalysis() {
        if (isRunning) {
            return;
        }
        
        isRunning = true;
        analysisTimer = new Timer();
        
        // 立即执行一次
        analyzeStocks();
        
        // 定时执行
        analysisTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                analyzeStocks();
            }
        }, ANALYSIS_INTERVAL, ANALYSIS_INTERVAL);
        
        Log.d(TAG, "Analysis started");
    }
    
    /**
     * 停止分析
     */
    private void stopAnalysis() {
        isRunning = false;
        if (analysisTimer != null) {
            analysisTimer.cancel();
            analysisTimer = null;
        }
        Log.d(TAG, "Analysis stopped");
    }
    
    /**
     * 分析股票
     */
    private void analyzeStocks() {
        Log.d(TAG, "Analyzing stocks...");
        
        executorService.execute(() -> {
            try {
                // 获取关注的股票数据
                List<String> watchList = stockRepository.getWatchList();
                List<StockData> stockDataList = stockRepository.getLatestStockData(watchList);
                List<StockNews> newsList = stockRepository.getLatestNews(20);
                
                if (stockDataList == null || stockDataList.isEmpty()) {
                    Log.d(TAG, "No stock data to analyze");
                    return;
                }
                
                // 选择合适的提示词
                String promptTemplate = loadPromptTemplate();
                
                // 分析每只股票
                for (StockData stockData : stockDataList) {
                    try {
                        // 构建分析输入
                        String analysisInput = buildAnalysisInput(stockData, newsList, promptTemplate);
                        
                        // 调用GLM-4.7进行分析
                        String response = glm4Client.analyze(analysisInput);
                        
                        // 解析推荐结果
                        if (response != null && !response.isEmpty()) {
                            AIRecommendation recommendation = parseRecommendation(response);
                            if (recommendation != null) {
                                stockRepository.saveRecommendation(recommendation);
                                Log.d(TAG, "Saved recommendation for: " + stockData.getStockCode());
                                
                                // 发送高置信度推荐通知
                                if (recommendation.getConfidence() >= 80.0) {
                                    sendRecommendationNotification(recommendation);
                                }
                            }
                        }
                        
                        // 避免请求过快
                        Thread.sleep(1000);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error analyzing stock: " + stockData.getStockCode(), e);
                    }
                }
                
                Log.d(TAG, "Stock analysis completed");
                
            } catch (Exception e) {
                Log.e(TAG, "Error in stock analysis", e);
            }
        });
    }
    
    /**
     * 加载提示词模板
     */
    private String loadPromptTemplate() {
        switch (analysisMode) {
            case "quant":
                return promptLoader.loadPrompt("quantitative_analysis.txt");
            case "hot_money":
                return promptLoader.loadPrompt("hot_money_analysis.txt");
            case "both":
            default:
                return promptLoader.loadPrompt("combined_analysis.txt");
        }
    }
    
    /**
     * 构建分析输入
     */
    private String buildAnalysisInput(StockData stockData, List<StockNews> newsList, String promptTemplate) {
        StringBuilder input = new StringBuilder();
        
        // 添加提示词模板
        input.append(promptTemplate);
        input.append("\n\n");
        
        // 添加股票数据
        input.append("## 股票数据\n");
        input.append("股票代码：").append(stockData.getStockCode()).append("\n");
        input.append("股票名称：").append(stockData.getStockName()).append("\n");
        input.append("当前价格：").append(stockData.getCurrentPrice()).append("元\n");
        input.append("涨跌幅：").append(stockData.getChangePercent()).append("%\n");
        input.append("成交量：").append(stockData.getVolume()).append("股\n");
        input.append("成交额：").append(stockData.getAmount()).append("元\n");
        input.append("开盘价：").append(stockData.getOpen()).append("元\n");
        input.append("最高价：").append(stockData.getHigh()).append("元\n");
        input.append("最低价：").append(stockData.getLow()).append("元\n");
        input.append("换手率：").append(stockData.getTurnoverRate()).append("%\n");
        input.append("市盈率：").append(stockData.getPeRatio()).append("\n");
        input.append("市值：").append(stockData.getMarketCap()).append("亿元\n");
        input.append("主力净流入：").append(stockData.getMainInflow()).append("元\n");
        input.append("散户净流入：").append(stockData.getRetailInflow()).append("元\n");
        input.append("近期涨停次数：").append(stockData.getLimitUpTimes()).append("次\n");
        input.append("连续上涨天数：").append(stockData.getConsecutiveRiseDays()).append("天\n");
        
        // 添加相关新闻
        if (newsList != null && !newsList.isEmpty()) {
            input.append("\n## 相关新闻\n");
            int count = 0;
            for (StockNews news : newsList) {
                // 简单判断新闻是否相关
                if (isNewsRelated(stockData, news)) {
                    input.append("标题：").append(news.getTitle()).append("\n");
                    input.append("摘要：").append(news.getSummary()).append("\n");
                    input.append("发布时间：").append(news.getPublishTime()).append("\n");
                    input.append("重要性：").append(news.getImportance()).append("\n");
                    input.append("情绪：").append(news.getSentiment()).append("\n");
                    input.append("影响级别：").append(news.getImpactLevel()).append("\n");
                    input.append("---\n");
                    count++;
                    if (count >= 5) break; // 最多5条相关新闻
                }
            }
        }
        
        input.append("\n请根据以上数据进行分析，并输出JSON格式的推荐结果。");
        
        return input.toString();
    }
    
    /**
     * 判断新闻是否与股票相关
     */
    private boolean isNewsRelated(StockData stockData, StockNews news) {
        String title = news.getTitle();
        String summary = news.getSummary();
        String stockCode = stockData.getStockCode();
        String stockName = stockData.getStockName();
        
        // 检查是否包含股票代码或名称
        boolean codeMatch = (title != null && title.contains(stockCode)) ||
                           (summary != null && summary.contains(stockCode));
        boolean nameMatch = (title != null && title.contains(stockName)) ||
                           (summary != null && summary.contains(stockName));
        
        // 检查新闻中的股票代码
        if (news.getStockCodes() != null) {
            for (String code : news.getStockCodes()) {
                if (stockCode.equals(code)) {
                    return true;
                }
            }
        }
        
        return codeMatch || nameMatch;
    }
    
    /**
     * 解析推荐结果
     */
    private AIRecommendation parseRecommendation(String response) {
        try {
            // 这里需要解析JSON响应
            // 使用Gson或其他JSON库
            com.google.gson.Gson gson = new com.google.gson.Gson();
            return gson.fromJson(response, AIRecommendation.class);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing recommendation", e);
            return null;
        }
    }
    
    /**
     * 发送推荐通知
     */
    private void sendRecommendationNotification(AIRecommendation recommendation) {
        if (recommendation == null) {
            return;
        }
        
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );
        
        String content = String.format("%s(%s) - 置信度%.1f%% - %s",
            recommendation.getStockName(),
            recommendation.getStockCode(),
            recommendation.getConfidence(),
            getRecommendTypeText(recommendation.getRecommendType())
        );
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI推荐")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build();
        
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), notification);
        }
    }
    
    /**
     * 获取推荐类型文本
     */
    private String getRecommendTypeText(String type) {
        switch (type) {
            case "quant":
                return "量化推荐";
            case "hot_money":
                return "游资推荐";
            case "both":
                return "强烈推荐";
            default:
                return "推荐";
        }
    }
    
    /**
     * 设置分析模式
     */
    public void setAnalysisMode(String mode) {
        this.analysisMode = mode;
        Log.d(TAG, "Analysis mode set to: " + mode);
    }
    
    /**
     * 手动触发分析
     */
    public void triggerAnalysis() {
        executorService.execute(this::analyzeStocks);
    }
}
