package com.gp.stockapp.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

/**
 * 连板股历史数据实体
 */
@Entity(tableName = "continuous_limit_history", 
        primaryKeys = {"tradeDate", "code"},
        indices = {@Index(value = {"tradeDate", "code"}, unique = true)})
public class ContinuousLimitEntity {
    @NonNull
    private String tradeDate;       // 交易日期 yyyyMMdd
    @NonNull
    private String code;            // 股票代码
    private String name;            // 股票名称
    private int continuousCount;    // 连板天数
    private double changePercent;   // 涨跌幅%
    private double turnoverRate;    // 换手率%
    private double marketCap;       // 流通市值(亿)
    private String concept;         // 所属概念
    private long fetchTime;         // 抓取时间戳

    // Getters & Setters
    public String getTradeDate() { return tradeDate; }
    public void setTradeDate(String tradeDate) { this.tradeDate = tradeDate; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getContinuousCount() { return continuousCount; }
    public void setContinuousCount(int continuousCount) { this.continuousCount = continuousCount; }
    public double getChangePercent() { return changePercent; }
    public void setChangePercent(double changePercent) { this.changePercent = changePercent; }
    public double getTurnoverRate() { return turnoverRate; }
    public void setTurnoverRate(double turnoverRate) { this.turnoverRate = turnoverRate; }
    public double getMarketCap() { return marketCap; }
    public void setMarketCap(double marketCap) { this.marketCap = marketCap; }
    public String getConcept() { return concept; }
    public void setConcept(String concept) { this.concept = concept; }
    public long getFetchTime() { return fetchTime; }
    public void setFetchTime(long fetchTime) { this.fetchTime = fetchTime; }
}
