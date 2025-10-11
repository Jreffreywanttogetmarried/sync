package com.easydeals.sync.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * CRM API响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrmResponse {
    
    /**
     * 业务错误码
     */
    private Integer code;
    
    /**
     * 业务错误码描述
     */
    private String msg;
    
    /**
     * 请求编码
     */
    private String requestCode;
    
    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return code != null && code == 600000;
    }
}