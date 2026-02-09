# 图片和图表

本目录包含书中使用的所有图片、图表和示意图。

## 目录

### 架构图
- `architecture-diagrams.md` - Gluten 整体架构的 Mermaid 图表
- `gluten-architecture.png` - 架构概览图（待添加）
- `data-flow.png` - 数据流转示意图（待添加）

### 性能图表
- `performance-charts.md` - 性能对比的文本图表
- `velox-performance-chart.png` - Velox 后端性能对比图（待添加）
- `clickhouse-performance-chart.png` - ClickHouse 后端性能对比图（待添加）

### UI 截图
- `spark-ui-gluten-operators.png` - Spark UI 中的 Gluten 算子（待添加）
- `gluten-ui-fallback.png` - Gluten UI Fallback 信息（待添加）
- `gluten-ui.png` - Gluten UI 主界面（待添加）

### 概念示意图
- `columnar-vs-row.png` - 列式 vs 行式存储对比（待添加）
- `simd-vectorization.png` - SIMD 向量化示意图（待添加）

## 生成图表

### 使用 Mermaid
本书的图表使用 Mermaid 语法编写，可以：
1. 直接在支持 Mermaid 的 Markdown 编辑器中查看
2. 使用 [Mermaid Live Editor](https://mermaid.live/) 在线编辑
3. 使用 MkDocs 自动渲染

### 导出为图片
如需将 Mermaid 图表导出为 PNG：

```bash
# 安装 mermaid-cli
npm install -g @mermaid-js/mermaid-cli

# 转换
mmdc -i architecture-diagrams.md -o architecture-diagrams.png
```

## 占位符图片

当前某些图片使用占位符标记，待后续添加实际截图。

## 贡献

欢迎贡献更多图表和示意图！
