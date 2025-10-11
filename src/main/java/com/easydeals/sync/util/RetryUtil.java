package com.easydeals.sync.util;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * 重试工具类
 */
@Slf4j
public class RetryUtil {
    
    /**
     * 执行重试操作
     * 
     * @param operation 要执行的操作
     * @param maxAttempts 最大重试次数
     * @param delayMs 重试间隔（毫秒）
     * @param operationName 操作名称（用于日志）
     * @return 操作结果
     */
    public static <T> T executeWithRetry(Supplier<T> operation, int maxAttempts, long delayMs, String operationName) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                log.warn("{}执行失败，第{}次尝试，错误: {}", operationName, attempt, e.getMessage());
                
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                }
            }
        }
        
        log.error("{}执行失败，已达到最大重试次数: {}", operationName, maxAttempts);
        throw new RuntimeException(operationName + "执行失败", lastException);
    }
    
    /**
     * 执行重试操作（无返回值）
     */
    public static void executeWithRetry(Runnable operation, int maxAttempts, long delayMs, String operationName) {
        executeWithRetry(() -> {
            operation.run();
            return null;
        }, maxAttempts, delayMs, operationName);
    }
}