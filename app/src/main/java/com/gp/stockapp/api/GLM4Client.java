package com.gp.stockapp.api;

import android.util.Log;

import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * GLM-4.7 API客户端
 * 用于调用智谱AI的GLM-4.7模型进行股票分析
 */
public class GLM4Client {
    private static final String TAG = "GLM4Client";
    private static GLM4Client instance;
    
    private static final String API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private static final String MODEL_NAME = "glm-4";
    
    private OkHttpClient client;
    private String apiKey = ""; // 需要设置API密钥
    
    private GLM4Client() {
        client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    }
    
    public static synchronized GLM4Client getInstance() {
        if (instance == null) {
            instance = new GLM4Client();
        }
        return instance;
    }
    
    /**
     * 设置API密钥
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        Log.d(TAG, "API Key set");
    }
    
    /**
     * 分析股票数据
     */
    public String analyze(String prompt) {
        if (apiKey == null || apiKey.isEmpty()) {
            Log.e(TAG, "API Key is not set");
            return null;
        }
        
        try {
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", MODEL_NAME);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 2000);
            requestBody.put("top_p", 0.9);
            
            // 构建消息
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", prompt);
            
            requestBody.put("messages", new org.json.JSONArray().put(message));
            
            // 构建请求
            RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
            );
            
            Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();
            
            // 发送请求
            Response response = client.newCall(request).execute();
            
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                Log.d(TAG, "API Response received");
                
                // 解析响应
                return parseResponse(responseBody);
            } else {
                Log.e(TAG, "API request failed: " + response.code());
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error calling GLM API", e);
            return null;
        }
    }
    
    /**
     * 解析API响应
     */
    private String parseResponse(String responseBody) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            org.json.JSONArray choices = jsonResponse.optJSONArray("choices");
            
            if (choices != null && choices.length() > 0) {
                JSONObject firstChoice = choices.optJSONObject(0);
                JSONObject message = firstChoice.optJSONObject("message");
                
                if (message != null) {
                    String content = message.optString("content");
                    // 尝试提取JSON部分
                    return extractJsonFromContent(content);
                }
            }
            
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing response", e);
            return null;
        }
    }
    
    /**
     * 从内容中提取JSON
     */
    private String extractJsonFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        
        try {
            // 查找JSON的开始和结束
            int jsonStart = content.indexOf("{");
            int jsonEnd = content.lastIndexOf("}");
            
            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                String jsonStr = content.substring(jsonStart, jsonEnd + 1);
                
                // 验证是否是有效的JSON
                new JSONObject(jsonStr);
                
                return jsonStr;
            }
            
            return content;
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting JSON from content", e);
            return content;
        }
    }
    
    /**
     * 测试API连接
     */
    public boolean testConnection() {
        try {
            String response = analyze("请回复：连接成功");
            return response != null && !response.isEmpty();
        } catch (Exception e) {
            Log.e(TAG, "Connection test failed", e);
            return false;
        }
    }
}
