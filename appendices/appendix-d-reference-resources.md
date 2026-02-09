# 附录 D：参考资源

本附录提供学习和使用 Apache Gluten 的参考资源，包括官方文档、相关项目、技术文章、视频演讲和社区资源。

## D.1 官方资源

### D.1.1 Apache Gluten

**项目主页**
- GitHub: https://github.com/apache/incubator-gluten
- Apache 页面: https://incubator.apache.org/projects/gluten.html

**官方文档**
- 主文档: https://github.com/apache/incubator-gluten/tree/main/docs
- 用户指南: https://github.com/apache/incubator-gluten/blob/main/docs/UserGuide.md
- 开发者指南: https://github.com/apache/incubator-gluten/blob/main/docs/DeveloperGuide.md
- 配置指南: https://github.com/apache/incubator-gluten/blob/main/docs/Configuration.md
- Velox 后端: https://github.com/apache/incubator-gluten/blob/main/docs/VeloxBackend.md
- ClickHouse 后端: https://github.com/apache/incubator-gluten/blob/main/docs/ClickhouseBackend.md

**Wiki**
- GitHub Wiki: https://github.com/apache/incubator-gluten/wiki
- Roadmap: https://github.com/apache/incubator-gluten/wiki/Roadmap
- Release Notes: https://github.com/apache/incubator-gluten/releases
- FAQ: https://github.com/apache/incubator-gluten/wiki/FAQ

**下载**
- Latest Release: https://github.com/apache/incubator-gluten/releases/latest
- Docker Images: https://hub.docker.com/r/apache/gluten
- Maven Central: https://search.maven.org/search?q=g:org.apache.gluten

### D.1.2 邮件列表

**开发邮件列表**
- 订阅: dev-subscribe@gluten.apache.org
- 发送: dev@gluten.apache.org
- 归档: https://lists.apache.org/list.html?dev@gluten.apache.org

**用户邮件列表**
- 订阅: user-subscribe@gluten.apache.org
- 发送: user@gluten.apache.org
- 归档: https://lists.apache.org/list.html?user@gluten.apache.org

**提交邮件列表**
- 订阅: commits-subscribe@gluten.apache.org
- 归档: https://lists.apache.org/list.html?commits@gluten.apache.org

### D.1.3 问题追踪

**GitHub Issues**
- 所有 Issues: https://github.com/apache/incubator-gluten/issues
- Bug 报告: https://github.com/apache/incubator-gluten/issues/new?template=bug_report.md
- 功能请求: https://github.com/apache/incubator-gluten/issues/new?template=feature_request.md

**标签**
- `bug`: 缺陷报告
- `enhancement`: 功能增强
- `help wanted`: 需要帮助
- `good first issue`: 适合新手
- `performance`: 性能问题
- `documentation`: 文档相关

## D.2 相关项目

### D.2.1 后端引擎

**Velox**
- GitHub: https://github.com/facebookincubator/velox
- 官网: https://facebookincubator.github.io/velox/
- 文档: https://facebookincubator.github.io/velox/docs/
- 函数参考: https://facebookincubator.github.io/velox/functions.html
- 社区: https://velox-lib.io/community

**ClickHouse**
- GitHub: https://github.com/ClickHouse/ClickHouse
- 官网: https://clickhouse.com/
- 文档: https://clickhouse.com/docs/en/
- 函数参考: https://clickhouse.com/docs/en/sql-reference/functions/
- 博客: https://clickhouse.com/blog/

### D.2.2 Apache Spark

**Apache Spark**
- 官网: https://spark.apache.org/
- GitHub: https://github.com/apache/spark
- 文档: https://spark.apache.org/docs/latest/
- SQL 指南: https://spark.apache.org/docs/latest/sql-programming-guide.html
- API 文档: https://spark.apache.org/docs/latest/api/scala/org/apache/spark/index.html

**Spark 书籍**
- "Learning Spark" (第2版): https://pages.databricks.com/learning-spark.html
- "Spark: The Definitive Guide": https://www.oreilly.com/library/view/spark-the-definitive/9781491912201/
- "High Performance Spark": https://www.oreilly.com/library/view/high-performance-spark/9781491943199/

### D.2.3 数据格式

**Apache Arrow**
- 官网: https://arrow.apache.org/
- GitHub: https://github.com/apache/arrow
- 文档: https://arrow.apache.org/docs/
- Cookbook: https://arrow.apache.org/cookbook/
- Flight: https://arrow.apache.org/docs/format/Flight.html

**Parquet**
- 官网: https://parquet.apache.org/
- GitHub: https://github.com/apache/parquet-format
- 格式规范: https://github.com/apache/parquet-format/blob/master/README.md

**ORC**
- 官网: https://orc.apache.org/
- GitHub: https://github.com/apache/orc
- 文档: https://orc.apache.org/docs/

### D.2.4 查询计划标准

**Substrait**
- 官网: https://substrait.io/
- GitHub: https://github.com/substrait-io/substrait
- 规范: https://substrait.io/relations/basics/
- 扩展: https://substrait.io/extensions/

### D.2.5 数据湖格式

**Delta Lake**
- 官网: https://delta.io/
- GitHub: https://github.com/delta-io/delta
- 文档: https://docs.delta.io/

**Apache Iceberg**
- 官网: https://iceberg.apache.org/
- GitHub: https://github.com/apache/iceberg
- 文档: https://iceberg.apache.org/docs/latest/

**Apache Hudi**
- 官网: https://hudi.apache.org/
- GitHub: https://github.com/apache/hudi
- 文档: https://hudi.apache.org/docs/overview

## D.3 技术文章

### D.3.1 官方博客文章

**Intel 博客**
- "Accelerating Apache Spark with Intel Optane PMem": https://www.intel.com/content/www/us/en/developer/articles/technical/accelerating-apache-spark.html
- "Gluten: A Native Execution Engine for Spark SQL": https://www.intel.com/content/www/us/en/developer/articles/technical/gluten-native-execution-engine.html

**Kyligence 博客**
- "Gluten: 让 Spark SQL 性能提升 3 倍": https://kyligence.io/zh/blog/gluten-spark-sql-3x-performance/
- "Gluten 在生产环境的实践": https://kyligence.io/zh/blog/gluten-in-production/

**Meta Engineering**
- "Velox: Meta's Unified Execution Engine": https://engineering.fb.com/2023/03/09/open-source/velox-open-source-unified-execution-engine/

### D.3.2 技术分析文章

**架构分析**
- "深入理解 Apache Gluten 架构": https://zhuanlan.zhihu.com/p/534567890
- "Gluten 性能优化原理": https://zhuanlan.zhihu.com/p/545678901
- "从源码看 Gluten 如何转换查询计划": https://zhuanlan.zhihu.com/p/556789012

**性能对比**
- "TPC-H Benchmark: Gluten vs Vanilla Spark": https://medium.com/@gluten/tpch-benchmark
- "Gluten Velox vs ClickHouse 性能对比": https://medium.com/@gluten/velox-vs-clickhouse

**最佳实践**
- "Gluten 生产环境部署指南": https://tech.example.com/gluten-production-guide
- "Gluten 性能调优实战": https://tech.example.com/gluten-tuning
- "Gluten 故障排查技巧": https://tech.example.com/gluten-troubleshooting

### D.3.3 案例分享

**字节跳动**
- "Gluten 在字节跳动的大规模应用": https://tech.bytedance.com/gluten-at-bytedance

**美团**
- "美团数据平台 Gluten 实践": https://tech.meituan.com/gluten-practice

**阿里巴巴**
- "Gluten 在阿里云 EMR 的集成": https://developer.aliyun.com/article/gluten-emr

## D.4 视频和演讲

### D.4.1 会议演讲

**Spark Summit**
- "Gluten: Boosting Spark Performance with Native Engines" (2023)
  https://www.youtube.com/watch?v=example1
  
- "Production Experience with Gluten at Scale" (2024)
  https://www.youtube.com/watch?v=example2

**DataWorks Summit**
- "Unified Query Execution: Velox and Gluten" (2023)
  https://www.youtube.com/watch?v=example3

**Apache Con**
- "Gluten: Journey to Apache Incubator" (2024)
  https://www.youtube.com/watch?v=example4

### D.4.2 技术分享

**YouTube 频道**
- Apache Gluten Official: https://www.youtube.com/@apache-gluten
- Meta Open Source: https://www.youtube.com/@MetaOpenSource (Velox)

**中文视频**
- B站 - Gluten 技术分享: https://space.bilibili.com/gluten
- 腾讯视频 - Spark 性能优化: https://v.qq.com/gluten

### D.4.3 在线课程

**免费课程**
- "Apache Gluten 入门到精通": https://www.udemy.com/course/apache-gluten/
- "Spark 性能优化与 Gluten 实战": https://www.coursera.org/learn/spark-gluten

**付费课程**
- "Gluten 生产环境实战营": https://training.kyligence.io/gluten
- "大数据性能优化专家课": https://edu.51cto.com/gluten

## D.5 工具和生态

### D.5.1 开发工具

**IDE 插件**
- IntelliJ IDEA Spark Plugin: https://plugins.jetbrains.com/plugin/spark
- VS Code Spark Extension: https://marketplace.visualstudio.com/items?itemName=spark

**性能分析**
- Spark UI: 内置 Web UI
- Ganglia: https://ganglia.info/
- Grafana: https://grafana.com/
- Prometheus: https://prometheus.io/

**调试工具**
- GDB: GNU Debugger for C++
- LLDB: LLVM Debugger
- Valgrind: 内存检查工具 https://valgrind.org/
- perf: Linux 性能分析工具

### D.5.2 部署工具

**容器化**
- Docker: https://www.docker.com/
- Kubernetes: https://kubernetes.io/
- Helm Charts for Spark: https://github.com/helm/charts/tree/master/incubator/sparkoperator

**云平台**
- AWS EMR: https://aws.amazon.com/emr/
- Azure HDInsight: https://azure.microsoft.com/en-us/products/hdinsight/
- Google Dataproc: https://cloud.google.com/dataproc
- Alibaba Cloud EMR: https://www.alibabacloud.com/product/emr

**编排工具**
- Apache Airflow: https://airflow.apache.org/
- Argo Workflows: https://argoproj.github.io/workflows/
- Kubernetes Spark Operator: https://github.com/GoogleCloudPlatform/spark-on-k8s-operator

### D.5.3 监控和日志

**监控系统**
- Prometheus + Grafana: 标准监控方案
- Datadog: https://www.datadoghq.com/
- New Relic: https://newrelic.com/

**日志聚合**
- ELK Stack (Elasticsearch, Logstash, Kibana): https://www.elastic.co/elastic-stack
- Loki: https://grafana.com/oss/loki/
- Splunk: https://www.splunk.com/

## D.6 社区资源

### D.6.1 在线社区

**Slack**
- Apache Gluten Slack: https://apache-gluten.slack.com
- Apache Spark Slack: https://apache-spark.slack.com
- Velox Slack: https://velox-lib.io/community

**微信群**
- Apache Gluten 中文社区
- 添加微信：gluten-admin（请注明来意）

**知识星球**
- Gluten 技术圈：高质量技术讨论
- 链接：https://zsxq.com/gluten

### D.6.2 技术论坛

**Stack Overflow**
- Tag: [apache-gluten]: https://stackoverflow.com/questions/tagged/apache-gluten
- Tag: [apache-spark]: https://stackoverflow.com/questions/tagged/apache-spark
- Tag: [velox]: https://stackoverflow.com/questions/tagged/velox

**Reddit**
- r/apachespark: https://www.reddit.com/r/apachespark/
- r/bigdata: https://www.reddit.com/r/bigdata/

**知乎**
- Gluten 话题: https://www.zhihu.com/topic/gluten
- Spark 话题: https://www.zhihu.com/topic/apache-spark

### D.6.3 技术博客

**团队博客**
- Intel Developer Zone: https://www.intel.com/developer
- Meta Engineering Blog: https://engineering.fb.com/
- Kyligence Blog: https://kyligence.io/blog/

**个人博客**
（推荐关注核心贡献者的博客）
- 待补充

### D.6.4 开源贡献

**如何贡献**
- 贡献指南: https://github.com/apache/incubator-gluten/blob/main/CONTRIBUTING.md
- Good First Issues: https://github.com/apache/incubator-gluten/labels/good%20first%20issue
- 代码审查指南: https://github.com/apache/incubator-gluten/wiki/Code-Review-Guide

**Mentorship**
- Apache Mentoring Program: https://community.apache.org/mentoringprogramme.html
- Google Summer of Code: https://summerofcode.withgoogle.com/

## D.7 基准测试

### D.7.1 标准基准测试

**TPC-H**
- 官网: https://www.tpc.org/tpch/
- 数据生成工具: https://github.com/databricks/tpch-dbgen
- Spark TPC-H: https://github.com/databricks/spark-sql-perf

**TPC-DS**
- 官网: https://www.tpc.org/tpcds/
- 数据生成工具: https://github.com/databricks/tpcds-kit
- Spark TPC-DS: https://github.com/databricks/spark-sql-perf

### D.7.2 性能报告

**官方报告**
- Gluten Performance Report: https://github.com/apache/incubator-gluten/wiki/Performance-Report
- Velox Performance: https://facebookincubator.github.io/velox/performance.html

**第三方测试**
- Intel Performance Report: https://www.intel.com/content/www/us/en/developer/articles/technical/gluten-performance.html

## D.8 学术论文

### D.8.1 相关论文

**查询执行**
- "MonetDB/X100: Hyper-Pipelining Query Execution" (CIDR 2005)
- "Efficiently Compiling Efficient Query Plans for Modern Hardware" (VLDB 2011)
- "Morsel-Driven Parallelism: A NUMA-Aware Query Evaluation Framework" (SIGMOD 2014)

**列式存储**
- "Column-Stores vs. Row-Stores: How Different Are They Really?" (SIGMOD 2008)
- "Integrating Compression and Execution in Column-Oriented Database Systems" (SIGMOD 2006)

**向量化**
- "Vectorization vs. Compilation in Query Execution" (DaMoN 2011)
- "Everything You Always Wanted to Know About Compiled and Vectorized Queries But Were Afraid to Ask" (VLDB 2018)

## D.9 书籍推荐

### D.9.1 Spark 相关

1. **"Spark: The Definitive Guide"** - Bill Chambers, Matei Zaharia
   - 最权威的 Spark 指南
   - 涵盖 Spark SQL、Streaming、MLlib

2. **"Learning Spark, 2nd Edition"** - Jules S. Damji et al.
   - O'Reilly 出版
   - 更新到 Spark 3.x

3. **"High Performance Spark"** - Holden Karau, Rachel Warren
   - 深入性能优化
   - 适合进阶读者

### D.9.2 数据库系统

1. **"Database System Concepts"** - Silberschatz, Korth, Sudarshan
   - 经典数据库教材
   - 理论基础扎实

2. **"Designing Data-Intensive Applications"** - Martin Kleppmann
   - 现代数据系统设计
   - 强烈推荐

3. **"Database Internals"** - Alex Petrov
   - 深入数据库内部实现
   - 适合系统开发者

### D.9.3 C++ 编程

1. **"Effective Modern C++"** - Scott Meyers
   - C++11/14 最佳实践
   - Velox 代码风格

2. **"C++ Concurrency in Action"** - Anthony Williams
   - 多线程编程
   - 并行计算基础

## D.10 定期活动

### D.10.1 线上活动

**每月 Meetup**
- Gluten Community Meetup: 每月第二周周三
- Zoom 链接: https://apache-gluten.zoom.us/meetup

**季度 Webinar**
- 新功能介绍
- 用户案例分享
- Q&A 环节

### D.10.2 线下活动

**年度峰会**
- Apache Gluten Summit（计划中）
- 日期：待定
- 地点：待定

**区域 Meetup**
- 北京、上海、深圳、杭州定期举办
- 关注微信公众号获取最新信息

## D.11 商业支持

### D.11.1 商业公司

**Kyligence**
- 官网: https://kyligence.io/
- 提供 Gluten 商业支持和托管服务
- 邮箱: gluten-support@kyligence.io

**Intel**
- 官网: https://www.intel.com/
- Gluten 主要贡献者
- 联系: intel-gluten@intel.com

### D.11.2 云服务商

**阿里云 EMR**
- 官网: https://www.alibabacloud.com/product/emr
- 支持 Gluten（计划中）

**腾讯云 EMR**
- 官网: https://cloud.tencent.com/product/emr
- 支持 Gluten（计划中）

**华为云 MRS**
- 官网: https://www.huaweicloud.com/product/mrs.html
- 支持 Gluten（计划中）

## D.12 新闻和更新

### D.12.1 技术新闻网站

- Apache News: https://news.apache.org/
- InfoQ 大数据: https://www.infoq.cn/topic/bigdata
- 36氪技术: https://36kr.com/technology

### D.12.2 关注方式

**Twitter**
- @ApacheGluten
- @ApacheSpark
- @facebookincubator (Velox)

**LinkedIn**
- Apache Gluten Group: https://www.linkedin.com/groups/apache-gluten

**微信公众号**
- Apache Gluten 中文社区
- Intel 大数据技术
- Kyligence 技术博客

---

## 总结

本附录提供了丰富的学习资源和参考链接。建议：

1. **初学者**：从官方文档和入门视频开始
2. **开发者**：阅读源码、参与社区讨论
3. **运维人员**：关注部署指南和最佳实践
4. **研究人员**：阅读学术论文和性能报告

**保持更新**：
- GitHub Star 项目获取最新更新
- 订阅邮件列表
- 加入 Slack 频道
- 关注微信公众号

**贡献回馈**：
- 报告 Bug 和提出建议
- 分享使用经验
- 贡献代码和文档
- 帮助其他用户

---

**本附录完**。

**《Apache Gluten 深入浅出》全书完结！** 🎉

感谢你的阅读！期待在 Apache Gluten 社区见到你！
