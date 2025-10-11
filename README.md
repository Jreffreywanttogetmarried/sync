# 数据同步中间服务

## 项目简介

这是一个数据同步中间服务项目，用于接收多个网站的数据并同步到CRM系统。项目具有良好的性能、持久化、日志记录和兼容性，支持大批量数据处理。

## 技术栈

- **框架**: Spring Boot 3.5.6 + JDK 21 + Maven
- **数据库**: MySQL 8 + MyBatis
- **消息队列**: Redis + Redisson
- **重试机制**: Spring Retry + 定时任务
- **HTTP客户端**: WebFlux WebClient

## 核心功能

### 1. 大批量数据处理
- 支持一次性接收大量客户数据
- 自动拆分为单条记录进行处理
- 批量插入数据库优化性能
- 批次管理和统计功能

### 2. CRM集成
- 调用CRM API提交客户数据（单条处理）
- 异步查询执行结果
- 支持重试机制

### 3. 任务管理
- 批次级别的任务管理
- 任务状态跟踪（待处理、处理中、成功、失败）
- 失败任务自动重试
- 任务统计和监控

## 数据库设计

### 批次表 (sync_batch)
用于管理大批量数据的批次信息：
```sql
CREATE TABLE sync_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id VARCHAR(64) UNIQUE NOT NULL,
    website_code VARCHAR(32) NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    processing_count INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 任务表 (sync_task)
用于存储拆分后的单条数据记录：
```sql
CREATE TABLE sync_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) UNIQUE NOT NULL,
    batch_id VARCHAR(64) NOT NULL,
    website_code VARCHAR(32) NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    business_id VARCHAR(64) NOT NULL,
    sync_data JSON NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    crm_request_code VARCHAR(64),
    error_msg TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

## 项目结构

```
src/main/java/com/easydeals/sync/
├── config/          # 配置类
├── controller/      # REST API控制器
├── consumer/        # 消息队列消费者
├── dto/            # 数据传输对象
├── entity/         # 实体类
├── exception/      # 异常处理
├── mapper/         # MyBatis Mapper接口
├── scheduler/      # 定时任务
├── service/        # 业务服务层
└── util/           # 工具类
```

## 处理流程

### 大批量数据处理流程
1. **接收请求** → 创建批次记录 → 数据校验
2. **数据拆分** → 将大批量数据拆分为单条记录 → 去重检查
3. **批量插入** → 优化数据库插入性能 → 发送到消息队列
4. **异步处理** → 消费队列 → 调用CRM API（单条）
5. **状态更新** → 更新任务状态 → 更新批次统计
6. **结果查询** → 定时查询CRM执行结果 → 最终状态更新

### 性能优化特性
- **批量插入**: 使用MyBatis批量插入，默认每100条一批
- **分批处理**: 大数据自动分批，避免内存溢出
- **异步队列**: Redis队列异步处理，提高并发能力
- **连接池**: 数据库和Redis连接池优化

## API接口

### 1. 提交同步任务
```
POST /api/sync/submit
Content-Type: application/json

{
  "websiteCode": "WEBSITE001",
  "dataType": "CUSTOMER",
  "dataList": [
    {
      "businessId": "BIZ001",
      "getTime": "2025-01-08T20:27:01+08:00",
      "cusName": "测试客户",
      "classID": "CLASS001",
      "customize4": "test@example.com",
      "linkManList": [
        {
          "realName": "张三",
          "mobilePhone": "13800000000"
        }
      ]
    }
    // 支持大批量数据...
  ]
}
```

### 2. 查询任务状态
```
GET /api/sync/status/{taskId}
```

### 3. 健康检查
```
GET /api/sync/health
```

## 配置说明

### application.properties 主要配置项

```properties
# 数据库配置
spring.datasource.url=jdbc:mysql://localhost:3306/sync_db
spring.datasource.username=root
spring.datasource.password=123456

# MyBatis配置
mybatis.mapper-locations=classpath:mapper/*.xml
mybatis.type-aliases-package=com.easydeals.sync.entity
mybatis.configuration.map-underscore-to-camel-case=true

# Redis配置
spring.data.redis.host=localhost
spring.data.redis.port=6379

# CRM API配置
crm.api.base-url=https://openapi.kehu51.com/v1/openapi/671139
crm.api.timeout=30000
crm.api.max-retry=3

# 任务配置
task.batch-size=100
```

## 部署说明

### 1. 环境要求
- JDK 21+
- MySQL 8.0+
- Redis 6.0+

### 2. 数据库初始化
执行 `src/main/resources/sql/schema.sql` 创建表结构：
```bash
mysql -u root -p sync_db < src/main/resources/sql/schema.sql
```

### 3. 启动应用
```bash
mvnw.cmd clean package
java -jar target/sync-0.0.1-SNAPSHOT.jar
```

### 4. 验证部署
访问健康检查接口：
```
GET http://localhost:8080/api/sync/health
```

## 性能特性

### 大数据量支持
- **批次管理**: 自动创建批次记录，跟踪整体进度
- **数据拆分**: 将大批量数据拆分为单条记录处理
- **批量插入**: MyBatis批量插入优化，可配置批次大小
- **内存优化**: 分批处理避免大数据量导致的内存问题

### 并发处理
- **异步队列**: Redis队列支持高并发
- **线程池**: 可配置的异步任务执行器
- **连接池**: 数据库和Redis连接池优化

### 监控与统计
- **批次统计**: 实时统计成功、失败、处理中数量
- **任务监控**: 详细的任务状态跟踪
- **性能日志**: 批量操作性能日志记录

## 注意事项

1. **CRM API限制**: CRM API只支持单条数据提交，项目自动拆分处理
2. **数据去重**: 基于网站代码+业务ID进行去重检查
3. **批量优化**: 默认批次大小为100，可根据实际情况调整
4. **重试机制**: 失败任务会自动重试，最多3次
5. **数据清理**: 建议定期清理历史数据以保持性能

## 更新日志

### v2.0 (当前版本)
- ✅ 修复Redis配置中过时的setObjectMapper方法
- ✅ 重构为MyBatis实现，替换JPA
- ✅ 添加批次表和任务表，支持大批量数据处理
- ✅ 优化数据处理架构，支持数据拆分和批量插入
- ✅ 完善错误处理和重试机制