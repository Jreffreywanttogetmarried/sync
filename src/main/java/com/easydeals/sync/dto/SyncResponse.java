package com.easydeals.sync.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * 同步响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncResponse {
    
    /**
     * 响应码
     */
    private int code;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 成功的任务ID列表
     */
    private List<String> successTaskIds;
    
    /**
     * 失败的任务信息列表
     */
    private List<FailedTask> failedTasks;
    
    /**
     * 失败任务信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FailedTask {
        private String businessId;
        private String reason;
        private String dataType;
    }
    
    /**
     * 创建成功响应
     */
    public static SyncResponse success(List<String> taskIds) {
        return SyncResponse.builder()
                .code(200)
                .message("全部提交成功(未推送客户无忧)")
                .successTaskIds(taskIds)
                .build();
    }
    
    /**
     * 创建部分成功响应
     */
    public static SyncResponse partialSuccess(List<String> successTaskIds, List<FailedTask> failedTasks) {
        return SyncResponse.builder()
                .code(206)
                .message("部分提交成功(未推送客户无忧)")
                .successTaskIds(successTaskIds)
                .failedTasks(failedTasks)
                .build();
    }
    
    /**
     * 创建失败响应
     */
    public static SyncResponse error(String message) {
        return SyncResponse.builder()
                .code(400)
                .message(message)
                .build();
    }
}