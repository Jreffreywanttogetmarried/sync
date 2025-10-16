package com.easydeals.sync.service;

import com.easydeals.sync.dto.khwy.CustomerCrmRequest;
import com.easydeals.sync.dto.khwy.OrderCrmRequest;
import com.easydeals.sync.dto.khwy.OrderCrmProduct;
import com.easydeals.sync.dto.CrmResponse;
import com.easydeals.sync.entity.SyncTask;
import com.easydeals.sync.enums.DataTypeEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * 同步处理服务
 * 支持自动实体类装配和多种数据类型处理
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncProcessorService {
    
    private final SyncTaskService syncTaskService;
    private final CrmApiService crmApiService;
    private final ObjectMapper objectMapper;
    private final CallbackService callbackService;
    private final DelayedRetryService delayedRetryService;
    
    /**
     * 处理同步任务（支持自动实体类装配）
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 5000))
    public void processTask(String taskId) throws JsonProcessingException {
        try {
            log.info("开始处理同步任务: taskId={}", taskId);
            
            // 获取任务详情，添加重试机制防止读取不到刚插入的数据
            SyncTask task = null;
            int retryCount = 0;
            while (task == null && retryCount < 3) {
                task = syncTaskService.getTask(taskId);
                if (task == null) {
                    retryCount++;
                    log.warn("任务暂时不存在，等待重试: taskId={}, retryCount={}", taskId, retryCount);
                    try {
                        Thread.sleep(200); // 等待200ms后重试
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            if (task == null) {
                log.error("任务不存在: taskId={}", taskId);
                return;
            }
            
            // 检查任务状态 - 允许待处理、处理中、重试中和延迟重试状态
            if (task.getStatus() != SyncTask.Status.PENDING.getCode() && 
                task.getStatus() != SyncTask.Status.PROCESSING.getCode() &&
                task.getStatus() != SyncTask.Status.RETRYING.getCode() &&
                task.getStatus() != SyncTask.Status.DELAYED_RETRY.getCode()) {
                log.warn("任务状态不允许处理: taskId={}, status={}", taskId, task.getStatus());
                return;
            }
            
            // 根据当前状态决定如何更新状态
            if (task.getStatus() == SyncTask.Status.PENDING.getCode()) {
                // 首次处理，更新为处理中
                syncTaskService.updateTaskToProcessing(taskId);
            } else if (task.getStatus() == SyncTask.Status.PROCESSING.getCode()) {
                // 第一次重试，更新为重试中
                syncTaskService.updateTaskToRetrying(taskId);
            }
            // 如果已经是重试中状态，不需要更新状态

            // 根据数据类型自动装配和处理
            processTaskByDataType(task);
            
        } catch (Exception e) {
            log.error("处理同步任务异常: taskId={}", taskId, e);
            // 不在这里直接标记为失败，让Spring Retry处理重试
            // 只有在@Recover方法中才最终标记为失败
            throw e; // 重新抛出异常以触发重试
        }
    }
    
    /**
     * 重试失败后的恢复方法
     * 当processTask方法重试3次都失败后，会调用此方法
     */
    @Recover
    public void recoverProcessTask(Exception ex, String taskId) {
        log.error("任务处理失败，已达到最大重试次数: taskId={}, error={}", taskId, ex.getMessage());
        
        try {
            // 最终标记任务为失败
            syncTaskService.updateTaskToFailedAfterReTry(taskId, "重试3次后失败: " + ex.getMessage());
            
        } catch (Exception e) {
            log.error("恢复方法执行异常: taskId={}", taskId, e);
        }
    }
    
    /**
     * 根据数据类型自动装配和处理任务
     */
    private void processTaskByDataType(SyncTask task) throws JsonProcessingException {
        try {
            log.info("开始根据数据类型处理任务: taskId={}, dataType={}", task.getTaskId(), task.getDataType());
            
            DataTypeEnum dataType = DataTypeEnum.fromCode(task.getDataType());
            
            switch (dataType) {
                case CUSTOMER:
                    processCustomerTask(task);
                    break;
                case ORDER:
                    // 预留订单处理逻辑
                    processOrderTask(task);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的数据类型: " + task.getDataType());
            }
            
            log.info("数据类型处理完成: taskId={}, dataType={}", task.getTaskId(), task.getDataType());
            
        } catch (Exception e) {
//            log.error("数据类型处理异常: taskId={}, dataType={}", task.getTaskId(), task.getDataType(), e);
            throw e;
        }
    }
    
    /**
     * 处理客户数据任务
     */
    private void processCustomerTask(SyncTask task) throws JsonProcessingException {
        try {
            log.info("开始处理客户任务: taskId={}", task.getTaskId());
            
            // 直接解析存储的JSON数据，不依赖实体类
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> customerData = objectMapper.readValue(
                    task.getSyncData(), java.util.Map.class);
            
            log.debug("客户数据解析成功: taskId={}, data={}", task.getTaskId(), customerData);
            
            // 转换为CRM请求格式
            CustomerCrmRequest crmRequest = convertToCustomerCrmRequest(customerData);
            
            log.debug("CRM请求转换成功: taskId={}, request={}", task.getTaskId(), crmRequest);
            
            // 调用CRM API
            CrmResponse crmResponse = crmApiService.submitCustomer(crmRequest);
            
            if (crmResponse != null && crmResponse.isSuccess()) {
                // 保存CRM请求编码，等待执行结果查询
                syncTaskService.updateTaskCrmRequestCode(task.getTaskId(), crmResponse.getRequestCode());
                log.info("客户CRM API调用成功，等待执行结果: taskId={}, requestCode={}", 
                        task.getTaskId(), crmResponse.getRequestCode());
            } else {
                // 检查是否为频率限制错误
                if (crmResponse != null && 
                    delayedRetryService.isRateLimitError(crmResponse.getCode(), crmResponse.getMsg())) {
                    
                    // 频率限制错误，加入延迟重试队列
                    int delayMinutes = delayedRetryService.calculateDelayMinutes(task.getRetryCount());
                    delayedRetryService.addToDelayedRetryQueue(task.getTaskId(), delayMinutes);
                    
                    log.warn("客户CRM API频率限制，已加入延迟重试队列: taskId={}, delayMinutes={}", 
                            task.getTaskId(), delayMinutes);
                    return; // 不抛出异常，避免触发Spring Retry
                }
                
                // 其他错误，抛出异常让重试机制处理
                String errorMsg = String.format("客户CRM API调用失败: code=%d, msg=%s", 
                        crmResponse != null ? crmResponse.getCode() : -1,
                        crmResponse != null ? crmResponse.getMsg() : "响应为空");
                log.error("客户CRM API调用失败: taskId={}, {}", task.getTaskId(), errorMsg);
                throw new RuntimeException(errorMsg);
            }
            
        } catch (Exception e) {
//            log.error("处理客户任务异常: taskId={}, 异常详情: ", task.getTaskId(), e);
            
            // 不在这里发送失败回调，让Spring Retry重试
            // 只有在最终失败时才在@Recover方法中发送失败回调
            
            // 重新抛出异常以触发重试机制
            throw e;
        }
    }
    
    /**
     * 处理订单数据任务
     */
    private void processOrderTask(SyncTask task) throws JsonProcessingException {
        try {
            log.info("开始处理订单任务: taskId={}", task.getTaskId());
            
            // 直接解析存储的JSON数据，不依赖实体类
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> orderData = objectMapper.readValue(
                    task.getSyncData(), java.util.Map.class);
            
            log.debug("订单数据解析成功: taskId={}, data={}", task.getTaskId(), orderData);
            
            // 转换为CRM请求格式
            OrderCrmRequest crmRequest = convertToOrderCrmRequest(orderData);
            
            log.debug("CRM请求转换成功: taskId={}, request={}", task.getTaskId(), crmRequest);
            
            // 调用CRM API
            CrmResponse crmResponse = crmApiService.submitOrder(crmRequest);
            
            if (crmResponse != null && crmResponse.isSuccess()) {
                // 保存CRM请求编码
                syncTaskService.updateTaskCrmRequestCode(task.getTaskId(), crmResponse.getRequestCode());
                log.info("订单CRM API调用成功，等待执行结果: taskId={}, requestCode={}", 
                        task.getTaskId(), crmResponse.getRequestCode());
            } else {
                // 检查是否为频率限制错误
                if (crmResponse != null && 
                    delayedRetryService.isRateLimitError(crmResponse.getCode(), crmResponse.getMsg())) {
                    
                    // 频率限制错误，加入延迟重试队列
                    int delayMinutes = delayedRetryService.calculateDelayMinutes(task.getRetryCount());
                    delayedRetryService.addToDelayedRetryQueue(task.getTaskId(), delayMinutes);
                    
                    log.warn("订单CRM API频率限制，已加入延迟重试队列: taskId={}, delayMinutes={}", 
                            task.getTaskId(), delayMinutes);
                    return; // 不抛出异常，避免触发Spring Retry
                }
                
                // 其他错误，抛出异常让重试机制处理
                String errorMsg = String.format("订单CRM API调用失败: code=%d, msg=%s", 
                        crmResponse != null ? crmResponse.getCode() : -1, 
                        crmResponse != null ? crmResponse.getMsg() : "响应为空");
                log.error("订单CRM API调用失败: taskId={}, {}", task.getTaskId(), errorMsg);
                throw new RuntimeException(errorMsg);
            }
            
        } catch (Exception e) {
//            log.error("处理订单任务异常: taskId={}", task.getTaskId(), e);
            
            // 不在这里发送失败回调，让Spring Retry重试
            // 只有在最终失败时才在@Recover方法中发送失败回调
            
            // 重新抛出异常以触发重试机制
            throw e;
        }
    }
    
    /**
     * 转换为订单CRM请求格式
     */
    public OrderCrmRequest convertToOrderCrmRequest(Object orderData) {
        try {
            log.debug("开始转换订单CRM请求: orderData={}", orderData);
            
            OrderCrmRequest.OrderCrmRequestBuilder builder = OrderCrmRequest.builder();
            
            // 获取各个字段的值
            Object title = getFieldValue(orderData, "title");
            Object cusName = getFieldValue(orderData, "cusName");
            Object cusMobilePhone = getFieldValue(orderData, "cusMobilePhone");
            Object dealAmount = getFieldValue(orderData, "dealAmount");
            Object dealTime = getFieldValue(orderData, "dealTime");
            Object payModeID = getFieldValue(orderData, "payModeID");
            Object dealStateID = getFieldValue(orderData, "dealStateID");
            Object intro = getFieldValue(orderData, "intro");
            Object recipient = getFieldValue(orderData, "recipient");
            Object diyDesignFile = getFieldValue(orderData, "diyDesignFile");
            Object uploadFile = getFieldValue(orderData, "uploadFile");
            Object website = getFieldValue(orderData, "website");
            
            // 账单信息
            Object paymentFirstLastName = getFieldValue(orderData, "paymentFirstLastName");
            Object paymentCompany = getFieldValue(orderData, "paymentCompany");
            Object paymentAddress1 = getFieldValue(orderData, "paymentAddress1");
            Object paymentAddress2 = getFieldValue(orderData, "paymentAddress2");
            Object paymentCountry = getFieldValue(orderData, "paymentCountry");
            Object paymentPostcode = getFieldValue(orderData, "paymentPostcode");
            Object paymentCity = getFieldValue(orderData, "paymentCity");
            Object paymentZone = getFieldValue(orderData, "paymentZone");
            
            // 运输信息
            Object shippingFirstLastName = getFieldValue(orderData, "shippingFirstLastName");
            Object shippingCompany = getFieldValue(orderData, "shippingCompany");
            Object shippingAddress1 = getFieldValue(orderData, "shippingAddress1");
            Object shippingAddress2 = getFieldValue(orderData, "shippingAddress2");
            Object shippingCity = getFieldValue(orderData, "shippingCity");
            Object shippingPostcode = getFieldValue(orderData, "shippingPostcode");
            Object shippingCountry = getFieldValue(orderData, "shippingCountry");
            Object shippingZone = getFieldValue(orderData, "shippingZone");
            Object shippingMethod = getFieldValue(orderData, "shippingMethod");
            
            Object ip = getFieldValue(orderData, "ip");
            Object productOptions = getFieldValue(orderData, "productOptions");
            Object orderTelephone = getFieldValue(orderData, "orderTelephone");
            Object dealProductList = getFieldValue(orderData, "dealProductList");
            
            log.debug("订单字段值获取结果: title={}, cusName={}, dealAmount={}, dealTime={}", 
                    title, cusName, dealAmount, dealTime);
            
            // 设置基本字段值
            if (title != null) builder.title(title.toString());
            if (cusName != null) builder.cusName(cusName.toString());
            if (cusMobilePhone != null) builder.cusMobilePhone(cusMobilePhone.toString());
            if (payModeID != null) builder.payModeID(payModeID.toString());
            if (dealStateID != null) builder.dealStateID(dealStateID.toString());
            if (intro != null) builder.intro(intro.toString());
            if (recipient != null) builder.recipient(recipient.toString());
            if (diyDesignFile != null) builder.diyDesignFile(diyDesignFile.toString());
            if (uploadFile != null) builder.uploadFile(uploadFile.toString());
            if (website != null) builder.website(website.toString());
            if (ip != null) builder.ip(ip.toString());
            if (productOptions != null) builder.productOptions(productOptions.toString());
            if (orderTelephone != null) builder.orderTelephone(orderTelephone.toString());

            // 处理金额字段
            if (dealAmount != null) {
                if (dealAmount instanceof java.math.BigDecimal) {
                    builder.dealAmount((java.math.BigDecimal) dealAmount);
                } else {
                    try {
                        builder.dealAmount(new java.math.BigDecimal(dealAmount.toString()));
                    } catch (NumberFormatException e) {
                        log.warn("金额格式解析失败: {}", dealAmount, e);
                    }
                }
            }
            
            // 处理时间字段
            if (dealTime != null) {
                if (dealTime instanceof String) {
                    try {
                        java.time.OffsetDateTime offsetDateTime = java.time.OffsetDateTime.parse(dealTime.toString());
                        builder.dealTime(offsetDateTime);
                    } catch (Exception e) {
                        log.warn("时间格式解析失败: {}", dealTime, e);
                    }
                } else if (dealTime instanceof java.time.OffsetDateTime) {
                    builder.dealTime((java.time.OffsetDateTime) dealTime);
                }
            }
            
            // 设置账单信息
            if (paymentFirstLastName != null) builder.paymentFirstLastName(paymentFirstLastName.toString());
            if (paymentCompany != null) builder.paymentCompany(paymentCompany.toString());
            if (paymentAddress1 != null) builder.paymentAddress1(paymentAddress1.toString());
            if (paymentAddress2 != null) builder.paymentAddress2(paymentAddress2.toString());
            if (paymentCountry != null) builder.paymentCountry(paymentCountry.toString());
            if (paymentPostcode != null) builder.paymentPostcode(paymentPostcode.toString());
            if (paymentCity != null) builder.paymentCity(paymentCity.toString());
            if (paymentZone != null) builder.paymentZone(paymentZone.toString());
            
            // 设置运输信息
            if (shippingFirstLastName != null) builder.shippingFirstLastName(shippingFirstLastName.toString());
            if (shippingCompany != null) builder.shippingCompany(shippingCompany.toString());
            if (shippingAddress1 != null) builder.shippingAddress1(shippingAddress1.toString());
            if (shippingAddress2 != null) builder.shippingAddress2(shippingAddress2.toString());
            if (shippingCity != null) builder.shippingCity(shippingCity.toString());
            if (shippingPostcode != null) builder.shippingPostcode(shippingPostcode.toString());
            if (shippingCountry != null) builder.shippingCountry(shippingCountry.toString());
            if (shippingZone != null) builder.shippingZone(shippingZone.toString());
            if (shippingMethod != null) builder.shippingMethod(shippingMethod.toString());
            
            // 转换产品列表
            if (dealProductList instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> products = (java.util.List<Object>) dealProductList;
                java.util.List<OrderCrmProduct> crmProducts = products.stream()
                        .map(this::convertToOrderCrmProduct)
                        .collect(java.util.stream.Collectors.toList());
                builder.dealProductList(crmProducts);
                log.debug("产品列表转换完成: count={}", crmProducts.size());
            }
            
            OrderCrmRequest result = builder.build();
            log.debug("订单CRM请求转换完成: {}", result);
            return result;
            
        } catch (Exception e) {
            log.error("转换订单CRM请求失败: orderData={}", orderData, e);
            throw new RuntimeException("转换订单CRM请求失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 转换订单产品对象
     */
    private OrderCrmProduct convertToOrderCrmProduct(Object productObj) {
        try {
            log.debug("转换订单产品: productObj={}", productObj);
            
            OrderCrmProduct.OrderCrmProductBuilder builder = OrderCrmProduct.builder();
            
            // 获取产品字段值
            Object skuProduct = getFieldValue(productObj, "skuProduct");
            Object productName = getFieldValue(productObj, "productName");
            Object productSpecsName = getFieldValue(productObj, "productSpecsName");
            Object unitPrice = getFieldValue(productObj, "unitPrice");
            Object dealCount = getFieldValue(productObj, "dealCount");
            Object dealPrice = getFieldValue(productObj, "dealPrice");
            Object remark = getFieldValue(productObj, "remark");
            Object originalPrice = getFieldValue(productObj, "originalPrice");
            Object actualUnitPrice = getFieldValue(productObj, "actualUnitPrice");
            
            // 设置字符串字段
            if (skuProduct != null) builder.skuProduct(skuProduct.toString());
            if (productName != null) builder.productName(productName.toString());
            if (productSpecsName != null) builder.productSpecsName(productSpecsName.toString());
            if (remark != null) builder.remark(remark.toString());
            
            // 处理数值字段
            if (unitPrice != null) {
                if (unitPrice instanceof java.math.BigDecimal) {
                    builder.unitPrice((java.math.BigDecimal) unitPrice);
                } else {
                    try {
                        builder.unitPrice(new java.math.BigDecimal(unitPrice.toString()));
                    } catch (NumberFormatException e) {
                        log.warn("单价格式解析失败: {}", unitPrice, e);
                    }
                }
            }
            
            if (dealCount != null) {
                if (dealCount instanceof java.math.BigDecimal) {
                    builder.dealCount((java.math.BigDecimal) dealCount);
                } else {
                    try {
                        builder.dealCount(new java.math.BigDecimal(dealCount.toString()));
                    } catch (NumberFormatException e) {
                        log.warn("数量格式解析失败: {}", dealCount, e);
                    }
                }
            }
            
            if (dealPrice != null) {
                if (dealPrice instanceof java.math.BigDecimal) {
                    builder.dealPrice((java.math.BigDecimal) dealPrice);
                } else {
                    try {
                        builder.dealPrice(new java.math.BigDecimal(dealPrice.toString()));
                    } catch (NumberFormatException e) {
                        log.warn("成交价格式解析失败: {}", dealPrice, e);
                    }
                }
            }

            if (originalPrice != null) {
                if (originalPrice instanceof java.math.BigDecimal) {
                    builder.originalPrice((java.math.BigDecimal) originalPrice);
                } else {
                    try {
                        builder.originalPrice(new java.math.BigDecimal(originalPrice.toString()));
                    } catch (NumberFormatException e) {
                        log.warn("单价格式解析失败: {}", unitPrice, e);
                    }
                }
            }

            if (actualUnitPrice != null) {
                if (actualUnitPrice instanceof java.math.BigDecimal) {
                    builder.actualUnitPrice((java.math.BigDecimal) actualUnitPrice);
                } else {
                    try {
                        builder.actualUnitPrice(new java.math.BigDecimal(actualUnitPrice.toString()));
                    } catch (NumberFormatException e) {
                        log.warn("单价格式解析失败: {}", unitPrice, e);
                    }
                }
            }

            OrderCrmProduct result = builder.build();
            log.debug("订单产品转换完成: {}", result);
            return result;
            
        } catch (Exception e) {
            log.error("转换订单产品失败: productObj={}", productObj, e);
            throw new RuntimeException("转换订单产品失败: " + e.getMessage(), e);
        }
    }
    public CustomerCrmRequest convertToCustomerCrmRequest(Object customerData) {
        try {
            log.debug("开始转换CRM请求: customerData={}", customerData);
            
            CustomerCrmRequest.CustomerCrmRequestBuilder builder = CustomerCrmRequest.builder();
            
            // 获取各个字段的值
            Object getTime = getFieldValue(customerData, "getTime");
            Object cusName = getFieldValue(customerData, "cusName");
            Object classID = getFieldValue(customerData, "classID");
            Object customize4 = getFieldValue(customerData, "customize4");
            Object website = getFieldValue(customerData, "website");
            Object linkManList = getFieldValue(customerData, "linkManList");
            
            log.debug("字段值获取结果: getTime={}, cusName={}, classID={}, customize4={}, website={}, linkManList={}", getTime, cusName, classID, customize4, website, linkManList);
            
            // 设置字段值
            if (getTime != null) {
                // 处理时间字段，可能是字符串格式
                if (getTime instanceof String) {
                    try {
                        java.time.OffsetDateTime offsetDateTime = java.time.OffsetDateTime.parse(getTime.toString());
                        builder.getTime(offsetDateTime);
                    } catch (Exception e) {
                        log.warn("时间格式解析失败: {}", getTime, e);
                    }
                } else if (getTime instanceof java.time.OffsetDateTime) {
                    builder.getTime((java.time.OffsetDateTime) getTime);
                }
            }
            if (cusName != null) {
                builder.cusName(cusName.toString());
            }
            if (classID != null) {
                builder.classID(classID.toString());
            }
            if (customize4 != null) {
                builder.customize4(customize4.toString());
            }
            if (website != null){
                builder.website(website.toString());
            }
            
            // 转换联系人列表
            if (linkManList instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> linkMans = (java.util.List<Object>) linkManList;
                java.util.List<CustomerCrmRequest.LinkMan> crmLinkMans = linkMans.stream()
                        .map(this::convertToLinkMan)
                        .collect(java.util.stream.Collectors.toList());
                builder.linkManList(crmLinkMans);
                log.debug("联系人列表转换完成: count={}", crmLinkMans.size());
            }
            
            CustomerCrmRequest result = builder.build();
            log.debug("CRM请求转换完成: {}", result);
            return result;
            
        } catch (Exception e) {
            log.error("转换客户CRM请求失败: customerData={}", customerData, e);
            throw new RuntimeException("转换客户CRM请求失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 转换联系人对象
     */
    private CustomerCrmRequest.LinkMan convertToLinkMan(Object linkManObj) {
        try {
            Object realName = getFieldValue(linkManObj, "realName");
            Object mobilePhone = getFieldValue(linkManObj, "mobilePhone");
            
            return CustomerCrmRequest.LinkMan.builder()
                    .realName(realName != null ? realName.toString() : null)
                    .mobilePhone(mobilePhone != null ? mobilePhone.toString() : null)
                    .build();
        } catch (Exception e) {
            log.error("转换联系人失败", e);
            return CustomerCrmRequest.LinkMan.builder().build();
        }
    }
    
    /**
     * 通过反射或Map方式获取字段值
     */
    private Object getFieldValue(Object obj, String fieldName) {
        try {
            // 如果是Map类型（JSON反序列化的默认类型）
            if (obj instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) obj;
                return dataMap.get(fieldName);
            }
            
            // 如果是实体类，使用反射获取字段
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            log.info("获取字段值失败: fieldName={}, error={}, 对象类型={}",
                    fieldName, e.getMessage(), obj.getClass().getSimpleName());
            return null;
        }
    }
}