package com.gp.stockapp.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 股票实时数据模型
 */
public class StockData {
    @SerializedName("stock_code")
    private String stockCode;
    
    @SerializedName("stock_name")
    private String stockName;
    
    @SerializedName("current_price")
    private double currentPrice;
    
    @SerializedName("pre_close")
    private double preClose;
    
    @SerializedName("change_percent")
    private double changePercent;
    
    @SerializedName("volume")
    private long volume;
    
    @SerializedName("amount")
    private double amount;
    
    @SerializedName("high")
    private double high;
    
    @SerializedName("low")
    private double low;
    
    @SerializedName("open")
    private double open;
    
    @SerializedName("timestamp")
    private long timestamp;
    
    // 量化和游资分析指标
    @SerializedName("turnover_rate")
    private double turnoverRate;
    
    @SerializedName("pe_ratio")
    private double peRatio;
    
    @SerializedName("market_cap")
    private double marketCap;
    
    @SerializedName("main_inflow")
    private double mainInflow;
    
    @SerializedName("retail_inflow")
    private double retailInflow;
    
    @SerializedName("limit_up_times")
    private int limitUpTimes;
    
    @SerializedName("consecutive_rise_days")
    private int consecutiveRiseDays;
    
    // Getters and Setters
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    
    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }
    
    public double getPreClose() { return preClose; }
    public void setPreClose(double preClose) { this.preClose = preClose; }
    
    public double getChangePercent() { return changePercent; }
    public void setChangePercent(double changePercent) { this.changePercent = changePercent; }
    
    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }
    
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    
    public double getHigh() { return high; }
    public void setHigh(double high) { this.high = high; }
    
    public double getLow() { return low; }
    public void setLow(double low) { this.low = low; }
    
    public double getOpen() { return open; }
    public void setOpen(double open) { this.open = open; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public double getTurnoverRate() { return turnoverRate; }
    public void setTurnoverRate(double turnoverRate) { this.turnoverRate = turnoverRate; }
    
    public double getPeRatio() { return peRatio; }
    public void setPeRatio(double peRatio) { this.peRatio = peRatio; }
    
    public double getMarketCap() { return marketCap; }
    public void setMarketCap(double marketCap) { this.marketCap = marketCap; }
    
    public double getMainInflow() { return mainInflow; }
    public void setMainInflow(double mainInflow) { this.mainInflow = mainInflow; }
    
    public double getRetailInflow() { return retailInflow; }
    public void setRetailInflow(double retailInflow) { this.retailInflow = retailInflow; }
    
    public int getLimitUpTimes() { return limitUpTimes; }
    public void setLimitUpTimes(int limitUpTimes) { this.limitUpTimes = limitUpTimes; }
    
    public int getConsecutiveRiseDays() { return consecutiveRiseDays; }
    public void setConsecutiveRiseDays(int consecutiveRiseDays) { this.consecutiveRiseDays = consecutiveRiseDays; }
    
    /**
     * 计算涨跌幅颜色标识
     */
    public int getPriceChangeColor() {
        if (changePercent > 0) return 0xFFFF0000; // 红色上涨
        if (changePercent < 0) return 0xFF00FF00; // 绿色下跌
        return 0xFF999999; // 灰色平盘
    }
    
    /**
     * 判断是否是热门股（游资关注）
     */
    public boolean isHotStock() {
        return turnoverRate > 5.0 || // 换手率大于5%
               limitUpTimes > 0 || // 近期涨停
               mainInflow > 10000000; // 主力流入超1000万
    }
    
    /**
     * 判断是否是量化交易信号
     */
    public boolean hasQuantSignal() {
        return volume > 0 &&
               turnoverRate > 3.0 &&
               mainInflow > 5000000 &&
               changePercent > 3.0;
    }
}
