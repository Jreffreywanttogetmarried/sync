/*
 Navicat Premium Data Transfer

 Source Server         : sync
 Source Server Type    : MySQL
 Source Server Version : 80029 (8.0.29)
 Source Host           : localhost:3306
 Source Schema         : sync

 Target Server Type    : MySQL
 Target Server Version : 80029 (8.0.29)
 File Encoding         : 65001

 Date: 13/10/2025 16:15:46
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for sync_batch
-- ----------------------------
DROP TABLE IF EXISTS `sync_batch`;
CREATE TABLE `sync_batch`
(
    `id`               bigint                                                       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `batch_id`         varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '批次ID，唯一标识',
    `website_code`     varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '网站代码，用于区分数据来源',
    `data_type`        varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据类型',
    `total_count`      int                                                          NOT NULL DEFAULT 0 COMMENT '批次总数量',
    `success_count`    int                                                          NOT NULL DEFAULT 0 COMMENT '成功数量',
    `failed_count`     int                                                          NOT NULL DEFAULT 0 COMMENT '失败数量',
    `processing_count` int                                                          NOT NULL DEFAULT 0 COMMENT '处理中数量',
    `status`           tinyint                                                      NOT NULL DEFAULT 0 COMMENT '批次状态：0-待处理，1-处理中，2-已完成，3-部分失败',
    `created_at`       timestamp                                                    NULL     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       timestamp                                                    NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `batch_id` (`batch_id` ASC) USING BTREE,
    INDEX `idx_batch_id` (`batch_id` ASC) USING BTREE,
    INDEX `idx_website_code` (`website_code` ASC) USING BTREE,
    INDEX `idx_status` (`status` ASC) USING BTREE,
    INDEX `idx_created_at` (`created_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 83 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '同步批次表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sync_task
-- ----------------------------
DROP TABLE IF EXISTS `sync_task`;
CREATE TABLE `sync_task`
(
    `id`               bigint                                                       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`          varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务ID，唯一标识',
    `batch_id`         varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '批次ID，关联批次表',
    `website_code`     varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '网站代码，用于区分数据来源',
    `data_type`        varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据类型',
    `business_id`      varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务ID，用于去重',
    `sync_data`        json                                                         NOT NULL COMMENT '同步数据，JSON格式（单条客户数据）',
    `status`           tinyint                                                      NOT NULL DEFAULT 0 COMMENT '任务状态：0-待处理，1-处理中，2-成功，3-失败，4-重试中，5-延迟重试',
    `retry_count`      int                                                          NOT NULL DEFAULT 0 COMMENT '重试次数',
    `crm_request_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL     DEFAULT NULL COMMENT 'CRM请求编码',
    `error_msg`        text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci        NULL COMMENT '错误信息',
    `callback_status`  int                                                          NOT NULL DEFAULT 0 COMMENT '回调状态：0-未回调，1-已回调，2-回调失败',
    `created_at`       timestamp                                                    NULL     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       timestamp                                                    NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `task_id` (`task_id` ASC) USING BTREE,
    INDEX `idx_task_id` (`task_id` ASC) USING BTREE,
    INDEX `idx_batch_id` (`batch_id` ASC) USING BTREE,
    INDEX `idx_website_business` (`website_code` ASC, `business_id` ASC) USING BTREE,
    INDEX `idx_status` (`status` ASC) USING BTREE,
    INDEX `idx_crm_request_code` (`crm_request_code` ASC) USING BTREE,
    INDEX `idx_created_at` (`created_at` ASC) USING BTREE,
    INDEX `idx_callback_status` (`callback_status` ASC) USING BTREE,
    CONSTRAINT `sync_task_ibfk_1` FOREIGN KEY (`batch_id`) REFERENCES `sync_batch` (`batch_id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 82 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '同步任务表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
