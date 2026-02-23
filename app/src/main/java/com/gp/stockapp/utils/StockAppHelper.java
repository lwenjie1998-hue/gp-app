package com.gp.stockapp.utils;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

/**
 * 股票APP跳转工具类
 * 支持跳转到同花顺、东方财富等第三方APP查看股票详情
 */
public class StockAppHelper {

    private static final String TAG = "StockAppHelper";

    // 同花顺包名
    private static final String THS_PACKAGE = "com.hexin.plat.android";
    // 东方财富包名
    private static final String DFN_PACKAGE = "com.eastmoney.android.berlin";

    /**
     * 打开同花顺APP查看股票详情
     * 始终强制指定同花顺包名，绝不让浏览器接管
     */
    public static void openInTongHuaShun(Context context, String stockCode, String stockName) {
        if (stockCode == null || stockCode.isEmpty()) {
            Toast.makeText(context, "股票代码为空，无法跳转", Toast.LENGTH_SHORT).show();
            return;
        }

        String pureCode = stockCode.replaceAll("[^0-9]", "").trim();
        String displayName = stockName != null && !stockName.isEmpty() ? stockName : stockCode;
        String formattedCode = formatStockCode(pureCode);

        // 先将股票代码复制到剪切板，方便用户在APP内直接粘贴搜索
        copyToClipboard(context, pureCode);

        // 多种 URL scheme 依次尝试，全部强制指定同花顺包名
        String[] schemes = {
            "hexin://stock/detail?code=" + pureCode,
            "ths://stockDetail?stockCode=" + formattedCode,
            "hexin://stockDetail?stockCode=" + formattedCode,
            "hexin://quote/stock?code=" + pureCode,
        };

        for (String url : schemes) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage(THS_PACKAGE);  // 强制同花顺，绝不走浏览器
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Log.d(TAG, "成功跳转: " + url);
                return;
            } catch (ActivityNotFoundException e) {
                Log.d(TAG, "scheme不支持: " + url);
            } catch (Exception e) {
                Log.d(TAG, "跳转异常: " + url + " -> " + e.getMessage());
            }
        }

        // 所有 scheme 都失败，尝试直接打开同花顺APP让用户搜索
        if (tryLaunchApp(context, THS_PACKAGE)) {
            return;
        }

        // 全部失败，确实未安装
        Toast.makeText(context, "未安装同花顺APP", Toast.LENGTH_SHORT).show();
    }

    /**
     * 复制文本到剪切板（确保在主线程执行）
     */
    private static void copyToClipboard(Context context, String text) {
        Runnable copyTask = () -> {
            try {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ClipData clip = ClipData.newPlainText("stock_code", text);
                    clipboard.setPrimaryClip(clip);
                    Log.d(TAG, "已复制到剪切板: " + text);
                    Toast.makeText(context, "已复制: " + text, Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "ClipboardManager为null");
                }
            } catch (Exception e) {
                Log.e(TAG, "复制到剪切板失败: " + e.getMessage());
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            // 当前是主线程，直接执行，避免因Activity切换导致的应用失去焦点问题
            copyTask.run();
        } else {
            // 非主线程，Post到主线程执行
            new Handler(Looper.getMainLooper()).post(copyTask);
        }
    }

    /**
     * 直接启动APP（通过 getLaunchIntentForPackage）
     */
    private static boolean tryLaunchApp(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
                Log.d(TAG, "通过LaunchIntent打开APP");
                return true;
            }
        } catch (Exception e) {
            Log.d(TAG, "LaunchIntent失败: " + e.getMessage());
        }
        return false;
    }

    /**
     * 格式化股票代码
     * 沪市（60开头）：前缀1 -> 1.600000
     * 深市（00/30开头）：前缀0 -> 0.000001 或 0.300001
     * 科创板（688开头）：前缀1 -> 1.688000
     * 
     * @param stockCode 原始股票代码（如：000001、600000）
     * @return 格式化后的代码（如：0.000001、1.600000）
     */
    private static String formatStockCode(String stockCode) {
        if (stockCode == null || stockCode.isEmpty()) {
            return stockCode;
        }

        // 去除空格
        stockCode = stockCode.trim();

        // 如果已经是带前缀的格式（如0.000001），直接返回
        if (stockCode.matches("^[01]\\.\\d{6}$")) {
            return stockCode;
        }

        // 提取纯数字代码（去除可能的市场前缀）
        String pureCode = stockCode.replaceAll("[^0-9]", "");
        
        if (pureCode.length() != 6) {
            return stockCode; // 不是标准6位代码，直接返回原值
        }

        // 判断市场
        if (pureCode.startsWith("60") || pureCode.startsWith("68")) {
            // 沪市：60/68开头
            return "1." + pureCode;
        } else if (pureCode.startsWith("00") || pureCode.startsWith("30")) {
            // 深市：00/30开头
            return "0." + pureCode;
        } else {
            // 其他情况，默认深市
            return "0." + pureCode;
        }
    }

    /**
     * 打开应用市场下载APP
     */
    public static void openAppInMarket(Context context, String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            // 如果没有应用市场，用浏览器打开
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=" + packageName));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(context, "无法打开应用市场", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 从股票名称中提取股票代码
     * 支持格式：
     * - "中国平安(601318)"
     * - "中国平安 601318"
     * - "601318 中国平安"
     * - "601318"
     * 
     * @param stockNameOrCode 股票名称或代码
     * @return 提取出的代码，如果提取失败返回原字符串
     */
    public static String extractStockCode(String stockNameOrCode) {
        if (stockNameOrCode == null || stockNameOrCode.isEmpty()) {
            return stockNameOrCode;
        }

        // 匹配括号中的6位数字：(000001)
        if (stockNameOrCode.matches(".*\\(\\d{6}\\).*")) {
            return stockNameOrCode.replaceAll(".*\\((\\d{6})\\).*", "$1");
        }

        // 匹配空格分隔的6位数字：中国平安 601318 或 601318 中国平安
        if (stockNameOrCode.matches(".*\\s+\\d{6}.*")) {
            return stockNameOrCode.replaceAll(".*\\s+(\\d{6}).*", "$1");
        }
        if (stockNameOrCode.matches("\\d{6}\\s+.*")) {
            return stockNameOrCode.replaceAll("(\\d{6})\\s+.*", "$1");
        }

        // 匹配纯6位数字
        if (stockNameOrCode.matches("\\d{6}")) {
            return stockNameOrCode;
        }

        // 无法提取，返回原字符串
        return stockNameOrCode;
    }
}
