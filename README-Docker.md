# Docker Deployment Guide for Sync Application

## Table of Contents
- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Environment Configuration](#environment-configuration)
- [Deployment Process](#deployment-process)
- [Service Management](#service-management)
- [Monitoring and Logging](#monitoring-and-logging)
- [Troubleshooting](#troubleshooting)
- [Best Practices](#best-practices)
- [Security Considerations](#security-considerations)

## Overview

This guide provides comprehensive instructions for deploying the Sync Application using Docker and Docker Compose. The deployment supports multiple environments (development and production) with automated deployment scripts and comprehensive monitoring.

### Architecture Components

- **Spring Boot Application**: Main synchronization service
- **MySQL Database**: Data persistence layer
- **Redis Cache**: Caching and session management
- **Nginx**: Load balancer and reverse proxy (production)
- **Monitoring Stack**: Prometheus, Grafana, and log aggregation
- **Development Tools**: Redis Commander, phpMyAdmin (development only)

## Prerequisites

### System Requirements

- **Operating System**: Linux (Ubuntu 20.04+ recommended), macOS, or Windows with WSL2
- **Docker**: Version 20.10 or higher
- **Docker Compose**: Version 2.0 or higher
- **Maven**: Version 3.8 or higher
- **Java**: JDK 21
- **Git**: For code management
- **Minimum Hardware**:
  - Development: 4GB RAM, 2 CPU cores, 20GB disk space
  - Production: 8GB RAM, 4 CPU cores, 100GB disk space

### Software Installation

```bash
# Install Docker (Ubuntu/Debian)
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Install Maven
sudo apt update
sudo apt install maven openjdk-21-jdk

# Verify installations
docker --version
docker-compose --version
mvn --version
java --version
```

## Quick Start

### 1. Clone and Setup

```bash
# Clone the repository
git clone <repository-url>
cd sync-khwy/sync

# Make deployment script executable
chmod +x deploy.sh

# Copy environment template
cp .env.template .env
```

### 2. Configure Environment

Edit the `.env` file with your environment-specific values:

```bash
# Basic configuration for development
SPRING_PROFILES_ACTIVE=dev
DB_USERNAME=sync_dev
DB_PASSWORD=your_secure_password
REDIS_PASSWORD=
CRM_API_URL=https://dev-api.crm.company.com
```

### 3. Deploy

```bash
# Development deployment
./deploy.sh dev

# Development with management tools
./deploy.sh dev --with-tools

# Production deployment
./deploy.sh prod
```

## Environment Configuration

### Development Environment

The development environment includes:
- Single MySQL instance
- Single Redis instance
- Development tools (optional)
- Relaxed security settings
- Detailed logging

**Configuration Files:**
- `docker-compose-dev.yml`: Development services
- `application-dev.properties`: Development application settings
- `redisson-dev.yml`: Development Redis configuration

**Default Ports:**
- Application: 8080
- Management: 8081
- MySQL: 3306
- Redis: 6379
- Redis Commander: 8082 (with --with-tools)
- phpMyAdmin: 8083 (with --with-tools)

### Production Environment

The production environment includes:
- External MySQL database connection
- Redis cluster support
- Nginx load balancer
- SSL/TLS support
- Monitoring and logging stack
- Resource limits and security hardening

**Configuration Files:**
- `docker-compose-prod.yml`: Production services
- `application-prod.properties`: Production application settings
- `redisson-prod.yml`: Production Redis cluster configuration

**Default Ports:**
- HTTP: 80
- HTTPS: 443
- Prometheus: 9090
- Grafana: 3000

## Deployment Process

### Automated Deployment Script

The `deploy.sh` script provides comprehensive deployment automation:

```bash
# Script usage
./deploy.sh [ENVIRONMENT] [OPTIONS]

# Available options
--build-only        # Build Docker images without deployment
--skip-tests        # Skip Maven tests during build
--with-tools        # Include development tools (dev only)
--force             # Force deployment without confirmation
--skip-backup       # Skip backup creation (not recommended for prod)
--help              # Show help message
```

### Deployment Steps

1. **Prerequisites Check**: Verifies Docker, Maven, Git installation
2. **Code Update Check**: Ensures code is up to date with repository
3. **Backup Creation**: Creates backup of current deployment
4. **Application Build**: Compiles Java application with Maven
5. **Docker Image Build**: Creates optimized Docker images
6. **Service Deployment**: Starts all required services
7. **Health Checks**: Verifies application health
8. **Cleanup**: Removes old images and backups

### Manual Deployment

If you prefer manual deployment:

```bash
# 1. Build application
mvn clean package

# 2. Build Docker images
docker build -t sync-app:dev .

# 3. Start services
docker-compose -f docker-compose-dev.yml up -d

# 4. Check status
docker-compose -f docker-compose-dev.yml ps
```

## Service Management

### Starting Services

```bash
# Start all services
docker-compose -f docker-compose-dev.yml up -d

# Start specific service
docker-compose -f docker-compose-dev.yml up -d sync-app

# Start with profiles (production)
docker-compose -f docker-compose-prod.yml --profile monitoring up -d
```

### Stopping Services

```bash
# Stop all services
docker-compose -f docker-compose-dev.yml down

# Stop and remove volumes
docker-compose -f docker-compose-dev.yml down -v

# Stop specific service
docker-compose -f docker-compose-dev.yml stop sync-app
```

### Viewing Logs

```bash
# View all logs
docker-compose -f docker-compose-dev.yml logs

# Follow logs for specific service
docker-compose -f docker-compose-dev.yml logs -f sync-app

# View last 100 lines
docker-compose -f docker-compose-dev.yml logs --tail=100 sync-app
```

### Scaling Services

```bash
# Scale application instances (production)
docker-compose -f docker-compose-prod.yml up -d --scale sync-app=3

# Check scaled services
docker-compose -f docker-compose-prod.yml ps
```

## Monitoring and Logging

### Health Checks

All services include health checks:

```bash
# Check service health
docker-compose -f docker-compose-dev.yml ps

# Application health endpoint
curl http://localhost:8081/actuator/health

# Detailed health information
curl http://localhost:8081/actuator/health/detailed
```

### Monitoring Stack (Production)

**Prometheus Metrics:**
- URL: http://localhost:9090
- Collects application and system metrics
- Configured with alerting rules

**Grafana Dashboards:**
- URL: http://localhost:3000
- Default credentials: admin/admin (change immediately)
- Pre-configured dashboards for application monitoring

**Log Aggregation:**
- Filebeat ships logs to Elasticsearch
- Centralized logging for all services
- Log retention and rotation policies

### Application Metrics

The application exposes metrics via Actuator:

```bash
# Metrics endpoint
curl http://localhost:8081/actuator/metrics

# Specific metric
curl http://localhost:8081/actuator/metrics/jvm.memory.used

# Prometheus format
curl http://localhost:8081/actuator/prometheus
```

## Troubleshooting

### Common Issues

#### 1. Port Conflicts

```bash
# Check port usage
netstat -tulpn | grep :8080

# Change ports in .env file
SERVER_PORT=8090
```

#### 2. Database Connection Issues

```bash
# Check MySQL container
docker-compose -f docker-compose-dev.yml logs mysql-dev

# Test database connection
docker-compose -f docker-compose-dev.yml exec mysql-dev mysql -u sync_dev -p

# Reset database
docker-compose -f docker-compose-dev.yml down -v
docker-compose -f docker-compose-dev.yml up -d mysql-dev
```

#### 3. Redis Connection Issues

```bash
# Check Redis container
docker-compose -f docker-compose-dev.yml logs redis-dev

# Test Redis connection
docker-compose -f docker-compose-dev.yml exec redis-dev redis-cli ping

# Clear Redis data
docker-compose -f docker-compose-dev.yml exec redis-dev redis-cli FLUSHALL
```

#### 4. Application Startup Issues

```bash
# Check application logs
docker-compose -f docker-compose-dev.yml logs sync-app

# Check Java heap space
docker stats

# Increase memory limits in .env
JVM_MAX_HEAP=4g
MEMORY_LIMIT=6G
```

#### 5. Build Issues

```bash
# Clean Maven cache
mvn clean

# Rebuild without cache
docker build --no-cache -t sync-app:dev .

# Check disk space
df -h
```

### Debugging Commands

```bash
# Enter application container
docker-compose -f docker-compose-dev.yml exec sync-app bash

# Check container resources
docker stats

# Inspect container configuration
docker inspect sync-app-dev

# View Docker system information
docker system df
docker system prune
```

### Log Locations

- **Application logs**: `./logs/` (mounted volume)
- **Container logs**: `docker-compose logs`
- **System logs**: `/var/log/` (production)

## Best Practices

### Development

1. **Use Development Tools**: Enable management tools for easier debugging
2. **Volume Mounts**: Use volume mounts for configuration hot-reloading
3. **Resource Limits**: Set appropriate resource limits to prevent system overload
4. **Regular Updates**: Keep Docker images and dependencies updated
5. **Backup Data**: Regularly backup development databases

### Production

1. **Security Hardening**: Use non-root users, enable SSL, secure passwords
2. **Resource Planning**: Monitor resource usage and plan capacity
3. **High Availability**: Use multiple replicas and external databases
4. **Monitoring**: Implement comprehensive monitoring and alerting
5. **Backup Strategy**: Automated backups with retention policies
6. **Update Strategy**: Blue-green or rolling deployments
7. **Network Security**: Use private networks and firewall rules

### Configuration Management

1. **Environment Variables**: Use environment variables for all configuration
2. **Secrets Management**: Use Docker secrets or external secret management
3. **Configuration Validation**: Validate configuration before deployment
4. **Version Control**: Track configuration changes in version control
5. **Documentation**: Keep configuration documentation updated

## Security Considerations

### Container Security

```bash
# Run containers as non-root user
USER 1001:1001

# Remove unnecessary privileges
security_opt:
  - no-new-privileges:true

# Use read-only root filesystem where possible
read_only: true
```

### Network Security

```bash
# Use custom networks
networks:
  sync-network:
    driver: bridge
    internal: true  # No external access

# Limit port exposure
ports:
  - "127.0.0.1:8080:8080"  # Bind to localhost only
```

### Data Security

1. **Encrypt Data at Rest**: Use encrypted volumes
2. **Encrypt Data in Transit**: Enable SSL/TLS
3. **Secure Passwords**: Use strong, unique passwords
4. **Regular Updates**: Keep all components updated
5. **Access Control**: Implement proper authentication and authorization

### Environment Variables Security

```bash
# Use Docker secrets for sensitive data
secrets:
  db_password:
    file: ./secrets/db_password.txt

# Set proper file permissions
chmod 600 .env
```

### Monitoring Security

1. **Secure Monitoring Endpoints**: Use authentication
2. **Log Sanitization**: Remove sensitive data from logs
3. **Audit Logging**: Enable audit trails
4. **Alert on Security Events**: Monitor for suspicious activities

## Advanced Configuration

### Custom Docker Images

Create custom base images for better security and performance:

```dockerfile
# Custom base image
FROM openjdk:21-jre-slim

# Install security updates
RUN apt-get update && apt-get upgrade -y && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Add custom configurations
COPY custom-configs/ /app/config/
```

### Multi-Stage Builds

Optimize image size with multi-stage builds:

```dockerfile
# Build stage
FROM maven:3.8-openjdk-21 as builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src src
RUN mvn package -DskipTests

# Runtime stage
FROM openjdk:21-jre-slim
COPY --from=builder /app/target/*.jar app.jar
```

### External Services Integration

Configure external services for production:

```yaml
# External MySQL
services:
  sync-app:
    environment:
      - DB_URL=jdbc:mysql://external-db.company.com:3306/sync_prod
      - REDIS_CLUSTER_NODES=redis1.company.com:6379,redis2.company.com:6379
```

## Support and Maintenance

### Regular Maintenance Tasks

1. **Update Dependencies**: Monthly security updates
2. **Clean Up Resources**: Remove unused images and volumes
3. **Monitor Performance**: Review metrics and optimize
4. **Backup Verification**: Test backup restoration procedures
5. **Security Audits**: Regular security assessments

### Getting Help

- **Documentation**: Check this README and inline comments
- **Logs**: Review application and container logs
- **Health Checks**: Use built-in health endpoints
- **Community**: Consult Docker and Spring Boot communities
- **Support**: Contact development team for application-specific issues

### Contributing

1. Follow the established patterns in configuration files
2. Test changes in development environment first
3. Update documentation for any configuration changes
4. Use meaningful commit messages
5. Submit pull requests for review

---

**Last Updated**: $(date)
**Version**: 1.0.0
**Maintainer**: DevOps Team