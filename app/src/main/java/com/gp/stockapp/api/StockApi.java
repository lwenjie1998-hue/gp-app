package com.gp.stockapp.api;

import android.util.Log;

import com.gp.stockapp.model.StockData;
import com.gp.stockapp.model.StockNews;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 股票数据API客户端
 * 负责抓取股票实时数据和新闻数据
 */
public class StockApi {
    private static final String TAG = "StockApi";
    private static StockApi instance;
    
    private OkHttpClient client;
    private Gson gson;
    
    // 数据源API地址（这里使用示例地址，实际需要替换为真实的API）
    private static final String STOCK_DATA_API = "https://api.example.com/stock/realtime";
    private static final String NEWS_API = "https://api.example.com/stock/news";
    
    private StockApi() {
        client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        
        gson = new Gson();
    }
    
    public static synchronized StockApi getInstance() {
        if (instance == null) {
            instance = new StockApi();
        }
        return instance;
    }
    
    /**
     * 抓取实时行情数据
     */
    public List<StockData> fetchRealTimeData(List<String> stockCodes) {
        if (stockCodes == null || stockCodes.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            // 构建URL参数
            StringBuilder urlBuilder = new StringBuilder(STOCK_DATA_API);
            urlBuilder.append("?codes=");
            for (int i = 0; i < stockCodes.size(); i++) {
                if (i > 0) urlBuilder.append(",");
                urlBuilder.append(stockCodes.get(i));
            }
            
            Request request = new Request.Builder()
                .url(urlBuilder.toString())
                .get()
                .build();
            
            Response response = client.newCall(request).execute();
            
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                
                Type listType = new TypeToken<List<StockData>>(){}.getType();
                List<StockData> stockDataList = gson.fromJson(responseBody, listType);
                
                Log.d(TAG, "Fetched " + stockDataList.size() + " stock data records");
                return stockDataList;
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Error fetching stock data", e);
        }
        
        return new ArrayList<>();
    }
    
    /**
     * 抓取单只股票的实时数据
     */
    public StockData fetchSingleStockData(String stockCode) {
        try {
            String url = STOCK_DATA_API + "?code=" + stockCode;
            
            Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
            
            Response response = client.newCall(request).execute();
            
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                return gson.fromJson(responseBody, StockData.class);
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Error fetching single stock data", e);
        }
        
        return null;
    }
    
    /**
     * 抓取新闻数据
     */
    public List<StockNews> fetchNewsData() {
        return fetchNewsData(50); // 默认抓取50条
    }
    
    /**
     * 抓取指定数量的新闻数据
     */
    public List<StockNews> fetchNewsData(int limit) {
        try {
            String url = NEWS_API + "?limit=" + limit;
            
            Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
            
            Response response = client.newCall(request).execute();
            
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                
                Type listType = new TypeToken<List<StockNews>>(){}.getType();
                List<StockNews> newsList = gson.fromJson(responseBody, listType);
                
                Log.d(TAG, "Fetched " + newsList.size() + " news records");
                return newsList;
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Error fetching news data", e);
        }
        
        return new ArrayList<>();
    }
    
    /**
     * 抓取特定股票的新闻
     */
    public List<StockNews> fetchStockNews(String stockCode, int limit) {
        try {
            String url = NEWS_API + "?code=" + stockCode + "&limit=" + limit;
            
            Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
            
            Response response = client.newCall(request).execute();
            
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                
                Type listType = new TypeToken<List<StockNews>>(){}.getType();
                List<StockNews> newsList = gson.fromJson(responseBody, listType);
                
                return newsList;
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Error fetching stock news", e);
        }
        
        return new ArrayList<>();
    }
    
    /**
     * 搜索股票
     */
    public List<StockData> searchStocks(String keyword) {
        try {
            String url = STOCK_DATA_API + "/search?q=" + keyword;
            
            Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
            
            Response response = client.newCall(request).execute();
            
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                
                Type listType = new TypeToken<List<StockData>>(){}.getType();
                return gson.fromJson(responseBody, listType);
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Error searching stocks", e);
        }
        
        return new ArrayList<>();
    }
}
