package com.easydeals.sync.controller;

import com.easydeals.sync.dto.SyncRequest;
import com.easydeals.sync.dto.SyncResponse;
import com.easydeals.sync.dto.TaskStatusResponse;
import com.easydeals.sync.service.SyncTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 同步API控制器
 * 支持自动实体类装配，一套接口适配多种数据类型
 */
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
@Slf4j
public class SyncController {
    
    private final SyncTaskService syncTaskService;
    
    /**
     * 提交同步任务
     * 支持多种数据类型：客户数据(CUSTOMER)、订单数据(ORDER)等
     * 根据dataType自动装配对应的实体类
     */
    @PostMapping("/submit")
    public ResponseEntity<SyncResponse> submitSync(@Valid @RequestBody SyncRequest<?> request) {
        try {
            log.info("收到同步请求: websiteCode={}, dataType={}, dataCount={}", 
                    request.getWebsiteCode(), request.getDataType(), request.getDataList().size());
            
            SyncResponse response = syncTaskService.submitSyncTasks(request);
            
            log.info("同步请求处理完成: websiteCode={}, dataType={}, successCount={}, failedCount={}", 
                    request.getWebsiteCode(), request.getDataType(),
                    response.getSuccessTaskIds() != null ? response.getSuccessTaskIds().size() : 0,
                    response.getFailedTasks() != null ? response.getFailedTasks().size() : 0);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("同步请求处理异常: websiteCode={}, dataType={}", 
                    request.getWebsiteCode(), request.getDataType(), e);
            return ResponseEntity.badRequest()
                    .body(SyncResponse.error("系统异常: " + e.getMessage()));
        }
    }
    
    /**
     * 查询任务状态
     */
    @GetMapping("/status/{taskId}")
    public ResponseEntity<TaskStatusResponse> getTaskStatus(@PathVariable String taskId) {
        try {
            log.info("查询任务状态: taskId={}", taskId);
            
            TaskStatusResponse response = syncTaskService.getTaskStatus(taskId);
            
            if (response == null) {
                log.warn("任务不存在: taskId={}", taskId);
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("查询任务状态异常: taskId={}", taskId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}