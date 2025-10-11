package com.easydeals.sync.mapper;

import com.easydeals.sync.entity.SyncBatch;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 同步批次Mapper
 */
@Mapper
public interface SyncBatchMapper {
    
    /**
     * 插入批次记录
     */
    @Insert("INSERT INTO sync_batch (batch_id, website_code, data_type, total_count, success_count, " +
            "failed_count, processing_count, status, created_at, updated_at) " +
            "VALUES (#{batchId}, #{websiteCode}, #{dataType}, #{totalCount}, #{successCount}, " +
            "#{failedCount}, #{processingCount}, #{status}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SyncBatch syncBatch);
    
    /**
     * 根据批次ID查询
     */
    @Select("SELECT * FROM sync_batch WHERE batch_id = #{batchId}")
    SyncBatch findByBatchId(String batchId);
    
    /**
     * 更新批次统计信息
     */
    @Update("UPDATE sync_batch SET total_count = #{totalCount}, success_count = #{successCount}, failed_count = #{failedCount}, " +
            "processing_count = #{processingCount}, status = #{status}, updated_at = #{updatedAt} " +
            "WHERE batch_id = #{batchId}")
    int updateStatistics(SyncBatch syncBatch);
    
    /**
     * 根据状态查询批次列表
     */
    @Select("SELECT * FROM sync_batch WHERE status = #{status} ORDER BY created_at ASC")
    List<SyncBatch> findByStatus(Integer status);
    
    /**
     * 根据网站代码和状态统计批次数量
     */
    @Select("SELECT COUNT(*) FROM sync_batch WHERE website_code = #{websiteCode} AND status = #{status}")
    long countByWebsiteCodeAndStatus(@Param("websiteCode") String websiteCode, @Param("status") Integer status);
    
    /**
     * 查询指定时间范围内的批次
     */
    @Select("SELECT * FROM sync_batch WHERE created_at BETWEEN #{startTime} AND #{endTime} ORDER BY created_at DESC")
    List<SyncBatch> findByCreatedAtBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
    
    /**
     * 删除批次记录
     */
    @Delete("DELETE FROM sync_batch WHERE batch_id = #{batchId}")
    int deleteByBatchId(String batchId);
}