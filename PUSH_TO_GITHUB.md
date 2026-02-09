# GitHub 推送指南

## 当前状态

✅ **本地仓库已准备就绪**
- 已完成 Part 1（3章，约49,000字）
- 已添加代码示例（13个文件）
- 已添加架构图和性能图表
- Git 仓库已初始化，共 6 次提交

## 推送到 GitHub 的步骤

### 步骤 1：在 GitHub 上创建仓库

1. 访问 https://github.com/new
2. 填写信息：
   - **Repository name**: `apache-gluten-book`（或 `gluten-book-zh`）
   - **Description**: `Apache Gluten 深入浅出 - 从入门到精通的中文指南`
   - **Visibility**: **Public**（推荐）或 Private
   - **❌ 不要勾选**：Initialize with README, .gitignore, license（我们已经有了）
3. 点击 **Create repository**

### 步骤 2：获取你的 GitHub 用户名

假设你的 GitHub 用户名是：**kunwan**（请替换为实际用户名）

### 步骤 3：添加远程仓库并推送

```bash
cd /home/kunwan/ws/apache-gluten-book

# 添加远程仓库（使用 HTTPS）
git remote add origin https://github.com/kunwan/apache-gluten-book.git

# 或使用 SSH（如果配置了 SSH 密钥）
# git remote add origin git@github.com:kunwan/apache-gluten-book.git

# 推送代码
git push -u origin main
```

### 步骤 4：验证推送成功

访问：https://github.com/kunwan/apache-gluten-book

应该能看到：
- ✅ README.md 显示在首页
- ✅ 文件目录结构
- ✅ 6 次提交历史

### 步骤 5：更新 README 中的链接

推送成功后，更新文件中的占位符：

```bash
cd /home/kunwan/ws/apache-gluten-book

# 替换用户名（使用实际的 GitHub 用户名）
sed -i 's/YOUR_USERNAME/kunwan/g' README.md
sed -i 's/YOUR_USERNAME/kunwan/g' mkdocs.yml
sed -i 's/YOUR_USERNAME/kunwan/g' index.md
sed -i 's/YOUR_USERNAME/kunwan/g' CONTRIBUTING.md

# 替换邮箱（可选）
sed -i 's/your-email@example.com/your-actual-email@example.com/g' README.md
sed -i 's/your-email@example.com/your-actual-email@example.com/g' mkdocs.yml
sed -i 's/your-email@example.com/your-actual-email@example.com/g' index.md

# 提交更新
git add README.md mkdocs.yml index.md CONTRIBUTING.md
git commit -m "Update repository URLs and email with actual information"
git push
```

## 可选：启用 GitHub Pages

### 方法 1：简单部署（直接使用 Markdown）

1. 进入仓库 → Settings → Pages
2. Source: Deploy from a branch
3. Branch: `main`, Folder: `/ (root)`
4. Save
5. 访问：https://kunwan.github.io/apache-gluten-book/

### 方法 2：使用 MkDocs（推荐，更美观）

创建 GitHub Actions 工作流：

```bash
cd /home/kunwan/ws/apache-gluten-book
mkdir -p .github/workflows

cat > .github/workflows/deploy-docs.yml << 'EOF'
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
      
      - name: Install dependencies
        run: |
          pip install mkdocs-material
          pip install mkdocs-git-revision-date-localized-plugin
      
      - name: Deploy to GitHub Pages
        run: mkdocs gh-deploy --force
EOF

# 提交并推送
git add .github/workflows/deploy-docs.yml
git commit -m "Add GitHub Actions workflow for MkDocs deployment"
git push
```

等待几分钟后，访问：https://kunwan.github.io/apache-gluten-book/

## 常见问题

### Q1: 推送被拒绝（Authentication failed）

**原因**：GitHub 不再支持密码认证

**解决方案 A：使用 Personal Access Token**

1. 访问 https://github.com/settings/tokens
2. Generate new token (classic)
3. 勾选 `repo` 权限
4. 生成 token 并复制
5. 推送时使用 token 作为密码：
   ```bash
   git push -u origin main
   # Username: kunwan
   # Password: [粘贴 token]
   ```

**解决方案 B：配置 SSH 密钥**

```bash
# 生成 SSH 密钥
ssh-keygen -t ed25519 -C "your-email@example.com"

# 复制公钥
cat ~/.ssh/id_ed25519.pub

# 在 GitHub 添加 SSH 密钥
# Settings → SSH and GPG keys → New SSH key
# 粘贴公钥内容

# 更改远程仓库地址为 SSH
git remote set-url origin git@github.com:kunwan/apache-gluten-book.git
git push -u origin main
```

### Q2: 推送被拒绝（rejected）

**原因**：远程仓库有更新

**解决方案**：

```bash
# 先拉取远程更新
git pull origin main --rebase

# 再推送
git push origin main
```

### Q3: 仓库已存在内容

**原因**：创建仓库时勾选了初始化选项

**解决方案**：

```bash
# 强制推送（注意：会覆盖远程内容）
git push -u origin main --force

# 或者先合并
git pull origin main --allow-unrelated-histories
git push -u origin main
```

## 后续维护

### 每次修改后推送：

```bash
cd /home/kunwan/ws/apache-gluten-book

# 查看修改
git status

# 添加修改
git add .

# 提交
git commit -m "Add chapter 4 content"

# 推送
git push
```

### 查看仓库信息：

```bash
# 查看远程仓库
git remote -v

# 查看提交历史
git log --oneline --graph

# 查看分支
git branch -a
```

## 下一步

推送成功后，你可以：

1. ✅ **分享链接**：邀请其他人查看或贡献
2. ✅ **启用 Issues**：收集读者反馈
3. ✅ **启用 Discussions**：创建讨论区
4. ✅ **添加 Topics**：在仓库页面添加标签（apache, gluten, spark, book等）
5. ✅ **添加 Star**：给仓库加星以便追踪

## 推广

推送后，可以在以下地方分享：

- Apache Gluten 邮件列表
- Spark 中文社区
- 相关技术论坛（如掘金、CSDN）
- 社交媒体（微信公众号、Twitter）

---

**准备好了吗？执行上面的命令开始推送！** 🚀
