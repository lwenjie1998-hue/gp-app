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
import com.gp.stockapp.api.StockApi;
import com.gp.stockapp.model.StockData;
import com.gp.stockapp.model.StockNews;
import com.gp.stockapp.repository.StockRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 股票数据抓取服务
 * 负责循环抓取股票实时数据和新闻数据
 */
public class StockDataService extends Service {
    private static final String TAG = "StockDataService";
    private static final String CHANNEL_ID = "StockDataChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final long FETCH_INTERVAL = 30000; // 30秒抓取一次
    
    private StockRepository stockRepository;
    private StockApi stockApi;
    private ExecutorService executorService;
    private Timer fetchTimer;
    private boolean isRunning = false;
    
    // 关注的股票代码列表
    private List<String> watchList = new ArrayList<>();
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "StockDataService created");
        
        stockRepository = new StockRepository(getApplicationContext());
        stockApi = StockApi.getInstance();
        executorService = Executors.newFixedThreadPool(3);
        
        createNotificationChannel();
        loadWatchList();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "StockDataService started");
        
        startForeground(NOTIFICATION_ID, createNotification());
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
    
    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "股票数据服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("实时抓取股票数据");
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
            .setContentTitle("股票数据抓取中")
            .setContentText("正在实时抓取股票数据和新闻")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }
    
    /**
     * 加载关注列表
     */
    private void loadWatchList() {
        executorService.execute(() -> {
            watchList = stockRepository.getWatchList();
            Log.d(TAG, "Loaded watch list: " + watchList.size() + " stocks");
        });
    }
    
    /**
     * 开始数据抓取
     */
    private void startDataFetching() {
        if (isRunning) {
            return;
        }
        
        isRunning = true;
        fetchTimer = new Timer();
        
        // 立即执行一次
        fetchStockData();
        
        // 定时执行
        fetchTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                fetchStockData();
            }
        }, FETCH_INTERVAL, FETCH_INTERVAL);
        
        Log.d(TAG, "Data fetching started");
    }
    
    /**
     * 停止数据抓取
     */
    private void stopDataFetching() {
        isRunning = false;
        if (fetchTimer != null) {
            fetchTimer.cancel();
            fetchTimer = null;
        }
        Log.d(TAG, "Data fetching stopped");
    }
    
    /**
     * 抓取股票数据
     */
    private void fetchStockData() {
        Log.d(TAG, "Fetching stock data...");
        
        executorService.execute(() -> {
            try {
                // 抓取实时行情数据
                List<StockData> stockDataList = stockApi.fetchRealTimeData(watchList);
                if (stockDataList != null && !stockDataList.isEmpty()) {
                    stockRepository.saveStockData(stockDataList);
                    Log.d(TAG, "Saved " + stockDataList.size() + " stock data records");
                }
                
                // 抓取新闻数据
                List<StockNews> newsList = stockApi.fetchNewsData();
                if (newsList != null && !newsList.isEmpty()) {
                    stockRepository.saveNewsData(newsList);
                    Log.d(TAG, "Saved " + newsList.size() + " news records");
                }
                
                // 检查是否有重要信号
                checkImportantSignals(stockDataList);
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching stock data", e);
            }
        });
    }
    
    /**
     * 检查重要信号
     */
    private void checkImportantSignals(List<StockData> stockDataList) {
        if (stockDataList == null || stockDataList.isEmpty()) {
            return;
        }
        
        for (StockData data : stockDataList) {
            // 检查量化信号
            if (data.hasQuantSignal()) {
                Log.d(TAG, "Quant signal detected: " + data.getStockCode());
                sendSignalNotification(data.getStockCode(), data.getStockName(), "量化信号");
            }
            
            // 检查游资信号
            if (data.isHotStock()) {
                Log.d(TAG, "Hot money signal detected: " + data.getStockCode());
                sendSignalNotification(data.getStockCode(), data.getStockName(), "游资信号");
            }
        }
    }
    
    /**
     * 发送信号通知
     */
    private void sendSignalNotification(String stockCode, String stockName, String signalType) {
        // 这里可以发送推送通知
        Log.d(TAG, "Signal notification: " + stockCode + " - " + signalType);
    }
    
    /**
     * 添加股票到关注列表
     */
    public void addToWatchList(String stockCode) {
        executorService.execute(() -> {
            stockRepository.addToWatchList(stockCode);
            watchList.add(stockCode);
            Log.d(TAG, "Added to watch list: " + stockCode);
        });
    }
    
    /**
     * 从关注列表移除股票
     */
    public void removeFromWatchList(String stockCode) {
        executorService.execute(() -> {
            stockRepository.removeFromWatchList(stockCode);
            watchList.remove(stockCode);
            Log.d(TAG, "Removed from watch list: " + stockCode);
        });
    }
    
    /**
     * 更新关注列表
     */
    public void updateWatchList(List<String> newWatchList) {
        executorService.execute(() -> {
            stockRepository.updateWatchList(newWatchList);
            watchList.clear();
            watchList.addAll(newWatchList);
            Log.d(TAG, "Updated watch list: " + watchList.size() + " stocks");
        });
    }
}
