package com.easydeals.sync.dto.khwy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * CRM订单产品信息实体
 * 对应客户无忧CRM系统的产品信息格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCrmProduct {
    
    /**
     * SKU
     */
    @JsonProperty("SKU_product")
    private String skuProduct;
    
    /**
     * 产品名称
     */
    @JsonProperty("ProductName")
    private String productName;
    
    /**
     * 规格
     */
    @JsonProperty("ProductSpecsName")
    private String productSpecsName;
    
    /**
     * 指导价
     */
    @JsonProperty("UnitPrice")
    private BigDecimal unitPrice;
    
    /**
     * 成交数量
     */
    @JsonProperty("DealCount")
    private BigDecimal dealCount;
    
    /**
     * 成交总价
     */
    @JsonProperty("DealPrice")
    private BigDecimal dealPrice;
    
    /**
     * 备注
     */
    @JsonProperty("Remark")
    private String remark;

    /**
     * 指导总价
     */
    @JsonProperty("OriginalPrice")
    private BigDecimal originalPrice;

    /**
     * 成交单价
     */
    @JsonProperty("ActualUnitPrice")
    private BigDecimal actualUnitPrice;
}