package com.gp.stockapp.utils;

import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

/**
 * 统一的HTTP客户端单例
 * 全局共享连接池，提高网络请求效率
 */
public class HttpClient {
    private static final String TAG = "HttpClient";
    private static volatile OkHttpClient instance;
    
    // 连接池配置：最大5个空闲连接，保活5分钟
    private static final int MAX_IDLE_CONNECTIONS = 5;
    private static final long KEEP_ALIVE_DURATION = 5; // 分钟
    
    // 默认超时配置
    private static final long DEFAULT_CONNECT_TIMEOUT = 60; // 秒
    private static final long DEFAULT_READ_TIMEOUT = 30; // 秒
    private static final long DEFAULT_WRITE_TIMEOUT = 30; // 秒
    
    // 长时间超时配置（用于AI分析等耗时请求）
    private static final long LONG_READ_TIMEOUT = 120; // 秒
    
    private static volatile OkHttpClient longTimeoutInstance;

    private HttpClient() {
        // 私有构造函数，防止外部实例化
    }

    /**
     * 获取默认超时的OkHttpClient实例
     */
    public static OkHttpClient getInstance() {
        if (instance == null) {
            synchronized (HttpClient.class) {
                if (instance == null) {
                    instance = createDefaultClient();
                }
            }
        }
        return instance;
    }

    /**
     * 获取长时间超时的OkHttpClient实例
     * 用于AI分析等耗时请求
     */
    public static OkHttpClient getLongTimeoutInstance() {
        if (longTimeoutInstance == null) {
            synchronized (HttpClient.class) {
                if (longTimeoutInstance == null) {
                    longTimeoutInstance = createLongTimeoutClient();
                }
            }
        }
        return longTimeoutInstance;
    }

    /**
     * 创建默认配置的客户端
     */
    private static OkHttpClient createDefaultClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_WRITE_TIMEOUT, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(
                        MAX_IDLE_CONNECTIONS, 
                        KEEP_ALIVE_DURATION, 
                        TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    /**
     * 创建长时间超时的客户端
     */
    private static OkHttpClient createLongTimeoutClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(LONG_READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_WRITE_TIMEOUT, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(
                        MAX_IDLE_CONNECTIONS,
                        KEEP_ALIVE_DURATION,
                        TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    /**
     * 创建自定义配置的Builder（用于特殊需求）
     */
    public static OkHttpClient.Builder newBuilder() {
        return getInstance().newBuilder();
    }
}
