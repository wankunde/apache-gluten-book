# 贡献指南

首先，感谢你愿意为《Apache Gluten 深入浅出》做出贡献！

## 如何贡献

### 报告错误或建议改进

如果你发现书中的错误（如拼写错误、技术错误、代码问题等），或者有改进建议，请：

1. 在 GitHub 上[创建一个 Issue](https://github.com/YOUR_USERNAME/apache-gluten-book/issues/new)
2. 清楚地描述问题或建议
3. 如果是技术错误，请提供相关的章节和行号

### 贡献内容

我们欢迎以下类型的贡献：

- 修正错别字、语法错误
- 改进现有章节的内容
- 添加代码示例
- 添加图表和示意图
- 翻译（如果未来支持其他语言）
- 添加新的章节或附录（需要先讨论）

### 提交 Pull Request 的步骤

1. **Fork 仓库**
   ```bash
   # 在 GitHub 上点击 Fork 按钮
   ```

2. **克隆你的 Fork**
   ```bash
   git clone https://github.com/YOUR_USERNAME/apache-gluten-book.git
   cd apache-gluten-book
   ```

3. **创建分支**
   ```bash
   git checkout -b fix/chapter01-typo
   # 或
   git checkout -b feature/add-code-example
   ```

4. **做出修改**
   - 编辑相关文件
   - 确保遵循写作规范（见下文）

5. **提交修改**
   ```bash
   git add .
   git commit -m "修正第1章的拼写错误"
   ```

6. **推送到你的 Fork**
   ```bash
   git push origin fix/chapter01-typo
   ```

7. **创建 Pull Request**
   - 在 GitHub 上访问你的 Fork
   - 点击 "New Pull Request"
   - 填写 PR 描述，说明你做了什么改动

### 写作规范

#### Markdown 格式

- 使用标准 Markdown 语法
- 章节标题使用 `#`、`##`、`###` 等层级
- 代码块使用三个反引号，并指定语言：
  ````markdown
  ```scala
  // 代码示例
  ```
  ````

#### 中文写作规范

- 使用简体中文
- 中英文之间加空格（例如："Gluten 是一个"而不是"Gluten是一个"）
- 使用中文标点符号（除了代码中）
- 专有名词使用正确的大小写（如 Apache Spark、Velox、ClickHouse）
- 保持术语一致性（参考附录C术语表）

#### 代码示例规范

- 代码要可运行、有效
- 添加必要的注释
- 使用有意义的变量名
- 遵循相关语言的代码规范（Scala、Java、Python 等）

#### 图表规范

- 优先使用 Mermaid 图表（便于版本控制）
- 如果使用图片，放在 `images/` 目录
- 图片文件名使用有意义的英文名称
- 在 Markdown 中添加图片的 alt 文本

### 章节结构模板

每个章节应该包含：

```markdown
# 第X章：章节标题

> **本章要点**：列出本章的核心要点（3-5 条）

## 引言

简要介绍本章内容和学习目标。

## X.1 小节标题

内容...

### 示例

代码或实例...

### 注意事项

提醒读者需要注意的地方...

## X.2 小节标题

内容...

## 本章小结

总结本章的核心内容。

## 参考资料

- [链接1](url)
- [链接2](url)
```

### 代码审查流程

所有 Pull Request 都会经过审查：

1. 自动检查（如果配置了 CI）
2. 内容审查（准确性、清晰度）
3. 技术审查（代码示例、技术细节）
4. 语言审查（中文写作质量）

审查者可能会：
- 批准并合并
- 请求修改
- 提供建议

请耐心等待审查，我们会尽快响应。

### 贡献者行为准则

- 尊重所有贡献者
- 接受建设性的批评
- 关注对项目最有利的方面
- 友好和耐心地对待其他社区成员

## 内容优先级

当前优先级（按顺序）：

1. **第一部分：入门篇** - 让初学者能快速上手
2. **第二部分：架构篇** - 深入理解核心机制
3. **第三部分：后端引擎篇** - 详解 Velox 和 ClickHouse
4. **附录** - 提供快速参考
5. **第四部分：源码剖析篇** - 面向高级开发者
6. **第五部分：实战篇** - 真实案例分析

## 技术栈和工具

### 必需工具

- Git
- Markdown 编辑器（推荐：VS Code、Typora）
- GitHub 账号

### 可选工具

- **MkDocs**: 用于生成静态站点
  ```bash
  pip install mkdocs mkdocs-material
  mkdocs serve
  ```
- **Mermaid**: 用于创建图表
- **Draw.io**: 用于创建复杂图表

## 获取帮助

如果你有任何问题：

1. 查看现有的 [Issues](https://github.com/YOUR_USERNAME/apache-gluten-book/issues)
2. 查看 [Discussions](https://github.com/YOUR_USERNAME/apache-gluten-book/discussions)
3. 创建新的 Issue 或 Discussion
4. 发送邮件至：[your-email@example.com]

## 致谢

感谢所有为本书做出贡献的人！你的每一份努力都让这本书变得更好。

---

再次感谢你的贡献！🎉
