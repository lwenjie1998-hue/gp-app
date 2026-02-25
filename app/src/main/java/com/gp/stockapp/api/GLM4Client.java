package com.gp.stockapp.api;

import android.util.Log;

import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * GLM API客户端（双模型架构）
 * 竞价推荐/尾盘推荐使用GLM-5（高精度）
 * 大盘分析/板块推荐/新闻推荐等使用GLM-4.7（轻量快速）
 * 两个模型各自独立并发限制，互不影响
 */
public class GLM4Client {
    private static final String TAG = "GLM4Client";
    private static GLM4Client instance;
    
    private static final String API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    
    // 双模型配置
    private static final String MODEL_PREMIUM = "glm-5";     // 高精度模型：竞价推荐、尾盘推荐
    private static final String MODEL_STANDARD = "glm-4.7";  // 轻量模型：大盘分析、板块推荐、新闻推荐等
    
    private OkHttpClient client;
    private String apiKey = ""; // 需要设置API密钥
    
    private GLM4Client() {
        client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(160, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
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
     * 高精度分析（竞价推荐、尾盘推荐）
     * 使用GLM-5模型，精度更高
     */
    public String analyzePremium(String prompt) {
        Log.d(TAG, "[Premium] Using model: " + MODEL_PREMIUM);
        return doAnalyze(prompt, MODEL_PREMIUM);
    }

    /**
     * 标准分析（大盘分析、板块推荐、新闻推荐等）
     * 使用GLM-4.7模型，轻量快速
     */
    public String analyze(String prompt) {
        Log.d(TAG, "[Standard] Using model: " + MODEL_STANDARD);
        return doAnalyze(prompt, MODEL_STANDARD);
    }

    /**
     * 实际执行API调用
     */
    private String doAnalyze(String prompt, String model) {
        if (apiKey == null || apiKey.isEmpty()) {
            Log.e(TAG, "API Key is not set");
            return null;
        }
        
        try {
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 20000);
            requestBody.put("top_p", 0.9);
            
            // 关闭思考模式，加快响应速度
            JSONObject thinkingConfig = new JSONObject();
            thinkingConfig.put("type", "disabled");
            requestBody.put("thinking", thinkingConfig);
            
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
     * 支持自动修复因token截断导致的不完整JSON
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
                try {
                    new JSONObject(jsonStr);
                    return jsonStr;
                } catch (Exception e) {
                    // JSON不完整，尝试修复
                    Log.w(TAG, "JSON validation failed, attempting repair...");
                    String repaired = repairTruncatedJson(jsonStr);
                    if (repaired != null) {
                        return repaired;
                    }
                }
            }
            
            // 没有找到闭合的}，可能整个JSON都被截断了
            if (jsonStart != -1) {
                String truncated = content.substring(jsonStart);
                Log.w(TAG, "JSON appears truncated, attempting repair...");
                String repaired = repairTruncatedJson(truncated);
                if (repaired != null) {
                    return repaired;
                }
            }
            
            return content;
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting JSON from content", e);
            return content;
        }
    }
    
    /**
     * 修复因max_tokens截断导致的不完整JSON
     * 策略：移除最后一个不完整的元素，然后补全所有未闭合的括号
     */
    private String repairTruncatedJson(String json) {
        try {
            if (json == null || json.isEmpty()) return null;
            
            String trimmed = json.trim();
            
            // 移除末尾不完整的键值对（截断在字符串值中间的情况）
            // 向前找到最后一个完整的结构（以 } 或 ] 或 "string" 或 数字 结束）
            int lastComplete = findLastCompleteElement(trimmed);
            if (lastComplete > 0 && lastComplete < trimmed.length()) {
                trimmed = trimmed.substring(0, lastComplete + 1);
            }
            
            // 移除末尾可能的悬挂逗号
            trimmed = trimmed.replaceAll(",\\s*$", "");
            
            // 统计未闭合的括号，补全
            int openBraces = 0, openBrackets = 0;
            boolean inString = false;
            char prevChar = 0;
            for (int i = 0; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (c == '"' && prevChar != '\\') {
                    inString = !inString;
                } else if (!inString) {
                    if (c == '{') openBraces++;
                    else if (c == '}') openBraces--;
                    else if (c == '[') openBrackets++;
                    else if (c == ']') openBrackets--;
                }
                prevChar = c;
            }
            
            // 如果在字符串内部截断，先闭合字符串
            if (inString) {
                trimmed += "\"";
            }
            
            // 补全未闭合的括号（先]后}）
            StringBuilder sb = new StringBuilder(trimmed);
            for (int i = 0; i < openBrackets; i++) {
                sb.append("]");
            }
            for (int i = 0; i < openBraces; i++) {
                sb.append("}");
            }
            
            String repaired = sb.toString();
            
            // 验证修复后的JSON
            new JSONObject(repaired);
            Log.d(TAG, "JSON repair successful");
            return repaired;
            
        } catch (Exception e) {
            Log.e(TAG, "JSON repair failed", e);
            return null;
        }
    }
    
    /**
     * 找到JSON字符串中最后一个完整元素的结束位置
     */
    private int findLastCompleteElement(String json) {
        boolean inString = false;
        char prevChar = 0;
        int lastGoodPos = -1;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && prevChar != '\\') {
                inString = !inString;
                if (!inString) {
                    // 字符串刚关闭
                    lastGoodPos = i;
                }
            } else if (!inString) {
                if (c == '}' || c == ']') {
                    lastGoodPos = i;
                }
            }
            prevChar = c;
        }
        return lastGoodPos;
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
