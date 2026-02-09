# Apache Gluten 深入浅出

<p align="center">
  <img src="images/book-cover.png" alt="Apache Gluten Book" width="400">
</p>

## 欢迎！

欢迎来到《Apache Gluten 深入浅出》！这是一本全面介绍 Apache Gluten 的中文书籍，旨在帮助读者从零开始学习 Gluten，深入理解其架构原理，掌握后端执行引擎的使用和优化。

## 🎯 本书特点

- **系统全面**：涵盖从入门到精通的所有内容
- **深入浅出**：循序渐进，适合不同层次的读者
- **实战导向**：丰富的代码示例和真实案例
- **持续更新**：跟随 Gluten 版本更新而更新

## 📖 适合谁读

### 初级读者
如果你是大数据工程师，想要了解和使用 Gluten 来加速 Spark 查询，本书的**第一部分（入门篇）**将帮助你快速上手。

### 中级读者
如果你想深入理解 Gluten 的架构原理、内存管理、查询计划转换等核心机制，**第二部分（架构篇）**和**第三部分（后端引擎篇）**将满足你的需求。

### 高级读者
如果你希望为 Gluten 贡献代码、开发 UDF 或进行定制化开发，**第四部分（源码剖析篇）**将带你深入源码，掌握扩展开发技能。

### 实践者
无论你处于哪个层次，**第五部分（实战篇）**都提供了生产环境部署、真实案例分析和故障排查的宝贵经验。

## 🚀 快速导航

### [第一部分：入门篇](chapters/part1-beginner/chapter01-introduction.md)

从零开始了解 Gluten，快速上手使用。

- [第1章：Gluten 简介](chapters/part1-beginner/chapter01-introduction.md) - 什么是 Gluten，为什么需要它
- [第2章：快速入门](chapters/part1-beginner/chapter02-quick-start.md) - 安装和运行第一个示例
- [第3章：Gluten 使用指南](chapters/part1-beginner/chapter03-usage-guide.md) - 配置、调优和最佳实践

### [第二部分：架构篇](chapters/part2-architecture/chapter04-overall-architecture.md)

深入理解 Gluten 的核心架构和机制。

### [第三部分：后端引擎篇](chapters/part3-backend-engines/chapter11-velox-backend.md)

详解 Velox 和 ClickHouse 两大后端引擎。

### [第四部分：源码剖析篇](chapters/part4-source-code/chapter14-dev-environment.md)

源码分析和扩展开发指南。

### [第五部分：实战篇](chapters/part5-practice/chapter20-production-deployment.md)

生产环境部署和真实案例分析。

### [第六部分：社区与未来](chapters/part6-community/chapter23-community-participation.md)

参与社区和了解 Gluten 的未来发展。

## 💡 如何阅读本书

### 按顺序阅读
建议初学者从第一章开始，按顺序阅读，这样可以建立完整的知识体系。

### 按需查阅
如果你已经有一定基础，可以直接跳到感兴趣的章节。每个章节都尽量保持独立性。

### 动手实践
书中包含大量代码示例，强烈建议边读边实践。所有示例都在 [code-examples](code-examples/) 目录中。

**代码示例亮点**：
- 📊 **16+ 生产级工具**：内存监控、Fallback 检测、性能对比等
- 🔧 **完整配置模板**：Velox & ClickHouse 开箱即用
- 🚀 **自动化脚本**：后端切换、性能测试一键完成
- 📚 **6,000+ 行代码**：涵盖所有核心功能和最佳实践

详见 [代码示例完整列表](code-examples/README.md)

### 参考附录
遇到不熟悉的术语或配置参数，可以查阅附录获得快速参考。

## 🤝 参与贡献

本书是开源的，欢迎任何形式的贡献！

- 发现错误？[提交 Issue](https://github.com/wankunde/apache-gluten-book/issues)
- 想要贡献内容？查看 [贡献指南](CONTRIBUTING.md)
- 有问题或建议？在 [Discussions](https://github.com/wankunde/apache-gluten-book/discussions) 中讨论

## 📚 相关资源

- [Apache Gluten 官网](https://gluten.apache.org/)
- [Gluten GitHub](https://github.com/apache/incubator-gluten)
- [Velox 项目](https://github.com/facebookincubator/velox)
- [ClickHouse 官网](https://clickhouse.com/)

## 📝 版权声明

本书采用 [Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License](LICENSE) 许可。

---

<p align="center">
  <b>开始你的 Gluten 学习之旅吧！👉 <a href="chapters/part1-beginner/chapter01-introduction.md">第1章：Gluten 简介</a></b>
</p>
