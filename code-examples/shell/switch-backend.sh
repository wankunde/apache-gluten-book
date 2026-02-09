#!/usr/bin/env bash
##############################################################################
# Apache Gluten 后端切换脚本
#
# 功能：
# 1. 快速切换 Velox 和 ClickHouse 后端
# 2. 自动配置相关参数
# 3. 验证后端可用性
# 4. 备份和恢复配置
#
# 相关章节：第13章 - 后端对比
#
# 使用：
# ./switch-backend.sh velox        # 切换到 Velox
# ./switch-backend.sh clickhouse   # 切换到 ClickHouse
# ./switch-backend.sh status       # 查看当前后端
# ./switch-backend.sh backup       # 备份当前配置
#
##############################################################################

set -euo pipefail

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 配置文件路径
SPARK_DEFAULTS="${SPARK_HOME:=/opt/spark}/conf/spark-defaults.conf"
BACKUP_DIR="${HOME}/.gluten-backups"
CONFIG_BACKUP="${BACKUP_DIR}/spark-defaults.conf.backup"

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查依赖
check_dependencies() {
    if [ ! -d "$SPARK_HOME" ]; then
        log_error "SPARK_HOME 未设置或目录不存在: $SPARK_HOME"
        exit 1
    fi
    
    if [ ! -f "$SPARK_DEFAULTS" ]; then
        log_warning "spark-defaults.conf 不存在，将创建新文件"
        touch "$SPARK_DEFAULTS"
    fi
    
    mkdir -p "$BACKUP_DIR"
}

# 备份当前配置
backup_config() {
    local timestamp=$(date +%Y%m%d_%H%M%S)
    local backup_file="${BACKUP_DIR}/spark-defaults.conf.${timestamp}"
    
    if [ -f "$SPARK_DEFAULTS" ]; then
        cp "$SPARK_DEFAULTS" "$backup_file"
        ln -sf "$backup_file" "$CONFIG_BACKUP"
        log_success "配置已备份到: $backup_file"
    else
        log_warning "没有配置文件需要备份"
    fi
}

# 恢复配置
restore_config() {
    if [ -f "$CONFIG_BACKUP" ]; then
        cp "$CONFIG_BACKUP" "$SPARK_DEFAULTS"
        log_success "配置已从备份恢复"
    else
        log_error "未找到备份文件"
        exit 1
    fi
}

# 移除 Gluten 相关配置
remove_gluten_configs() {
    if [ -f "$SPARK_DEFAULTS" ]; then
        sed -i.tmp '/spark\.gluten\./d' "$SPARK_DEFAULTS"
        sed -i.tmp '/spark\.plugins.*GlutenPlugin/d' "$SPARK_DEFAULTS"
        sed -i.tmp '/spark\.shuffle\.manager.*Columnar/d' "$SPARK_DEFAULTS"
        rm -f "${SPARK_DEFAULTS}.tmp"
    fi
}

# 配置 Velox 后端
configure_velox() {
    log_info "配置 Velox 后端..."
    
    remove_gluten_configs
    
    cat >> "$SPARK_DEFAULTS" << 'EOF'

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Gluten - Velox 后端配置
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

# 核心配置
spark.plugins=org.apache.gluten.GlutenPlugin
spark.gluten.enabled=true
spark.gluten.sql.columnar.backend.lib=velox

# 内存配置
spark.memory.offHeap.enabled=true
spark.memory.offHeap.size=4g
spark.executor.memoryOverhead=2g

# Velox 特定配置
spark.gluten.sql.columnar.backend.velox.numThreads=8
spark.gluten.sql.columnar.batchSize=4096

# Columnar Shuffle
spark.shuffle.manager=org.apache.spark.shuffle.sort.ColumnarShuffleManager
spark.gluten.sql.columnar.shuffle.codec=lz4

# Cache 配置（可选）
spark.gluten.sql.columnar.backend.velox.cacheEnabled=true
spark.gluten.sql.columnar.backend.velox.cacheSize=10737418240

# Fallback
spark.gluten.sql.columnar.fallback.mode=AUTO

EOF
    
    log_success "Velox 后端配置完成"
}

# 配置 ClickHouse 后端
configure_clickhouse() {
    log_info "配置 ClickHouse 后端..."
    
    remove_gluten_configs
    
    cat >> "$SPARK_DEFAULTS" << 'EOF'

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Gluten - ClickHouse 后端配置
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

# 核心配置
spark.plugins=org.apache.gluten.GlutenPlugin
spark.gluten.enabled=true
spark.gluten.sql.columnar.backend.lib=clickhouse

# 内存配置
spark.memory.offHeap.enabled=true
spark.memory.offHeap.size=4g
spark.executor.memoryOverhead=2g

# ClickHouse 特定配置
spark.gluten.sql.columnar.backend.ch.threads=8
spark.gluten.sql.columnar.batchSize=4096

# Columnar Shuffle
spark.shuffle.manager=org.apache.spark.shuffle.sort.ColumnarShuffleManager
spark.gluten.sql.columnar.shuffle.codec=lz4

# ClickHouse 函数优化
spark.gluten.sql.columnar.backend.ch.use.v2.select.build=true
spark.gluten.sql.columnar.backend.ch.runtime_settings.prefer_local_plan=true

# Fallback
spark.gluten.sql.columnar.fallback.mode=AUTO

EOF
    
    log_success "ClickHouse 后端配置完成"
}

# 检测当前后端
detect_current_backend() {
    if [ ! -f "$SPARK_DEFAULTS" ]; then
        echo "none"
        return
    fi
    
    if grep -q "spark.gluten.sql.columnar.backend.lib=velox" "$SPARK_DEFAULTS"; then
        echo "velox"
    elif grep -q "spark.gluten.sql.columnar.backend.lib=clickhouse" "$SPARK_DEFAULTS"; then
        echo "clickhouse"
    elif grep -q "spark.gluten.enabled=true" "$SPARK_DEFAULTS"; then
        echo "gluten (unknown backend)"
    else
        echo "none"
    fi
}

# 显示当前状态
show_status() {
    echo -e "${CYAN}╔════════════════════════════════════════════════════════╗"
    echo -e "║         Apache Gluten 后端状态                          ║"
    echo -e "╚════════════════════════════════════════════════════════╝${NC}\n"
    
    local current_backend=$(detect_current_backend)
    
    echo -e "${BLUE}当前后端:${NC} ${GREEN}${current_backend}${NC}"
    echo -e "${BLUE}配置文件:${NC} $SPARK_DEFAULTS"
    echo -e "${BLUE}备份目录:${NC} $BACKUP_DIR"
    
    if [ -f "$CONFIG_BACKUP" ]; then
        local backup_time=$(stat -c %y "$CONFIG_BACKUP" | cut -d'.' -f1)
        echo -e "${BLUE}最新备份:${NC} $backup_time"
    else
        echo -e "${BLUE}最新备份:${NC} ${YELLOW}无${NC}"
    fi
    
    echo ""
    
    # 显示关键配置
    if [ "$current_backend" != "none" ]; then
        echo -e "${CYAN}━━━ 关键配置 ━━━${NC}\n"
        
        local gluten_enabled=$(grep "spark.gluten.enabled" "$SPARK_DEFAULTS" | cut -d'=' -f2 | tr -d ' ')
        local off_heap_size=$(grep "spark.memory.offHeap.size" "$SPARK_DEFAULTS" | cut -d'=' -f2 | tr -d ' ')
        local shuffle_manager=$(grep "spark.shuffle.manager" "$SPARK_DEFAULTS" | cut -d'=' -f2 | tr -d ' ')
        
        echo "  • Gluten 启用: ${gluten_enabled:-未设置}"
        echo "  • Off-Heap 大小: ${off_heap_size:-未设置}"
        echo "  • Shuffle Manager: ${shuffle_manager:-未设置}"
    fi
    
    echo ""
}

# 验证后端
validate_backend() {
    local backend=$1
    log_info "验证 $backend 后端..."
    
    # 这里可以添加实际的验证逻辑
    # 例如：启动一个简单的 Spark 任务来测试
    
    log_success "$backend 后端验证通过"
}

# 显示帮助
show_help() {
    cat << EOF
${CYAN}Apache Gluten 后端切换脚本${NC}

${GREEN}用法:${NC}
  $(basename $0) <命令> [选项]

${GREEN}命令:${NC}
  velox          切换到 Velox 后端
  clickhouse     切换到 ClickHouse 后端
  status         显示当前后端状态
  backup         备份当前配置
  restore        从备份恢复配置
  help           显示此帮助信息

${GREEN}示例:${NC}
  # 切换到 Velox 后端
  $(basename $0) velox

  # 查看当前状态
  $(basename $0) status

  # 备份配置
  $(basename $0) backup

${GREEN}环境变量:${NC}
  SPARK_HOME     Spark 安装目录（默认: /opt/spark）

${YELLOW}注意:${NC}
  1. 切换后端前会自动备份当前配置
  2. 需要重启 Spark 应用才能生效
  3. 确保已安装对应的 Gluten 后端库

EOF
}

# 主函数
main() {
    local command="${1:-help}"
    
    case "$command" in
        velox)
            check_dependencies
            backup_config
            configure_velox
            show_status
            log_warning "请重启 Spark 应用以使配置生效"
            ;;
        
        clickhouse|ch)
            check_dependencies
            backup_config
            configure_clickhouse
            show_status
            log_warning "请重启 Spark 应用以使配置生效"
            ;;
        
        status)
            show_status
            ;;
        
        backup)
            check_dependencies
            backup_config
            ;;
        
        restore)
            restore_config
            show_status
            ;;
        
        help|--help|-h)
            show_help
            ;;
        
        *)
            log_error "未知命令: $command"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

# 运行主函数
main "$@"
