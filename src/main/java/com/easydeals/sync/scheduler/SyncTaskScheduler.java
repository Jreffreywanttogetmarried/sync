package com.easydeals.sync.scheduler;

import com.easydeals.sync.dto.CrmStatusResponse;
import com.easydeals.sync.entity.SyncTask;
import com.easydeals.sync.service.CrmApiService;
import com.easydeals.sync.service.SyncTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 同步任务定时调度器
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class SyncTaskScheduler {
    
    private final SyncTaskService syncTaskService;
    private final CrmApiService crmApiService;
    private final RedissonClient redissonClient;
    
    @Value("${crm.api.max-retry:3}")
    private int maxRetry;
    
    @Value("${crm.api.retry-interval:5000}")
    private int retryInterval;
    
    private static final String SYNC_QUEUE = "sync:queue";
    
    /**
     * 查询CRM执行结果
     * 每10秒执行一次
     */
    @Scheduled(fixedDelay = 10000)
    public void queryExecutionResults() {
        try {
            List<SyncTask> processingTasks = syncTaskService.getProcessingTasksWithRequestCode();
            
            if (processingTasks.isEmpty()) {
                return;
            }
            
            log.info("开始查询CRM执行结果，任务数量: {}", processingTasks.size());
            
            for (SyncTask task : processingTasks) {
                try {
                    CrmStatusResponse statusResponse = crmApiService.queryExecutionResult(task.getCrmRequestCode());
                    
                    if (statusResponse.isSuccess()) {
                        // 执行成功
                        syncTaskService.updateTaskToSuccess(task.getTaskId());
                        log.info("任务执行成功: taskId={}, requestCode={}", 
                                task.getTaskId(), task.getCrmRequestCode());
                    } else if (statusResponse.isFailed()) {
                        // 执行失败
                        String errorMsg = "CRM执行失败: " + statusResponse.getMsg();
                        syncTaskService.updateTaskToFailed(task.getTaskId(), errorMsg);
                        log.error("任务执行失败: taskId={}, requestCode={}, msg={}", 
                                task.getTaskId(), task.getCrmRequestCode(), statusResponse.getMsg());
                    }
                    // 如果是待执行状态，继续等待
                    
                } catch (Exception e) {
                    log.error("查询CRM执行结果异常: taskId={}, requestCode={}", 
                            task.getTaskId(), task.getCrmRequestCode(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("查询CRM执行结果定时任务异常", e);
        }
    }
    
    /**
     * 重试失败任务
     * 每30秒执行一次
     */
    @Scheduled(fixedDelay = 30000)
    public void retryFailedTasks() {
        try {
            int retryIntervalMinutes = retryInterval / 1000 / 60; // 转换为分钟
            List<SyncTask> retryableTasks = syncTaskService.getRetryableTasks(maxRetry, retryIntervalMinutes);
            
            if (retryableTasks.isEmpty()) {
                return;
            }
            
            log.info("开始重试失败任务，任务数量: {}", retryableTasks.size());
            
            RQueue<String> queue = redissonClient.getQueue(SYNC_QUEUE);
            
            for (SyncTask task : retryableTasks) {
                try {
                    // 重置任务状态为待处理（而不是处理中）
                    syncTaskService.updateTaskToPending(task.getTaskId());
                    
                    // 重新加入队列
                    queue.offer(task.getTaskId());
                    
                    log.info("任务已重新加入队列: taskId={}, retryCount={}", 
                            task.getTaskId(), task.getRetryCount());
                    
                } catch (Exception e) {
                    log.error("重试任务异常: taskId={}", task.getTaskId(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("重试失败任务定时任务异常", e);
        }
    }
    
    /**
     * 统计任务状态
     * 每5分钟执行一次
     */
    @Scheduled(fixedDelay = 300000)
    public void statisticsTasks() {
        try {
            long pendingCount = syncTaskService.getTaskCountByStatus(SyncTask.Status.PENDING);
            long processingCount = syncTaskService.getTaskCountByStatus(SyncTask.Status.PROCESSING);
            long successCount = syncTaskService.getTaskCountByStatus(SyncTask.Status.SUCCESS);
            long failedCount = syncTaskService.getTaskCountByStatus(SyncTask.Status.FAILED);
            
            log.info("任务统计 - 待处理: {}, 处理中: {}, 成功: {}, 失败: {}", 
                    pendingCount, processingCount, successCount, failedCount);
            
        } catch (Exception e) {
            log.error("统计任务状态异常", e);
        }
    }
}