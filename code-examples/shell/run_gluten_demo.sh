#!/bin/bash
# 文件名：run_gluten_demo.sh
# 用途：运行 Gluten 示例程序

set -e

# 配置
SPARK_HOME=${SPARK_HOME:-~/spark-3.3.1}
GLUTEN_JAR=${GLUTEN_JAR:-~/gluten/gluten-velox-bundle-spark3.3_2.12-1.2.0.jar}
DATA_PATH=${DATA_PATH:-./data/test_data.parquet}

echo "========================================="
echo "  Gluten 示例程序"
echo "========================================="
echo ""
echo "Spark Home: $SPARK_HOME"
echo "Gluten JAR: $GLUTEN_JAR"
echo "Data Path: $DATA_PATH"
echo ""

# 检查文件
if [ ! -f "$GLUTEN_JAR" ]; then
    echo "❌ 错误: Gluten JAR 文件不存在: $GLUTEN_JAR"
    echo "请设置正确的 GLUTEN_JAR 环境变量"
    exit 1
fi

if [ ! -d "$DATA_PATH" ]; then
    echo "⚠️  警告: 数据文件不存在，将先生成测试数据"
    python3 generate_test_data.py
fi

# 运行 Spark 应用（使用 Gluten）
echo "开始运行 Gluten 示例..."
echo ""

$SPARK_HOME/bin/spark-submit \
  --master local[*] \
  --conf spark.plugins=org.apache.gluten.GlutenPlugin \
  --conf spark.memory.offHeap.enabled=true \
  --conf spark.memory.offHeap.size=2g \
  --conf spark.shuffle.manager=org.apache.spark.shuffle.sort.ColumnarShuffleManager \
  --conf spark.driver.extraClassPath=$GLUTEN_JAR \
  --conf spark.executor.extraClassPath=$GLUTEN_JAR \
  --conf spark.sql.adaptive.enabled=true \
  ../python/gluten_demo.py

echo ""
echo "========================================="
echo "  完成"
echo "========================================="
