package com.easydeals.sync.consumer;

import com.easydeals.sync.service.SyncProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 同步任务消费者
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SyncTaskConsumer {
    
    private final RedissonClient redissonClient;
    private final SyncProcessorService syncProcessorService;
    
    private static final String SYNC_QUEUE = "sync:queue";
    private volatile boolean running = true;
    
    /**
     * 启动消费者
     */
    @Bean
    public ApplicationRunner startConsumer() {
        return args -> {
            log.info("启动同步任务消费者");
            startConsuming();
        };
    }
    
    /**
     * 开始消费消息
     */
    @Async
    public void startConsuming() {
        RBlockingQueue<String> queue = redissonClient.getBlockingQueue(SYNC_QUEUE);
        
        while (running) {
            try {
                // 阻塞等待任务，超时时间5秒
                String taskId = queue.poll(5, TimeUnit.SECONDS);
                
                if (taskId != null) {
                    log.info("收到同步任务: taskId={}", taskId);
                    processTaskAsync(taskId);
                }
                
            } catch (InterruptedException e) {
                log.warn("消费者被中断");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("消费消息异常", e);
                // 短暂休眠后继续
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        log.info("同步任务消费者已停止");
    }
    
    /**
     * 异步处理任务
     */
    @Async
    public void processTaskAsync(String taskId) {
        try {
            syncProcessorService.processTask(taskId);
        } catch (Exception e) {
            log.error("异步处理任务失败: taskId={}", taskId, e);
        }
    }
    
    /**
     * 停止消费者
     */
    public void stop() {
        log.info("停止同步任务消费者");
        running = false;
    }
}