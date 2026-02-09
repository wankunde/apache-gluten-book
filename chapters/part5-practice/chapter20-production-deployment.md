# 第20章：生产环境部署

> 本章要点：
> - 生产环境部署架构设计
> - Kubernetes 容器化部署方案
> - 高可用和容灾配置
> - 监控告警体系搭建
> - 安全配置和权限管理
> - 升级和回滚策略

## 引言

将 Gluten 部署到生产环境需要考虑可靠性、可用性、可维护性等多个方面。本章将提供一套完整的生产环境部署方案，涵盖架构设计、容器化部署、监控告警、安全配置等关键环节，帮助你安全稳定地将 Gluten 应用于生产系统。

## 20.1 部署架构设计

### 20.1.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        Load Balancer                         │
│                    (Nginx / HAProxy)                         │
└──────────────────────────┬──────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
┌───────▼──────────┐ ┌────▼─────────┐ ┌─────▼────────┐
│  Spark Master 1   │ │ Spark Master 2│ │Spark Master 3│
│   (Standby)       │ │  (Active)     │ │ (Standby)    │
└───────┬──────────┘ └────┬─────────┘ └─────┬────────┘
        │                  │                  │
        └─────────┬────────┴────────┬─────────┘
                  │   ZooKeeper     │
                  │   (HA Cluster)  │
                  └─────────────────┘
                           │
        ┌──────────────────┼──────────────────┬─────────────┐
        │                  │                  │             │
┌───────▼──────────┐ ┌────▼─────────┐ ┌─────▼────────┐ ┌─┴────────┐
│ Spark Worker 1    │ │Spark Worker 2│ │Spark Worker 3│ │  ...  N  │
│ + Gluten Velox    │ │+ Gluten Velox│ │+ Gluten Velox│ │          │
│ 64 cores, 256GB   │ │              │ │              │ │          │
└───────┬──────────┘ └────┬─────────┘ └─────┬────────┘ └─┬────────┘
        │                  │                  │             │
        └──────────────────┴──────────────────┴─────────────┘
                           │
                  ┌────────▼────────┐
                  │   Shared Storage │
                  │  (HDFS / S3 /    │
                  │   Azure Blob)    │
                  └──────────────────┘
```

### 20.1.2 资源规划

**小规模集群（<10 节点）**：

| 组件 | 数量 | 配置 | 用途 |
|------|------|------|------|
| Master | 1 | 8 cores, 32GB RAM | 任务调度 |
| Worker | 5-10 | 32 cores, 128GB RAM | 计算节点 |
| ZooKeeper | 3 | 4 cores, 16GB RAM | HA 协调 |

**中规模集群（10-50 节点）**：

| 组件 | 数量 | 配置 | 用途 |
|------|------|------|------|
| Master | 3 | 16 cores, 64GB RAM | 高可用 |
| Worker | 10-50 | 64 cores, 256GB RAM | 计算节点 |
| ZooKeeper | 3 | 8 cores, 32GB RAM | HA 协调 |
| Celeborn | 5 | 32 cores, 128GB RAM, 2TB SSD | Remote Shuffle |

**大规模集群（>50 节点）**：

| 组件 | 数量 | 配置 | 用途 |
|------|------|------|------|
| Master | 5 | 32 cores, 128GB RAM | 高可用 |
| Worker | 50+ | 64 cores, 256GB RAM, 1TB NVMe | 计算节点 |
| ZooKeeper | 5 | 16 cores, 64GB RAM | HA 协调 |
| Celeborn | 10+ | 64 cores, 256GB RAM, 4TB SSD | Remote Shuffle |
| Monitoring | 3 | 16 cores, 64GB RAM | Prometheus, Grafana |

### 20.1.3 网络规划

```
┌─────────────────────────────────────────┐
│         Public Network (1Gbps)           │
│         (User Access, API)               │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│      Management Network (10Gbps)         │
│  (Spark Master ↔ Workers, Monitoring)    │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│       Data Network (25/40Gbps)           │
│   (Shuffle, Storage Access, Replication) │
└──────────────────────────────────────────┘
```

**网络要求**：
- **公共网络**：1Gbps，用于 API 访问和监控
- **管理网络**：10Gbps，用于控制平面通信
- **数据网络**：25Gbps 或以上，用于 Shuffle 和存储访问

## 20.2 Kubernetes 部署

### 20.2.1 Namespace 和 Resource Quota

```yaml
# gluten-namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: gluten-prod
  labels:
    env: production
    app: gluten

---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: gluten-quota
  namespace: gluten-prod
spec:
  hard:
    requests.cpu: "1000"
    requests.memory: "2Ti"
    limits.cpu: "2000"
    limits.memory: "4Ti"
    persistentvolumeclaims: "50"
```

### 20.2.2 Spark Master Deployment（HA 模式）

```yaml
# spark-master-deployment.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: spark-master
  namespace: gluten-prod
spec:
  serviceName: spark-master-service
  replicas: 3
  selector:
    matchLabels:
      app: spark-master
  template:
    metadata:
      labels:
        app: spark-master
    spec:
      affinity:
        # 确保 Master 分布在不同节点
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchExpressions:
                  - key: app
                    operator: In
                    values:
                      - spark-master
              topologyKey: kubernetes.io/hostname
      containers:
        - name: spark-master
          image: spark:3.3.1-gluten-1.2.0
          ports:
            - containerPort: 7077  # Spark Master port
              name: spark
            - containerPort: 8080  # Web UI
              name: webui
          env:
            - name: SPARK_MODE
              value: "master"
            - name: SPARK_MASTER_OPTS
              value: "-Dspark.deploy.recoveryMode=ZOOKEEPER
                      -Dspark.deploy.zookeeper.url=zk-0.zk-service:2181,zk-1.zk-service:2181,zk-2.zk-service:2181
                      -Dspark.deploy.zookeeper.dir=/spark"
          resources:
            requests:
              cpu: "8"
              memory: "32Gi"
            limits:
              cpu: "16"
              memory: "64Gi"
          volumeMounts:
            - name: spark-conf
              mountPath: /opt/spark/conf
          livenessProbe:
            httpGet:
              path: /
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 5
      volumes:
        - name: spark-conf
          configMap:
            name: spark-config

---
apiVersion: v1
kind: Service
metadata:
  name: spark-master-service
  namespace: gluten-prod
spec:
  type: LoadBalancer
  selector:
    app: spark-master
  ports:
    - name: spark
      port: 7077
      targetPort: 7077
    - name: webui
      port: 8080
      targetPort: 8080
```

### 20.2.3 Spark Worker Deployment

```yaml
# spark-worker-deployment.yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: spark-worker
  namespace: gluten-prod
spec:
  selector:
    matchLabels:
      app: spark-worker
  template:
    metadata:
      labels:
        app: spark-worker
    spec:
      # 使用 hostNetwork 提高 Shuffle 性能
      hostNetwork: true
      nodeSelector:
        spark-worker: "true"  # 只在标记的节点上运行
      containers:
        - name: spark-worker
          image: spark:3.3.1-gluten-1.2.0
          env:
            - name: SPARK_MODE
              value: "worker"
            - name: SPARK_MASTER_URL
              value: "spark://spark-master-service:7077"
            - name: SPARK_WORKER_CORES
              value: "64"
            - name: SPARK_WORKER_MEMORY
              value: "200g"
            - name: SPARK_WORKER_OPTS
              value: "-Dspark.worker.cleanup.enabled=true
                      -Dspark.worker.cleanup.interval=1800"
          resources:
            requests:
              cpu: "60"
              memory: "240Gi"
            limits:
              cpu: "64"
              memory: "256Gi"
          volumeMounts:
            - name: gluten-native-libs
              mountPath: /opt/gluten/lib
            - name: spark-work-dir
              mountPath: /opt/spark/work
            - name: spark-conf
              mountPath: /opt/spark/conf
            - name: local-ssd
              mountPath: /mnt/ssd
          securityContext:
            # 允许访问 Huge Pages
            privileged: true
      volumes:
        - name: gluten-native-libs
          hostPath:
            path: /opt/gluten/lib
            type: Directory
        - name: spark-work-dir
          emptyDir: {}
        - name: spark-conf
          configMap:
            name: spark-config
        - name: local-ssd
          hostPath:
            path: /mnt/ssd
            type: Directory
```

### 20.2.4 ConfigMap 配置

```yaml
# spark-config.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: spark-config
  namespace: gluten-prod
data:
  spark-defaults.conf: |
    # Gluten 配置
    spark.plugins                                    org.apache.gluten.GlutenPlugin
    spark.gluten.sql.columnar.backend.lib            velox
    spark.memory.offHeap.enabled                     true
    spark.memory.offHeap.size                        128g
    
    # Shuffle 配置
    spark.shuffle.manager                            org.apache.spark.shuffle.sort.ColumnarShuffleManager
    spark.shuffle.service.enabled                    true
    spark.dynamicAllocation.enabled                  true
    spark.dynamicAllocation.minExecutors             10
    spark.dynamicAllocation.maxExecutors             100
    spark.dynamicAllocation.initialExecutors         20
    
    # 内存配置
    spark.executor.memory                            200g
    spark.executor.memoryOverhead                    50g
    spark.driver.memory                              32g
    spark.driver.memoryOverhead                      8g
    
    # 网络配置
    spark.network.timeout                            600s
    spark.rpc.askTimeout                             600s
    spark.executor.heartbeatInterval                 30s
    
    # 监控配置
    spark.metrics.namespace                          gluten_prod
    spark.metrics.conf.*.sink.prometheus.class       org.apache.spark.metrics.sink.PrometheusSink
    spark.metrics.conf.*.sink.prometheus.pushgateway-address prometheus-pushgateway:9091
    
    # 日志配置
    spark.eventLog.enabled                           true
    spark.eventLog.dir                               hdfs://namenode:9000/spark-logs
    spark.history.fs.logDirectory                    hdfs://namenode:9000/spark-logs
  
  log4j2.properties: |
    rootLogger.level = INFO
    rootLogger.appenderRef.stdout.ref = console
    rootLogger.appenderRef.file.ref = file
    
    # Console appender
    appender.console.type = Console
    appender.console.name = console
    appender.console.layout.type = PatternLayout
    appender.console.layout.pattern = %d{yyyy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n
    
    # File appender
    appender.file.type = RollingFile
    appender.file.name = file
    appender.file.fileName = /var/log/spark/spark.log
    appender.file.filePattern = /var/log/spark/spark-%d{yyyy-MM-dd}-%i.log.gz
    appender.file.layout.type = PatternLayout
    appender.file.layout.pattern = %d{yyyy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n
    appender.file.policies.type = Policies
    appender.file.policies.size.type = SizeBasedTriggeringPolicy
    appender.file.policies.size.size = 100MB
    appender.file.strategy.type = DefaultRolloverStrategy
    appender.file.strategy.max = 10
    
    # Gluten specific loggers
    logger.gluten.name = org.apache.gluten
    logger.gluten.level = INFO
    logger.velox.name = org.apache.gluten.backend.velox
    logger.velox.level = INFO
```

### 20.2.5 部署脚本

```bash
#!/bin/bash
# deploy-gluten.sh

set -e

NAMESPACE="gluten-prod"
KUBECTL="kubectl --namespace=$NAMESPACE"

echo "===> Creating namespace..."
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

echo "===> Applying resource quotas..."
kubectl apply -f gluten-namespace.yaml

echo "===> Creating ConfigMaps..."
kubectl apply -f spark-config.yaml

echo "===> Deploying ZooKeeper..."
kubectl apply -f zookeeper-statefulset.yaml
echo "Waiting for ZooKeeper to be ready..."
$KUBECTL rollout status statefulset/zk --timeout=5m

echo "===> Deploying Spark Master..."
kubectl apply -f spark-master-deployment.yaml
echo "Waiting for Spark Master to be ready..."
$KUBECTL rollout status statefulset/spark-master --timeout=5m

echo "===> Deploying Spark Workers..."
kubectl apply -f spark-worker-deployment.yaml
echo "Waiting for Spark Workers to be ready..."
$KUBECTL rollout status daemonset/spark-worker --timeout=10m

echo "===> Verifying deployment..."
$KUBECTL get pods
$KUBECTL get services

echo "===> Deployment complete!"
echo "Spark Master UI: http://$(kubectl get svc spark-master-service -n $NAMESPACE -o jsonpath='{.status.loadBalancer.ingress[0].ip}'):8080"
```

## 20.3 高可用配置

### 20.3.1 Master HA（ZooKeeper）

```yaml
# zookeeper-statefulset.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: zk
  namespace: gluten-prod
spec:
  serviceName: zk-service
  replicas: 3
  selector:
    matchLabels:
      app: zk
  template:
    metadata:
      labels:
        app: zk
    spec:
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchExpressions:
                  - key: app
                    operator: In
                    values:
                      - zk
              topologyKey: kubernetes.io/hostname
      containers:
        - name: zookeeper
          image: zookeeper:3.8
          ports:
            - containerPort: 2181
              name: client
            - containerPort: 2888
              name: server
            - containerPort: 3888
              name: leader-election
          env:
            - name: ZOO_MY_ID
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
            - name: ZOO_SERVERS
              value: "server.0=zk-0.zk-service:2888:3888;2181 server.1=zk-1.zk-service:2888:3888;2181 server.2=zk-2.zk-service:2888:3888;2181"
            - name: ZOO_TICK_TIME
              value: "2000"
            - name: ZOO_INIT_LIMIT
              value: "10"
            - name: ZOO_SYNC_LIMIT
              value: "5"
          volumeMounts:
            - name: datadir
              mountPath: /data
  volumeClaimTemplates:
    - metadata:
        name: datadir
      spec:
        accessModes: [ "ReadWriteOnce" ]
        resources:
          requests:
            storage: 50Gi

---
apiVersion: v1
kind: Service
metadata:
  name: zk-service
  namespace: gluten-prod
spec:
  clusterIP: None
  selector:
    app: zk
  ports:
    - name: client
      port: 2181
    - name: server
      port: 2888
    - name: leader-election
      port: 3888
```

### 20.3.2 健康检查配置

```scala
// 自定义健康检查端点
class GlutenHealthCheckServlet extends HttpServlet {
  override def doGet(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
    val checks = Map(
      "native_backend" -> checkNativeBackend(),
      "memory" -> checkMemory(),
      "disk" -> checkDisk()
    )
    
    val allHealthy = checks.values.forall(identity)
    
    resp.setContentType("application/json")
    resp.setStatus(if (allHealthy) 200 else 503)
    
    val json = s"""
    {
      "status": "${if (allHealthy) "healthy" else "unhealthy"}",
      "checks": {
        ${checks.map { case (k, v) => s""""$k": ${if (v) "\"ok\"" else "\"failed\""}""" }.mkString(",")}
      }
    }
    """
    resp.getWriter.write(json)
  }
  
  private def checkNativeBackend(): Boolean = {
    try {
      BackendFactory.getBackend.isInitialized()
    } catch {
      case _: Exception => false
    }
  }
  
  private def checkMemory(): Boolean = {
    val usedMemory = Runtime.getRuntime.totalMemory() - Runtime.getRuntime.freeMemory()
    val maxMemory = Runtime.getRuntime.maxMemory()
    usedMemory.toDouble / maxMemory < 0.9  // <90% 使用率
  }
  
  private def checkDisk(): Boolean = {
    val file = new File("/tmp")
    val freeSpace = file.getFreeSpace
    val totalSpace = file.getTotalSpace
    freeSpace.toDouble / totalSpace > 0.1  // >10% 可用空间
  }
}
```

### 20.3.3 自动故障转移

```yaml
# 使用 Kubernetes Deployment 的自动重启机制
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spark-application
spec:
  replicas: 1
  strategy:
    type: Recreate  # 确保只有一个实例运行
  template:
    spec:
      restartPolicy: Always  # 失败时自动重启
      containers:
        - name: spark-app
          image: my-spark-app:latest
          livenessProbe:
            httpGet:
              path: /health
              port: 4040
            initialDelaySeconds: 60
            periodSeconds: 30
            timeoutSeconds: 10
            failureThreshold: 3  # 3 次失败后重启
```

## 20.4 监控告警

### 20.4.1 Prometheus 配置

```yaml
# prometheus-config.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
  namespace: gluten-prod
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s
      evaluation_interval: 15s
    
    scrape_configs:
      # Spark Master metrics
      - job_name: 'spark-master'
        kubernetes_sd_configs:
          - role: pod
            namespaces:
              names:
                - gluten-prod
        relabel_configs:
          - source_labels: [__meta_kubernetes_pod_label_app]
            regex: spark-master
            action: keep
          - source_labels: [__meta_kubernetes_pod_ip]
            target_label: __address__
            replacement: ${1}:8080
      
      # Spark Worker metrics
      - job_name: 'spark-worker'
        kubernetes_sd_configs:
          - role: pod
            namespaces:
              names:
                - gluten-prod
        relabel_configs:
          - source_labels: [__meta_kubernetes_pod_label_app]
            regex: spark-worker
            action: keep
          - source_labels: [__meta_kubernetes_pod_ip]
            target_label: __address__
            replacement: ${1}:8081
      
      # Gluten metrics
      - job_name: 'gluten'
        static_configs:
          - targets: ['prometheus-pushgateway:9091']
    
    alerting:
      alertmanagers:
        - static_configs:
            - targets:
                - alertmanager:9093
    
    rule_files:
      - '/etc/prometheus/rules/*.yml'
```

### 20.4.2 告警规则

```yaml
# alert-rules.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-rules
  namespace: gluten-prod
data:
  gluten_alerts.yml: |
    groups:
      - name: gluten_alerts
        interval: 30s
        rules:
          # Worker 节点不可用
          - alert: SparkWorkerDown
            expr: up{job="spark-worker"} == 0
            for: 2m
            labels:
              severity: critical
            annotations:
              summary: "Spark Worker {{ $labels.instance }} is down"
              description: "Worker node has been down for more than 2 minutes"
          
          # 内存使用率过高
          - alert: HighMemoryUsage
            expr: (spark_executor_memory_used / spark_executor_memory_total) > 0.9
            for: 5m
            labels:
              severity: warning
            annotations:
              summary: "High memory usage on {{ $labels.instance }}"
              description: "Memory usage is {{ $value | humanizePercentage }} on {{ $labels.instance }}"
          
          # 任务失败率高
          - alert: HighTaskFailureRate
            expr: rate(spark_executor_failed_tasks[5m]) > 0.1
            for: 5m
            labels:
              severity: warning
            annotations:
              summary: "High task failure rate"
              description: "Task failure rate is {{ $value }} tasks/sec"
          
          # Fallback 过多
          - alert: HighFallbackRate
            expr: (rate(gluten_fallback_count[5m]) / rate(gluten_total_operators[5m])) > 0.3
            for: 10m
            labels:
              severity: warning
            annotations:
              summary: "High Gluten fallback rate"
              description: "Fallback rate is {{ $value | humanizePercentage }}, check operator compatibility"
          
          # Shuffle 写入慢
          - alert: SlowShuffleWrite
            expr: rate(spark_shuffle_write_bytes[1m]) < 10485760  # <10MB/s
            for: 5m
            labels:
              severity: info
            annotations:
              summary: "Slow shuffle write performance"
              description: "Shuffle write speed is {{ $value | humanize }}B/s"
```

### 20.4.3 Grafana Dashboard

```json
{
  "dashboard": {
    "title": "Gluten Production Monitoring",
    "panels": [
      {
        "title": "Cluster Status",
        "targets": [
          {
            "expr": "sum(up{job=\"spark-worker\"})",
            "legendFormat": "Active Workers"
          },
          {
            "expr": "sum(spark_worker_cores_total)",
            "legendFormat": "Total Cores"
          }
        ]
      },
      {
        "title": "Memory Usage",
        "targets": [
          {
            "expr": "sum(spark_executor_memory_used) / sum(spark_executor_memory_total)",
            "legendFormat": "Memory Utilization %"
          }
        ]
      },
      {
        "title": "Query Throughput",
        "targets": [
          {
            "expr": "rate(spark_sql_execution_total[5m])",
            "legendFormat": "Queries/sec"
          }
        ]
      },
      {
        "title": "Gluten Performance",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(gluten_query_duration_seconds_bucket[5m]))",
            "legendFormat": "P95 Query Latency"
          },
          {
            "expr": "rate(gluten_fallback_count[5m]) / rate(gluten_total_operators[5m])",
            "legendFormat": "Fallback Rate %"
          }
        ]
      }
    ]
  }
}
```

## 20.5 安全配置

### 20.5.1 认证和授权

```scala
// 配置 Spark 认证
spark.conf.set("spark.authenticate", "true")
spark.conf.set("spark.authenticate.secret", "your-secret-key")

// 配置 ACL
spark.conf.set("spark.acls.enable", "true")
spark.conf.set("spark.admin.acls", "admin,user1,user2")
spark.conf.set("spark.modify.acls", "user3,user4")
spark.conf.set("spark.ui.view.acls", "user5,user6")

// SSL/TLS 配置
spark.conf.set("spark.ssl.enabled", "true")
spark.conf.set("spark.ssl.keyStore", "/path/to/keystore.jks")
spark.conf.set("spark.ssl.keyStorePassword", "password")
spark.conf.set("spark.ssl.keyPassword", "password")
spark.conf.set("spark.ssl.trustStore", "/path/to/truststore.jks")
spark.conf.set("spark.ssl.trustStorePassword", "password")
```

### 20.5.2 网络隔离

```yaml
# network-policy.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: gluten-network-policy
  namespace: gluten-prod
spec:
  podSelector:
    matchLabels:
      app: spark-worker
  policyTypes:
    - Ingress
    - Egress
  ingress:
    # 只允许来自 Master 的连接
    - from:
        - podSelector:
            matchLabels:
              app: spark-master
      ports:
        - protocol: TCP
          port: 7078
    # 允许 Prometheus 抓取指标
    - from:
        - namespaceSelector:
            matchLabels:
              name: monitoring
      ports:
        - protocol: TCP
          port: 8081
  egress:
    # 允许访问 Master
    - to:
        - podSelector:
            matchLabels:
              app: spark-master
      ports:
        - protocol: TCP
          port: 7077
    # 允许访问存储
    - to:
        - podSelector:
            matchLabels:
              app: hdfs
      ports:
        - protocol: TCP
          port: 9000
```

### 20.5.3 Secret 管理

```yaml
# gluten-secrets.yaml
apiVersion: v1
kind: Secret
metadata:
  name: gluten-secrets
  namespace: gluten-prod
type: Opaque
stringData:
  spark-authenticate-secret: "your-strong-secret-here"
  s3-access-key: "AKIAIOSFODNN7EXAMPLE"
  s3-secret-key: "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
  hdfs-keytab: |
    <base64-encoded-keytab>

---
# 在 Pod 中使用 Secret
apiVersion: v1
kind: Pod
metadata:
  name: spark-executor
spec:
  containers:
    - name: executor
      image: spark:3.3.1-gluten-1.2.0
      env:
        - name: SPARK_AUTHENTICATE_SECRET
          valueFrom:
            secretKeyRef:
              name: gluten-secrets
              key: spark-authenticate-secret
        - name: AWS_ACCESS_KEY_ID
          valueFrom:
            secretKeyRef:
              name: gluten-secrets
              key: s3-access-key
        - name: AWS_SECRET_ACCESS_KEY
          valueFrom:
            secretKeyRef:
              name: gluten-secrets
              key: s3-secret-key
```

## 20.6 升级和回滚

### 20.6.1 滚动升级策略

```yaml
# 使用 StatefulSet 的滚动升级
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: spark-master
spec:
  updateStrategy:
    type: RollingUpdate
    rollingUpdate:
      partition: 0  # 从最后一个 Pod 开始升级
  template:
    spec:
      containers:
        - name: spark-master
          image: spark:3.3.1-gluten-1.3.0  # 新版本
```

升级脚本：

```bash
#!/bin/bash
# upgrade-gluten.sh

set -e

NEW_VERSION="1.3.0"
NAMESPACE="gluten-prod"

echo "===> Backing up current configuration..."
kubectl get all -n $NAMESPACE -o yaml > backup-$(date +%Y%m%d).yaml

echo "===> Upgrading Gluten version to $NEW_VERSION..."

# 1. 升级 Master（一次一个）
for i in 0 1 2; do
  echo "Upgrading spark-master-$i..."
  kubectl set image statefulset/spark-master \
    -n $NAMESPACE \
    spark-master=spark:3.3.1-gluten-$NEW_VERSION
  
  kubectl rollout status statefulset/spark-master -n $NAMESPACE --timeout=5m
  
  # 等待 Master 稳定
  sleep 30
done

# 2. 升级 Workers（分批升级）
echo "Upgrading Spark Workers..."
kubectl set image daemonset/spark-worker \
  -n $NAMESPACE \
  spark-worker=spark:3.3.1-gluten-$NEW_VERSION

# 监控升级进度
kubectl rollout status daemonset/spark-worker -n $NAMESPACE --timeout=15m

echo "===> Upgrade complete!"
kubectl get pods -n $NAMESPACE
```

### 20.6.2 蓝绿部署

```bash
#!/bin/bash
# blue-green-deployment.sh

set -e

# 部署绿色环境
kubectl apply -f spark-master-deployment-green.yaml
kubectl apply -f spark-worker-deployment-green.yaml

# 等待绿色环境就绪
kubectl rollout status deployment/spark-master-green --timeout=10m
kubectl rollout status daemonset/spark-worker-green --timeout=10m

# 运行烟雾测试
./smoke-test.sh green

if [ $? -eq 0 ]; then
  echo "Smoke test passed. Switching traffic to green..."
  
  # 切换流量
  kubectl patch service spark-master-service \
    -p '{"spec":{"selector":{"version":"green"}}}'
  
  echo "Traffic switched to green. Monitoring for 5 minutes..."
  sleep 300
  
  # 检查是否有问题
  if ./health-check.sh; then
    echo "Green deployment successful. Removing blue..."
    kubectl delete deployment spark-master-blue
    kubectl delete daemonset spark-worker-blue
  else
    echo "Health check failed. Rolling back to blue..."
    kubectl patch service spark-master-service \
      -p '{"spec":{"selector":{"version":"blue"}}}'
  fi
else
  echo "Smoke test failed. Keeping blue deployment."
  kubectl delete deployment spark-master-green
  kubectl delete daemonset spark-worker-green
fi
```

### 20.6.3 快速回滚

```bash
#!/bin/bash
# rollback-gluten.sh

set -e

NAMESPACE="gluten-prod"

echo "===> Rolling back to previous version..."

# 回滚 Master
kubectl rollout undo statefulset/spark-master -n $NAMESPACE
kubectl rollout status statefulset/spark-master -n $NAMESPACE --timeout=5m

# 回滚 Workers
kubectl rollout undo daemonset/spark-worker -n $NAMESPACE
kubectl rollout status daemonset/spark-worker -n $NAMESPACE --timeout=10m

echo "===> Rollback complete!"
kubectl get pods -n $NAMESPACE
```

## 本章小结

本章介绍了 Gluten 生产环境部署的完整方案：

1. **架构设计**：整体架构、资源规划、网络规划
2. **Kubernetes 部署**：Master/Worker StatefulSet/DaemonSet 配置
3. **高可用**：ZooKeeper HA、健康检查、自动故障转移
4. **监控告警**：Prometheus + Grafana + AlertManager 完整监控体系
5. **安全配置**：认证授权、网络隔离、Secret 管理
6. **升级回滚**：滚动升级、蓝绿部署、快速回滚

通过本章学习，你应该能够：
- ✅ 设计生产级 Gluten 集群架构
- ✅ 在 Kubernetes 上部署 Gluten
- ✅ 配置高可用和容灾
- ✅ 搭建监控告警体系
- ✅ 实施安全最佳实践
- ✅ 安全地升级和回滚

下一章将分析真实案例，展示 Gluten 在不同场景下的应用。

## 参考资料

1. Spark on Kubernetes：https://spark.apache.org/docs/latest/running-on-kubernetes.html
2. Kubernetes Best Practices：https://kubernetes.io/docs/concepts/configuration/overview/
3. Prometheus Operator：https://github.com/prometheus-operator/prometheus-operator
4. Spark HA：https://spark.apache.org/docs/latest/spark-standalone.html#high-availability
5. ZooKeeper Admin：https://zookeeper.apache.org/doc/current/zookeeperAdmin.html
