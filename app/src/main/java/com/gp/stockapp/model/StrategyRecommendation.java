package com.gp.stockapp.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 策略推荐模型 - 统一支持板块推荐、开盘竞价推荐(游资策略)、尾盘推荐
 */
public class StrategyRecommendation {

    /**
     * 推荐类型
     */
    public enum Type {
        SECTOR,          // 板块推荐
        OPEN_AUCTION,    // 开盘竞价推荐（游资策略）
        CLOSING          // 尾盘推荐
    }

    @SerializedName("type")
    private String type;

    @SerializedName("title")
    private String title;

    @SerializedName("summary")
    private String summary;

    @SerializedName("confidence")
    private double confidence; // 0-100

    @SerializedName("risk_level")
    private String riskLevel; // low / medium / high

    @SerializedName("items")
    private List<RecommendItem> items;

    @SerializedName("strategy_note")
    private String strategyNote;

    @SerializedName("timestamp")
    private long timestamp;

    @SerializedName("analysis_text")
    private String analysisText;

    // 游资策略扩展字段（竞价推荐/尾盘推荐使用）
    @SerializedName("market_sentiment")
    private String marketSentiment;   // 市场情绪周期：冰点/修复/高潮/分化/退潮

    @SerializedName("main_line")
    private String mainLine;          // 当前最强主线题材

    @SerializedName("overnight_risk")
    private String overnightRisk;     // 隔夜风险评估（尾盘推荐用）

    /**
     * 单个推荐条目
     */
    public static class RecommendItem {
        @SerializedName("name")
        private String name; // 板块名或股票名

        @SerializedName("code")
        private String code; // 板块代码或股票代码

        @SerializedName("reason")
        private String reason;

        @SerializedName("highlight")
        private String highlight; // 亮点标签

        @SerializedName("score")
        private double score; // 评分 0-100

        @SerializedName("target_price")
        private String targetPrice;

        @SerializedName("stop_loss")
        private String stopLoss;

        @SerializedName("entry_timing")
        private String entryTiming; // 介入时机

        @SerializedName("related_stocks")
        private List<String> relatedStocks; // 关联个股（板块推荐用）

        @SerializedName("tags")
        private List<String> tags;

        @SerializedName("next_day_plan")
        private String nextDayPlan; // 次日操作预案（尾盘推荐用）

        // Getters & Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public String getHighlight() { return highlight; }
        public void setHighlight(String highlight) { this.highlight = highlight; }

        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }

        public String getTargetPrice() { return targetPrice; }
        public void setTargetPrice(String targetPrice) { this.targetPrice = targetPrice; }

        public String getStopLoss() { return stopLoss; }
        public void setStopLoss(String stopLoss) { this.stopLoss = stopLoss; }

        public String getEntryTiming() { return entryTiming; }
        public void setEntryTiming(String entryTiming) { this.entryTiming = entryTiming; }

        public List<String> getRelatedStocks() { return relatedStocks; }
        public void setRelatedStocks(List<String> relatedStocks) { this.relatedStocks = relatedStocks; }

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }

        public String getNextDayPlan() { return nextDayPlan; }
        public void setNextDayPlan(String nextDayPlan) { this.nextDayPlan = nextDayPlan; }

        /**
         * 获取评分对应的颜色
         */
        public int getScoreColor() {
            if (score >= 80) return 0xFFE53935; // 红色-强烈推荐
            if (score >= 60) return 0xFFFF9800; // 橙色-推荐
            if (score >= 40) return 0xFF1A73E8; // 蓝色-一般
            return 0xFF9E9E9E; // 灰色-观望
        }
    }

    // Getters & Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public List<RecommendItem> getItems() { return items; }
    public void setItems(List<RecommendItem> items) { this.items = items; }

    public String getStrategyNote() { return strategyNote; }
    public void setStrategyNote(String strategyNote) { this.strategyNote = strategyNote; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getAnalysisText() { return analysisText; }
    public void setAnalysisText(String analysisText) { this.analysisText = analysisText; }

    public String getMarketSentiment() { return marketSentiment; }
    public void setMarketSentiment(String marketSentiment) { this.marketSentiment = marketSentiment; }

    public String getMainLine() { return mainLine; }
    public void setMainLine(String mainLine) { this.mainLine = mainLine; }

    public String getOvernightRisk() { return overnightRisk; }
    public void setOvernightRisk(String overnightRisk) { this.overnightRisk = overnightRisk; }

    /**
     * 获取风险等级对应的颜色
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

    /**
     * 获取风险等级文字
     */
    public String getRiskText() {
        if (riskLevel == null) return "未知";
        switch (riskLevel) {
            case "low": return "低风险";
            case "medium": return "中风险";
            case "high": return "高风险";
            default: return riskLevel;
        }
    }

    /**
     * 获取置信度标签
     */
    public String getConfidenceLabel() {
        if (confidence >= 80) return "强推荐";
        if (confidence >= 60) return "推荐";
        if (confidence >= 40) return "一般";
        return "观望";
    }
}
