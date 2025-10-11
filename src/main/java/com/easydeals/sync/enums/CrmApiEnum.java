package com.easydeals.sync.enums;

/**
 * CRM API枚举类
 * 集中管理所有CRM接口的完整URL路径
 */
public enum CrmApiEnum {
    
    /**
     * 新增企业客户API
     */
    ADD_CUSTOMER("https://openapi.kehu51.com/v1/openapi/671139/01u04Y39478d6e6", "新增企业客户"),
    
    /**
     * 新增销售订单API
     */
    ADD_ORDER("https://openapi.kehu51.com/v1/openapi/671139/01j0493957qc340", "新增销售订单"),
    
    /**
     * 查询执行结果API
     * 注意：实际使用时需要将{requestCode}替换为具体的请求编码
     */
    QUERY_RESULT("https://openapi.kehu51.com/v1/openapi/671139/{requestCode}", "查询执行结果");
    
    private final String url;
    private final String description;
    
    CrmApiEnum(String url, String description) {
        this.url = url;
        this.description = description;
    }
    
    public String getUrl() {
        return url;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 获取查询结果的完整URL
     * @param requestCode 请求编码
     * @return 完整的查询URL
     */
    public static String getQueryResultUrl(String requestCode) {
        return QUERY_RESULT.getUrl().replace("{requestCode}", requestCode);
    }
}