package com.easydeals.sync.dto.website;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 订单产品信息实体
 * 对应订单中的单个产品信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderProduct {
    
    /**
     * 产品SKU
     */
    @JsonProperty("skuProduct")
    private String skuProduct;
    
    /**
     * 产品名称
     */
    @JsonProperty("productName")
    private String productName;
    
    /**
     * 产品规格名称
     */
    @JsonProperty("productSpecsName")
    private String productSpecsName;
    
    /**
     * 单价（指导价）
     */
    @JsonProperty("unitPrice")
    private BigDecimal unitPrice;
    
    /**
     * 成交数量
     */
    @JsonProperty("dealCount")
    private BigDecimal dealCount;
    
    /**
     * 成交总价
     */
    @JsonProperty("dealPrice")
    private BigDecimal dealPrice;
    
    /**
     * 备注
     */
    @JsonProperty("remark")
    private String remark;

    /**
     * 指导总价
     */
    @JsonProperty("OriginalPrice")
    private BigDecimal originalPrice;

    /**
        成交单价
     */
    @JsonProperty("ActualUnitPrice")
    private BigDecimal actualUnitPrice;
}