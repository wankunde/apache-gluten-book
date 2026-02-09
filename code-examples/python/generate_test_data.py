# 文件名：generate_test_data.py
# 用途：生成 Gluten 测试数据

from pyspark.sql import SparkSession
from pyspark.sql.functions import rand, randn, col
import sys

def generate_data(num_rows=10000000, output_path="data/test_data.parquet"):
    """生成测试数据"""
    
    print(f"正在生成 {num_rows:,} 行测试数据...")
    
    # 创建 Spark Session
    spark = SparkSession.builder \
        .appName("Generate Test Data") \
        .master("local[*]") \
        .config("spark.sql.shuffle.partitions", "8") \
        .getOrCreate()
    
    try:
        # 生成数据
        df = spark.range(0, num_rows) \
            .withColumn("value1", (rand() * 1000).cast("int")) \
            .withColumn("value2", (rand() * 1000).cast("int")) \
            .withColumn("value3", randn() * 100) \
            .withColumn("category", (rand() * 10).cast("int")) \
            .withColumn("name", col("id").cast("string"))
        
        # 保存为 Parquet
        print(f"保存数据到: {output_path}")
        df.write.mode("overwrite").parquet(output_path)
        
        # 显示统计信息
        print(f"\n数据生成完成!")
        print(f"总行数: {df.count():,}")
        print("\nSchema:")
        df.printSchema()
        
        print("\n前 10 行数据:")
        df.show(10)
        
    finally:
        spark.stop()

if __name__ == "__main__":
    rows = int(sys.argv[1]) if len(sys.argv) > 1 else 10000000
    path = sys.argv[2] if len(sys.argv) > 2 else "data/test_data.parquet"
    
    generate_data(rows, path)
