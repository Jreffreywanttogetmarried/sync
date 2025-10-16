package com.easydeals.sync.service;

import com.easydeals.sync.dto.khwy.CustomerCrmRequest;
import com.easydeals.sync.dto.khwy.OrderCrmRequest;
import com.easydeals.sync.dto.CrmResponse;
import com.easydeals.sync.dto.CrmStatusResponse;
import com.easydeals.sync.enums.CrmApiEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * CRM API服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrmApiService {
    
    private final WebClient webClient;
    private final RateLimiterService rateLimiterService;
    private final DelayedRetryService delayedRetryService;
    
    /**
     * 提交客户数据到CRM
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000),
               exclude = {WebClientResponseException.BadRequest.class})
    public CrmResponse submitCustomer(CustomerCrmRequest request) {
        try {
            // 频率控制：等待可用调用槽位
            rateLimiterService.waitForAvailableSlot("customer");
            
            log.info("开始调用CRM API提交客户数据: cusName={}, remainingCalls={}", 
                    request.getCusName(), rateLimiterService.getRemainingCalls("customer"));
            
            CrmResponse response = webClient
                    .post()
                    .uri(CrmApiEnum.ADD_CUSTOMER.getUrl())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(CrmResponse.class)
                    .block();
            
            if (response != null && response.isSuccess()) {
                log.info("CRM API调用成功: cusName={}, requestCode={}", 
                        request.getCusName(), response.getRequestCode());
            } else {
                log.warn("CRM API调用失败: cusName={}, code={}, msg={}", 
                        request.getCusName(), 
                        response != null ? response.getCode() : "null", 
                        response != null ? response.getMsg() : "null");
            }
            
            return response;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("CRM API频率控制等待被中断: cusName={}", request.getCusName(), e);
            return CrmResponse.builder()
                    .code(429)
                    .msg("频率控制等待被中断: " + e.getMessage())
                    .build();
        } catch (WebClientResponseException e) {
            log.error("CRM API HTTP错误: cusName={}, status={}, body={}", 
                    request.getCusName(), e.getStatusCode(), e.getResponseBodyAsString(), e);
            
            // 检查是否为频率限制错误
            CrmResponse errorResponse = CrmResponse.builder()
                    .code(e.getStatusCode().value())
                    .msg("HTTP错误: " + e.getMessage())
                    .build();
            
            // 如果是400错误且包含频率限制信息，标记为频率限制错误
            if (e.getStatusCode().value() == 400 && 
                (e.getResponseBodyAsString().contains("频率过高") || 
                 e.getResponseBodyAsString().contains("稍后重试"))) {
                errorResponse.setMsg("接口调用频率过高，请稍后重试");
            }
            
            return errorResponse;
        } catch (Exception e) {
            log.error("CRM API调用异常: cusName={}", request.getCusName(), e);
            return CrmResponse.builder()
                    .code(500)
                    .msg("系统异常: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 提交订单数据到CRM
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000),
               exclude = {WebClientResponseException.BadRequest.class})
    public CrmResponse submitOrder(OrderCrmRequest request) {
        try {
            // 频率控制：等待可用调用槽位
            rateLimiterService.waitForAvailableSlot("order");
            
            log.info("提交订单数据到CRM: title={}, cusName={}, dealAmount={}, remainingCalls={}", 
                    request.getTitle(), request.getCusName(), request.getDealAmount(),
                    rateLimiterService.getRemainingCalls("order"));
            log.debug("订单数据详情: {}", request);
            
            CrmResponse response = webClient
                    .post()
                    .uri(CrmApiEnum.ADD_ORDER.getUrl())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(CrmResponse.class)
                    .block();
            
            if (response != null) {
                log.info("订单数据提交完成: title={}, code={}, msg={}, requestCode={}", 
                        request.getTitle(), response.getCode(), response.getMsg(), response.getRequestCode());
            }
            
            return response;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("CRM API频率控制等待被中断: title={}", request.getTitle(), e);
            return CrmResponse.builder()
                    .code(429)
                    .msg("频率控制等待被中断: " + e.getMessage())
                    .build();
        } catch (WebClientResponseException e) {
            log.error("订单数据提交HTTP错误: title={}, status={}, body={}", 
                    request.getTitle(), e.getStatusCode(), e.getResponseBodyAsString(), e);
            
            // 检查是否为频率限制错误
            CrmResponse errorResponse = CrmResponse.builder()
                    .code(e.getStatusCode().value())
                    .msg("HTTP错误: " + e.getMessage())
                    .build();
            
            // 如果是400错误且包含频率限制信息，标记为频率限制错误
            if (e.getStatusCode().value() == 400 && 
                (e.getResponseBodyAsString().contains("频率过高") || 
                 e.getResponseBodyAsString().contains("稍后重试"))) {
                errorResponse.setMsg("接口调用频率过高，请稍后重试");
            }
            
            return errorResponse;
        } catch (Exception e) {
            log.error("订单数据提交异常: title={}", request.getTitle(), e);
            return CrmResponse.builder()
                    .code(500)
                    .msg("系统异常: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 提交客户数据到CRM（延迟队列专用）
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000),
               exclude = {WebClientResponseException.BadRequest.class})
    public CrmResponse submitCustomerFromDelayedQueue(CustomerCrmRequest request) {
        try {
            // 频率控制：等待可用调用槽位（延迟队列优先级）
            rateLimiterService.waitForAvailableSlot("customer", true);
            
            log.info("延迟队列调用CRM API提交客户数据: cusName={}, remainingCalls={}", 
                    request.getCusName(), rateLimiterService.getRemainingCalls("customer", true));
            
            CrmResponse response = webClient
                    .post()
                    .uri(CrmApiEnum.ADD_CUSTOMER.getUrl())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(CrmResponse.class)
                    .block();
            
            if (response != null && response.isSuccess()) {
                log.info("延迟队列CRM API调用成功: cusName={}, requestCode={}", 
                        request.getCusName(), response.getRequestCode());
            } else {
                log.warn("延迟队列CRM API调用失败: cusName={}, code={}, msg={}", 
                        request.getCusName(), 
                        response != null ? response.getCode() : "null", 
                        response != null ? response.getMsg() : "null");
            }
            
            return response;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("延迟队列CRM API频率控制等待被中断: cusName={}", request.getCusName(), e);
            return CrmResponse.builder()
                    .code(429)
                    .msg("频率控制等待被中断: " + e.getMessage())
                    .build();
        } catch (WebClientResponseException e) {
            log.error("延迟队列CRM API HTTP错误: cusName={}, status={}, body={}", 
                    request.getCusName(), e.getStatusCode(), e.getResponseBodyAsString(), e);
            
            // 检查是否为频率限制错误
            CrmResponse errorResponse = CrmResponse.builder()
                    .code(e.getStatusCode().value())
                    .msg("HTTP错误: " + e.getMessage())
                    .build();
            
            // 如果是400错误且包含频率限制信息，标记为频率限制错误
            if (e.getStatusCode().value() == 400 && 
                (e.getResponseBodyAsString().contains("频率过高") || 
                 e.getResponseBodyAsString().contains("稍后重试"))) {
                errorResponse.setMsg("接口调用频率过高，请稍后重试");
            }
            
            return errorResponse;
        } catch (Exception e) {
            log.error("延迟队列CRM API调用异常: cusName={}", request.getCusName(), e);
            return CrmResponse.builder()
                    .code(500)
                    .msg("系统异常: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 提交订单数据到CRM（延迟队列专用）
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000),
               exclude = {WebClientResponseException.BadRequest.class})
    public CrmResponse submitOrderFromDelayedQueue(OrderCrmRequest request) {
        try {
            // 频率控制：等待可用调用槽位（延迟队列优先级）
            rateLimiterService.waitForAvailableSlot("order", true);
            
            log.info("延迟队列提交订单数据到CRM: title={}, cusName={}, dealAmount={}, remainingCalls={}", 
                    request.getTitle(), request.getCusName(), request.getDealAmount(),
                    rateLimiterService.getRemainingCalls("order", true));
            log.debug("延迟队列订单数据详情: {}", request);
            
            CrmResponse response = webClient
                    .post()
                    .uri(CrmApiEnum.ADD_ORDER.getUrl())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(CrmResponse.class)
                    .block();
            
            if (response != null) {
                log.info("延迟队列订单数据提交完成: title={}, code={}, msg={}, requestCode={}", request.getTitle(), response.getCode(), response.getMsg(), response.getRequestCode());
            }
            
            return response;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("延迟队列CRM API频率控制等待被中断: title={}", request.getTitle(), e);
            return CrmResponse.builder()
                    .code(429)
                    .msg("频率控制等待被中断: " + e.getMessage())
                    .build();
        } catch (WebClientResponseException e) {
            log.error("延迟队列订单数据提交HTTP错误: title={}, status={}, body={}", 
                    request.getTitle(), e.getStatusCode(), e.getResponseBodyAsString(), e);
            
            // 检查是否为频率限制错误
            CrmResponse errorResponse = CrmResponse.builder()
                    .code(e.getStatusCode().value())
                    .msg("HTTP错误: " + e.getMessage())
                    .build();
            
            // 如果是400错误且包含频率限制信息，标记为频率限制错误
            if (e.getStatusCode().value() == 400 && 
                (e.getResponseBodyAsString().contains("频率过高") || 
                 e.getResponseBodyAsString().contains("稍后重试"))) {
                errorResponse.setMsg("接口调用频率过高，请稍后重试");
            }
            
            return errorResponse;
        } catch (Exception e) {
            log.error("延迟队列订单数据提交异常: title={}", request.getTitle(), e);
            return CrmResponse.builder()
                    .code(500)
                    .msg("系统异常: " + e.getMessage())
                    .build();
        }
    }
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 1000),
               exclude = {WebClientResponseException.NotFound.class})
    public CrmStatusResponse queryExecutionResult(String requestCode) {
        try {
            log.info("查询CRM执行结果: requestCode={}", requestCode);
            
            CrmStatusResponse response = webClient
                    .get()
                    .uri(CrmApiEnum.getQueryResultUrl(requestCode))
                    .retrieve()
                    .bodyToMono(CrmStatusResponse.class)
                    .block();
            
            if (response != null) {
                log.info("CRM执行结果查询完成: requestCode={}, state={}, msg={}",
                        requestCode, response.getState(), response.getMsg());
            }
            
            return response;
            
        } catch (WebClientResponseException e) {
            log.error("CRM状态查询HTTP错误: requestCode={}, status={}, body={}", 
                    requestCode, e.getStatusCode(), e.getResponseBodyAsString(), e);
            return CrmStatusResponse.builder()
                    .state(2)
                    .msg("HTTP错误: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("CRM状态查询异常: requestCode={}", requestCode, e);
            return CrmStatusResponse.builder()
                    .state(2)
                    .msg("系统异常: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 测试CRM API连接
     */
    public boolean testConnection() {
        try {
            // 创建一个测试请求
            CustomerCrmRequest testRequest = CustomerCrmRequest.builder()
                    .cusName("测试连接")
                    .linkManList(java.util.List.of(
                            CustomerCrmRequest.LinkMan.builder()
                                    .realName("测试")
                                    .mobilePhone("13800000000")
                                    .build()
                    ))
                    .build();
            
            CrmResponse response = submitCustomer(testRequest);
            
            // 即使业务失败，只要能收到响应就说明连接正常
            return response != null && response.getCode() != null;
            
        } catch (Exception e) {
            log.error("CRM API连接测试失败", e);
            return false;
        }
    }
}