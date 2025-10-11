#!/bin/bash

# ========================================
# Automated Deployment Script for Sync Application
# ========================================
# This script provides automated deployment functionality with:
# - Multi-environment support (dev/prod)
# - Code update verification
# - Health checks and rollback capabilities
# - Graceful service restart
# - Comprehensive logging
#
# Usage:
#   ./deploy.sh [ENVIRONMENT] [OPTIONS]
#
# Examples:
#   ./deploy.sh dev                    # Deploy to development
#   ./deploy.sh prod                   # Deploy to production
#   ./deploy.sh dev --build-only       # Build without deployment
#   ./deploy.sh prod --skip-tests      # Skip tests during build
#   ./deploy.sh dev --with-tools       # Include development tools
#
# Author: DevOps Team
# Version: 1.0.0
# ========================================

set -euo pipefail  # Exit on error, undefined vars, pipe failures

# ========================================
# Configuration and Constants
# ========================================
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_NAME="sync-app"
readonly LOG_FILE="${SCRIPT_DIR}/logs/deploy-$(date +%Y%m%d-%H%M%S).log"
readonly BACKUP_DIR="${SCRIPT_DIR}/backups"
readonly MAX_WAIT_TIME=300  # Maximum wait time for health checks (seconds)
readonly HEALTH_CHECK_INTERVAL=10  # Health check interval (seconds)

# Color codes for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# Default values
ENVIRONMENT=""
BUILD_ONLY=false
SKIP_TESTS=false
WITH_TOOLS=false
FORCE_DEPLOY=false
SKIP_BACKUP=false

# ========================================
# Utility Functions
# ========================================

# Print colored output
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1" | tee -a "$LOG_FILE"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "$LOG_FILE"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1" | tee -a "$LOG_FILE"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1" | tee -a "$LOG_FILE"
}

# Create necessary directories
setup_directories() {
    mkdir -p "$(dirname "$LOG_FILE")"
    mkdir -p "$BACKUP_DIR"
    mkdir -p "${SCRIPT_DIR}/docker/mysql"
    mkdir -p "${SCRIPT_DIR}/docker/redis"
    mkdir -p "${SCRIPT_DIR}/docker/nginx"
    mkdir -p "${SCRIPT_DIR}/docker/prometheus"
    mkdir -p "${SCRIPT_DIR}/docker/grafana/dashboards"
    mkdir -p "${SCRIPT_DIR}/docker/grafana/datasources"
    mkdir -p "${SCRIPT_DIR}/docker/filebeat"
}

# Display usage information
show_usage() {
    cat << EOF
Usage: $0 [ENVIRONMENT] [OPTIONS]

ENVIRONMENT:
    dev         Deploy to development environment
    prod        Deploy to production environment

OPTIONS:
    --build-only        Build Docker images without deployment
    --skip-tests        Skip Maven tests during build
    --with-tools        Include development tools (dev environment only)
    --force             Force deployment without confirmation
    --skip-backup       Skip backup creation (not recommended for prod)
    --help              Show this help message

EXAMPLES:
    $0 dev                          # Standard development deployment
    $0 prod                         # Standard production deployment
    $0 dev --with-tools             # Development with management tools
    $0 prod --force --skip-tests    # Production deployment, skip tests and confirmation

PREREQUISITES:
    - Docker and Docker Compose installed
    - .env file configured (copy from .env.template)
    - Maven installed (for building)
    - Git repository up to date

EOF
}

# Parse command line arguments
parse_arguments() {
    if [[ $# -eq 0 ]]; then
        print_error "No environment specified"
        show_usage
        exit 1
    fi

    ENVIRONMENT="$1"
    shift

    while [[ $# -gt 0 ]]; do
        case $1 in
            --build-only)
                BUILD_ONLY=true
                shift
                ;;
            --skip-tests)
                SKIP_TESTS=true
                shift
                ;;
            --with-tools)
                WITH_TOOLS=true
                shift
                ;;
            --force)
                FORCE_DEPLOY=true
                shift
                ;;
            --skip-backup)
                SKIP_BACKUP=true
                shift
                ;;
            --help)
                show_usage
                exit 0
                ;;
            *)
                print_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done

    # Validate environment
    if [[ "$ENVIRONMENT" != "dev" && "$ENVIRONMENT" != "prod" ]]; then
        print_error "Invalid environment: $ENVIRONMENT. Must be 'dev' or 'prod'"
        exit 1
    fi

    # Validate tool options
    if [[ "$WITH_TOOLS" == true && "$ENVIRONMENT" == "prod" ]]; then
        print_warning "Development tools are not recommended for production environment"
        WITH_TOOLS=false
    fi
}

# Check prerequisites
check_prerequisites() {
    print_info "Checking prerequisites..."

    # Check Docker
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed or not in PATH"
        exit 1
    fi

    # Check Docker Compose
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        print_error "Docker Compose is not installed or not in PATH"
        exit 1
    fi

    # Check Maven
    if ! command -v mvn &> /dev/null; then
        print_error "Maven is not installed or not in PATH"
        exit 1
    fi

    # Check Git
    if ! command -v git &> /dev/null; then
        print_error "Git is not installed or not in PATH"
        exit 1
    fi

    # Check environment file
    if [[ ! -f "${SCRIPT_DIR}/.env" ]]; then
        print_error ".env file not found. Please copy .env.template to .env and configure it"
        exit 1
    fi

    # Check Docker Compose file
    local compose_file="${SCRIPT_DIR}/docker-compose-${ENVIRONMENT}.yml"
    if [[ ! -f "$compose_file" ]]; then
        print_error "Docker Compose file not found: $compose_file"
        exit 1
    fi

    print_success "All prerequisites satisfied"
}

# Check for code updates
check_code_updates() {
    print_info "Checking for code updates..."

    # Check if we're in a git repository
    if [[ ! -d "${SCRIPT_DIR}/.git" ]]; then
        print_warning "Not a git repository. Skipping update check"
        return 0
    fi

    # Fetch latest changes
    git fetch origin

    # Check if local branch is behind remote
    local local_commit=$(git rev-parse HEAD)
    local remote_commit=$(git rev-parse @{u} 2>/dev/null || echo "$local_commit")

    if [[ "$local_commit" != "$remote_commit" ]]; then
        print_warning "Local code is not up to date with remote repository"
        print_info "Local commit: $local_commit"
        print_info "Remote commit: $remote_commit"
        
        if [[ "$FORCE_DEPLOY" != true ]]; then
            read -p "Do you want to continue with deployment? (y/N): " -n 1 -r
            echo
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                print_info "Deployment cancelled. Please update your code and try again"
                exit 0
            fi
        fi
    else
        print_success "Code is up to date"
    fi
}

# Create backup of current deployment
create_backup() {
    if [[ "$SKIP_BACKUP" == true ]]; then
        print_info "Skipping backup creation"
        return 0
    fi

    print_info "Creating backup of current deployment..."

    local backup_name="${PROJECT_NAME}-${ENVIRONMENT}-$(date +%Y%m%d-%H%M%S)"
    local backup_path="${BACKUP_DIR}/${backup_name}"

    mkdir -p "$backup_path"

    # Backup Docker Compose file
    if [[ -f "${SCRIPT_DIR}/docker-compose-${ENVIRONMENT}.yml" ]]; then
        cp "${SCRIPT_DIR}/docker-compose-${ENVIRONMENT}.yml" "$backup_path/"
    fi

    # Backup environment file
    if [[ -f "${SCRIPT_DIR}/.env" ]]; then
        cp "${SCRIPT_DIR}/.env" "$backup_path/"
    fi

    # Backup application configuration
    if [[ -d "${SCRIPT_DIR}/src/main/resources" ]]; then
        cp -r "${SCRIPT_DIR}/src/main/resources" "$backup_path/"
    fi

    # Export current Docker images
    local current_image=$(docker images --format "table {{.Repository}}:{{.Tag}}" | grep "$PROJECT_NAME" | head -1 || echo "")
    if [[ -n "$current_image" ]]; then
        print_info "Backing up Docker image: $current_image"
        docker save "$current_image" | gzip > "${backup_path}/docker-image.tar.gz"
    fi

    print_success "Backup created: $backup_path"
    echo "$backup_path" > "${SCRIPT_DIR}/.last_backup"
}

# Build application
build_application() {
    print_info "Building application..."

    cd "$SCRIPT_DIR"

    # Clean previous builds
    print_info "Cleaning previous builds..."
    mvn clean

    # Build with or without tests
    local maven_cmd="mvn package -DskipTests=false"
    if [[ "$SKIP_TESTS" == true ]]; then
        maven_cmd="mvn package -DskipTests=true"
        print_warning "Skipping tests as requested"
    fi

    print_info "Running Maven build: $maven_cmd"
    if ! $maven_cmd; then
        print_error "Maven build failed"
        exit 1
    fi

    print_success "Application built successfully"
}

# Build Docker images
build_docker_images() {
    print_info "Building Docker images..."

    cd "$SCRIPT_DIR"

    # Create Dockerfile if it doesn't exist
    create_dockerfile

    # Build main application image
    local image_tag="${PROJECT_NAME}:${ENVIRONMENT}-$(date +%Y%m%d-%H%M%S)"
    local latest_tag="${PROJECT_NAME}:${ENVIRONMENT}-latest"

    print_info "Building Docker image: $image_tag"
    docker build -t "$image_tag" -t "$latest_tag" \
        --build-arg SPRING_PROFILE="$ENVIRONMENT" \
        --build-arg BUILD_DATE="$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
        --build-arg VCS_REF="$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')" \
        .

    if [[ $? -ne 0 ]]; then
        print_error "Docker image build failed"
        exit 1
    fi

    # Update .env file with new image tag
    if [[ -f "${SCRIPT_DIR}/.env" ]]; then
        sed -i.bak "s/^APP_VERSION=.*/APP_VERSION=${image_tag##*:}/" "${SCRIPT_DIR}/.env"
    fi

    print_success "Docker images built successfully"
    print_info "Image tags: $image_tag, $latest_tag"
}

# Create Dockerfile
create_dockerfile() {
    if [[ -f "${SCRIPT_DIR}/Dockerfile" ]]; then
        return 0
    fi

    print_info "Creating Dockerfile..."

    cat > "${SCRIPT_DIR}/Dockerfile" << 'EOF'
# Multi-stage build for Spring Boot application
FROM openjdk:21-jdk-slim as builder

# Set working directory
WORKDIR /app

# Copy Maven files
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build application
RUN ./mvnw package -DskipTests

# Production stage
FROM openjdk:21-jre-slim

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Set working directory
WORKDIR /app

# Copy built application
COPY --from=builder /app/target/*.jar app.jar

# Copy configuration files
COPY --from=builder /app/src/main/resources/ /app/config/

# Create logs directory
RUN mkdir -p /app/logs && chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose ports
EXPOSE 8080 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# Set JVM options and run application
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS $JAVA_TOOL_OPTIONS -jar app.jar"]
EOF

    print_success "Dockerfile created"
}

# Deploy services
deploy_services() {
    if [[ "$BUILD_ONLY" == true ]]; then
        print_info "Build-only mode. Skipping deployment"
        return 0
    fi

    print_info "Deploying services for $ENVIRONMENT environment..."

    cd "$SCRIPT_DIR"

    # Load environment variables
    set -a
    source .env
    set +a

    # Prepare Docker Compose command
    local compose_file="docker-compose-${ENVIRONMENT}.yml"
    local compose_cmd="docker-compose -f $compose_file"

    # Add profiles if needed
    local profiles=""
    if [[ "$WITH_TOOLS" == true ]]; then
        profiles="--profile tools"
    fi
    if [[ "$ENVIRONMENT" == "prod" ]]; then
        profiles="$profiles --profile monitoring --profile logging"
    fi

    # Stop existing services gracefully
    print_info "Stopping existing services..."
    $compose_cmd down --remove-orphans

    # Pull latest images for external services
    print_info "Pulling latest images..."
    $compose_cmd pull

    # Start services
    print_info "Starting services..."
    $compose_cmd up -d $profiles

    if [[ $? -ne 0 ]]; then
        print_error "Failed to start services"
        rollback_deployment
        exit 1
    fi

    print_success "Services started successfully"
}

# Perform health checks
perform_health_checks() {
    if [[ "$BUILD_ONLY" == true ]]; then
        return 0
    fi

    print_info "Performing health checks..."

    local compose_file="docker-compose-${ENVIRONMENT}.yml"
    local app_container="${PROJECT_NAME}-${ENVIRONMENT}"
    local waited=0

    # Wait for application to be healthy
    while [[ $waited -lt $MAX_WAIT_TIME ]]; do
        if docker-compose -f "$compose_file" ps | grep -q "healthy"; then
            print_success "Application is healthy"
            return 0
        fi

        print_info "Waiting for application to become healthy... (${waited}s/${MAX_WAIT_TIME}s)"
        sleep $HEALTH_CHECK_INTERVAL
        waited=$((waited + HEALTH_CHECK_INTERVAL))
    done

    print_error "Health check failed after ${MAX_WAIT_TIME} seconds"
    
    # Show container logs for debugging
    print_info "Container logs:"
    docker-compose -f "$compose_file" logs --tail=50 sync-app

    rollback_deployment
    exit 1
}

# Rollback deployment
rollback_deployment() {
    print_warning "Rolling back deployment..."

    if [[ ! -f "${SCRIPT_DIR}/.last_backup" ]]; then
        print_error "No backup found for rollback"
        return 1
    fi

    local backup_path=$(cat "${SCRIPT_DIR}/.last_backup")
    if [[ ! -d "$backup_path" ]]; then
        print_error "Backup directory not found: $backup_path"
        return 1
    fi

    # Stop current services
    docker-compose -f "docker-compose-${ENVIRONMENT}.yml" down

    # Restore backup files
    if [[ -f "${backup_path}/docker-compose-${ENVIRONMENT}.yml" ]]; then
        cp "${backup_path}/docker-compose-${ENVIRONMENT}.yml" "${SCRIPT_DIR}/"
    fi

    if [[ -f "${backup_path}/.env" ]]; then
        cp "${backup_path}/.env" "${SCRIPT_DIR}/"
    fi

    # Restore Docker image
    if [[ -f "${backup_path}/docker-image.tar.gz" ]]; then
        print_info "Restoring Docker image..."
        docker load < "${backup_path}/docker-image.tar.gz"
    fi

    # Restart services
    docker-compose -f "docker-compose-${ENVIRONMENT}.yml" up -d

    print_success "Rollback completed"
}

# Cleanup old backups and images
cleanup() {
    print_info "Cleaning up old backups and images..."

    # Keep only last 5 backups
    if [[ -d "$BACKUP_DIR" ]]; then
        find "$BACKUP_DIR" -maxdepth 1 -type d -name "${PROJECT_NAME}-${ENVIRONMENT}-*" | \
            sort -r | tail -n +6 | xargs -r rm -rf
    fi

    # Remove dangling Docker images
    docker image prune -f

    # Remove old application images (keep last 3)
    docker images --format "table {{.Repository}}:{{.Tag}}\t{{.CreatedAt}}" | \
        grep "$PROJECT_NAME" | sort -k2 -r | tail -n +4 | \
        awk '{print $1}' | xargs -r docker rmi

    print_success "Cleanup completed"
}

# Display deployment summary
show_deployment_summary() {
    print_success "Deployment Summary"
    echo "===========================================" | tee -a "$LOG_FILE"
    echo "Environment: $ENVIRONMENT" | tee -a "$LOG_FILE"
    echo "Deployment Time: $(date)" | tee -a "$LOG_FILE"
    echo "Build Only: $BUILD_ONLY" | tee -a "$LOG_FILE"
    echo "Skip Tests: $SKIP_TESTS" | tee -a "$LOG_FILE"
    echo "With Tools: $WITH_TOOLS" | tee -a "$LOG_FILE"
    echo "Log File: $LOG_FILE" | tee -a "$LOG_FILE"

    if [[ "$BUILD_ONLY" != true ]]; then
        echo "" | tee -a "$LOG_FILE"
        echo "Service Status:" | tee -a "$LOG_FILE"
        docker-compose -f "docker-compose-${ENVIRONMENT}.yml" ps | tee -a "$LOG_FILE"

        echo "" | tee -a "$LOG_FILE"
        echo "Access URLs:" | tee -a "$LOG_FILE"
        if [[ "$ENVIRONMENT" == "dev" ]]; then
            echo "  Application: http://localhost:8080" | tee -a "$LOG_FILE"
            echo "  Management: http://localhost:8081/actuator" | tee -a "$LOG_FILE"
            if [[ "$WITH_TOOLS" == true ]]; then
                echo "  Redis Commander: http://localhost:8082" | tee -a "$LOG_FILE"
                echo "  phpMyAdmin: http://localhost:8083" | tee -a "$LOG_FILE"
            fi
        else
            echo "  Application: https://your-domain.com" | tee -a "$LOG_FILE"
            echo "  Monitoring: http://localhost:3000 (Grafana)" | tee -a "$LOG_FILE"
            echo "  Metrics: http://localhost:9090 (Prometheus)" | tee -a "$LOG_FILE"
        fi
    fi

    echo "===========================================" | tee -a "$LOG_FILE"
}

# Main deployment function
main() {
    print_info "Starting deployment process..."
    print_info "Script: $0"
    print_info "Arguments: $*"
    print_info "Log file: $LOG_FILE"

    # Setup
    setup_directories
    parse_arguments "$@"
    check_prerequisites
    check_code_updates

    # Confirmation for production
    if [[ "$ENVIRONMENT" == "prod" && "$FORCE_DEPLOY" != true ]]; then
        print_warning "You are about to deploy to PRODUCTION environment"
        read -p "Are you sure you want to continue? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_info "Deployment cancelled"
            exit 0
        fi
    fi

    # Deployment steps
    create_backup
    build_application
    build_docker_images
    deploy_services
    perform_health_checks
    cleanup
    show_deployment_summary

    print_success "Deployment completed successfully!"
}

# Trap signals for cleanup
trap 'print_error "Deployment interrupted"; exit 1' INT TERM

# Run main function
main "$@"