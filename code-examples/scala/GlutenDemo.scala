// 文件名：GlutenDemo.scala
// 用途：Gluten 功能演示（Scala 版本）

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object GlutenDemo {
  
  def createSparkSession(appName: String, useGluten: Boolean = true): SparkSession = {
    val builder = SparkSession.builder()
      .appName(appName)
      .master("local[*]")
      .config("spark.sql.adaptive.enabled", "true")
    
    if (useGluten) {
      val glutenJar = sys.env.getOrElse("GLUTEN_JAR", 
        throw new RuntimeException("请设置 GLUTEN_JAR 环境变量"))
      
      builder
        .config("spark.plugins", "org.apache.gluten.GlutenPlugin")
        .config("spark.memory.offHeap.enabled", "true")
        .config("spark.memory.offHeap.size", "2g")
        .config("spark.shuffle.manager", "org.apache.spark.shuffle.sort.ColumnarShuffleManager")
        .config("spark.driver.extraClassPath", glutenJar)
        .config("spark.executor.extraClassPath", glutenJar)
    } else {
      builder
    }
    
    builder.getOrCreate()
  }
  
  def runQuery(spark: SparkSession, queryName: String, sql: String): Double = {
    println(s"\n${"=" * 60}")
    println(s"执行查询: $queryName")
    println(s"${"=" * 60}")
    println(s"SQL: $sql")
    println()
    
    val startTime = System.currentTimeMillis()
    val result = spark.sql(sql)
    result.show()
    val count = result.count()
    val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
    
    println(s"\n结果行数: $count")
    println(f"执行时间: $elapsed%.2f 秒")
    
    elapsed
  }
  
  def main(args: Array[String]): Unit = {
    val dataPath = sys.env.getOrElse("DATA_PATH", "data/test_data.parquet")
    
    println("=" * 60)
    println("Gluten 功能演示 (Scala)")
    println("=" * 60)
    
    val spark = createSparkSession("Gluten Demo - Scala")
    
    try {
      // 读取数据
      println(s"\n读取数据: $dataPath")
      val df = spark.read.parquet(dataPath)
      df.createOrReplaceTempView("test_table")
      
      println("\nSchema:")
      df.printSchema()
      
      val totalRows = df.count()
      println(f"\n总行数: $totalRows%,d")
      
      // 查询 1: 简单过滤和聚合
      val query1 =
        """
          |SELECT 
          |    category,
          |    COUNT(*) as count,
          |    AVG(value1) as avg_value1,
          |    SUM(value2) as sum_value2,
          |    MAX(value3) as max_value3
          |FROM test_table
          |WHERE value1 > 500
          |GROUP BY category
          |ORDER BY category
        """.stripMargin
      val t1 = runQuery(spark, "查询1: 过滤 + 聚合", query1)
      
      // 查询 2: DataFrame API
      println(s"\n${"=" * 60}")
      println("查询2: DataFrame API 示例")
      println(s"${"=" * 60}\n")
      
      val startTime = System.currentTimeMillis()
      val result2 = df
        .filter(col("value1") > 300 && col("value2") < 700)
        .groupBy("category")
        .agg(
          count("*").alias("count"),
          avg("value1").alias("avg_value1"),
          sum("value2").alias("sum_value2")
        )
        .orderBy(desc("count"))
      
      result2.show()
      val t2 = (System.currentTimeMillis() - startTime) / 1000.0
      println(f"执行时间: $t2%.2f 秒")
      
      // 查询 3: 复杂计算
      val query3 =
        """
          |SELECT 
          |    category,
          |    COUNT(DISTINCT id) as unique_ids,
          |    AVG(value1 * value2) as avg_product,
          |    STDDEV(value3) as stddev_value3
          |FROM test_table
          |WHERE value1 + value2 > 1000
          |GROUP BY category
          |HAVING COUNT(*) > 50000
        """.stripMargin
      val t3 = runQuery(spark, "查询3: 复杂计算", query3)
      
      // 显示总结
      println("\n" + "=" * 60)
      println("查询性能总结")
      println("=" * 60)
      println(f"查询1: $t1%.2f 秒")
      println(f"查询2: $t2%.2f 秒")
      println(f"查询3: $t3%.2f 秒")
      println(f"总计: ${t1 + t2 + t3}%.2f 秒")
      
      // 显示执行计划
      println("\n" + "=" * 60)
      println("查询1 执行计划")
      println("=" * 60)
      spark.sql(query1).explain(extended = true)
      
    } finally {
      spark.stop()
    }
  }
}
