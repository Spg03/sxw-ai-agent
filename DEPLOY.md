# 部署说明

本文档用于本地演示、服务器部署和 Docker 启动。项目要求 JDK 21，所有密钥都应通过环境变量注入，不要写入代码仓库。

## 本地运行

```bash
cp .env.example .env
```

编辑 `.env`：

```bash
DASHSCOPE_API_KEY=sk-xxx
DASHSCOPE_CHAT_MODEL=qwen-plus
```

启动：

```bash
mvn spring-boot:run
```

访问：

- `http://localhost:8123/api/health`
- `http://localhost:8123/api/`
- `http://localhost:8123/api/doc.html`

## 生产 / 公网演示安全项

建议开启 API Key 保护：

```bash
SXW_SECURITY_API_KEY_ENABLED=true
SXW_SECURITY_API_KEY=<strong-random-value>
```

开启后，访问 `/api/ai/**`、`/api/notes/**`、`/api/skills/**`、`/api/agent/**` 时需要请求头：

```http
X-SXW-API-Key: <strong-random-value>
```

不要暴露真实的 DashScope、RAGFlow、Search API Key。公网环境建议同时限制防火墙、反向代理来源和日志脱敏策略。

## RAGFlow

如果使用本机 RAGFlow：

```bash
RAGFLOW_ENABLED=true
RAGFLOW_BASE_URL=http://localhost:9380
RAGFLOW_API_KEY=<ragflow-api-key>
RAGFLOW_DATASET_IDS=<dataset-id-1,dataset-id-2>
```

Docker 或远程部署时，将 `RAGFLOW_BASE_URL` 改为服务可访问地址。

## Docker Compose

```bash
docker compose --env-file .env up -d --build
```

查看日志：

```bash
docker compose logs -f sxw-ai-agent
```

停止：

```bash
docker compose down
```

## 验证

```bash
mvn test
mvn verify
```

如果出现“不支持发行版本 21”，说明 Maven 当前使用的不是 JDK 21。请检查：

```bash
java -version
mvn -version
```

## 运维端点

- 健康检查：`/api/actuator/health`
- Prometheus：`/api/actuator/prometheus`
- Manus trace：`/api/agent/traces/{chatId}?limit=5`
