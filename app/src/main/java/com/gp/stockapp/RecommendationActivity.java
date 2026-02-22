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

        // 显示摘要和时间
        if (rec.getSummary() != null && !rec.getSummary().isEmpty()) {
            TextView tvSummary = new TextView(this);
            tvSummary.setText(rec.getSummary());
            tvSummary.setTextColor(0xFF333333);
            tvSummary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tvSummary.setLineSpacing(0, 1.4f);
            LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            summaryParams.bottomMargin = dpToPx(16);
            tvSummary.setLayoutParams(summaryParams);
            layoutContent.addView(tvSummary);
        }

        // 显示时间
        if (rec.getTimestamp() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
            TextView tvTime = new TextView(this);
            tvTime.setText("更新时间：" + sdf.format(new Date(rec.getTimestamp())));
            tvTime.setTextColor(0xFF999999);
            tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            timeParams.bottomMargin = dpToPx(20);
            tvTime.setLayoutParams(timeParams);
            layoutContent.addView(tvTime);
        }

        // 显示推荐列表
        List<StrategyRecommendation.RecommendItem> items = rec.getItems();
        for (int i = 0; i < items.size(); i++) {
            StrategyRecommendation.RecommendItem item = items.get(i);
            if (item.getName() == null || item.getName().isEmpty()) continue;

            View itemView = createRecommendItemView(item, i + 1);
            layoutContent.addView(itemView);
        }

        // 显示详细分析
        if (rec.getAnalysisText() != null && !rec.getAnalysisText().isEmpty()) {
            TextView tvAnalysisTitle = new TextView(this);
            tvAnalysisTitle.setText("详细分析");
            tvAnalysisTitle.setTextColor(0xFF333333);
            tvAnalysisTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tvAnalysisTitle.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams analysisTitleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            analysisTitleParams.topMargin = dpToPx(24);
            analysisTitleParams.bottomMargin = dpToPx(12);
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
        // 外层卡片容器
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dpToPx(12);
        card.setLayoutParams(cardParams);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_item_transparent);
        card.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        card.setBackgroundColor(0xFFFFFFFF);
        card.setElevation(dpToPx(2));

        // 第一行：序号 + 名称 + 评分
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        // 序号
        TextView tvIndex = new TextView(this);
        tvIndex.setText(String.valueOf(index));
        tvIndex.setTextColor(0xFF666666);
        tvIndex.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvIndex.setGravity(Gravity.CENTER);
        tvIndex.setWidth(dpToPx(24));
        row1.addView(tvIndex);

        // 名称
        TextView tvName = new TextView(this);
        tvName.setText(item.getName() != null ? item.getName() : "--");
        tvName.setTextColor(0xFF333333);
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvName.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = dpToPx(8);
        tvName.setLayoutParams(nameParams);
        row1.addView(tvName);

        // 评分
        if (item.getScore() > 0) {
            TextView tvScore = new TextView(this);
            tvScore.setText(String.format(Locale.CHINA, "%.0f分", item.getScore()));
            tvScore.setTextColor(item.getScoreColor());
            tvScore.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            tvScore.setTypeface(null, Typeface.BOLD);
            row1.addView(tvScore);
        }

        card.addView(row1);

        // 推荐理由
        if (item.getReason() != null && !item.getReason().isEmpty()) {
            TextView tvReason = new TextView(this);
            tvReason.setText("推荐理由：" + item.getReason());
            tvReason.setTextColor(0xFF666666);
            tvReason.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tvReason.setLineSpacing(0, 1.4f);
            LinearLayout.LayoutParams reasonParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            reasonParams.topMargin = dpToPx(8);
            reasonParams.leftMargin = dpToPx(32);
            tvReason.setLayoutParams(reasonParams);
            card.addView(tvReason);
        }

        // 关联个股标签
        if (item.getRelatedStocks() != null && !item.getRelatedStocks().isEmpty()) {
            TextView tvStocksLabel = new TextView(this);
            tvStocksLabel.setText("关联个股：");
            tvStocksLabel.setTextColor(0xFF999999);
            tvStocksLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            LinearLayout.LayoutParams stocksLabelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            stocksLabelParams.topMargin = dpToPx(8);
            stocksLabelParams.leftMargin = dpToPx(32);
            tvStocksLabel.setLayoutParams(stocksLabelParams);
            card.addView(tvStocksLabel);

            // 标签容器
            com.google.android.flexbox.FlexboxLayout tagContainer = new com.google.android.flexbox.FlexboxLayout(this);
            tagContainer.setFlexWrap(com.google.android.flexbox.FlexWrap.WRAP);
            tagContainer.setFlexDirection(com.google.android.flexbox.FlexDirection.ROW);
            LinearLayout.LayoutParams tagContainerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tagContainerParams.topMargin = dpToPx(4);
            tagContainerParams.leftMargin = dpToPx(32);
            tagContainer.setLayoutParams(tagContainerParams);

            for (String stock : item.getRelatedStocks()) {
                if (stock == null || stock.isEmpty()) continue;
                TextView tvTag = new TextView(this);
                tvTag.setText(stock);
                tvTag.setTextColor(0xFF1976D2);
                tvTag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                tvTag.setBackgroundResource(R.drawable.tag_bg_light);
                tvTag.setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3));
                LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                tagParams.rightMargin = dpToPx(8);
                tagParams.bottomMargin = dpToPx(6);
                tvTag.setLayoutParams(tagParams);
                tagContainer.addView(tvTag);
            }

            card.addView(tagContainer);
        }

        return card;
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
