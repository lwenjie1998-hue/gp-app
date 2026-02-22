package com.gp.stockapp.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.gp.stockapp.MainActivity;
import com.gp.stockapp.api.MarketApi;
import com.gp.stockapp.model.MarketIndex;
import com.gp.stockapp.model.StockNews;
import com.gp.stockapp.repository.StockRepository;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 大盘数据抓取服务
 * 循环抓取三大指数实时数据和市场新闻
 */
public class StockDataService extends Service {
    private static final String TAG = "StockDataService";
    private static final String CHANNEL_ID = "MarketDataChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final long FETCH_INTERVAL = 30000; // 30秒刷新一次

    private StockRepository stockRepository;
    private MarketApi marketApi;
    private ExecutorService executorService;
    private Timer fetchTimer;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "StockDataService created");

        stockRepository = StockRepository.getInstance(getApplicationContext());
        marketApi = MarketApi.getInstance();
        executorService = Executors.newFixedThreadPool(2);

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "StockDataService started");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification("正在获取大盘数据..."),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, createNotification("正在获取大盘数据..."));
        }
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

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "大盘数据监控",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("实时抓取大盘指数数据");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("大盘AI助手")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void startDataFetching() {
        if (isRunning) return;

        isRunning = true;
        fetchTimer = new Timer();

        // 立即执行一次
        fetchMarketData();

        // 定时执行
        fetchTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                fetchMarketData();
            }
        }, FETCH_INTERVAL, FETCH_INTERVAL);

        Log.d(TAG, "Data fetching started, interval: " + FETCH_INTERVAL + "ms");
    }

    private void stopDataFetching() {
        isRunning = false;
        if (fetchTimer != null) {
            fetchTimer.cancel();
            fetchTimer = null;
        }
        Log.d(TAG, "Data fetching stopped");
    }

    /**
     * 抓取大盘数据
     */
    private void fetchMarketData() {
        executorService.execute(() -> {
            try {
                // 抓取三大指数数据
                List<MarketIndex> indices = marketApi.fetchMarketIndices();
                if (indices != null && !indices.isEmpty()) {
                    stockRepository.saveMarketIndices(indices);

                    // 更新通知
                    updateNotification(indices);

                    // 通知UI刷新
                    sendBroadcast(MainActivity.ACTION_DATA_UPDATED);

                    Log.d(TAG, "Market indices updated: " + indices.size());
                }

                // 抓取市场新闻
                List<StockNews> newsList = marketApi.fetchMarketNews();
                if (newsList != null && !newsList.isEmpty()) {
                    stockRepository.saveNewsData(newsList);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error fetching market data", e);
            }
        });
    }

    /**
     * 更新通知栏显示
     */
    private void updateNotification(List<MarketIndex> indices) {
        StringBuilder text = new StringBuilder();
        for (MarketIndex index : indices) {
            if (text.length() > 0) text.append(" | ");
            text.append(index.getIndexName())
                    .append(" ")
                    .append(String.format("%.0f", index.getCurrentPoint()))
                    .append(" ")
                    .append(index.getFormattedChangePercent());
        }

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(text.toString()));
        }
    }

    /**
     * 发送广播通知UI更新
     */
    private void sendBroadcast(String action) {
        Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }
}
