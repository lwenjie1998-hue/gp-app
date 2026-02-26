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

    // 龙虎榜 - 东方财富龙虎榜列表接口（每日更新，16点后可查当天数据）
    // 使用 push2.eastmoney.com 接口，返回JSON格式
    private static final String EASTMONEY_LHB_API =
            "https://push2.eastmoney.com/api/qt/clist/get?" +
            "fid=f184&po=1&pz=100&pn=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281" +
            "&fs=m:1+t:2,m:1+t:23&fields=f12,f14,f2,f3,f62,f184,f66,f69,f72,f75,f78,f81,f84,f87,f124,f128,f136" +
            "&rt=%d";  // 时间戳防缓存

    // 涨停板 - 涨停股池 (东方财富涨停板数据)
    // 使用 push2.eastmoney.com 接口，返回当天实时数据
    private static final String EASTMONEY_LIMIT_UP_API =
            "https://push2.eastmoney.com/api/qt/clist/get?" +
            "fid=f3&po=1&pz=100&pn=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281" +
            "&fs=m:1+t:2,m:1+t:23&fields=f12,f14,f2,f3,f8,f128" +
            "&rt=%d";

    // 连板股数据 (东方财富连板天梯)
    // 使用 push2.eastmoney.com 接口，返回当天实时数据
    private static final String EASTMONEY_CONTINUOUS_LIMIT_API =
            "https://push2.eastmoney.com/api/qt/clist/get?" +
            "fid=f75&po=1&pz=100&pn=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281" +
            "&fs=m:1+t:2,m:1+t:23&fields=f12,f14,f2,f3,f75,f8,f128" +
            "&rt=%d";

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
     * 抓取龙虎榜数据（东方财富 push2 API）
     * 返回JSON格式
     */
    private List<HotStockData.DragonTigerItem> fetchDragonTigerList(String dateStr) throws IOException {
        List<HotStockData.DragonTigerItem> result = new ArrayList<>();

        // 使用时间戳防缓存
        String url = String.format(EASTMONEY_LHB_API, System.currentTimeMillis());
        Log.d(TAG, "龙虎榜请求URL: " + url);

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://data.eastmoney.com/")
                .get()
                .build();
        
        Response response = client.newCall(request).execute();

        if (!response.isSuccessful() || response.body() == null) {
            Log.w(TAG, "龙虎榜请求失败: " + response.code());
            return result;
        }

        String body = response.body().string();
        Log.d(TAG, "龙虎榜响应长度: " + body.length() + ", 前500字符: " + 
                (body.length() > 500 ? body.substring(0, 500) : body));
        
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            
            // 检查data存在
            if (!json.has("data") || json.get("data").isJsonNull()) {
                Log.w(TAG, "龙虎榜无data数据(可能非交易日或数据未更新), dateStr=" + dateStr);
                return result;
            }
            
            JsonObject dataObj = json.getAsJsonObject("data");
            if (!dataObj.has("diff") || dataObj.get("diff").isJsonNull()) {
                Log.w(TAG, "龙虎榜无diff数组, dateStr=" + dateStr);
                return result;
            }

            JsonArray diffArr = dataObj.getAsJsonArray("diff");
            if (diffArr == null || diffArr.size() == 0) {
                Log.w(TAG, "龙虎榜diff数组为空, dateStr=" + dateStr);
                return result;
            }
            Log.d(TAG, "龙虎榜diff数组长度: " + diffArr.size());

            for (JsonElement elem : diffArr) {
                try {
                    JsonObject item = elem.getAsJsonObject();
                    HotStockData.DragonTigerItem lhb = new HotStockData.DragonTigerItem();

                    // 新API字段映射
                    lhb.setCode(getJsonString(item, "f12"));        // 代码
                    lhb.setName(getJsonString(item, "f14"));        // 名称
                    lhb.setClose(getJsonDouble(item, "f2"));        // 收盘价
                    lhb.setChangePercent(getJsonDouble(item, "f3")); // 涨跌幅
                    // 龙虎榜净买入(万) - f184是净买入额(元)
                    lhb.setNetBuy(getJsonDouble(item, "f184") / 10000.0);
                    // 买入总额 - f66是买入额
                    lhb.setBuyAmount(getJsonDouble(item, "f66") / 10000.0);
                    // 卖出总额 - f69是卖出额
                    lhb.setSellAmount(getJsonDouble(item, "f69") / 10000.0);
                    // 换手率 - f8
                    lhb.setTurnoverRate(getJsonDouble(item, "f8"));
                    // 流通市值 - f128
                    lhb.setMarketCap(getJsonDouble(item, "f128") / 100000000.0); // 元->亿
                    // 上榜原因 - 这个API没有提供
                    lhb.setReason("");

                    // 仅保留主板(600xxx/000xxx)，过滤创业板/科创板/北交所，且涨幅<9%
                    String code = lhb.getCode();
                    if (code != null && (code.startsWith("600") || code.startsWith("601") 
                            || code.startsWith("603") || code.startsWith("605") 
                            || code.startsWith("000") || code.startsWith("001") || code.startsWith("002"))
                            && lhb.getChangePercent() < 9.0) {
                        result.add(lhb);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "解析龙虎榜条目失败: " + e.getMessage());
                }
            }
            
            Log.d(TAG, "龙虎榜解析成功: " + result.size() + " 条");
            
        } catch (Exception e) {
            Log.e(TAG, "解析龙虎榜JSON失败", e);
        }

        return result;
    }

    /**
     * 抓取涨停板数据（东方财富 push2 API）
     * 注意：返回当天实时数据
     */
    private List<HotStockData.LimitUpItem> fetchLimitUpList(String dateStr) throws IOException {
        List<HotStockData.LimitUpItem> result = new ArrayList<>();

        String url = String.format(EASTMONEY_LIMIT_UP_API, System.currentTimeMillis());
        Log.d(TAG, "涨停板请求URL: " + url);
        Request request = new Request.Builder().url(url).get().build();
        Response response = client.newCall(request).execute();

        if (!response.isSuccessful() || response.body() == null) {
            Log.w(TAG, "涨停板请求失败: " + response.code());
            return result;
        }

        String body = response.body().string();
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            
            // 检查data存在
            if (!json.has("data") || json.get("data").isJsonNull()) return result;
            JsonObject dataObj = json.getAsJsonObject("data");
            if (!dataObj.has("diff") || dataObj.get("diff").isJsonNull()) return result;

            JsonArray diffArr = dataObj.getAsJsonArray("diff");
            if (diffArr == null) return result;

            for (JsonElement elem : diffArr) {
                try {
                    JsonObject item = elem.getAsJsonObject();
                    
                    String code = getJsonString(item, "f12");
                    double changePercent = getJsonDouble(item, "f3");
                    
                    // 涨停判断：涨幅>=9.9%
                    if (changePercent >= 9.9) {
                        HotStockData.LimitUpItem lu = new HotStockData.LimitUpItem();
                        lu.setCode(code);
                        lu.setName(getJsonString(item, "f14"));
                        lu.setTurnoverRate(getJsonDouble(item, "f8"));
                        lu.setMarketCap(getJsonDouble(item, "f128") / 100000000.0);
                        lu.setLimitUpType("涨停");
                        lu.setConcept("");
                        
                        // 仅保留主板
                        if (code != null && (code.startsWith("600") || code.startsWith("601") 
                                || code.startsWith("603") || code.startsWith("605") 
                                || code.startsWith("000") || code.startsWith("001") || code.startsWith("002"))) {
                            result.add(lu);
                        }
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
     * 抓取连板股数据（东方财富 push2 API）
     * 注意：返回当天实时数据
     */
    private List<HotStockData.ContinuousLimitItem> fetchContinuousLimitList(String dateStr) throws IOException {
        List<HotStockData.ContinuousLimitItem> result = new ArrayList<>();

        String url = String.format(EASTMONEY_CONTINUOUS_LIMIT_API, System.currentTimeMillis());
        Log.d(TAG, "连板股请求URL: " + url);
        Request request = new Request.Builder().url(url).get().build();
        Response response = client.newCall(request).execute();

        if (!response.isSuccessful() || response.body() == null) {
            Log.w(TAG, "连板股请求失败: " + response.code());
            return result;
        }

        String body = response.body().string();
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            
            // 检查data存在
            if (!json.has("data") || json.get("data").isJsonNull()) return result;
            JsonObject dataObj = json.getAsJsonObject("data");
            if (!dataObj.has("diff") || dataObj.get("diff").isJsonNull()) return result;

            JsonArray diffArr = dataObj.getAsJsonArray("diff");
            if (diffArr == null) return result;

            for (JsonElement elem : diffArr) {
                try {
                    JsonObject item = elem.getAsJsonObject();
                    HotStockData.ContinuousLimitItem cl = new HotStockData.ContinuousLimitItem();

                    String code = getJsonString(item, "f12");
                    cl.setCode(code);
                    cl.setName(getJsonString(item, "f14"));
                    // f75是连板数
                    cl.setContinuousCount(getJsonInt(item, "f75"));
                    cl.setChangePercent(getJsonDouble(item, "f3"));
                    cl.setTurnoverRate(getJsonDouble(item, "f8"));
                    cl.setMarketCap(getJsonDouble(item, "f128") / 100000000.0);
                    cl.setConcept("");

                    // 只保留连板数>=2的
                    if (cl.getContinuousCount() >= 2 && code != null && 
                            (code.startsWith("600") || code.startsWith("601") 
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
            
            // 健壮的null检查
            if (!json.has("data") || !json.get("data").isJsonObject()) return result;
            JsonObject data = json.getAsJsonObject("data");
            if (!data.has("diff") || !data.get("diff").isJsonArray()) return result;

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
