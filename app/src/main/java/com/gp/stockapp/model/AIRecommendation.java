package com.gp.stockapp.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * AI推荐结果模型
 */
public class AIRecommendation {
    @SerializedName("stock_code")
    private String stockCode;
    
    @SerializedName("stock_name")
    private String stockName;
    
    @SerializedName("recommend_type")
    private String recommendType; // quant(量化) / hot_money(游资) / both(两者皆可)
    
    @SerializedName("confidence")
    private double confidence; // 0-100
    
    @SerializedName("target_price")
    private double targetPrice;
    
    @SerializedName("risk_level")
    private String riskLevel; // low, medium, high
    
    @SerializedName("holding_period")
    private String holdingPeriod; // short(1-3天), medium(1-2周), long(1个月以上)
    
    @SerializedName("entry_price")
    private double entryPrice;
    
    @SerializedName("stop_loss")
    private double stopLoss;
    
    @SerializedName("take_profit")
    private double takeProfit;
    
    @SerializedName("reasoning")
    private String reasoning;
    
    @SerializedName("key_factors")
    private List<String> keyFactors;
    
    @SerializedName("quant_signals")
    private QuantSignal quantSignals;
    
    @SerializedName("hot_money_signals")
    private HotMoneySignal hotMoneySignals;
    
    @SerializedName("timestamp")
    private long timestamp;
    
    /**
     * 量化信号
     */
    public static class QuantSignal {
        @SerializedName("momentum_score")
        private double momentumScore;
        
        @SerializedName("volume_surge")
        private boolean volumeSurge;
        
        @SerializedName("breakout_signal")
        private boolean breakoutSignal;
        
        @SerializedName("trend_strength")
        private String trendStrength;
        
        @SerializedName("technical_score")
        private double technicalScore;
        
        public double getMomentumScore() { return momentumScore; }
        public void setMomentumScore(double momentumScore) { this.momentumScore = momentumScore; }
        
        public boolean isVolumeSurge() { return volumeSurge; }
        public void setVolumeSurge(boolean volumeSurge) { this.volumeSurge = volumeSurge; }
        
        public boolean isBreakoutSignal() { return breakoutSignal; }
        public void setBreakoutSignal(boolean breakoutSignal) { this.breakoutSignal = breakoutSignal; }
        
        public String getTrendStrength() { return trendStrength; }
        public void setTrendStrength(String trendStrength) { this.trendStrength = trendStrength; }
        
        public double getTechnicalScore() { return technicalScore; }
        public void setTechnicalScore(double technicalScore) { this.technicalScore = technicalScore; }
    }
    
    /**
     * 游资信号
     */
    public static class HotMoneySignal {
        @SerializedName("limit_up_potential")
        private double limitUpPotential;
        
        @SerializedName("main_force_position")
        private String mainForcePosition;
        
        @SerializedName("speculative_heat")
        private int speculativeHeat;
        
        @SerializedName("leading_stock")
        private boolean leadingStock;
        
        @SerializedName("short_term_momentum")
        private double shortTermMomentum;
        
        public double getLimitUpPotential() { return limitUpPotential; }
        public void setLimitUpPotential(double limitUpPotential) { this.limitUpPotential = limitUpPotential; }
        
        public String getMainForcePosition() { return mainForcePosition; }
        public void setMainForcePosition(String mainForcePosition) { this.mainForcePosition = mainForcePosition; }
        
        public int getSpeculativeHeat() { return speculativeHeat; }
        public void setSpeculativeHeat(int speculativeHeat) { this.speculativeHeat = speculativeHeat; }
        
        public boolean isLeadingStock() { return leadingStock; }
        public void setLeadingStock(boolean leadingStock) { this.leadingStock = leadingStock; }
        
        public double getShortTermMomentum() { return shortTermMomentum; }
        public void setShortTermMomentum(double shortTermMomentum) { this.shortTermMomentum = shortTermMomentum; }
    }
    
    // Getters and Setters
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    
    public String getRecommendType() { return recommendType; }
    public void setRecommendType(String recommendType) { this.recommendType = recommendType; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    public double getTargetPrice() { return targetPrice; }
    public void setTargetPrice(double targetPrice) { this.targetPrice = targetPrice; }
    
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    
    public String getHoldingPeriod() { return holdingPeriod; }
    public void setHoldingPeriod(String holdingPeriod) { this.holdingPeriod = holdingPeriod; }
    
    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }
    
    public double getStopLoss() { return stopLoss; }
    public void setStopLoss(double stopLoss) { this.stopLoss = stopLoss; }
    
    public double getTakeProfit() { return takeProfit; }
    public void setTakeProfit(double takeProfit) { this.takeProfit = takeProfit; }
    
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    
    public List<String> getKeyFactors() { return keyFactors; }
    public void setKeyFactors(List<String> keyFactors) { this.keyFactors = keyFactors; }
    
    public QuantSignal getQuantSignals() { return quantSignals; }
    public void setQuantSignals(QuantSignal quantSignals) { this.quantSignals = quantSignals; }
    
    public HotMoneySignal getHotMoneySignals() { return hotMoneySignals; }
    public void setHotMoneySignals(HotMoneySignal hotMoneySignals) { this.hotMoneySignals = hotMoneySignals; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
