# 第14章：源码环境搭建

> 本章要点：
> - 完整的 Gluten 开发环境搭建流程
> - Velox 和 ClickHouse 后端源码编译
> - IDE 配置和调试环境设置
> - Docker 容器化构建环境
> - 常见编译问题和解决方案

## 引言

要深入理解 Gluten 的实现原理，阅读和调试源码是必不可少的。本章将详细介绍如何搭建 Gluten 的开发环境，包括源码下载、依赖安装、编译构建、IDE 配置等各个环节。通过本章学习，你将能够在本地环境中编译和调试 Gluten 源码，为后续的源码分析和扩展开发打下基础。

## 14.1 开发环境准备

### 14.1.1 硬件要求

**最低配置**：
- CPU: 4核心（推荐8核心以上）
- 内存: 16GB（推荐32GB以上）
- 磁盘: 100GB 可用空间（推荐SSD）

**推荐配置**：
- CPU: 16核心或更多
- 内存: 64GB
- 磁盘: 500GB SSD
- 网络: 稳定的互联网连接（用于下载依赖）

**编译时间参考**（基于推荐配置）：
| 组件 | 首次编译 | 增量编译 |
|------|----------|----------|
| Gluten Core | ~15分钟 | ~2分钟 |
| Velox Backend | ~45分钟 | ~5分钟 |
| ClickHouse Backend | ~60分钟 | ~8分钟 |
| 全量编译 | ~90分钟 | ~10分钟 |

### 14.1.2 操作系统要求

**支持的操作系统**：

1. **Ubuntu 20.04/22.04**（推荐）
2. **CentOS 8 / Rocky Linux 8**
3. **macOS 12+ (Intel/Apple Silicon)**
4. **Docker 容器**（任意主机OS）

本章以 **Ubuntu 22.04** 为例进行说明。

### 14.1.3 基础软件安装

#### Ubuntu/Debian 系统

```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 安装基础开发工具
sudo apt install -y \
    build-essential \
    cmake \
    ccache \
    ninja-build \
    git \
    wget \
    curl \
    unzip

# 安装编译依赖
sudo apt install -y \
    libssl-dev \
    libboost-all-dev \
    libcurl4-openssl-dev \
    libdouble-conversion-dev \
    libgoogle-glog-dev \
    libgflags-dev \
    libiberty-dev \
    libevent-dev \
    libre2-dev \
    libsnappy-dev \
    liblz4-dev \
    libzstd-dev \
    libbz2-dev \
    liblzo2-dev \
    zlib1g-dev

# 安装 Python 依赖
sudo apt install -y \
    python3 \
    python3-pip \
    python3-dev

# 安装 Protobuf
sudo apt install -y \
    protobuf-compiler \
    libprotobuf-dev
```

#### CentOS/Rocky Linux 系统

```bash
# 启用 EPEL 和 PowerTools
sudo dnf install -y epel-release
sudo dnf config-manager --set-enabled powertools

# 安装开发工具组
sudo dnf groupinstall -y "Development Tools"

# 安装 CMake 3.20+
sudo dnf install -y cmake

# 安装编译依赖
sudo dnf install -y \
    openssl-devel \
    boost-devel \
    libcurl-devel \
    double-conversion-devel \
    glog-devel \
    gflags-devel \
    libevent-devel \
    re2-devel \
    snappy-devel \
    lz4-devel \
    libzstd-devel \
    bzip2-devel \
    lzo-devel \
    zlib-devel
```

#### macOS 系统

```bash
# 安装 Homebrew（如果未安装）
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 安装开发工具
brew install \
    cmake \
    ccache \
    ninja \
    git \
    wget \
    boost \
    openssl \
    double-conversion \
    glog \
    gflags \
    libevent \
    re2 \
    snappy \
    lz4 \
    zstd \
    protobuf
```

### 14.1.4 Java 环境

```bash
# 安装 OpenJDK 8（Gluten 主要支持）
sudo apt install -y openjdk-8-jdk

# 或安装 OpenJDK 17（实验性支持）
sudo apt install -y openjdk-17-jdk

# 配置 JAVA_HOME
echo 'export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc

# 验证安装
java -version
javac -version
```

### 14.1.5 Maven 安装

```bash
# 安装 Maven 3.8+
cd /tmp
wget https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz
sudo tar -xzf apache-maven-3.9.6-bin.tar.gz -C /opt
sudo ln -s /opt/apache-maven-3.9.6 /opt/maven

# 配置环境变量
echo 'export MAVEN_HOME=/opt/maven' >> ~/.bashrc
echo 'export PATH=$MAVEN_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc

# 验证安装
mvn -version
```

**Maven 配置优化** (`~/.m2/settings.xml`)：

```xml
<settings>
  <mirrors>
    <!-- 使用阿里云镜像加速（国内用户） -->
    <mirror>
      <id>aliyun-maven</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
  
  <profiles>
    <profile>
      <id>jdk-8</id>
      <activation>
        <activeByDefault>true</activeByDefault>
        <jdk>1.8</jdk>
      </activation>
      <properties>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
      </properties>
    </profile>
  </profiles>
</settings>
```

### 14.1.6 编译器版本要求

**GCC/G++ 版本**：
- 最低要求: GCC 9.0+
- 推荐版本: GCC 11.0+

```bash
# 检查 GCC 版本
gcc --version
g++ --version

# Ubuntu 上安装 GCC 11
sudo apt install -y gcc-11 g++-11

# 设置为默认编译器
sudo update-alternatives --install /usr/bin/gcc gcc /usr/bin/gcc-11 100
sudo update-alternatives --install /usr/bin/g++ g++ /usr/bin/g++-11 100
```

**Clang 版本**（可选，macOS 默认）：
- 最低要求: Clang 12.0+
- 推荐版本: Clang 15.0+

```bash
# Ubuntu 上安装 Clang 15
sudo apt install -y clang-15 libc++-15-dev libc++abi-15-dev
```

## 14.2 源码下载和编译

### 14.2.1 获取 Gluten 源码

```bash
# 创建工作目录
mkdir -p ~/workspace
cd ~/workspace

# 克隆 Gluten 仓库
git clone https://github.com/apache/incubator-gluten.git
cd incubator-gluten

# 查看可用分支和标签
git branch -a
git tag -l

# 切换到最新稳定分支（例如）
git checkout branch-1.2

# 或切换到特定标签
git checkout v1.2.0
```

**目录结构说明**：

```
incubator-gluten/
├── cpp/                    # C++ 核心代码
│   ├── core/              # 核心 JNI 接口
│   ├── velox/             # Velox 后端实现
│   └── CMakeLists.txt
├── backends-velox/         # Velox 后端 JVM 代码
├── backends-clickhouse/    # ClickHouse 后端 JVM 代码
├── cpp-ch/                # ClickHouse 后端 C++ 代码
├── gluten-core/           # Gluten 核心模块（Scala）
├── gluten-substrait/      # Substrait 转换模块
├── gluten-arrow/          # Arrow 集成模块
├── shims/                 # Spark 版本兼容层
├── tools/                 # 构建和测试工具
├── pom.xml                # Maven 主配置
└── build.sh               # 一键构建脚本
```

### 14.2.2 编译 Velox 后端

#### 方式1：使用一键脚本（推荐）

```bash
cd ~/workspace/incubator-gluten

# 编译 Velox 后端（包含 JVM 和 Native 部分）
./dev/buildbundle-veloxbe.sh

# 可选参数：
# --build_type=Release    # Release 构建（默认）
# --build_type=Debug      # Debug 构建（用于调试）
# --build_tests=ON        # 编译测试（默认 OFF）
# --enable_s3=ON          # 启用 S3 支持
# --enable_hdfs=ON        # 启用 HDFS 支持
# --enable_abfs=ON        # 启用 Azure Blob 支持
```

**完整编译示例**（包含所有功能）：

```bash
./dev/buildbundle-veloxbe.sh \
    --build_type=Release \
    --build_tests=ON \
    --enable_s3=ON \
    --enable_hdfs=ON \
    --enable_abfs=ON
```

#### 方式2：分步编译

```bash
# 步骤1：编译 Velox 依赖
cd ~/workspace/incubator-gluten
./ep/build-velox/src/build_velox.sh

# 步骤2：编译 Gluten C++ 代码
cd cpp
mkdir -p build && cd build
cmake -DBUILD_VELOX_BACKEND=ON \
      -DCMAKE_BUILD_TYPE=Release \
      -GNinja \
      ..
ninja

# 步骤3：编译 Gluten JVM 代码
cd ~/workspace/incubator-gluten
mvn clean package -Pbackends-velox -Pspark-3.3 -DskipTests

# 指定 Spark 版本：
# -Pspark-3.2  # Spark 3.2.x
# -Pspark-3.3  # Spark 3.3.x
# -Pspark-3.4  # Spark 3.4.x
# -Pspark-3.5  # Spark 3.5.x
```

#### 编译产物

成功编译后，产物位于：

```
package/target/
└── gluten-velox-bundle-spark3.3_2.12-<version>-jar-with-dependencies.jar
```

### 14.2.3 编译 ClickHouse 后端

#### 使用一键脚本

```bash
cd ~/workspace/incubator-gluten

# 编译 ClickHouse 后端
./dev/buildbundle-clickhousebe.sh

# 可选参数：
# --build_type=Release    # Release 构建
# --build_type=Debug      # Debug 构建
```

**完整编译示例**：

```bash
./dev/buildbundle-clickhousebe.sh \
    --build_type=Release
```

#### 分步编译

```bash
# 步骤1：编译 ClickHouse Local Engine
cd ~/workspace/incubator-gluten/cpp-ch
./build_clickhouse.sh

# 步骤2：编译 Gluten ClickHouse 桥接层
cd ../cpp
mkdir -p build-ch && cd build-ch
cmake -DBUILD_CLICKHOUSE_BACKEND=ON \
      -DCMAKE_BUILD_TYPE=Release \
      -GNinja \
      ..
ninja

# 步骤3：编译 Gluten JVM 代码
cd ~/workspace/incubator-gluten
mvn clean package -Pbackends-clickhouse -Pspark-3.3 -DskipTests
```

#### 编译产物

```
package/target/
└── gluten-clickhouse-bundle-spark3.3_2.12-<version>-jar-with-dependencies.jar
```

### 14.2.4 使用 ccache 加速编译

```bash
# 安装 ccache
sudo apt install -y ccache

# 配置 ccache
ccache --max-size=20G
ccache --set-config=compression=true

# 配置环境变量
export CC="ccache gcc"
export CXX="ccache g++"

# 或在 CMake 中启用
cmake -DCMAKE_C_COMPILER_LAUNCHER=ccache \
      -DCMAKE_CXX_COMPILER_LAUNCHER=ccache \
      ...

# 查看 ccache 统计
ccache -s
```

**加速效果**：
- 首次编译: ~45分钟（Velox）
- 二次编译: ~5分钟（使用 ccache）
- 增量编译: ~2分钟

### 14.2.5 并行编译优化

```bash
# 使用 Ninja（比 Make 更快）
cmake -GNinja ...
ninja -j$(nproc)  # 使用所有核心

# 或使用 Make
make -j$(nproc)

# Maven 并行编译
mvn clean package -T 4C  # 使用 4倍CPU核心数的线程
```

## 14.3 IDE 配置

### 14.3.1 IntelliJ IDEA 配置

#### 导入项目

1. **打开 IntelliJ IDEA**
2. **File → Open → 选择 `incubator-gluten` 目录**
3. **选择 "Import as Maven project"**
4. **等待索引完成**（首次可能需要10-20分钟）

#### JDK 配置

1. **File → Project Structure → Project**
2. **Project SDK**: 选择 JDK 8 或 JDK 17
3. **Project language level**: 设置为 8

#### Maven 配置

1. **File → Settings → Build, Execution, Deployment → Build Tools → Maven**
2. **Maven home directory**: 指向 Maven 安装目录
3. **User settings file**: 指向 `~/.m2/settings.xml`
4. **Profiles**: 勾选 `backends-velox` 和 `spark-3.3`

#### Scala 插件

1. **File → Settings → Plugins**
2. **搜索并安装 "Scala" 插件**
3. **重启 IDEA**

#### 代码风格

导入 Gluten 代码风格配置：

```bash
# 配置文件位于
incubator-gluten/dev/intellij-code-style.xml
```

1. **File → Settings → Editor → Code Style**
2. **点击齿轮图标 → Import Scheme → IntelliJ IDEA code style XML**
3. **选择 `dev/intellij-code-style.xml`**

#### 运行配置

创建 Spark Shell 运行配置：

1. **Run → Edit Configurations → Add New → Application**
2. **配置如下**：

```
Name: Gluten Spark Shell
Main class: org.apache.spark.deploy.SparkSubmit
VM options:
  -Xmx4g
  -XX:+UseG1GC
  
Program arguments:
  --master local[*]
  --driver-memory 4g
  --conf spark.plugins=org.apache.gluten.GlutenPlugin
  --conf spark.gluten.sql.columnar.backend.lib=velox
  --conf spark.memory.offHeap.enabled=true
  --conf spark.memory.offHeap.size=2g
  --jars /path/to/gluten-velox-bundle.jar
  spark-shell

Working directory: $ProjectFileDir$
```

### 14.3.2 CLion 配置（C++ 开发）

#### 导入 CMake 项目

1. **打开 CLion**
2. **File → Open → 选择 `incubator-gluten/cpp` 目录**
3. **CLion 会自动检测 CMakeLists.txt**

#### CMake 配置

1. **File → Settings → Build, Execution, Deployment → CMake**
2. **添加 Release 配置**：

```
Name: Release-Velox
Build type: Release
CMake options:
  -DBUILD_VELOX_BACKEND=ON
  -DCMAKE_BUILD_TYPE=Release
  -DCMAKE_EXPORT_COMPILE_COMMANDS=ON
Generation path: build-release-velox
```

3. **添加 Debug 配置**：

```
Name: Debug-Velox
Build type: Debug
CMake options:
  -DBUILD_VELOX_BACKEND=ON
  -DCMAKE_BUILD_TYPE=Debug
  -DCMAKE_EXPORT_COMPILE_COMMANDS=ON
Generation path: build-debug-velox
```

#### 代码格式化

```bash
# 安装 clang-format
sudo apt install -y clang-format

# 使用 Gluten 的 .clang-format 配置
cd ~/workspace/incubator-gluten/cpp
clang-format -i <file>.cpp
```

### 14.3.3 VS Code 配置

#### 安装插件

```json
// .vscode/extensions.json
{
  "recommendations": [
    "ms-vscode.cpptools",
    "ms-python.python",
    "scalameta.metals",
    "redhat.java",
    "vscjava.vscode-maven",
    "twxs.cmake",
    "ms-vscode.cmake-tools"
  ]
}
```

#### C++ 配置

```json
// .vscode/c_cpp_properties.json
{
  "configurations": [
    {
      "name": "Linux",
      "includePath": [
        "${workspaceFolder}/cpp/**",
        "${workspaceFolder}/ep/build-velox/build/velox_ep/include",
        "/usr/include"
      ],
      "defines": [],
      "compilerPath": "/usr/bin/g++",
      "cStandard": "c17",
      "cppStandard": "c++17",
      "intelliSenseMode": "linux-gcc-x64",
      "compileCommands": "${workspaceFolder}/cpp/build/compile_commands.json"
    }
  ]
}
```

#### CMake 配置

```json
// .vscode/settings.json
{
  "cmake.sourceDirectory": "${workspaceFolder}/cpp",
  "cmake.buildDirectory": "${workspaceFolder}/cpp/build",
  "cmake.configureArgs": [
    "-DBUILD_VELOX_BACKEND=ON",
    "-DCMAKE_BUILD_TYPE=Release",
    "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON"
  ],
  "C_Cpp.default.compileCommands": "${workspaceFolder}/cpp/build/compile_commands.json"
}
```

## 14.4 调试环境配置

### 14.4.1 GDB 调试 Native 代码

#### 编译 Debug 版本

```bash
cd ~/workspace/incubator-gluten/cpp
mkdir -p build-debug && cd build-debug

cmake -DBUILD_VELOX_BACKEND=ON \
      -DCMAKE_BUILD_TYPE=Debug \
      -DCMAKE_CXX_FLAGS="-g -O0" \
      -GNinja \
      ..
ninja
```

#### GDB 调试 Spark 任务

```bash
# 启动 Spark Shell（等待调试器连接）
spark-shell \
  --master local[1] \
  --driver-memory 4g \
  --conf spark.plugins=org.apache.gluten.GlutenPlugin \
  --conf spark.gluten.sql.columnar.backend.lib=velox \
  --conf spark.memory.offHeap.enabled=true \
  --conf spark.memory.offHeap.size=2g \
  --conf spark.executorEnv.LD_LIBRARY_PATH=/path/to/gluten/cpp/build-debug \
  --jars /path/to/gluten-velox-bundle.jar

# 在另一个终端，附加 GDB 到 Spark 进程
ps aux | grep spark
sudo gdb -p <spark_pid>

# 在 GDB 中设置断点
(gdb) break VeloxPlanConverter::toVeloxPlan
(gdb) continue

# 在 Spark Shell 中执行查询
scala> spark.sql("SELECT * FROM table").collect()

# GDB 会在断点处停下
```

#### 常用 GDB 命令

```gdb
# 设置断点
break gluten::VeloxBackend::initialize
break velox/exec/Driver.cpp:123

# 查看调用栈
backtrace
frame 3

# 查看变量
print variable_name
print *this

# 单步调试
step      # 进入函数
next      # 跳过函数
finish    # 执行到函数返回

# 继续执行
continue

# 查看源码
list
```

### 14.4.2 LLDB 调试（macOS）

```bash
# 类似 GDB，但使用 LLDB
lldb -p <spark_pid>

# LLDB 命令
(lldb) breakpoint set --name VeloxPlanConverter::toVeloxPlan
(lldb) continue
(lldb) bt
(lldb) frame variable
```

### 14.4.3 远程调试（JDWP）

#### 启动 Spark 远程调试

```bash
spark-shell \
  --master local[*] \
  --driver-java-options "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005" \
  --jars /path/to/gluten-velox-bundle.jar
```

#### IntelliJ IDEA 连接

1. **Run → Edit Configurations → Add New → Remote JVM Debug**
2. **配置**：
   ```
   Name: Gluten Remote Debug
   Host: localhost
   Port: 5005
   ```
3. **点击 Debug 按钮连接**

#### 设置断点

在 Gluten 源码中设置断点，例如：
- `GlutenPlugin.scala` 的 `driverPlugin` 方法
- `VeloxBackend.scala` 的 `initialize` 方法
- `ColumnarToRowExec.scala` 的 `doExecute` 方法

### 14.4.4 日志配置

#### 启用 Gluten Debug 日志

```bash
# 配置 log4j2.properties
spark.driver.extraJavaOptions=-Dlog4j.configuration=file:///path/to/log4j2.properties

# log4j2.properties 内容
log4j.logger.org.apache.gluten=DEBUG
log4j.logger.org.apache.spark.sql.execution.GlutenPlugin=TRACE
```

#### Native 层日志

```cpp
// 在 C++ 代码中使用 glog
#include <glog/logging.h>

LOG(INFO) << "Velox plan: " << plan->toString();
LOG(WARNING) << "Fallback to Spark: " << reason;
VLOG(1) << "Detailed debug info";  // 需要 --v=1 启动参数
```

启用 Native 日志：

```bash
export GLOG_logtostderr=1
export GLOG_v=1  # Verbosity level (0-4)
```

## 14.5 Docker 构建环境

### 14.5.1 使用官方 Docker 镜像

```bash
# 拉取构建镜像
docker pull apache/gluten:build-env-ubuntu22.04

# 运行容器
docker run -it \
  -v ~/workspace/incubator-gluten:/workspace/gluten \
  -w /workspace/gluten \
  apache/gluten:build-env-ubuntu22.04 \
  bash

# 在容器内编译
./dev/buildbundle-veloxbe.sh
```

### 14.5.2 自定义 Dockerfile

```dockerfile
# Dockerfile
FROM ubuntu:22.04

# 设置非交互模式
ENV DEBIAN_FRONTEND=noninteractive

# 安装基础工具
RUN apt-get update && apt-get install -y \
    build-essential \
    cmake \
    ninja-build \
    git \
    wget \
    openjdk-8-jdk \
    maven \
    && rm -rf /var/lib/apt/lists/*

# 安装 Gluten 依赖
RUN apt-get update && apt-get install -y \
    libssl-dev \
    libboost-all-dev \
    libcurl4-openssl-dev \
    && rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /workspace

# 设置环境变量
ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
ENV MAVEN_HOME=/usr/share/maven
ENV PATH=$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH

CMD ["/bin/bash"]
```

构建和使用：

```bash
# 构建镜像
docker build -t gluten-dev:latest .

# 运行容器（挂载源码目录）
docker run -it \
  -v ~/workspace/incubator-gluten:/workspace/gluten \
  -v ~/.m2:/root/.m2 \
  -w /workspace/gluten \
  gluten-dev:latest \
  bash
```

### 14.5.3 Docker Compose 配置

```yaml
# docker-compose.yml
version: '3.8'

services:
  gluten-dev:
    image: apache/gluten:build-env-ubuntu22.04
    container_name: gluten-dev
    volumes:
      - ~/workspace/incubator-gluten:/workspace/gluten
      - ~/.m2:/root/.m2
      - ccache-volume:/root/.ccache
    working_dir: /workspace/gluten
    command: /bin/bash
    stdin_open: true
    tty: true
    environment:
      - JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
      - CC=ccache gcc
      - CXX=ccache g++

volumes:
  ccache-volume:
```

使用：

```bash
# 启动容器
docker-compose up -d

# 进入容器
docker-compose exec gluten-dev bash

# 编译
./dev/buildbundle-veloxbe.sh

# 停止容器
docker-compose down
```

## 14.6 常见编译问题和解决方案

### 问题1：依赖库版本不匹配

**错误信息**：
```
error: 'boost::filesystem3' has not been declared
```

**解决方案**：
```bash
# 安装正确版本的 Boost
sudo apt install -y libboost1.74-dev
```

### 问题2：内存不足

**错误信息**：
```
c++: fatal error: Killed signal terminated program cc1plus
```

**解决方案**：
```bash
# 减少并行编译线程
ninja -j2  # 只使用2个线程

# 或增加 swap 空间
sudo fallocate -l 8G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
```

### 问题3：Maven 依赖下载失败

**错误信息**：
```
Could not resolve dependencies for project org.apache.gluten:gluten-parent
```

**解决方案**：
```bash
# 使用国内镜像（见 14.1.5 节）
# 或使用代理
export http_proxy=http://proxy.example.com:8080
export https_proxy=http://proxy.example.com:8080

# 清理并重新下载
mvn clean
rm -rf ~/.m2/repository
mvn package -DskipTests
```

### 问题4：Protobuf 版本冲突

**错误信息**：
```
This file was generated by an older version of protoc
```

**解决方案**：
```bash
# 安装指定版本的 Protobuf
cd /tmp
wget https://github.com/protocolbuffers/protobuf/releases/download/v21.9/protobuf-cpp-3.21.9.tar.gz
tar -xzf protobuf-cpp-3.21.9.tar.gz
cd protobuf-3.21.9
./configure
make -j$(nproc)
sudo make install
sudo ldconfig
```

### 问题5：找不到 Velox 库

**错误信息**：
```
libvelox.so: cannot open shared object file: No such file or directory
```

**解决方案**：
```bash
# 设置 LD_LIBRARY_PATH
export LD_LIBRARY_PATH=/path/to/gluten/cpp/build/releases:$LD_LIBRARY_PATH

# 或在 spark-defaults.conf 中配置
spark.executorEnv.LD_LIBRARY_PATH=/path/to/gluten/cpp/build/releases
```

### 问题6：JNI 头文件找不到

**错误信息**：
```
fatal error: jni.h: No such file or directory
```

**解决方案**：
```bash
# 确保 JAVA_HOME 正确设置
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

# 在 CMake 中指定
cmake -DJAVA_HOME=$JAVA_HOME ...
```

## 14.7 验证编译结果

### 14.7.1 运行单元测试

```bash
# Java 单元测试
cd ~/workspace/incubator-gluten
mvn test -Pbackends-velox -Pspark-3.3

# C++ 单元测试
cd cpp/build
ctest --output-on-failure
```

### 14.7.2 运行简单查询

```bash
# 启动 Spark Shell
spark-shell \
  --master local[*] \
  --driver-memory 4g \
  --conf spark.plugins=org.apache.gluten.GlutenPlugin \
  --conf spark.gluten.sql.columnar.backend.lib=velox \
  --conf spark.memory.offHeap.enabled=true \
  --conf spark.memory.offHeap.size=2g \
  --jars package/target/gluten-velox-bundle-spark3.3_2.12-<version>.jar

# 执行测试查询
scala> val df = spark.range(1000000).selectExpr("id", "id % 100 as key", "rand() as value")
scala> df.groupBy("key").agg(sum("value")).show()

# 检查是否使用了 Gluten（查看执行计划）
scala> df.groupBy("key").agg(sum("value")).explain()
```

查看执行计划中是否包含 `VeloxColumnarToRow` 或 `GlutenHashAggregate` 等算子。

### 14.7.3 检查 Native 库加载

```scala
// 在 Spark Shell 中执行
scala> System.getProperty("java.library.path")

// 检查 Gluten 插件是否加载
scala> spark.conf.get("spark.plugins")
```

## 本章小结

本章详细介绍了 Gluten 开发环境的搭建流程：

1. **环境准备**：操作系统、基础软件、编译器、Java、Maven 等依赖的安装
2. **源码编译**：Velox 和 ClickHouse 两种后端的编译方法，包括一键脚本和分步编译
3. **IDE 配置**：IntelliJ IDEA、CLion、VS Code 三种 IDE 的详细配置
4. **调试环境**：GDB/LLDB Native 调试、JDWP 远程调试、日志配置
5. **Docker 环境**：容器化构建环境的使用和自定义
6. **问题排查**：常见编译问题和解决方案

通过本章的学习，你应该能够：
- ✅ 搭建完整的 Gluten 开发环境
- ✅ 成功编译 Velox 和 ClickHouse 后端
- ✅ 配置 IDE 进行代码阅读和调试
- ✅ 解决常见的编译和运行时问题

下一章我们将深入分析 Gluten 核心模块的源码实现。

## 参考资料

1. Gluten 官方文档：https://github.com/apache/incubator-gluten/tree/main/docs
2. Velox 编译指南：https://facebookincubator.github.io/velox/develop/
3. CMake 官方文档：https://cmake.org/documentation/
4. GDB 调试手册：https://sourceware.org/gdb/documentation/
5. Maven 官方指南：https://maven.apache.org/guides/
