package com.gp.stockapp.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * AI策略分析结果模型
 */
public class MarketAnalysis {

    @SerializedName("market_sentiment")
    private String marketSentiment;   // bullish(偏多) / bearish(偏空) / neutral(中性)

    @SerializedName("trend_direction")
    private String trendDirection;    // up(上行) / down(下行) / sideways(震荡)

    @SerializedName("risk_level")
    private String riskLevel;         // low / medium / high

    @SerializedName("confidence")
    private double confidence;        // 0-100 置信度

    @SerializedName("short_term_view")
    private String shortTermView;     // 短期观点

    @SerializedName("medium_term_view")
    private String mediumTermView;    // 中期观点

    @SerializedName("suggestion")
    private String suggestion;        // 操作建议

    @SerializedName("key_factors")
    private List<String> keyFactors;  // 关键影响因素

    @SerializedName("analysis_text")
    private String analysisText;      // 完整分析文本

    @SerializedName("support_level")
    private double supportLevel;      // 支撑位

    @SerializedName("resistance_level")
    private double resistanceLevel;   // 压力位

    @SerializedName("timestamp")
    private long timestamp;           // 分析时间戳

    // ===== Getters & Setters =====

    public String getMarketSentiment() { return marketSentiment; }
    public void setMarketSentiment(String marketSentiment) { this.marketSentiment = marketSentiment; }

    public String getTrendDirection() { return trendDirection; }
    public void setTrendDirection(String trendDirection) { this.trendDirection = trendDirection; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getShortTermView() { return shortTermView; }
    public void setShortTermView(String shortTermView) { this.shortTermView = shortTermView; }

    public String getMediumTermView() { return mediumTermView; }
    public void setMediumTermView(String mediumTermView) { this.mediumTermView = mediumTermView; }

    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }

    public List<String> getKeyFactors() { return keyFactors; }
    public void setKeyFactors(List<String> keyFactors) { this.keyFactors = keyFactors; }

    public String getAnalysisText() { return analysisText; }
    public void setAnalysisText(String analysisText) { this.analysisText = analysisText; }

    public double getSupportLevel() { return supportLevel; }
    public void setSupportLevel(double supportLevel) { this.supportLevel = supportLevel; }

    public double getResistanceLevel() { return resistanceLevel; }
    public void setResistanceLevel(double resistanceLevel) { this.resistanceLevel = resistanceLevel; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    /**
     * 获取情绪文本
     */
    public String getSentimentText() {
        if (marketSentiment == null) return "未知";
        switch (marketSentiment) {
            case "bullish": return "偏多 📈";
            case "bearish": return "偏空 📉";
            case "neutral": return "中性 ➡️";
            default: return marketSentiment;
        }
    }

    /**
     * 获取情绪颜色
     */
    public int getSentimentColor() {
        if (marketSentiment == null) return 0xFF9E9E9E;
        switch (marketSentiment) {
            case "bullish": return 0xFFE53935;
            case "bearish": return 0xFF43A047;
            case "neutral": return 0xFFFF9800;
            default: return 0xFF9E9E9E;
        }
    }

    /**
     * 获取趋势文本
     */
    public String getTrendText() {
        if (trendDirection == null) return "未知";
        switch (trendDirection) {
            case "up": return "上行趋势";
            case "down": return "下行趋势";
            case "sideways": return "震荡整理";
            default: return trendDirection;
        }
    }

    /**
     * 获取风险等级文本
     */
    public String getRiskText() {
        if (riskLevel == null) return "未知";
        switch (riskLevel) {
            case "low": return "低风险";
            case "medium": return "中等风险";
            case "high": return "高风险";
            default: return riskLevel;
        }
    }

    /**
     * 获取风险颜色
     */
    public int getRiskColor() {
        if (riskLevel == null) return 0xFF9E9E9E;
        switch (riskLevel) {
            case "low": return 0xFF43A047;
            case "medium": return 0xFFFF9800;
            case "high": return 0xFFE53935;
            default: return 0xFF9E9E9E;
        }
    }
}
