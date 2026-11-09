                                                                                                                                                                                                                                                                # API 说明

Base URL：`http://localhost:8080`。除注册、登录、健康检查外，都需要：

```http
Authorization: Bearer <登录接口返回的 token>
Content-Type: application/json
```

错误响应统一为：

```json
{ "success": false, "message": "具体错误信息" }
```

---

## 1. 认证

### 1.1 注册

`POST /api/auth/register`

```json
{ "username": "student01", "password": "student123", "displayName": "小矿同学" }
```

返回：

```json
{
  "token": "eyJhbGciOi...",
  "user": { "id": 2, "username": "student01", "displayName": "小矿同学", "role": "STUDENT" }
}
```

### 1.2 登录

`POST /api/auth/login`

```json
{ "username": "admin", "password": "change-me-admin-123456" }
```

### 1.3 当前用户

`GET /api/auth/me` → `UserDto`

### 1.4 修改密码

`POST /api/auth/change-password`

```json
{ "oldPassword": "admin123456", "newPassword": "new-strong-password" }
```

---

## 2. 文件

上传接口为 `multipart/form-data`：

```bash
curl -X POST http://localhost:8080/api/file/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./samples/图书馆使用指南.md"
```

管理员可以额外传 `visibility=public` 上传到公共知识库：

```bash
curl -X POST http://localhost:8080/api/file/upload \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -F "file=@./samples/图书馆使用指南.md" \
  -F "visibility=public"
```

学生即使传 `visibility=public` 也会被强制改为私有。

返回：

```json
{
  "success": true,
  "message": "✅ 文件已接收，正在解析并录入知识库，解析完成后你可以针对这份资料提问、划重点、梳理考点进行复习。",
  "file": {
    "id": 1, "ownerId": 1, "filename": "图书馆使用指南.md",
    "fileType": "md", "size": 2048, "status": "PENDING",
    "visibility": "PUBLIC", "chunkCount": 0,
    "ownerName": "系统管理员", "errorMessage": null,
    "createdAt": "2026-11-09T11:20:00"
  }
}
```

解析是异步的，前端每 3 秒轮询列表，状态会经历：

`PENDING` → `PROCESSING` → `READY` / `FAILED`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/file/myList` | 我上传的资料 |
| GET | `/api/file/publicList` | 公共知识库资料 |
| GET | `/api/file/accessibleList` | 公共 + 我的 |
| DELETE | `/api/file/{fileId}` | 删除文件、元数据、向量 |
| POST | `/api/file/{fileId}/reindex` | 重新解析入库 |
| GET | `/api/file/{fileId}/download` | 下载原始文件 |

---

## 3. 问答

`POST /api/chat/ask`

```json
{
  "userId": 2,
  "question": "国家励志奖学金需要什么条件？",
  "conversationId": null,
  "mode": "qa",
  "fileIds": null
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `userId` | 否 | 兼容前端约定；若传，必须等于 JWT 用户 ID |
| `question` | 是 | 1-4000 字 |
| `conversationId` | 否 | 多轮对话时传上一轮返回值；不传则新建会话 |
| `mode` | 否 | `qa` / `summary` / `keypoints` / `explain` / `quiz` / `integrate` |
| `fileIds` | 否 | 聚焦某几份资料；会校验归属与解析状态，最多 10 个 |

返回：

```json
{
  "conversationId": 8,
  "messageId": 17,
  "answer": "国家励志奖学金面向家庭经济困难且品学兼优的学生，基本条件包括……\n\n- **学习成绩**：…… [1]\n- **家庭经济困难**：…… [2]",
  "citations": [
    { "index": 1, "fileId": 3, "filename": "奖助学金申请指南.md",
      "chunkIndex": 4, "score": 0.83, "snippet": "国家励志奖学金用于奖励资助……" }
  ],
  "mode": "qa",
  "latencyMs": 2350
}
```

### 固定行为

- 无检索结果：直接返回
  `📖 当前知识库/该份资料中未查询到该相关内容，你可以核对资料，或者上传对应的学习资料后再提问。`
- 索要整套作业 / 论文 / 试卷答案：返回
  `📝 我可以为你讲解相关知识点、梳理解题思路，不能直接提供完整作业答案哦。`
- 游戏、情感、时政、闲聊等：返回
  `抱歉，我是校园知识库&学习复习助手，暂无法解答该类问题。`
- 寒暄：返回能力介绍，不调用模型。

---

## 4. 健康检查

`GET /api/health`（无需登录）

```json
{
  "status": "ok", "version": "0.2.0",
  "chatModel": "qwen-plus", "embeddingModel": "text-embedding-v3",
  "chatConfigured": true, "embeddingConfigured": true,
  "vectorStore": "chroma"
}
```