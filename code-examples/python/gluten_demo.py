# 文件名：gluten_demo.py
# 用途：Gluten 功能演示

from pyspark.sql import SparkSession
from pyspark.sql.functions import col, avg, sum, max, count
import time
import os

def create_spark_session(app_name, use_gluten=True):
    """创建 Spark Session"""
    builder = SparkSession.builder.appName(app_name).master("local[*]")
    
    if use_gluten:
        gluten_jar = os.environ.get('GLUTEN_JAR')
        if not gluten_jar:
            raise ValueError("请设置 GLUTEN_JAR 环境变量")
        
        builder = builder \
            .config("spark.plugins", "org.apache.gluten.GlutenPlugin") \
            .config("spark.memory.offHeap.enabled", "true") \
            .config("spark.memory.offHeap.size", "2g") \
            .config("spark.shuffle.manager", "org.apache.spark.shuffle.sort.ColumnarShuffleManager") \
            .config("spark.driver.extraClassPath", gluten_jar) \
            .config("spark.executor.extraClassPath", gluten_jar) \
            .config("spark.sql.adaptive.enabled", "true")
    
    return builder.getOrCreate()

def run_query(spark, query_name, sql):
    """运行查询并计时"""
    print(f"\n{'='*60}")
    print(f"执行查询: {query_name}")
    print(f"{'='*60}")
    print(f"SQL: {sql}")
    print()
    
    start_time = time.time()
    result = spark.sql(sql)
    result.show()
    count = result.count()
    elapsed = time.time() - start_time
    
    print(f"\n结果行数: {count}")
    print(f"执行时间: {elapsed:.2f} 秒")
    
    return elapsed

def main():
    """主函数"""
    data_path = os.environ.get('DATA_PATH', 'data/test_data.parquet')
    
    print("="*60)
    print("Gluten 功能演示")
    print("="*60)
    
    # 创建 Spark Session
    spark = create_spark_session("Gluten Demo")
    
    try:
        # 读取数据
        print(f"\n读取数据: {data_path}")
        df = spark.read.parquet(data_path)
        df.createOrReplaceTempView("test_table")
        
        print("\nSchema:")
        df.printSchema()
        
        print(f"\n总行数: {df.count():,}")
        
        # 查询 1: 简单过滤和聚合
        query1 = """
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
        t1 = run_query(spark, "查询1: 过滤 + 聚合", query1)
        
        # 查询 2: 多重过滤
        query2 = """
        SELECT 
            category,
            COUNT(*) as count,
            AVG(value1 + value2) as avg_sum
        FROM test_table
        WHERE value1 > 300 AND value2 < 700
        GROUP BY category
        HAVING COUNT(*) > 100000
        ORDER BY avg_sum DESC
        """
        t2 = run_query(spark, "查询2: 复杂过滤 + 聚合", query2)
        
        # 查询 3: Window 函数
        query3 = """
        SELECT 
            category,
            id,
            value1,
            ROW_NUMBER() OVER (PARTITION BY category ORDER BY value1 DESC) as rank
        FROM test_table
        WHERE id < 1000000
        """
        t3 = run_query(spark, "查询3: Window 函数", query3)
        
        # 显示总结
        print("\n" + "="*60)
        print("查询性能总结")
        print("="*60)
        print(f"查询1: {t1:.2f} 秒")
        print(f"查询2: {t2:.2f} 秒")
        print(f"查询3: {t3:.2f} 秒")
        print(f"总计: {t1+t2+t3:.2f} 秒")
        
        # 显示执行计划
        print("\n" + "="*60)
        print("查询1 执行计划 (检查是否使用 Gluten)")
        print("="*60)
        spark.sql(query1).explain()
        
    finally:
        spark.stop()

if __name__ == "__main__":
    main()
