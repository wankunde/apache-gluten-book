# 第23章：社区参与

> 本章要点：
> - Apache 孵化项目介绍
> - 如何贡献代码到 Gluten
> - 代码风格和开发规范
> - Issue 报告和 PR 提交流程
> - 社区沟通渠道（邮件列表、Slack、微信）
> - 成为 Committer 的路径

## 引言

Apache Gluten 是一个活跃的开源项目，社区的参与和贡献推动着项目的快速发展。无论你是想修复 bug、添加新功能，还是改进文档，都可以为 Gluten 做出贡献。本章将介绍如何参与 Gluten 社区，从提交第一个 Issue 到成为项目 Committer。

## 23.1 Apache 孵化项目简介

### 23.1.1 Apache Software Foundation (ASF)

Apache 软件基金会是世界上最大的开源软件基金会，管理着 350+ 个开源项目。

**核心价值观**：
- **Community Over Code**：社区比代码更重要
- **Meritocracy**：精英管理，贡献决定权利
- **Open Development**：开放透明的开发流程
- **Vendor Neutral**：中立，不受任何厂商控制

### 23.1.2 Gluten 孵化状态

**项目信息**：
- **名称**：Apache Gluten (Incubating)
- **孵化时间**：2023 年 10 月
- **Mentor**：多位 Apache Member
- **状态**：Incubating（孵化中）

**孵化流程**：

```
提案 → 投票 → 孵化 → 毕业 → 顶级项目
  ↑                ↑      ↑
 2023.08        2023.10  预计2025
```

**孵化要求**：
- ✅ 多样化的社区（不依赖单一公司）
- ✅ 活跃的开发（定期 Release）
- ✅ 遵循 Apache Way
- ✅ 完善的文档和治理

### 23.1.3 项目组织结构

```
PPMC (Podling Project Management Committee)
├── PPMC Members (8人)
├── Committers (15人)
└── Contributors (100+ 人)
```

**角色说明**：

| 角色 | 权限 | 要求 |
|------|------|------|
| **Contributor** | 提交 PR | 任何人 |
| **Committer** | 合并 PR | 持续贡献，PPMC 投票 |
| **PPMC Member** | 项目决策 | Committer + 社区领导力 |

## 23.2 准备贡献

### 23.2.1 环境搭建

参考第 14 章完整搭建开发环境：

```bash
# 1. Fork Gluten 仓库
# 访问 https://github.com/apache/incubator-gluten
# 点击 "Fork"

# 2. Clone 你的 Fork
git clone https://github.com/YOUR_USERNAME/incubator-gluten.git
cd incubator-gluten

# 3. 添加 upstream 远程仓库
git remote add upstream https://github.com/apache/incubator-gluten.git

# 4. 同步最新代码
git fetch upstream
git checkout main
git merge upstream/main
```

### 23.2.2 选择合适的任务

**新手友好 Issue**：

在 GitHub 上寻找标签为 `good-first-issue` 或 `help-wanted` 的 Issue：

```
https://github.com/apache/incubator-gluten/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22
```

**贡献类型**：

1. **文档改进**（最简单）
   - 修复文档中的错别字
   - 补充缺失的文档
   - 翻译文档

2. **Bug 修复**（中等难度）
   - 修复已知 Bug
   - 添加测试用例

3. **新功能**（较难）
   - 实现新算子支持
   - 添加新的后端功能
   - 性能优化

4. **基础设施**
   - 改进 CI/CD
   - 添加性能基准测试
   - 改进构建系统

## 23.3 代码风格和规范

### 23.3.1 Scala 代码风格

Gluten 使用 **Scalafmt** 进行代码格式化。

**配置文件** (`.scalafmt.conf`)：

```hocon
version = "3.7.3"
runner.dialect = scala212

maxColumn = 100
assumeStandardLibraryStripMargin = true

rewrite.rules = [
  RedundantBraces,
  RedundantParens,
  SortModifiers,
  PreferCurlyFors
]

rewrite.redundantBraces.stringInterpolation = true
rewrite.sortModifiers.order = [
  "override", "private", "protected", "lazy",
  "implicit", "final", "sealed", "abstract"
]
```

**使用方式**：

```bash
# 格式化所有代码
./dev/scalafmt

# 检查格式
./dev/scalafmt --test

# 在 IntelliJ IDEA 中
# Preferences → Editor → Code Style → Scala
# 导入 .scalafmt.conf
```

**代码规范**：

```scala
// ✅ 好的风格
class GlutenPlan extends SparkPlan {
  override def output: Seq[Attribute] = child.output
  
  override def doExecute(): RDD[InternalRow] = {
    throw new UnsupportedOperationException("Use executeColumnar instead")
  }
  
  override def executeColumnar(): RDD[ColumnarBatch] = {
    val nativeIterator = new NativeColumnarIterator(...)
    child.executeColumnar().mapPartitions { batches =>
      nativeIterator.process(batches)
    }
  }
}

// ❌ 不好的风格
class GlutenPlan extends SparkPlan{  // 缺少空格
    override def output:Seq[Attribute]=child.output  // 没有空格
    
    override def doExecute():RDD[InternalRow]={  // 格式混乱
        throw new UnsupportedOperationException("Use executeColumnar instead")}
    
  override def executeColumnar():RDD[ColumnarBatch]={
val nativeIterator=new NativeColumnarIterator(...)  // 缩进错误
child.executeColumnar().mapPartitions{batches=>nativeIterator.process(batches)}}  // 单行过长
}
```

### 23.3.2 C++ 代码风格

Gluten 使用 **clang-format** 格式化 C++ 代码。

**配置文件** (`.clang-format`)：

```yaml
BasedOnStyle: Google
Language: Cpp
ColumnLimit: 100
IndentWidth: 2
PointerAlignment: Left
DerivePointerAlignment: false
```

**使用方式**：

```bash
# 格式化单个文件
clang-format -i cpp/core/jni/JniWrapper.cc

# 格式化所有 C++ 文件
find cpp -name "*.cc" -o -name "*.h" | xargs clang-format -i

# 检查格式
find cpp -name "*.cc" -o -name "*.h" | xargs clang-format --dry-run -Werror
```

**命名规范**：

```cpp
// ✅ 好的风格
namespace gluten {

class VeloxBackend : public Backend {
 public:
  VeloxBackend(const std::unordered_map<std::string, std::string>& config);
  ~VeloxBackend() override;
  
  void initialize() override;
  std::shared_ptr<ColumnarBatch> execute(
      const substrait::Plan& plan,
      const std::vector<std::shared_ptr<ColumnarBatch>>& inputs) override;
  
 private:
  std::unique_ptr<VeloxRuntime> runtime_;
  std::shared_ptr<memory::MemoryPool> pool_;
};

}  // namespace gluten

// ❌ 不好的风格
namespace gluten{  // 缺少空格
class veloxBackend:public Backend{  // 命名不规范，缺少空格
public:  // 缩进错误
VeloxBackend(const std::unordered_map<std::string,std::string>&config);  // 格式错误
~VeloxBackend()override;
void Initialize();  // 命名不一致（应该是 initialize）
private:
std::unique_ptr<VeloxRuntime>runtime;  // 缺少下划线后缀
};
}
```

### 23.3.3 Git Commit 规范

**Commit Message 格式**：

```
[GLUTEN-1234] Brief description of the change

Detailed description of what this commit does.
Can be multiple paragraphs.

### What changes are proposed in this PR?
- Change 1
- Change 2

### Why are the changes needed?
Explain the motivation behind these changes.

### Does this PR introduce any user-facing changes?
Yes/No. If yes, describe the changes.
```

**示例**：

```
[GLUTEN-1234] Add support for Velox Window functions

This PR adds support for Window functions (lag, lead, rank) in Velox backend.

### What changes are proposed in this PR?
- Implement WindowTransformer for lag/lead/rank functions
- Add Substrait conversion for Window expressions
- Add unit tests for Window functions

### Why are the changes needed?
Many users need Window functions for time-series analysis and ranking.
Currently these functions fallback to Spark, causing performance issues.

### Does this PR introduce any user-facing changes?
Yes. Users can now use lag(), lead(), rank() functions with Gluten
without fallback, resulting in 3-5x performance improvement.
```

## 23.4 Issue 报告流程

### 23.4.1 提交 Bug Report

**Bug 模板**：

```markdown
## Bug Description
A clear and concise description of the bug.

## To Reproduce
Steps to reproduce the behavior:
1. Run this query: `SELECT ...`
2. With this configuration: `spark.conf.set(...)`
3. See error

## Expected Behavior
What you expected to happen.

## Actual Behavior
What actually happened (with error message).

## Environment
- Gluten Version: 1.2.0
- Spark Version: 3.3.1
- Backend: Velox
- OS: Ubuntu 22.04
- Java Version: 1.8.0_352

## Additional Context
Any other information, logs, screenshots.

## Logs
```
Paste relevant logs here
```
```

**示例 Issue**：

**标题**：`[BUG] OutOfMemoryError when running large aggregation with Velox backend`

**内容**：
```markdown
## Bug Description
Getting OutOfMemoryError when running large GROUP BY aggregation (1B rows) with Velox backend.

## To Reproduce
```scala
spark.conf.set("spark.plugins", "org.apache.gluten.GlutenPlugin")
spark.conf.set("spark.gluten.sql.columnar.backend.lib", "velox")
spark.conf.set("spark.memory.offHeap.size", "10g")

spark.range(1000000000)
  .selectExpr("id % 1000 as key", "id as value")
  .groupBy("key")
  .agg(sum("value"))
  .collect()
```

## Expected Behavior
Query should complete successfully, possibly with spilling to disk.

## Actual Behavior
Query fails with:
```
java.lang.OutOfMemoryError: Direct buffer memory
```

## Environment
- Gluten Version: 1.2.0
- Spark Version: 3.3.1
- Backend: Velox
- OS: Ubuntu 22.04
- Executor memory: 20g
- Off-heap memory: 10g

## Additional Context
The same query works fine with smaller dataset (100M rows).
Increasing off-heap to 20g helps but query becomes very slow (30 min vs 5 min expected).

## Logs
```
2024-01-01 10:00:00 ERROR Executor: Exception in task 0.0
java.lang.OutOfMemoryError: Direct buffer memory
    at java.nio.Bits.reserveMemory(Bits.java:695)
    at java.nio.DirectByteBuffer.<init>(DirectByteBuffer.java:123)
    at org.apache.gluten.vectorized.ArrowColumnarBatch.allocate(ArrowColumnarBatch.java:45)
    ...
```
```

### 23.4.2 提交 Feature Request

**Feature 模板**：

```markdown
## Feature Request
Clear description of the feature you'd like to see.

## Use Case
Describe the use case and why this feature is needed.

## Proposed Solution
If you have ideas on how to implement this, describe them here.

## Alternatives Considered
What other solutions have you considered?

## Additional Context
Any other information.
```

## 23.5 Pull Request 流程

### 23.5.1 创建 PR

```bash
# 1. 创建新分支
git checkout -b feature/add-window-support

# 2. 进行修改
# ... 编辑文件 ...

# 3. 运行测试
mvn test -Pbackends-velox

# 4. 格式化代码
./dev/scalafmt
find cpp -name "*.cc" | xargs clang-format -i

# 5. Commit
git add .
git commit -m "[GLUTEN-1234] Add Window function support"

# 6. Push 到你的 Fork
git push origin feature/add-window-support

# 7. 在 GitHub 上创建 PR
# 访问 https://github.com/YOUR_USERNAME/incubator-gluten
# 点击 "New Pull Request"
```

### 23.5.2 PR 描述模板

```markdown
### What changes are proposed in this PR?
- Change 1
- Change 2
- Change 3

### Why are the changes needed?
Explain the motivation and context.

### Does this PR introduce any user-facing changes?
Yes/No. If yes, describe what changes users will see.

### How was this PR tested?
- [ ] Unit tests
- [ ] Integration tests
- [ ] Manual testing with TPC-H queries
- [ ] Performance benchmark

### Checklist
- [ ] My code follows the project's code style
- [ ] I have added tests that prove my fix/feature works
- [ ] I have added necessary documentation
- [ ] All new and existing tests passed
```

### 23.5.3 Code Review 流程

```
PR 提交 → CI 自动测试 → Reviewer 审查 → 修改 → 再审查 → 合并
  ↓          ↓               ↓          ↓       ↓
 你        GitHub Actions  Committer  你    Committer
```

**Review 常见反馈**：

1. **代码风格**
   ```
   Comment: "Please run scalafmt to format the code."
   Action: ./dev/scalafmt && git commit --amend
   ```

2. **测试覆盖**
   ```
   Comment: "Can you add a unit test for the new function?"
   Action: Add test in GlutenPlanSuite.scala
   ```

3. **性能影响**
   ```
   Comment: "This change may impact performance. Can you run benchmarks?"
   Action: Run TPC-H and report results in PR
   ```

4. **文档更新**
   ```
   Comment: "Please update the documentation in docs/."
   Action: Add entry to docs/operators.md
   ```

**处理反馈**：

```bash
# 1. 在原分支上修改
git checkout feature/add-window-support

# 2. 进行修改
# ... 修复 review 意见 ...

# 3. Commit 修改（使用 --amend 保持单个 commit）
git add .
git commit --amend

# 4. Force push（因为 amended）
git push -f origin feature/add-window-support

# PR 会自动更新
```

## 23.6 社区沟通

### 23.6.1 邮件列表

**订阅邮件列表**：

```bash
# 开发者邮件列表
# 发送邮件到：dev-subscribe@gluten.apache.org
# 等待确认邮件，点击链接确认

# 用户邮件列表
# 发送邮件到：user-subscribe@gluten.apache.org
```

**邮件礼仪**：

1. **清晰的主题**
   ```
   ✅ [DISCUSS] Support for Spark 3.5
   ✅ [VOTE] Release Gluten 1.3.0
   ❌ Question
   ❌ Help
   ```

2. **引用上下文**
   ```
   > On 2024-01-01, User A wrote:
   > > Should we support Spark 3.5?
   >
   > I think yes, because...
   ```

3. **及时回复**
   - 24-48 小时内回复邮件
   - 如果需要更多时间，先回复说明

### 23.6.2 Slack 频道

**加入 Slack**：

```
1. 访问 https://the-asf.slack.com/
2. 使用 Apache ID 登录
3. 加入 #gluten 频道
```

**Slack 使用建议**：
- 用于快速讨论和实时沟通
- 重要决定应在邮件列表讨论
- 使用线程（Thread）保持对话有序

### 23.6.3 微信群组

**中文社区**：

Gluten 有活跃的中文微信群，便于国内开发者交流。

**加入方式**：
1. 扫描 GitHub README 中的二维码
2. 或搜索微信号：`gluten-community`

**群规**：
- 尊重他人，友善交流
- 不发广告和无关内容
- 技术问题优先在 GitHub/邮件列表讨论
- 重要讨论需同步到邮件列表

## 23.7 成为 Committer

### 23.7.1 Committer 标准

要成为 Committer，需要：

1. **持续贡献**
   - 至少 6个月的活跃贡献
   - 多个有价值的 PR（不仅是 typo 修复）
   - 参与 code review

2. **技术能力**
   - 深入理解 Gluten 架构
   - 高质量的代码
   - 能够独立设计和实现功能

3. **社区参与**
   - 参与邮件列表讨论
   - 帮助其他贡献者
   - 参与设计讨论

4. **责任心**
   - 及时回应 review 意见
   - 维护自己贡献的代码
   - 遵循 Apache Way

### 23.7.2 提名流程

```
PPMC Member 提名 → 私密讨论 → 投票 → 邀请 → 接受 → 公告
```

**投票标准**：
- 至少 3 个 +1 from PPMC
- 无 -1 (veto)
- 72 小时投票期

### 23.7.3 Committer 权限和责任

**权限**：
- 合并 PR 到主分支
- 创建 Release 分支
- 参与项目治理讨论

**责任**：
- Review 他人的 PR
- 维护代码质量
- 指导新贡献者
- 参与社区建设

## 本章小结

本章介绍了如何参与 Gluten 社区：

1. **Apache 项目**：Gluten 的孵化状态和组织结构
2. **贡献准备**：环境搭建、任务选择
3. **代码规范**：Scala/C++ 代码风格、Git commit 规范
4. **Issue 流程**：Bug 报告、Feature 请求
5. **PR 流程**：创建 PR、Code Review、处理反馈
6. **社区沟通**：邮件列表、Slack、微信群
7. **成为 Committer**：标准、流程、权限和责任

通过本章学习，你应该能够：
- ✅ 理解 Apache 开源项目的运作方式
- ✅ 提交高质量的 Issue 和 PR
- ✅ 参与社区讨论和建设
- ✅ 沿着 Contributor → Committer → PPMC 的路径成长

欢迎加入 Gluten 社区，一起打造更好的查询加速引擎！

## 参考资料

1. Apache Way：https://www.apache.org/theapacheway/
2. Apache Voting：https://www.apache.org/foundation/voting.html
3. Gluten Contributing Guide：https://github.com/apache/incubator-gluten/blob/main/CONTRIBUTING.md
4. GitHub Flow：https://guides.github.com/introduction/flow/
5. How to Write a Git Commit Message：https://chris.beams.io/posts/git-commit/
