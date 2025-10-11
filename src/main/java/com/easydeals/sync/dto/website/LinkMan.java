package com.easydeals.sync.dto.website;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 联系人信息实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkMan {
    
    /**
     * 姓名
     */
    @NotBlank(message = "联系人姓名不能为空")
    private String realName;
    
    /**
     * 手机号
     */
    private String mobilePhone;
}