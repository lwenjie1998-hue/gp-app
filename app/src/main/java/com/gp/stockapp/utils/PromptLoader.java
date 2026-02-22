package com.gp.stockapp.utils;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 提示词加载工具
 * 从assets目录加载提示词模板
 */
public class PromptLoader {
    private static final String TAG = "PromptLoader";
    
    private Context context;
    
    public PromptLoader(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 加载提示词文件
     */
    public String loadPrompt(String fileName) {
        InputStream inputStream = null;
        BufferedReader reader = null;
        StringBuilder content = new StringBuilder();
        
        try {
            inputStream = context.getAssets().open("prompts/" + fileName);
            reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            
            Log.d(TAG, "Loaded prompt: " + fileName);
            return content.toString();
            
        } catch (IOException e) {
            Log.e(TAG, "Error loading prompt: " + fileName, e);
            return null;
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing resources", e);
            }
        }
    }
    
    /**
     * 加载大盘分析提示词
     */
    public String loadMarketAnalysisPrompt() {
        return loadPrompt("market_analysis.txt");
    }
    
    /**
     * 加载板块推荐提示词
     */
    public String loadSectorStrategyPrompt() {
        return loadPrompt("sector_strategy.txt");
    }
    
    /**
     * 加载开盘竞价推荐提示词（游资策略）
     */
    public String loadAuctionStrategyPrompt() {
        return loadPrompt("auction_strategy.txt");
    }
    
    /**
     * 加载尾盘推荐提示词（游资策略）
     */
    public String loadClosingStrategyPrompt() {
        return loadPrompt("closing_strategy.txt");
    }
    
    /**
     * 加载量化分析提示词
     */
    public String loadQuantitativePrompt() {
        return loadPrompt("quantitative_analysis.txt");
    }
    
    /**
     * 加载游资分析提示词
     */
    public String loadHotMoneyPrompt() {
        return loadPrompt("hot_money_analysis.txt");
    }
    
    /**
     * 加载综合分析提示词（量化+游资融合）
     */
    public String loadCombinedPrompt() {
        return loadPrompt("combined_analysis.txt");
    }
}
