package com.easydeals.sync.service;

import com.easydeals.sync.entity.SyncTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 回调服务
 * 用于处理特定网站（如aom）的回调通知
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallbackService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${callback.aom.url:}")
    private String aomCallbackUrl;

    @Value("${callback.timeout:10}")
    private int callbackTimeout;

    /**
     * 为aom网站发送回调通知（同步方法，返回成功状态）
     *
     * @param task 同步任务
     * @param success 是否成功
     * @param errorMessage 错误信息（如果失败）
     * @return 回调是否成功
     */
    public boolean sendAomCallback(SyncTask task, boolean success, String errorMessage) {
        if (aomCallbackUrl == null || aomCallbackUrl.trim().isEmpty()) {
            log.warn("aom回调URL未配置，跳过回调: taskId={}", task.getTaskId());
            return true; // URL未配置视为成功，避免重复尝试
        }

        try {
            log.info("开始发送aom回调: taskId={}, success={}", task.getTaskId(), success);

            // 构建回调数据
            Map<String, Object> callbackData = buildCallbackData(task, success, errorMessage);
            log.info("aom callbackData built: {}", callbackData);

            // 发送回调请求
            String response = webClient.post()
                    .uri(aomCallbackUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(callbackData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(callbackTimeout))
                    .block();

            log.info("aom回调发送成功: taskId={}, response={}", task.getTaskId(), response);
            return true;

        } catch (WebClientResponseException e) {
            log.error("aom回调HTTP错误: taskId={}, status={}, body={}",
                    task.getTaskId(), e.getStatusCode(), e.getResponseBodyAsString(), e);
            return false;
        } catch (Exception e) {
            log.error("aom回调发送失败: taskId={}", task.getTaskId(), e);
            return false;
        }
    }

    /**
     * 为aom网站发送回调通知（带重试机制）
     *
     * @param task 同步任务
     * @param success 是否成功
     * @param errorMessage 错误信息（如果失败）
     * @param maxRetries 最大重试次数
     * @return 回调是否成功
     */
    public boolean sendAomCallbackWithRetry(SyncTask task, boolean success, String errorMessage, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                boolean result = sendAomCallback(task, success, errorMessage);
                if (result) {
                    if (attempt > 1) {
                        log.info("aom回调重试成功: taskId={}, attempt={}", task.getTaskId(), attempt);
                    }
                    return true;
                }

                if (attempt < maxRetries) {
                    log.warn("aom回调失败，准备重试: taskId={}, attempt={}/{}",
                            task.getTaskId(), attempt, maxRetries);
                    Thread.sleep(1000 * attempt); // 递增延迟：1s, 2s, 3s...
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("aom回调重试被中断: taskId={}", task.getTaskId(), e);
                return false;
            } catch (Exception e) {
                log.error("aom回调重试异常: taskId={}, attempt={}", task.getTaskId(), attempt, e);
                if (attempt >= maxRetries) {
                    return false;
                }
            }
        }

        log.error("aom回调重试全部失败: taskId={}, maxRetries={}", task.getTaskId(), maxRetries);
        return false;
    }

    /**
     * 异步发送aom回调（不阻塞主流程）
     */
    public void sendAomCallbackAsync(SyncTask task, boolean success, String errorMessage) {
        if (aomCallbackUrl == null || aomCallbackUrl.trim().isEmpty()) {
            log.warn("aom回调URL未配置，跳过回调: taskId={}", task.getTaskId());
            return;
        }

        // 异步执行回调，不影响主流程
        Mono.fromCallable(() -> sendAomCallback(task, success, errorMessage))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .doOnError(throwable -> log.error("异步aom回调执行失败: taskId={}", task.getTaskId(), throwable))
                .subscribe();
    }

    /**
     * 构建回调数据
     */
    private Map<String, Object> buildCallbackData(SyncTask task, boolean success, String errorMessage) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getTaskId());
        data.put("batchId", task.getBatchId());
        data.put("businessId", task.getBusinessId());
        data.put("dataType", task.getDataType());
        data.put("success", success);
        data.put("status", success ? SyncTask.Status.SUCCESS.getCode() : SyncTask.Status.FAILED.getCode());
        data.put("timestamp", System.currentTimeMillis());

        if (!success && errorMessage != null) {
            data.put("errorMessage", errorMessage);
        }

        if (task.getCrmRequestCode() != null) {
            data.put("crmRequestCode", task.getCrmRequestCode());
        }

        return data;
    }

    /**
     * 检查是否需要发送回调
     *
     * @param websiteCode 网站代码
     * @return 是否需要回调
     */
    public boolean shouldSendCallback(String websiteCode) {
        return "aom".equalsIgnoreCase(websiteCode);
    }
}