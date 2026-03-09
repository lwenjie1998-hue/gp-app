package com.gp.stockapp;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.gp.stockapp.api.GLM4Client;
import com.gp.stockapp.model.MarketAnalysis;
import com.gp.stockapp.model.MarketIndex;
import com.gp.stockapp.model.StockNews;
import com.gp.stockapp.model.StrategyRecommendation;
import com.gp.stockapp.repository.StockRepository;
import com.gp.stockapp.service.StockDataService;
import com.gp.stockapp.service.AIRecommendationService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 主界面 - GP-Tool
 * 展示三大指数实时行情和AI分析结果
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    // 上证指数 UI
    private TextView tvShPoint, tvShChange, tvShPercent, tvShVolume, tvShRange;
    // 深证成指 UI
    private TextView tvSzPoint, tvSzChange, tvSzPercent, tvSzVolume;
    // 创业板指 UI
    private TextView tvCyPoint, tvCyChange, tvCyPercent, tvCyVolume;
    // AI分析 UI
    private TextView tvSentiment, tvTrend, tvRisk, tvSuggestion;
    private TextView tvAnalysisDetail, tvToggleDetail, tvAnalysisTime;
    // 新闻 UI
    private LinearLayout cardNews;
    private TextView tvNewsList;
    private TextView tvNewsMore;
    private LinearLayout layoutNewsItems;
    // 状态 UI
    private TextView tvStatus, tvLastUpdate;
    private View statusIndicator;
    // 按钮
    private Button btnStartService, btnSyncHistory;

    private StockRepository stockRepository;
    private GLM4Client glm4Client;
    private boolean isDetailExpanded = false;
    private boolean isServiceRunning = false;

    // 数据更新广播
    public static final String ACTION_DATA_UPDATED = "com.gp.stockapp.DATA_UPDATED";
    public static final String ACTION_ANALYSIS_UPDATED = "com.gp.stockapp.ANALYSIS_UPDATED";
    public static final String ACTION_STRATEGY_UPDATED = "com.gp.stockapp.STRATEGY_UPDATED";

    // 策略推荐 UI
    private TextView tvSectorMore, tvSectorList;
    private LinearLayout layoutSectorItems;

    private TextView tvAuctionMore, tvAuctionList;
    private LinearLayout layoutAuctionItems;

    private TextView tvClosingMore, tvClosingList;
    private LinearLayout layoutClosingItems;

    private BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_DATA_UPDATED.equals(intent.getAction())) {
                refreshMarketData();
                refreshNews();
            } else if (ACTION_ANALYSIS_UPDATED.equals(intent.getAction())) {
                refreshAnalysis();
            } else if (ACTION_STRATEGY_UPDATED.equals(intent.getAction())) {
                refreshStrategyRecommendations();
            } else if (StockDataService.ACTION_HISTORY_SYNC_STATUS.equals(intent.getAction())) {
                boolean running = intent.getBooleanExtra(StockDataService.EXTRA_HISTORY_SYNC_RUNNING, false);
                String message = intent.getStringExtra(StockDataService.EXTRA_HISTORY_SYNC_MESSAGE);
                updateHistorySyncButtonState(running);
                if (message != null && !message.isEmpty()) {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initToolbar();
        initData();
        requestPermissions();
    }

    private void initToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "设置").setIcon(android.R.drawable.ic_menu_preferences)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            showSettingsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initViews() {
        // 上证指数
        tvShPoint = findViewById(R.id.tv_sh_point);
        tvShChange = findViewById(R.id.tv_sh_change);
        tvShPercent = findViewById(R.id.tv_sh_percent);
        tvShVolume = findViewById(R.id.tv_sh_volume);
        tvShRange = findViewById(R.id.tv_sh_range);

        // 深证成指
        tvSzPoint = findViewById(R.id.tv_sz_point);
        tvSzChange = findViewById(R.id.tv_sz_change);
        tvSzPercent = findViewById(R.id.tv_sz_percent);
        tvSzVolume = findViewById(R.id.tv_sz_volume);

        // 创业板指
        tvCyPoint = findViewById(R.id.tv_cy_point);
        tvCyChange = findViewById(R.id.tv_cy_change);
        tvCyPercent = findViewById(R.id.tv_cy_percent);
        tvCyVolume = findViewById(R.id.tv_cy_volume);

        // AI分析
        tvSentiment = findViewById(R.id.tv_sentiment);
        tvTrend = findViewById(R.id.tv_trend);
        tvRisk = findViewById(R.id.tv_risk);
        tvSuggestion = findViewById(R.id.tv_suggestion);
        tvAnalysisDetail = findViewById(R.id.tv_analysis_detail);
        tvToggleDetail = findViewById(R.id.tv_toggle_detail);
        tvAnalysisTime = findViewById(R.id.tv_analysis_time);

        // 新闻
        cardNews = findViewById(R.id.card_news);
        tvNewsList = findViewById(R.id.tv_news_list);
        tvNewsMore = findViewById(R.id.tv_news_more);
        layoutNewsItems = findViewById(R.id.layout_news_items);
        
        // 点击整个新闻卡片或"查看更多"都跳转到新闻列表页
        View.OnClickListener newsClickListener = v -> {
            startActivity(new Intent(MainActivity.this, NewsActivity.class));
        };
        cardNews.setOnClickListener(newsClickListener);
        tvNewsMore.setOnClickListener(newsClickListener);
        layoutNewsItems.setOnClickListener(newsClickListener);

        // 板块推荐
        tvSectorMore = findViewById(R.id.tv_sector_more);
        tvSectorList = findViewById(R.id.tv_sector_list);
        layoutSectorItems = findViewById(R.id.layout_sector_items);
        findViewById(R.id.card_sector).setOnClickListener(v -> openRecommendationDetail("sector"));

        // 开盘竞价推荐
        tvAuctionMore = findViewById(R.id.tv_auction_more);
        tvAuctionList = findViewById(R.id.tv_auction_list);
        layoutAuctionItems = findViewById(R.id.layout_auction_items);
        findViewById(R.id.card_auction).setOnClickListener(v -> openRecommendationDetail("auction"));

        // 尾盘推荐
        tvClosingMore = findViewById(R.id.tv_closing_more);
        tvClosingList = findViewById(R.id.tv_closing_list);
        layoutClosingItems = findViewById(R.id.layout_closing_items);
        findViewById(R.id.card_closing).setOnClickListener(v -> openRecommendationDetail("closing"));

        // 状态
        tvStatus = findViewById(R.id.tv_status);
        tvLastUpdate = findViewById(R.id.tv_last_update);
        statusIndicator = findViewById(R.id.status_indicator);

        // 按钮
        btnStartService = findViewById(R.id.btn_start_service);
        btnSyncHistory = findViewById(R.id.btn_sync_history);

        btnStartService.setOnClickListener(v -> {
            if (isServiceRunning) {
                onStopService(v);
            } else {
                onStartService(v);
            }
        });

        btnSyncHistory.setOnClickListener(v -> triggerHistorySync());

        // 详情展开/收起
        tvToggleDetail.setOnClickListener(v -> toggleAnalysisDetail());
    }

    private void initData() {
        stockRepository = StockRepository.getInstance(this);
        glm4Client = GLM4Client.getInstance();

        // 加载API密钥
        String apiKey = getApiKey();
        if (!apiKey.isEmpty()) {
            glm4Client.setApiKey(apiKey);
        }

        // 加载已有数据
        refreshMarketData();
        refreshAnalysis();
        refreshNews();
        refreshStrategyRecommendations();
    }

    // ===== 服务控制 =====

    private void onStartService(View view) {
        String apiKey = getApiKey();
        if (apiKey.isEmpty()) {
            showSettingsDialog();
            Toast.makeText(this, "请先设置API密钥", Toast.LENGTH_SHORT).show();
            return;
        }

        glm4Client.setApiKey(apiKey);

        // 启动数据抓取服务
        Intent dataIntent = new Intent(this, StockDataService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(dataIntent);
        } else {
            startService(dataIntent);
        }

        // 启动AI分析服务
        Intent aiIntent = new Intent(this, AIRecommendationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(aiIntent);
        } else {
            startService(aiIntent);
        }
        
        // 触发板块推荐强制刷新
        Intent sectorIntent = new Intent(this, AIRecommendationService.class);
        sectorIntent.setAction(AIRecommendationService.ACTION_FORCE_SECTOR);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(sectorIntent);
        } else {
            startService(sectorIntent);
        }

        isServiceRunning = true;
        updateServiceStatus(true);
        Toast.makeText(this, "监控已启动", Toast.LENGTH_SHORT).show();
    }

    private void onStopService(View view) {
        // 先停止前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopService(new Intent(this, StockDataService.class));
            stopService(new Intent(this, AIRecommendationService.class));
        } else {
            stopService(new Intent(this, StockDataService.class));
            stopService(new Intent(this, AIRecommendationService.class));
        }

        isServiceRunning = false;
        updateServiceStatus(false);
        Toast.makeText(this, "监控已停止", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "用户停止监控服务");
    }

    private void updateServiceStatus(boolean running) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setSize(16, 16);

        if (running) {
            dot.setColor(0xFF43A047); // 绿色
            statusIndicator.setBackground(dot);
            tvStatus.setText("监控运行中");
            tvStatus.setTextColor(0xFF43A047);
            btnStartService.setText("停止监控");
            btnStartService.setBackgroundResource(R.drawable.btn_outline);
            btnStartService.setTextColor(0xFF666666);
        } else {
            dot.setColor(0xFF9E9E9E); // 灰色
            statusIndicator.setBackground(dot);
            tvStatus.setText("服务未启动");
            tvStatus.setTextColor(0xFF666666);
            btnStartService.setText("开始监控");
            btnStartService.setBackgroundResource(R.drawable.btn_primary);
            btnStartService.setTextColor(0xFFFFFFFF);
        }
    }

    private void updateHistorySyncButtonState(boolean syncing) {
        if (btnSyncHistory == null) {
            return;
        }
        btnSyncHistory.setEnabled(!syncing);
        btnSyncHistory.setText(syncing ? "更新中..." : "更新数据");
        if (syncing) {
            btnSyncHistory.setBackgroundResource(R.drawable.btn_outline);
            btnSyncHistory.setTextColor(0xFF999999);
        } else {
            btnSyncHistory.setBackgroundResource(R.drawable.btn_outline);
            btnSyncHistory.setTextColor(0xFF666666);
        }
    }

    private void triggerHistorySync() {
        updateHistorySyncButtonState(true);
        Intent intent = new Intent(this, StockDataService.class);
        intent.setAction(StockDataService.ACTION_SYNC_HISTORY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    // ===== 数据刷新 =====

    private void refreshMarketData() {
        List<MarketIndex> indices = stockRepository.getMarketIndices();
        if (indices == null || indices.isEmpty()) return;

        for (MarketIndex index : indices) {
            if (index.getIndexCode() == null) continue;

            switch (index.getIndexCode()) {
                case MarketIndex.SH_INDEX:
                    updateIndexUI(tvShPoint, tvShChange, tvShPercent, tvShVolume, tvShRange, index);
                    break;
                case MarketIndex.SZ_INDEX:
                    updateIndexUI(tvSzPoint, tvSzChange, tvSzPercent, tvSzVolume, null, index);
                    break;
                case MarketIndex.CY_INDEX:
                    updateIndexUI(tvCyPoint, tvCyChange, tvCyPercent, tvCyVolume, null, index);
                    break;
            }
        }

        // 更新最后刷新时间
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
        tvLastUpdate.setText("更新: " + sdf.format(new Date()));
    }

    /**
     * 通用指数UI更新方法
     * @param tvPoint 点位TextView
     * @param tvChange 涨跌点数TextView
     * @param tvPercent 涨跌幅TextView
     * @param tvVolume 成交额TextView
     * @param tvRange 振幅TextView（可为null）
     * @param index 指数数据
     */
    private void updateIndexUI(TextView tvPoint, TextView tvChange, 
                               TextView tvPercent, TextView tvVolume, 
                               TextView tvRange, MarketIndex index) {
        tvPoint.setText(String.format(Locale.CHINA, "%.2f", index.getCurrentPoint()));
        tvChange.setText(index.getFormattedChangePoint());
        tvPercent.setText(index.getFormattedChangePercent());
        
        // 成交额 + 放量/缩量标识
        String volumeText = "成交额: " + index.getFormattedAmount();
        String volumeChange = index.getVolumeChangeText();
        if (!volumeChange.isEmpty()) {
            volumeText += " (" + volumeChange + ")";
        }
        tvVolume.setText(volumeText);

        int color = index.getChangeColor();
        tvChange.setTextColor(color);
        tvPercent.setTextColor(color);
        
        // 振幅（如果提供了TextView）
        if (tvRange != null && index.getHigh() > 0 && index.getLow() > 0 && index.getPreClose() > 0) {
            double range = (index.getHigh() - index.getLow()) / index.getPreClose() * 100;
            tvRange.setText(String.format(Locale.CHINA, "振幅: %.2f%%", range));
        }
    }

    private void refreshAnalysis() {
        MarketAnalysis analysis = stockRepository.getLatestMarketAnalysis();
        if (analysis == null) return;

        tvSentiment.setText(analysis.getSentimentText());
        tvSentiment.setTextColor(analysis.getSentimentColor());

        tvTrend.setText(analysis.getTrendText());
        tvRisk.setText(analysis.getRiskText());
        tvRisk.setTextColor(analysis.getRiskColor());

        if (analysis.getSuggestion() != null && !analysis.getSuggestion().isEmpty()) {
            tvSuggestion.setText(analysis.getSuggestion());
        }

        if (analysis.getAnalysisText() != null && !analysis.getAnalysisText().isEmpty()) {
            tvAnalysisDetail.setText(analysis.getAnalysisText());
        }

        // 显示分析时间
        if (analysis.getTimestamp() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.CHINA);
            tvAnalysisTime.setText(sdf.format(new Date(analysis.getTimestamp())));
        }
    }

    private void refreshNews() {
        List<StockNews> newsList = stockRepository.getLatestNews(20);
        if (newsList == null || newsList.isEmpty()) {
            tvNewsList.setVisibility(View.VISIBLE);
            tvNewsList.setText("暂无新闻数据");
            layoutNewsItems.setVisibility(View.GONE);
            tvNewsMore.setVisibility(View.GONE);
            return;
        }

        tvNewsList.setVisibility(View.GONE);
        layoutNewsItems.setVisibility(View.VISIBLE);
        layoutNewsItems.removeAllViews();

        // 主界面只展示前3条标题，点击查看详情
        int previewCount = Math.min(3, newsList.size());
        
        for (int i = 0; i < previewCount; i++) {
            StockNews news = newsList.get(i);
            if (news.getTitle() == null || news.getTitle().isEmpty()) continue;

            // 新闻条目容器
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            int padV = dpToPx(6);
            int padH = dpToPx(2);
            itemLayout.setPadding(padH, padV, padH, padV);
            
            // 点击单条也可以跳转
            itemLayout.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, NewsActivity.class)));

            // 标题 (只显示标题，去除时间和来源，界面更简洁)
            TextView tvTitle = new TextView(this);
            tvTitle.setText("• " + news.getTitle());
            tvTitle.setTextColor(0xFFFFFFFF);
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvTitle.setMaxLines(1);
            tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            
            // 重大新闻高亮显示
            if (news.isHighImpact()) {
                tvTitle.setTextColor(0xFFFF8A80); // 浅红色高亮
                tvTitle.setTypeface(null, Typeface.BOLD);
            }
            itemLayout.addView(tvTitle);

            layoutNewsItems.addView(itemLayout);

            // 分割线（最后一条不加）
            if (i < previewCount - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(0x15FFFFFF);
                LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
                divParams.topMargin = dpToPx(2);
                divParams.bottomMargin = dpToPx(2);
                divider.setLayoutParams(divParams);
                layoutNewsItems.addView(divider);
            }
        }

        // 总是显示查看更多，引导用户点击
        tvNewsMore.setVisibility(View.VISIBLE);
        tvNewsMore.setText("查看全部 ▶");
    }

    /**
     * 格式化新闻列表为文本
     */
    private String formatNewsList(List<StockNews> newsList) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.CHINA);
        SimpleDateFormat sdfFull = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
        long now = System.currentTimeMillis();
        long todayStart = now - (now % 86400000);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < newsList.size(); i++) {
            StockNews news = newsList.get(i);
            if (news.getTitle() == null || news.getTitle().isEmpty()) continue;

            sb.append("• ").append(news.getTitle());

            StringBuilder meta = new StringBuilder();
            if (news.getSource() != null && !news.getSource().isEmpty()) {
                meta.append(news.getSource());
            }
            if (news.getPublishTime() > 0) {
                if (meta.length() > 0) meta.append("  ");
                if (news.getPublishTime() >= todayStart) {
                    meta.append(sdf.format(new Date(news.getPublishTime())));
                } else {
                    meta.append(sdfFull.format(new Date(news.getPublishTime())));
                }
            }
            if (meta.length() > 0) {
                sb.append("\n  ").append(meta);
            }

            if (i < newsList.size() - 1) {
                sb.append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * 显示全部新闻的弹窗
     */
    private void showNewsDialog() {
        List<StockNews> newsList = stockRepository.getLatestNews(20);
        if (newsList == null || newsList.isEmpty()) {
            Toast.makeText(this, "暂无新闻数据", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建滚动视图
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setPadding(32, 24, 32, 24);

        TextView tvContent = new TextView(this);
        tvContent.setText(formatNewsList(newsList));
        tvContent.setTextSize(13);
        tvContent.setTextColor(0xFF555555);
        tvContent.setLineSpacing(0, 1.5f);
        scrollView.addView(tvContent);

        new AlertDialog.Builder(this)
                .setTitle("📰 市场要闻 (" + newsList.size() + "条)")
                .setView(scrollView)
                .setPositiveButton("关闭", null)
                .show();
    }

    // ===== 策略推荐刷新 =====

    private void refreshStrategyRecommendations() {
        refreshSectorRecommendation();
        refreshAuctionRecommendation();
        refreshClosingRecommendation();
    }

    private void refreshSectorRecommendation() {
        StrategyRecommendation rec = stockRepository.getSectorRecommendation();
        if (rec == null || rec.getItems() == null || rec.getItems().isEmpty()) {
            tvSectorList.setVisibility(View.VISIBLE);
            tvSectorList.setText("等待AI分析...");
            layoutSectorItems.setVisibility(View.GONE);
            tvSectorMore.setVisibility(View.GONE);
            return;
        }

        tvSectorList.setVisibility(View.GONE);
        layoutSectorItems.setVisibility(View.VISIBLE);
        renderRecommendItems(layoutSectorItems, rec, R.drawable.badge_red);
        tvSectorMore.setVisibility(View.VISIBLE);
    }

    private void refreshAuctionRecommendation() {
        StrategyRecommendation rec = stockRepository.getAuctionRecommendation();
        if (rec == null || rec.getItems() == null || rec.getItems().isEmpty()) {
            tvAuctionList.setVisibility(View.VISIBLE);
            tvAuctionList.setText("暂无竞价推荐，进入详情页下拉可手动生成");
            layoutAuctionItems.setVisibility(View.GONE);
            tvAuctionMore.setVisibility(View.GONE);
            return;
        }

        tvAuctionList.setVisibility(View.GONE);
        layoutAuctionItems.setVisibility(View.VISIBLE);
        renderRecommendItems(layoutAuctionItems, rec, R.drawable.badge_red);
        tvAuctionMore.setVisibility(View.VISIBLE);
    }

    private void refreshClosingRecommendation() {
        StrategyRecommendation rec = stockRepository.getClosingRecommendation();
        if (rec == null || rec.getItems() == null || rec.getItems().isEmpty()) {
            tvClosingList.setVisibility(View.VISIBLE);
            tvClosingList.setText("暂无尾盘推荐，进入详情页下拉可手动生成");
            layoutClosingItems.setVisibility(View.GONE);
            tvClosingMore.setVisibility(View.GONE);
            return;
        }

        tvClosingList.setVisibility(View.GONE);
        layoutClosingItems.setVisibility(View.VISIBLE);
        renderRecommendItems(layoutClosingItems, rec, R.drawable.badge_red);
        tvClosingMore.setVisibility(View.VISIBLE);
    }

    /**
     * 动态渲染推荐条目到容器中（名称 + 右侧推荐理由标签）
     */
    private void renderRecommendItems(LinearLayout container, StrategyRecommendation rec, int tagBgRes) {
        container.removeAllViews();

        List<StrategyRecommendation.RecommendItem> items = rec.getItems();
        if (items == null || items.isEmpty()) return;

        int previewCount = Math.min(3, items.size());

        for (int i = 0; i < previewCount; i++) {
            StrategyRecommendation.RecommendItem item = items.get(i);
            if (item.getName() == null || item.getName().isEmpty()) continue;

            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int padV = dpToPx(5);
            int padH = dpToPx(2);
            itemLayout.setPadding(padH, padV, padH, padV);

            // 左侧名称
            TextView tvTitle = new TextView(this);
            tvTitle.setText("• " + item.getName());
            tvTitle.setTextColor(0xFFFFFFFF);
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvTitle.setMaxLines(1);
            tvTitle.setSingleLine(true);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tvTitle.setLayoutParams(nameParams);
            itemLayout.addView(tvTitle);

            // 右侧推荐理由标签
            String reasonText = getReasonTag(item);
            if (reasonText != null && !reasonText.isEmpty()) {
                TextView tvReason = new TextView(this);
                tvReason.setText(reasonText);
                tvReason.setTextColor(0xFFFFFFFF);
                tvReason.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                tvReason.setTypeface(null, Typeface.BOLD);
                tvReason.setMaxLines(1);
                tvReason.setSingleLine(true);
                tvReason.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tvReason.setBackgroundResource(R.drawable.tag_reason_bg);
                LinearLayout.LayoutParams reasonParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                reasonParams.setMarginStart(dpToPx(4));
                tvReason.setLayoutParams(reasonParams);
                itemLayout.addView(tvReason);
            }

            container.addView(itemLayout);

            // 分割线（最后一条不加）
            if (i < previewCount - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(0x15FFFFFF);
                LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
                divParams.topMargin = dpToPx(2);
                divParams.bottomMargin = dpToPx(2);
                divider.setLayoutParams(divParams);
                container.addView(divider);
            }
        }
    }

    /**
     * 获取推荐条目的理由标签文本（优先highlight，其次reason截断）
     */
    private String getReasonTag(StrategyRecommendation.RecommendItem item) {
        if (item.getHighlight() != null && !item.getHighlight().isEmpty()) {
            return item.getHighlight();
        }
        if (item.getReason() != null && !item.getReason().isEmpty()) {
            return item.getReason();
        }
        if (item.getTags() != null && !item.getTags().isEmpty()) {
            return item.getTags().get(0);
        }
        return null;
    }

    /**
     * 打开推荐详情页
     */
    private void openRecommendationDetail(String type) {
        Intent intent = new Intent(this, RecommendationActivity.class);
        intent.putExtra("type", type);
        startActivity(intent);
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    // ===== 策略详情展开/收起 =====

    // ===== UI交互 =====

    private void toggleAnalysisDetail() {
        isDetailExpanded = !isDetailExpanded;
        if (isDetailExpanded) {
            tvAnalysisDetail.setVisibility(View.VISIBLE);
            tvToggleDetail.setText("收起详细分析 ▲");
        } else {
            tvAnalysisDetail.setVisibility(View.GONE);
            tvToggleDetail.setText("查看详细分析 ▼");
        }
    }

    // ===== 设置对话框 =====

    private void showSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        EditText etApiKey = dialogView.findViewById(R.id.et_dialog_api_key);

        String savedKey = getApiKey();
        if (!savedKey.isEmpty()) {
            etApiKey.setText(savedKey);
        }

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String apiKey = etApiKey.getText().toString().trim();
                    if (!apiKey.isEmpty()) {
                        saveApiKey(apiKey);
                        glm4Client.setApiKey(apiKey);
                        Toast.makeText(this, "API密钥已保存", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("测试连接", (dialog, which) -> {
                    String apiKey = etApiKey.getText().toString().trim();
                    if (apiKey.isEmpty()) {
                        Toast.makeText(this, "请输入API密钥", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveApiKey(apiKey);
                    glm4Client.setApiKey(apiKey);
                    testApiConnection();
                })
                .show();
    }

    private void testApiConnection() {
        Toast.makeText(this, "正在测试连接...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean success = glm4Client.testConnection();
            runOnUiThread(() -> {
                if (success) {
                    Toast.makeText(this, "API连接成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "API连接失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    // ===== API Key 存取 =====

    private void saveApiKey(String apiKey) {
        getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit()
                .putString("api_key", apiKey)
                .apply();
    }

    private String getApiKey() {
        return getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getString("api_key", "");
    }

    // ===== 权限 =====

    private void requestPermissions() {
        List<String> needed = new ArrayList<>();
        String[] permissions = {
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.POST_NOTIFICATIONS
        };

        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // ===== 生命周期 =====

    @Override
    protected void onResume() {
        super.onResume();

        // 注册广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_DATA_UPDATED);
        filter.addAction(ACTION_ANALYSIS_UPDATED);
        filter.addAction(ACTION_STRATEGY_UPDATED);
        filter.addAction(StockDataService.ACTION_HISTORY_SYNC_STATUS);
        LocalBroadcastManager.getInstance(this).registerReceiver(dataReceiver, filter);

        // 刷新数据
        refreshMarketData();
        refreshAnalysis();
        refreshNews();
        refreshStrategyRecommendations();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
