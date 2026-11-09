# 部署说明

## 1. 依赖

- Docker Engine 24+ / Docker Compose v2
- 4GB+ 内存（云端 API 模式）；本地 Ollama 7B 建议 16GB+
- 可访问大模型 API 的网络（或本地 Ollama）

## 2. 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SERVER_PORT` | 8080 | 宿主机端口 |
| `JWT_SECRET` | 示例值 | JWT 签名密钥，生产必须修改为 32 位以上随机串 |
| `ADMIN_USERNAME` | admin | 首次启动管理员用户名 |
| `ADMIN_PASSWORD` | 示例值 | 首次启动管理员密码，务必修改 |
| `DB_PASSWORD` | campus123456 | MySQL 业务用户密码 |
| `MYSQL_ROOT_PASSWORD` | root123456 | MySQL root 密码 |
| `CHROMA_URL` | http://chroma:8000 | 容器内 Chroma 地址 |
| `VECTOR_STORE` | chroma | `chroma` / `memory`（测试用） |
| `LLM_BASE_URL` | 百炼兼容地址 | 对话模型 OpenAI 兼容地址 |
| `LLM_API_KEY` | 空 | 对话模型 Key |
| `LLM_MODEL` | qwen-plus | 对话模型名 |
| `EMBEDDING_BASE_URL` | 百炼兼容地址 | 向量模型地址 |
| `EMBEDDING_API_KEY` | 空（回退 LLM Key） | 向量模型 Key |
| `EMBEDDING_MODEL` | text-embedding-v3 | 向量模型名 |
| `EMBEDDING_DIM` | 1024 | 必须与模型输出维度一致 |
| `EMBEDDING_SEND_DIMENSIONS` | true | Ollama 等不支持的场景改为 false |
| `CHUNK_SIZE` / `CHUNK_OVERLAP` | 500 / 80 | 切片参数 |
| `TOP_K` / `MIN_SCORE` | 6 / 0.30 | 召回数量与阈值 |
| `UPLOAD_DIR` | ./uploads | 上传目录（Compose 覆盖为 /app/uploads） |

> Chrome 的向量维度与 `EMBEDDING_DIM` 强绑定。切换 Embedding 模型后，必须更换 `CHROMA_COLLECTION` 名称，或删除 Chroma volume 后重新入库，否则会报维度不一致。

## 3. 方案 A：Docker Compose（推荐）

```bash
cp .env.example .env
# 编辑 .env
docker compose up -d --build
docker compose ps
docker compose logs -f app
```

启动顺序：MySQL 健康 → Chroma → App。首次构建需要下载 Maven 依赖，时间较长。

访问 `http://服务器IP:8080`。

### 3.1 只启动依赖，后端在 IntelliJ 调试

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mysql chroma
```

- MySQL：`127.0.0.1:3306`（仅在开发覆盖文件中暴露）
- Chroma：`127.0.0.1:8000`
- 后端启动：IntelliJ 运行 `CampusKbApplication`，Active profiles 留空
- `.env` 中 `CHROMA_URL=http://localhost:8000`；`DB_PASSWORD` 与 Compose 的 `DB_PASSWORD` 保持一致

如果只执行 `docker compose up -d mysql chroma`，MySQL 不会映射到宿主机，IntelliJ 后端会报 `Connection refused`。
### 数据持久化

| Volume | 内容 |
|--------|------|
| `mysql_data` | 用户、文件元数据、会话 |
| `chroma_data` | 向量索引 |
| `upload_data` | 上传的原始文件 |

备份：

```bash
docker compose exec mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" campus_kb' > campus_kb.sql
docker run --rm -v campus-kb_chroma_data:/data -v "$PWD":/backup alpine tar czf /backup/chroma.tar.gz -C /data .
docker run --rm -v campus-kb_upload_data:/data -v "$PWD":/backup alpine tar czf /backup/uploads.tar.gz -C /data .
```

## 4. 方案 B：Ollama 本地模型

```bash
docker compose -f docker-compose.yml -f docker-compose.ollama.yml up -d --build
docker compose exec ollama ollama pull qwen2.5:7b
docker compose exec ollama ollama pull bge-m3
```

检查：

```bash
docker compose exec ollama ollama list
curl http://localhost:11434/v1/models
```

有 GPU 时，在 `docker-compose.ollama.yml` 的 `ollama` 服务下增加：

```yaml
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]
```

## 5. 方案 C：vLLM / 其他 OpenAI 兼容服务

只需保证 `/v1/chat/completions` 和 `/v1/embeddings` 可用，然后设置：

```env
LLM_BASE_URL=http://your-vllm:8000/v1
LLM_API_KEY=EMPTY
LLM_MODEL=Qwen/Qwen2.5-7B-Instruct
EMBEDDING_BASE_URL=http://your-embedding:8001/v1
EMBEDDING_API_KEY=EMPTY
EMBEDDING_MODEL=BAAI/bge-m3
EMBEDDING_DIM=1024
EMBEDDING_SEND_DIMENSIONS=false
```

## 6. 反向代理示例（Nginx）

```nginx
server {
    listen 80;
    server_name kb.example.edu.cn;
    client_max_body_size 60m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 180s;
    }
}
```

上传 50MB 的限制同时受 Nginx `client_max_body_size`、Spring `max-file-size` 和 `MAX_FILE_MB` 影响，三处需协调。

## 7. 常见问题

| 现象 | 排查 |
|------|------|
| 健康检查 `chatConfigured=false` | 没填 `LLM_API_KEY`，或 `.env` 没被 Compose 读取 |
| 问答 503「尚未配置对话模型」 | 同上；本地模型确认容器已启动 |
| 问答 503「知识库检索失败」 | Chroma 未启动、`CHROMA_URL` 错误，或 collection 维度不一致 |
| 上传一直 PROCESSING | 查看 `docker compose logs -f app`；Embedding 调用失败会写 FAILED |
| 上传后 FAILED，提示扫描件 | 当前不做 OCR，请上传可编辑 PDF / PPT / Word |
| 维度不一致 | 换 `CHROMA_COLLECTION` 名称或删除 Chroma volume 后重新入库 |
| 登录 401 | Token 过期（默认 7 天）或 `JWT_SECRET` 变更导致旧 Token 失效 |
| 页面样式全无 | CDN 不可达；将 Vue/Tailwind/Font Awesome 下载到本地静态目录 |