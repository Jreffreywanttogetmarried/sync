package com.easydeals.sync.dto.khwy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * CRM订单请求实体
 * 对应客户无忧CRM系统的订单API格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCrmRequest {
    
    /**
     * 主题
     */
    @JsonProperty("Title")
    private String title;
    
    /**
     * 成交客户
     */
    @JsonProperty("CusName")
    private String cusName;
    
    /**
     * 客户手机号
     */
    @JsonProperty("CusMobilePhone")
    private String cusMobilePhone;
    
    /**
     * 成交总额
     */
    @JsonProperty("DealAmount")
    private BigDecimal dealAmount;
    
    /**
     * 实际成交时间
     */
    @JsonProperty("DealTime")
    private OffsetDateTime dealTime;
    
    /**
     * 付款方式
     */
    @JsonProperty("PayModeID")
    private String payModeID;
    
    /**
     * 状态
     */
    @JsonProperty("DealStateID")
    private String dealStateID;
    
    /**
     * 说明
     */
    @JsonProperty("Intro")
    private String intro;
    
    /**
     * 所属网站的订单号
     */
    @JsonProperty("Recipient")
    private String recipient;
    
    /**
     * DIY设计文件
     */
    @JsonProperty("DiyDesignFile")
    private String diyDesignFile;
    
    /**
     * 客户上传的设计文件
     */
    @JsonProperty("UploadFile")
    private String uploadFile;
    
    /**
     * 所属网站
     */
    @JsonProperty("website")
    private String website;
    
    /**
     * 账单-客户名
     */
    @JsonProperty("payment_first_last_name")
    private String paymentFirstLastName;
    
    /**
     * 账单-公司
     */
    @JsonProperty("payment_company")
    private String paymentCompany;
    
    /**
     * 账单-地址1
     */
    @JsonProperty("payment_address1")
    private String paymentAddress1;
    
    /**
     * 账单-地址2
     */
    @JsonProperty("payment_address2")
    private String paymentAddress2;
    
    /**
     * 账单-国家
     */
    @JsonProperty("payment_country")
    private String paymentCountry;
    
    /**
     * 账单-邮编
     */
    @JsonProperty("payment_postcode")
    private String paymentPostcode;
    
    /**
     * 账单-城市
     */
    @JsonProperty("payment_city")
    private String paymentCity;
    
    /**
     * 账单-地区
     */
    @JsonProperty("payment_zone")
    private String paymentZone;
    
    /**
     * 运输-客户名
     */
    @JsonProperty("shipping_first_last_name")
    private String shippingFirstLastName;
    
    /**
     * 运输-公司
     */
    @JsonProperty("shipping_company")
    private String shippingCompany;
    
    /**
     * 运输-地址1
     */
    @JsonProperty("shipping_address1")
    private String shippingAddress1;
    
    /**
     * 运输-地址2
     */
    @JsonProperty("shipping_address2")
    private String shippingAddress2;
    
    /**
     * 运输-城市
     */
    @JsonProperty("shipping_city")
    private String shippingCity;
    
    /**
     * 运输-邮编
     */
    @JsonProperty("shipping_postcode")
    private String shippingPostcode;
    
    /**
     * 运输-国家
     */
    @JsonProperty("shipping_country")
    private String shippingCountry;
    
    /**
     * 运输-地区
     */
    @JsonProperty("shipping_zone")
    private String shippingZone;
    
    /**
     * 运输-方式
     */
    @JsonProperty("shipping_method")
    private String shippingMethod;
    
    /**
     * IP
     */
    @JsonProperty("ip")
    private String ip;
    
    /**
     * 成交产品列表
     */
    @JsonProperty("DealProductList")
    private List<OrderCrmProduct> dealProductList;
}