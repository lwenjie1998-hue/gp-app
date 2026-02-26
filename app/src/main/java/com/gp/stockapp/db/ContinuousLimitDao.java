package com.gp.stockapp.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * 连板股数据访问对象
 */
@Dao
public interface ContinuousLimitDao {
    
    /**
     * 插入连板股数据（重复则替换）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ContinuousLimitEntity> items);
    
    /**
     * 插入单条连板股数据
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ContinuousLimitEntity item);
    
    /**
     * 获取指定日期的连板股数据
     */
    @Query("SELECT * FROM continuous_limit_history WHERE tradeDate = :tradeDate ORDER BY continuousCount DESC")
    List<ContinuousLimitEntity> getByDate(String tradeDate);
    
    /**
     * 获取最近N天的连板股数据
     */
    @Query("SELECT * FROM continuous_limit_history WHERE tradeDate >= :startDate ORDER BY tradeDate DESC, continuousCount DESC")
    List<ContinuousLimitEntity> getRecentDays(String startDate);
    
    /**
     * 获取指定股票的历史连板记录
     */
    @Query("SELECT * FROM continuous_limit_history WHERE code = :code ORDER BY tradeDate DESC")
    List<ContinuousLimitEntity> getByCode(String code);
    
    /**
     * 获取所有交易日列表
     */
    @Query("SELECT DISTINCT tradeDate FROM continuous_limit_history ORDER BY tradeDate DESC")
    List<String> getAllTradeDates();
    
    /**
     * 获取最新的交易日期
     */
    @Query("SELECT MAX(tradeDate) FROM continuous_limit_history")
    String getLatestTradeDate();
    
    /**
     * 删除指定日期之前的数据
     */
    @Query("DELETE FROM continuous_limit_history WHERE tradeDate < :beforeDate")
    void deleteBeforeDate(String beforeDate);
    
    /**
     * 获取数据总条数
     */
    @Query("SELECT COUNT(*) FROM continuous_limit_history")
    int getCount();
}
