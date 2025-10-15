# Sync应用 Dev环境 Docker部署指南

## 概述
本项目使用Docker Compose管理dev测试环境的容器化部署，包含Spring Boot应用、MySQL数据库和Redis缓存服务。
**无需在服务器上安装Maven和JDK**，所有构建过程都在Docker容器内完成。

## 系统要求
- Linux服务器（4核心4G内存）
- Docker 20.0+
- Docker Compose 2.0+
- Node.js 14+ 和 npm 6+（用于执行部署脚本）

## 服务架构
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Spring Boot   │    │      MySQL      │    │      Redis      │
│  sync-test-app  │    │ sync-test-mysql │    │ sync-test-redis │
│    Port: 8061   │    │    Port: 3307   │    │    Port: 6380   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 容器配置
所有容器都使用 `sync-test-` 前缀命名：
- **sync-test-app**: Spring Boot应用容器
- **sync-test-mysql**: MySQL 8.0数据库容器
- **sync-test-redis**: Redis 7缓存容器

## 配置文件
- **docker-compose-dev.yml**: Dev环境的Docker Compose配置文件
- **Dockerfile**: Spring Boot应用的Docker镜像构建文件（多阶段构建）

## 快速部署

### 1. 一键部署（推荐）
```bash
# 执行完整部署流程：Docker构建 -> 部署
npm run deploy
```

### 2. 分步部署
```bash
# 1. 构建Docker镜像（包含Maven构建）
npm run docker:build

# 2. 启动所有服务
npm run docker:up
```

## Docker多阶段构建说明

本项目使用Docker多阶段构建技术：

### 第一阶段：Maven构建
- 使用 `maven:3.9.6-openjdk-21-slim` 镜像
- 在容器内完成Java应用的编译和打包
- 利用Docker缓存层优化构建速度

### 第二阶段：运行时镜像
- 使用 `openjdk:21-jdk-slim` 镜像
- 只包含运行时必需的文件
- 镜像体积更小，安全性更高

## 常用命令

### 部署相关
```bash
npm run deploy          # 完整部署流程
npm run docker:build    # 构建Docker镜像（包含Maven构建）
npm run docker:up       # 启动所有服务
npm run docker:down     # 停止所有服务
npm run docker:restart  # 重启所有服务
```

### 监控相关
```bash
npm run docker:status  # 查看容器状态
npm run docker:logs    # 查看所有服务日志
npm run docker:logs:app    # 查看应用日志
npm run docker:logs:mysql  # 查看MySQL日志
npm run docker:logs:redis  # 查看Redis日志
npm run health:check   # 检查应用健康状态
```

### 维护相关
```bash
npm run docker:clean   # 清理所有容器和数据卷（谨慎使用）
npm run help          # 显示所有可用命令
```

## 端口映射
- **应用服务**: http://localhost:8061
- **MySQL数据库**: localhost:3307
- **Redis缓存**: localhost:6380

## 数据持久化
项目配置了以下数据卷确保数据持久化：
- `sync-test-mysql-data`: MySQL数据存储
- `sync-test-redis-data`: Redis数据存储  
- `sync-test-app-logs`: 应用日志存储

## 配置文件说明
- `application-dev.properties`: Dev环境Spring Boot配置
- `redisson-dev.yml`: Dev环境Redisson配置
- `docker/mysql/conf.d/my.cnf`: MySQL优化配置
- `docker/redis/redis.conf`: Redis优化配置

## 健康检查
系统配置了健康检查机制：
- MySQL: 每10秒检查数据库连接
- Redis: 每5秒检查Redis连接
- 应用: 每30秒检查应用健康状态

## 故障排除

### 1. 端口冲突
如果遇到端口冲突，可以修改 `docker-compose.yml` 中的端口映射。

### 2. 内存不足
4G内存服务器的JVM参数已优化，如需调整可修改 `Dockerfile` 中的 `JAVA_OPTS`。

### 3. 数据库连接失败
检查MySQL容器是否正常启动：
```bash
npm run docker:logs:mysql
```

### 4. Redis连接失败
检查Redis容器状态：
```bash
npm run docker:logs:redis
```

### 5. 应用启动失败
查看应用日志：
```bash
npm run docker:logs:app
```

## 注意事项
1. 首次部署可能需要较长时间下载Docker镜像
2. 确保服务器有足够的磁盘空间存储镜像和数据
3. 生产环境部署前请修改默认密码
4. 定期备份数据卷中的重要数据

## 联系支持
如遇到部署问题，请联系开发团队或查看项目文档。