#!/bin/bash
# 文件名：check_environment.sh
# 用途：检查 Gluten 运行环境是否配置正确

echo "========================================="
echo "  Gluten 环境检查工具"
echo "========================================="
echo ""

# 检查 Java
echo "【1/7】检查 Java 环境..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1)
    echo "  ✅ Java 已安装: $JAVA_VERSION"
    if [ -z "$JAVA_HOME" ]; then
        echo "  ⚠️  警告: JAVA_HOME 环境变量未设置"
    else
        echo "  ✅ JAVA_HOME=$JAVA_HOME"
    fi
else
    echo "  ❌ Java 未安装"
    exit 1
fi

# 检查 Spark
echo ""
echo "【2/7】检查 Spark 环境..."
if command -v spark-shell &> /dev/null; then
    SPARK_VERSION=$(spark-shell --version 2>&1 | grep "version" | head -n 1)
    echo "  ✅ Spark 已安装: $SPARK_VERSION"
    if [ -z "$SPARK_HOME" ]; then
        echo "  ⚠️  警告: SPARK_HOME 环境变量未设置"
    else
        echo "  ✅ SPARK_HOME=$SPARK_HOME"
    fi
else
    echo "  ❌ Spark 未安装"
    exit 1
fi

# 检查 Git
echo ""
echo "【3/7】检查 Git..."
if command -v git &> /dev/null; then
    GIT_VERSION=$(git --version)
    echo "  ✅ $GIT_VERSION"
else
    echo "  ⚠️  Git 未安装（源码编译需要）"
fi

# 检查 Maven
echo ""
echo "【4/7】检查 Maven..."
if command -v mvn &> /dev/null; then
    MVN_VERSION=$(mvn --version | head -n 1)
    echo "  ✅ $MVN_VERSION"
else
    echo "  ⚠️  Maven 未安装（源码编译需要）"
fi

# 检查 CMake
echo ""
echo "【5/7】检查 CMake..."
if command -v cmake &> /dev/null; then
    CMAKE_VERSION=$(cmake --version | head -n 1)
    echo "  ✅ $CMAKE_VERSION"
else
    echo "  ⚠️  CMake 未安装（源码编译需要）"
fi

# 检查系统资源
echo ""
echo "【6/7】检查系统资源..."
CPU_CORES=$(nproc)
TOTAL_MEM=$(free -h | grep Mem | awk '{print $2}')
AVAIL_MEM=$(free -h | grep Mem | awk '{print $7}')
DISK_AVAIL=$(df -h . | tail -1 | awk '{print $4}')

echo "  CPU 核心数: $CPU_CORES"
echo "  总内存: $TOTAL_MEM"
echo "  可用内存: $AVAIL_MEM"
echo "  可用磁盘: $DISK_AVAIL"

# 推荐配置检查
if [ "$CPU_CORES" -lt 4 ]; then
    echo "  ⚠️  警告: CPU 核心数少于推荐值 (4核)"
fi

# 检查 Gluten JAR
echo ""
echo "【7/7】检查 Gluten JAR..."
if [ -n "$GLUTEN_JAR" ]; then
    if [ -f "$GLUTEN_JAR" ]; then
        JAR_SIZE=$(ls -lh "$GLUTEN_JAR" | awk '{print $5}')
        echo "  ✅ GLUTEN_JAR=$GLUTEN_JAR"
        echo "  ✅ 文件大小: $JAR_SIZE"
    else
        echo "  ❌ GLUTEN_JAR 指向的文件不存在: $GLUTEN_JAR"
    fi
else
    echo "  ⚠️  GLUTEN_JAR 环境变量未设置"
    echo "     请运行: export GLUTEN_JAR=/path/to/gluten-jar"
fi

echo ""
echo "========================================="
echo "  检查完成"
echo "========================================="
