# Gluten 架构图

```mermaid
graph TB
    subgraph "用户层"
        SQL[SQL Query]
        DF[DataFrame API]
    end
    
    subgraph "Spark 层"
        Parser[SQL Parser]
        Analyzer[Analyzer]
        Optimizer[Optimizer]
        Planner[Physical Planner]
    end
    
    subgraph "Gluten 层"
        Plugin[Gluten Plugin]
        Transformer[Plan Transformer]
        MemMgr[Memory Manager]
        JNI[JNI Bridge]
    end
    
    subgraph "原生层"
        Substrait[Substrait Plan]
        Velox[Velox Engine]
        CH[ClickHouse Engine]
    end
    
    SQL --> Parser
    DF --> Analyzer
    Parser --> Analyzer
    Analyzer --> Optimizer
    Optimizer --> Planner
    Planner --> Plugin
    Plugin --> Transformer
    Transformer --> MemMgr
    MemMgr --> JNI
    JNI --> Substrait
    Substrait --> Velox
    Substrait --> CH
    
    style Gluten层 fill:#e1f5ff
    style 原生层 fill:#ffe1e1
```

## 数据流转过程

```mermaid
sequenceDiagram
    participant User
    participant Spark
    participant Gluten
    participant Velox
    
    User->>Spark: Submit SQL Query
    Spark->>Spark: Parse & Optimize
    Spark->>Gluten: Physical Plan
    Gluten->>Gluten: Transform to Substrait
    Gluten->>Velox: Execute Native Plan
    Velox->>Velox: Process Data
    Velox->>Gluten: Return Results
    Gluten->>Spark: Columnar Batch
    Spark->>User: Query Results
```

## 内存管理架构

```mermaid
graph LR
    subgraph "JVM Heap"
        SparkMem[Spark Memory]
        ObjPool[Object Pool]
    end
    
    subgraph "Off-Heap"
        GlutenMem[Gluten Memory]
        VeloxMem[Velox Memory]
        ArrowMem[Arrow Memory]
    end
    
    subgraph "Native"
        Malloc[Native Malloc]
        HugePages[Huge Pages]
    end
    
    SparkMem --> GlutenMem
    GlutenMem --> VeloxMem
    VeloxMem --> ArrowMem
    ArrowMem --> Malloc
    Malloc --> HugePages
    
    style "Off-Heap" fill:#ffffcc
    style Native fill:#ffcccc
```

## Fallback 流程

```mermaid
graph TD
    Start[Physical Plan] --> Check{算子支持?}
    Check -->|支持| Native[Native Execution]
    Check -->|不支持| C2R[ColumnarToRow]
    C2R --> Spark[Spark Execution]
    Spark --> R2C[RowToColumnar]
    R2C --> Continue[Continue Pipeline]
    Native --> Continue
    Continue --> End[Result]
    
    style C2R fill:#ffcccc
    style R2C fill:#ffcccc
```

## Shuffle 架构

```mermaid
graph TB
    subgraph "Map Side"
        Reader[Data Reader]
        Filter[Filter]
        Project[Project]
        Partition[Partitioner]
        Writer[Shuffle Writer]
    end
    
    subgraph "Shuffle Service"
        LocalDisk[Local Disk]
        Celeborn[Celeborn]
        Uniffle[Uniffle]
    end
    
    subgraph "Reduce Side"
        ShuffleReader[Shuffle Reader]
        Merge[Merge Sort]
        Agg[Aggregation]
        Output[Output]
    end
    
    Reader --> Filter
    Filter --> Project
    Project --> Partition
    Partition --> Writer
    Writer --> LocalDisk
    Writer --> Celeborn
    Writer --> Uniffle
    LocalDisk --> ShuffleReader
    Celeborn --> ShuffleReader
    Uniffle --> ShuffleReader
    ShuffleReader --> Merge
    Merge --> Agg
    Agg --> Output
```
