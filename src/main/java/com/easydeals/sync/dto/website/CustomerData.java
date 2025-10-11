package com.easydeals.sync.dto.website;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 客户数据实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerData {
    
    /**
     * 业务ID，用于去重
     */
    @NotBlank(message = "业务ID不能为空")
    private String businessId;
    
    /**
     * 获得时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime getTime;
    
    /**
     * 客户名称
     */
    @NotBlank(message = "客户名称不能为空")
    private String cusName;
    
    /**
     * 客户类别
     */
    private String classID;
    
    /**
     * 邮箱
     */
    private String customize4;

    /**
     * 所属网站
     */
    private String website;
    
    /**
     * 联系人列表
     */
    @NotEmpty(message = "联系人列表不能为空")
    @Valid
    private List<LinkMan> linkManList;
}