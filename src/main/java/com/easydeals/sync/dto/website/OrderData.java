package com.easydeals.sync.dto.website;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 网站端订单数据实体
 * 对应网站提交的订单信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderData {
    
    /**
     * 业务ID - 用于去重和状态跟踪
     * 通常是网站端的订单号
     */
    @JsonProperty("businessId")
    private String businessId;
    
    /**
     * 订单标题/主题
     */
    @JsonProperty("title")
    private String title;
    
    /**
     * 客户姓名
     */
    @JsonProperty("cusName")
    private String cusName;
    
    /**
     * 客户手机号
     */
    @JsonProperty("cusMobilePhone")
    private String cusMobilePhone;
    
    /**
     * 成交总额
     */
    @JsonProperty("dealAmount")
    private BigDecimal dealAmount;
    
    /**
     * 成交时间
     */
    @JsonProperty("dealTime")
    private OffsetDateTime dealTime;
    
    /**
     * 付款方式ID
     */
    @JsonProperty("payModeID")
    private String payModeID;
    
    /**
     * 订单状态ID
     */
    @JsonProperty("dealStateID")
    private String dealStateID;
    
    /**
     * 订单说明
     */
    @JsonProperty("intro")
    private String intro;
    
    /**
     * 收件人
     */
    @JsonProperty("recipient")
    private String recipient;
    
    /**
     * 收货地址
     */
    @JsonProperty("shippingAddress")
    private String shippingAddress;
    
    /**
     * 邮政编码
     */
    @JsonProperty("postCode")
    private String postCode;
    
    /**
     * 所属网站
     */
    @JsonProperty("website")
    private String website;
    
    /**
     * 账单信息
     */
    @JsonProperty("paymentFirstLastName")
    private String paymentFirstLastName;
    
    @JsonProperty("paymentCompany")
    private String paymentCompany;
    
    @JsonProperty("paymentAddress1")
    private String paymentAddress1;
    
    @JsonProperty("paymentAddress2")
    private String paymentAddress2;
    
    @JsonProperty("paymentCountry")
    private String paymentCountry;
    
    @JsonProperty("paymentPostcode")
    private String paymentPostcode;
    
    @JsonProperty("paymentCity")
    private String paymentCity;
    
    @JsonProperty("paymentZone")
    private String paymentZone;
    
    /**
     * 运输信息
     */
    @JsonProperty("shippingFirstLastName")
    private String shippingFirstLastName;
    
    @JsonProperty("shippingCompany")
    private String shippingCompany;
    
    @JsonProperty("shippingAddress1")
    private String shippingAddress1;
    
    @JsonProperty("shippingAddress2")
    private String shippingAddress2;
    
    @JsonProperty("shippingCity")
    private String shippingCity;
    
    @JsonProperty("shippingPostcode")
    private String shippingPostcode;
    
    @JsonProperty("shippingCountry")
    private String shippingCountry;
    
    @JsonProperty("shippingZone")
    private String shippingZone;
    
    @JsonProperty("shippingMethod")
    private String shippingMethod;
    
    /**
     * 客户IP地址
     */
    @JsonProperty("ip")
    private String ip;

    /**
     * 购买产品的选项
     */
    @JsonProperty("productOptions")
    private String productOptions;

    /**
     * 订单产品列表
     */
    @JsonProperty("dealProductList")
    private List<OrderProduct> dealProductList;
}