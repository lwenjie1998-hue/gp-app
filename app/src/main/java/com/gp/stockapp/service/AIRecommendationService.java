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
import java.util.ArrayList;
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
     * @param strategyType 策略类型: "open_auction"=竞价(用前一交易日龙虎榜+技术指标+竞价数据), "closing"=尾盘(用大盘走势+板块+宏观), 其他=用当天数据
     */
    private String buildFullStrategyPrompt(String strategyPrompt, List<MarketIndex> indices, List<StockNews> newsList, String strategyType) {
        StringBuilder sb = new StringBuilder();
        
        // 添加具体策略要求
        sb.append(strategyPrompt).append("\n\n");
        
        // 添加市场数据
        sb.append(buildMarketDataText(indices, newsList));
        
        if ("closing".equals(strategyType)) {
            // ===== 尾盘策略：聚焦大盘走势、板块轮动、国际国内局势、主力资金和技术指标 =====
            Log.d(TAG, "尾盘策略: 聚焦大盘走势+板块分析+宏观局势+技术指标");
            
            // 只提供活跃股数据（用于板块分析和主力资金判断），不提供龙虎榜等个股热点数据
            HotStockData hotData = stockRepository.getHotStockData();
            if (hotData != null && hotData.getTopGainers() != null && !hotData.getTopGainers().isEmpty()) {
                sb.append("\n## 当日主板活跃股数据（按成交额排序）\n\n");
                sb.append("以下是今日成交最活跃的主板股票，请用于分析板块资金流向和主力动向：\n\n");
                for (HotStockData.TopGainerItem item : hotData.getTopGainers()) {
                    sb.append("- ").append(item.toString()).append("\n");
                }
                sb.append("\n**分析要点**：从以上活跃股中提取板块信息和资金流向，结合大盘走势和国际国内局势，");
                sb.append("筛选尾盘处于技术支撑位附近、所在板块具有持续性的中小市值标的（流通市值30-120亿）。\n");
            }
            
            sb.append("\n## 分析重点提示\n\n");
            sb.append("请重点从以下维度分析：\n");
            sb.append("1. 大盘全天走势形态和尾盘趋势\n");
            sb.append("2. 板块资金轮动方向和明日预判\n");
            sb.append("3. 新闻中的国际国内局势对A股的影响\n");
            sb.append("4. 主力资金流入方向和成交量变化\n");
            sb.append("5. 推荐标的的技术面信号（均线支撑/MACD/KDJ/RSI等）\n");
            
        } else if ("open_auction".equals(strategyType)) {
            // ===== 竞价策略：昨日龙虎榜+热搜榜+技术指标+集合竞价 =====
            Log.d(TAG, "竞价策略: 使用前一交易日龙虎榜+热搜+技术指标+竞价分析");
            
            HotStockData hotData = stockRepository.getPrevDayHotStockData();
            if (hotData != null) {
                String hotText = hotData.toAnalysisText();
                if (hotText != null && !hotText.isEmpty()) {
                    sb.append("\n## 昨日热门股票数据（龙虎榜+涨停板+连板+活跃股）\n\n");
                    sb.append("以下是前一交易日的真实市场热点数据，请基于这些数据进行分析和推荐：\n\n");
                    sb.append(hotText);
                    sb.append("\n**重要提示**：请优先从以上龙虎榜和活跃股数据中，筛选流通市值30-120亿的中小盘标的。\n");
                }
            }
            
            sb.append("\n## 竞价分析要求\n\n");
            sb.append("请对筛选出的标的，从以下维度进行综合评估：\n");
            sb.append("1. **昨日龙虎榜**：净买入额、知名游资/机构席位参与情况\n");
            sb.append("2. **热搜/题材热度**：结合新闻判断标的所在题材的市场热度和持续性\n");
            sb.append("3. **技术指标验证**：均线排列(5/10/20日)、MACD金叉/红柱、KDJ超卖区金叉、RSI位置、量能变化\n");
            sb.append("4. **集合竞价预判**：根据龙虎榜和题材热度，预判竞价高开/低开可能性，给出介入条件\n");
            sb.append("5. 每只推荐必须提到至少一项技术指标信号作为辅助依据\n");
            
        } else {
            // ===== 板块策略等其他类型：使用当天全量热门数据 =====
            HotStockData hotData = stockRepository.getHotStockData();
            String dataLabel = "当天";
            Log.d(TAG, "板块策略: 使用当天热门数据");
            
            if (hotData != null) {
                String hotText = hotData.toAnalysisText();
                if (hotText != null && !hotText.isEmpty()) {
                    sb.append("\n## 热门股票数据（").append(dataLabel).append("）\n\n");
                    sb.append("以下是").append(dataLabel).append("市场的真实热点数据，请基于这些数据进行分析和推荐：\n\n");
                    sb.append(hotText);
                    sb.append("\n**重要提示**：请优先从以上数据中的中小市值股票（流通市值30-120亿）中选择推荐标的。");
                    sb.append("龙虎榜净买入、涨停板、连板股是游资参与度最高的标的，请结合题材热点重点分析。\n");
                }
            }
        }
        
        sb.append("\n请根据以上数据进行分析，输出JSON格式结果。");
        sb.append("\n\n**最终强制提醒（违反则结果无效）：**");
        sb.append("\n1. 股票代码必须以600或000开头（主板），严禁出现300（创业板）、688（科创板）开头的代码");
        sb.append("\n2. 已涨停或接近涨停（距涨停价<1%）的股票绝对不能推荐");
        sb.append("\n3. 输出前逐只核对股票代码前3位，不符合600/000的必须替换");
        
        return sb.toString();
    }

    /**
     * 板块推荐分析
     */
    private void analyzeSectorStrategy(List<MarketIndex> indices, List<StockNews> newsList) {
        try {
            String prompt = buildFullStrategyPrompt(getSectorPrompt(), indices, newsList, "sector");

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
     * 使用GLM-5高精度模型
     */
    private void analyzeAuctionStrategy(List<MarketIndex> indices, List<StockNews> newsList) {
        try {
            String prompt = buildFullStrategyPrompt(getAuctionPrompt(), indices, newsList, "open_auction");

            Log.d(TAG, "竞价推荐: 使用GLM-5高精度模型");
            String response = glm4Client.analyzePremium(prompt);
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
     * 使用GLM-5高精度模型
     */
    private void analyzeClosingStrategy(List<MarketIndex> indices, List<StockNews> newsList) {
        try {
            String prompt = buildFullStrategyPrompt(getClosingPrompt(), indices, newsList, "closing");

            Log.d(TAG, "尾盘推荐: 使用GLM-5高精度模型");
            String response = glm4Client.analyzePremium(prompt);
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
            if (rec != null) {
                // 竞价和尾盘推荐：代码层面强制过滤非主板股票
                if ("open_auction".equals(type) || "closing".equals(type)) {
                    filterMainBoardOnly(rec);
                }
                return rec;
            }
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

    /**
     * 强制过滤非主板股票（竞价/尾盘推荐专用）
     * 只保留600xxx（上证主板）和000xxx（深证主板）的股票
     * 移除300xxx（创业板）、688xxx（科创板）等非主板股票
     */
    private void filterMainBoardOnly(StrategyRecommendation rec) {
        if (rec == null || rec.getItems() == null || rec.getItems().isEmpty()) return;
        
        List<StrategyRecommendation.RecommendItem> filtered = new ArrayList<>();
        for (StrategyRecommendation.RecommendItem item : rec.getItems()) {
            String code = item.getCode();
            if (code == null || code.isEmpty()) {
                // 没有代码的条目保留（可能是板块推荐等）
                filtered.add(item);
                continue;
            }
            // 去除可能的前缀如 sh/sz/SH/SZ
            String cleanCode = code.replaceAll("(?i)^(sh|sz)", "").trim();
            if (cleanCode.startsWith("600") || cleanCode.startsWith("601") || 
                    cleanCode.startsWith("603") || cleanCode.startsWith("605") || 
                    cleanCode.startsWith("000") || cleanCode.startsWith("001") ||
                    cleanCode.startsWith("002")) {
                // 更新为纯数字代码
                item.setCode(cleanCode);
                filtered.add(item);
                Log.d(TAG, "保留主板股票: " + item.getName() + "(" + cleanCode + ")");
            } else {
                Log.w(TAG, "过滤非主板股票: " + item.getName() + "(" + code + ") - 非600/000开头");
            }
        }
        
        if (filtered.size() < rec.getItems().size()) {
            Log.d(TAG, "主板过滤: " + rec.getItems().size() + " -> " + filtered.size() + " 只");
        }
        
        // 如果过滤后还有数据，使用过滤后的结果
        // 如果全部被过滤掉了，保留原始数据但在摘要中添加提示
        if (!filtered.isEmpty()) {
            rec.setItems(filtered);
        } else {
            Log.w(TAG, "主板过滤后无剩余股票，保留原始推荐并标注");
            String originalSummary = rec.getSummary() != null ? rec.getSummary() : "";
            rec.setSummary(originalSummary + "（注意：以下部分推荐含非主板股票，请自行甄别）");
        }
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
     * 开盘竞价推荐 Prompt（昨日龙虎榜+热搜+技术指标+集合竞价）
     */
    private String getAuctionPrompt() {
        String prompt = promptLoader.loadAuctionStrategyPrompt();
        if (prompt != null && !prompt.isEmpty()) return prompt;

        return "你是一名专精A股短线交易的实战高手，擅长综合昨日龙虎榜、热搜榜、技术指标和集合竞价情况，精准狙击开盘短线机会。\n\n" +
                "**重要限制：**\n" +
                "1. 只推荐主板股票（上证600xxx、深证000xxx），严禁推荐创业板(300xxx)、科创板(688xxx)和北交所\n" +
                "2. 绝对禁止推荐已经涨停或接近涨停的股票（距涨停价<1%不可推荐）\n" +
                "3. 只推荐流通市值30-120亿的中小盘股\n\n" +
                "**分析维度（必须涵盖）：**\n" +
                "1. 昨日龙虎榜：净买入额、游资/机构席位参与、资金占比\n" +
                "2. 热搜/题材热度：题材持续性、市场关注度\n" +
                "3. 技术指标：MACD/KDJ/RSI/均线多头排列/量能变化/突破信号\n" +
                "4. 集合竞价信号：高开幅度预判、量比、匹配量变化\n" +
                "综合评分 = 龙虎榜资金(25%) + 技术信号(25%) + 题材热度(20%) + 竞价表现(20%) + 情绪(10%)\n\n" +
                "请输出JSON格式，包含以下字段：\n" +
                "{\n" +
                "  \"title\": \"开盘竞价推荐\",\n" +
                "  \"summary\": \"今日竞价策略概述(50字内)\",\n" +
                "  \"confidence\": 0-100,\n" +
                "  \"risk_level\": \"high\",\n" +
                "  \"items\": [\n" +
                "    {\n" +
                "      \"name\": \"股票名称\",\n" +
                "      \"code\": \"股票代码(6位数字)\",\n" +
                "      \"reason\": \"推荐理由(30字内，需引用龙虎榜+技术指标+竞价信号)\",\n" +
                "      \"highlight\": \"核心亮点(如:龙虎榜净买入+MACD金叉+竞价高开)\",\n" +
                "      \"score\": 0-100,\n" +
                "      \"entry_timing\": \"竞价介入策略\",\n" +
                "      \"stop_loss\": \"止损位(基于技术支撑位)\",\n" +
                "      \"tags\": [\"龙虎榜\", \"技术突破\", \"竞价强势\", \"热搜\"]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"market_sentiment\": \"市场情绪周期(冰点/修复/高潮/分化/退潮)\",\n" +
                "  \"main_line\": \"当前最强主线题材\",\n" +
                "  \"strategy_note\": \"仓位管理与风控纪律提醒\",\n" +
                "  \"analysis_text\": \"详细分析(含龙虎榜分析+技术面验证+竞价信号)\"\n" +
                "}\n\n" +
                "只推荐5只股票，每只必须给出技术指标依据和止损位。\n" +
                "**重要：只推荐主板股票（600xxx/000xxx），不要推荐创业板/科创板/港股/美股，不要推荐已涨停或接近涨停的股票**";
    }

    /**
     * 尾盘推荐 Prompt（大盘走势+板块轮动+国际国内局势+主力资金技术指标）
     */
    private String getClosingPrompt() {
        String prompt = promptLoader.loadClosingStrategyPrompt();
        if (prompt != null && !prompt.isEmpty()) return prompt;

        return "你是一名专精A股大势研判和板块轮动分析的实战高手，擅长从宏观大盘走势、板块资金流向、国际国内局势以及主力资金动向中，精准发现尾盘低吸机会。\n\n" +
                "**重要限制：**\n" +
                "1. 只推荐主板股票（上证600xxx、深证000xxx），严禁推荐创业板(300xxx)、科创板(688xxx)和北交所\n" +
                "2. 绝对禁止推荐已经涨停或接近涨停的股票（距涨停价<1%不可推荐）\n" +
                "3. 只推荐流通市值30-120亿的中小盘股\n\n" +
                "**分析维度（自上而下，必须涵盖）：**\n" +
                "1. 大盘走势：全天走势形态、量价配合、尾盘趋势、技术位\n" +
                "2. 板块轮动：领涨/领跌板块、资金流向、明日轮动预判\n" +
                "3. 国际国内局势：外盘影响、政策动向、地缘因素\n" +
                "4. 主力资金+技术指标：均线支撑/MACD/KDJ/RSI/成交量变化\n" +
                "综合评分 = 大盘环境(25%) + 板块趋势(25%) + 技术面信号(20%) + 宏观因素(15%) + 隔夜风控(15%)\n\n" +
                "请输出JSON格式，包含以下字段：\n" +
                "{\n" +
                "  \"title\": \"尾盘推荐\",\n" +
                "  \"summary\": \"尾盘策略概述(50字内，含大盘走势+板块方向)\",\n" +
                "  \"confidence\": 0-100,\n" +
                "  \"risk_level\": \"low/medium/high\",\n" +
                "  \"items\": [\n" +
                "    {\n" +
                "      \"name\": \"股票名称\",\n" +
                "      \"code\": \"股票代码(6位数字)\",\n" +
                "      \"reason\": \"推荐理由(30字内，引用板块走势+技术指标)\",\n" +
                "      \"highlight\": \"核心亮点(如:板块龙头回调/主力资金流入/技术超卖)\",\n" +
                "      \"score\": 0-100,\n" +
                "      \"entry_timing\": \"买入时间和价格策略\",\n" +
                "      \"stop_loss\": \"止损位(基于技术支撑位)\",\n" +
                "      \"next_day_plan\": \"次日操作预案(高开/平开/低开怎么做)\",\n" +
                "      \"tags\": [\"板块轮动\", \"技术支撑\", \"主力资金\", \"低吸\"]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"market_sentiment\": \"全天市场情绪总结(冰点/修复/高潮/分化/退潮)\",\n" +
                "  \"main_line\": \"今日最强板块方向及明日轮动预判\",\n" +
                "  \"overnight_risk\": \"隔夜风险评估(外盘/国际局势/政策面/周末效应)\",\n" +
                "  \"strategy_note\": \"尾盘风控纪律(单只最多3成仓，隔夜亏损不超3%)\",\n" +
                "  \"analysis_text\": \"详细分析(含大盘复盘+板块分析+国际国内局势+技术面)\"\n" +
                "}\n\n" +
                "只推荐5只股票，隔夜持仓次日择机卖出，必须给出止损位和次日操作预案。\n" +
                "**重要：只推荐主板股票（600xxx/000xxx），不要推荐创业板/科创板/港股/美股，不要推荐已涨停或接近涨停的股票**";
    }
}
