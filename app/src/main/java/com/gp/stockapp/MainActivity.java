package com.gp.stockapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.gp.stockapp.api.GLM4Client;
import com.gp.stockapp.repository.StockRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 主Activity
 * 提供用户界面和服务控制
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    // UI组件
    private EditText etApiKey;
    private EditText etStockCode;
    private Button btnStartService;
    private Button btnStopService;
    private Button btnAddStock;
    private Button btnRemoveStock;
    private Button btnTestApi;
    private TextView tvStatus;
    private TextView tvStats;
    
    // 服务
    private StockDataService stockDataService;
    private AIRecommendationService aiRecommendationService;
    
    // 数据仓库
    private StockRepository stockRepository;
    
    // API客户端
    private GLM4Client glm4Client;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 初始化组件
        initViews();
        initServices();
        initData();
        
        // 请求权限
        requestPermissions();
    }
    
    /**
     * 初始化视图
     */
    private void initViews() {
        etApiKey = findViewById(R.id.et_api_key);
        etStockCode = findViewById(R.id.et_stock_code);
        btnStartService = findViewById(R.id.btn_start_service);
        btnStopService = findViewById(R.id.btn_stop_service);
        btnAddStock = findViewById(R.id.btn_add_stock);
        btnRemoveStock = findViewById(R.id.btn_remove_stock);
        btnTestApi = findViewById(R.id.btn_test_api);
        tvStatus = findViewById(R.id.tv_status);
        tvStats = findViewById(R.id.tv_stats);
        
        // 设置按钮点击事件
        btnStartService.setOnClickListener(this::onStartService);
        btnStopService.setOnClickListener(this::onStopService);
        btnAddStock.setOnClickListener(this::onAddStock);
        btnRemoveStock.setOnClickListener(this::onRemoveStock);
        btnTestApi.setOnClickListener(this::onTestApi);
    }
    
    /**
     * 初始化服务
     */
    private void initServices() {
        stockDataService = new StockDataService();
        aiRecommendationService = new AIRecommendationService();
    }
    
    /**
     * 初始化数据
     */
    private void initData() {
        stockRepository = StockRepository.getInstance(this);
        glm4Client = GLM4Client.getInstance();
        
        // 加载保存的API Key
        loadApiKey();
        
        // 更新统计信息
        updateStats();
    }
    
    /**
     * 请求权限
     */
    private void requestPermissions() {
        String[] permissions = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.POST_NOTIFICATIONS
        };
        
        List<String> neededPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(permission);
            }
        }
        
        if (!neededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                neededPermissions.toArray(new String[0]),
                PERMISSION_REQUEST_CODE
            );
        }
    }
    
    /**
     * 启动服务
     */
    private void onStartService(View view) {
        String apiKey = etApiKey.getText().toString().trim();
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请先设置API密钥", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 保存API密钥
        saveApiKey(apiKey);
        glm4Client.setApiKey(apiKey);
        
        // 启动股票数据服务
        startService(new Intent(this, StockDataService.class));
        
        // 启动AI推荐服务
        startService(new Intent(this, AIRecommendationService.class));
        
        updateStatus("服务已启动");
        Toast.makeText(this, "服务已启动", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 停止服务
     */
    private void onStopService(View view) {
        stopService(new Intent(this, StockDataService.class));
        stopService(new Intent(this, AIRecommendationService.class));
        
        updateStatus("服务已停止");
        Toast.makeText(this, "服务已停止", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 添加股票到关注列表
     */
    private void onAddStock(View view) {
        String stockCode = etStockCode.getText().toString().trim();
        if (stockCode.isEmpty()) {
            Toast.makeText(this, "请输入股票代码", Toast.LENGTH_SHORT).show();
            return;
        }
        
        stockRepository.addToWatchList(stockCode);
        Toast.makeText(this, "已添加到关注列表", Toast.LENGTH_SHORT).show();
        
        updateStats();
        etStockCode.setText("");
    }
    
    /**
     * 从关注列表移除股票
     */
    private void onRemoveStock(View view) {
        String stockCode = etStockCode.getText().toString().trim();
        if (stockCode.isEmpty()) {
            Toast.makeText(this, "请输入股票代码", Toast.LENGTH_SHORT).show();
            return;
        }
        
        stockRepository.removeFromWatchList(stockCode);
        Toast.makeText(this, "已从关注列表移除", Toast.LENGTH_SHORT).show();
        
        updateStats();
        etStockCode.setText("");
    }
    
    /**
     * 测试API连接
     */
    private void onTestApi(View view) {
        String apiKey = etApiKey.getText().toString().trim();
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请先设置API密钥", Toast.LENGTH_SHORT).show();
            return;
        }
        
        saveApiKey(apiKey);
        glm4Client.setApiKey(apiKey);
        
        new Thread(() -> {
            boolean success = glm4Client.testConnection();
            runOnUiThread(() -> {
                if (success) {
                    Toast.makeText(MainActivity.this, "API连接成功", Toast.LENGTH_SHORT).show();
                    updateStatus("API连接正常");
                } else {
                    Toast.makeText(MainActivity.this, "API连接失败", Toast.LENGTH_SHORT).show();
                    updateStatus("API连接失败");
                }
            });
        }).start();
    }
    
    /**
     * 保存API密钥
     */
    private void saveApiKey(String apiKey) {
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putString("api_key", apiKey)
            .apply();
    }
    
    /**
     * 加载API密钥
     */
    private void loadApiKey() {
        String apiKey = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getString("api_key", "");
        etApiKey.setText(apiKey);
    }
    
    /**
     * 更新状态文本
     */
    private void updateStatus(String status) {
        tvStatus.setText("状态: " + status);
    }
    
    /**
     * 更新统计信息
     */
    private void updateStats() {
        String stats = stockRepository.getDataStatistics();
        tvStats.setText(stats);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateStats();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "部分权限未授予", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
