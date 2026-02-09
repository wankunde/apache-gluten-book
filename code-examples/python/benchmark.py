# 文件名：benchmark.py
# 用途：对比 Gluten 和原生 Spark 的性能

from pyspark.sql import SparkSession
import time
import os

def run_benchmark(use_gluten=False, num_iterations=3):
    """运行性能基准测试"""
    
    app_name = "Benchmark with Gluten" if use_gluten else "Benchmark without Gluten"
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
            .config("spark.executor.extraClassPath", gluten_jar)
    
    spark = builder.getOrCreate()
    
    try:
        # 读取数据
        data_path = os.environ.get('DATA_PATH', 'data/test_data.parquet')
        df = spark.read.parquet(data_path)
        df.createOrReplaceTempView("test_table")
        
        # 定义测试查询
        queries = {
            "简单聚合": """
                SELECT category, COUNT(*), AVG(value1) 
                FROM test_table 
                GROUP BY category
            """,
            "过滤+聚合": """
                SELECT category, COUNT(*), SUM(value2) 
                FROM test_table 
                WHERE value1 > 500 
                GROUP BY category
            """,
            "复杂聚合": """
                SELECT 
                    category,
                    COUNT(*) as cnt,
                    AVG(value1) as avg1,
                    AVG(value2) as avg2,
                    SUM(value3) as sum3,
                    MAX(value1) as max1,
                    MIN(value2) as min2
                FROM test_table
                WHERE value1 > 300 AND value2 < 700
                GROUP BY category
                HAVING cnt > 100000
            """
        }
        
        results = {}
        
        # 运行每个查询
        for query_name, sql in queries.items():
            times = []
            
            # 预热
            spark.sql(sql).count()
            
            # 多次运行取平均值
            for i in range(num_iterations):
                start = time.time()
                spark.sql(sql).count()
                elapsed = time.time() - start
                times.append(elapsed)
            
            avg_time = sum(times) / len(times)
            results[query_name] = avg_time
        
        return results
        
    finally:
        spark.stop()

def main():
    """主函数"""
    print("="*70)
    print("Gluten vs 原生 Spark 性能对比")
    print("="*70)
    print()
    
    # 运行不使用 Gluten 的基准测试
    print("第1步: 运行原生 Spark 基准测试...")
    vanilla_results = run_benchmark(use_gluten=False)
    
    print("\n第2步: 运行 Gluten 基准测试...")
    gluten_results = run_benchmark(use_gluten=True)
    
    # 显示结果
    print("\n" + "="*70)
    print("性能对比结果")
    print("="*70)
    print()
    print(f"{'查询':<20} {'原生Spark':<15} {'Gluten':<15} {'加速比':<10}")
    print("-"*70)
    
    total_vanilla = 0
    total_gluten = 0
    
    for query_name in vanilla_results.keys():
        vanilla_time = vanilla_results[query_name]
        gluten_time = gluten_results[query_name]
        speedup = vanilla_time / gluten_time
        
        total_vanilla += vanilla_time
        total_gluten += gluten_time
        
        print(f"{query_name:<20} {vanilla_time:>10.2f}s    {gluten_time:>10.2f}s    {speedup:>6.2f}x")
    
    print("-"*70)
    overall_speedup = total_vanilla / total_gluten
    print(f"{'总计':<20} {total_vanilla:>10.2f}s    {total_gluten:>10.2f}s    {overall_speedup:>6.2f}x")
    print()
    print("="*70)
    
    if overall_speedup > 1.5:
        print("✅ Gluten 显著提升性能！")
    elif overall_speedup > 1.1:
        print("✅ Gluten 有性能提升")
    else:
        print("⚠️  性能提升不明显，请检查配置")

if __name__ == "__main__":
    main()
