package com.easydeals.sync.dto.khwy;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 客户CRM API请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerCrmRequest {
    
    /**
     * 获得时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime getTime;
    
    /**
     * 客户名称
     */
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
    private List<LinkMan> linkManList;
    
    /**
     * 联系人信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LinkMan {
        
        /**
         * 姓名
         */
        private String realName;
        
        /**
         * 手机号
         */
        private String mobilePhone;
    }
}