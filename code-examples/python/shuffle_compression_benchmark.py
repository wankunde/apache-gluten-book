#!/usr/bin/env python3
"""
Apache Gluten Shuffle 压缩算法基准测试

功能：
1. 对比 LZ4、Zstd、Snappy 等压缩算法
2. 测试不同数据类型的压缩效果
3. 分析压缩比 vs 性能权衡
4. 生成性能报告和图表

相关章节：第8章 - Columnar Shuffle

使用：
```bash
python code-examples/python/shuffle_compression_benchmark.py --data-size 10000000
python code-examples/python/shuffle_compression_benchmark.py --output report.html
```

依赖：
pip install pandas matplotlib pyspark
"""

import time
import random
import string
import argparse
from typing import Dict, List, Tuple
from dataclasses import dataclass
import json

@dataclass
class CompressionResult:
    """压缩测试结果"""
    codec: str
    original_size: int
    compressed_size: int
    compression_time: float
    decompression_time: float
    compression_ratio: float
    throughput_mbps: float

class ShuffleCompressionBenchmark:
    """Shuffle 压缩基准测试"""
    
    CODECS = ['lz4', 'zstd', 'snappy', 'none']
    
    def __init__(self, data_size: int = 10000000):
        self.data_size = data_size
        self.results: List[CompressionResult] = []
        
    def generate_test_data(self, data_type: str = 'mixed') -> bytes:
        """生成测试数据
        
        Args:
            data_type: 数据类型
                - 'numeric': 数值数据（高压缩率）
                - 'string': 字符串数据（中等压缩率）
                - 'random': 随机数据（低压缩率）
                - 'mixed': 混合数据
        """
        print(f"[INFO] 生成 {data_type} 类型测试数据 ({self.data_size:,} 字节)...")
        
        if data_type == 'numeric':
            # 重复数值模式 - 高压缩率
            pattern = b''.join(str(i % 100).encode() for i in range(1000))
            data = pattern * (self.data_size // len(pattern) + 1)
            return data[:self.data_size]
            
        elif data_type == 'string':
            # 文本数据 - 中等压缩率
            words = ['user', 'order', 'product', 'category', 'price', 'date']
            text = ' '.join(random.choices(words, k=self.data_size // 10))
            return text.encode()[:self.data_size]
            
        elif data_type == 'random':
            # 随机数据 - 低压缩率
            return bytes(random.getrandbits(8) for _ in range(self.data_size))
            
        else:  # mixed
            # 混合数据
            numeric_part = self.generate_test_data('numeric')[:self.data_size // 3]
            string_part = self.generate_test_data('string')[:self.data_size // 3]
            random_part = self.generate_test_data('random')[:self.data_size // 3]
            return numeric_part + string_part + random_part
    
    def test_codec(self, codec: str, data: bytes) -> CompressionResult:
        """测试单个压缩算法"""
        print(f"  测试 {codec.upper()} 压缩...")
        
        if codec == 'none':
            return CompressionResult(
                codec=codec,
                original_size=len(data),
                compressed_size=len(data),
                compression_time=0.0,
                decompression_time=0.0,
                compression_ratio=1.0,
                throughput_mbps=float('inf')
            )
        
        # 导入压缩库
        if codec == 'lz4':
            import lz4.frame
            compress_func = lz4.frame.compress
            decompress_func = lz4.frame.decompress
        elif codec == 'zstd':
            import zstandard as zstd
            compressor = zstd.ZstdCompressor()
            decompressor = zstd.ZstdDecompressor()
            compress_func = compressor.compress
            decompress_func = decompressor.decompress
        elif codec == 'snappy':
            import snappy
            compress_func = snappy.compress
            decompress_func = snappy.decompress
        else:
            raise ValueError(f"不支持的编解码器: {codec}")
        
        # 压缩测试
        start_time = time.time()
        compressed = compress_func(data)
        compression_time = time.time() - start_time
        
        # 解压测试
        start_time = time.time()
        decompressed = decompress_func(compressed)
        decompression_time = time.time() - start_time
        
        # 验证
        if decompressed != data:
            raise ValueError(f"{codec} 压缩/解压验证失败")
        
        # 计算指标
        compression_ratio = len(data) / len(compressed)
        total_time = compression_time + decompression_time
        throughput_mbps = (len(data) / 1024 / 1024) / total_time if total_time > 0 else 0
        
        return CompressionResult(
            codec=codec,
            original_size=len(data),
            compressed_size=len(compressed),
            compression_time=compression_time,
            decompression_time=decompression_time,
            compression_ratio=compression_ratio,
            throughput_mbps=throughput_mbps
        )
    
    def run_benchmark(self, data_type: str = 'mixed') -> List[CompressionResult]:
        """运行完整基准测试"""
        print(f"\n{'='*70}")
        print(f"  Shuffle 压缩算法基准测试 - {data_type.upper()}")
        print(f"{'='*70}\n")
        
        data = self.generate_test_data(data_type)
        self.results = []
        
        for codec in self.CODECS:
            try:
                result = self.test_codec(codec, data)
                self.results.append(result)
            except ImportError:
                print(f"  ⚠ {codec.upper()} 库未安装，跳过")
            except Exception as e:
                print(f"  ✗ {codec.upper()} 测试失败: {e}")
        
        return self.results
    
    def print_results(self):
        """打印测试结果"""
        if not self.results:
            print("[WARNING] 没有测试结果")
            return
        
        print(f"\n{'━'*85}")
        print("测试结果汇总")
        print(f"{'━'*85}\n")
        
        # 表头
        print(f"{'算法':<12} {'原始大小':>12} {'压缩后':>12} {'压缩比':>10} "
              f"{'压缩时间':>12} {'解压时间':>12} {'吞吐量':>12}")
        print('─' * 85)
        
        # 结果行
        for result in sorted(self.results, key=lambda x: x.compression_ratio, reverse=True):
            print(f"{result.codec:<12} "
                  f"{self._format_size(result.original_size):>12} "
                  f"{self._format_size(result.compressed_size):>12} "
                  f"{result.compression_ratio:>9.2f}x "
                  f"{result.compression_time:>11.3f}s "
                  f"{result.decompression_time:>11.3f}s "
                  f"{result.throughput_mbps:>9.1f} MB/s")
        
        print()
        
        # 推荐
        self._print_recommendations()
    
    def _format_size(self, size: int) -> str:
        """格式化文件大小"""
        if size < 1024:
            return f"{size} B"
        elif size < 1024 * 1024:
            return f"{size/1024:.1f} KB"
        else:
            return f"{size/1024/1024:.1f} MB"
    
    def _print_recommendations(self):
        """打印推荐建议"""
        print("━━━ 推荐建议 ━━━\n")
        
        # 找出最佳压缩比
        best_compression = max(self.results, key=lambda x: x.compression_ratio)
        print(f"✓ 最佳压缩比: {best_compression.codec.upper()} "
              f"({best_compression.compression_ratio:.2f}x)")
        
        # 找出最快速度
        best_speed = max(self.results, key=lambda x: x.throughput_mbps)
        print(f"✓ 最快速度:   {best_speed.codec.upper()} "
              f"({best_speed.throughput_mbps:.1f} MB/s)")
        
        # 综合推荐
        print("\n场景推荐:")
        print("  • 网络带宽受限: 使用 ZSTD（最高压缩比）")
        print("  • CPU 资源充足: 使用 ZSTD（平衡性能和压缩）")
        print("  • 低延迟要求:   使用 LZ4（最快速度）")
        print("  • 通用场景:     使用 LZ4（Gluten 默认推荐）")
        print()
    
    def generate_spark_config(self) -> str:
        """生成 Spark 配置建议"""
        if not self.results:
            return ""
        
        best_codec = max(self.results, key=lambda x: x.compression_ratio)
        
        config = f"""
# Gluten Shuffle 压缩配置（基于基准测试）
# 测试数据大小: {self._format_size(self.data_size)}
# 推荐算法: {best_codec.codec.upper()}

spark.gluten.sql.columnar.shuffle.codec={best_codec.codec}

# 备选配置（根据场景选择）:
# - 最高压缩比: zstd (网络带宽受限场景)
# - 最快速度:   lz4  (低延迟场景)
# - 平衡选择:   lz4  (通用推荐)

# 其他相关配置
spark.shuffle.compress=true
spark.shuffle.spill.compress=true
"""
        return config
    
    def export_json(self, output_file: str):
        """导出 JSON 报告"""
        report = {
            "test_config": {
                "data_size": self.data_size,
                "codecs_tested": self.CODECS
            },
            "results": [
                {
                    "codec": r.codec,
                    "original_size": r.original_size,
                    "compressed_size": r.compressed_size,
                    "compression_ratio": r.compression_ratio,
                    "compression_time": r.compression_time,
                    "decompression_time": r.decompression_time,
                    "throughput_mbps": r.throughput_mbps
                }
                for r in self.results
            ],
            "spark_config": self.generate_spark_config()
        }
        
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(report, f, indent=2, ensure_ascii=False)
        
        print(f"[INFO] JSON 报告已导出: {output_file}")
    
    def plot_results(self, output_file: str = 'compression_benchmark.png'):
        """生成对比图表"""
        try:
            import matplotlib.pyplot as plt
            import matplotlib
            matplotlib.use('Agg')
        except ImportError:
            print("[WARNING] matplotlib 未安装，跳过图表生成")
            return
        
        fig, axes = plt.subplots(2, 2, figsize=(14, 10))
        fig.suptitle('Shuffle Compression Benchmark', fontsize=16, fontweight='bold')
        
        codecs = [r.codec for r in self.results]
        
        # 图1: 压缩比
        ax1 = axes[0, 0]
        ratios = [r.compression_ratio for r in self.results]
        bars1 = ax1.bar(codecs, ratios, color=['#4CAF50', '#2196F3', '#FF9800', '#9E9E9E'])
        ax1.set_title('Compression Ratio')
        ax1.set_ylabel('Ratio (higher is better)')
        for bar in bars1:
            height = bar.get_height()
            ax1.text(bar.get_x() + bar.get_width()/2., height,
                    f'{height:.2f}x', ha='center', va='bottom')
        
        # 图2: 压缩后大小
        ax2 = axes[0, 1]
        sizes = [r.compressed_size / 1024 / 1024 for r in self.results]  # MB
        bars2 = ax2.bar(codecs, sizes, color=['#4CAF50', '#2196F3', '#FF9800', '#9E9E9E'])
        ax2.set_title('Compressed Size')
        ax2.set_ylabel('Size (MB, lower is better)')
        for bar in bars2:
            height = bar.get_height()
            ax2.text(bar.get_x() + bar.get_width()/2., height,
                    f'{height:.1f}', ha='center', va='bottom')
        
        # 图3: 时间对比
        ax3 = axes[1, 0]
        compress_times = [r.compression_time * 1000 for r in self.results]  # ms
        decompress_times = [r.decompression_time * 1000 for r in self.results]
        x = range(len(codecs))
        width = 0.35
        ax3.bar([i - width/2 for i in x], compress_times, width, label='Compression', color='#4CAF50')
        ax3.bar([i + width/2 for i in x], decompress_times, width, label='Decompression', color='#2196F3')
        ax3.set_title('Compression/Decompression Time')
        ax3.set_ylabel('Time (ms, lower is better)')
        ax3.set_xticks(x)
        ax3.set_xticklabels(codecs)
        ax3.legend()
        
        # 图4: 吞吐量
        ax4 = axes[1, 1]
        throughputs = [r.throughput_mbps for r in self.results if r.throughput_mbps != float('inf')]
        throughput_codecs = [r.codec for r in self.results if r.throughput_mbps != float('inf')]
        bars4 = ax4.bar(throughput_codecs, throughputs, color=['#4CAF50', '#2196F3', '#FF9800'])
        ax4.set_title('Overall Throughput')
        ax4.set_ylabel('MB/s (higher is better)')
        for bar in bars4:
            height = bar.get_height()
            ax4.text(bar.get_x() + bar.get_width()/2., height,
                    f'{height:.0f}', ha='center', va='bottom')
        
        plt.tight_layout()
        plt.savefig(output_file, dpi=150, bbox_inches='tight')
        print(f"[INFO] 图表已保存: {output_file}")

def main():
    parser = argparse.ArgumentParser(description='Shuffle 压缩算法基准测试')
    parser.add_argument('--data-size', type=int, default=10000000,
                       help='测试数据大小（字节）')
    parser.add_argument('--data-type', choices=['numeric', 'string', 'random', 'mixed'],
                       default='mixed', help='数据类型')
    parser.add_argument('--output', help='输出文件（JSON格式）')
    parser.add_argument('--plot', help='生成图表文件（PNG格式）')
    
    args = parser.parse_args()
    
    # 运行基准测试
    benchmark = ShuffleCompressionBenchmark(data_size=args.data_size)
    benchmark.run_benchmark(data_type=args.data_type)
    benchmark.print_results()
    
    # 打印配置建议
    print("━━━ Spark 配置建议 ━━━")
    print(benchmark.generate_spark_config())
    
    # 导出结果
    if args.output:
        benchmark.export_json(args.output)
    
    if args.plot:
        benchmark.plot_results(args.plot)
    
    print("\n✓ 基准测试完成")

if __name__ == '__main__':
    main()
