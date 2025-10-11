package com.easydeals.sync.service;

import com.easydeals.sync.entity.SyncBatch;
import com.easydeals.sync.mapper.SyncBatchMapper;
import com.easydeals.sync.mapper.SyncTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 同步批次服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncBatchService {
    
    private final SyncBatchMapper syncBatchMapper;
    private final SyncTaskMapper syncTaskMapper;
    
    /**
     * 创建同步批次
     */
    @Transactional
    public SyncBatch createBatch(String websiteCode, String dataType, int totalCount) {
        String batchId = generateBatchId();
        
        SyncBatch batch = SyncBatch.builder()
                .batchId(batchId)
                .websiteCode(websiteCode)
                .dataType(dataType)
                .totalCount(totalCount)
                .status(SyncBatch.Status.PENDING.getCode())
                .build();
        
        syncBatchMapper.insert(batch);
        
        log.info("创建同步批次: batchId={}, websiteCode={}, totalCount={}", 
                batchId, websiteCode, totalCount);
        
        return batch;
    }
    
    /**
     * 更新批次的实际任务数量
     */
    @Transactional
    public void updateBatchTotalCount(String batchId, int actualTaskCount) {
        SyncBatch batch = syncBatchMapper.findByBatchId(batchId);
        if (batch == null) {
            log.warn("批次不存在: batchId={}", batchId);
            return;
        }
        
        batch.setTotalCount(actualTaskCount);
        batch.setUpdatedAt(LocalDateTime.now());
        
        syncBatchMapper.updateStatistics(batch);
        
        log.info("更新批次实际任务数量: batchId={}, actualTaskCount={}", batchId, actualTaskCount);
    }
    
    /**
     * 更新批次统计信息
     */
    @Transactional
    public void updateBatchStatistics(String batchId) {
        // 查询批次下各状态任务数量
        List<SyncTaskMapper.TaskStatusCount> statusCounts = syncTaskMapper.countByBatchIdGroupByStatus(batchId);
        
        int successCount = 0;
        int failedCount = 0;
        int processingCount = 0;
        
        for (SyncTaskMapper.TaskStatusCount statusCount : statusCounts) {
            switch (statusCount.getStatus()) {
                case 2: // SUCCESS
                    successCount = statusCount.getCount().intValue();
                    break;
                case 3: // FAILED
                    failedCount = statusCount.getCount().intValue();
                    break;
                case 1: // PROCESSING
                    processingCount = statusCount.getCount().intValue();
                    break;
            }
        }
        
        // 获取批次信息
        SyncBatch batch = syncBatchMapper.findByBatchId(batchId);
        if (batch == null) {
            log.warn("批次不存在: batchId={}", batchId);
            return;
        }
        
        // 更新统计信息
        batch.setSuccessCount(successCount);
        batch.setFailedCount(failedCount);
        batch.setProcessingCount(processingCount);
        batch.setUpdatedAt(LocalDateTime.now());
        
        // 判断批次状态
        int totalProcessed = successCount + failedCount;
        if (totalProcessed == batch.getTotalCount()) {
            // 全部处理完成
            if (failedCount == 0) {
                batch.setStatus(SyncBatch.Status.COMPLETED.getCode());
            } else {
                batch.setStatus(SyncBatch.Status.PARTIAL_FAILED.getCode());
            }
        } else if (processingCount > 0 || totalProcessed > 0) {
            // 有任务在处理中或已处理部分
            batch.setStatus(SyncBatch.Status.PROCESSING.getCode());
        }
        
        syncBatchMapper.updateStatistics(batch);
        
        log.info("更新批次统计: batchId={}, success={}, failed={}, processing={}, status={}",
                batchId, successCount, failedCount, processingCount, batch.getStatus());
    }
    
    /**
     * 根据批次ID查询批次
     */
    public SyncBatch getBatch(String batchId) {
        return syncBatchMapper.findByBatchId(batchId);
    }
    
    /**
     * 查询指定状态的批次列表
     */
    public List<SyncBatch> getBatchesByStatus(SyncBatch.Status status) {
        return syncBatchMapper.findByStatus(status.getCode());
    }
    
    /**
     * 删除批次及其所有任务
     */
    @Transactional
    public void deleteBatch(String batchId) {
        // 先删除任务
        syncTaskMapper.deleteByBatchId(batchId);
        // 再删除批次
        syncBatchMapper.deleteByBatchId(batchId);
        
        log.info("删除批次及其任务: batchId={}", batchId);
    }
    
    /**
     * 生成批次ID
     */
    private String generateBatchId() {
        return "BATCH_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}