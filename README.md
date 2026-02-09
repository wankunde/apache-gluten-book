# Apache Gluten 深入浅出

<!-- 
<p align="center">
  <img src="images/book-cover.png" alt="Apache Gluten Book" width="300">
</p>
-->

> 一本从入门到精通的 Apache Gluten 中文指南

[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc-sa/4.0/)
[![GitHub Stars](https://img.shields.io/github/stars/wankunde/apache-gluten-book?style=social)](https://github.com/wankunde/apache-gluten-book)

## 📖 关于本书

本书是一本全面介绍 Apache Gluten 的中文书籍，旨在帮助读者从零开始学习 Gluten，深入理解其架构原理，掌握后端执行引擎（Velox 和 ClickHouse）的使用和优化。

### 目标读者

- **初级读者**：希望了解和使用 Gluten 的大数据工程师
- **中级读者**：希望深入理解 Gluten 架构的开发者
- **高级读者**：希望贡献代码或进行定制化开发的核心开发者

### 为什么要写这本书

Apache Gluten 是一个革命性的项目，它通过将 Spark SQL 的执行卸载到原生执行引擎（如 Velox 和 ClickHouse），实现了显著的性能提升。然而，目前缺少系统性的中文学习资料。本书旨在填补这一空白，让更多的中文开发者能够学习和使用 Gluten。

## 📚 目录结构

### 第一部分：入门篇
- [第1章：Gluten 简介](chapters/part1-beginner/chapter01-introduction.md)
- [第2章：快速入门](chapters/part1-beginner/chapter02-quick-start.md)
- [第3章：Gluten 使用指南](chapters/part1-beginner/chapter03-usage-guide.md)

### 第二部分：架构篇
- [第4章：Gluten 整体架构](chapters/part2-architecture/chapter04-overall-architecture.md)
- [第5章：查询计划转换](chapters/part2-architecture/chapter05-query-plan-transformation.md)
- [第6章：内存管理](chapters/part2-architecture/chapter06-memory-management.md)
- [第7章：数据格式与传输](chapters/part2-architecture/chapter07-data-format-and-transfer.md)
- [第8章：Columnar Shuffle](chapters/part2-architecture/chapter08-columnar-shuffle.md)
- [第9章：Fallback 机制深入](chapters/part2-architecture/chapter09-fallback-mechanism.md)
- [第10章：多版本兼容（Shim Layer）](chapters/part2-architecture/chapter10-shim-layer.md)

### 第三部分：后端引擎篇
- [第11章：Velox 后端详解](chapters/part3-backends/chapter11-velox-backend.md)
- [第12章：ClickHouse 后端详解](chapters/part3-backends/chapter12-clickhouse-backend.md)
- [第13章：后端对比与选择](chapters/part3-backends/chapter13-backend-comparison.md)

### 第四部分：源码剖析篇
- [第14章：源码环境搭建](chapters/part4-source-code/chapter14-dev-environment-setup.md)
- [第15章：核心模块源码分析](chapters/part4-source-code/chapter15-core-modules-analysis.md)
- [第16章：算子实现剖析](chapters/part4-source-code/chapter16-operator-implementation.md)
- [第17章：扩展开发](chapters/part4-source-code/chapter17-extension-development.md)
- [第18章：测试与质量保证](chapters/part4-source-code/chapter18-testing-quality.md)
- [第19章：性能分析与调优](chapters/part4-source-code/chapter19-performance-tuning.md)

### 第五部分：实战篇
- [第20章：生产环境部署](chapters/part5-practice/chapter20-production-deployment.md)
- [第21章：案例分析](chapters/part5-practice/chapter21-case-studies.md)
- [第22章：故障排查实战](chapters/part5-practice/chapter22-troubleshooting.md)

### 第六部分：社区与未来
- [第23章：社区参与](chapters/part6-community/chapter23-community-participation.md)
- [第24章：Gluten 的未来](chapters/part6-community/chapter24-future-of-gluten.md)

### 附录
- [附录A：配置参数速查表](appendices/appendix-a-configuration-reference.md)
- [附录B：函数支持列表](appendices/appendix-b-function-support-list.md)
- [附录C：术语表](appendices/appendix-c-glossary.md)
- [附录D：参考资源](appendices/appendix-d-reference-resources.md)

## 🚀 快速开始

### 在线阅读

- **GitHub Pages**: [https://wankunde.github.io/apache-gluten-book](https://wankunde.github.io/apache-gluten-book)
- **Google Docs**: [链接待添加]

### 本地阅读

```bash
# 克隆仓库
git clone https://github.com/wankunde/apache-gluten-book.git
cd apache-gluten-book

# 使用 Markdown 阅读器查看
# 或者使用 MkDocs 构建静态站点（可选）
pip install mkdocs mkdocs-material
mkdocs serve
# 访问 http://localhost:8000
```

## 📝 代码示例

本书包含丰富的**生产级代码示例**，位于 `code-examples/` 目录：

### 📊 统计概览
- **总文件数**: 24 个（16 个新增核心工具 + 8 个原有示例）
- **代码行数**: ~6,000 行
- **覆盖章节**: 第2-13章
- **质量等级**: 生产级（完整注释、错误处理、使用文档）

### 🎯 核心工具亮点

#### 性能分析工具
- **PlanTransformationDemo.scala** - 执行计划对比分析
- **MemoryMonitoring.scala** - 内存监控和泄漏检测
- **ColumnarShuffleDemo.scala** - Shuffle 性能对比

#### 问题诊断工具
- **FallbackDetection.scala** - 自动 Fallback 检测
- **fallback_analysis.py** - Fallback 原因分析和报告生成

#### 后端对比工具
- **backend_comparison.py** - Velox vs ClickHouse 自动化测试
- **switch-backend.sh** - 一键后端切换脚本

#### 配置模板
- **velox-config.conf** - Velox 完整配置（205行）
- **clickhouse-config.conf** - ClickHouse 完整配置（156行）

#### 开发示例
- **velox_udf_example.cpp** - Velox Native UDF 开发
- **shuffle_compression_benchmark.py** - 压缩算法基准测试

📖 **完整列表和使用说明**: [code-examples/README.md](code-examples/README.md)

### 按语言分类
- **Scala**: [code-examples/scala/](code-examples/scala/) - 9 个文件，3,100+ 行
- **Python**: [code-examples/python/](code-examples/python/) - 7 个文件，2,200+ 行
- **Shell**: [code-examples/shell/](code-examples/shell/) - 3 个文件，440 行
- **Config**: [code-examples/configs/](code-examples/configs/) - 4 个文件，480 行
- **C++**: [code-examples/cpp/](code-examples/cpp/) - 1 个文件，51 行

## 🤝 贡献指南

我们欢迎任何形式的贡献！如果你发现错误、有改进建议，或者想要添加新的内容，请：

1. Fork 本仓库
2. 创建你的特性分支 (`git checkout -b feature/amazing-content`)
3. 提交你的改动 (`git commit -m '添加了某某内容'`)
4. 推送到分支 (`git push origin feature/amazing-content`)
5. 开启一个 Pull Request

### 贡献者

感谢所有为本书做出贡献的人！

<a href="https://github.com/wankunde/apache-gluten-book/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=wankunde/apache-gluten-book" />
</a>

## 📄 许可证

本书采用 [Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License](LICENSE) 许可。

- ✅ 可以分享和改编本书内容
- ✅ 必须注明原作者
- ❌ 不得用于商业用途
- ✅ 改编作品必须使用相同许可

## 🔗 相关资源

### Apache Gluten 官方资源
- [Apache Gluten 官网](https://gluten.apache.org/)
- [GitHub 仓库](https://github.com/apache/incubator-gluten)
- [官方文档](https://github.com/apache/incubator-gluten/tree/main/docs)
- [邮件列表](mailto:dev@gluten.apache.org)

### 后端引擎
- [Velox](https://github.com/facebookincubator/velox)
- [ClickHouse](https://clickhouse.com/)
- [Substrait](https://substrait.io/)

### 社区
- [Slack Channel](https://the-asf.slack.com/) - 频道：#incubator-gluten
- [GitHub Discussions](https://github.com/apache/incubator-gluten/discussions)

## 📮 联系方式

如有任何问题或建议，欢迎通过以下方式联系：

- 提交 [GitHub Issue](https://github.com/wankunde/apache-gluten-book/issues)
- 发送邮件至：[wankunde@163.com]

## ⭐ Star History

如果本书对你有帮助，请给我们一个 Star！

[![Star History Chart](https://api.star-history.com/svg?repos=wankunde/apache-gluten-book&type=Date)](https://star-history.com/#wankunde/apache-gluten-book&Date)

## 📊 进度

**本书已完成！** ✅

- [x] 项目框架搭建
- [x] 第一部分：入门篇（3/3 章完成）
- [x] 第二部分：架构篇（7/7 章完成）
- [x] 第三部分：后端引擎篇（3/3 章完成）
- [x] 第四部分：源码剖析篇（6/6 章完成）
- [x] 第五部分：实战篇（3/3 章完成）
- [x] 第六部分：社区与未来（2/2 章完成）
- [x] 附录（4/4 个附录完成）

**统计数据**：
- 📖 总章节：24章 + 4个附录
- 📝 总字符数：~535,000
- 📄 总页数：~23,500 行
- 💾 代码示例：7个完整示例
- 🎨 架构图：7+ 个 Mermaid 图表

---

<p align="center">
  <b>让我们一起学习 Apache Gluten，加速大数据处理！</b>
</p>

<p align="center">
  Made with ❤️ by the Apache Gluten Community
</p>
