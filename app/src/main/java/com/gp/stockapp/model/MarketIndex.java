package com.gp.stockapp.model;

import com.google.gson.annotations.SerializedName;

/**
 * 大盘指数数据模型
 * 支持上证指数、深证成指、创业板指等
 */
public class MarketIndex {

    @SerializedName("index_code")
    private String indexCode;      // 指数代码，如 sh000001

    @SerializedName("index_name")
    private String indexName;      // 指数名称，如 上证指数

    @SerializedName("current_point")
    private double currentPoint;   // 当前点位

    @SerializedName("change_point")
    private double changePoint;    // 涨跌点数

    @SerializedName("change_percent")
    private double changePercent;  // 涨跌幅(%)

    @SerializedName("volume")
    private long volume;           // 成交量(手)

    @SerializedName("amount")
    private double amount;         // 成交额(亿元)

    @SerializedName("prev_amount")
    private double prevAmount;     // 前日成交额(亿元)，用于判断放量/缩量

    @SerializedName("open")
    private double open;           // 开盘点位

    @SerializedName("high")
    private double high;           // 最高点位

    @SerializedName("low")
    private double low;            // 最低点位

    @SerializedName("pre_close")
    private double preClose;       // 昨收点位

    @SerializedName("timestamp")
    private long timestamp;        // 时间戳

    @SerializedName("advance_count")
    private int advanceCount;      // 上涨家数

    @SerializedName("decline_count")
    private int declineCount;      // 下跌家数

    @SerializedName("flat_count")
    private int flatCount;         // 平盘家数

    // ===== 常用指数代码 =====
    public static final String SH_INDEX = "sh000001";     // 上证指数
    public static final String SZ_INDEX = "sz399001";     // 深证成指
    public static final String CY_INDEX = "sz399006";     // 创业板指

    // ===== Getters & Setters =====

    public String getIndexCode() { return indexCode; }
    public void setIndexCode(String indexCode) { this.indexCode = indexCode; }

    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }

    public double getCurrentPoint() { return currentPoint; }
    public void setCurrentPoint(double currentPoint) { this.currentPoint = currentPoint; }

    public double getChangePoint() { return changePoint; }
    public void setChangePoint(double changePoint) { this.changePoint = changePoint; }

    public double getChangePercent() { return changePercent; }
    public void setChangePercent(double changePercent) { this.changePercent = changePercent; }

    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public double getPrevAmount() { return prevAmount; }
    public void setPrevAmount(double prevAmount) { this.prevAmount = prevAmount; }

    public double getOpen() { return open; }
    public void setOpen(double open) { this.open = open; }

    public double getHigh() { return high; }
    public void setHigh(double high) { this.high = high; }

    public double getLow() { return low; }
    public void setLow(double low) { this.low = low; }

    public double getPreClose() { return preClose; }
    public void setPreClose(double preClose) { this.preClose = preClose; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getAdvanceCount() { return advanceCount; }
    public void setAdvanceCount(int advanceCount) { this.advanceCount = advanceCount; }

    public int getDeclineCount() { return declineCount; }
    public void setDeclineCount(int declineCount) { this.declineCount = declineCount; }

    public int getFlatCount() { return flatCount; }
    public void setFlatCount(int flatCount) { this.flatCount = flatCount; }

    /**
     * 获取涨跌颜色 (A股：红涨绿跌)
     */
    public int getChangeColor() {
        if (changePercent > 0) return 0xFFE53935;   // 红色上涨
        if (changePercent < 0) return 0xFF43A047;   // 绿色下跌
        return 0xFF9E9E9E;                          // 灰色平盘
    }

    /**
     * 获取格式化的成交额文本
     */
    public String getFormattedAmount() {
        if (amount >= 10000) {
            return String.format("%.0f万亿", amount / 10000);
        } else if (amount >= 1) {
            return String.format("%.0f亿", amount);
        } else {
            return String.format("%.2f亿", amount);
        }
    }

    /**
     * 获取涨跌幅文本（带+/-号）
     */
    public String getFormattedChangePercent() {
        if (changePercent > 0) {
            return String.format("+%.2f%%", changePercent);
        }
        return String.format("%.2f%%", changePercent);
    }

    /**
     * 获取涨跌点数文本（带+/-号）
     */
    public String getFormattedChangePoint() {
        if (changePoint > 0) {
            return String.format("+%.2f", changePoint);
        }
        return String.format("%.2f", changePoint);
    }

    /**
     * 判断当前是否上涨
     */
    public boolean isUp() {
        return changePercent > 0;
    }

    /**
     * 判断当前是否下跌
     */
    public boolean isDown() {
        return changePercent < 0;
    }

    /**
     * 获取成交额变化率（相对于前日）
     * @return 变化率百分比，如果没有前日数据返回0
     */
    public double getAmountChangePercent() {
        if (prevAmount <= 0 || amount <= 0) {
            return 0;
        }
        return ((amount - prevAmount) / prevAmount) * 100;
    }

    /**
     * 判断是否放量
     * @return true表示放量（成交额增加超过5%）
     */
    public boolean isVolumeIncrease() {
        return getAmountChangePercent() > 5.0;
    }

    /**
     * 判断是否缩量
     * @return true表示缩量（成交额减少超过5%）
     */
    public boolean isVolumeDecrease() {
        return getAmountChangePercent() < -5.0;
    }

    /**
     * 获取放量/缩量文本标识
     * @return 放量标识文本，如"放量"、"缩量"或空字符串
     */
    public String getVolumeChangeText() {
        if (prevAmount <= 0) {
            return ""; // 没有历史数据，不显示
        }
        double changePercent = getAmountChangePercent();
        if (changePercent > 10) {
            return "放量";
        } else if (changePercent > 5) {
            return "小幅放量";
        } else if (changePercent < -10) {
            return "缩量";
        } else if (changePercent < -5) {
            return "小幅缩量";
        }
        return "平量";
    }

    /**
     * 获取放量/缩量颜色
     * @return 颜色值
     */
    public int getVolumeChangeColor() {
        double changePercent = getAmountChangePercent();
        if (changePercent > 5) {
            return 0xFFE53935;   // 红色：放量
        } else if (changePercent < -5) {
            return 0xFF43A047;   // 绿色：缩量
        }
        return 0xFF9E9E9E;      // 灰色：平量
    }
}
