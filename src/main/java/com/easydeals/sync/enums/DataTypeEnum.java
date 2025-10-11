package com.easydeals.sync.enums;

import com.easydeals.sync.dto.website.CustomerData;
import com.easydeals.sync.dto.website.OrderData;

/**
 * 数据类型枚举
 * 支持自动实体类装配
 */
public enum DataTypeEnum {
    
    /**
     * 客户数据
     */
    CUSTOMER("CUSTOMER", "客户数据", CustomerData.class),
    
    /**
     * 订单数据
     */
    ORDER("ORDER", "订单数据", OrderData.class);
    
    private final String code;
    private final String description;
    private final Class<?> entityClass;
    
    DataTypeEnum(String code, String description, Class<?> entityClass) {
        this.code = code;
        this.description = description;
        this.entityClass = entityClass;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public Class<?> getEntityClass() {
        return entityClass;
    }
    
    /**
     * 根据代码获取枚举
     */
    public static DataTypeEnum fromCode(String code) {
        for (DataTypeEnum dataType : values()) {
            if (dataType.code.equals(code)) {
                return dataType;
            }
        }
        throw new IllegalArgumentException("未知的数据类型: " + code);
    }
    
    /**
     * 检查是否支持该数据类型
     */
    public static boolean isSupported(String code) {
        try {
            DataTypeEnum dataType = fromCode(code);
            return dataType.entityClass != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}