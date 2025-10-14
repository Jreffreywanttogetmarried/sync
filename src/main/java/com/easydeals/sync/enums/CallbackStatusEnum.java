package com.easydeals.sync.enums;

/**
 * 回调状态枚举
 */
public enum CallbackStatusEnum {
    
    /**
     * 未回调
     */
    NOT_CALLED(0, "未回调"),
    
    /**
     * 已回调
     */
    CALLED(1, "已回调"),
    
    /**
     * 回调失败
     */
    FAILED(2, "回调失败");
    
    private final int code;
    private final String description;
    
    CallbackStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据代码获取枚举
     */
    public static CallbackStatusEnum fromCode(int code) {
        for (CallbackStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown callback status code: " + code);
    }
}