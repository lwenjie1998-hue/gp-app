package com.gp.stockapp.api;

import android.util.Log;

import com.gp.stockapp.model.HotStockData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 热门股票数据API
 * 从东方财富抓取龙虎榜、涨停板、连板股、涨幅榜等数据
 * 为AI策略分析提供真实的个股级别数据
 */
public class HotStockApi {
    private static final String TAG = "HotStockApi";
    private static HotStockApi instance;

    private OkHttpClient client;

    // ===== 东方财富数据接口 =====

    // 龙虎榜 - 每日活跃营业部
    // 返回字段: f2(收盘价) f3(涨跌幅) f6(成交额) f8(换手率) f12(代码) f14(名称) f20(总市值) f21(流通市值)
    // 净买入相关: f22(上榜后1日涨幅) 等; 详细买卖用子接口
    private static final String EASTMONEY_LHB_API =
            "https://datacenter-web.eastmoney.com/api/data/v1/get" +
            "?sortColumns=SECURITY_CODE&sortTypes=1&pageSize=50&pageNumber=1" +
            "&reportName=RPT_DAILYBILLBOARD_DETAILSNEW" +
            "&columns=SECURITY_CODE,SECURITY_NAME_ABBR,CHANGE_RATE,CLOSE_PRICE," +
            "TURNOVERRATE,BILLBOARD_NET_AMT,BILLBOARD_BUY_AMT,BILLBOARD_SELL_AMT," +
            "BILLBOARD_DEAL_AMT,EXPLANATION,DEAL_AMT,FREE_MARKET_CAP" +
            "&filter=(TRADE_DATE='%s')&source=WEB&client=WEB";

    // 涨停板 - 涨停股池 (东方财富涨停板数据)
    // f1=2: 涨停  f2=1: 沪深A股
    private static final String EASTMONEY_LIMIT_UP_API =
            "https://push2ex.eastmoney.com/getTopicZTPool" +
            "?ut=7eea3edcaed734bea9cb99f84f5d01d6&dpt=wz.ztzt&Ession=" +
            "&date=%s&_=";

    // 连板股数据 (东方财富连板天梯)
    private static final String EASTMONEY_CONTINUOUS_LIMIT_API =
            "https://push2ex.eastmoney.com/getTopicLBPool" +
            "?ut=7eea3edcaed734bea9cb99f84f5d01d6&dpt=wz.ztzt&Ession=" +
            "&date=%s&_=";

    // 活跃股 - A股主板活跃股（按成交额降序排列, 取前30）
    // 仅主板(600xxx/000xxx)，排除创业板(300)、科创板(688)和北交所
    private static final String EASTMONEY_TOP_GAINERS_API =
            "https://push2.eastmoney.com/api/qt/clist/get" +
            "?pn=1&pz=30&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281" +
            "&fltt=2&invt=2&fid=f6&fs=m:0+t:6,m:1+t:2" +
            "&fields=f2,f3,f4,f5,f6,f7,f8,f9,f10,f12,f14,f15,f16,f17,f18,f20,f21" +
            "&_=";

    private HotStockApi() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request request = original.newBuilder()
                            .header("Referer", "https://data.eastmoney.com/")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .build();
                    return chain.proceed(request);
                })
                .build();
    }

    public static synchronized HotStockApi getInstance() {
        if (instance == null) {
            instance = new HotStockApi();
        }
        return instance;
    }

    /**
     * 抓取所有热门股票数据
     * @param dateStr 日期，格式 yyyyMMdd（龙虎榜和涨停用）
     */
    public HotStockData fetchAllHotData(String dateStr) {
        HotStockData data = new HotStockData();
        data.setTimestamp(System.currentTimeMillis());

        // 龙虎榜
        try {
            List<HotStockData.DragonTigerItem> lhb = fetchDragonTigerList(dateStr);
            data.setDragonTigerList(lhb);
            Log.d(TAG, "龙虎榜抓取成功: " + (lhb != null ? lhb.size() : 0) + " 条");
        } catch (Exception e) {
            Log.e(TAG, "龙虎榜抓取失败", e);
        }

        // 涨停板
        try {
            List<HotStockData.LimitUpItem> limitUp = fetchLimitUpList(dateStr);
            data.setLimitUpList(limitUp);
            Log.d(TAG, "涨停板抓取成功: " + (limitUp != null ? limitUp.size() : 0) + " 条");
        } catch (Exception e) {
            Log.e(TAG, "涨停板抓取失败", e);
        }

        // 连板股
        try {
            List<HotStockData.ContinuousLimitItem> continuous = fetchContinuousLimitList(dateStr);
            data.setContinuousLimitList(continuous);
            Log.d(TAG, "连板股抓取成功: " + (continuous != null ? continuous.size() : 0) + " 条");
        } catch (Exception e) {
            Log.e(TAG, "连板股抓取失败", e);
        }

        // 涨幅榜
        try {
            List<HotStockData.TopGainerItem> gainers = fetchTopGainers();
            data.setTopGainers(gainers);
            Log.d(TAG, "涨幅榜抓取成功: " + (gainers != null ? gainers.size() : 0) + " 条");
        } catch (Exception e) {
            Log.e(TAG, "涨幅榜抓取失败", e);
        }

        return data;
    }

    /**
     * 抓取龙虎榜数据（东方财富 datacenter-web）
     */
    private List<HotStockData.DragonTigerItem> fetchDragonTigerList(String dateStr) throws IOException {
        List<HotStockData.DragonTigerItem> result = new ArrayList<>();

        // dateStr格式转换: 20260223 -> 2026-02-23
        String formattedDate = dateStr.substring(0, 4) + "-" + dateStr.substring(4, 6) + "-" + dateStr.substring(6, 8);
        String url = String.format(EASTMONEY_LHB_API, formattedDate);

        Request request = new Request.Builder().url(url).get().build();
        Response response = client.newCall(request).execute();

        if (!response.isSuccessful() || response.body() == null) {
            Log.w(TAG, "龙虎榜请求失败: " + response.code());
            return result;
        }

        String body = response.body().string();
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (!json.has("result") || json.get("result").isJsonNull()) {
                Log.w(TAG, "龙虎榜无数据(可能非交易日或数据未更新)");
                return result;
            }

            JsonObject resultObj = json.getAsJsonObject("result");
            JsonArray dataArr = resultObj.getAsJsonArray("data");
            if (dataArr == null) return result;

            for (JsonElement elem : dataArr) {
                try {
                    JsonObject item = elem.getAsJsonObject();
                    HotStockData.DragonTigerItem lhb = new HotStockData.DragonTigerItem();

                    lhb.setCode(getJsonString(item, "SECURITY_CODE"));
                    lhb.setName(getJsonString(item, "SECURITY_NAME_ABBR"));
                    lhb.setClose(getJsonDouble(item, "CLOSE_PRICE"));
                    lhb.setChangePercent(getJsonDouble(item, "CHANGE_RATE"));
                    lhb.setTurnoverRate(getJsonDouble(item, "TURNOVERRATE"));
                    lhb.setNetBuy(getJsonDouble(item, "BILLBOARD_NET_AMT") / 10000.0); // 元->万
                    lhb.setBuyAmount(getJsonDouble(item, "BILLBOARD_BUY_AMT") / 10000.0);
                    lhb.setSellAmount(getJsonDouble(item, "BILLBOARD_SELL_AMT") / 10000.0);
                    lhb.setReason(getJsonString(item, "EXPLANATION"));
                    lhb.setMarketCap(getJsonDouble(item, "FREE_MARKET_CAP") / 100000000.0); // 元->亿

                    // 仅保留主板(600xxx/000xxx)，过滤创业板/科创板/北交所，且涨幅<9%
                    String code = lhb.getCode();
                    if (code != null && (code.startsWith("600") || code.startsWith("601") 
                            || code.startsWith("603") || code.startsWith("605") 
                            || code.startsWith("000") || code.startsWith("001") || code.startsWith("002"))
                            && lhb.getChangePercent() < 9.0) {
                        result.add(lhb);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "解析龙虎榜条目失败", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析龙虎榜JSON失败", e);
        }

        return result;
    }

    /**
     * 抓取涨停板数据（东方财富涨停股池）
     */
    private List<HotStockData.LimitUpItem> fetchLimitUpList(String dateStr) throws IOException {
        List<HotStockData.LimitUpItem> result = new ArrayList<>();

        String url = String.format(EASTMONEY_LIMIT_UP_API, dateStr) + System.currentTimeMillis();
        Request request = new Request.Builder().url(url).get().build();
        Response response = client.newCall(request).execute();

        if (!response.isSuccessful() || response.body() == null) {
            Log.w(TAG, "涨停板请求失败: " + response.code());
            return result;
        }

        String body = response.body().string();
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonObject data = json.getAsJsonObject("data");
            if (data == null || !data.has("pool")) return result;

            JsonArray pool = data.getAsJsonArray("pool");
            if (pool == null) return result;

            for (JsonElement elem : pool) {
                try {
                    JsonObject item = elem.getAsJsonObject();
                    HotStockData.LimitUpItem lu = new HotStockData.LimitUpItem();

                    String code = getJsonString(item, "c");    // 代码
                    lu.setCode(code);
                    lu.setName(getJsonString(item, "n"));      // 名称

                    // 首次涨停时间
                    String fbt = getJsonString(item, "fbt");
                    if (fbt != null && fbt.length() == 6) {
                        lu.setFirstLimitTime(fbt.substring(0, 2) + ":" + fbt.substring(2, 4) + ":" + fbt.substring(4, 6));
                    }
                    // 最后涨停时间
                    String lbt = getJsonString(item, "lbt");
                    if (lbt != null && lbt.length() == 6) {
                        lu.setLastLimitTime(lbt.substring(0, 2) + ":" + lbt.substring(2, 4) + ":" + lbt.substring(4, 6));
                    }

                    lu.setOpenCount(getJsonInt(item, "oc"));   // 开板次数
                    lu.setTurnoverRate(getJsonDouble(item, "tr") / 100.0); // 换手率(接口返回的是百分比*100)

                    // 流通市值(元->亿)
                    lu.setMarketCap(getJsonDouble(item, "ltsz") / 100000000.0);

                    // 涨停类型判断
                    double tr = lu.getTurnoverRate();
                    int oc = lu.getOpenCount();
                    if (tr < 1.0 && oc == 0) {
                        lu.setLimitUpType("一字板");
                    } else if (tr < 3.0 && oc == 0) {
                        lu.setLimitUpType("T字板");
                    } else {
                        lu.setLimitUpType("换手板");
                    }

                    // 所属概念
                    lu.setConcept(getJsonString(item, "hybk"));

                    // 仅保留主板(600xxx/000xxx)，过滤创业板/科创板/北交所
                    if (code != null && (code.startsWith("600") || code.startsWith("601") 
                            || code.startsWith("603") || code.startsWith("605") 
                            || code.startsWith("000") || code.startsWith("001") || code.startsWith("002"))) {
                        result.add(lu);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "解析涨停板条目失败", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析涨停板JSON失败", e);
        }

        return result;
    }

    /**
     * 抓取连板股数据（东方财富连板天梯）
     */
    private List<HotStockData.ContinuousLimitItem> fetchContinuousLimitList(String dateStr) throws IOException {
        List<HotStockData.ContinuousLimitItem> result = new ArrayList<>();

        String url = String.format(EASTMONEY_CONTINUOUS_LIMIT_API, dateStr) + System.currentTimeMillis();
        Request request = new Request.Builder().url(url).get().build();
        Response response = client.newCall(request).execute();

        if (!response.isSuccessful() || response.body() == null) {
            Log.w(TAG, "连板股请求失败: " + response.code());
            return result;
        }

        String body = response.body().string();
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonObject data = json.getAsJsonObject("data");
            if (data == null || !data.has("pool")) return result;

            JsonArray pool = data.getAsJsonArray("pool");
            if (pool == null) return result;

            for (JsonElement elem : pool) {
                try {
                    JsonObject item = elem.getAsJsonObject();
                    HotStockData.ContinuousLimitItem cl = new HotStockData.ContinuousLimitItem();

                    String code = getJsonString(item, "c");
                    cl.setCode(code);
                    cl.setName(getJsonString(item, "n"));
                    cl.setContinuousCount(getJsonInt(item, "ct"));    // 连板天数
                    cl.setChangePercent(getJsonDouble(item, "zdp") / 100.0);
                    cl.setTurnoverRate(getJsonDouble(item, "tr") / 100.0);
                    cl.setMarketCap(getJsonDouble(item, "ltsz") / 100000000.0);
                    cl.setConcept(getJsonString(item, "hybk"));

                    if (code != null && (code.startsWith("600") || code.startsWith("601") 
                            || code.startsWith("603") || code.startsWith("605") 
                            || code.startsWith("000") || code.startsWith("001") || code.startsWith("002"))) {
                        result.add(cl);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "解析连板股条目失败", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析连板股JSON失败", e);
        }

        return result;
    }

    /**
     * 抓取主板活跃股TOP30（按成交额排序，仅600xxx/000xxx主板）
     */
    private List<HotStockData.TopGainerItem> fetchTopGainers() throws IOException {
        List<HotStockData.TopGainerItem> result = new ArrayList<>();

        String url = EASTMONEY_TOP_GAINERS_API + System.currentTimeMillis();
        Request request = new Request.Builder().url(url).get().build();
        Response response = client.newCall(request).execute();

        if (!response.isSuccessful() || response.body() == null) {
            Log.w(TAG, "涨幅榜请求失败: " + response.code());
            return result;
        }

        String body = response.body().string();
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonObject data = json.getAsJsonObject("data");
            if (data == null || !data.has("diff")) return result;

            JsonArray diff = data.getAsJsonArray("diff");
            if (diff == null) return result;

            for (JsonElement elem : diff) {
                try {
                    JsonObject item = elem.getAsJsonObject();
                    HotStockData.TopGainerItem gainer = new HotStockData.TopGainerItem();

                    String code = getJsonString(item, "f12");   // 代码
                    gainer.setCode(code);
                    gainer.setName(getJsonString(item, "f14"));  // 名称
                    gainer.setClose(getJsonDouble(item, "f2"));  // 最新价
                    gainer.setChangePercent(getJsonDouble(item, "f3")); // 涨跌幅
                    gainer.setTurnoverRate(getJsonDouble(item, "f8")); // 换手率
                    gainer.setAmount(getJsonDouble(item, "f6") / 10000.0); // 成交额 元->万
                    gainer.setMarketCap(getJsonDouble(item, "f21") / 100000000.0); // 流通市值 元->亿

                    // 仅保留主板(600xxx/000xxx)，过滤创业板/科创板/北交所/ST，且涨幅<9%
                    String name = gainer.getName();
                    if (code != null && (code.startsWith("600") || code.startsWith("601") 
                            || code.startsWith("603") || code.startsWith("605") || code.startsWith("000"))
                            && name != null && !name.contains("ST")
                            && gainer.getChangePercent() < 9.0) {
                        result.add(gainer);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "解析涨幅榜条目失败", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析涨幅榜JSON失败", e);
        }

        return result;
    }

    // ===== JSON工具方法 =====

    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private double getJsonDouble(JsonObject obj, String key) {
        try {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsDouble();
            }
        } catch (Exception e) {
            // ignore
        }
        return 0.0;
    }

    private int getJsonInt(JsonObject obj, String key) {
        try {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsInt();
            }
        } catch (Exception e) {
            // ignore
        }
        return 0;
    }
}
