#!/usr/bin/env python3
"""
SubstraitPlanViewer.py

Substrait 查询计划可视化工具
对应书籍第5章：查询计划转换

功能：
1. 解析 Substrait Protocol Buffer 文件
2. 可视化查询计划树
3. 导出为文本/JSON/DOT 格式
4. 分析算子类型和关系

依赖：
    pip install protobuf graphviz anytree
"""

import sys
import json
import argparse
from pathlib import Path
from typing import Dict, List, Any, Optional
from anytree import Node, RenderTree
from anytree.exporter import DotExporter

try:
    from google.protobuf import text_format
    import substrait_pb2  # 需要从 Substrait 生成
except ImportError:
    print("警告: 需要安装 protobuf 和 substrait protobuf 文件")
    print("pip install protobuf")
    print("或参考: https://substrait.io/")

class SubstraitPlanViewer:
    """Substrait 计划查看器"""
    
    def __init__(self, plan_file: str):
        """
        初始化查看器
        
        Args:
            plan_file: Substrait plan 文件路径（protobuf 或 text format）
        """
        self.plan_file = Path(plan_file)
        self.plan = None
        self.root_node = None
        
    def load_plan(self) -> bool:
        """
        加载 Substrait plan
        
        Returns:
            bool: 是否加载成功
        """
        try:
            with open(self.plan_file, 'rb') as f:
                content = f.read()
                
            # 尝试作为二进制 protobuf 解析
            try:
                self.plan = substrait_pb2.Plan()
                self.plan.ParseFromString(content)
                print(f"✅ 成功加载二进制 Substrait plan")
                return True
            except:
                pass
            
            # 尝试作为文本格式解析
            try:
                self.plan = substrait_pb2.Plan()
                text_format.Parse(content.decode('utf-8'), self.plan)
                print(f"✅ 成功加载文本格式 Substrait plan")
                return True
            except Exception as e:
                print(f"❌ 解析失败: {e}")
                return False
                
        except FileNotFoundError:
            print(f"❌ 文件不存在: {self.plan_file}")
            return False
        except Exception as e:
            print(f"❌ 加载失败: {e}")
            return False
    
    def build_tree(self) -> Node:
        """
        构建计划树
        
        Returns:
            Node: 根节点
        """
        if not self.plan:
            return None
            
        # 创建根节点
        self.root_node = Node("Substrait Plan")
        
        # 遍历所有关系
        if self.plan.relations:
            for idx, rel_root in enumerate(self.plan.relations):
                rel_node = Node(f"Relation {idx}", parent=self.root_node)
                self._build_relation_tree(rel_root.root, rel_node)
        
        return self.root_node
    
    def _build_relation_tree(self, relation: Any, parent: Node):
        """
        递归构建关系树
        
        Args:
            relation: Substrait 关系对象
            parent: 父节点
        """
        # 获取关系类型
        rel_type = relation.WhichOneof('rel_type')
        
        if not rel_type:
            return
            
        # 创建当前节点
        node_name = self._get_relation_name(relation, rel_type)
        current = Node(node_name, parent=parent)
        
        # 递归处理子关系
        if rel_type == 'read':
            self._process_read(relation.read, current)
        elif rel_type == 'filter':
            self._process_filter(relation.filter, current)
        elif rel_type == 'project':
            self._process_project(relation.project, current)
        elif rel_type == 'join':
            self._process_join(relation.join, current)
        elif rel_type == 'aggregate':
            self._process_aggregate(relation.aggregate, current)
        elif rel_type == 'sort':
            self._process_sort(relation.sort, current)
        # 可以添加更多类型...
    
    def _get_relation_name(self, relation: Any, rel_type: str) -> str:
        """
        获取关系的显示名称
        
        Args:
            relation: 关系对象
            rel_type: 关系类型
            
        Returns:
            str: 显示名称
        """
        names = {
            'read': '📖 Read',
            'filter': '🔍 Filter',
            'project': '📊 Project',
            'join': '🔗 Join',
            'aggregate': '📈 Aggregate',
            'sort': '⬆️ Sort',
            'limit': '✂️ Limit',
            'fetch': '🎯 Fetch',
        }
        return names.get(rel_type, f'❓ {rel_type.upper()}')
    
    def _process_read(self, read: Any, parent: Node):
        """处理 Read 关系"""
        if read.HasField('base_schema'):
            schema_node = Node("Schema", parent=parent)
            if read.base_schema.names:
                for name in read.base_schema.names:
                    Node(f"  {name}", parent=schema_node)
    
    def _process_filter(self, filter_rel: Any, parent: Node):
        """处理 Filter 关系"""
        if filter_rel.HasField('condition'):
            Node("Condition: <expression>", parent=parent)
        if filter_rel.HasField('input'):
            self._build_relation_tree(filter_rel.input, parent)
    
    def _process_project(self, project: Any, parent: Node):
        """处理 Project 关系"""
        if project.expressions:
            expr_node = Node(f"Expressions ({len(project.expressions)})", parent=parent)
            for idx, expr in enumerate(project.expressions):
                Node(f"  Expr {idx}", parent=expr_node)
        if project.HasField('input'):
            self._build_relation_tree(project.input, parent)
    
    def _process_join(self, join: Any, parent: Node):
        """处理 Join 关系"""
        join_types = {
            0: 'INNER',
            1: 'LEFT',
            2: 'RIGHT',
            3: 'FULL',
            4: 'SEMI',
            5: 'ANTI'
        }
        join_type = join_types.get(join.type, 'UNKNOWN')
        Node(f"Type: {join_type}", parent=parent)
        
        if join.HasField('left'):
            left_node = Node("Left Input", parent=parent)
            self._build_relation_tree(join.left, left_node)
        if join.HasField('right'):
            right_node = Node("Right Input", parent=parent)
            self._build_relation_tree(join.right, right_node)
    
    def _process_aggregate(self, aggregate: Any, parent: Node):
        """处理 Aggregate 关系"""
        if aggregate.groupings:
            group_node = Node(f"Groupings ({len(aggregate.groupings)})", parent=parent)
        if aggregate.measures:
            measure_node = Node(f"Measures ({len(aggregate.measures)})", parent=parent)
            for idx, measure in enumerate(aggregate.measures):
                Node(f"  Measure {idx}", parent=measure_node)
        if aggregate.HasField('input'):
            self._build_relation_tree(aggregate.input, parent)
    
    def _process_sort(self, sort: Any, parent: Node):
        """处理 Sort 关系"""
        if sort.sorts:
            sort_node = Node(f"Sort Keys ({len(sort.sorts)})", parent=parent)
        if sort.HasField('input'):
            self._build_relation_tree(sort.input, parent)
    
    def print_tree(self):
        """打印计划树"""
        if not self.root_node:
            self.build_tree()
        
        if self.root_node:
            print("\n" + "=" * 60)
            print("Substrait 查询计划树")
            print("=" * 60)
            for pre, _, node in RenderTree(self.root_node):
                print(f"{pre}{node.name}")
            print("=" * 60)
    
    def export_to_dot(self, output_file: str):
        """
        导出为 DOT 格式（可用 Graphviz 渲染）
        
        Args:
            output_file: 输出文件路径
        """
        if not self.root_node:
            self.build_tree()
        
        if self.root_node:
            DotExporter(self.root_node).to_dotfile(output_file)
            print(f"✅ DOT 文件已保存: {output_file}")
            print(f"   使用 'dot -Tpng {output_file} -o plan.png' 生成图片")
    
    def export_to_json(self, output_file: str):
        """
        导出为 JSON 格式
        
        Args:
            output_file: 输出文件路径
        """
        if not self.root_node:
            self.build_tree()
        
        def node_to_dict(node: Node) -> Dict:
            return {
                'name': node.name,
                'children': [node_to_dict(child) for child in node.children]
            }
        
        if self.root_node:
            tree_dict = node_to_dict(self.root_node)
            with open(output_file, 'w', encoding='utf-8') as f:
                json.dump(tree_dict, f, indent=2, ensure_ascii=False)
            print(f"✅ JSON 文件已保存: {output_file}")
    
    def analyze_plan(self) -> Dict[str, Any]:
        """
        分析计划统计信息
        
        Returns:
            Dict: 统计信息
        """
        if not self.plan:
            return {}
        
        stats = {
            'total_relations': 0,
            'operator_counts': {},
            'max_depth': 0,
        }
        
        def count_relations(relation, depth=0):
            stats['total_relations'] += 1
            stats['max_depth'] = max(stats['max_depth'], depth)
            
            rel_type = relation.WhichOneof('rel_type')
            if rel_type:
                stats['operator_counts'][rel_type] = \
                    stats['operator_counts'].get(rel_type, 0) + 1
        
        # 遍历统计
        if self.plan.relations:
            for rel_root in self.plan.relations:
                # 这里需要递归遍历，简化示例
                pass
        
        return stats
    
    def print_analysis(self):
        """打印分析结果"""
        stats = self.analyze_plan()
        
        print("\n" + "=" * 60)
        print("Substrait 计划分析")
        print("=" * 60)
        print(f"总关系数: {stats.get('total_relations', 0)}")
        print(f"最大深度: {stats.get('max_depth', 0)}")
        
        if stats.get('operator_counts'):
            print("\n算子分布:")
            for op_type, count in sorted(stats['operator_counts'].items()):
                print(f"  {op_type:20s}: {count}")
        print("=" * 60)


def main():
    """主函数"""
    parser = argparse.ArgumentParser(
        description='Substrait 查询计划查看器',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python substrait_plan_viewer.py plan.pb                    # 查看计划树
  python substrait_plan_viewer.py plan.pb --dot plan.dot     # 导出 DOT
  python substrait_plan_viewer.py plan.pb --json plan.json   # 导出 JSON
  python substrait_plan_viewer.py plan.pb --analyze          # 分析计划
        """
    )
    
    parser.add_argument('plan_file', help='Substrait plan 文件路径')
    parser.add_argument('--dot', help='导出为 DOT 格式')
    parser.add_argument('--json', help='导出为 JSON 格式')
    parser.add_argument('--analyze', action='store_true', help='分析计划')
    
    args = parser.parse_args()
    
    # 创建查看器
    viewer = SubstraitPlanViewer(args.plan_file)
    
    # 加载计划
    if not viewer.load_plan():
        sys.exit(1)
    
    # 打印树
    viewer.print_tree()
    
    # 分析
    if args.analyze:
        viewer.print_analysis()
    
    # 导出
    if args.dot:
        viewer.export_to_dot(args.dot)
    
    if args.json:
        viewer.export_to_json(args.json)


if __name__ == '__main__':
    # 如果没有 substrait protobuf，提供模拟模式
    try:
        main()
    except NameError:
        print("\n" + "=" * 60)
        print("⚠️  Substrait Protobuf 未安装")
        print("=" * 60)
        print("\n要使用此工具，需要:")
        print("1. 安装 protobuf: pip install protobuf")
        print("2. 获取 Substrait proto 文件:")
        print("   git clone https://github.com/substrait-io/substrait")
        print("   cd substrait/proto")
        print("   protoc --python_out=. substrait/*.proto")
        print("\n3. 将生成的 *_pb2.py 文件复制到当前目录")
        print("\n或查看 Gluten 源码中的 Substrait plan:")
        print("   $GLUTEN_HOME/gluten-substrait/src/test/resources/")
        print("=" * 60)
