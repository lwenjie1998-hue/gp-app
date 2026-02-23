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
import com.gp.stockapp.R;
import com.gp.stockapp.api.GLM4Client;
import com.gp.stockapp.model.HotStockData;
import com.gp.stockapp.model.MarketAnalysis;
import com.gp.stockapp.model.MarketIndex;
import com.gp.stockapp.model.StockNews;
import com.gp.stockapp.model.StrategyRecommendation;
import com.gp.stockapp.repository.StockRepository;
import com.gp.stockapp.utils.PromptLoader;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
    private static final long STRATEGY_INTERVAL = 300000; // 5分钟策略分析一次
    
    // 手动刷新动作
    public static final String ACTION_FORCE_AUCTION = "com.gp.stockapp.FORCE_AUCTION";
    public static final String ACTION_FORCE_CLOSING = "com.gp.stockapp.FORCE_CLOSING";
    public static final String ACTION_FORCE_SECTOR = "com.gp.stockapp.FORCE_SECTOR";

    private StockRepository stockRepository;
    private GLM4Client glm4Client;
    private PromptLoader promptLoader;
    private ExecutorService executorService;
    private Timer analysisTimer;
    private Timer strategyTimer;
    private boolean isRunning = false;
    
    // 策略执行标志位 - 确保每天只自动执行一次（仅针对竞价和尾盘）
    private boolean auctionExecutedToday = false;
    private boolean closingExecutedToday = false;
    private String lastExecutionDate = "";

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
        
        // 处理手动刷新动作
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_FORCE_AUCTION.equals(action)) {
                forceAnalyzeAuctionStrategy();
            } else if (ACTION_FORCE_CLOSING.equals(action)) {
                forceAnalyzeClosingStrategy();
            } else if (ACTION_FORCE_SECTOR.equals(action)) {
                forceAnalyzeSectorStrategy();
            }
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
                    getString(R.string.app_name) + " 分析",
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
                .setContentTitle(getString(R.string.app_name) + " AI分析")
                .setContentText("正在分析大盘走势...")
                .setSmallIcon(R.drawable.ic_gp_tool_logo)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void startAnalysis() {
        if (isRunning) return;

        isRunning = true;
        analysisTimer = new Timer();
        strategyTimer = new Timer();

        // 延迟10秒后首次分析（等待数据就绪）
        analysisTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                analyzeMarket();
            }
        }, 10000, ANALYSIS_INTERVAL);

        // 延迟20秒后首次策略分析
        strategyTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                analyzeStrategies();
            }
        }, 20000, STRATEGY_INTERVAL);

        Log.d(TAG, "Analysis started, interval: " + ANALYSIS_INTERVAL + "ms, strategy: " + STRATEGY_INTERVAL + "ms");
    }

    private void stopAnalysis() {
        isRunning = false;
        if (analysisTimer != null) {
            analysisTimer.cancel();
            analysisTimer = null;
        }
        if (strategyTimer != null) {
            strategyTimer.cancel();
            strategyTimer = null;
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
                String promptTemplate = promptLoader.loadMarketAnalysisPrompt();
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

    // ===== 策略推荐分析 =====

    /**
     * 根据时间段分析适当的策略推荐
     */
    private void analyzeStrategies() {
        Log.d(TAG, "Analyzing strategies...");

        executorService.execute(() -> {
            try {
                List<MarketIndex> indices = stockRepository.getMarketIndices();
                List<StockNews> newsList = stockRepository.getLatestNews(10);

                if (indices == null || indices.isEmpty()) {
                    Log.d(TAG, "No market data for strategy analysis");
                    return;
                }

                Calendar cal = Calendar.getInstance();
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                int minute = cal.get(Calendar.MINUTE);
                
                // 检查日期，如果是新的一天，重置执行标志
                String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
                if (!today.equals(lastExecutionDate)) {
                    lastExecutionDate = today;
                    auctionExecutedToday = false;
                    closingExecutedToday = false;
                    Log.d(TAG, "New trading day, reset execution flags");
                }

                // 板块推荐 - 9:25之后每5分钟自动执行
                if ((hour == 9 && minute >= 25) || (hour >= 10)) {
                    analyzeSectorStrategy(indices, newsList);
                }

                // 开盘竞价推荐 - 只在9:25之后执行，且每天只自动执行一次
                if ((hour == 9 && minute >= 25) || (hour >= 10)) {
                    if (!auctionExecutedToday) {
                        analyzeAuctionStrategy(indices, newsList);
                        auctionExecutedToday = true;
                        Log.d(TAG, "Auction strategy executed for today");
                    }
                }

                // 尾盘推荐 - 只在14:50之后执行，且每天只自动执行一次
                if ((hour == 14 && minute >= 50) || hour >= 15) {
                    if (!closingExecutedToday) {
                        analyzeClosingStrategy(indices, newsList);
                        closingExecutedToday = true;
                        Log.d(TAG, "Closing strategy executed for today");
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Error analyzing strategies", e);
            }
        });
    }

    /**
     * 构建融合热门数据+市场数据的完整Prompt
     * 将龙虎榜、涨停板、连板股等真实数据提供给AI
     */
    private String buildFullStrategyPrompt(String strategyPrompt, List<MarketIndex> indices, List<StockNews> newsList) {
        StringBuilder sb = new StringBuilder();
        
        // 添加具体策略要求
        sb.append(strategyPrompt).append("\n\n");
        
        // 添加市场数据
        sb.append(buildMarketDataText(indices, newsList));
        
        // 添加热门股票数据（龙虎榜、涨停板、连板股、涨幅榜）
        HotStockData hotData = stockRepository.getHotStockData();
        if (hotData != null) {
            String hotText = hotData.toAnalysisText();
            if (hotText != null && !hotText.isEmpty()) {
                sb.append("\n## 热门股票实时数据\n\n");
                sb.append("以下是当前市场的真实热点数据，请基于这些数据进行分析和推荐：\n\n");
                sb.append(hotText);
                sb.append("\n**重要提示**：请优先从以上数据中的中小市值股票（流通市值30-120亿）中选择推荐标的。");
                sb.append("龙虎榜净买入、涨停板、连板股是游资参与度最高的标的，请结合题材热点重点分析。\n");
            }
        }
        
        sb.append("\n请根据以上数据进行分析，输出JSON格式结果。");
        
        return sb.toString();
    }

    /**
     * 板块推荐分析
     */
    private void analyzeSectorStrategy(List<MarketIndex> indices, List<StockNews> newsList) {
        try {
            String prompt = buildFullStrategyPrompt(getSectorPrompt(), indices, newsList);

            String response = glm4Client.analyze(prompt);
            if (response != null && !response.isEmpty()) {
                StrategyRecommendation recommendation = parseStrategyRecommendation(response, "sector");
                if (recommendation != null) {
                    recommendation.setTimestamp(System.currentTimeMillis());
                    recommendation.setType("sector");
                    stockRepository.saveSectorRecommendation(recommendation);
                    sendBroadcast(MainActivity.ACTION_STRATEGY_UPDATED);
                    Log.d(TAG, "Sector strategy analysis completed");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in sector analysis", e);
        }
    }

    /**
     * 开盘竞价推荐分析（量化+游资融合策略）
     */
    private void analyzeAuctionStrategy(List<MarketIndex> indices, List<StockNews> newsList) {
        try {
            String prompt = buildFullStrategyPrompt(getAuctionPrompt(), indices, newsList);

            String response = glm4Client.analyze(prompt);
            if (response != null && !response.isEmpty()) {
                StrategyRecommendation recommendation = parseStrategyRecommendation(response, "open_auction");
                if (recommendation != null) {
                    recommendation.setTimestamp(System.currentTimeMillis());
                    recommendation.setType("open_auction");
                    stockRepository.saveAuctionRecommendation(recommendation);
                    sendBroadcast(MainActivity.ACTION_STRATEGY_UPDATED);
                    Log.d(TAG, "Auction strategy analysis completed");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in auction analysis", e);
        }
    }

    /**
     * 尾盘推荐分析（量化+游资融合策略）
     */
    private void analyzeClosingStrategy(List<MarketIndex> indices, List<StockNews> newsList) {
        try {
            String prompt = buildFullStrategyPrompt(getClosingPrompt(), indices, newsList);

            String response = glm4Client.analyze(prompt);
            if (response != null && !response.isEmpty()) {
                StrategyRecommendation recommendation = parseStrategyRecommendation(response, "closing");
                if (recommendation != null) {
                    recommendation.setTimestamp(System.currentTimeMillis());
                    recommendation.setType("closing");
                    stockRepository.saveClosingRecommendation(recommendation);
                    sendBroadcast(MainActivity.ACTION_STRATEGY_UPDATED);
                    Log.d(TAG, "Closing strategy analysis completed");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in closing analysis", e);
        }
    }
    
    // ===== 手动刷新方法（供外部调用）=====
    
    /**
     * 手动刷新竞价推荐（忽略时间和执行标志限制）
     */
    public void forceAnalyzeAuctionStrategy() {
        Log.d(TAG, "Force analyzing auction strategy...");
        executorService.execute(() -> {
            try {
                List<MarketIndex> indices = stockRepository.getMarketIndices();
                List<StockNews> newsList = stockRepository.getLatestNews(10);
                if (indices != null && !indices.isEmpty()) {
                    analyzeAuctionStrategy(indices, newsList);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in force auction analysis", e);
            }
        });
    }
    
    /**
     * 手动刷新尾盘推荐（忽略时间和执行标志限制）
     */
    public void forceAnalyzeClosingStrategy() {
        Log.d(TAG, "Force analyzing closing strategy...");
        executorService.execute(() -> {
            try {
                List<MarketIndex> indices = stockRepository.getMarketIndices();
                List<StockNews> newsList = stockRepository.getLatestNews(10);
                if (indices != null && !indices.isEmpty()) {
                    analyzeClosingStrategy(indices, newsList);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in force closing analysis", e);
            }
        });
    }
    
    /**
     * 手动刷新板块推荐
     */
    public void forceAnalyzeSectorStrategy() {
        Log.d(TAG, "Force analyzing sector strategy...");
        executorService.execute(() -> {
            try {
                List<MarketIndex> indices = stockRepository.getMarketIndices();
                List<StockNews> newsList = stockRepository.getLatestNews(10);
                if (indices != null && !indices.isEmpty()) {
                    analyzeSectorStrategy(indices, newsList);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in force sector analysis", e);
            }
        });
    }

    /**
     * 构建市场数据文本
     */
    private String buildMarketDataText(List<MarketIndex> indices, List<StockNews> newsList) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 当前大盘数据\n\n");
        for (MarketIndex index : indices) {
            sb.append("### ").append(index.getIndexName()).append("\n");
            sb.append("当前点位：").append(String.format("%.2f", index.getCurrentPoint())).append("\n");
            sb.append("涨跌幅：").append(index.getFormattedChangePercent()).append("\n");
            sb.append("成交额：").append(index.getFormattedAmount());
            String volumeChange = index.getVolumeChangeText();
            if (!volumeChange.isEmpty()) {
                sb.append(" (").append(volumeChange).append(")");
            }
            sb.append("\n\n");
        }
        if (newsList != null && !newsList.isEmpty()) {
            sb.append("## 市场新闻\n\n");
            for (StockNews news : newsList) {
                if (news.getTitle() != null) {
                    sb.append("- ").append(news.getTitle()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * 解析策略推荐结果
     */
    private StrategyRecommendation parseStrategyRecommendation(String response, String type) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            StrategyRecommendation rec = gson.fromJson(response, StrategyRecommendation.class);
            if (rec != null) return rec;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing strategy recommendation JSON", e);
        }

        // JSON解析失败时，创建文本结果
        StrategyRecommendation rec = new StrategyRecommendation();
        rec.setType(type);
        rec.setAnalysisText(response);
        rec.setConfidence(50);
        rec.setRiskLevel("medium");
        switch (type) {
            case "sector":
                rec.setTitle("板块推荐");
                rec.setSummary("AI分析解析中，请参考原始分析文本。");
                break;
            case "open_auction":
                rec.setTitle("开盘竞价推荐");
                rec.setSummary("AI分析解析中，请参考原始分析文本。");
                break;
            case "closing":
                rec.setTitle("尾盘推荐");
                rec.setSummary("AI分析解析中，请参考原始分析文本。");
                break;
        }
        return rec;
    }

    // ===== 策略 Prompt =====

    /**
     * 板块推荐 Prompt
     */
    private String getSectorPrompt() {
        String prompt = promptLoader.loadSectorStrategyPrompt();
        if (prompt != null && !prompt.isEmpty()) return prompt;

        return "你是一位资深A股板块分析师。\n" +
                "请根据以下大盘数据和市场新闻，分析当前最值得关注的行业板块。\n\n" +
                "请输出JSON格式，包含以下字段：\n" +
                "{\n" +
                "  \"title\": \"板块推荐\",\n" +
                "  \"summary\": \"整体板块分析概述(50字内)\",\n" +
                "  \"confidence\": 0-100,\n" +
                "  \"risk_level\": \"low/medium/high\",\n" +
                "  \"items\": [\n" +
                "    {\n" +
                "      \"name\": \"板块名称\",\n" +
                "      \"reason\": \"推荐理由(30字内)\",\n" +
                "      \"highlight\": \"亮点标签(如:政策利好/资金涌入/业绩超预期)\",\n" +
                "      \"score\": 0-100,\n" +
                "      \"related_stocks\": [\"龙头股1\", \"龙头股2\", \"龙头股3\", \"龙头股4\", \"龙头股5\"],\n" +
                "      \"tags\": [\"标签1\", \"标签2\"]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"strategy_note\": \"操作建议\",\n" +
                "  \"analysis_text\": \"详细分析文本\"\n" +
                "}\n\n" +
                "请推荐3-5个板块，按综合评分从高到低排列。每个板块请给出5只相关股票（含龙头股和弹性股）。\n" +
                "推荐理由需融合量化信号和游资逻辑。\n" +
                "**重要：只推荐A股股票（沪深交易所上市），不要推荐港股、美股或其他市场的股票**";
    }

    /**
     * 开盘竞价推荐 Prompt（量化+游资融合策略）
     */
    private String getAuctionPrompt() {
        String prompt = promptLoader.loadAuctionStrategyPrompt();
        if (prompt != null && !prompt.isEmpty()) return prompt;

        return "你同时具备量化分析师和游资操盘手的双重能力，专精A股集合竞价阶段的短线狙击。\n" +
                "融合量化数据信号与游资实战经验，推荐5只适合竞价介入的A股短线标的。\n\n" +
                "量化筛选：MACD/KDJ/RSI/均线多头排列/量能放大/突破信号\n" +
                "游资逻辑：龙头优先/弱转强/题材爆发/资金合力/情绪共振\n" +
                "综合评分 = 量化技术信号(30%) + 动量(20%) + 题材(20%) + 资金(15%) + 情绪(15%)\n\n" +
                "请输出JSON格式，包含以下字段：\n" +
                "{\n" +
                "  \"title\": \"开盘竞价推荐\",\n" +
                "  \"summary\": \"今日竞价策略概述(50字内，含量化信号和市场情绪判断)\",\n" +
                "  \"confidence\": 0-100,\n" +
                "  \"risk_level\": \"high\",\n" +
                "  \"items\": [\n" +
                "    {\n" +
                "      \"name\": \"股票名称\",\n" +
                "      \"code\": \"股票代码(6位数字)\",\n" +
                "      \"reason\": \"推荐理由(30字内，融合量化信号和游资逻辑)\",\n" +
                "      \"highlight\": \"核心亮点(如:量游共振/技术突破+龙头确认)\",\n" +
                "      \"score\": 0-100,\n" +
                "      \"entry_timing\": \"竞价介入策略\",\n" +
                "      \"stop_loss\": \"止损位(基于量化支撑位)\",\n" +
                "      \"tags\": [\"打板\", \"龙头\", \"弱转强\", \"量化突破\"]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"market_sentiment\": \"市场情绪周期(冰点/修复/高潮/分化/退潮)\",\n" +
                "  \"main_line\": \"当前最强主线题材\",\n" +
                "  \"strategy_note\": \"仓位管理与风控纪律提醒\",\n" +
                "  \"analysis_text\": \"详细分析文本(含量化扫描和游资研判)\"\n" +
                "}\n\n" +
                "只推荐5只股票，短线持有1-3天，必须给出止损位。\n" +
                "**重要：只推荐A股股票（沪深交易所上市），不要推荐港股、美股或其他市场的股票**";
    }

    /**
     * 尾盘推荐 Prompt（量化+游资融合策略）
     */
    private String getClosingPrompt() {
        String prompt = promptLoader.loadClosingStrategyPrompt();
        if (prompt != null && !prompt.isEmpty()) return prompt;

        return "你同时具备量化分析师和游资操盘手的双重能力，专精A股尾盘低吸和隔夜套利。\n" +
                "融合量化数据分析与游资实战经验，推荐5只适合尾盘介入的A股短线标的。\n\n" +
                "量化筛选：趋势确认/均线支撑/动量评估/量能分析/波动率评估\n" +
                "游资逻辑：强势回调低吸/炸板回封/板块尾盘异动/弱转强/连板确认\n" +
                "综合评分 = 量化趋势(25%) + 尾盘异动(20%) + 次日高开概率(20%) + 题材(15%) + 资金(10%) + 风控(10%)\n\n" +
                "请输出JSON格式，包含以下字段：\n" +
                "{\n" +
                "  \"title\": \"尾盘推荐\",\n" +
                "  \"summary\": \"尾盘策略概述(50字内，含量化信号和市场情绪判断)\",\n" +
                "  \"confidence\": 0-100,\n" +
                "  \"risk_level\": \"low/medium/high\",\n" +
                "  \"items\": [\n" +
                "    {\n" +
                "      \"name\": \"股票名称\",\n" +
                "      \"code\": \"股票代码(6位数字)\",\n" +
                "      \"reason\": \"推荐理由(30字内，融合量化信号和游资逻辑)\",\n" +
                "      \"highlight\": \"核心亮点(如:量化支撑+龙头低吸/弱转强+动量反转)\",\n" +
                "      \"score\": 0-100,\n" +
                "      \"entry_timing\": \"买入时间和价格策略\",\n" +
                "      \"stop_loss\": \"止损位(基于量化支撑位)\",\n" +
                "      \"next_day_plan\": \"次日操作预案(高开/平开/低开怎么做)\",\n" +
                "      \"tags\": [\"隔夜\", \"低吸\", \"弱转强\", \"量化支撑\"]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"market_sentiment\": \"全天市场情绪总结(冰点/修复/高潮/分化/退潮)\",\n" +
                "  \"main_line\": \"今日最强主线题材\",\n" +
                "  \"overnight_risk\": \"隔夜风险评估(外盘、消息面等)\",\n" +
                "  \"strategy_note\": \"尾盘风控纪律(单只最多3成仓，隔夜亏损不超3%)\",\n" +
                "  \"analysis_text\": \"详细分析文本(含量化复盘和游资研判)\"\n" +
                "}\n\n" +
                "只推荐5只股票，隔夜持仓次日择机卖出，必须给出止损位和次日操作预案。\n" +
                "**重要：只推荐A股股票（沪深交易所上市），不要推荐港股、美股或其他市场的股票**";
    }
}
