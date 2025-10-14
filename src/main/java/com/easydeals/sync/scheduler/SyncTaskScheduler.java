package com.easydeals.sync.scheduler;

import com.easydeals.sync.dto.CrmStatusResponse;
import com.easydeals.sync.entity.SyncTask;
import com.easydeals.sync.enums.CallbackStatusEnum;
import com.easydeals.sync.service.CallbackService;
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

import java.time.LocalDateTime;
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
    private final CallbackService callbackService;
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

    /**
     * 处理回调任务
     * 每15秒执行一次，扫描需要回调的任务并发送回调
     */
    @Scheduled(fixedDelay = 15000)
    public void processCallbacks() {
        try {
            // 查询需要回调的aom网站任务（成功或失败且未回调的任务）
            List<SyncTask> callbackTasks = syncTaskService.findTasksNeedCallback("aom", 10);

            if (callbackTasks.isEmpty()) {
                return;
            }

            log.info("开始处理回调任务，数量: {}", callbackTasks.size());

            for (SyncTask task : callbackTasks) {
                try {
                    boolean isSuccess = task.getStatus() == SyncTask.Status.SUCCESS.getCode();
                    String errorMessage = isSuccess ? null : task.getErrorMsg();

                    // 发送回调（带重试机制，最多重试3次）
                    boolean callbackSuccess = callbackService.sendAomCallbackWithRetry(task, isSuccess, errorMessage, 3);

                    if (callbackSuccess) {
                        // 回调成功，更新回调状态为已回调
                        syncTaskService.updateCallbackStatus(task.getTaskId(),
                                CallbackStatusEnum.CALLED.getCode(), LocalDateTime.now());
                        log.info("回调任务处理成功: taskId={}, success={}", task.getTaskId(), isSuccess);
                    } else {
                        // 回调失败，更新回调状态为失败，保持原状态不变
                        syncTaskService.updateCallbackStatus(task.getTaskId(),
                                CallbackStatusEnum.FAILED.getCode(), LocalDateTime.now());
                        log.warn("回调任务失败: taskId={}, success={}, 状态保持不变", task.getTaskId(), isSuccess);
                    }

                } catch (Exception e) {
                    log.error("回调任务处理异常: taskId={}", task.getTaskId(), e);

                    // 更新回调状态为失败
                    try {
                        syncTaskService.updateCallbackStatus(task.getTaskId(),
                                CallbackStatusEnum.FAILED.getCode(), LocalDateTime.now());
                    } catch (Exception updateEx) {
                        log.error("更新回调状态失败: taskId={}", task.getTaskId(), updateEx);
                    }
                }
            }

        } catch (Exception e) {
            log.error("处理回调任务异常", e);
        }
    }
}