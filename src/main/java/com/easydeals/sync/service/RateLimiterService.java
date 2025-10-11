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
    private static final String RATE_LIMIT_KEY_PREFIX = "crm:rate_limit:";
    
    /**
     * 检查是否可以进行CRM接口调用
     * 如果超过限制，会自动等待直到可以调用
     * 
     * @param apiType API类型（customer或order）
     * @return true表示可以调用，false表示需要等待
     */
    public boolean tryAcquire(String apiType) {
        String currentMinute = getCurrentMinuteKey();
        String rateLimitKey = RATE_LIMIT_KEY_PREFIX + apiType + ":" + currentMinute;
        
        RAtomicLong counter = redissonClient.getAtomicLong(rateLimitKey);
        
        // 原子性增加计数
        long newCount = counter.incrementAndGet();
        
        // 如果是第一次创建这个key（计数为1），设置过期时间
        if (newCount == 1) {
            counter.expire(Duration.ofSeconds(120));
            log.debug("设置新的限流key过期时间: key={}, ttl=120秒", rateLimitKey);
        }
        
        if (newCount > MAX_CALLS_PER_MINUTE) {
            // 如果增加后超过限制，需要减回去
            counter.decrementAndGet();
            log.warn("CRM接口调用频率达到限制: apiType={}, attemptedCount={}, limit={}", 
                    apiType, newCount, MAX_CALLS_PER_MINUTE);
            return false;
        }
        
        log.debug("CRM接口调用计数: apiType={}, currentCount={}, limit={}", 
                apiType, newCount, MAX_CALLS_PER_MINUTE);
        
        return true;
    }
    
    /**
     * 等待直到可以进行API调用
     * 使用智能等待策略，避免长时间阻塞
     * 
     * @param apiType API类型
     * @throws InterruptedException 如果等待被中断
     */
    public void waitForAvailableSlot(String apiType) throws InterruptedException {
        int maxWaitSeconds = 70; // 最多等待70秒（超过一分钟窗口）
        int waitInterval = 1000; // 每次等待1秒
        int totalWaitTime = 0;
        
        while (!tryAcquire(apiType) && totalWaitTime < maxWaitSeconds * 1000) {
            log.info("CRM接口调用频率限制，等待中: apiType={}, waitTime={}ms", apiType, totalWaitTime);
            
            Thread.sleep(waitInterval);
            totalWaitTime += waitInterval;
            
            // 动态调整等待间隔，接近分钟边界时等待时间更短
            int secondsInMinute = LocalDateTime.now().getSecond();
            if (secondsInMinute > 50) {
                // 接近分钟结束，缩短等待间隔
                waitInterval = 500;
            } else {
                waitInterval = 1000;
            }
        }
        
        if (totalWaitTime >= maxWaitSeconds * 1000) {
            log.error("CRM接口调用频率限制等待超时: apiType={}, maxWaitTime={}s", apiType, maxWaitSeconds);
            throw new RuntimeException("CRM接口调用频率限制等待超时");
        }
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
     * @return 剩余可用调用次数
     */
    public long getRemainingCalls(String apiType) {
        long currentCount = getCurrentCallCount(apiType);
        return Math.max(0, MAX_CALLS_PER_MINUTE - currentCount);
    }
}