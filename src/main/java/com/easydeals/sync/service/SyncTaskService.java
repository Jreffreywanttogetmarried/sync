package com.easydeals.sync.service;

import com.easydeals.sync.dto.*;
import com.easydeals.sync.entity.SyncBatch;
import com.easydeals.sync.entity.SyncTask;
import com.easydeals.sync.enums.DataTypeEnum;
import com.easydeals.sync.mapper.SyncTaskMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 同步任务服务（重构支持批量处理和自动实体类装配）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncTaskService {
    
    private final SyncTaskMapper syncTaskMapper;
    private final SyncBatchService syncBatchService;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    
    @Value("${task.batch-size:100}")
    private int batchSize;
    
    private static final String SYNC_QUEUE = "sync:queue";
    
    /**
     * 提交同步任务（支持大批量数据拆分和自动实体类装配）
     */
    @Transactional
    public SyncResponse submitSyncTasks(SyncRequest<?> request) {
        List<String> successTaskIds = new ArrayList<>();
        List<SyncResponse.FailedTask> failedTasks = new ArrayList<>();
        List<String> taskIdsToQueue = new ArrayList<>(); // 用于存储需要入队的任务ID
        
        try {
            // 验证数据类型是否支持
            if (!DataTypeEnum.isSupported(request.getDataType())) {
                return SyncResponse.error("不支持的数据类型: " + request.getDataType());
            }
            
            // 1. 先创建一个临时批次记录（totalCount暂时设为0）
            SyncBatch batch = syncBatchService.createBatch(
                    request.getWebsiteCode(), 
                    request.getDataType(), 
                    0  // 先设为0，后面会更新为实际任务数量
            );
            
            // 2. 拆分大数据为单条任务
            List<SyncTask> tasks = new ArrayList<>();
            
            for (Object dataItem : request.getDataList()) {
                try {
                    // 提取业务ID（通过反射获取businessId字段）
                    String businessId = extractBusinessId(dataItem);
                    
                    // 检查是否已经成功同步过
                    SyncTask existingTask = syncTaskMapper.findByWebsiteCodeAndBusinessIdAndStatus(
                            request.getWebsiteCode(), 
                            businessId, 
                            SyncTask.Status.SUCCESS.getCode()
                    );
                    
                    if (existingTask != null) {
                        log.warn("任务已存在且已成功: websiteCode={}, businessId={}", 
                                request.getWebsiteCode(), businessId);
                        failedTasks.add(SyncResponse.FailedTask.builder()
                                .businessId(businessId)
                                .reason("数据已存在且已成功同步")
                                .build());
                        continue;
                    }
                    
                    // 创建单条任务
                    String taskId = generateTaskId();
                    String syncDataJson = objectMapper.writeValueAsString(dataItem);
                    
                    SyncTask syncTask = SyncTask.builder()
                            .taskId(taskId)
                            .batchId(batch.getBatchId())
                            .websiteCode(request.getWebsiteCode())
                            .dataType(request.getDataType())
                            .businessId(businessId)
                            .syncData(syncDataJson)
                            .status(SyncTask.Status.PENDING.getCode())
                            .build();
                    
                    tasks.add(syncTask);
                    successTaskIds.add(taskId);
                    taskIdsToQueue.add(taskId); // 记录需要入队的任务ID
                    
                    // 批量插入优化
                    if (tasks.size() >= batchSize) {
                        syncTaskMapper.batchInsert(tasks);
                        log.info("批量插入任务: batchId={}, count={}", batch.getBatchId(), tasks.size());
                        tasks.clear();
                    }
                    
                } catch (JsonProcessingException e) {
                    log.error("JSON序列化失败: dataItem={}", dataItem, e);
                    failedTasks.add(SyncResponse.FailedTask.builder()
                            .businessId("unknown")
                            .reason("数据格式错误")
                            .build());
                } catch (Exception e) {
                    log.error("任务创建失败: dataItem={}", dataItem, e);
                    failedTasks.add(SyncResponse.FailedTask.builder()
                            .businessId("unknown")
                            .reason("系统错误: " + e.getMessage())
                            .build());
                }
            }
            
            // 处理剩余任务
            if (!tasks.isEmpty()) {
                syncTaskMapper.batchInsert(tasks);
                log.info("批量插入剩余任务: batchId={}, count={}", batch.getBatchId(), tasks.size());
            }
            
            // 3. 更新批次的实际任务数量
            int actualTaskCount = successTaskIds.size();
            syncBatchService.updateBatchTotalCount(batch.getBatchId(), actualTaskCount);
            
            // 4. 更新批次统计
            syncBatchService.updateBatchStatistics(batch.getBatchId());
            
            log.info("批量任务提交完成: batchId={}, dataType={}, 请求数量={}, 实际任务数量={}, failedCount={}", 
                    batch.getBatchId(), request.getDataType(), request.getDataList().size(), actualTaskCount, failedTasks.size());
            
        } catch (Exception e) {
            log.error("批量任务提交异常: websiteCode={}, dataType={}", 
                    request.getWebsiteCode(), request.getDataType(), e);
            return SyncResponse.error("系统异常: " + e.getMessage());
        }
        
        // 5. 事务提交后，将任务加入队列
        addTasksToQueue(taskIdsToQueue);
        
        // 返回响应
        if (failedTasks.isEmpty()) {
            return SyncResponse.success(successTaskIds);
        } else if (!successTaskIds.isEmpty()) {
            return SyncResponse.partialSuccess(successTaskIds, failedTasks);
        } else {
            return SyncResponse.error("所有任务提交失败");
        }
    }
    
    /**
     * 通过反射或Map方式提取业务ID
     */
    private String extractBusinessId(Object dataItem) {
        try {
            // 如果是Map类型（JSON反序列化的默认类型）
            if (dataItem instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) dataItem;
                Object businessId = dataMap.get("businessId");
                return businessId != null ? businessId.toString() : "unknown";
            }
            
            // 如果是实体类，使用反射获取businessId字段
            java.lang.reflect.Field field = dataItem.getClass().getDeclaredField("businessId");
            field.setAccessible(true);
            Object value = field.get(dataItem);
            return value != null ? value.toString() : "unknown";
            
        } catch (Exception e) {
            log.warn("无法提取businessId: {}, dataItem类型: {}", e.getMessage(), dataItem.getClass().getSimpleName());
            return "unknown";
        }
    }
    
    /**
     * 将任务加入队列（在事务外执行）
     */
    private void addTasksToQueue(List<String> taskIds) {
        if (taskIds.isEmpty()) {
            return;
        }
        
        try {
            // 添加短暂延迟，确保数据库事务完全提交
            Thread.sleep(100);
            
            RQueue<String> queue = redissonClient.getQueue(SYNC_QUEUE);
            for (String taskId : taskIds) {
                queue.offer(taskId);
            }
            log.info("任务已加入队列: count={}", taskIds.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("任务加入队列被中断: taskIds={}", taskIds, e);
        } catch (Exception e) {
            log.error("任务加入队列失败: taskIds={}", taskIds, e);
        }
    }
    
    /**
     * 查询任务状态
     */
    public TaskStatusResponse getTaskStatus(String taskId) {
        SyncTask task = syncTaskMapper.findByTaskId(taskId);
        
        if (task == null) {
            return null;
        }
        
        SyncTask.Status status = SyncTask.Status.fromCode(task.getStatus());
        
        return TaskStatusResponse.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus())
                .statusDesc(status.getDescription())
                .retryCount(task.getRetryCount())
                .crmRequestCode(task.getCrmRequestCode())
                .errorMsg(task.getErrorMsg())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
    
    /**
     * 更新任务状态为处理中
     */
    @Transactional
    public void updateTaskToProcessing(String taskId) {
        syncTaskMapper.updateStatus(taskId, SyncTask.Status.PROCESSING.getCode(), LocalDateTime.now());
        log.info("任务状态更新为处理中: taskId={}", taskId);
    }
    
    /**
     * 更新任务状态为待处理
     */
    @Transactional
    public void updateTaskToPending(String taskId) {
        syncTaskMapper.updateStatus(taskId, SyncTask.Status.PENDING.getCode(), LocalDateTime.now());
        log.info("任务状态更新为待处理: taskId={}", taskId);
    }
    
    /**
     * 更新任务CRM请求编码
     */
    @Transactional
    public void updateTaskCrmRequestCode(String taskId, String requestCode) {
        syncTaskMapper.updateCrmRequestCode(taskId, requestCode, LocalDateTime.now());
        log.info("任务CRM请求编码已更新: taskId={}, requestCode={}", taskId, requestCode);
    }
    
    /**
     * 更新任务状态为成功
     */
    @Transactional
    public void updateTaskToSuccess(String taskId) {
        syncTaskMapper.updateToSuccess(taskId, LocalDateTime.now());
        
        // 更新批次统计
        SyncTask task = syncTaskMapper.findByTaskId(taskId);
        if (task != null) {
            syncBatchService.updateBatchStatistics(task.getBatchId());
        }
        
        log.info("任务状态更新为成功: taskId={}", taskId);
    }
    
    /**
     * 更新任务状态为失败
     */
    @Transactional
    public void updateTaskToFailed(String taskId, String errorMsg) {
        syncTaskMapper.updateToFailed(taskId, errorMsg, LocalDateTime.now());
        
        // 更新批次统计
        SyncTask task = syncTaskMapper.findByTaskId(taskId);
        if (task != null) {
            syncBatchService.updateBatchStatistics(task.getBatchId());
        }
        
        log.error("任务状态更新为失败: taskId={}, errorMsg={}", taskId, errorMsg);
    }
    
    /**
     * 获取任务详情
     */
    public SyncTask getTask(String taskId) {
        return syncTaskMapper.findByTaskId(taskId);
    }
    
    /**
     * 查询需要重试的任务
     */
    public List<SyncTask> getRetryableTasks(int maxRetry, int retryIntervalMinutes) {
        LocalDateTime beforeTime = LocalDateTime.now().minusMinutes(retryIntervalMinutes);
        return syncTaskMapper.findRetryableTasks(maxRetry, beforeTime, batchSize);
    }
    
    /**
     * 查询处理中且有CRM请求编码的任务
     */
    public List<SyncTask> getProcessingTasksWithRequestCode() {
        return syncTaskMapper.findProcessingTasksWithRequestCode(batchSize);
    }
    
    /**
     * 获取任务统计信息
     */
    public long getTaskCountByStatus(SyncTask.Status status) {
        return syncTaskMapper.countByStatus(status.getCode());
    }
    
    /**
     * 生成任务ID
     */
    private String generateTaskId() {
        return "TASK_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}