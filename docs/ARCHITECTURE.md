# 架构与关键设计

## 1. 组件

| 组件 | 职责 |
|------|------|
| `auth/` | JWT 签发 / 解析、BCrypt 密码、Spring Security、注册登录 |
| `file/` | 上传落盘、Tika 解析、LangChain4j 分片、异步入库、文件列表 / 删除 / 下载 |
| `rag/` | 向量写入、按用户过滤的相似度检索、删除文件向量 |
| `chat/` | 会话、意图护栏、Prompt 组装、调用对话模型、引用持久化 |
| `config/` | `AppProperties`、LangChain4j Bean、异步线程池、管理员初始化 |
| `static/index.html` | Vue3 CDN 单页前端 |

## 2. 数据模型（MySQL）

```text
users
  id, username(unique), password_hash, display_name, role, created_at

knowledge_files
  id, owner_id, owner_name, visibility(PUBLIC/PRIVATE),
  original_name, stored_name, file_type, file_size, sha256,
  status(PENDING/PROCESSING/READY/FAILED), chunk_count, error_message,
  created_at, updated_at

chat_conversations
  id, user_id, title, created_at, updated_at

chat_messages
  id, conversation_id, role(USER/ASSISTANT), content,
  citations_json, created_at
```

文件本体保存在 `UPLOAD_DIR`（Docker 为 `/app/uploads` 命名卷），数据库只保存 UUID 文件名。

## 3. 向量元数据

每一条 Chroma 向量都带有：

```json
{
  "fileId": 12,
  "accessOwnerId": 0,
  "filename": "软件工程第二章.pdf",
  "visibility": "PUBLIC",
  "chunkIndex": 5
}
```

- 公共资料：`accessOwnerId = 0`
- 私有资料：`accessOwnerId = 上传者用户 ID`

检索过滤器：

```text
accessOwnerId in (0, currentUserId)
AND (fileIds 为空 OR fileId in fileIds)
```

因此即使用户伪造 `fileIds`，也无法召回他人私有资料；后端还会在检索前用 MySQL 再校验一次文件归属和 `READY` 状态。

## 4. 上传与解析

1. 校验扩展名、大小、空文件；学生强制 `PRIVATE`，管理员可选 `PUBLIC`。
2. 使用 SHA-256 去重（公共库全局去重，私有库按用户去重）。
3. 保存为 `UUID + 扩展名`，写 `knowledge_files`，状态 `PENDING`。
4. 立即返回需求文档指定的“文件已接收”文案。
5. `@Async("fileTaskExecutor")` 后台执行：
   - 状态改为 `PROCESSING`
   - `ApacheTikaDocumentParser` 提取文本；空文本 → 固定解析失败话术
   - `DocumentSplitters.recursive(500, 80)` 分片
   - 为每个 segment 写入 `chunkIndex`
   - 删除该文件旧向量（支持重建索引）
   - `OpenAiEmbeddingModel.embedAll` → `ChromaEmbeddingStore.addAll`
   - 成功：`READY` + `chunkCount`；失败：`FAILED` + `errorMessage`
6. 前端每 3 秒轮询进行中的文件。

## 5. 问答流程

```text
JWT 用户
  → 校验 conversationId 归属（防越权会话）
  → 保存用户消息
  → 意图护栏（作业 / 无关 / 寒暄）
  → 校验聚焦文件归属 + READY
  → 构造检索 query（追问时拼接上一轮问题）
  → Embedding + Chroma Top-K（accessOwnerId 过滤）
  → 无结果：固定话术，不调用 LLM
  → PromptBuilder：系统提示词 + 上下文 + 历史 + 当前任务
  → ChatModel.chat
  → 保存回答与 citations_json
  → 返回 answer + citations
```

## 6. Prompt 设计

系统提示词文件：`src/main/resources/prompts/system-prompt.txt`。

- 完整保留需求文档中的 RAG 优先、释义约束、权限隔离、防代写、无关拒答、固定话术、输出风格。
- 上下文以 `[1] 来源：《文件名》（第 N 段，相关度 0.83）` 的格式注入。
- 复习模式通过 `【本次任务】` 追加指令，实现总结 / 划重点 / 讲解 / 出题 / 整合。
- 资料中的指令被视为数据，防止提示注入。

## 7. 权限模型

| 操作 | 学生 | 管理员 |
|------|------|--------|
| 上传私有资料 | ✅ | ✅ |
| 上传公共资料 | ❌（强制私有） | ✅ |
| 查看 / 检索自己的私有资料 | ✅ | ✅ |
| 查看 / 检索他人私有资料 | ❌ | ❌（隐私设计） |
| 删除 / 重建他人私有资料 | ❌ | ✅（仅管理，不读正文） |
| 删除公共资料 | ❌ | ✅ |

## 8. 已知边界

- Tika 不做 OCR，扫描版 PDF 会返回固定失败提示。
- Chroma 版本固定 `0.5.15`，使用 API V1；若升级到 0.7+ 需切换 `ChromaApiVersion.V2` 并配置 tenant/database。
- 当前为单实例部署；`@Async` 队列在进程内，重启会丢失进行中的任务（状态可能停留在 PROCESSING，可手动重建索引）。
- `ddl-auto=update` 是初版便利配置，生产建议 Flyway。