# GitHub 仓库创建和推送指南

本文档说明如何将本地仓库推送到 GitHub。

## 前提条件

- 已有 GitHub 账号
- 已安装 Git
- 已配置 Git 凭据或 SSH 密钥

## 步骤

### 1. 在 GitHub 上创建新仓库

1. 访问 [https://github.com/new](https://github.com/new)
2. 填写仓库信息：
   - **Repository name**: `apache-gluten-book`（或其他名称，如 `gluten-book-zh`）
   - **Description**: `Apache Gluten 深入浅出 - 从入门到精通的中文指南`
   - **Visibility**: Public（公开）或 Private（私有）
   - **不要** 勾选 "Initialize this repository with a README"（我们已经有了）
   - **不要** 添加 .gitignore 或 license（我们已经有了）
3. 点击 "Create repository"

### 2. 推送本地代码到 GitHub

GitHub 会显示推送命令，使用以下命令（替换 YOUR_USERNAME 为你的 GitHub 用户名）：

```bash
cd /home/kunwan/ws/apache-gluten-book

# 添加远程仓库
git remote add origin https://github.com/YOUR_USERNAME/apache-gluten-book.git

# 或者使用 SSH（如果你配置了 SSH 密钥）
# git remote add origin git@github.com:YOUR_USERNAME/apache-gluten-book.git

# 推送代码
git push -u origin main
```

### 3. 更新 README.md 中的链接

推送成功后，需要更新 README.md 中的占位符：

```bash
# 在 README.md 中将 YOUR_USERNAME 替换为你的实际 GitHub 用户名
sed -i 's/YOUR_USERNAME/你的用户名/g' README.md
sed -i 's/your-email@example.com/你的邮箱/g' README.md

# 同样更新 mkdocs.yml
sed -i 's/YOUR_USERNAME/你的用户名/g' mkdocs.yml
sed -i 's/your-email@example.com/你的邮箱/g' mkdocs.yml

# 提交更新
git add README.md mkdocs.yml
git commit -m "Update repository URLs with actual username"
git push
```

### 4. （可选）启用 GitHub Pages

如果想要将书发布为静态网站：

1. 在 GitHub 仓库页面，点击 "Settings"
2. 在左侧菜单找到 "Pages"
3. 在 "Source" 下选择分支和文件夹：
   - Branch: `main`
   - Folder: `/ (root)` 或者使用 GitHub Actions 自动构建
4. 点击 "Save"

#### 使用 MkDocs 构建（推荐）

创建 GitHub Actions 工作流来自动构建和部署：

创建文件 `.github/workflows/deploy-docs.yml`:

```yaml
name: Deploy Documentation

on:
  push:
    branches:
      - main

permissions:
  contents: write

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-python@v4
        with:
          python-version: 3.x
      - run: pip install mkdocs-material mkdocs-git-revision-date-localized-plugin
      - run: mkdocs gh-deploy --force
```

### 5. 验证

- 访问 `https://github.com/YOUR_USERNAME/apache-gluten-book` 查看仓库
- 如果启用了 GitHub Pages，访问 `https://YOUR_USERNAME.github.io/apache-gluten-book`

## 后续工作

### 创建 Google Docs

1. 访问 [Google Docs](https://docs.google.com)
2. 创建新文档，命名为 "Apache Gluten 深入浅出"
3. 创建文档结构（参考 README.md 的目录）
4. 在 README.md 中添加 Google Docs 链接

### 邀请协作者

如果有其他人想要贡献：

1. 在 GitHub 仓库页面，点击 "Settings" > "Collaborators"
2. 点击 "Add people"
3. 输入 GitHub 用户名或邮箱

## 问题排查

### Push 失败：Authentication failed

**解决方案**：
- 使用 Personal Access Token 代替密码
- 或配置 SSH 密钥

### Push 被拒绝：Updates were rejected

**解决方案**：
```bash
git pull origin main --rebase
git push origin main
```

## 需要帮助？

- 查看 [GitHub 文档](https://docs.github.com)
- 提交 Issue: `https://github.com/YOUR_USERNAME/apache-gluten-book/issues`
