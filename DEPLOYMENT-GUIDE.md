# 🚀 Sync项目部署与发版操作指南

## 📋 常用操作命令速查表

### 🔧 基础服务管理
```bash
# 查看服务状态
npm run docker:status

# 查看服务日志
npm run docker:logs

# 查看应用健康状态
npm run health:check
```

### 🛑 关闭服务
```bash
# 停止服务（保留容器和数据）
npm run docker:stop

# 完全关闭服务（删除容器，保留数据卷）
npm run docker:down

# 完全清理（删除容器、镜像、数据卷）
npm run docker:clean
```

### 🔄 重启服务
```bash
# 重启服务（不重新构建）
npm run docker:restart

# 重新部署（重新构建镜像）
npm run docker:redeploy
```

## 🎯 发版流程

### 方式一：快速发版（推荐）
```bash
# 一键重新部署新版本
npm run docker:redeploy
```

### 方式二：分步发版
```bash
# 1. 停止当前服务
npm run docker:stop

# 2. 重新构建镜像
npm run docker:build

# 3. 启动新版本
npm run docker:up

# 4. 检查服务状态
npm run docker:status
npm run health:check
```

### 方式三：完整重新部署
```bash
# 完全重新部署（包括清理旧容器）
npm run deploy
```

## 📊 服务监控

### 查看日志
```bash
# 查看所有服务日志
npm run docker:logs

# 查看应用日志
npm run docker:logs:app

# 查看MySQL日志
npm run docker:logs:mysql

# 查看Redis日志
npm run docker:logs:redis
```

### 健康检查
```bash
# 检查应用健康状态
npm run health:check

# 查看容器状态
npm run docker:status
```

## 🗂️ 数据管理

### 数据持久化
项目使用Docker数据卷持久化数据：
- `mysql_data`: MySQL数据库文件
- `redis_data`: Redis持久化文件
- `app_logs`: 应用日志文件

### 数据备份
```bash
# 备份MySQL数据
docker-compose -f docker-compose-dev.yml exec sync-test-mysql mysqldump -u root -p123456 sync_test > backup_$(date +%Y%m%d_%H%M%S).sql

# 备份Redis数据
docker-compose -f docker-compose-dev.yml exec sync-test-redis redis-cli BGSAVE
```

### 数据恢复
```bash
# 恢复MySQL数据
docker-compose -f docker-compose-dev.yml exec -T sync-test-mysql mysql -u root -p123456 sync_test < backup_file.sql
```

## 🧹 清理操作

### 清理级别
```bash
# 轻度清理：停止服务，保留数据
npm run docker:stop

# 中度清理：删除容器，保留数据卷和镜像
npm run docker:down

# 重度清理：删除容器、镜像、数据卷
npm run docker:clean

# 完全清理：删除所有Docker资源
npm run docker:clean:all
```

## 🚨 故障排查

### 常见问题
1. **服务启动失败**
   ```bash
   npm run docker:logs:app  # 查看应用日志
   npm run docker:status    # 查看容器状态
   ```

2. **数据库连接失败**
   ```bash
   npm run docker:logs:mysql  # 查看MySQL日志
   # 检查MySQL是否健康启动
   ```

3. **Redis连接失败**
   ```bash
   npm run docker:logs:redis  # 查看Redis日志
   ```

### 紧急恢复
```bash
# 如果服务异常，快速恢复
npm run docker:down
npm run deploy
```

## 📝 发版检查清单

### 发版前检查
- [ ] 代码已提交到版本控制
- [ ] 本地测试通过
- [ ] 数据库迁移脚本准备就绪

### 发版操作
- [ ] 执行 `npm run docker:redeploy`
- [ ] 检查服务状态 `npm run docker:status`
- [ ] 验证健康检查 `npm run health:check`
- [ ] 查看应用日志 `npm run docker:logs:app`

### 发版后验证
- [ ] 应用接口正常响应
- [ ] 数据库连接正常
- [ ] Redis缓存正常
- [ ] 关键业务功能测试通过

## 🔧 高级操作

### 进入容器调试
```bash
# 进入应用容器
docker-compose -f docker-compose-dev.yml exec sync-test-app bash

# 进入MySQL容器
docker-compose -f docker-compose-dev.yml exec sync-test-mysql bash

# 进入Redis容器
docker-compose -f docker-compose-dev.yml exec sync-test-redis bash
```

### 单独重启某个服务
```bash
# 重启应用服务
docker-compose -f docker-compose-dev.yml restart sync-test-app

# 重启MySQL服务
docker-compose -f docker-compose-dev.yml restart sync-test-mysql

# 重启Redis服务
docker-compose -f docker-compose-dev.yml restart sync-test-redis
```