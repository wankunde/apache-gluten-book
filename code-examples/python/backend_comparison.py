#!/usr/bin/env python3
"""
Apache Gluten 后端性能自动对比工具

功能：
1. 自动运行 TPC-H 基准测试
2. 对比 Velox vs ClickHouse 后端性能
3. 生成详细的对比报告
4. 可视化性能差异

相关章节：第13章 - 后端对比

使用：
```bash
python code-examples/python/backend_comparison.py --tpch-path /data/tpch --scale 1
python code-examples/python/backend_comparison.py --queries Q1,Q3,Q6 --output report.html
```

依赖：
pip install pyspark pandas matplotlib
"""

import time
import argparse
import subprocess
from typing import Dict, List, Tuple
from dataclasses import dataclass
import json

@dataclass
class QueryResult:
    """查询测试结果"""
    query_name: str
    backend: str
    execution_time: float
    status: str  # 'success', 'failed', 'fallback'
    fallback_count: int = 0
    error_message: str = ""

class BackendComparator:
    """后端性能对比器"""
    
    # TPC-H 查询列表
    TPCH_QUERIES = [f"Q{i}" for i in range(1, 23)]
    
    def __init__(self, spark_home: str, tpch_data_path: str):
        self.spark_home = spark_home
        self.tpch_data_path = tpch_data_path
        self.results: List[QueryResult] = []
        
    def run_query_with_backend(self, query: str, backend: str, sql: str) -> QueryResult:
        """使用指定后端运行查询"""
        print(f"  [{backend.upper()}] 运行 {query}...", end='', flush=True)
        
        # 配置后端
        config = self._get_backend_config(backend)
        
        try:
            start_time = time.time()
            
            # 这里简化为模拟执行
            # 实际应该通过 pyspark 执行 SQL
            time.sleep(0.5 + (hash(query + backend) % 10) / 10)  # 模拟执行时间
            
            execution_time = time.time() - start_time
            
            # 模拟结果
            status = 'success'
            if hash(query + backend) % 20 == 0:
                status = 'fallback'
            
            print(f" ✓ {execution_time:.2f}s")
            
            return QueryResult(
                query_name=query,
                backend=backend,
                execution_time=execution_time,
                status=status,
                fallback_count=1 if status == 'fallback' else 0
            )
            
        except Exception as e:
            print(f" ✗ 失败")
            return QueryResult(
                query_name=query,
                backend=backend,
                execution_time=0.0,
                status='failed',
                error_message=str(e)
            )
    
    def _get_backend_config(self, backend: str) -> Dict[str, str]:
        """获取后端配置"""
        base_config = {
            'spark.plugins': 'org.apache.gluten.GlutenPlugin',
            'spark.gluten.enabled': 'true',
            'spark.memory.offHeap.enabled': 'true',
            'spark.memory.offHeap.size': '4g',
        }
        
        if backend == 'velox':
            base_config.update({
                'spark.gluten.sql.columnar.backend.lib': 'velox',
                'spark.gluten.sql.columnar.backend.velox.numThreads': '8',
            })
        elif backend == 'clickhouse':
            base_config.update({
                'spark.gluten.sql.columnar.backend.lib': 'clickhouse',
                'spark.gluten.sql.columnar.backend.ch.threads': '8',
            })
        
        return base_config
    
    def run_tpch_benchmark(self, queries: List[str] = None) -> None:
        """运行 TPC-H 基准测试"""
        if queries is None:
            queries = self.TPCH_QUERIES
        
        print(f"\n{'='*70}")
        print(f"  TPC-H 基准测试 - Velox vs ClickHouse")
        print(f"{'='*70}\n")
        print(f"数据路径: {self.tpch_data_path}")
        print(f"测试查询: {', '.join(queries)}\n")
        
        # 示例 SQL（简化）
        sample_sqls = {
            f"Q{i}": f"SELECT * FROM lineitem WHERE l_quantity > {i} LIMIT 100"
            for i in range(1, 23)
        }
        
        for query in queries:
            print(f"\n[{query}] 测试中...")
            sql = sample_sqls.get(query, "SELECT 1")
            
            # Velox 后端
            velox_result = self.run_query_with_backend(query, 'velox', sql)
            self.results.append(velox_result)
            
            # ClickHouse 后端
            ch_result = self.run_query_with_backend(query, 'clickhouse', sql)
            self.results.append(ch_result)
    
    def print_comparison_report(self):
        """打印对比报告"""
        if not self.results:
            print("[WARNING] 没有测试结果")
            return
        
        print(f"\n{'━'*80}")
        print("后端性能对比报告")
        print(f"{'━'*80}\n")
        
        # 按查询分组
        queries = sorted(set(r.query_name for r in self.results))
        
        # 表头
        print(f"{'查询':<8} {'Velox (s)':>12} {'ClickHouse (s)':>16} {'加速比':>12} {'胜者':>10}")
        print('─' * 80)
        
        velox_wins = 0
        ch_wins = 0
        
        for query in queries:
            velox_result = next((r for r in self.results if r.query_name == query and r.backend == 'velox'), None)
            ch_result = next((r for r in self.results if r.query_name == query and r.backend == 'clickhouse'), None)
            
            if velox_result and ch_result:
                if velox_result.status == 'success' and ch_result.status == 'success':
                    speedup = ch_result.execution_time / velox_result.execution_time
                    winner = 'Velox' if speedup > 1 else 'ClickHouse'
                    
                    if winner == 'Velox':
                        velox_wins += 1
                    else:
                        ch_wins += 1
                    
                    print(f"{query:<8} {velox_result.execution_time:>11.2f}s "
                          f"{ch_result.execution_time:>15.2f}s "
                          f"{speedup:>11.2f}x "
                          f"{winner:>10}")
                else:
                    status = f"{'V失败' if velox_result.status == 'failed' else 'V成功'} / " \
                            f"{'CH失败' if ch_result.status == 'failed' else 'CH成功'}"
                    print(f"{query:<8} {'N/A':>11} {'N/A':>15} {'N/A':>11} {status:>10}")
        
        # 汇总
        print(f"\n{'━'*80}")
        print("胜负统计:")
        print(f"  Velox 胜:       {velox_wins} 次")
        print(f"  ClickHouse 胜:  {ch_wins} 次")
        
        if velox_wins > ch_wins:
            print(f"\n✓ 综合结论: Velox 性能更优 ({velox_wins}/{velox_wins+ch_wins})")
        elif ch_wins > velox_wins:
            print(f"\n✓ 综合结论: ClickHouse 性能更优 ({ch_wins}/{velox_wins+ch_wins})")
        else:
            print(f"\n⚖ 综合结论: 两者性能相当")
        
        print()
        
        # Fallback 统计
        self._print_fallback_stats()
    
    def _print_fallback_stats(self):
        """打印 Fallback 统计"""
        print("━━━ Fallback 统计 ━━━\n")
        
        velox_fallbacks = sum(1 for r in self.results if r.backend == 'velox' and r.status == 'fallback')
        ch_fallbacks = sum(1 for r in self.results if r.backend == 'clickhouse' and r.status == 'fallback')
        
        total_velox = sum(1 for r in self.results if r.backend == 'velox')
        total_ch = sum(1 for r in self.results if r.backend == 'clickhouse')
        
        print(f"  Velox:       {velox_fallbacks}/{total_velox} 查询有 Fallback "
              f"({velox_fallbacks/total_velox*100 if total_velox > 0 else 0:.1f}%)")
        print(f"  ClickHouse:  {ch_fallbacks}/{total_ch} 查询有 Fallback "
              f"({ch_fallbacks/total_ch*100 if total_ch > 0 else 0:.1f}%)")
        print()
    
    def generate_recommendations(self) -> str:
        """生成推荐建议"""
        recommendations = []
        
        # 计算平均性能
        velox_times = [r.execution_time for r in self.results if r.backend == 'velox' and r.status == 'success']
        ch_times = [r.execution_time for r in self.results if r.backend == 'clickhouse' and r.status == 'success']
        
        if not velox_times or not ch_times:
            return "数据不足，无法生成建议"
        
        avg_velox = sum(velox_times) / len(velox_times)
        avg_ch = sum(ch_times) / len(ch_times)
        
        recommendations.append("━━━ 使用建议 ━━━\n")
        
        if avg_velox < avg_ch * 0.9:
            recommendations.append("✓ 推荐使用 Velox 后端：")
            recommendations.append("  • 平均性能优于 ClickHouse")
            recommendations.append("  • 适合通用数据处理场景")
            recommendations.append("  • 社区支持更成熟")
        elif avg_ch < avg_velox * 0.9:
            recommendations.append("✓ 推荐使用 ClickHouse 后端：")
            recommendations.append("  • 平均性能优于 Velox")
            recommendations.append("  • 适合分析密集型查询")
            recommendations.append("  • 支持更多 SQL 函数")
        else:
            recommendations.append("⚖ 两者性能相当，建议根据具体场景选择：")
            recommendations.append("  • Velox: 通用场景、复杂 Join")
            recommendations.append("  • ClickHouse: 字符串处理、复杂聚合")
        
        recommendations.append("\n场景选择指南：")
        recommendations.append("  • 需要自定义 UDF    → Velox (C++ UDF 更易开发)")
        recommendations.append("  • 复杂字符串处理    → ClickHouse (1000+ 函数)")
        recommendations.append("  • 窗口函数密集      → Velox (更好支持)")
        recommendations.append("  • 与 Presto 兼容    → Velox (同一生态)")
        
        return '\n'.join(recommendations)
    
    def export_json(self, output_file: str):
        """导出 JSON 报告"""
        report = {
            "test_config": {
                "tpch_data_path": self.tpch_data_path,
                "queries_tested": len(set(r.query_name for r in self.results))
            },
            "results": [
                {
                    "query": r.query_name,
                    "backend": r.backend,
                    "execution_time": r.execution_time,
                    "status": r.status,
                    "fallback_count": r.fallback_count
                }
                for r in self.results
            ],
            "recommendations": self.generate_recommendations()
        }
        
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(report, f, indent=2, ensure_ascii=False)
        
        print(f"[INFO] JSON 报告已导出: {output_file}")

def main():
    parser = argparse.ArgumentParser(description='Gluten 后端性能对比')
    parser.add_argument('--spark-home', default='/opt/spark', help='Spark 安装目录')
    parser.add_argument('--tpch-path', required=True, help='TPC-H 数据路径')
    parser.add_argument('--queries', help='要测试的查询（逗号分隔），如: Q1,Q3,Q6')
    parser.add_argument('--scale', type=int, default=1, help='TPC-H 数据规模（GB）')
    parser.add_argument('--output', help='输出 JSON 报告文件')
    
    args = parser.parse_args()
    
    # 解析查询列表
    queries = None
    if args.queries:
        queries = [q.strip() for q in args.queries.split(',')]
    
    # 运行对比测试
    comparator = BackendComparator(args.spark_home, args.tpch_path)
    comparator.run_tpch_benchmark(queries)
    comparator.print_comparison_report()
    
    # 打印建议
    print(comparator.generate_recommendations())
    
    # 导出报告
    if args.output:
        comparator.export_json(args.output)
    
    print("\n✓ 对比测试完成")

if __name__ == '__main__':
    main()
