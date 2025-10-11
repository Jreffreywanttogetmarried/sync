package com.easydeals.sync.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 同步任务实体类（单条数据记录）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncTask {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 任务ID，唯一标识
     */
    private String taskId;
    
    /**
     * 批次ID，关联批次表
     */
    private String batchId;
    
    /**
     * 网站代码，用于区分数据来源
     */
    private String websiteCode;
    
    /**
     * 数据类型
     */
    private String dataType;
    
    /**
     * 业务ID，用于去重
     */
    private String businessId;
    
    /**
     * 同步数据，JSON格式（单条客户数据）
     */
    private String syncData;
    
    /**
     * 任务状态：0-待处理，1-处理中，2-成功，3-失败
     */
    @Builder.Default
    private Integer status = 0;
    
    /**
     * 重试次数
     */
    @Builder.Default
    private Integer retryCount = 0;
    
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
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    /**
     * 任务状态枚举
     */
    public enum Status {
        PENDING(0, "待处理"),
        PROCESSING(1, "处理中"),
        SUCCESS(2, "成功"),
        FAILED(3, "失败");
        
        private final int code;
        private final String description;
        
        Status(int code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public int getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
        
        public static Status fromCode(int code) {
            for (Status status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown status code: " + code);
        }
    }
}