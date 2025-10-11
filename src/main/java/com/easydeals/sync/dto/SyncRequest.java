package com.easydeals.sync.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * 同步请求DTO（泛型设计）
 * 支持自动实体类装配
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncRequest<T> {
    
    /**
     * 网站代码
     */
    @NotBlank(message = "网站代码不能为空")
    private String websiteCode;
    
    /**
     * 数据类型（CUSTOMER, ORDER等）
     * 用于自动装配对应的实体类
     */
    @NotBlank(message = "数据类型不能为空")
    private String dataType;
    
    /**
     * 同步数据列表（泛型支持不同类型的数据实体）
     */
    @NotEmpty(message = "同步数据不能为空")
    @Valid
    private List<T> dataList;
}