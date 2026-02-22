package com.gp.stockapp.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 交易日判断工具
 * 判断是否为A股交易日（排除周末和中国法定节假日）
 *
 * 注意：每年需要更新节假日和调休数据
 */
public class TradingDayHelper {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyyMMdd", Locale.CHINA);

    // ===== 2026年中国法定节假日（股市休市） =====
    private static final Set<String> HOLIDAYS_2026 = new HashSet<>(Arrays.asList(
            // 元旦 1.1-1.3
            "20260101", "20260102", "20260103",
            // 春节 2.16-2.22（除夕到初六）
            "20260216", "20260217", "20260218", "20260219", "20260220", "20260221", "20260222",
            // 清明节 4.4-4.6
            "20260404", "20260405", "20260406",
            // 劳动节 5.1-5.5
            "20260501", "20260502", "20260503", "20260504", "20260505",
            // 端午节 5.31-6.2（预估）
            "20260531", "20260601", "20260602",
            // 中秋节 9.25-9.27（预估）
            "20260925", "20260926", "20260927",
            // 国庆节 10.1-10.7
            "20261001", "20261002", "20261003", "20261004", "20261005", "20261006", "20261007"
    ));

    // 2025年节假日
    private static final Set<String> HOLIDAYS_2025 = new HashSet<>(Arrays.asList(
            "20250101",
            "20250128", "20250129", "20250130", "20250131", "20250201", "20250202", "20250203", "20250204",
            "20250404", "20250405", "20250406",
            "20250501", "20250502", "20250503", "20250504", "20250505",
            "20250531", "20250601", "20250602",
            "20251004", "20251005", "20251006", "20251007", "20251008"
    ));

    // ===== 周末调休上班日（需交易的周末） =====
    private static final Set<String> WORKDAYS_ON_WEEKEND_2026 = new HashSet<>(Arrays.asList(
            "20260214", // 春节调休 周六上班
            "20260215"  // 春节调休 周日上班
            // 其他调休日按实际情况添加
    ));

    private static final Set<String> WORKDAYS_ON_WEEKEND_2025 = new HashSet<>(Arrays.asList(
            "20250126", // 春节调休
            "20250208", // 春节调休
            "20250928", // 国庆调休
            "20251011"  // 国庆调休
    ));

    /**
     * 判断指定日期是否是交易日
     */
    public static boolean isTradingDay(Date date) {
        String dateStr = SDF.format(date);
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

        // 1. 如果是法定节假日 → 休市
        if (isHoliday(dateStr)) {
            return false;
        }

        // 2. 如果是调休工作日（周末但要上班） → 开市
        if (isWorkdayOnWeekend(dateStr)) {
            return true;
        }

        // 3. 如果是周末 → 休市
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            return false;
        }

        // 4. 普通工作日 → 开市
        return true;
    }

    /**
     * 判断今天是否是交易日
     */
    public static boolean isTodayTradingDay() {
        return isTradingDay(new Date());
    }

    /**
     * 获取上一个交易日的日期
     */
    public static Date getPreviousTradingDay(Date fromDate) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(fromDate);

        // 向前找，最多找30天
        for (int i = 0; i < 30; i++) {
            cal.add(Calendar.DAY_OF_MONTH, -1);
            if (isTradingDay(cal.getTime())) {
                return cal.getTime();
            }
        }

        return null; // 理论上不会走到这里
    }

    /**
     * 获取上一个交易日的日期字符串 (yyyyMMdd)
     */
    public static String getPreviousTradingDayStr(Date fromDate) {
        Date prevDay = getPreviousTradingDay(fromDate);
        return prevDay != null ? SDF.format(prevDay) : "";
    }

    /**
     * 获取最近的交易日（如果今天是交易日返回今天，否则返回上一个交易日）
     */
    public static Date getLatestTradingDay() {
        Date today = new Date();
        if (isTradingDay(today)) {
            return today;
        }
        return getPreviousTradingDay(today);
    }

    /**
     * 获取最近的交易日字符串 (yyyyMMdd)
     */
    public static String getLatestTradingDayStr() {
        Date day = getLatestTradingDay();
        return day != null ? SDF.format(day) : "";
    }

    /**
     * 判断两个日期字符串是否为同一个交易日
     * 比如周五数据在周六查看，都属于同一个"最近交易日"
     */
    public static boolean isSameTradingDay(String dateStr1, String dateStr2) {
        if (dateStr1 == null || dateStr2 == null) return false;
        return dateStr1.equals(dateStr2);
    }

    /**
     * 格式化日期
     */
    public static String formatDate(Date date) {
        return SDF.format(date);
    }

    /**
     * 解析日期字符串
     */
    public static Date parseDate(String dateStr) {
        try {
            return SDF.parse(dateStr);
        } catch (ParseException e) {
            return null;
        }
    }

    private static boolean isHoliday(String dateStr) {
        return HOLIDAYS_2025.contains(dateStr) || HOLIDAYS_2026.contains(dateStr);
    }

    private static boolean isWorkdayOnWeekend(String dateStr) {
        return WORKDAYS_ON_WEEKEND_2025.contains(dateStr) || WORKDAYS_ON_WEEKEND_2026.contains(dateStr);
    }
}
