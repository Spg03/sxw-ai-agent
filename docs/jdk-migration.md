# JDK 版本迁移记录

## 事件概述

- **时间**：2026-07-08
- **问题**：Dockerfile 中 JDK 版本被错误降级到 17，项目要求使用 JDK 21
- **结果**：已恢复为 JDK 21，并排查清理了错误的升级配置

## 变更历史

### 1. 错误降级（已修复）

Dockerfile 中构建阶段和运行阶段镜像被错误改为 JDK 17：

```dockerfile
# 错误状态
FROM maven:3.9-eclipse-temurin-17 AS build
FROM eclipse-temurin:17-jre
```

### 2. 恢复 JDK 21

将 Dockerfile 镜像恢复为 JDK 21：

```dockerfile
# 正确状态
FROM maven:3.9-eclipse-temurin-21 AS build
FROM eclipse-temurin:21-jre
```

### 3. 错误的升级尝试（已回退）

曾尝试升级到尚不存在的 JDK 版本，使用了实验性 GC 参数，已完全回退：

- Dockerfile 镜像改回 `temurin-21`
- GC 参数恢复为 `-XX:+UseZGC`（实验性 GC 已移除）
- pom.xml `java.version` 恢复为 `21`
- pom.xml 中 `--enable-preview` 编译参数已移除

## 当前正确配置

### Dockerfile

| 项目 | 值 |
|------|------|
| 构建镜像 | `maven:3.9-eclipse-temurin-21` |
| 运行镜像 | `eclipse-temurin:21-jre` |
| GC | `-XX:+UseZGC` |
| 内存限制 | `-XX:MaxRAMPercentage=75.0` |

### pom.xml

| 项目 | 值 |
|------|------|
| `java.version` | `21` |

## 经验教训

1. **JDK 版本变更需确认镜像可用性**：`eclipse-temurin` 镜像标签对应实际发布的 JDK 版本，升级到未发布的版本会导致构建失败
2. **GC 参数与 JDK 版本绑定**：ZGC 在 JDK 21 中已生产就绪；未正式发布的 GC 算法不应使用
3. **回退时需完整检查**：回退 JDK 版本时，需同步检查 GC 参数、编译参数（`--enable-preview`）等关联配置，避免残留
4. **本地编译环境与 Docker 构建环境独立**：本地 JDK 版本不影响 Docker 构建，Dockerfile 应始终以项目目标版本为准
