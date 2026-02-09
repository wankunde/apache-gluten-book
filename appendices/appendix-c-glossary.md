# 附录 C：术语表

本附录收录 Apache Gluten 相关的重要术语和概念，按字母顺序排列。

## A

**Adaptive Query Execution (AQE)**
- Spark 3.0+ 引入的自适应查询执行功能
- 在运行时动态优化查询计划
- Gluten 支持 AQE，可在 Native 层应用优化

**Aggregate (聚合)**
- 数据聚合操作，如 SUM、COUNT、AVG
- Gluten 将聚合下推到 Velox/ClickHouse 执行
- 分为 Partial Aggregate 和 Final Aggregate

**Apache Arrow**
- 列式内存格式标准
- Gluten 使用 Arrow 进行数据交换
- 支持零拷贝跨进程传输

**Apache Spark**
- 分布式大数据处理引擎
- Gluten 作为 Spark 插件运行
- 支持 Spark 3.2+版本

## B

**Backend (后端)**
- 指原生执行引擎，如 Velox 或 ClickHouse
- 负责实际的数据处理和计算
- 通过 JNI 与 JVM 层交互

**Broadcast Join**
- 将小表广播到所有节点的 Join 策略
- Gluten 原生支持，性能优于 Spark
- 默认阈值：10MB

**Build Side**
- Hash Join 中构建哈希表的一侧
- 通常是较小的表
- Gluten 自动选择 Build Side

**ByPass**
- 绕过某个处理阶段
- 与 Fallback 不同，ByPass 是优化手段

## C

**Cache (缓存)**
- Velox 支持列式数据缓存
- 可配置缓存大小和策略
- 提升重复查询性能

**C2R (ColumnarToRow)**
- 列式格式转行式格式
- 发生在 Fallback 时
- 有性能开销，应尽量避免

**ClickHouse**
- Yandex 开发的 OLAP 数据库
- Gluten 支持的后端之一
- 函数支持最丰富（1000+ 函数）

**Columnar Format (列式格式)**
- 按列存储数据的格式
- OLAP 查询性能优于行式格式
- Gluten 全程使用列式格式

**Columnar Shuffle**
- 列式 Shuffle 实现
- 相比 Spark 的行式 Shuffle 性能更好
- 减少序列化/反序列化开销

**Compute Pushdown (计算下推)**
- 将计算推到数据源执行
- 减少数据传输量
- Gluten 的核心优化之一

## D

**Data Skew (数据倾斜)**
- 数据分布不均导致某些任务特别慢
- Gluten 通过 AQE 缓解数据倾斜
- 仍需要业务层配合优化

**Driver**
- Spark 驱动程序
- 负责任务调度和协调
- Gluten 主要在 Executor 侧生效

**Dynamic Partition Pruning (DPP)**
- 动态分区裁剪
- 在运行时根据数据过滤分区
- Gluten 计划支持（Roadmap 功能）

## E

**Executor**
- Spark 执行器
- 运行具体的任务
- Gluten Native 引擎在 Executor 中执行

**Expression (表达式)**
- SQL 中的计算表达式
- 如：`col1 + col2 > 100`
- Gluten 将表达式转换为 Substrait

**Extension Point (扩展点)**
- Spark 提供的插件扩展接口
- Gluten 通过扩展点注入
- 包括 ColumnarRule、Strategy 等

## F

**Fallback (回退)**
- 当 Native 引擎不支持某操作时回退到 Spark
- 涉及 C2R 和 R2C 转换
- 应尽量减少 Fallback

**Filter Pushdown (过滤下推)**
- 将过滤条件推到数据源
- 减少读取的数据量
- Parquet/ORC 原生支持

**Fragment (片段)**
- 查询执行的一个阶段
- 通常以 Shuffle 为边界
- 每个 Fragment 可以并行执行

## G

**Gluten**
- Apache 孵化项目
- 全称：Gluten: Plugin to Double SparkSQL's Performance
- 目标：将 Spark 性能提升 2-5 倍

**GPU Acceleration (GPU 加速)**
- 使用 GPU 加速计算
- Gluten 计划支持 RAPIDS cuDF
- 适用于大规模数据处理

## H

**Hash Aggregate**
- 基于哈希表的聚合算法
- 性能优于 Sort Aggregate
- Gluten 优先使用 Hash Aggregate

**Hash Join**
- 基于哈希表的 Join 算法
- Gluten 默认使用（相比 SMJ）
- 适用于大多数场景

**Hybrid Engine (混合引擎)**
- JVM 和 Native 混合执行
- 根据场景选择最优引擎
- Gluten 的设计理念

## I

**Iceberg**
- Apache 表格式规范
- Gluten 支持 Iceberg 读取
- 深度集成在 Roadmap 中

**In-Memory Columnar Cache (内存列缓存)**
- 将热数据缓存在内存
- Velox 支持，需显式配置
- 显著提升重复查询性能

## J

**JNI (Java Native Interface)**
- Java 调用原生代码的接口
- Gluten 通过 JNI 调用 C++ 代码
- JNI 有一定性能开销

**Join**
- 表连接操作
- Gluten 支持 Hash Join、Shuffled Hash Join
- 不推荐使用 Sort Merge Join

## K

**Kafka**
- 分布式流平台
- Spark Streaming 常用数据源
- Gluten 支持 Streaming 加速

**Kubernetes (K8s)**
- 容器编排平台
- Gluten 支持 K8s 部署
- 云原生环境首选

## L

**Lakehouse**
- 数据湖和数据仓库的结合
- Delta Lake、Iceberg、Hudi
- Gluten 适配 Lakehouse 架构

**Late Materialization (延迟物化)**
- 推迟列读取到真正需要时
- 减少不必要的数据读取
- Gluten 自动应用

## M

**Memory Isolation (内存隔离)**
- Task 之间的内存隔离
- 两种模式：Shared vs Isolated
- 默认使用 Shared 模式

**Memory Pool (内存池)**
- 内存管理的基本单位
- Velox 使用多级内存池
- 支持 Spill 到磁盘

**Metadata**
- 数据的元数据信息
- 如 Parquet Footer、统计信息
- 用于谓词下推和裁剪

## N

**Native Execution (原生执行)**
- 在 C++ 层执行查询
- 相比 JVM 执行性能更高
- Gluten 的核心价值

**Native Memory (原生内存)**
- 堆外内存（Off-Heap Memory）
- 不受 JVM GC 管理
- Gluten 大量使用 Native Memory

## O

**Off-Heap Memory (堆外内存)**
- JVM 堆之外的内存
- Gluten 必须启用
- 建议占 Executor 内存的 60-70%

**OLAP (Online Analytical Processing)**
- 在线分析处理
- Gluten 主要应用场景
- 与 OLTP 相对

**Operator (算子)**
- 查询计划中的操作节点
- 如 Filter、Project、Join
- Gluten 支持 30+ 算子

**ORC**
- 列式存储格式
- Hadoop 生态常用
- Gluten 原生支持

## P

**Parquet**
- 列式存储格式
- 大数据领域事实标准
- Gluten 原生支持，性能优异

**Partial Aggregate**
- 聚合的第一阶段
- 在 Map 端执行
- 减少 Shuffle 数据量

**Partition Pruning (分区裁剪)**
- 根据谓词跳过不需要的分区
- 大幅减少数据扫描量
- Gluten 原生支持

**Physical Plan (物理计划)**
- Spark 查询的执行计划
- Gluten 拦截并转换物理计划
- 转换为 Substrait Plan

**Predicate Pushdown (谓词下推)**
- 将过滤条件推到数据源
- 核心优化技术
- Gluten 充分利用

**Probe Side**
- Hash Join 中探测哈希表的一侧
- 通常是较大的表
- 与 Build Side 相对

## Q

**Query Optimization (查询优化)**
- 优化查询执行效率
- 包括逻辑优化和物理优化
- Gluten 在物理层优化

**Query Plan (查询计划)**
- SQL 的执行计划
- 分为逻辑计划和物理计划
- Gluten 转换物理计划

## R

**R2C (RowToColumnar)**
- 行式格式转列式格式
- 发生在 Fallback 后重新进入 Native
- 有性能开销

**Roadmap (路线图)**
- 项目的未来发展计划
- Gluten Roadmap 包括 GPU 支持、更多后端等
- 参见 GitHub Wiki

**Row Format (行式格式)**
- 按行存储数据的格式
- OLTP 场景常用
- Spark 默认使用行式

## S

**Scalar Function (标量函数)**
- 对单个值进行操作的函数
- 如 `abs()`、`upper()`
- Gluten 支持 200+ 标量函数

**Scan (扫描)**
- 读取数据源的操作
- 可以应用谓词下推、列裁剪
- Gluten 原生实现 Scan

**Shim Layer (垫片层)**
- 适配不同 Spark 版本的兼容层
- Gluten 通过 Shim 支持 Spark 3.2-3.5
- 隔离版本差异

**Shuffle**
- 数据重分布操作
- 性能瓶颈之一
- Gluten 使用 Columnar Shuffle 优化

**Shuffled Hash Join (SHJ)**
- 需要 Shuffle 的 Hash Join
- Gluten 默认策略
- 性能优于 Sort Merge Join

**SIMD (Single Instruction Multiple Data)**
- 单指令多数据
- CPU 向量化技术
- Velox 大量使用 SIMD

**Sort Merge Join (SMJ)**
- 基于排序的 Join 算法
- Gluten 不推荐使用
- 性能不如 Hash Join

**Spill (溢写)**
- 内存不足时写入磁盘
- Gluten 支持 Spill
- 配置 Spill 路径和阈值

**Substrait**
- 跨引擎的查询计划表示标准
- Gluten 使用 Substrait 作为中间格式
- Protocol Buffers 序列化

## T

**Task**
- Spark 中的最小执行单元
- 一个 Task 处理一个分区
- Gluten 在 Task 级别执行

**TPC-H**
- 标准决策支持基准测试
- 22 个查询
- Gluten 性能测试常用

**TPC-DS**
- 标准决策支持基准测试
- 99 个查询，更复杂
- 测试真实场景性能

**Transformer**
- Gluten 中转换算子的类
- 如 FilterExecTransformer
- 继承自 Spark 的 SparkPlan

## U

**UDF (User Defined Function)**
- 用户自定义函数
- Scala/Python UDF 需要 Fallback
- Gluten 计划支持 Columnar UDF

**Unified Memory Management (统一内存管理)**
- Spark 的内存管理机制
- 动态调整执行内存和存储内存
- Gluten 遵循 Spark 内存管理

## V

**Vanilla Spark**
- 原生的、未经修改的 Spark
- 用于性能对比基准
- Gluten 目标：2-5x 加速 Vanilla Spark

**Vectorization (向量化)**
- 批量处理数据
- 利用 CPU SIMD 指令
- Velox 的核心技术

**Velox**
- Meta 开发的统一执行引擎
- Gluten 支持的主要后端
- C++ 实现，高性能

## W

**Whole Stage Code Generation (全阶段代码生成)**
- Spark 的代码生成优化
- Gluten 在 Native 层也应用代码生成
- 减少虚函数调用

**Window Function (窗口函数)**
- 在窗口内计算的函数
- 如 `row_number()`、`rank()`
- Gluten 原生支持窗口函数

## Z

**Zero-Copy (零拷贝)**
- 数据传输无需拷贝
- Apache Arrow 支持零拷贝
- 显著提升性能

---

## 缩写词表

| 缩写 | 全称 | 中文 |
|------|------|------|
| AQE | Adaptive Query Execution | 自适应查询执行 |
| C2R | Columnar To Row | 列转行 |
| CPU | Central Processing Unit | 中央处理器 |
| DAG | Directed Acyclic Graph | 有向无环图 |
| DPP | Dynamic Partition Pruning | 动态分区裁剪 |
| GC | Garbage Collection | 垃圾回收 |
| GPU | Graphics Processing Unit | 图形处理器 |
| JIT | Just-In-Time | 即时编译 |
| JNI | Java Native Interface | Java 本地接口 |
| JVM | Java Virtual Machine | Java 虚拟机 |
| K8s | Kubernetes | 容器编排平台 |
| LLVM | Low Level Virtual Machine | 底层虚拟机 |
| OLAP | Online Analytical Processing | 在线分析处理 |
| OLTP | Online Transaction Processing | 在线事务处理 |
| ORC | Optimized Row Columnar | 优化行列式 |
| R2C | Row To Columnar | 行转列 |
| RDD | Resilient Distributed Dataset | 弹性分布式数据集 |
| SHJ | Shuffled Hash Join | Shuffled 哈希连接 |
| SIMD | Single Instruction Multiple Data | 单指令多数据 |
| SMJ | Sort Merge Join | 排序合并连接 |
| SQL | Structured Query Language | 结构化查询语言 |
| UDF | User Defined Function | 用户自定义函数 |
| UDAF | User Defined Aggregate Function | 用户自定义聚合函数 |
| UDTF | User Defined Table Function | 用户自定义表函数 |

---

**本附录完**。
