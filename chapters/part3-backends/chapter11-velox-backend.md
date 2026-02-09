# 第11章：Velox 后端详解

> **本章要点**：
> - 深入理解 Velox 执行引擎架构
> - 掌握 Velox 的算子实现机制
> - 学习 Velox 的向量化执行模型
> - 了解 Velox 的特色功能（缓存、Spill、S3）
> - 掌握 Velox 性能优化技巧

## 引言

Velox 是 Meta（Facebook）开源的统一执行引擎，是 Gluten 默认和最成熟的后端。Velox 提供了高性能的向量化执行、丰富的算子库和强大的扩展能力。本章将深入剖析 Velox 的架构和实现。

## 11.1 Velox 架构概览

### 11.1.1 Velox 简介

**Velox 是什么**？
- 🚀 C++ 向量化执行引擎
- 🔧 模块化设计，可嵌入多种系统
- 📦 由 Meta 开发并开源
- 🌟 被 Presto, Spark (Gluten), PyTorch 等项目使用

**设计目标**：
- ⚡ **高性能**：C++ 实现，SIMD 优化
- 🔌 **可扩展**：插件化架构，易于添加新算子
- 💾 **内存高效**：零拷贝，智能内存管理
- 🌐 **跨平台**：支持 x86、ARM、GPU

### 11.1.2 Velox 整体架构

```
查询接口层
    ↓
Velox API
    ↓
执行层 (Task → Driver → Operator Pipeline)
    ↓
算子层 (Scan, Filter, Project, Join, Agg)
    ↓
向量层 (FlatVector, DictionaryVector, RowVector)
    ↓
表达式层 (FieldAccess, Function Call)
    ↓
函数库 (Scalar, Aggregate, Window)
    ↓
存储层 (Local FS, S3, HDFS, Cache)
```

### 11.1.3 核心概念

**1. Vector（向量）**

```cpp
// Velox 的基本数据单元
class BaseVector {
  TypePtr type_;                // 向量类型
  vector_size_t size_;          // 向量长度
  BufferPtr nulls_;             // Null 值位图
  vector_size_t nullCount_;     // Null 值数量
  memory::MemoryPool* pool_;    // 内存池
};
```

**2. RowVector（行向量）**

```cpp
// 表示一批行数据
class RowVector : public BaseVector {
  std::vector<VectorPtr> children_;  // 子向量（每列一个）
  
  VectorPtr& childAt(column_index_t index) {
    return children_[index];
  }
};
```

**3. Operator（算子）**

```cpp
// 算子基类
class Operator {
public:
  virtual RowVectorPtr getOutput() = 0;       // 产生输出
  virtual void addInput(RowVectorPtr input) = 0;  // 添加输入
  virtual bool needsInput() const = 0;        // 是否需要更多输入
  virtual bool isFinished() const = 0;        // 是否已完成
};
```

**4. Driver（驱动器）**

```cpp
// 执行 Operator Pipeline
class Driver {
private:
  std::vector<std::unique_ptr<Operator>> operators_;  // Operator 链
  std::shared_ptr<Task> task_;                        // 任务上下文
  
public:
  void runInternal() {
    // 从最后一个 Operator 开始拉取数据
    auto output = operators_.back()->getOutput();
    if (output) {
      processOutput(output);
    }
  }
};
```

**5. Task（任务）**

```cpp
// 表示一个查询任务
class Task {
private:
  std::string taskId_;
  std::shared_ptr<core::PlanNode> planFragment_;
  std::vector<std::shared_ptr<Driver>> drivers_;
  std::shared_ptr<memory::MemoryPool> pool_;
  
public:
  void start(uint32_t numDrivers) {
    for (uint32_t i = 0; i < numDrivers; ++i) {
      auto driver = createDriver(i);
      drivers_.push_back(driver);
      driver->run();
    }
  }
};
```

## 11.2 Velox 向量化执行

### 11.2.1 向量化 vs 行式执行

**传统行式执行**（Volcano Model）：

```cpp
// 逐行处理
while (row = scan.next()) {
  if (filter.apply(row)) {
    result = project.apply(row);
    output.add(result);
  }
}
```

**问题**：
- ❌ 每行一次函数调用（开销大）
- ❌ 分支预测失败
- ❌ CPU 缓存不友好
- ❌ 无法利用 SIMD

**向量化执行**：

```cpp
// 批量处理（每批 1024-4096 行）
RowVectorPtr batch;
while (batch = scan.getOutput()) {  // 1024 行
  batch = filter.apply(batch);       // 批量过滤
  batch = project.apply(batch);      // 批量投影
  output.add(batch);
}
```

**优势**：
- ✅ 减少函数调用开销（1000x）
- ✅ 更好的缓存利用
- ✅ 可以使用 SIMD 指令
- ✅ 编译器优化机会更多

### 11.2.2 SIMD 优化示例

**标量版本**：

```cpp
// 逐个计算
void add_scalar(const int32_t* a, const int32_t* b, int32_t* result, size_t n) {
  for (size_t i = 0; i < n; ++i) {
    result[i] = a[i] + b[i];
  }
}
// 性能：~1 cycle per element
```

**SIMD 版本（AVX2）**：

```cpp
#include <immintrin.h>

void add_simd(const int32_t* a, const int32_t* b, int32_t* result, size_t n) {
  size_t i = 0;
  
  // 一次处理 8 个 int32 (256 bits / 32 bits = 8)
  for (; i + 8 <= n; i += 8) {
    __m256i va = _mm256_loadu_si256((__m256i*)(a + i));
    __m256i vb = _mm256_loadu_si256((__m256i*)(b + i));
    __m256i vr = _mm256_add_epi32(va, vb);
    _mm256_storeu_si256((__m256i*)(result + i), vr);
  }
  
  // 处理剩余元素
  for (; i < n; ++i) {
    result[i] = a[i] + b[i];
  }
}
// 性能：~0.125 cycle per element（8x 加速）
```

### 11.2.3 Velox Vector 类型

**FlatVector**：最简单的向量

```cpp
// 连续存储的值
template <typename T>
class FlatVector : public SimpleVector<T> {
private:
  BufferPtr values_;  // 值缓冲区
  
public:
  T valueAt(vector_size_t index) const {
    return reinterpret_cast<const T*>(values_->as<void>())[index];
  }
  
  void set(vector_size_t index, T value) {
    auto* data = values_->asMutable<T>();
    data[index] = value;
  }
};
```

**DictionaryVector**：编码向量

```cpp
// 使用字典编码，节省内存
class DictionaryVector : public BaseVector {
private:
  VectorPtr dictionaryValues_;  // 字典（实际值）
  BufferPtr indices_;            // 索引（指向字典）
  
public:
  template <typename T>
  T valueAt(vector_size_t index) const {
    auto dictIndex = indices_->as<vector_size_t>()[index];
    return dictionaryValues_->as<FlatVector<T>>()->valueAt(dictIndex);
  }
};
```

**ConstantVector**：常量向量

```cpp
// 所有位置都是同一个值
class ConstantVector : public BaseVector {
private:
  std::shared_ptr<BaseVector> valueVector_;  // 单个值
  
public:
  template <typename T>
  T valueAt(vector_size_t index) const {
    return valueVector_->as<FlatVector<T>>()->valueAt(0);
  }
};

// 示例：1000 个值都是 42，内存占用：只存储一个值！
auto constantVector = BaseVector::createConstant(INTEGER(), 42, 1000, pool);
```

## 11.3 Velox 特色功能

### 11.3.1 SSD Cache（缓存）

**设计目标**：
- 缓存热数据到 SSD
- 减少对象存储（S3）访问
- 提升重复查询性能

**架构**：

```cpp
class SsdCache {
private:
  std::vector<std::unique_ptr<SsdFile>> files_;         // SSD 文件
  CacheIndex index_;                                     // 缓存索引
  std::unique_ptr<LRUEvictionPolicy> evictionPolicy_;  // LRU 淘汰
  
public:
  // 读取缓存
  CacheEntry* get(const CacheKey& key) {
    auto entry = index_.find(key);
    if (entry) {
      evictionPolicy_->touch(entry);
      return entry;
    }
    return nullptr;
  }
  
  // 写入缓存
  void put(const CacheKey& key, const std::string_view& data) {
    auto fileIndex = selectFile(key);
    auto& file = files_[fileIndex];
    auto offset = file->write(data);
    
    CacheEntry entry{fileIndex, offset, data.size()};
    index_.insert(key, entry);
    evictionPolicy_->insert(&entry);
    
    if (isFull()) {
      evict();
    }
  }
};
```

**配置**：

```properties
# 启用 SSD 缓存
velox.cache.enabled=true
velox.cache.path=/mnt/ssd/velox-cache
velox.cache.size=107374182400  # 100GB
velox.cache.num-shards=4
```

### 11.3.2 Async I/O（异步 I/O）

```cpp
class HiveDataSource {
private:
  std::shared_ptr<AsyncSource<RowVectorPtr>> asyncSource_;
  
public:
  RowVectorPtr next() {
    if (!asyncSource_) {
      asyncSource_ = std::make_shared<AsyncSource<RowVectorPtr>>(
        [this]() { return readNextBatch(); }
      );
      asyncSource_->prepare();  // 后台线程开始读取
    }
    
    auto result = asyncSource_->move();
    asyncSource_->prepare();  // 立即启动下一次预取
    
    return result;
  }
};
```

**性能提升**：CPU 和 I/O 并行，减少等待时间（~30% 提升）

### 11.3.3 S3 Connector

```cpp
class S3ReadFile : public ReadFile {
private:
  std::string bucket_;
  std::string key_;
  std::shared_ptr<Aws::S3::S3Client> client_;
  
public:
  void pread(uint64_t offset, uint64_t length, void* buffer) override {
    // HTTP Range 请求
    Aws::S3::Model::GetObjectRequest request;
    request.SetBucket(bucket_);
    request.SetKey(key_);
    request.SetRange(fmt::format("bytes={}-{}", offset, offset + length - 1));
    
    auto outcome = client_->GetObject(request);
    auto& stream = outcome.GetResult().GetBody();
    stream.read(static_cast<char*>(buffer), length);
  }
  
  // 批量读取（合并小请求）
  void preadvMultiple(
    const std::vector<std::pair<uint64_t, uint64_t>>& regions,
    std::vector<void*> buffers
  ) override {
    auto merged = mergeRegions(regions);  // 合并相邻请求
    for (auto& [offset, length] : merged) {
      pread(offset, length, ...);
    }
  }
};
```

**优化**：批量读取、SSD 缓存、Prefetch、重试机制

## 11.4 Velox 性能优化

### 11.4.1 编译优化

```bash
# 编译选项
cmake .. \
  -DCMAKE_BUILD_TYPE=Release \
  -DVELOX_ENABLE_PARQUET=ON \
  -DVELOX_ENABLE_S3=ON \
  -DVELOX_ENABLE_HDFS=ON

make -j$(nproc)
```

**优化标志**：
- `-O3`：最高优化级别
- `-march=native`：针对当前 CPU 优化（AVX2/AVX-512）
- `-flto`：链接时优化
- `-ffast-math`：浮点数快速数学

### 11.4.2 并行度调优

```properties
# 并行配置
spark.executor.cores=16
spark.task.cpus=1

# Velox Driver 数量（通常 = executor.cores）
spark.gluten.sql.columnar.backend.velox.numDrivers=16

# I/O 线程数
spark.gluten.sql.columnar.backend.velox.IOThreads=20

# Spill 线程数
spark.gluten.sql.columnar.backend.velox.spillThreads=8
```

## 本章小结

本章深入学习了 Velox 后端：

1. ✅ **Velox 架构**：Task/Driver/Operator/Vector 的层次结构
2. ✅ **向量化执行**：向量化模型和 SIMD 优化
3. ✅ **特色功能**：SSD Cache、Async I/O、S3 Connector
4. ✅ **性能优化**：编译优化、算子融合、并行度调优

下一章我们将学习 ClickHouse 后端。

## 参考资料

- [Velox Documentation](https://facebookincubator.github.io/velox/)
- [Velox GitHub](https://github.com/facebookincubator/velox)
- [Velox Paper (VLDB 2022)](https://www.vldb.org/pvldb/vol15/p3372-pedreira.pdf)

---

**下一章预告**：[第12章：ClickHouse 后端详解](chapter12-clickhouse-backend.md)
