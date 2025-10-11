package com.easydeals.sync.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 任务状态查询响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskStatusResponse {
    
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 任务状态：0-待处理，1-处理中，2-成功，3-失败
     */
    private Integer status;
    
    /**
     * 状态描述
     */
    private String statusDesc;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * CRM请求编码
     */
    private String crmRequestCode;
    
    /**
     * 错误信息
     */
    private String errorMsg;
    
    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}