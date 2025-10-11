-- 同步批次表
CREATE TABLE IF NOT EXISTS sync_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    batch_id VARCHAR(64) UNIQUE NOT NULL COMMENT '批次ID，唯一标识',
    website_code VARCHAR(32) NOT NULL COMMENT '网站代码，用于区分数据来源',
    data_type VARCHAR(32) NOT NULL COMMENT '数据类型',
    total_count INT NOT NULL DEFAULT 0 COMMENT '批次总数量',
    success_count INT NOT NULL DEFAULT 0 COMMENT '成功数量',
    failed_count INT NOT NULL DEFAULT 0 COMMENT '失败数量',
    processing_count INT NOT NULL DEFAULT 0 COMMENT '处理中数量',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '批次状态：0-待处理，1-处理中，2-已完成，3-部分失败',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_batch_id (batch_id),
    INDEX idx_website_code (website_code),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='同步批次表';

-- 同步任务表（单条数据记录）
CREATE TABLE IF NOT EXISTS sync_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id VARCHAR(64) UNIQUE NOT NULL COMMENT '任务ID，唯一标识',
    batch_id VARCHAR(64) NOT NULL COMMENT '批次ID，关联批次表',
    website_code VARCHAR(32) NOT NULL COMMENT '网站代码，用于区分数据来源',
    data_type VARCHAR(32) NOT NULL COMMENT '数据类型',
    business_id VARCHAR(64) NOT NULL COMMENT '业务ID，用于去重',
    sync_data JSON NOT NULL COMMENT '同步数据，JSON格式（单条客户数据）',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '任务状态：0-待处理，1-处理中，2-成功，3-失败',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    crm_request_code VARCHAR(64) COMMENT 'CRM请求编码',
    error_msg TEXT COMMENT '错误信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_task_id (task_id),
    INDEX idx_batch_id (batch_id),
    INDEX idx_website_business (website_code, business_id),
    INDEX idx_status (status),
    INDEX idx_crm_request_code (crm_request_code),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (batch_id) REFERENCES sync_batch(batch_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='同步任务表';

-- 创建数据库（如果不存在）
-- CREATE DATABASE IF NOT EXISTS sync_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;