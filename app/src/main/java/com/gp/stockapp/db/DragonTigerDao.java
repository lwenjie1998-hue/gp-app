package com.gp.stockapp.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * 龙虎榜数据访问对象
 */
@Dao
public interface DragonTigerDao {
    
    /**
     * 插入龙虎榜数据（重复则替换）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<DragonTigerEntity> items);
    
    /**
     * 插入单条龙虎榜数据
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(DragonTigerEntity item);
    
    /**
     * 获取指定日期的龙虎榜数据
     */
    @Query("SELECT * FROM dragon_tiger_history WHERE tradeDate = :tradeDate ORDER BY netBuy DESC")
    List<DragonTigerEntity> getByDate(String tradeDate);
    
    /**
     * 获取最近N天的龙虎榜数据
     */
    @Query("SELECT * FROM dragon_tiger_history WHERE tradeDate >= :startDate ORDER BY tradeDate DESC, netBuy DESC")
    List<DragonTigerEntity> getRecentDays(String startDate);
    
    /**
     * 获取指定股票的历史龙虎榜记录
     */
    @Query("SELECT * FROM dragon_tiger_history WHERE code = :code ORDER BY tradeDate DESC")
    List<DragonTigerEntity> getByCode(String code);
    
    /**
     * 获取所有交易日列表
     */
    @Query("SELECT DISTINCT tradeDate FROM dragon_tiger_history ORDER BY tradeDate DESC")
    List<String> getAllTradeDates();
    
    /**
     * 获取最新的交易日期
     */
    @Query("SELECT MAX(tradeDate) FROM dragon_tiger_history")
    String getLatestTradeDate();
    
    /**
     * 删除指定日期之前的数据
     */
    @Query("DELETE FROM dragon_tiger_history WHERE tradeDate < :beforeDate")
    void deleteBeforeDate(String beforeDate);
    
    /**
     * 获取数据总条数
     */
    @Query("SELECT COUNT(*) FROM dragon_tiger_history")
    int getCount();
}
