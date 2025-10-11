package com.easydeals.sync.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * CRM状态查询响应DTO
 * 用于新增API后的查询接口响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrmStatusResponse {
    
    /**
     * 执行状态码：0-待执行，1-执行成功，2-执行失败
     */
    private Integer state;
    
    /**
     * 执行失败原因
     */
    private String msg;
    
    /**
     * 是否执行成功
     */
    public boolean isSuccess() {
        return state != null && state == 1;
    }
    
    /**
     * 是否执行失败
     */
    public boolean isFailed() {
        return state != null && state == 2;
    }
    
    /**
     * 是否待执行
     */
    public boolean isPending() {
        return state != null && state == 0;
    }
}