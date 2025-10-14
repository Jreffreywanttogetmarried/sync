package com.easydeals.sync.service;

import com.easydeals.sync.dto.CrmResponse;
import com.easydeals.sync.dto.khwy.CustomerCrmRequest;
import com.easydeals.sync.dto.khwy.OrderCrmRequest;
import com.easydeals.sync.entity.SyncTask;
import com.easydeals.sync.enums.DataTypeEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 延迟重试处理器
 * 专门处理延迟重试队列中的任务，与正常处理流程完全分离
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DelayedRetryProcessor {
    
    private final SyncTaskService syncTaskService;
    private final CrmApiService crmApiService;
    private final ObjectMapper objectMapper;
    private final SyncProcessorService syncProcessorService; // 复用转换逻辑
    
    /**
     * 处理延迟重试任务
     * 这是一个完全独立的处理流程，不依赖Spring Retry机制
     */
    public void processDelayedRetryTask(String taskId) {
        try {
            log.info("开始处理延迟重试任务: taskId={}", taskId);
            
            // 获取任务详情
            SyncTask task = syncTaskService.getTask(taskId);
            if (task == null) {
                log.error("延迟重试任务不存在: taskId={}", taskId);
                return;
            }
            
            // 验证任务状态必须是DELAYED_RETRY
            if (task.getStatus() != SyncTask.Status.DELAYED_RETRY.getCode()) {
                log.warn("任务状态不是延迟重试状态，跳过处理: taskId={}, status={}", taskId, task.getStatus());
                return;
            }
            
            log.info("延迟重试任务验证通过: taskId={}, dataType={}, retryCount={}", 
                    taskId, task.getDataType(), task.getRetryCount());
            
            // 根据数据类型处理任务
            boolean success = processTaskByDataType(task);
            
            if (success) {
                log.info("延迟重试任务处理成功: taskId={}", taskId);
            } else {
                log.warn("延迟重试任务处理失败: taskId={}", taskId);
                // 延迟重试失败，将任务重新标记为失败状态
                syncTaskService.updateTaskToFailed(taskId, "延迟重试后仍然失败");
            }
            
        } catch (Exception e) {
            log.error("处理延迟重试任务异常: taskId={}", taskId, e);
            // 延迟重试异常，将任务标记为失败状态
            try {
                syncTaskService.updateTaskToFailed(taskId, "延迟重试处理异常: " + e.getMessage());
            } catch (Exception updateEx) {
                log.error("更新延迟重试任务失败状态异常: taskId={}", taskId, updateEx);
            }
        }
    }
    
    /**
     * 根据数据类型处理任务
     */
    private boolean processTaskByDataType(SyncTask task) throws JsonProcessingException {
        if (DataTypeEnum.CUSTOMER.getCode().equals(task.getDataType())) {
            return processDelayedRetryCustomerTask(task);
        } else if (DataTypeEnum.ORDER.getCode().equals(task.getDataType())) {
            return processDelayedRetryOrderTask(task);
        } else {
            log.error("不支持的数据类型: taskId={}, dataType={}", task.getTaskId(), task.getDataType());
            return false;
        }
    }
    
    /**
     * 处理延迟重试的客户任务
     */
    private boolean processDelayedRetryCustomerTask(SyncTask task) throws JsonProcessingException {
        try {
            log.info("开始处理延迟重试客户任务: taskId={}", task.getTaskId());
            
            // 解析客户数据
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> customerData = objectMapper.readValue(
                    task.getSyncData(), java.util.Map.class);
            
            // 转换为CRM请求格式（复用原有逻辑）
            CustomerCrmRequest crmRequest = syncProcessorService.convertToCustomerCrmRequest(customerData);
            
            // 调用CRM API
            CrmResponse crmResponse = crmApiService.submitCustomer(crmRequest);
            
            if (crmResponse != null && crmResponse.isSuccess()) {
                // 成功：更新状态为PROCESSING，清除错误信息，保存CRM请求编码
                updateDelayedRetryTaskToSuc(task.getTaskId(), crmResponse.getRequestCode());
                log.info("延迟重试客户任务CRM API调用成功: taskId={}, requestCode={}", 
                        task.getTaskId(), crmResponse.getRequestCode());
                return true;
            } else {
                // 失败：记录错误信息
                String errorMsg = String.format("延迟重试客户CRM API调用失败: code=%d, msg=%s", 
                        crmResponse != null ? crmResponse.getCode() : -1,
                        crmResponse != null ? crmResponse.getMsg() : "响应为空");
                log.error("延迟重试客户任务失败: taskId={}, {}", task.getTaskId(), errorMsg);
                return false;
            }
            
        } catch (Exception e) {
            log.error("处理延迟重试客户任务异常: taskId={}", task.getTaskId(), e);
            return false;
        }
    }
    
    /**
     * 处理延迟重试的订单任务
     */
    private boolean processDelayedRetryOrderTask(SyncTask task) throws JsonProcessingException {
        try {
            log.info("开始处理延迟重试订单任务: taskId={}", task.getTaskId());
            
            // 解析订单数据
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> orderData = objectMapper.readValue(
                    task.getSyncData(), java.util.Map.class);
            
            // 转换为CRM请求格式（复用原有逻辑）
            OrderCrmRequest crmRequest = syncProcessorService.convertToOrderCrmRequest(orderData);
            
            // 调用CRM API
            CrmResponse crmResponse = crmApiService.submitOrder(crmRequest);
            
            if (crmResponse != null && crmResponse.isSuccess()) {
                // 成功：更新状态为PROCESSING，清除错误信息，保存CRM请求编码
                updateDelayedRetryTaskToSuc(task.getTaskId(), crmResponse.getRequestCode());
                log.info("延迟重试订单任务CRM API调用成功: taskId={}, requestCode={}", 
                        task.getTaskId(), crmResponse.getRequestCode());
                return true;
            } else {
                // 失败：记录错误信息
                String errorMsg = String.format("延迟重试订单CRM API调用失败: code=%d, msg=%s", 
                        crmResponse != null ? crmResponse.getCode() : -1,
                        crmResponse != null ? crmResponse.getMsg() : "响应为空");
                log.error("延迟重试订单任务失败: taskId={}, {}", task.getTaskId(), errorMsg);
                return false;
            }
            
        } catch (Exception e) {
            log.error("处理延迟重试订单任务异常: taskId={}", task.getTaskId(), e);
            return false;
        }
    }
    
    /**
     * 将延迟重试任务更新为处理中状态
     * 同时清除错误信息并保存CRM请求编码
     */
    private void updateDelayedRetryTaskToSuc(String taskId, String crmRequestCode) {
        try {
            // 使用专门的更新方法：状态更新为PROCESSING，清除错误信息，保存CRM请求编码
            syncTaskService.updateDelayedRetryTaskToSuc(taskId, crmRequestCode);
            log.info("延迟重试任务状态已更新为PROCESSING: taskId={}, crmRequestCode={}", taskId, crmRequestCode);
        } catch (Exception e) {
            log.error("更新延迟重试任务状态异常: taskId={}", taskId, e);
            throw e;
        }
    }
}