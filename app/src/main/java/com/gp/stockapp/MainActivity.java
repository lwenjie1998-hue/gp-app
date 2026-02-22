package com.gp.stockapp;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import com.gp.stockapp.repository.StockRepository;
import com.gp.stockapp.service.StockDataService;
import com.gp.stockapp.service.AIRecommendationService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 主界面 - 大盘AI助手
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
    private TextView tvNewsList;
    // 状态 UI
    private TextView tvStatus, tvLastUpdate;
    private View statusIndicator;
    // 按钮
    private Button btnStartService, btnStopService;

    private StockRepository stockRepository;
    private GLM4Client glm4Client;
    private Handler refreshHandler;
    private boolean isDetailExpanded = false;
    private boolean isServiceRunning = false;

    // 数据更新广播
    public static final String ACTION_DATA_UPDATED = "com.gp.stockapp.DATA_UPDATED";
    public static final String ACTION_ANALYSIS_UPDATED = "com.gp.stockapp.ANALYSIS_UPDATED";

    private BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_DATA_UPDATED.equals(intent.getAction())) {
                refreshMarketData();
                refreshNews();
            } else if (ACTION_ANALYSIS_UPDATED.equals(intent.getAction())) {
                refreshAnalysis();
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
            getSupportActionBar().setTitle("大盘AI助手");
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
        tvNewsList = findViewById(R.id.tv_news_list);

        // 状态
        tvStatus = findViewById(R.id.tv_status);
        tvLastUpdate = findViewById(R.id.tv_last_update);
        statusIndicator = findViewById(R.id.status_indicator);

        // 按钮
        btnStartService = findViewById(R.id.btn_start_service);
        btnStopService = findViewById(R.id.btn_stop_service);

        btnStartService.setOnClickListener(this::onStartService);
        btnStopService.setOnClickListener(this::onStopService);

        // 详情展开/收起
        tvToggleDetail.setOnClickListener(v -> toggleAnalysisDetail());
    }

    private void initData() {
        stockRepository = StockRepository.getInstance(this);
        glm4Client = GLM4Client.getInstance();
        refreshHandler = new Handler(Looper.getMainLooper());

        // 加载API密钥
        String apiKey = getApiKey();
        if (!apiKey.isEmpty()) {
            glm4Client.setApiKey(apiKey);
        }

        // 加载已有数据
        refreshMarketData();
        refreshAnalysis();
        refreshNews();
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

        isServiceRunning = true;
        updateServiceStatus(true);
        Toast.makeText(this, "监控已启动", Toast.LENGTH_SHORT).show();
    }

    private void onStopService(View view) {
        stopService(new Intent(this, StockDataService.class));
        stopService(new Intent(this, AIRecommendationService.class));

        isServiceRunning = false;
        updateServiceStatus(false);
        Toast.makeText(this, "监控已停止", Toast.LENGTH_SHORT).show();
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
        } else {
            dot.setColor(0xFF9E9E9E); // 灰色
            statusIndicator.setBackground(dot);
            tvStatus.setText("服务未启动");
            tvStatus.setTextColor(0xFF666666);
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
                    updateShIndex(index);
                    break;
                case MarketIndex.SZ_INDEX:
                    updateSzIndex(index);
                    break;
                case MarketIndex.CY_INDEX:
                    updateCyIndex(index);
                    break;
            }
        }

        // 更新最后刷新时间
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
        tvLastUpdate.setText("更新: " + sdf.format(new Date()));
    }

    private void updateShIndex(MarketIndex index) {
        tvShPoint.setText(String.format(Locale.CHINA, "%.2f", index.getCurrentPoint()));
        tvShChange.setText(index.getFormattedChangePoint());
        tvShPercent.setText(index.getFormattedChangePercent());
        
        // 成交额 + 放量/缩量标识
        String volumeText = "成交额: " + index.getFormattedAmount();
        String volumeChange = index.getVolumeChangeText();
        if (!volumeChange.isEmpty()) {
            volumeText += " (" + volumeChange + ")";
        }
        tvShVolume.setText(volumeText);

        int color = index.getChangeColor();
        tvShChange.setTextColor(color);
        tvShPercent.setTextColor(color);

        if (index.getHigh() > 0 && index.getLow() > 0 && index.getPreClose() > 0) {
            double range = (index.getHigh() - index.getLow()) / index.getPreClose() * 100;
            tvShRange.setText(String.format(Locale.CHINA, "振幅: %.2f%%", range));
        }
    }

    private void updateSzIndex(MarketIndex index) {
        tvSzPoint.setText(String.format(Locale.CHINA, "%.2f", index.getCurrentPoint()));
        tvSzChange.setText(index.getFormattedChangePoint());
        tvSzPercent.setText(index.getFormattedChangePercent());
        
        // 成交额 + 放量/缩量标识
        String volumeText = "成交额: " + index.getFormattedAmount();
        String volumeChange = index.getVolumeChangeText();
        if (!volumeChange.isEmpty()) {
            volumeText += " (" + volumeChange + ")";
        }
        tvSzVolume.setText(volumeText);

        int color = index.getChangeColor();
        tvSzChange.setTextColor(color);
        tvSzPercent.setTextColor(color);
    }

    private void updateCyIndex(MarketIndex index) {
        tvCyPoint.setText(String.format(Locale.CHINA, "%.2f", index.getCurrentPoint()));
        tvCyChange.setText(index.getFormattedChangePoint());
        tvCyPercent.setText(index.getFormattedChangePercent());
        
        // 成交额 + 放量/缩量标识
        String volumeText = "成交额: " + index.getFormattedAmount();
        String volumeChange = index.getVolumeChangeText();
        if (!volumeChange.isEmpty()) {
            volumeText += " (" + volumeChange + ")";
        }
        tvCyVolume.setText(volumeText);

        int color = index.getChangeColor();
        tvCyChange.setTextColor(color);
        tvCyPercent.setTextColor(color);
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
        List<StockNews> newsList = stockRepository.getLatestNews(5);
        if (newsList == null || newsList.isEmpty()) {
            tvNewsList.setText("暂无新闻数据");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < newsList.size(); i++) {
            StockNews news = newsList.get(i);
            if (news.getTitle() != null) {
                sb.append("• ").append(news.getTitle());
                if (i < newsList.size() - 1) {
                    sb.append("\n\n");
                }
            }
        }
        tvNewsList.setText(sb.toString());
    }

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
        LocalBroadcastManager.getInstance(this).registerReceiver(dataReceiver, filter);

        // 刷新数据
        refreshMarketData();
        refreshAnalysis();
        refreshNews();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refreshHandler != null) {
            refreshHandler.removeCallbacksAndMessages(null);
        }
    }
}
