package com.easydeals.sync.scheduler;

import com.easydeals.sync.service.DelayedRetryProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 延迟重试调度器
 * 定期从延迟重试队列中获取任务并处理
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DelayedRetryScheduler {
    
    private final RedissonClient redissonClient;
    private final DelayedRetryProcessor delayedRetryProcessor; // 使用新的独立处理器
    
    private static final String DELAYED_RETRY_QUEUE = "sync:delayed_retry_queue";
    
    /**
     * 定期处理延迟重试队列中的任务
     * 每60秒执行一次
     */
    @Scheduled(fixedDelay = 60000)
    public void processDelayedRetryTasks() {
        try {
            RQueue<String> delayedRetryQueue = redissonClient.getQueue(DELAYED_RETRY_QUEUE);
            
            // 批量处理延迟重试任务，每次最多处理5个
            int batchSize = 5;
            int processedCount = 0;
            
            for (int i = 0; i < batchSize; i++) {
                String taskId = delayedRetryQueue.poll();
                if (taskId == null) {
                    break; // 队列为空，退出循环
                }
                
                try {
                    log.info("从延迟重试队列获取任务: taskId={}", taskId);
                    
                    // 使用独立的延迟重试处理器处理任务
                    delayedRetryProcessor.processDelayedRetryTask(taskId);
                    processedCount++;
                    
                } catch (Exception e) {
                    log.error("处理延迟重试任务异常: taskId={}", taskId, e);
                    // 继续处理下一个任务，不中断整个批次
                }
            }
            
            if (processedCount > 0) {
                log.info("本次延迟重试任务处理完成: processedCount={}", processedCount);
            }
            
        } catch (Exception e) {
            log.error("延迟重试调度器执行异常", e);
        }
    }
    
    /**
     * 获取延迟重试队列大小
     */
    public int getDelayedRetryQueueSize() {
        try {
            RQueue<String> delayedRetryQueue = redissonClient.getQueue(DELAYED_RETRY_QUEUE);
            return delayedRetryQueue.size();
        } catch (Exception e) {
            log.error("获取延迟重试队列大小异常", e);
            return 0;
        }
    }
}