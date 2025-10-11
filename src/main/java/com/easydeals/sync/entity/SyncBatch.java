package com.easydeals.sync.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 同步批次实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncBatch {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 批次ID，唯一标识
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
     * 批次总数量
     */
    private Integer totalCount;
    
    /**
     * 成功数量
     */
    @Builder.Default
    private Integer successCount = 0;
    
    /**
     * 失败数量
     */
    @Builder.Default
    private Integer failedCount = 0;
    
    /**
     * 处理中数量
     */
    @Builder.Default
    private Integer processingCount = 0;
    
    /**
     * 批次状态：0-待处理，1-处理中，2-已完成，3-部分失败
     */
    @Builder.Default
    private Integer status = 0;
    
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
     * 批次状态枚举
     */
    public enum Status {
        PENDING(0, "待处理"),
        PROCESSING(1, "处理中"),
        COMPLETED(2, "已完成"),
        PARTIAL_FAILED(3, "部分失败");
        
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