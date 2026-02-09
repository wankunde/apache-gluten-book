# Gluten 代码示例

本目录包含《Apache Gluten 深入浅出》一书的所有代码示例。

## 目录结构

```
code-examples/
├── python/          # Python 示例
├── scala/           # Scala 示例
├── java/            # Java 示例（待添加）
├── shell/           # Shell 脚本
└── configs/         # 配置文件模板
```

## Python 示例

### 1. generate_test_data.py
生成测试数据（Parquet 格式）

**用法**：
```bash
python generate_test_data.py [行数] [输出路径]

# 示例
python generate_test_data.py 10000000 data/test_data.parquet
```

### 2. gluten_demo.py
Gluten 功能演示

**用法**：
```bash
export GLUTEN_JAR=/path/to/gluten-jar
export DATA_PATH=data/test_data.parquet
python gluten_demo.py
```

### 3. benchmark.py
性能对比测试

**用法**：
```bash
export GLUTEN_JAR=/path/to/gluten-jar
export DATA_PATH=data/test_data.parquet
python benchmark.py
```

## Scala 示例

### 1. GlutenDemo.scala
Gluten 功能演示（Scala 版本）

**编译**：
```bash
scalac -classpath "$SPARK_HOME/jars/*" GlutenDemo.scala
```

**运行**：
```bash
export GLUTEN_JAR=/path/to/gluten-jar
export DATA_PATH=data/test_data.parquet

spark-submit \
  --class GlutenDemo \
  --master local[*] \
  --conf spark.plugins=org.apache.gluten.GlutenPlugin \
  --conf spark.memory.offHeap.enabled=true \
  --conf spark.memory.offHeap.size=2g \
  --conf spark.shuffle.manager=org.apache.spark.shuffle.sort.ColumnarShuffleManager \
  --conf spark.driver.extraClassPath=$GLUTEN_JAR \
  --conf spark.executor.extraClassPath=$GLUTEN_JAR \
  GlutenDemo.jar
```

### 2. GlutenBenchmark.scala
性能基准测试（Scala 版本）

**编译和运行**：同上

## Shell 脚本

### 1. check_environment.sh
检查 Gluten 运行环境

**用法**：
```bash
chmod +x check_environment.sh
./check_environment.sh
```

### 2. run_gluten_demo.sh
一键运行 Gluten 演示

**用法**：
```bash
export GLUTEN_JAR=/path/to/gluten-jar
chmod +x run_gluten_demo.sh
./run_gluten_demo.sh
```

## 配置文件

### 1. gluten-basic.conf
基础配置模板

**用法**：
```bash
# 复制到 Spark 配置目录
cp gluten-basic.conf $SPARK_HOME/conf/spark-defaults.conf
```

### 2. gluten-production.conf
生产环境配置模板

**用法**：
```bash
# 作为命令行参数
spark-submit --properties-file gluten-production.conf your_app.py
```

## 快速开始

### 1. 检查环境

```bash
cd shell
./check_environment.sh
```

### 2. 生成测试数据

```bash
cd ../python
python generate_test_data.py 10000000
```

### 3. 运行演示

```bash
export GLUTEN_JAR=/path/to/your/gluten-jar
python gluten_demo.py
```

### 4. 性能对比

```bash
python benchmark.py
```

## 环境变量

需要设置以下环境变量：

| 变量 | 说明 | 示例 |
|------|------|------|
| `GLUTEN_JAR` | Gluten JAR 文件路径 | `/opt/gluten/gluten-velox-bundle-spark3.3_2.12-1.2.0.jar` |
| `SPARK_HOME` | Spark 安装目录 | `~/spark-3.3.1` |
| `DATA_PATH` | 测试数据路径 | `data/test_data.parquet` |
| `JAVA_HOME` | Java 安装目录 | `/usr/lib/jvm/java-8-openjdk-amd64` |

## 常见问题

### Q1: UnsatisfiedLinkError
**问题**：运行时提示找不到动态库

**解决**：
```bash
# 使用官方预编译包（推荐）
# 或设置
spark.gluten.loadLibFromJar=true
```

### Q2: 数据文件不存在
**问题**：找不到 test_data.parquet

**解决**：
```bash
# 先生成数据
python generate_test_data.py
```

### Q3: 内存不足
**问题**：OOM 错误

**解决**：
```bash
# 增加 Off-Heap 内存
spark.memory.offHeap.size=4g  # 或更大
```

## 贡献

欢迎贡献更多示例！请确保：
- 代码可以正常运行
- 添加清晰的注释
- 更新本 README

## 许可证

本项目采用 CC BY-NC-SA 4.0 许可证。

## 参考

- [Apache Gluten 官方文档](https://gluten.apache.org/)
- [Gluten GitHub 仓库](https://github.com/apache/incubator-gluten)
- [《Apache Gluten 深入浅出》](../../README.md)
