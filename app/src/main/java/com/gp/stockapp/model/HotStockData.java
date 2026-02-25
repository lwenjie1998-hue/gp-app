package com.gp.stockapp.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 热门股票数据模型
 * 包含龙虎榜、涨停板、热门股票等数据
 */
public class HotStockData {

    /** 龙虎榜数据 */
    @SerializedName("dragon_tiger_list")
    private List<DragonTigerItem> dragonTigerList;

    /** 涨停板数据 */
    @SerializedName("limit_up_list")
    private List<LimitUpItem> limitUpList;

    /** 连板股数据 */
    @SerializedName("continuous_limit_list")
    private List<ContinuousLimitItem> continuousLimitList;

    /** 涨幅榜(热门活跃股) */
    @SerializedName("top_gainers")
    private List<TopGainerItem> topGainers;

    /** 数据更新时间 */
    @SerializedName("timestamp")
    private long timestamp;

    // === 龙虎榜条目 ===
    public static class DragonTigerItem {
        @SerializedName("code")
        private String code;          // 股票代码

        @SerializedName("name")
        private String name;          // 股票名称

        @SerializedName("close")
        private double close;         // 收盘价

        @SerializedName("change_percent")
        private double changePercent; // 涨跌幅%

        @SerializedName("turnover_rate")
        private double turnoverRate;  // 换手率%

        @SerializedName("net_buy")
        private double netBuy;        // 龙虎榜净买入额(万)

        @SerializedName("buy_amount")
        private double buyAmount;     // 买入总额(万)

        @SerializedName("sell_amount")
        private double sellAmount;    // 卖出总额(万)

        @SerializedName("reason")
        private String reason;        // 上榜原因

        @SerializedName("market_cap")
        private double marketCap;     // 流通市值(亿)

        // Getters & Setters
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getClose() { return close; }
        public void setClose(double close) { this.close = close; }
        public double getChangePercent() { return changePercent; }
        public void setChangePercent(double changePercent) { this.changePercent = changePercent; }
        public double getTurnoverRate() { return turnoverRate; }
        public void setTurnoverRate(double turnoverRate) { this.turnoverRate = turnoverRate; }
        public double getNetBuy() { return netBuy; }
        public void setNetBuy(double netBuy) { this.netBuy = netBuy; }
        public double getBuyAmount() { return buyAmount; }
        public void setBuyAmount(double buyAmount) { this.buyAmount = buyAmount; }
        public double getSellAmount() { return sellAmount; }
        public void setSellAmount(double sellAmount) { this.sellAmount = sellAmount; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public double getMarketCap() { return marketCap; }
        public void setMarketCap(double marketCap) { this.marketCap = marketCap; }

        @Override
        public String toString() {
            return String.format("%s(%s) 涨幅%.2f%% 换手%.1f%% 净买入%.0f万 市值%.0f亿 [%s]",
                    name, code, changePercent, turnoverRate, netBuy, marketCap,
                    reason != null ? reason : "");
        }
    }

    // === 涨停板条目 ===
    public static class LimitUpItem {
        @SerializedName("code")
        private String code;

        @SerializedName("name")
        private String name;

        @SerializedName("first_limit_time")
        private String firstLimitTime;  // 首次涨停时间

        @SerializedName("last_limit_time")
        private String lastLimitTime;   // 最后涨停时间

        @SerializedName("open_count")
        private int openCount;          // 打开涨停次数

        @SerializedName("limit_up_type")
        private String limitUpType;     // 涨停类型(一字板/T字板/换手板)

        @SerializedName("turnover_rate")
        private double turnoverRate;

        @SerializedName("market_cap")
        private double marketCap;       // 流通市值(亿)

        @SerializedName("concept")
        private String concept;         // 所属概念/题材

        // Getters & Setters
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getFirstLimitTime() { return firstLimitTime; }
        public void setFirstLimitTime(String firstLimitTime) { this.firstLimitTime = firstLimitTime; }
        public String getLastLimitTime() { return lastLimitTime; }
        public void setLastLimitTime(String lastLimitTime) { this.lastLimitTime = lastLimitTime; }
        public int getOpenCount() { return openCount; }
        public void setOpenCount(int openCount) { this.openCount = openCount; }
        public String getLimitUpType() { return limitUpType; }
        public void setLimitUpType(String limitUpType) { this.limitUpType = limitUpType; }
        public double getTurnoverRate() { return turnoverRate; }
        public void setTurnoverRate(double turnoverRate) { this.turnoverRate = turnoverRate; }
        public double getMarketCap() { return marketCap; }
        public void setMarketCap(double marketCap) { this.marketCap = marketCap; }
        public String getConcept() { return concept; }
        public void setConcept(String concept) { this.concept = concept; }

        @Override
        public String toString() {
            return String.format("%s(%s) 首封%s 换手%.1f%% 市值%.0f亿 %s [%s]",
                    name, code, firstLimitTime != null ? firstLimitTime : "-",
                    turnoverRate, marketCap,
                    limitUpType != null ? limitUpType : "",
                    concept != null ? concept : "");
        }
    }

    // === 连板股条目 ===
    public static class ContinuousLimitItem {
        @SerializedName("code")
        private String code;

        @SerializedName("name")
        private String name;

        @SerializedName("continuous_count")
        private int continuousCount;    // 连板天数

        @SerializedName("change_percent")
        private double changePercent;

        @SerializedName("turnover_rate")
        private double turnoverRate;

        @SerializedName("market_cap")
        private double marketCap;

        @SerializedName("concept")
        private String concept;

        // Getters & Setters
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

        @Override
        public String toString() {
            return String.format("%s(%s) %d连板 涨幅%.2f%% 换手%.1f%% 市值%.0f亿 [%s]",
                    name, code, continuousCount, changePercent, turnoverRate, marketCap,
                    concept != null ? concept : "");
        }
    }

    // === 涨幅榜条目 ===
    public static class TopGainerItem {
        @SerializedName("code")
        private String code;

        @SerializedName("name")
        private String name;

        @SerializedName("close")
        private double close;

        @SerializedName("change_percent")
        private double changePercent;

        @SerializedName("turnover_rate")
        private double turnoverRate;

        @SerializedName("amount")
        private double amount;          // 成交额(万)

        @SerializedName("market_cap")
        private double marketCap;       // 流通市值(亿)

        // Getters & Setters
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getClose() { return close; }
        public void setClose(double close) { this.close = close; }
        public double getChangePercent() { return changePercent; }
        public void setChangePercent(double changePercent) { this.changePercent = changePercent; }
        public double getTurnoverRate() { return turnoverRate; }
        public void setTurnoverRate(double turnoverRate) { this.turnoverRate = turnoverRate; }
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
        public double getMarketCap() { return marketCap; }
        public void setMarketCap(double marketCap) { this.marketCap = marketCap; }

        @Override
        public String toString() {
            return String.format("%s(%s) 涨幅%.2f%% 换手%.1f%% 成交%.0f万 市值%.0f亿",
                    name, code, changePercent, turnoverRate, amount, marketCap);
        }
    }

    // === 主类 Getters & Setters ===
    public List<DragonTigerItem> getDragonTigerList() { return dragonTigerList; }
    public void setDragonTigerList(List<DragonTigerItem> dragonTigerList) { this.dragonTigerList = dragonTigerList; }

    public List<LimitUpItem> getLimitUpList() { return limitUpList; }
    public void setLimitUpList(List<LimitUpItem> limitUpList) { this.limitUpList = limitUpList; }

    public List<ContinuousLimitItem> getContinuousLimitList() { return continuousLimitList; }
    public void setContinuousLimitList(List<ContinuousLimitItem> continuousLimitList) { this.continuousLimitList = continuousLimitList; }

    public List<TopGainerItem> getTopGainers() { return topGainers; }
    public void setTopGainers(List<TopGainerItem> topGainers) { this.topGainers = topGainers; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    /**
     * 生成供AI分析的文本摘要
     */
    public String toAnalysisText() {
        StringBuilder sb = new StringBuilder();

        // 龙虎榜
        if (dragonTigerList != null && !dragonTigerList.isEmpty()) {
            sb.append("### 龙虎榜数据（最近交易日）\n");
            sb.append("以下个股登上龙虎榜，有游资/机构大额买卖：\n");
            for (DragonTigerItem item : dragonTigerList) {
                sb.append("- ").append(item.toString()).append("\n");
            }
            sb.append("\n");
        }

        // 涨停板
        if (limitUpList != null && !limitUpList.isEmpty()) {
            sb.append("### 今日涨停板（").append(limitUpList.size()).append("只）\n");
            sb.append("**注意：以下涨停股仅供了解市场情绪，绝对禁止推荐已涨停的股票！**\n");
            sb.append("涨停个股明细：\n");
            for (LimitUpItem item : limitUpList) {
                sb.append("- ").append(item.toString()).append("\n");
            }
            sb.append("\n");
        }

        // 连板股
        if (continuousLimitList != null && !continuousLimitList.isEmpty()) {
            sb.append("### 连板股（市场高度）\n");
            sb.append("**注意：以下连板股仅供了解市场高度，绝对禁止直接推荐已涨停的股票！**\n");
            for (ContinuousLimitItem item : continuousLimitList) {
                sb.append("- ").append(item.toString()).append("\n");
            }
            sb.append("\n");
        }

        // 主板活跃股（按成交额排序）
        if (topGainers != null && !topGainers.isEmpty()) {
            sb.append("### 主板活跃股TOP（按成交额排序）\n");
            for (TopGainerItem item : topGainers) {
                sb.append("- ").append(item.toString()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
