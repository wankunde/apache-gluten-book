# 第2章：快速入门

> **本章要点**：
> - 掌握 Gluten 运行所需的环境和依赖
> - 学会通过预编译包或源码编译获取 Gluten
> - 运行第一个 Gluten 示例程序
> - 理解 Gluten 的核心配置参数
> - 掌握验证和排查方法

## 引言

在了解了 Gluten 的基本概念和价值之后，本章将带你动手实践，从环境准备到运行第一个 Gluten 程序，让你快速体验 Gluten 的强大性能。

本章采用循序渐进的方式，即使是初次接触 Gluten 的读者也能轻松上手。

## 2.1 环境准备与依赖安装

### 2.1.1 系统要求

Gluten 对运行环境有一定要求，以下是推荐的配置：

#### 硬件要求

| 项目 | 最低要求 | 推荐配置 |
|------|----------|----------|
| CPU | 4 核 | 16 核及以上 |
| 内存 | 8 GB | 64 GB 及以上 |
| 磁盘 | 50 GB 可用空间 | 500 GB SSD |
| 架构 | x86_64 或 aarch64 | x86_64 |

**注意**：
- 编译 Gluten 可能需要较大内存（建议 64GB 以上）
- 如果内存不足，编译时可能会因 OOM（内存溢出）失败

#### 操作系统支持

Gluten 目前支持以下 Linux 发行版：

| 发行版 | 版本 | 状态 |
|--------|------|------|
| Ubuntu | 20.04 | ✅ 官方支持 |
| Ubuntu | 22.04 | ✅ 官方支持 |
| CentOS | 7 | ✅ 官方支持 |
| CentOS | 8 | ✅ 官方支持 |
| 其他 Linux | - | ⚠️ 可能支持（静态编译）|

**支持架构**：
- x86_64（主要支持）
- aarch64（ARM64，实验性支持）

### 2.1.2 软件依赖

#### 必需软件

##### 1. Java 开发环境

Gluten 支持 Java 8 和 Java 17：

```bash
# Ubuntu 系统安装 OpenJDK 8
sudo apt-get update
sudo apt-get install -y openjdk-8-jdk

# CentOS 系统安装 OpenJDK 8
sudo yum install -y java-1.8.0-openjdk-devel

# 验证安装
java -version
```

设置 JAVA_HOME 环境变量：

```bash
# x86_64 架构
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# aarch64 架构
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-arm64
export PATH=$JAVA_HOME/bin:$PATH

# 添加到 ~/.bashrc 或 ~/.zshrc 使其永久生效
echo 'export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc
```

##### 2. Apache Spark

Gluten 支持以下 Spark 版本：

| Spark 版本 | 支持状态 |
|-----------|----------|
| 3.2.2 | ✅ 官方支持 |
| 3.3.1 | ✅ 官方支持 |
| 3.4.4 | ✅ 官方支持 |
| 3.5.5 | ✅ 官方支持 |

下载和安装 Spark（以 3.3.1 为例）：

```bash
# 下载 Spark
cd ~
wget https://archive.apache.org/dist/spark/spark-3.3.1/spark-3.3.1-bin-hadoop3.tgz

# 解压
tar -xzf spark-3.3.1-bin-hadoop3.tgz
mv spark-3.3.1-bin-hadoop3 spark-3.3.1

# 设置环境变量
export SPARK_HOME=~/spark-3.3.1
export PATH=$SPARK_HOME/bin:$PATH

# 添加到配置文件
echo 'export SPARK_HOME=~/spark-3.3.1' >> ~/.bashrc
echo 'export PATH=$SPARK_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc

# 验证安装
spark-shell --version
```

##### 3. 编译工具（如果需要从源码编译）

如果你打算从源码编译 Gluten，还需要安装以下工具：

```bash
# Ubuntu 系统
sudo apt-get install -y \
    build-essential \
    cmake \
    git \
    ninja-build \
    ccache \
    libssl-dev \
    libboost-all-dev \
    maven

# CentOS 系统
sudo yum install -y \
    gcc \
    gcc-c++ \
    cmake \
    git \
    ninja-build \
    ccache \
    openssl-devel \
    boost-devel \
    maven
```

##### 4. Maven

用于编译 Gluten 的 Java 模块：

```bash
# Ubuntu
sudo apt-get install -y maven

# CentOS
sudo yum install -y maven

# 验证
mvn --version

# 配置 Maven 镜像（可选，加速下载）
mkdir -p ~/.m2
cat > ~/.m2/settings.xml << 'EOF'
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven Mirror</name>
      <url>https://maven.aliyun.com/repository/central</url>
    </mirror>
  </mirrors>
</settings>
EOF
```

### 2.1.3 存储系统（可选）

如果需要访问远程存储，还需要配置相应的客户端：

#### HDFS 支持

```bash
# 设置 HADOOP_HOME
export HADOOP_HOME=/path/to/hadoop
export PATH=$HADOOP_HOME/bin:$PATH

# Gluten 会自动加载 $HADOOP_HOME/lib/native/libhdfs.so
```

#### S3 支持

不需要额外安装，Gluten 内置 AWS SDK 支持。

#### Azure Blob 存储支持

不需要额外安装，Gluten 内置 Azure SDK 支持。

### 2.1.4 环境验证

创建一个脚本来验证环境是否配置正确：

```bash
#!/bin/bash
# 文件名：check_environment.sh

echo "=== Gluten 环境检查 ==="

# 检查 Java
echo -n "Java: "
if command -v java &> /dev/null; then
    java -version 2>&1 | head -n 1
    if [ -z "$JAVA_HOME" ]; then
        echo "  ⚠️  JAVA_HOME 未设置"
    else
        echo "  ✅ JAVA_HOME=$JAVA_HOME"
    fi
else
    echo "  ❌ Java 未安装"
fi

# 检查 Spark
echo -n "Spark: "
if command -v spark-shell &> /dev/null; then
    spark-shell --version 2>&1 | grep "version" | head -n 1
    if [ -z "$SPARK_HOME" ]; then
        echo "  ⚠️  SPARK_HOME 未设置"
    else
        echo "  ✅ SPARK_HOME=$SPARK_HOME"
    fi
else
    echo "  ❌ Spark 未安装"
fi

# 检查编译工具
echo -n "Git: "
if command -v git &> /dev/null; then
    git --version
else
    echo "  ❌ Git 未安装"
fi

echo -n "Maven: "
if command -v mvn &> /dev/null; then
    mvn --version | head -n 1
else
    echo "  ❌ Maven 未安装"
fi

echo -n "CMake: "
if command -v cmake &> /dev/null; then
    cmake --version | head -n 1
else
    echo "  ⚠️  CMake 未安装（源码编译需要）"
fi

# 检查系统资源
echo "=== 系统资源 ==="
echo "CPU 核心数: $(nproc)"
echo "内存: $(free -h | grep Mem | awk '{print $2}')"
echo "可用磁盘: $(df -h . | tail -1 | awk '{print $4}')"

echo "=== 检查完成 ==="
```

运行检查脚本：

```bash
chmod +x check_environment.sh
./check_environment.sh
```

## 2.2 获取 Gluten

有两种方式获取 Gluten：
1. **下载预编译包**（推荐，快速简单）
2. **从源码编译**（灵活，可定制）

### 2.2.1 下载预编译包（推荐）

Apache Gluten 提供官方发布的预编译包，这是最快速的方式。

#### 下载稳定版本

访问 [Apache Gluten 下载页面](https://downloads.apache.org/incubator/gluten/)：

```bash
# 创建工作目录
mkdir -p ~/gluten
cd ~/gluten

# 下载最新版本（以 1.2.0 为例，请查看官网获取最新版本号）
wget https://downloads.apache.org/incubator/gluten/1.2.0/apache-gluten-1.2.0-bin.tar.gz

# 解压
tar -xzf apache-gluten-1.2.0-bin.tar.gz
cd apache-gluten-1.2.0-bin

# 查看内容
ls -lh jars/
```

预编译包中包含：
- `gluten-velox-bundle-spark3.x_*.jar` - Velox 后端
- `gluten-clickhouse-bundle-spark3.x_*.jar` - ClickHouse 后端

#### 下载每日构建版本（Nightly Build）

如果想尝试最新特性，可以下载每日构建版本：

```bash
# 访问每日构建页面
# https://nightlies.apache.org/gluten/

# 下载示例（选择你的 Spark 版本和日期）
wget https://nightlies.apache.org/gluten/2024-02-09/gluten-velox-bundle-spark3.3_2.12-1.3.0-snapshot.jar
```

**注意**：
- 每日构建版本可能不稳定
- 仅用于测试和早期体验
- 生产环境请使用稳定发布版

#### 选择合适的 JAR 文件

根据你的 Spark 版本和后端选择：

```
# Velox 后端
gluten-velox-bundle-spark3.2_2.12-x.x.x.jar  # Spark 3.2
gluten-velox-bundle-spark3.3_2.12-x.x.x.jar  # Spark 3.3
gluten-velox-bundle-spark3.4_2.12-x.x.x.jar  # Spark 3.4
gluten-velox-bundle-spark3.5_2.12-x.x.x.jar  # Spark 3.5

# ClickHouse 后端
gluten-clickhouse-bundle-spark3.2_2.12-x.x.x.jar
gluten-clickhouse-bundle-spark3.3_2.12-x.x.x.jar
```

### 2.2.2 从源码编译

如果预编译包不满足需求，或者需要定制化编译，可以从源码编译。

#### 克隆源码

```bash
cd ~
git clone https://github.com/apache/incubator-gluten.git
cd incubator-gluten

# 查看最新稳定分支
git branch -r

# 切换到稳定分支（可选）
git checkout branch-1.2
```

#### 编译 Velox 后端（推荐）

Gluten 提供了一键编译脚本：

```bash
# x86_64 架构编译
./dev/buildbundle-veloxbe.sh

# aarch64 架构编译
export CPU_TARGET="aarch64"
./dev/buildbundle-veloxbe.sh
```

**编译选项说明**：

```bash
# 完整编译（首次编译）
./dev/buildbundle-veloxbe.sh

# 只编译 Gluten 代码（已编译过依赖）
./dev/buildbundle-veloxbe.sh --build_arrow=OFF --run_setup_script=OFF

# 启用 HDFS 支持
./dev/buildbundle-veloxbe.sh --enable_hdfs=ON

# 启用 S3 支持
./dev/buildbundle-veloxbe.sh --enable_s3=ON

# 启用 Azure Blob 支持
./dev/buildbundle-veloxbe.sh --enable_abfs=ON

# 启用 Celeborn 远程 Shuffle
./dev/buildbundle-veloxbe.sh --enable_celeborn=ON

# 组合多个选项
./dev/buildbundle-veloxbe.sh --enable_hdfs=ON --enable_s3=ON
```

**控制编译线程数（避免 OOM）**：

```bash
# 设置编译线程数为 4（默认使用所有 CPU 核心）
export NUM_THREADS=4
./dev/buildbundle-veloxbe.sh
```

#### 分步编译（高级）

如果需要更精细的控制，可以分步编译：

```bash
# 1. 编译 Arrow
./dev/builddeps-veloxbe.sh build_arrow

# 2. 编译 Velox
./dev/builddeps-veloxbe.sh build_velox

# 3. 编译 Gluten C++ 部分
./dev/builddeps-veloxbe.sh build_gluten_cpp

# 4. 编译 Gluten Java 模块
cd /path/to/gluten

# 选择你的 Spark 版本
mvn clean package -Pbackends-velox -Pspark-3.3 -DskipTests
# 或
mvn clean package -Pbackends-velox -Pspark-3.4 -DskipTests
# 或
mvn clean package -Pbackends-velox -Pspark-3.5 -DskipTests
```

#### 编译 ClickHouse 后端

```bash
# ClickHouse 后端编译
./dev/buildbundle-clickhousebe.sh

# 指定 Spark 版本
./dev/buildbundle-clickhousebe.sh --spark_version=3.3
```

#### 编译输出

编译成功后，JAR 文件位于：

```bash
# Gluten JAR 文件
ls -lh package/target/gluten-*-spark*.jar

# 示例输出
# gluten-velox-bundle-spark3.3_2.12-1.2.0.jar
# gluten-velox-bundle-spark3.4_2.12-1.2.0.jar
```

#### 在 Docker 中编译（推荐用于生产环境）

为了获得更干净、可重现的构建环境，建议在 Docker 中编译：

```bash
# 拉取官方构建镜像
docker pull ghcr.io/apache/incubator-gluten/gluten-buildenv:latest

# 在 Docker 中编译
docker run -it --rm \
  -v $PWD:/workspace \
  -w /workspace \
  ghcr.io/apache/incubator-gluten/gluten-buildenv:latest \
  ./dev/buildbundle-veloxbe.sh
```

详细信息参见官方文档：[在 Docker 中编译](https://github.com/apache/incubator-gluten/blob/main/docs/developers/velox-backend-build-in-docker.md)

#### 编译常见问题

**问题 1：编译时 OOM**
```bash
# 解决方案：减少编译线程数
export NUM_THREADS=4
./dev/buildbundle-veloxbe.sh
```

**问题 2：网络问题导致依赖下载失败**
```bash
# 解决方案：配置 Maven 镜像（参见 2.1.2 节）
# 或使用代理
export http_proxy=http://proxy.example.com:8080
export https_proxy=http://proxy.example.com:8080
```

**问题 3：CMake 版本太旧**
```bash
# 解决方案：升级 CMake
pip install cmake --upgrade
```

## 2.3 第一个 Gluten 应用（Hello World）

现在让我们运行第一个 Gluten 示例，体验它的性能提升。

### 2.3.1 准备测试数据

创建一个简单的 Parquet 数据文件：

```bash
# 创建数据目录
mkdir -p ~/gluten-demo/data
cd ~/gluten-demo
```

创建数据生成脚本 `generate_data.py`：

```python
# generate_data.py
from pyspark.sql import SparkSession
from pyspark.sql.functions import rand, randn

# 创建 Spark Session
spark = SparkSession.builder \
    .appName("Generate Test Data") \
    .master("local[*]") \
    .getOrCreate()

# 生成 1000 万行测试数据
df = spark.range(0, 10000000) \
    .withColumn("value1", (rand() * 1000).cast("int")) \
    .withColumn("value2", (rand() * 1000).cast("int")) \
    .withColumn("value3", randn() * 100) \
    .withColumn("category", (rand() * 10).cast("int"))

# 保存为 Parquet 格式
df.write.mode("overwrite").parquet("data/test_data.parquet")

print(f"数据已生成: {df.count()} 行")
print("Schema:")
df.printSchema()

spark.stop()
```

运行数据生成脚本：

```bash
python generate_data.py
```

### 2.3.2 使用原生 Spark 运行（基线）

首先用原生 Spark 运行一个查询作为基线：

```bash
# 创建测试脚本 test_vanilla_spark.py
cat > test_vanilla_spark.py << 'EOF'
from pyspark.sql import SparkSession
import time

spark = SparkSession.builder \
    .appName("Vanilla Spark Test") \
    .master("local[*]") \
    .config("spark.sql.adaptive.enabled", "true") \
    .getOrCreate()

# 读取数据
df = spark.read.parquet("data/test_data.parquet")
df.createOrReplaceTempView("test_table")

# 执行查询
query = """
SELECT 
    category,
    COUNT(*) as count,
    AVG(value1) as avg_value1,
    SUM(value2) as sum_value2,
    MAX(value3) as max_value3
FROM test_table
WHERE value1 > 500
GROUP BY category
ORDER BY category
"""

start_time = time.time()
result = spark.sql(query)
result.show()
end_time = time.time()

print(f"\n执行时间: {end_time - start_time:.2f} 秒")

spark.stop()
EOF

# 运行
python test_vanilla_spark.py
```

记录执行时间，例如：**5.32 秒**

### 2.3.3 使用 Gluten 运行

现在使用 Gluten 运行相同的查询：

```bash
# 设置 Gluten JAR 路径
export GLUTEN_JAR=~/gluten/apache-gluten-1.2.0-bin/jars/gluten-velox-bundle-spark3.3_2.12-1.2.0.jar

# 创建 Gluten 测试脚本
cat > test_with_gluten.py << 'EOF'
from pyspark.sql import SparkSession
import time

spark = SparkSession.builder \
    .appName("Gluten Test") \
    .master("local[*]") \
    .config("spark.plugins", "org.apache.gluten.GlutenPlugin") \
    .config("spark.memory.offHeap.enabled", "true") \
    .config("spark.memory.offHeap.size", "2g") \
    .config("spark.shuffle.manager", "org.apache.spark.shuffle.sort.ColumnarShuffleManager") \
    .config("spark.sql.adaptive.enabled", "true") \
    .config("spark.driver.extraClassPath", f"{import os; os.environ['GLUTEN_JAR']}") \
    .config("spark.executor.extraClassPath", f"{import os; os.environ['GLUTEN_JAR']}") \
    .getOrCreate()

# 读取数据
df = spark.read.parquet("data/test_data.parquet")
df.createOrReplaceTempView("test_table")

# 执行相同的查询
query = """
SELECT 
    category,
    COUNT(*) as count,
    AVG(value1) as avg_value1,
    SUM(value2) as sum_value2,
    MAX(value3) as max_value3
FROM test_table
WHERE value1 > 500
GROUP BY category
ORDER BY category
"""

start_time = time.time()
result = spark.sql(query)
result.show()
end_time = time.time()

print(f"\n执行时间: {end_time - start_time:.2f} 秒")
print("\n✅ Gluten 已启用！")

spark.stop()
EOF

# 运行
python test_with_gluten.py
```

对比结果，例如：
- **原生 Spark**: 5.32 秒
- **Gluten**: 2.15 秒
- **加速比**: 2.47x 🚀

### 2.3.4 使用 Spark Shell 交互式测试

也可以使用 Spark Shell 交互式测试：

```bash
# 启动带 Gluten 的 Spark Shell
spark-shell \
  --master local[*] \
  --conf spark.plugins=org.apache.gluten.GlutenPlugin \
  --conf spark.memory.offHeap.enabled=true \
  --conf spark.memory.offHeap.size=2g \
  --conf spark.shuffle.manager=org.apache.spark.shuffle.sort.ColumnarShuffleManager \
  --conf spark.driver.extraClassPath=$GLUTEN_JAR \
  --conf spark.executor.extraClassPath=$GLUTEN_JAR
```

在 Spark Shell 中运行：

```scala
// 读取数据
val df = spark.read.parquet("data/test_data.parquet")
df.createOrReplaceTempView("test_table")

// 执行查询
val result = spark.sql("""
  SELECT category, COUNT(*) as count, AVG(value1) as avg_value1
  FROM test_table
  WHERE value1 > 500
  GROUP BY category
  ORDER BY category
""")

// 显示结果
result.show()

// 查看物理计划（验证使用了 Gluten）
result.explain()
```

## 2.4 配置详解：如何启用 Gluten

### 2.4.1 核心配置参数

启用 Gluten 需要以下核心配置：

```properties
# 1. 加载 Gluten 插件
spark.plugins=org.apache.gluten.GlutenPlugin

# 2. 启用 Off-Heap 内存
spark.memory.offHeap.enabled=true
spark.memory.offHeap.size=20g

# 3. 启用 Columnar Shuffle
spark.shuffle.manager=org.apache.spark.shuffle.sort.ColumnarShuffleManager

# 4. 添加 Gluten JAR 到 ClassPath
spark.driver.extraClassPath=/path/to/gluten-jar
spark.executor.extraClassPath=/path/to/gluten-jar
```

### 2.4.2 配置方式

有多种方式设置这些配置：

#### 方式 1：命令行参数

```bash
spark-submit \
  --master yarn \
  --deploy-mode cluster \
  --conf spark.plugins=org.apache.gluten.GlutenPlugin \
  --conf spark.memory.offHeap.enabled=true \
  --conf spark.memory.offHeap.size=20g \
  --conf spark.shuffle.manager=org.apache.spark.shuffle.sort.ColumnarShuffleManager \
  --conf spark.driver.extraClassPath=$GLUTEN_JAR \
  --conf spark.executor.extraClassPath=$GLUTEN_JAR \
  your_application.py
```

#### 方式 2：spark-defaults.conf

编辑 `$SPARK_HOME/conf/spark-defaults.conf`：

```properties
spark.plugins                               org.apache.gluten.GlutenPlugin
spark.memory.offHeap.enabled                true
spark.memory.offHeap.size                   20g
spark.shuffle.manager                       org.apache.spark.shuffle.sort.ColumnarShuffleManager
spark.driver.extraClassPath                 /path/to/gluten-jar
spark.executor.extraClassPath               /path/to/gluten-jar
```

#### 方式 3：代码中设置（不推荐）

```python
from pyspark.sql import SparkSession

spark = SparkSession.builder \
    .config("spark.plugins", "org.apache.gluten.GlutenPlugin") \
    .config("spark.memory.offHeap.enabled", "true") \
    .config("spark.memory.offHeap.size", "20g") \
    .config("spark.shuffle.manager", "org.apache.spark.shuffle.sort.ColumnarShuffleManager") \
    .getOrCreate()
```

**注意**：在 Yarn cluster 模式下，代码中设置 ClassPath 无效，必须用命令行参数。

### 2.4.3 内存配置详解

Off-Heap 内存是 Gluten 最重要的配置：

```properties
# 启用 Off-Heap 内存（必需）
spark.memory.offHeap.enabled=true

# Off-Heap 内存大小（根据实际情况调整）
spark.memory.offHeap.size=20g
```

**如何确定 Off-Heap 大小**：

```
推荐公式：
spark.memory.offHeap.size = executor memory × 0.6

示例：
如果 spark.executor.memory=32g
则 spark.memory.offHeap.size=20g (约 60%)
```

**内存隔离模式**（推荐在多任务并发时启用）：

```properties
# 启用内存隔离
spark.gluten.memory.isolation=true

# 每个任务的最大内存 = executor memory / task slots
```

### 2.4.4 Shuffle 配置

```properties
# 使用 Columnar Shuffle（推荐）
spark.shuffle.manager=org.apache.spark.shuffle.sort.ColumnarShuffleManager

# Shuffle 缓冲区大小（可选）
spark.gluten.shuffleWriter.bufferSize=4m
```

### 2.4.5 后端选择

Gluten 支持多个后端，通过不同的 JAR 文件选择：

```bash
# 使用 Velox 后端
export GLUTEN_JAR=/path/to/gluten-velox-bundle-spark3.3_2.12-1.2.0.jar

# 使用 ClickHouse 后端
export GLUTEN_JAR=/path/to/gluten-clickhouse-bundle-spark3.3_2.12-1.2.0.jar
```

不需要额外配置，Gluten 会自动根据 JAR 文件加载相应的后端。

### 2.4.6 完整配置示例

创建一个配置模板 `gluten-spark-defaults.conf`：

```properties
# ========== Gluten Core Configuration ==========
spark.plugins                               org.apache.gluten.GlutenPlugin
spark.gluten.enabled                        true

# ========== Memory Configuration ==========
spark.memory.offHeap.enabled                true
spark.memory.offHeap.size                   20g
spark.gluten.memory.isolation               false
spark.gluten.memory.reservationBlockSize    8MB

# ========== Shuffle Configuration ==========
spark.shuffle.manager                       org.apache.spark.shuffle.sort.ColumnarShuffleManager

# ========== ClassPath Configuration ==========
spark.driver.extraClassPath                 /path/to/gluten-jar
spark.executor.extraClassPath               /path/to/gluten-jar

# ========== Adaptive Query Execution ==========
spark.sql.adaptive.enabled                  true
spark.sql.adaptive.coalescePartitions.enabled true

# ========== Columnar Execution ==========
spark.gluten.sql.columnar.batchscan         true
spark.gluten.sql.columnar.filter            true
spark.gluten.sql.columnar.project           true
spark.gluten.sql.columnar.hashagg           true
spark.gluten.sql.columnar.broadcastJoin     true

# ========== Fallback Configuration ==========
spark.gluten.sql.columnar.fallback.preferColumnar   true
```

## 2.5 验证 Gluten 是否生效

如何确认 Gluten 已经正确启用？有多种验证方法。

### 2.5.1 检查日志输出

启动 Spark 应用后，查看日志中是否有 Gluten 相关信息：

```bash
# 在日志中搜索 Gluten
grep -i "gluten" $SPARK_HOME/logs/spark-*.log

# 应该看到类似输出：
# INFO GlutenPlugin: Gluten plugin enabled
# INFO GlutenPlugin: Backend: velox
# INFO GlutenPlugin: Loaded Gluten shared libraries
```

### 2.5.2 查看物理执行计划

在 Spark Shell 或代码中查看物理计划：

```scala
// Scala
val df = spark.sql("SELECT * FROM table WHERE col > 100")
df.explain()

// 如果看到 *Transformer 相关算子，说明使用了 Gluten
// 例如：FilterExecTransformer, ProjectExecTransformer
```

```python
# Python
df = spark.sql("SELECT * FROM table WHERE col > 100")
df.explain()

# 输出示例（使用了 Gluten）：
# == Physical Plan ==
# *(1) FilterExecTransformer (value#0 > 100)
# +- *(1) FileScanTransformer parquet [value#0]
```

**关键标识**：
- `*Transformer` 后缀：表示使用了 Gluten 的原生算子
- 例如：`FilterExecTransformer`, `ProjectExecTransformer`, `HashAggregateTransformer`

### 2.5.3 使用 Spark UI

访问 Spark UI（默认 http://localhost:4040）：

1. 进入 **SQL** 标签页
2. 点击具体的查询
3. 查看 **Physical Plan**
4. 确认算子名称包含 `Transformer`

![Spark UI 中的 Gluten 算子](../../images/spark-ui-gluten-operators.png)

### 2.5.4 检查 Native 库加载

编写一个检查脚本：

```scala
// check_gluten.scala
import org.apache.spark.sql.SparkSession

object CheckGluten {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Check Gluten")
      .config("spark.plugins", "org.apache.gluten.GlutenPlugin")
      .config("spark.memory.offHeap.enabled", "true")
      .config("spark.memory.offHeap.size", "1g")
      .getOrCreate()

    // 执行一个简单查询
    val df = spark.range(100).filter("id > 50").selectExpr("id * 2 as doubled")
    
    // 查看执行计划
    df.explain()
    
    // 触发执行
    val count = df.count()
    println(s"Result count: $count")
    
    // 检查是否使用了 Gluten
    val plan = df.queryExecution.executedPlan.toString()
    if (plan.contains("Transformer")) {
      println("✅ Gluten is ENABLED")
    } else {
      println("❌ Gluten is NOT enabled")
    }
    
    spark.stop()
  }
}
```

### 2.5.5 性能对比测试

最直接的验证方法是进行性能对比：

```python
# benchmark.py
from pyspark.sql import SparkSession
import time

def run_benchmark(use_gluten=False):
    builder = SparkSession.builder.appName("Benchmark")
    
    if use_gluten:
        builder = builder \
            .config("spark.plugins", "org.apache.gluten.GlutenPlugin") \
            .config("spark.memory.offHeap.enabled", "true") \
            .config("spark.memory.offHeap.size", "2g") \
            .config("spark.shuffle.manager", "org.apache.spark.shuffle.sort.ColumnarShuffleManager")
    
    spark = builder.getOrCreate()
    
    df = spark.read.parquet("data/test_data.parquet")
    df.createOrReplaceTempView("test")
    
    query = "SELECT category, COUNT(*), AVG(value1) FROM test WHERE value1 > 500 GROUP BY category"
    
    # 预热
    spark.sql(query).count()
    
    # 计时
    start = time.time()
    result = spark.sql(query)
    result.count()
    elapsed = time.time() - start
    
    spark.stop()
    return elapsed

# 运行对比
print("Running without Gluten...")
time_without = run_benchmark(use_gluten=False)

print("Running with Gluten...")
time_with = run_benchmark(use_gluten=True)

print(f"\nWithout Gluten: {time_without:.2f}s")
print(f"With Gluten: {time_with:.2f}s")
print(f"Speedup: {time_without/time_with:.2f}x")
```

## 2.6 常见问题排查

### 2.6.1 Gluten 未生效

**症状**：执行计划中没有看到 `*Transformer` 算子

**排查步骤**：

1. **检查 JAR 是否正确加载**：
```bash
# 查看 Spark 进程的 ClassPath
jps -v | grep GlutenPlugin
```

2. **检查配置**：
```scala
spark.conf.get("spark.plugins")
// 应该输出：org.apache.gluten.GlutenPlugin
```

3. **检查日志**：
```bash
grep -i "error\|exception" $SPARK_HOME/logs/*.log
```

4. **可能的原因**：
   - JAR 路径错误
   - Off-Heap 内存未启用
   - Spark 版本不匹配
   - 查询不支持（自动 Fallback）

### 2.6.2 UnsatisfiedLinkError

**错误信息**：
```
java.lang.UnsatisfiedLinkError: no gluten in java.library.path
```

**解决方案**：

方案 1：确保使用静态编译的 Gluten JAR（推荐）
```bash
# 下载官方预编译包，它使用静态链接
```

方案 2：设置 `spark.gluten.loadLibFromJar=true`
```properties
spark.gluten.loadLibFromJar=true
```

方案 3：手动安装依赖库
```bash
# 查看缺少哪些库
ldd /path/to/libgluten.so

# 安装缺失的库
sudo apt-get install libboost-all-dev libssl-dev
```

### 2.6.3 Out of Memory (OOM)

**症状**：任务失败，日志显示 OOM

**解决方案**：

1. **增加 Off-Heap 内存**：
```properties
spark.memory.offHeap.size=30g  # 增大
```

2. **启用内存隔离**：
```properties
spark.gluten.memory.isolation=true
```

3. **减少并发任务数**：
```properties
spark.executor.cores=4  # 减少核心数
spark.sql.shuffle.partitions=200  # 增加分区数
```

### 2.6.4 性能没有提升

**可能原因**：

1. **查询太简单**：Gluten 在复杂查询中收益更明显
2. **IO 瓶颈**：瓶颈在存储而非计算
3. **不支持的算子**：发生了 Fallback
4. **数据量太小**：测试数据量不足

**排查方法**：

```scala
// 查看是否有 ColumnarToRow 转换（表示 Fallback）
df.explain()

// 如果看到多个 ColumnarToRow，说明部分算子回退到了 Spark
```

### 2.6.5 Fallback 警告

**日志信息**：
```
WARN GlutenPlugin: Operator XXX falls back to vanilla Spark
```

**说明**：某些算子不支持，自动回退到原生 Spark，这是正常的。

**如何减少 Fallback**：
1. 使用支持的数据类型和函数
2. 查看支持列表：[Velox 函数支持](https://gluten.apache.org/docs/velox-backend-support-progress/)
3. 升级到最新版本

### 2.6.6 编译错误

**错误：OOM during compilation**
```bash
# 解决：减少线程数
export NUM_THREADS=4
./dev/buildbundle-veloxbe.sh
```

**错误：CMake version too old**
```bash
# 解决：升级 CMake
pip3 install --upgrade cmake
```

**错误：Maven dependency download timeout**
```bash
# 解决：配置镜像或代理
vi ~/.m2/settings.xml
```

### 2.6.7 调试技巧

启用详细日志：

```properties
# 启用 DEBUG 日志
spark.gluten.sql.debug=true

# 查看 Substrait 计划
spark.gluten.sql.cacheWholeStageTransformerContext=true
```

查看具体的 Native 计划：

```scala
import org.apache.gluten.execution.WholeStageTransformer

// 在执行后获取 native plan
val plan = df.queryExecution.executedPlan
// 遍历查找 WholeStageTransformer
```

## 本章小结

通过本章的学习，你应该已经：

1. ✅ **搭建环境**：安装了 Java、Spark 和编译工具
2. ✅ **获取 Gluten**：通过预编译包或源码编译获得 Gluten JAR
3. ✅ **运行示例**：成功运行了第一个 Gluten 程序并体验到性能提升
4. ✅ **掌握配置**：理解了 Gluten 的核心配置参数
5. ✅ **验证方法**：学会了如何验证 Gluten 是否生效
6. ✅ **问题排查**：掌握了常见问题的解决方法

现在你已经可以在本地环境使用 Gluten 了！下一章我们将深入学习 Gluten 的使用指南，包括配置调优、监控和最佳实践。

## 参考资料

- [Gluten Getting Started](https://github.com/apache/incubator-gluten/tree/main/docs/get-started)
- [Velox Backend Guide](https://github.com/apache/incubator-gluten/blob/main/docs/get-started/Velox.md)
- [Build Guide](https://github.com/apache/incubator-gluten/blob/main/docs/get-started/build-guide.md)
- [Configuration Reference](https://github.com/apache/incubator-gluten/blob/main/docs/Configuration.md)

---

**下一章预告**：[第3章：Gluten 使用指南](chapter03-usage-guide.md) - 深入学习配置调优和最佳实践
