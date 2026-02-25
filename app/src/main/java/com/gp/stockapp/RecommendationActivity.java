package com.gp.stockapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.gp.stockapp.model.StrategyRecommendation;
import com.gp.stockapp.repository.StockRepository;
import com.gp.stockapp.service.AIRecommendationService;
import com.gp.stockapp.utils.StockAppHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecommendationActivity extends AppCompatActivity {

    private LinearLayout layoutContent;
    private SwipeRefreshLayout swipeRefreshLayout;
    private StockRepository stockRepository;
    private String recommendationType; // "sector", "auction", "closing"
    private BroadcastReceiver strategyUpdateReceiver;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recommendation);

        // 获取推荐类型
        recommendationType = getIntent().getStringExtra("type");
        if (recommendationType == null) {
            recommendationType = "sector";
        }

        // 设置 Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getTitle(recommendationType));
        }

        // 初始化 Repository
        stockRepository = StockRepository.getInstance(this);
        handler = new Handler(Looper.getMainLooper());

        // 初始化视图
        layoutContent = findViewById(R.id.layout_content);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);

        // 注册广播接收器
        registerStrategyUpdateReceiver();

        // 加载数据
        loadRecommendation(false);

        // 下拉刷新 - 触发AI重新分析
        swipeRefreshLayout.setOnRefreshListener(() -> loadRecommendation(true));
    }

    private String getTitle(String type) {
        switch (type) {
            case "auction":
                return "开盘竞价推荐";
            case "closing":
                return "尾盘推荐";
            case "sector":
            default:
                return "板块推荐";
        }
    }
    
    private void registerStrategyUpdateReceiver() {
        strategyUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // 收到策略更新广播，重新加载数据
                handler.postDelayed(() -> loadRecommendation(false), 500);
            }
        };
        
        IntentFilter filter = new IntentFilter(MainActivity.ACTION_STRATEGY_UPDATED);
        LocalBroadcastManager.getInstance(this).registerReceiver(strategyUpdateReceiver, filter);
    }

    private void loadRecommendation(boolean forceRefresh) {
        swipeRefreshLayout.setRefreshing(true);
        
        // 如果是强制刷新，发送intent触发AI分析
        if (forceRefresh) {
            Intent intent = new Intent(this, AIRecommendationService.class);
            switch (recommendationType) {
                case "auction":
                    intent.setAction(AIRecommendationService.ACTION_FORCE_AUCTION);
                    break;
                case "closing":
                    intent.setAction(AIRecommendationService.ACTION_FORCE_CLOSING);
                    break;
                case "sector":
                    intent.setAction(AIRecommendationService.ACTION_FORCE_SECTOR);
                    break;
            }
            startService(intent);
            
            // 等待分析完成（延迟加载），给AI分析留出时间
            handler.postDelayed(() -> loadDataFromRepository(), 3000);
        } else {
            // 直接从repository加载
            loadDataFromRepository();
        }
    }
    
    private void loadDataFromRepository() {
        new Thread(() -> {
            StrategyRecommendation rec = null;
            
            switch (recommendationType) {
                case "sector":
                    rec = stockRepository.getSectorRecommendation();
                    break;
                case "auction":
                    rec = stockRepository.getAuctionRecommendation();
                    break;
                case "closing":
                    rec = stockRepository.getClosingRecommendation();
                    break;
            }
            
            StrategyRecommendation finalRec = rec;
            runOnUiThread(() -> {
                swipeRefreshLayout.setRefreshing(false);
                if (finalRec != null && finalRec.getItems() != null && !finalRec.getItems().isEmpty()) {
                    displayRecommendation(finalRec);
                } else {
                    displayEmptyView();
                }
            });
        }).start();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (strategyUpdateReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(strategyUpdateReceiver);
        }
    }

    private void displayEmptyView() {
        layoutContent.removeAllViews();
        
        TextView tvEmpty = new TextView(this);
        tvEmpty.setText("暂无推荐数据");
        tvEmpty.setTextColor(0xFF666666);
        tvEmpty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setPadding(0, dpToPx(40), 0, 0);
        layoutContent.addView(tvEmpty);
    }

    private void displayRecommendation(StrategyRecommendation rec) {
        layoutContent.removeAllViews();

        // === 顶部信息卡片：摘要 + 时间 + 情绪/主线/风险 ===
        LinearLayout headerCard = new LinearLayout(this);
        headerCard.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable headerBg = new android.graphics.drawable.GradientDrawable();
        headerBg.setCornerRadius(dpToPx(12));
        headerBg.setColor(0xFFF5F5F5);
        headerCard.setBackground(headerBg);
        headerCard.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headerParams.bottomMargin = dpToPx(14);
        headerCard.setLayoutParams(headerParams);

        // 摘要
        if (rec.getSummary() != null && !rec.getSummary().isEmpty()) {
            TextView tvSummary = new TextView(this);
            tvSummary.setText(rec.getSummary());
            tvSummary.setTextColor(0xFF333333);
            tvSummary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tvSummary.setLineSpacing(0, 1.4f);
            headerCard.addView(tvSummary);
        }

        // 游资情绪/主线/隔夜风险信息行
        boolean hasSentiment = rec.getMarketSentiment() != null && !rec.getMarketSentiment().isEmpty();
        boolean hasMainLine = rec.getMainLine() != null && !rec.getMainLine().isEmpty();
        boolean hasOvernightRisk = rec.getOvernightRisk() != null && !rec.getOvernightRisk().isEmpty();

        if (hasSentiment || hasMainLine || hasOvernightRisk) {
            com.google.android.flexbox.FlexboxLayout infoFlow = new com.google.android.flexbox.FlexboxLayout(this);
            infoFlow.setFlexWrap(com.google.android.flexbox.FlexWrap.WRAP);
            infoFlow.setFlexDirection(com.google.android.flexbox.FlexDirection.ROW);
            LinearLayout.LayoutParams infoFlowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            infoFlowParams.topMargin = dpToPx(8);
            infoFlow.setLayoutParams(infoFlowParams);

            if (hasSentiment) {
                infoFlow.addView(createInfoChip("\ud83d\udcca " + rec.getMarketSentiment(), 0xFF1565C0, 0xFFE3F2FD));
            }
            if (hasMainLine) {
                infoFlow.addView(createInfoChip("\ud83d\udd25 " + rec.getMainLine(), 0xFFE65100, 0xFFFFF3E0));
            }
            if (hasOvernightRisk) {
                infoFlow.addView(createInfoChip("\u26a0 隔夜: " + rec.getOvernightRisk(), 0xFFB71C1C, 0xFFFFEBEE));
            }

            headerCard.addView(infoFlow);
        }

        // 风险等级和置信度
        if (rec.getRiskLevel() != null || rec.getConfidence() > 0) {
            LinearLayout riskRow = new LinearLayout(this);
            riskRow.setOrientation(LinearLayout.HORIZONTAL);
            riskRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams riskParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            riskParams.topMargin = dpToPx(8);
            riskRow.setLayoutParams(riskParams);

            if (rec.getRiskLevel() != null) {
                TextView tvRisk = new TextView(this);
                tvRisk.setText("风险: " + rec.getRiskText());
                tvRisk.setTextColor(rec.getRiskColor());
                tvRisk.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                tvRisk.setTypeface(null, Typeface.BOLD);
                riskRow.addView(tvRisk);
            }

            if (rec.getConfidence() > 0) {
                TextView tvConf = new TextView(this);
                tvConf.setText(String.format(Locale.CHINA, "  置信度: %.0f%%", rec.getConfidence()));
                tvConf.setTextColor(0xFF999999);
                tvConf.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                riskRow.addView(tvConf);
            }

            headerCard.addView(riskRow);
        }

        // 更新时间
        if (rec.getTimestamp() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
            TextView tvTime = new TextView(this);
            tvTime.setText("更新于 " + sdf.format(new Date(rec.getTimestamp())));
            tvTime.setTextColor(0xFFBDBDBD);
            tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            timeParams.topMargin = dpToPx(6);
            tvTime.setLayoutParams(timeParams);
            headerCard.addView(tvTime);
        }

        layoutContent.addView(headerCard);

        // === 推荐列表 ===
        List<StrategyRecommendation.RecommendItem> items = rec.getItems();
        for (int i = 0; i < items.size(); i++) {
            StrategyRecommendation.RecommendItem item = items.get(i);
            if (item.getName() == null || item.getName().isEmpty()) continue;

            View itemView = createRecommendItemView(item, i + 1);
            layoutContent.addView(itemView);
        }

        // === 详细分析 ===
        if (rec.getAnalysisText() != null && !rec.getAnalysisText().isEmpty()) {
            TextView tvAnalysisTitle = new TextView(this);
            tvAnalysisTitle.setText("详细分析");
            tvAnalysisTitle.setTextColor(0xFF333333);
            tvAnalysisTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tvAnalysisTitle.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams analysisTitleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            analysisTitleParams.topMargin = dpToPx(20);
            analysisTitleParams.bottomMargin = dpToPx(10);
            tvAnalysisTitle.setLayoutParams(analysisTitleParams);
            layoutContent.addView(tvAnalysisTitle);

            TextView tvAnalysis = new TextView(this);
            tvAnalysis.setText(rec.getAnalysisText());
            tvAnalysis.setTextColor(0xFF666666);
            tvAnalysis.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tvAnalysis.setLineSpacing(0, 1.5f);
            layoutContent.addView(tvAnalysis);
        }
    }

    private View createRecommendItemView(StrategyRecommendation.RecommendItem item, int index) {
        // 外层卡片容器 - 圆角阴影卡片
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dpToPx(10);
        card.setLayoutParams(cardParams);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));

        // 设置圆角白色背景
        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setCornerRadius(dpToPx(12));
        cardBg.setColor(0xFFFFFFFF);
        card.setBackground(cardBg);
        card.setElevation(dpToPx(2));

        // === 第一行：序号徽标 + 名称 + 代码 + 评分 ===
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        // 序号徽标（彩色圆形）
        TextView tvIndex = new TextView(this);
        tvIndex.setText(String.valueOf(index));
        tvIndex.setTextColor(0xFFFFFFFF);
        tvIndex.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvIndex.setTypeface(null, Typeface.BOLD);
        tvIndex.setGravity(Gravity.CENTER);
        int indexSize = dpToPx(22);
        LinearLayout.LayoutParams indexParams = new LinearLayout.LayoutParams(indexSize, indexSize);
        tvIndex.setLayoutParams(indexParams);
        // 根据排名设置不同颜色
        android.graphics.drawable.GradientDrawable indexBg = new android.graphics.drawable.GradientDrawable();
        indexBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        if (index == 1) indexBg.setColor(0xFFE53935);       // 第1名红色
        else if (index == 2) indexBg.setColor(0xFFFF9800);  // 第2名橙色
        else if (index == 3) indexBg.setColor(0xFFFFC107);  // 第3名黄色
        else indexBg.setColor(0xFF90A4AE);                  // 其他灰蓝色
        tvIndex.setBackground(indexBg);
        row1.addView(tvIndex);

        // 名称
        TextView tvName = new TextView(this);
        String displayName = item.getName() != null ? item.getName() : "--";
        tvName.setText(displayName);
        tvName.setTextColor(0xFF212121);
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvName.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.leftMargin = dpToPx(10);
        tvName.setLayoutParams(nameParams);

        // 如果有股票代码，名称可点击跳转同花顺
        boolean hasStockCode = item.getCode() != null && !item.getCode().isEmpty() && item.getCode().matches("\\d{6}");
        if (hasStockCode) {
            tvName.setTextColor(0xFF1565C0);
            tvName.setOnClickListener(v -> StockAppHelper.openInTongHuaShun(this, item.getCode(), item.getName()));
        }
        row1.addView(tvName);

        // 股票代码标签
        if (hasStockCode) {
            TextView tvCode = new TextView(this);
            tvCode.setText(item.getCode());
            tvCode.setTextColor(0xFF1565C0);  // 改为蓝色表示可点击
            tvCode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            codeParams.leftMargin = dpToPx(6);
            tvCode.setLayoutParams(codeParams);
            // 点击股票代码也可以跳转到同花顺
            tvCode.setOnClickListener(v -> StockAppHelper.openInTongHuaShun(this, item.getCode(), item.getName()));
            row1.addView(tvCode);
        }

        // 弹簧占位
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        row1.addView(spacer);

        // 评分
        if (item.getScore() > 0) {
            TextView tvScore = new TextView(this);
            tvScore.setText(String.format(Locale.CHINA, "%.0f", item.getScore()));
            tvScore.setTextColor(item.getScoreColor());
            tvScore.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            tvScore.setTypeface(null, Typeface.BOLD);
            row1.addView(tvScore);

            TextView tvScoreUnit = new TextView(this);
            tvScoreUnit.setText("分");
            tvScoreUnit.setTextColor(0xFF999999);
            tvScoreUnit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            unitParams.leftMargin = dpToPx(2);
            tvScoreUnit.setLayoutParams(unitParams);
            row1.addView(tvScoreUnit);
        }

        card.addView(row1);

        // === 亮点标签（highlight）- 拆分为多个小标签，流式布局 ===
        if (item.getHighlight() != null && !item.getHighlight().isEmpty()) {
            com.google.android.flexbox.FlexboxLayout hlFlow = new com.google.android.flexbox.FlexboxLayout(this);
            hlFlow.setFlexWrap(com.google.android.flexbox.FlexWrap.WRAP);
            hlFlow.setFlexDirection(com.google.android.flexbox.FlexDirection.ROW);
            LinearLayout.LayoutParams hlFlowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            hlFlowParams.topMargin = dpToPx(6);
            hlFlowParams.leftMargin = dpToPx(32);
            hlFlow.setLayoutParams(hlFlowParams);

            // 按 "+" 拆分为多个标签
            String[] hlParts = item.getHighlight().split("\\+");
            for (String part : hlParts) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                TextView tvHlTag = new TextView(this);
                tvHlTag.setText(trimmed);
                tvHlTag.setTextColor(0xFFFFFFFF);
                tvHlTag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                tvHlTag.setTypeface(null, Typeface.BOLD);
                android.graphics.drawable.GradientDrawable hlBg = new android.graphics.drawable.GradientDrawable();
                hlBg.setCornerRadius(dpToPx(4));
                hlBg.setColor(0xFFFF6D00);
                tvHlTag.setBackground(hlBg);
                tvHlTag.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));
                LinearLayout.LayoutParams hlTagParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                hlTagParams.rightMargin = dpToPx(4);
                hlTagParams.bottomMargin = dpToPx(4);
                tvHlTag.setLayoutParams(hlTagParams);
                hlFlow.addView(tvHlTag);
            }

            card.addView(hlFlow);
        }

        // === 推荐理由 ===
        if (item.getReason() != null && !item.getReason().isEmpty()) {
            TextView tvReason = new TextView(this);
            tvReason.setText(item.getReason());
            tvReason.setTextColor(0xFF616161);
            tvReason.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tvReason.setLineSpacing(0, 1.3f);
            LinearLayout.LayoutParams reasonParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            reasonParams.topMargin = dpToPx(8);
            reasonParams.leftMargin = dpToPx(32);
            tvReason.setLayoutParams(reasonParams);
            card.addView(tvReason);
        }

        // === 介入时机 / 止损位 / 次日预案 - 横排标签 ===
        boolean hasEntryTiming = item.getEntryTiming() != null && !item.getEntryTiming().isEmpty();
        boolean hasStopLoss = item.getStopLoss() != null && !item.getStopLoss().isEmpty();
        boolean hasNextDayPlan = item.getNextDayPlan() != null && !item.getNextDayPlan().isEmpty();

        if (hasEntryTiming || hasStopLoss || hasNextDayPlan) {
            LinearLayout infoRow = new LinearLayout(this);
            infoRow.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            infoParams.topMargin = dpToPx(8);
            infoParams.leftMargin = dpToPx(32);
            infoRow.setLayoutParams(infoParams);

            if (hasEntryTiming) {
                TextView tvEntry = new TextView(this);
                tvEntry.setText("\u23f0 介入时机：" + item.getEntryTiming());
                tvEntry.setTextColor(0xFF1565C0);
                tvEntry.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                tvEntry.setLineSpacing(0, 1.3f);
                infoRow.addView(tvEntry);
            }

            if (hasStopLoss) {
                TextView tvStop = new TextView(this);
                tvStop.setText("\u26a0 止损位：" + item.getStopLoss());
                tvStop.setTextColor(0xFFE53935);
                tvStop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                tvStop.setLineSpacing(0, 1.3f);
                LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (hasEntryTiming) stopParams.topMargin = dpToPx(3);
                tvStop.setLayoutParams(stopParams);
                infoRow.addView(tvStop);
            }

            if (hasNextDayPlan) {
                TextView tvPlan = new TextView(this);
                tvPlan.setText("\ud83d\udcdd 次日预案：" + item.getNextDayPlan());
                tvPlan.setTextColor(0xFF4527A0);
                tvPlan.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                tvPlan.setLineSpacing(0, 1.3f);
                LinearLayout.LayoutParams planParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (hasEntryTiming || hasStopLoss) planParams.topMargin = dpToPx(3);
                tvPlan.setLayoutParams(planParams);
                infoRow.addView(tvPlan);
            }

            card.addView(infoRow);
        }

        // === 关联个股标签 ===
        if (item.getRelatedStocks() != null && !item.getRelatedStocks().isEmpty()) {
            // 分隔线
            View divider = new View(this);
            divider.setBackgroundColor(0x0F000000);
            LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
            divParams.topMargin = dpToPx(10);
            divParams.bottomMargin = dpToPx(8);
            divParams.leftMargin = dpToPx(32);
            divider.setLayoutParams(divParams);
            card.addView(divider);

            TextView tvStocksLabel = new TextView(this);
            tvStocksLabel.setText("关联个股");
            tvStocksLabel.setTextColor(0xFF9E9E9E);
            tvStocksLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            LinearLayout.LayoutParams stocksLabelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            stocksLabelParams.leftMargin = dpToPx(32);
            stocksLabelParams.bottomMargin = dpToPx(4);
            tvStocksLabel.setLayoutParams(stocksLabelParams);
            card.addView(tvStocksLabel);

            // 标签流布局容器
            com.google.android.flexbox.FlexboxLayout tagContainer = new com.google.android.flexbox.FlexboxLayout(this);
            tagContainer.setFlexWrap(com.google.android.flexbox.FlexWrap.WRAP);
            tagContainer.setFlexDirection(com.google.android.flexbox.FlexDirection.ROW);
            LinearLayout.LayoutParams tagContainerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tagContainerParams.leftMargin = dpToPx(32);
            tagContainer.setLayoutParams(tagContainerParams);

            for (String stock : item.getRelatedStocks()) {
                if (stock == null || stock.isEmpty()) continue;
                TextView tvTag = new TextView(this);
                tvTag.setText(stock);
                tvTag.setTextColor(0xFF1565C0);
                tvTag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                // 蓝色边框背景
                android.graphics.drawable.GradientDrawable tagBgDrawable = new android.graphics.drawable.GradientDrawable();
                tagBgDrawable.setCornerRadius(dpToPx(4));
                tagBgDrawable.setColor(0xFFE3F2FD);
                tagBgDrawable.setStroke(dpToPx(1), 0x301565C0);
                tvTag.setBackground(tagBgDrawable);
                tvTag.setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3));
                LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                tagParams.rightMargin = dpToPx(6);
                tagParams.bottomMargin = dpToPx(5);
                tvTag.setLayoutParams(tagParams);

                // 点击跳转到同花顺
                String finalStock = stock;
                tvTag.setOnClickListener(v -> {
                    String stockCode = StockAppHelper.extractStockCode(finalStock);
                    StockAppHelper.openInTongHuaShun(this, stockCode, finalStock);
                });

                tagContainer.addView(tvTag);
            }

            card.addView(tagContainer);
        }

        // 如果有股票代码但没有关联个股，整个卡片也可以点击跳转
        if (hasStockCode && (item.getRelatedStocks() == null || item.getRelatedStocks().isEmpty())) {
            card.setClickable(true);
            card.setFocusable(true);
            // 添加按压水波纹效果
            TypedValue outValue = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            card.setForeground(getResources().getDrawable(outValue.resourceId, getTheme()));
            card.setOnClickListener(v -> StockAppHelper.openInTongHuaShun(this, item.getCode(), item.getName()));
        }

        return card;
    }

    /**
     * 创建信息 Chip 标签（用于情绪/主线/风险等）
     */
    private TextView createInfoChip(String text, int textColor, int bgColor) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(textColor);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chip.setTypeface(null, Typeface.BOLD);
        android.graphics.drawable.GradientDrawable chipBg = new android.graphics.drawable.GradientDrawable();
        chipBg.setCornerRadius(dpToPx(6));
        chipBg.setColor(bgColor);
        chip.setBackground(chipBg);
        chip.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipParams.rightMargin = dpToPx(8);
        chipParams.bottomMargin = dpToPx(4);
        chip.setLayoutParams(chipParams);
        return chip;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
