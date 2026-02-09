#!/usr/bin/env python3
"""
Apache Gluten Fallback 原因分析工具

功能：
1. 解析 Spark UI 和执行计划日志
2. 自动分类 Fallback 原因
3. 统计 Fallback 模式
4. 生成优化报告

相关章节：第9章 - Fallback 机制

使用：
```bash
python code-examples/python/fallback_analysis.py --log spark-events.log
python code-examples/python/fallback_analysis.py --plan plan.txt --output report.html
```

依赖：
pip install pandas matplotlib
"""

import re
import json
import argparse
from collections import Counter, defaultdict
from typing import List, Dict, Tuple
from dataclasses import dataclass
from pathlib import Path

@dataclass
class FallbackCase:
    """Fallback 案例"""
    query_id: str
    operator: str
    reason: str
    category: str
    location: str
    severity: str  # 'high', 'medium', 'low'

class FallbackAnalyzer:
    """Fallback 分析器"""
    
    # Fallback 模式匹配规则
    PATTERNS = {
        'udf': (r'UDF|UserDefinedFunction', 'UDF 不支持'),
        'window': (r'Window|WindowExec', '窗口函数'),
        'sort': (r'Sort|SortExec', '排序算子'),
        'aggregate': (r'Aggregate|HashAggregate', '聚合函数'),
        'join': (r'Join|BroadcastHashJoin|SortMergeJoin', 'Join 算子'),
        'c2r': (r'ColumnarToRow', '列转行'),
        'r2c': (r'RowToColumnar', '行转列'),
        'datatype': (r'DecimalType|MapType|StructType', '数据类型不支持'),
        'unknown': (r'.*', '未知原因')
    }
    
    def __init__(self):
        self.fallback_cases: List[FallbackCase] = []
        self.statistics = defaultdict(int)
        
    def parse_execution_plan(self, plan_text: str, query_id: str = "default") -> List[FallbackCase]:
        """解析执行计划，提取 Fallback 信息"""
        cases = []
        lines = plan_text.split('\n')
        
        for i, line in enumerate(lines):
            # 检测 ColumnarToRow 和 RowToColumnar
            if 'ColumnarToRow' in line or 'RowToColumnar' in line:
                operator = 'ColumnarToRow' if 'ColumnarToRow' in line else 'RowToColumnar'
                
                # 尝试提取上下文
                context_start = max(0, i - 2)
                context_end = min(len(lines), i + 3)
                context = '\n'.join(lines[context_start:context_end])
                
                # 分析原因
                reason, category = self._classify_fallback(context)
                severity = self._assess_severity(operator, category)
                
                case = FallbackCase(
                    query_id=query_id,
                    operator=operator,
                    reason=reason,
                    category=category,
                    location=line.strip()[:100],
                    severity=severity
                )
                cases.append(case)
                self.fallback_cases.append(case)
                self.statistics[category] += 1
        
        return cases
    
    def _classify_fallback(self, context: str) -> Tuple[str, str]:
        """分类 Fallback 原因"""
        for category, (pattern, reason) in self.PATTERNS.items():
            if re.search(pattern, context, re.IGNORECASE):
                return reason, category
        return "未知原因", "unknown"
    
    def _assess_severity(self, operator: str, category: str) -> str:
        """评估 Fallback 严重程度"""
        high_severity = ['udf', 'aggregate', 'join']
        medium_severity = ['window', 'sort', 'datatype']
        
        if category in high_severity:
            return 'high'
        elif category in medium_severity:
            return 'medium'
        else:
            return 'low'
    
    def parse_log_file(self, log_path: str) -> None:
        """解析日志文件"""
        print(f"[INFO] 解析日志文件: {log_path}")
        
        with open(log_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 提取查询执行计划
        plan_blocks = re.findall(r'=== Physical Plan ===(.+?)(?:===|$)', content, re.DOTALL)
        
        for idx, plan in enumerate(plan_blocks):
            query_id = f"query_{idx + 1}"
            self.parse_execution_plan(plan, query_id)
        
        print(f"[INFO] 发现 {len(self.fallback_cases)} 个 Fallback 案例")
    
    def generate_report(self, output_format: str = 'text') -> str:
        """生成分析报告"""
        if output_format == 'text':
            return self._generate_text_report()
        elif output_format == 'json':
            return self._generate_json_report()
        elif output_format == 'html':
            return self._generate_html_report()
        else:
            raise ValueError(f"不支持的输出格式: {output_format}")
    
    def _generate_text_report(self) -> str:
        """生成文本报告"""
        lines = []
        lines.append("=" * 70)
        lines.append("          Apache Gluten Fallback 分析报告")
        lines.append("=" * 70)
        lines.append("")
        
        # 统计概览
        lines.append("━━━ 统计概览 ━━━")
        lines.append("")
        lines.append(f"总 Fallback 数: {len(self.fallback_cases)}")
        
        # 按类别统计
        lines.append("\n按类别分布:")
        for category, count in sorted(self.statistics.items(), key=lambda x: x[1], reverse=True):
            percentage = count / len(self.fallback_cases) * 100 if self.fallback_cases else 0
            lines.append(f"  • {category:15s}: {count:3d} ({percentage:5.1f}%)")
        
        # 按严重程度统计
        severity_count = Counter(case.severity for case in self.fallback_cases)
        lines.append("\n按严重程度:")
        for severity in ['high', 'medium', 'low']:
            count = severity_count.get(severity, 0)
            percentage = count / len(self.fallback_cases) * 100 if self.fallback_cases else 0
            icon = "🔴" if severity == 'high' else "🟡" if severity == 'medium' else "🟢"
            lines.append(f"  {icon} {severity.upper():6s}: {count:3d} ({percentage:5.1f}%)")
        
        # 详细案例
        if self.fallback_cases:
            lines.append("\n━━━ Fallback 详情 (前10个) ━━━\n")
            for i, case in enumerate(self.fallback_cases[:10], 1):
                severity_icon = "🔴" if case.severity == 'high' else "🟡" if case.severity == 'medium' else "🟢"
                lines.append(f"[{i}] {severity_icon} {case.operator}")
                lines.append(f"    查询: {case.query_id}")
                lines.append(f"    原因: {case.reason} ({case.category})")
                lines.append(f"    位置: {case.location}")
                lines.append("")
        
        # 优化建议
        lines.append("━━━ 优化建议 ━━━\n")
        lines.extend(self._generate_recommendations())
        
        return '\n'.join(lines)
    
    def _generate_json_report(self) -> str:
        """生成 JSON 报告"""
        report = {
            "summary": {
                "total_fallbacks": len(self.fallback_cases),
                "by_category": dict(self.statistics),
                "by_severity": dict(Counter(case.severity for case in self.fallback_cases))
            },
            "cases": [
                {
                    "query_id": case.query_id,
                    "operator": case.operator,
                    "reason": case.reason,
                    "category": case.category,
                    "severity": case.severity,
                    "location": case.location
                }
                for case in self.fallback_cases
            ],
            "recommendations": self._generate_recommendations()
        }
        return json.dumps(report, indent=2, ensure_ascii=False)
    
    def _generate_html_report(self) -> str:
        """生成 HTML 报告"""
        html = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Gluten Fallback 分析报告</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; }
        h1 { color: #1a73e8; border-bottom: 3px solid #1a73e8; padding-bottom: 10px; }
        h2 { color: #333; margin-top: 30px; }
        .stat-box { display: inline-block; margin: 10px; padding: 20px; background: #f0f7ff; border-radius: 8px; min-width: 150px; }
        .stat-number { font-size: 36px; font-weight: bold; color: #1a73e8; }
        .stat-label { color: #666; margin-top: 5px; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
        th { background: #1a73e8; color: white; }
        tr:hover { background: #f5f5f5; }
        .high { color: #d93025; font-weight: bold; }
        .medium { color: #f9ab00; font-weight: bold; }
        .low { color: #1e8e3e; }
        .recommendation { background: #fff3cd; padding: 15px; border-left: 4px solid #f9ab00; margin: 10px 0; }
        .chart { margin: 20px 0; padding: 20px; background: #f9f9f9; border-radius: 8px; }
    </style>
</head>
<body>
    <div class="container">
        <h1>🔍 Apache Gluten Fallback 分析报告</h1>
        
        <h2>📊 统计概览</h2>
        <div>
            <div class="stat-box">
                <div class="stat-number">{total_fallbacks}</div>
                <div class="stat-label">总 Fallback 数</div>
            </div>
            <div class="stat-box">
                <div class="stat-number">{high_severity}</div>
                <div class="stat-label">高优先级</div>
            </div>
            <div class="stat-box">
                <div class="stat-number">{medium_severity}</div>
                <div class="stat-label">中优先级</div>
            </div>
            <div class="stat-box">
                <div class="stat-number">{low_severity}</div>
                <div class="stat-label">低优先级</div>
            </div>
        </div>
        
        <h2>📋 类别分布</h2>
        <table>
            <tr><th>类别</th><th>数量</th><th>占比</th></tr>
            {category_rows}
        </table>
        
        <h2>🔎 Fallback 详情</h2>
        <table>
            <tr><th>查询ID</th><th>算子</th><th>原因</th><th>严重程度</th></tr>
            {detail_rows}
        </table>
        
        <h2>💡 优化建议</h2>
        {recommendations}
    </div>
</body>
</html>
"""
        
        severity_count = Counter(case.severity for case in self.fallback_cases)
        
        # 类别行
        category_rows = ""
        for category, count in sorted(self.statistics.items(), key=lambda x: x[1], reverse=True):
            percentage = count / len(self.fallback_cases) * 100 if self.fallback_cases else 0
            category_rows += f"<tr><td>{category}</td><td>{count}</td><td>{percentage:.1f}%</td></tr>\n"
        
        # 详情行
        detail_rows = ""
        for case in self.fallback_cases[:20]:  # 显示前20个
            severity_class = case.severity
            detail_rows += f"""<tr>
                <td>{case.query_id}</td>
                <td>{case.operator}</td>
                <td>{case.reason}</td>
                <td class="{severity_class}">{case.severity.upper()}</td>
            </tr>\n"""
        
        # 建议
        recommendations = ""
        for rec in self._generate_recommendations():
            recommendations += f'<div class="recommendation">{rec}</div>\n'
        
        return html.format(
            total_fallbacks=len(self.fallback_cases),
            high_severity=severity_count.get('high', 0),
            medium_severity=severity_count.get('medium', 0),
            low_severity=severity_count.get('low', 0),
            category_rows=category_rows,
            detail_rows=detail_rows,
            recommendations=recommendations
        )
    
    def _generate_recommendations(self) -> List[str]:
        """生成优化建议"""
        recommendations = []
        
        if 'udf' in self.statistics:
            recommendations.append(
                "🔴 高优先级: 发现 UDF Fallback。建议:\n"
                "   • 使用 Velox UDF 或 ClickHouse UDF 替代 Scala/Java UDF\n"
                "   • 如果可能，用 SQL 表达式替换 UDF 逻辑"
            )
        
        if 'aggregate' in self.statistics or 'join' in self.statistics:
            recommendations.append(
                "🟡 中优先级: 聚合或 Join 算子 Fallback。建议:\n"
                "   • 检查使用的聚合函数是否在 Gluten 支持列表中\n"
                "   • 尝试不同的 Join 策略 (Broadcast vs SortMerge)\n"
                "   • 升级到最新版本的 Gluten"
            )
        
        if 'datatype' in self.statistics:
            recommendations.append(
                "🟡 中优先级: 数据类型不支持。建议:\n"
                "   • 避免使用复杂数据类型 (Map, Struct, Decimal)\n"
                "   • 考虑数据模型优化，扁平化嵌套结构"
            )
        
        c2r_count = sum(1 for case in self.fallback_cases if case.operator == 'ColumnarToRow')
        if c2r_count > len(self.fallback_cases) * 0.3:
            recommendations.append(
                "🔴 高优先级: ColumnarToRow 比例过高。建议:\n"
                "   • 检查查询逻辑，减少行列转换\n"
                "   • 重新组织查询，让更多操作在列式引擎中完成"
            )
        
        if not recommendations:
            recommendations.append("✅ 当前 Fallback 情况良好，建议继续关注新查询的执行情况")
        
        return recommendations
    
    def export_to_file(self, output_path: str, output_format: str = 'text') -> None:
        """导出报告到文件"""
        report = self.generate_report(output_format)
        
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(report)
        
        print(f"[INFO] 报告已导出到: {output_path}")

def main():
    parser = argparse.ArgumentParser(description='Gluten Fallback 分析工具')
    parser.add_argument('--log', help='Spark 日志文件路径')
    parser.add_argument('--plan', help='执行计划文件路径')
    parser.add_argument('--output', default='fallback_report.txt', help='输出文件路径')
    parser.add_argument('--format', choices=['text', 'json', 'html'], default='text', help='输出格式')
    
    args = parser.parse_args()
    
    analyzer = FallbackAnalyzer()
    
    if args.log:
        analyzer.parse_log_file(args.log)
    elif args.plan:
        with open(args.plan, 'r', encoding='utf-8') as f:
            plan_text = f.read()
        analyzer.parse_execution_plan(plan_text)
    else:
        # 演示模式
        print("[INFO] 演示模式 - 使用模拟数据\n")
        demo_plan = """
        == Physical Plan ==
        *(2) HashAggregate(keys=[category#10], functions=[sum(amount#11)])
        +- Exchange hashpartitioning(category#10, 200)
           +- *(1) HashAggregate(keys=[category#10], functions=[partial_sum(amount#11)])
              +- *(1) ColumnarToRow
                 +- FileScan parquet [category#10,amount#11]
        
        *(3) Project [id#20, custom_udf(value#21) AS result#30]
        +- *(3) ColumnarToRow
           +- WholeStageCodegen (2)
        """
        analyzer.parse_execution_plan(demo_plan, "demo_query")
    
    # 生成报告
    if analyzer.fallback_cases:
        analyzer.export_to_file(args.output, args.format)
        
        # 控制台输出摘要
        print("\n" + analyzer.generate_report('text'))
    else:
        print("[INFO] 未发现 Fallback 案例")

if __name__ == '__main__':
    main()
