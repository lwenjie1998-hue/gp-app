package com.gp.stockapp.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 龙虎榜历史数据实体
 * 用于存储每日龙虎榜数据，支持历史查询
 */
@Entity(tableName = "dragon_tiger_history", 
        indices = {@Index(value = {"tradeDate", "code"}, unique = true)})
public class DragonTigerEntity {
    
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    /** 交易日期 yyyy-MM-dd */
    private String tradeDate;
    
    /** 股票代码 */
    private String code;
    
    /** 股票名称 */
    private String name;
    
    /** 收盘价 */
    private double closePrice;
    
    /** 涨跌幅(%) */
    private double changePercent;
    
    /** 换手率(%) */
    private double turnoverRate;
    
    /** 龙虎榜净买(万) */
    private double netBuy;
    
    /** 买入额(万) */
    private double buyAmount;
    
    /** 卖出额(万) */
    private double sellAmount;
    
    /** 上榜原因 */
    private String reason;
    
    /** 流通市值(亿) */
    private double marketCap;
    
    /** 数据抓取时间 */
    private long fetchTime;

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(String tradeDate) {
        this.tradeDate = tradeDate;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(double closePrice) {
        this.closePrice = closePrice;
    }

    public double getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(double changePercent) {
        this.changePercent = changePercent;
    }

    public double getTurnoverRate() {
        return turnoverRate;
    }

    public void setTurnoverRate(double turnoverRate) {
        this.turnoverRate = turnoverRate;
    }

    public double getNetBuy() {
        return netBuy;
    }

    public void setNetBuy(double netBuy) {
        this.netBuy = netBuy;
    }

    public double getBuyAmount() {
        return buyAmount;
    }

    public void setBuyAmount(double buyAmount) {
        this.buyAmount = buyAmount;
    }

    public double getSellAmount() {
        return sellAmount;
    }

    public void setSellAmount(double sellAmount) {
        this.sellAmount = sellAmount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public double getMarketCap() {
        return marketCap;
    }

    public void setMarketCap(double marketCap) {
        this.marketCap = marketCap;
    }

    public long getFetchTime() {
        return fetchTime;
    }

    public void setFetchTime(long fetchTime) {
        this.fetchTime = fetchTime;
    }
}
