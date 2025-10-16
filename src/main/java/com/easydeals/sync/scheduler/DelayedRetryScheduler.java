package com.easydeals.sync.scheduler;

import java.util.Random;
import com.easydeals.sync.service.DelayedRetryProcessor;
import com.easydeals.sync.service.RateLimiterService;
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
    private final RateLimiterService rateLimiterService; // 添加频率限制服务
    
    private static final String DELAYED_RETRY_QUEUE = "sync:delayed_retry_queue";
    
    /**
     * 定期处理延迟重试队列中的任务
     * 每30秒执行一次，添加智能等待机制避免与正常队列频率冲突
     */
    @Scheduled(fixedDelay = 30000)
    public void processDelayedRetryTasks() {
        try {
            RQueue<String> delayedRetryQueue = redissonClient.getQueue(DELAYED_RETRY_QUEUE);
            
            // 批量处理延迟重试任务，每次最多处理5个
            int batchSize = 5;
            int processedCount = 0;
            
            // 智能等待机制：在开始处理延迟队列前，等待一段时间让频率窗口重置
            // 这样可以避免与正常队列处理产生频率冲突
            int initialWaitSeconds = calculateInitialWaitTime();
            if (initialWaitSeconds > 0) {
                log.info("延迟队列处理前智能等待: waitSeconds={}", initialWaitSeconds);
                Thread.sleep(initialWaitSeconds * 1000);
            }
            
            for (int i = 0; i < batchSize; i++) {
                String taskId = delayedRetryQueue.poll();
                if (taskId == null) {
                    break; // 队列为空，退出循环
                }
                
                try {
                    log.info("从延迟重试队列获取任务: taskId={}", taskId);
                    
                    // 在处理每个任务前，检查频率限制情况
                    // 如果当前频率使用率过高，额外等待一段时间
                    waitForOptimalProcessingTime();
                    
                    // 使用独立的延迟重试处理器处理任务
                    delayedRetryProcessor.processDelayedRetryTask(taskId);
                    processedCount++;

                    // 任务间等待时间：基础2秒 + 动态调整
                    int taskInterval = calculateTaskInterval(i, batchSize);
                    Thread.sleep(taskInterval);
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
     * 计算初始等待时间（智能等待策略）
     */
    private int calculateInitialWaitTime() {
        try {
            // 获取延迟队列的剩余调用次数
            long remainingCalls = rateLimiterService.getRemainingCalls("customer", true);
            
            if (remainingCalls < 10) {
                // 剩余调用次数很少，等待15-25秒让频率窗口重置
                return 15 + new Random().nextInt(11);
            } else if (remainingCalls < 20) {
                // 剩余调用次数较少，等待5-10秒
                return 5 + new Random().nextInt(6);
            }
            
            // 剩余调用次数充足，无需等待
            return 0;
        } catch (Exception e) {
            log.warn("计算初始等待时间异常，使用默认等待时间", e);
            return 5; // 默认等待5秒
        }
    }
    
    /**
     * 等待最佳处理时机
     */
    private void waitForOptimalProcessingTime() {
        try {
            // 获取延迟队列的剩余调用次数
            long remainingCalls = rateLimiterService.getRemainingCalls("customer", true);
            
            if (remainingCalls < 5) {
                // 剩余调用次数很少，随机等待3-5秒
                int waitTime = 3 + new Random().nextInt(3);
                log.info("延迟队列剩余调用次数不足({}次)，等待{}秒", remainingCalls, waitTime);
                Thread.sleep(waitTime * 1000);
            }
        } catch (Exception e) {
            log.warn("等待最佳处理时机异常", e);
        }
    }
    
    /**
     * 计算任务间隔时间
     * 根据批次进度动态调整间隔
     */
    private int calculateTaskInterval(int currentIndex, int batchSize) {
        // 基础间隔2秒
        int baseInterval = 2000;
        
        // 如果是批次中的最后几个任务，适当增加间隔避免频率冲突
        if (currentIndex >= batchSize - 2) {
            baseInterval += 1000; // 最后两个任务增加1秒间隔
        }
        
        // 添加随机因子，避免多实例同时处理时的冲突
        int randomFactor = (int)(Math.random() * 500); // 0-500ms随机
        
        return baseInterval + randomFactor;
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