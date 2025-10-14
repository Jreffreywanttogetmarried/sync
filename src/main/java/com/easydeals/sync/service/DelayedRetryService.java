package com.easydeals.sync.service;

import com.easydeals.sync.entity.SyncTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 延迟重试服务
 * 专门处理CRM接口调用频率过高的请求
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DelayedRetryService {
    
    private final RedissonClient redissonClient;
    private final SyncTaskService syncTaskService;
    
    // 延迟重试队列名称
    private static final String DELAYED_RETRY_QUEUE = "sync:delayed_retry_queue";
    private static final String DELAYED_RETRY_QUEUE_DELAYED = "sync:delayed_retry_queue_delayed";
    
    /**
     * 将频率过高的任务加入延迟重试队列
     * 
     * @param taskId 任务ID
     * @param delayMinutes 延迟分钟数
     */
    public void addToDelayedRetryQueue(String taskId, int delayMinutes) {
        try {
            // 获取延迟队列
            RQueue<String> queue = redissonClient.getQueue(DELAYED_RETRY_QUEUE);
            RDelayedQueue<String> delayedQueue = redissonClient.getDelayedQueue(queue);
            
            // 将任务加入延迟队列
            delayedQueue.offer(taskId, delayMinutes, TimeUnit.MINUTES);
            
            // 更新任务状态为延迟重试
            syncTaskService.updateTaskToDelayedRetry(taskId,"进入延迟队列重试");
            
            log.info("任务已加入延迟重试队列: taskId={}, delayMinutes={}", taskId, delayMinutes);
            
        } catch (Exception e) {
            log.error("加入延迟重试队列失败: taskId={}", taskId, e);
        }
    }
    
    /**
     * 从延迟重试队列获取待处理的任务
     * 
     * @return 任务ID，如果没有则返回null
     */
    public String pollFromDelayedRetryQueue() {
        try {
            RQueue<String> queue = redissonClient.getQueue(DELAYED_RETRY_QUEUE);
            return queue.poll();
        } catch (Exception e) {
            log.error("从延迟重试队列获取任务失败", e);
            return null;
        }
    }
    
    /**
     * 获取延迟重试队列的大小
     */
    public int getDelayedRetryQueueSize() {
        try {
            RQueue<String> queue = redissonClient.getQueue(DELAYED_RETRY_QUEUE);
            return queue.size();
        } catch (Exception e) {
            log.error("获取延迟重试队列大小失败", e);
            return 0;
        }
    }
    
    /**
     * 判断是否为频率限制错误
     * 
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     * @return true表示是频率限制错误
     */
    public boolean isRateLimitError(Integer errorCode, String errorMessage) {
        if (errorCode != null && errorCode == 400) {
            return errorMessage != null && 
                   (errorMessage.contains("频率过高") || 
                    errorMessage.contains("稍后重试") ||
                    errorMessage.contains("rate limit") ||
                    errorMessage.contains("too many requests"));
        }
        return false;
    }
    
    /**
     * 计算延迟重试时间
     * 根据重试次数递增延迟时间
     * 
     * @param retryCount 重试次数
     * @return 延迟分钟数
     */
    public int calculateDelayMinutes(int retryCount) {
        // 基础延迟2分钟，每次重试增加1分钟，最大不超过10分钟
        return Math.min(2 + retryCount, 10);
    }
}