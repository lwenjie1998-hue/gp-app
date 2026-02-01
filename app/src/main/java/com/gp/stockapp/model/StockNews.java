package com.gp.stockapp.model;

import com.google.gson.annotations.SerializedName;
import java.util.Date;

/**
 * 股票新闻数据模型
 */
public class StockNews {
    @SerializedName("news_id")
    private String newsId;
    
    @SerializedName("title")
    private String title;
    
    @SerializedName("content")
    private String content;
    
    @SerializedName("summary")
    private String summary;
    
    @SerializedName("source")
    private String source;
    
    @SerializedName("publish_time")
    private long publishTime;
    
    @SerializedName("stock_codes")
    private String[] stockCodes;
    
    @SerializedName("importance")
    private int importance; // 1-5，5最重要
    
    @SerializedName("news_type")
    private String newsType; // 政策、财报、公告、研报等
    
    @SerializedName("sentiment")
    private String sentiment; // positive, negative, neutral
    
    @SerializedName("impact_level")
    private String impactLevel; // high, medium, low
    
    // Getters and Setters
    public String getNewsId() { return newsId; }
    public void setNewsId(String newsId) { this.newsId = newsId; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public long getPublishTime() { return publishTime; }
    public void setPublishTime(long publishTime) { this.publishTime = publishTime; }
    
    public String[] getStockCodes() { return stockCodes; }
    public void setStockCodes(String[] stockCodes) { this.stockCodes = stockCodes; }
    
    public int getImportance() { return importance; }
    public void setImportance(int importance) { this.importance = importance; }
    
    public String getNewsType() { return newsType; }
    public void setNewsType(String newsType) { this.newsType = newsType; }
    
    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    
    public String getImpactLevel() { return impactLevel; }
    public void setImpactLevel(String impactLevel) { this.impactLevel = impactLevel; }
    
    /**
     * 判断是否是高影响力新闻
     */
    public boolean isHighImpact() {
        return "high".equals(impactLevel) || importance >= 4;
    }
    
    /**
     * 判断是否是利好消息
     */
    public boolean isPositive() {
        return "positive".equals(sentiment);
    }
}
