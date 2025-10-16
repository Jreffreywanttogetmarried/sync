package com.easydeals.sync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * CRM接口调用频率限制服务
 * 基于Redis实现滑动窗口频率控制，确保每分钟调用次数不超过40次
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {
    
    private final RedissonClient redissonClient;
    
    // CRM接口调用频率限制：每分钟40次
    private static final int MAX_CALLS_PER_MINUTE = 40;
    // 为延迟队列预留的调用额度（总额度的25%）
    private static final int RESERVED_CALLS_FOR_DELAYED_QUEUE = 10;
    // 正常队列的最大调用次数（总额度的75%）
    private static final int MAX_CALLS_FOR_NORMAL_QUEUE = MAX_CALLS_PER_MINUTE - RESERVED_CALLS_FOR_DELAYED_QUEUE;
    private static final String RATE_LIMIT_KEY_PREFIX = "crm:rate_limit:";
    
    /**
     * 尝试获取调用许可
     * 
     * @param apiType API类型
     * @return 是否成功获取许可
     */
    public boolean tryAcquire(String apiType) {
        return tryAcquire(apiType, false);
    }
    
    /**
     * 尝试获取调用许可（支持延迟队列优先级）
     * 
     * @param apiType API类型
     * @param isDelayedQueue 是否为延迟队列调用
     * @return 是否成功获取许可
     */
    public boolean tryAcquire(String apiType, boolean isDelayedQueue) {
        String currentMinute = getCurrentMinuteKey();
        String rateLimitKey = RATE_LIMIT_KEY_PREFIX + apiType + ":" + currentMinute;
        
        RAtomicLong counter = redissonClient.getAtomicLong(rateLimitKey);
        
        // 设置过期时间为2分钟，确保key会被自动清理
        counter.expire(Duration.ofMinutes(2));
        
        long currentCount = counter.get();
        
        // 根据调用类型选择不同的限制策略
        if (isDelayedQueue) {
            // 延迟队列：只要总调用次数未达到上限就允许
            if (currentCount < MAX_CALLS_PER_MINUTE) {
                counter.incrementAndGet();
                log.debug("延迟队列获取调用许可成功，当前调用次数: {}/{}", currentCount + 1, MAX_CALLS_PER_MINUTE);
                return true;
            }
        } else {
            // 正常队列：限制在预留额度内
            if (currentCount < MAX_CALLS_FOR_NORMAL_QUEUE) {
                counter.incrementAndGet();
                log.debug("正常队列获取调用许可成功，当前调用次数: {}/{}", currentCount + 1, MAX_CALLS_FOR_NORMAL_QUEUE);
                return true;
            }
        }
        
        log.debug("获取调用许可失败，当前调用次数: {}, 队列类型: {}", currentCount, isDelayedQueue ? "延迟队列" : "正常队列");
        return false;
    }
    
    /**
     * 等待可用的调用时机（支持延迟队列优先级）
     * 
     * @param apiType API类型
     * @param isDelayedQueue 是否为延迟队列调用
     * @throws InterruptedException 等待被中断
     */
    public void waitForAvailableSlot(String apiType, boolean isDelayedQueue) throws InterruptedException {
        int maxWaitTime = 65; // 最大等待65秒（稍微超过一分钟）
        int waitTime = 0;
        
        while (waitTime < maxWaitTime) {
            if (tryAcquire(apiType, isDelayedQueue)) {
                log.debug("等待{}秒后获取到调用许可: apiType={}, 队列类型={}", 
                        waitTime, apiType, isDelayedQueue ? "延迟队列" : "正常队列");
                return;
            }
            
            // 延迟队列使用更短的等待间隔
            int sleepInterval = isDelayedQueue ? 2 : 5;
            Thread.sleep(sleepInterval * 1000);
            waitTime += sleepInterval;
            
            log.debug("等待调用许可: apiType={}, 已等待{}秒, 队列类型={}", apiType, waitTime, isDelayedQueue ? "延迟队列" : "正常队列");
        }
        
        log.warn("等待调用许可超时: apiType={}, 等待时间={}秒, 队列类型={}", apiType, waitTime, isDelayedQueue ? "延迟队列" : "正常队列");
        throw new RuntimeException("CRM接口调用频率限制等待超时");
    }
    
    /**
     * 等待可用的调用时机（兼容原有接口）
     * 
     * @param apiType API类型
     * @throws InterruptedException 等待被中断
     */
    public void waitForAvailableSlot(String apiType) throws InterruptedException {
        waitForAvailableSlot(apiType, false);
    }
    
    /**
     * 获取当前分钟的Redis key
     * 格式：yyyy-MM-dd:HH:mm
     */
    private String getCurrentMinuteKey() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd:HH:mm"));
    }
    
    /**
     * 获取指定API类型的当前调用次数
     * 
     * @param apiType API类型
     * @return 当前分钟内的调用次数
     */
    public long getCurrentCallCount(String apiType) {
        String currentMinute = getCurrentMinuteKey();
        String rateLimitKey = RATE_LIMIT_KEY_PREFIX + apiType + ":" + currentMinute;
        
        RAtomicLong counter = redissonClient.getAtomicLong(rateLimitKey);
        return counter.get();
    }
    
    /**
     * 获取剩余可用调用次数
     * 
     * @param apiType API类型
     * @param isDelayedQueue 是否为延迟队列调用
     * @return 剩余可用调用次数
     */
    public long getRemainingCalls(String apiType, boolean isDelayedQueue) {
        long currentCount = getCurrentCallCount(apiType);
        if (isDelayedQueue) {
            // 延迟队列：基于总额度计算
            return Math.max(0, MAX_CALLS_PER_MINUTE - currentCount);
        } else {
            // 正常队列：基于预留额度计算
            return Math.max(0, MAX_CALLS_FOR_NORMAL_QUEUE - currentCount);
        }
    }
    
    /**
     * 获取剩余可用调用次数（兼容原有接口）
     * 
     * @param apiType API类型
     * @return 剩余可用调用次数
     */
    public long getRemainingCalls(String apiType) {
        return getRemainingCalls(apiType, false);
    }
}