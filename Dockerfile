# 多阶段构建 - 第一阶段：Maven构建
FROM maven:3.9.8-eclipse-temurin-21 AS builder

# 设置工作目录
WORKDIR /app

# 复制pom.xml和Maven wrapper文件
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw.cmd .

# 下载依赖（利用Docker缓存层）
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src src

# 构建应用
RUN mvn clean package -DskipTests -B

# 多阶段构建 - 第二阶段：运行时镜像
FROM openjdk:21

# 设置工作目录
WORKDIR /app

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 安装curl用于健康检查
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# 创建应用用户
RUN groupadd -r appuser && useradd -r -g appuser appuser

# 从构建阶段复制jar文件
COPY --from=builder /app/target/sync-*.jar app.jar

# 创建日志目录并设置权限
RUN mkdir -p /app/logs && chown -R appuser:appuser /app

# 切换到应用用户
USER appuser

# 暴露应用端口
EXPOSE 8061

# JVM参数优化（适合4G内存服务器）
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -Djava.security.egd=file:/dev/./urandom"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=dev"]