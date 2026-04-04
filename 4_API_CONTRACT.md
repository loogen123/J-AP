# J-AP (J-Architect-Pilot) 2026 — API 契约文档 (API Contract)

> **文档版本**: v1.0.0  
> **阶段**: 第四阶段 — API 契约生成与 Mocking  
> **日期**: 2026-04-04  
> **前置**: [3_PROTOTYPE.html](./3_PROTOTYPE.html) ✅ 已定稿  
> **状态**: 待实施

---

## 0. 推导来源与接口扫描

### 0.1 原型 HTML → 后端 API 映射

通过扫描 [3_PROTOTYPE.html](./3_PROTOTYPE.md) 的前端交互逻辑，提取以下后端调用模式：

| 前端操作 | 触发函数 | 对应后端 API | HTTP 方法 |
|---------|---------|-------------|----------|
| 点击 "Launch Agent Pipeline" | `startAgent()` | `/api/v1/tasks` | POST |
| 轮询任务状态 | `setInterval(pollStatus)` | `/api/v1/tasks/{id}/status` | GET |
| 获取事件流 | `readEventsSince()` | `/api/v1/tasks/{id}/events` | GET |
| 实时推送订阅 | STOMP subscribe | `/topic/tasks/{id}` | WS (STOMP) |
| 取消任务 | `cancel(taskId)` | `/api/v1/tasks/{id}/cancel` | POST |
| 获取文件内容 | `selectFile(path)` | `/api/v1/tasks/{id}/files/{path}` | GET |

### 0.2 架构文档 → Controller 结构映射

来自 [2_ARCHITECTURE.md](./2_ARCHITECTURE.md) §1.2 的 Controller 定义：

```
com.jap.api/
├── JapController.java           → POST /api/v1/tasks (任务提交)
├── TaskStatusController.java    → GET /api/v1/tasks/{id}/status (状态轮询)
├── TaskEventController.java     → GET /api/v1/tasks/{id}/events (事件流)
├── FilePreviewController.java   → GET /api/v1/tasks/{id}/files/** (文件读取)
└── TaskCancelController.java    → POST /api/v1/tasks/{id}/cancel (任务取消)
```

### 0.3 PRD §5 API 规格 → 响应格式基准

来自 [1_PRD.md](./1_PRD.md) §5 的响应格式定义，作为本契约的**最小兼容集**。

---

## 1. REST API 总览

### 1.1 端点矩阵

| Method | Path | 用途 | 认证 | 幂等性 |
|--------|------|------|------|-------|
| **POST** | `/api/v1/tasks` | 提交 Agent 任务 | Optional | No |
| **GET** | `/api/v1/tasks/{id}` | 获取任务完整状态 | Optional | Yes |
| **GET** | `/api/v1/tasks/{id}/status` | 轮询状态摘要 | Optional | Yes |
| **GET** | `/api/v1/tasks/{id}/logs` | 获取思考日志 | Optional | Yes |
| **GET** | `/api/v1/tasks/{id}/events` | 获取事件时间线 | Optional | Yes |
| **GET** | `/api/v1/tasks/{id}/files` | 获取生成文件列表 | Optional | Yes |
| **GET** | `/api/v1/tasks/{id}/files/{path:**} | 读取文件内容 | Optional | Yes |
| **POST** | `/api/v1/tasks/{id}/cancel` | 取消运行中的任务 | Optional | Idempotent |
| **GET** | `/api/v1/agents/stats` | Agent 池统计信息 | Optional | Yes |
| **GET** | `/api/v1/health` | 健康检查 | No | Yes |

### 1.2 URL 规范

```
Base URL: http://localhost:8080
API Prefix: /api/v1
Versioning: URL path versioning (v1)
Content-Type: application/json (所有请求/响应)
Charset: UTF-8
```

---

## 2. 核心 API 定义

### 2.1 POST /api/v1/tasks — 提交 Agent 任务

**触发原型行为**: 点击 "Launch Agent Pipeline" 按钮 (`startAgent()`)

#### Request

```http
POST /api/v1/tasks HTTP/1.1
Content-Type: application/json
Accept: application/json

{
  "requirement": "实现一个用户管理的 RESTful API，支持 CRUD 操作和分页查询",
  "options": {
    "packageName": "com.example.user",
    "databaseType": "MYSQL",
    "includeTests": true,
    "maxFixRetries": 3,
    "techStack": ["JDK_25", "SPRING_BOOT_34", "LANGCHAIN4J_10", "MYSQL_9"]
  },
  "faultSimulation": {
    "enabled": false,
    "faultType": null
  }
}
```

**Request DTO — Java 25 Record**:

```java
package com.jap.api.dto;

public record SubmitRequest(
    @NotBlank(message = "需求描述不能为空")
    @Size(max = 10000, message = "需求描述不能超过10000字符")
    String requirement,

    TaskOptions options,

    FaultSimulation faultSimulation
) {}

public record TaskOptions(
    @Pattern(regexp = "^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$", message = "包名格式不合法")
    String packageName,

    @Pattern(regexp = "^(MYSQL|H2|POSTGRESQL)$", message = "仅支持 MYSQL/H2/POSTGRESQL")
    String databaseType,

    boolean includeTests,

    @Min(1) @Max(10)
    Integer maxFixRetries,

    List<@Pattern(regexp = "^[A-Z_0-9]+$") String> techStack
) {
    public static final TaskOptions DEFAULTS = new TaskOptions(
        "com.example.generated", "MYSQL", true, 3,
        List.of("JDK_25", "SPRING_BOOT_34", "LANGCHAIN4J_10", "MYSQL_9")
    );
}

public record FaultSimulation(
    boolean enabled,
    @Pattern(regexp = "^E[0-9]{2}$", message = "错误类型格式: E01-E07")
    String faultType
) {}
```

#### Response — 202 Accepted

```http
HTTP/1.1 202 Accepted
Location: /api/v1/tasks/task-20260404-a1b2c3d4
Content-Type: application/json

{
  "taskId": "task-20260404-a1b2c3d4",
  "status": "INTENT_ANALYSIS",
  "createdAt": "2026-04-04T10:00:00.123Z",
  "_links": {
    "self": "/api/v1/tasks/task-20260404-a1b2c3d4",
    "status": "/api/v1/tasks/task-20260404-a1b2c3d4/status",
    "logs": "/api/v1/tasks/task-20260404-a1b2c3d4/logs",
    "events": "/api/v1/tasks/task-20260404-a1b2c3d4/events",
    "files": "/api/v1/tasks/task-20260404-a1b2c3d4/files",
    "ws": "/ws/tasks/task-20260404-a1b2c3d4"
  }
}
```

**Response DTO — Java 25 Record**:

```java
package com.jap.api.dto;

import java.time.Instant;
import java.util.Map;

public record TaskSubmittedResponse(
    String taskId,
    AgentStatus status,
    Instant createdAt,
    TaskLinks _links
) {}

public record TaskLinks(
    String self,
    String status,
    String logs,
    String events,
    String files,
    String ws
) {}
```

#### Error Responses

| HTTP Status | 场景 | Error Body |
|------------|------|-----------|
| **400 Bad Request** | requirement 为空 / options 校验失败 | `{ "code": "VALIDATION_ERROR", "message": "...", "field": "requirement", "details": [...] }` |
| **409 Conflict** | 并发限制达到上限 (maxConcurrentAgents) | `{ "code": "CONCURRENCY_LIMIT_REACHED", "message": "Agent pool exhausted, max=10 active tasks" }` |
| **500 Internal Server Error** | LLM 服务不可用 / Redis 连接失败 | `{ "code": "INTERNAL_ERROR", "message": "..." }` |

**Error DTO — Java 25 Record**:

```java
package com.jap.api.dto;

public record ErrorResponse(
    String code,
    String message,
    String field,
    Instant timestamp,
    String traceId,
    Object details
) {
    public static ErrorResponse validation(String field, String message) {
        return new ErrorResponse("VALIDATION_ERROR", message, field, 
            Instant.now(), null, null);
    }
    
    public static ErrorResponse conflict(String message) {
        return new ErrorResponse("CONCURRENCY_LIMIT_REACHED", message, null,
            Instant.now(), null, null);
    }
    
    public static ErrorResponse internal(String message, String traceId) {
        return new ErrorResponse("INTERNAL_ERROR", message, null,
            Instant.now(), traceId, null);
    }
}
```

---

### 2.2 GET /api/v1/tasks/{id}/status — 轮询任务状态

**触发原型行为**: `setInterval(pollStatus)` 更新状态机高亮 + 进度条 + 统计数字

#### Request

```http
GET /api/v1/tasks/task-20260404-a1b2c3d4/status HTTP/1.1
Accept: application/json
```

无请求体。

#### Response — 200 OK

```json
{
  "taskId": "task-20260404-a1b2c3d4",
  "status": "BUG_FIX",
  "currentStage": "SELF_HEALING",
  "progress": {
    "totalStages": 5,
    "completedStages": 3,
    "currentStageName": "缺陷修复",
    "percentage": 65
  },
  "healing": {
    "errorCategory": "E02",
    "currentRetry": 2,
    "maxRetries": 3,
    "lastError": "cannot find symbol: method finddByUid(java.lang.String)",
    "lastErrorFile": "src/main/java/com/example/service/UserService.java",
    "lastErrorLine": 42,
    "nextBackoffMs": 4000
  },
  "timeline": [
    { "stage": "INTENT_ANALYSIS", "status": "COMPLETED", "durationMs": 8500, "occurredAt": "2026-04-04T10:00:01.000Z" },
    { "stage": "CODE_GENERATION", "status": "COMPLETED", "durationMs": 12300, "occurredAt": "2026-04-04T10:00:09.500Z" },
    { "stage": "BUILD_TEST", "status": "FAILED", "durationMs": 45000, "error": "E02", "occurredAt": "2026-04-04T10:00:21.800Z" },
    { "stage": "BUG_FIX", "status": "IN_PROGRESS", "round": 1, "durationMs": 3000, "occurredAt": "2026-04-04T10:00:26.800Z" },
    { "stage": "BUG_FIX", "status": "IN_PROGRESS", "round": 2, "durationMs": 5500, "occurredAt": "2026-04-04T10:00:33.300Z" }
  ],
  "generatedFiles": [
    "src/main/java/com/example/entity/User.java",
    "src/main/java/com/example/repository/UserRepository.java",
    "src/main/java/com/example/service/UserService.java",
    "src/main/java/com/example/controller/UserController.java",
    "src/main/java/com/example/dto/UserDto.java",
    "src/main/resources/application.yml"
  ],
  "statistics": {
    "durationSeconds": 37,
    "llmTokenUsage": { "input": 2850, "output": 12400 },
    "mavenBuildCount": 3
  }
}
```

**Response DTO — Java 25 Record**:

```java
package com.jap.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record TaskStatusResponse(
    String taskId,
    AgentStatus status,
    String currentStage,
    ProgressInfo progress,
    Optional<HealingStatus> healing,
    List<TimelineEntry> timeline,
    List<String> generatedFiles,
    Optional<TaskStatistics> statistics
) {}

public record ProgressInfo(
    int totalStages,
    int completedStages,
    String currentStageName,
    int percentage
) {}

public record HealingStatus(
    String errorCategory,
    int currentRetry,
    int maxRetries,
    String lastError,
    String lastErrorFile,
    Integer lastErrorLine,
    long nextBackoffMs
) {}

public record TimelineEntry(
    String stage,
    TimelineStatus status,
    long durationMs,
    Instant occurredAt,
    Optional<Integer> round,
    Optional<String> error
) {
    public enum TimelineStatus {
        STARTED, COMPLETED, FAILED, IN_PROGRESS, SKIPPED
    }
}

public record TaskStatistics(
    long durationSeconds,
    TokenUsage llmTokenUsage,
    int mavenBuildCount
) {}

public record TokenUsage(int input, int output) {}
```

#### 特殊状态响应差异

| status 字段值 | 额外字段 | 说明 |
|---------------|---------|------|
| `COMPLETE` | `result`, `finalStatistics` | 包含最终结果和完整统计 |
| `MANUAL_INTERVENTION` | `contextUrl`, `humanReadableSummary` | 包含人工介入所需上下文 |
| `CANCELLED` | `cancelledAt`, `cancelledBy`, `reason` | 取消原因 |
| `FAILED` | `failureReason`, `exceptionType`, `stackTrace` | 未预期异常 |

**COMPLETE 扩展响应**:
```json
{
  "taskId": "task-xxx",
  "status": "COMPLETE",
  "currentStage": null,
  "progress": { "totalStages": 5, "completedStages": 5, "currentStageName": null, "percentage": 100 },
  "healing": null,
  "timeline": [ ... ],
  "generatedFiles": [ ... ],
  "statistics": { ... },
  "result": {
    "outcome": "HEALED",
    "totalHealingRounds": 2,
    "filesGenerated": 6,
    "testsPassed": 5,
    "testsFailed": 0,
    "buildTimeMs": 3891
  },
  "finalStatistics": {
    "totalDurationMs": 45230,
    "llmTotalTokens": { "input": 8500, "output": 42000 },
    "mavenExecutions": 5,
    "validationPasses": 12,
    "securityViolationsBlocked": 0
  }
}
```

---

### 2.3 GET /api/v1/tasks/{id}/logs — 获取思考日志

**触发原型行为**: 中间面板 Thought Logs 区域的实时日志流

#### Request

```http
GET /api/v1/tasks/task-20260404-a1b2c3d4/logs?offset=0&limit=50&type=ALL HTTP/1.1
Accept: application/json
```

**Query Parameters**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `offset` | int | 否 | 0 | 分页偏移（从第几条开始） |
| `limit` | int | 否 | 100 | 每次返回条数上限 |
| `type` | string | 否 | ALL | 过滤类型: ALL / SYSTEM / INNER / OUTER / SUCCESS / ERROR / WARN / FIX |

#### Response — 200 OK

```json
{
  "taskId": "task-20260404-a1b2c3d4",
  "totalCount": 47,
  "offset": 0,
  "limit": 50,
  "hasMore": false,
  "logs": [
    {
      "id": "log-001",
      "type": "SYSTEM",
      "stage": null,
      "timestamp": "2026-04-04T10:00:00.123Z",
      "title": "Agent Pipeline launched",
      "message": "Agent Pipeline launched for task: task-xxx",
      "details": null,
      "metadata": {}
    },
    {
      "id": "log-002",
      "type": "INNER",
      "stage": "INTENT_ANALYSIS",
      "timestamp": "2026-04-04T10:00:01.234Z",
      "title": "Sending requirement to AnalysisAiService",
      "message": "Sending requirement to AnalysisAiService with JSON Schema constraint...",
      "details": null,
      "metadata": { "llmModel": "gpt-4o", "temperature": 0.3 }
    },
    {
      "id": "log-003",
      "type": "ERROR",
      "stage": "INTENT_ANALYSIS",
      "timestamp": "2026-04-04T10:00:02.567Z",
      "title": "LLM returned malformed JSON",
      "message": "⚠ LLM returned malformed JSON (missing \"modules\" field)",
      "details": "{\"raw\": \"{\\\"module\\\": \\\"User CRUD\\\"}\"}",
      "metadata": { "errorCode": "E01", "retryCount": 0 }
    },
    {
      "id": "log-015",
      "type": "OUTER",
      "stage": "BUG_FIX",
      "timestamp": "2026-04-04T10:00:28.900Z",
      "title": "Self-Healing Round 1/3",
      "message": "━━━ Self-Healing Round 1/3 ━━━\nErrorClassifier mapping raw error → E02",
      "details": "ClassifiedError{category=E02, file=UserService.java, line=42, severity=NORMAL}",
      "metadata": { "round": 1, "errorCategory": "E02", "vectorSearchHits": 3 }
    },
    {
      "id": "log-040",
      "type": "SUCCESS",
      "stage": "BUILD_TEST",
      "timestamp": "2026-04-04T10:00:42.333Z",
      "title": "BUILD SUCCESS after healing!",
      "message": "✅ mvn clean test BUILD SUCCESS\nTests run: 5, Failures: 0",
      "details": "Build time: 3.891s",
      "metadata": { "buildResult": "SUCCESS", "healedInRound": 2 }
    }
  ]
}
```

**Log Entry DTO — Java 25 Record**:

```java
package com.jap.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public record LogPageResponse(
    String taskId,
    long totalCount,
    int offset,
    int limit,
    boolean hasMore,
    List<LogEntry> logs
) {}

public record LogEntry(
    String id,
    LogType type,
    Optional<String> stage,
    Instant timestamp,
    String title,
    String message,
    Optional<String> details,
    Map<String, Object> metadata
) {
    public enum LogType {
        SYSTEM, INFO, INNER, OUTER, SUCCESS, ERROR, WARN, FIX
    }
}
```

---

### 2.4 GET /api/v1/tasks/{id}/events — 获取事件时间线

**触发原型行为**: 底部 Mermaid 图的数据源、Timeline 组件的输入

#### Request

```http
GET /api/v1/tasks/task-20260404-a1b2c3d4/events?since=0-0-0-0-0-0-0000000000-00 HTTP/1.1
Accept: application/json
```

**Query Parameters**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `since` | string | 是 | Redis Stream ID，用于增量拉取。首次传 `"0-0"` |

#### Response — 200 OK

```json
{
  "taskId": "task-20260404-a1b2c3d4",
  "lastEventId": "1704388800000-0",
  "events": [
    {
      "eventId": "1704388800100-0",
      "type": "STAGE_CHANGED",
      "data": {
        "from": "IDLE",
        "to": "INTENT_ANALYSIS",
        "timestamp": "2026-04-04T10:00:00.100Z"
      }
    },
    {
      "eventId": "1704388800950-0",
      "type": "LOG_ADDED",
      "data": {
        "logId": "log-002",
        "logType": "INNER",
        "summary": "Sending requirement to AnalysisAiService"
      }
    },
    {
      "eventId": "1704388812300-0",
      "type": "FILE_GENERATED",
      "data": {
        "path": "src/main/java/com/example/entity/User.java",
        "size": 487
      }
    },
    {
      "eventId": "1704388834500-0",
      "type": "ERROR_DETECTED",
      "data": {
        "category": "E02",
        "file": "src/main/java/com/example/service/UserService.java",
        "line": 42,
        "message": "cannot find symbol: method finddByUid(String)"
      }
    },
    {
      "eventId": "1704388842800-0",
      "type": "FIX_ATTEMPTED",
      "data": {
        "round": 1,
        "fixApplied": true,
        "targetFile": "UserService.java",
        "errorCategory": "E02"
      }
    },
    {
      "eventId": "1704388889000-0",
      "type": "PIPELINE_COMPLETED",
      "data": {
        "status": "COMPLETE",
        "totalDurationMs": 45230,
        "outcome": "HEALED",
        "healingRounds": 2
      }
    }
  ]
}
```

**Task Event DTO — Java 25 Record**:

```java
package com.jap.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;

public record TaskEventPageResponse(
    String taskId,
    String lastEventId,
    List<TaskEvent> events
) {}

public sealed interface TaskEvent permits StageChangedEvent, LogAddedEvent, FileGeneratedEvent, 
    ErrorDetectedEvent, FixAttemptedEvent, ProgressUpdatedEvent, PipelineCompletedEvent, ManualInterventionRequiredEvent {

    String eventId();
    String type();
    Object data();
}

record StageChangedEvent(
    String eventId,
    String type,
    StageChangedData data
) implements TaskEvent {}

record StageChangedData(
    String from,
    String to,
    Instant timestamp
) {}

record LogAddedEvent(
    String eventId,
    String type,
    LogAddedData data
) implements TaskEvent {}

record LogAddedData(
    String logId,
    String logType,
    String summary
) {}

record FileGeneratedEvent(
    String eventId,
    String type,
    FileGeneratedData data
) implements TaskEvent {}

record FileGeneratedData(
    String path,
    long size
) {}

record ErrorDetectedEvent(
    String eventId,
    String type,
    ErrorDetectedData data
) implements TaskEvent {}

record ErrorDetectedData(
    String category,
    String file,
    Integer line,
    String message,
    Optional<String> suggestion
) {}

record FixAttemptedEvent(
    String eventId,
    String type,
    FixAttemptedData data
) implements TaskEvent {}

record FixAttemptedData(
    int round,
    boolean fixApplied,
    String targetFile,
    String errorCategory
) {}

record ProgressUpdatedEvent(
    String eventId,
    String type,
    ProgressUpdatedData data
) implements TaskEvent {}

record ProgressUpdatedData(
    int percentage,
    String stageName
) {}

record PipelineCompletedEvent(
    String eventId,
    String type,
    PipelineCompletedData data
) implements TaskEvent {}

record PipelineCompletedData(
    String status,
    long totalDurationMs,
    String outcome,
    int healingRounds
) {}

record ManualInterventionRequiredEvent(
    String eventId,
    String type,
    ManualInterventionData data
) implements TaskEvent {}

record ManualInterventionData(
    String reason,
    String contextUrl,
    String humanReadableSummary
) {}
```

---

### 2.5 GET /api/v1/tasks/{id}/files — 文件列表

**触发原型行为**: 右侧面板 File Tree 渲染

#### Request

```http
GET /api/v1/tasks/task-20260404-a1b2c3d4/files?path=src/main/java HTTP/1.1
Accept: application/json
```

**Query Parameters**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `path` | string | 否 | 根路径 | 返回指定子目录下的文件列表。不传则返回根目录 |

#### Response — 200 OK

```json
{
  "taskId": "task-20260404-a1b2c3d4",
  "basePath": "generated-workspace",
  "files": [
    {
      "name": "entity",
      "type": "DIRECTORY",
      "relativePath": "src/main/java/com/example/entity",
      "children": [
        { "name": "User.java", "type": "FILE", "relativePath": "src/main/java/com/example/entity/User.java", "size": 487, "lastModified": "2026-04-04T10:00:15.000Z" },
        { "name": "Role.java", "type": "FILE", "relativePath": "src/main/java/com/example/entity/Role.java", "size": 312, "lastModified": "2026-04-04T10:00:16.200Z" }
      ]
    },
    {
      "name": "pom.xml",
      "type": "FILE",
      "relativePath": "pom.xml",
      "size": 2048,
      "lastModified": "2026-04-04T10:00:14.000Z"
    }
  ],
  "totalFiles": 6,
  "totalSizeBytes": 15420
}
```

**File Node DTO — Java 25 Record**:

```java
package com.jap.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record FileTreeResponse(
    String taskId,
    String basePath,
    List<FileNode> files,
    int totalFiles,
    long totalSizeBytes
) {}

public record FileNode(
    String name,
    FileType type,
    String relativePath,
    long size,
    Instant lastModified,
    Optional<List<FileNode>> children
) {
    public enum FileType { FILE, DIRECTORY }
}
```

---

### 2.6 GET /api/v1/tasks/{id}/files/{path:\*\*} — 文件内容读取

**触发原型行为**: 右侧代码预览窗口渲染

#### Request

```http
GET /api/v1/tasks/task-20260404-a1b2c3d4/files/src/main/java/com/example/service/UserService.java HTTP/1.1
Accept: text/plain
```

#### Response — 200 OK

```
Content-Type: text/plain; charset=UTF-8
X-File-Path: src/main/java/com/example/service/UserService.java
X-File-Size: 892
X-SHA256: a1b2c3d4e5f6...

package com.example.service;

import com.example.dto.UserDto;
import com.example.entity.User;
import com.example.repository.UserRepository;
import lombok.RequiredArgsConstructor;
// ... (完整文件内容)
```

**注意**: 此端点返回原始文本（非 JSON），供前端代码高亮组件直接使用。

---

### 2.7 POST /api/v1/tasks/{id}/cancel — 取消任务

**触发原型行为**: 用户手动取消或系统超时取消

#### Request

```http
POST /api/v1/tasks/task-20260404-a1b2c3d4/cancel HTTP/1.1
Content-Type: application/json

{
  "reason": "User cancelled",
  "force": true
}
```

#### Response — 200 OK

```json
{
  "taskId": "task-20260404-a1b2c3d4",
  "previousStatus": "BUG_FIX",
  "newStatus": "CANCELLED",
  "cancelledAt": "2026-04-04T10:05:00.000Z",
  "gracefulShutdown": true,
  "processesKilled": ["mvn-process-12345"],
  "message": "Task cancelled successfully. Agent virtual thread interrupted."
}
```

---

## 3. WebSocket 契约 (STOMP)

### 3.1 连接配置

```
WebSocket Endpoint: /ws
Protocol: STOMP 1.2
Broker Prefix: /topic
Heartbeat: outgoing 15000ms, incoming 15000ms
```

### 3.2 订阅主题

客户端订阅: `SUBSCRIBE /topic/tasks/{taskId}`

### 3.3 推送消息格式

所有消息遵循统一信封结构:

```javascript
// STOMP MESSAGE frame headers:
destination: /topic/tasks/task-20260404-a1b2c3d4
content-type: application/json
```

#### 3.3.1 STAGE_CHANGED — 状态迁移

```json
{
  "type": "STAGE_CHANGED",
  "eventId": "evt-001",
  "timestamp": "2026-04-04T10:00:00.100Z",
  "data": {
    "from": "IDLE",
    "to": "INTENT_ANALYSIS",
    "transition": "StartAnalysis",
    "agentCount": 1,
    "activeAgents": 1
  }
}
```

**原型对应**: 状态机 SVG 节点颜色切换 + 箭头动画激活

#### 3.3.2 LOG_ADDED — 新增日志

```json
{
  "type": "LOG_ADDED",
  "eventId": "evt-002",
  "timestamp": "2026-04-04T10:00:01.234Z",
  "data": {
    "logId": "log-002",
    "logType": "INNER",
    "stage": "INTENT_ANALYSIS",
    "title": "Sending requirement to AnalysisAiService",
    "summary": "Sending requirement with JSON Schema constraint",
    "color": "#3b82f6"
  }
}
```

**原型对应**: 中间面板新增一条带颜色编码的日志条目

**Log Type ↔ Color 映射表**:

| logType | color (CSS) | 原型 class |
|---------|-------------|-----------|
| `SYSTEM` | `#64748b` | text-[--text-muted] |
| `INFO` | `#3b82f6` | dot: blue |
| `INNER` | `#60a5fa` | dot: light-blue (内环) |
| `OUTER` | `#f59e0b` | dot: orange (外环) |
| `SUCCESS` | `#10b981` | dot: green |
| `ERROR` | `#ef4444` | dot: red |
| `WARN` | `#f59e0b` | dot: yellow |
| `FIX` | `#8b5cf6` | dot: purple |

#### 3.3.3 PROGRESS_UPDATED — 进度更新

```json
{
  "type": "PROGRESS_UPDATED",
  "eventId": "evt-003",
  "timestamp": "2026-04-04T10:00:18.500Z",
  "data": {
    "percentage": 35,
    "stageName": "CODE_GENERATION",
    "detail": "Generating UserService.java"
  }
}
```

**原型对应**: 右侧进度环形图旋转 + 百分比数字更新

#### 3.3.4 FILE_GENERATED — 文件生成

```json
{
  "type": "FILE_GENERATED",
  "eventId": "evt-004",
  "timestamp": "2026-04-04T10:00:15.000Z",
  "data": {
    "path": "src/main/java/com/example/entity/User.java",
    "size": 487,
    "action": "CREATED",
    "language": "java"
  }
}
```

**原型对应**: 右侧文件树新增一个节点

#### 3.3.5 ERROR_DETECTED — 错误检测

```json
{
  "type": "ERROR_DETECTED",
  "eventId": "evt-005",
  "timestamp": "2026-04-04T10:00:22.800Z",
  "data": {
    "category": "E02",
    "severity": "NORMAL",
    "file": "src/main/java/com/example/service/UserService.java",
    "line": 42,
    "message": "cannot find symbol: method finddByUid(java.lang.String)",
    "suggestion": "Did you mean: findByUid()?",
    "sourceSnippet": "User entity = userRepository.finddByUid(id.toString());"
  }
}
```

**原型对应**: 日志区出现红色错误条目 + 状态机跳转到 BUG_FIX (红色发光)

#### 3.3.6 FIX_ATTEMPTED — 修复尝试

```json
{
  "type": "FIX_ATTEMPTED",
  "eventId": "evt-006",
  "timestamp": "2026-04-04T10:00:29.000Z",
  "data": {
    "round": 1,
    "errorCategory": "E02",
    "targetFile": "src/main/java/com/example/service/UserService.java",
    "fixApplied": true,
    "llmModel": "gpt-4o",
    "llmTemperature": 0.1,
    "patchType": "FULL_REPLACE",
    "backoffBeforeNextMs": 1000
  }
}
```

**原型对应**: 日志区紫色 FIX 条目 + 打字指示器动画

#### 3.3.7 PIPELINE_COMPLETED — 闭环完成

```json
{
  "type": "PIPELINE_COMPLETED",
  "eventId": "evt-007",
  "timestamp": "2026-04-04T10:00:42.333Z",
  "data": {
    "status": "COMPLETE",
    "outcome": "HEALED",
    "totalDurationMs": 45230,
    "healingRounds": 2,
    "filesGenerated": 6,
    "testsPassed": 5,
    "testsFailed": 0,
    "buildTimeFinalMs": 3891,
    "summary": "🎉 Self-Healing succeeded in 2 rounds for E02 compile error"
  }
}
```

**原型对应**: 状态机变为绿色 COMPLETE + 全局状态栏显示 PIPELINE COMPLETE + 弹出成功提示

#### 3.3.8 MANUAL_INTERVENTION_REQUIRED — 需人工介入

```json
{
  "type": "MANUAL_INTERVENTION_REQUIRED",
  "eventId": "evt-008",
  "timestamp": "2026-04-04T10:01:15.000Z",
  "data": {
    "reason": "retry_exhausted",
    "contextUrl": "/api/v1/tasks/task-xxx/context",
    "humanReadableSummary": "Agent failed to fix E02 error after 3 rounds of self-healing.\nLast error: cannot find symbol at line 42.\nPlease review healing records and manually fix.",
    "preservedContext": {
      "originalRequirement": true,
      "allGeneratedFiles": true,
      "healingHistory": true,
      "errorReports": true,
      "vectorSearchResults": true
    }
  }
}
```

**原型对应**: 状态机变为粉色 MANUAL_INTERVENTION

### 3.4 STOMP Message 信封 DTO — Java 25 Record

```java
package com.jap.api.ws;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public sealed interface StompMessage
    permits StompStageChanged, StompLogAdded, StompProgressUpdated,
            StompFileGenerated, StompErrorDetected, StompFixAttempted,
            StompPipelineCompleted, StompManualInterventionRequired {

    String type();
    String eventId();
    Instant timestamp();
    Object data();
}

record StompStageChanged(
    String type, String eventId, Instant timestamp,
    StageChangedPayload data
) implements StompMessage {}

record StageChangedPayload(
    String from, String to, String transition,
    int agentCount, int activeAgents
) {}

record StompLogAdded(
    String type, String eventId, Instant timestamp,
    LogAddedPayload data
) implements StompMessage {}

record LogAddedPayload(
    String logId, String logType, Optional<String> stage,
    String title, String summary, String color
) {}

record StompProgressUpdated(
    String type, String eventId, Instant timestamp,
    ProgressPayload data
) implements StompMessage {}

record ProgressPayload(int percentage, String stageName, String detail) {}

record StompFileGenerated(
    String type, String eventId, Instant timestamp,
    FilePayload data
) implements StompMessage {}

record FilePayload(String path, long size, String action, String language) {}

record StompErrorDetected(
    String type, String eventId, Instant timestamp,
    ErrorPayload data
) implements StompMessage {}

record ErrorPayload(
    String category, String severity, String file,
    Integer line, String message,
    Optional<String> suggestion, Optional<String> sourceSnippet
) {}

record StompFixAttempted(
    String type, String eventId, Instant timestamp,
    FixPayload data
) implements StompMessage {}

record FixPayload(
    int round, String errorCategory, String targetFile,
    boolean fixApplied, String llmModel,
    double llmTemperature, String patchType,
    long backoffBeforeNextMs
) {}

record StompPipelineCompleted(
    String type, String eventId, Instant timestamp,
    CompletedPayload data
) implements StompMessage {}

record CompletedPayload(
    String status, String outcome, long totalDurationMs,
    int healingRounds, int filesGenerated,
    int testsPassed, int testsFailed,
    long buildTimeFinalMs, String summary
) {}

record StompManualInterventionRequired(
    String type, String eventId, Instant timestamp,
    InterventionPayload data
) implements StompMessage {}

record InterventionPayload(
    String reason, String contextUrl, String humanReadableSummary,
    PreservedContext preservedContext
) {}

record PreservedContext(
    boolean originalRequirement,
    boolean allGeneratedFiles,
    boolean healingHistory,
    boolean errorReports,
    boolean vectorSearchResults
) {}
```

---

## 4. 枚举与常量定义

### 4.1 AgentStatus 枚举

```java
package com.jap.core.state;

public enum AgentStatus {
    IDLE,
    INTENT_ANALYSIS,
    CODE_GENERATION,
    BUILD_TEST,
    BUG_FIX,
    COMPLETE,
    MANUAL_INTERVENTION,
    FAILED,
    CANCELLED
}
```

### 4.2 ErrorCategory 枚举

```java
package com.jap.healing;

public enum ErrorCategory {
    FORMAT_ERROR("E01", "结构化输出格式错误"),
    COMPILE_ERROR("E02", "编译错误"),
    COMPLIANCE_ERROR("E03", "规范违规"),
    HALLUCINATION("E04", "幻觉内容"),
    SECURITY_VIOLATION("E05", "安全域越权"),
    TEST_FAILURE("E06", "测试失败"),
    DESIGN_DEFECT("E07", "设计缺陷");

    private final String code;
    private final String description;
}
```

### 4.3 PipelineStage 枚举

```java
package com.jap.core.pipeline;

public enum PipelineStage {
    INTENT_ANALYSIS,
    CODE_GENERATION,
    BUILD_TEST,
    BUG_FIX
}
```

### 4.4 HTTP Status Code 使用规范

| 状态码 | 使用场景 | 说明 |
|--------|---------|------|
| **202 Accepted** | POST /tasks | 任务已接收，异步执行中 |
| **200 OK** | 所有 GET 请求 | 成功返回数据 |
| **400 Bad Request** | 参数校验失败 | 返回 ErrorResponse + 具体字段 |
| **404 Not Found** | taskId 不存在 | 返回 ErrorResponse |
| **409 Conflict** | 并发限制 | Agent 池满 |
| **500 Internal Server Error** | 未预期异常 | 返回 ErrorResponse + traceId |

---

## 5. Mock 数据包：完整闭环场景 (E02 → BUG_FIX → COMPLETE)

以下 Mock 数据模拟了一个**完整的故障注入→自愈闭环**过程，可用于前端开发联调和自动化测试。

### 5.1 Mock Scenario: E02 Compile Error → Self-Healing Success

#### Step 1: 提交任务

```bash
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "requirement": "实现一个用户管理的 RESTful API，支持 CRUD 和分页查询",
    "options": {
      "packageName": "com.example.user",
      "databaseType": "MYSQL",
      "includeTests": true,
      "maxFixRetries": 3,
      "techStack": ["JDK_25", "SPRING_BOOT_34", "LANGCHAIN4J_10", "MYSQL_9"]
    },
    "faultSimulation": {
      "enabled": true,
      "faultType": "E02"
    }
  }'
```

**Mock Response (202)**:
```json
{
  "taskId": "task-mock-e02-heal-001",
  "status": "INTENT_ANALYSIS",
  "createdAt": "2026-04-04T10:00:00.000Z",
  "_links": {
    "self": "/api/v1/tasks/task-mock-e02-heal-001",
    "status": "/api/v1/tasks/task-mock-e02-heal-001/status",
    "logs": "/api/v1/tasks/task-mock-e02-heal-001/logs",
    "events": "/api/v1/tasks/task-mock-e02-heal-001/events",
    "files": "/api/v1/tasks/task-mock-e02-heal-001/files",
    "ws": "/ws/tasks/task-mock-e02-heal-001"
  }
}
```

#### Step 2: WebSocket 事件流 (完整序列)

```json
[
  {"type":"STAGE_CHANGED","eventId":"evt-m01","timestamp":"2026-04-04T10:00:00.100Z","data":{"from":"IDLE","to":"INTENT_ANALYSIS","transition":"StartAnalysis","agentCount":1,"activeAgents":1}},
  {"type":"LOG_ADDED","eventId":"evt-m02","timestamp":"2026-04-04T10:00:00.200Z","data":{"logId":"log-m01","logType":"SYSTEM","stage":null,"title":"Agent Pipeline launched","summary":"task-mock-e02-heal-001","color":"#64748b"}},
  {"type":"LOG_ADDED","eventId":"evt-m03","timestamp":"2026-04-04T10:00:00.500Z","data":{"logId":"log-m02","logType":"INFO","stage":"INTENT_ANALYSIS","title":"▶ INTENT_ANALYSIS started","summary":"Analyzing requirement...","color":"#3b82f6"}},
  {"type":"LOG_ADDED","eventId":"evt-m04","timestamp":"2026-04-04T10:00:01.000Z","data":{"logId":"log-m03","logType":"INNER","stage":"INTENT_ANALYSIS","title":"Sending to AnalysisAiService","summary":"JSON Schema constraint applied","color":"#60a5fa"}},
  {"type":"PROGRESS_UPDATED","eventId":"evt-m05","timestamp":"2026-04-04T10:00:01.200Z","data":{"percentage":5,"stageName":"Analyzing requirement...","detail":null}},
  {"type":"LOG_ADDED","eventId":"evt-m06","timestamp":"2026-04-04T10:00:02.000Z","data":{"logId":"log-m04","logType":"INNER","stage":"INTENT_ANALYSIS","title":"✓ RequirementSpec parsed successfully","summary":"5 modules resolved","color":"#10b981"}},
  {"type":"STAGE_CHANGED","eventId":"evt-m07","timestamp":"2026-04-04T10:00:02.500Z","data":{"from":"INTENT_ANALYSIS","to":"CODE_GENERATION","transition":"AnalysisComplete","agentCount":1,"activeAgents":1}},
  {"type":"PROGRESS_UPDATED","eventId":"evt-m08","timestamp":"2026-04-04T10:00:02.600Z","data":{"percentage":18,"stageName":"Intent analysis complete","detail":null}},
  {"type":"LOG_ADDED","eventId":"evt-m09","timestamp":"2026-04-04T10:00:03.000Z","data":{"logId":"log-m05","logType":"INFO","stage":"CODE_GENERATION","title":"▶ CODE_GENERATION started","summary":"Parallel module generation via Virtual Threads","color":"#3b82f6"}},
  {"type":"FILE_GENERATED","eventId":"evt-m10","timestamp":"2026-04-04T10:00:04.000Z","data":{"path":"src/main/java/com/example/entity/User.java","size":487,"action":"CREATED","language":"java"}},
  {"type":"FILE_GENERATED","eventId":"evt-m11","timestamp":"2026-04-04T10:00:04.800Z","data":{"path":"src/main/java/com/example/repository/UserRepository.java","size":312,"action":"CREATED","language":"java"}},
  {"type":"FILE_GENERATED","eventId":"evt-m12","timestamp":"2026-04-04T10:00:05.500Z","data":{"path":"src/main/java/com/example/service/UserService.java","size":892,"action":"CREATED","language":"java"}},
  {"type":"FILE_GENERATED","eventId":"evt-m13","timestamp":"2026-04-04T10:00:06.200Z","data":{"path":"src/main/java/com/example/controller/UserController.java","size":1024,"action":"CREATED","language":"java"}},
  {"type":"FILE_GENERATED","eventId":"evt-m14","timestamp":"2026-04-04T10:00:06.800Z","data":{"path":"src/main/java/com/example/dto/UserDto.java","size":256,"action":"CREATED","language":"java"}},
  {"type":"FILE_GENERATED","eventId":"evt-m15","timestamp":"2026-04-04T10:00:07.000Z","data":{"path":"src/main/resources/application.yml","size":512,"action":"CREATED","language":"yaml"}},
  {"type":"LOG_ADDED","eventId":"evt-m16","timestamp":"2026-04-04T10:00:08.000Z","data":{"logId":"log-m06","logType":"INFO","stage":"CODE_GENERATION","title":"All validations passed","summary":"Jakarta EE 11 compliant, no sandbox violations","color":"#10b981"}},
  {"type":"STAGE_CHANGED","eventId":"evt-m17","timestamp":"2026-04-04T10:00:08.500Z","data":{"from":"CODE_GENERATION","to":"BUILD_TEST","transition":"GenerationComplete","agentCount":1,"activeAgents":1}},
  {"type":"PROGRESS_UPDATED","eventId":"evt-m18","timestamp":"2026-04-04T10:00:08.600Z","data":{"percentage":72,"stageName":"Code generation complete","detail":null}},
  {"type":"LOG_ADDED","eventId":"evt-m19","timestamp":"2026-04-04T10:00:09.000Z","data":{"logId":"log-m07","logType":"INFO","stage":"BUILD_TEST","title":"▶ BUILD_TEST started","summary":"SecureMavenExecutor in isolated process","color":"#3b82f6"}},
  {"type":"LOG_ADDED","eventId":"evt-m20","timestamp":"2026-04-04T10:00:09.100Z","data":{"logId":"log-m08","logType":"INFO","stage":"BUILD_TEST","title":"Process sandbox active","summary":"JVM args locked, MAVEN_OPTS cleared","color":"#3b82f6"}},

  {"type":"ERROR_DETECTED","eventId":"evt-m21","timestamp":"2026-04-04T10:00:22.800Z","data":{
    "category":"E02","severity":"NORMAL",
    "file":"src/main/java/com/example/service/UserService.java",
    "line":42,
    "message":"cannot find symbol: method finddByUid(java.lang.String)",
    "suggestion":"Did you mean: findByUid()?",
    "sourceSnippet":"User entity = userRepository.finddByUid(id.toString());"
  }},
  {"type":"STAGE_CHANGED","eventId":"evt-m22","timestamp":"2026-04-04T10:00:23.000Z","data":{"from":"BUILD_TEST","to":"BUG_FIX","transition":"BuildFailed","agentCount":1,"activeAgents":1}},
  {"type":"LOG_ADDED","eventId":"evt-m23","timestamp":"2026-04-04T10:00:23.200Z","data":{"logId":"log-m09","logType":"OUTER","stage":"BUG_FIX","title":"━━━ Self-Healing Round 1/3 ━━━","summary":"ErrorClassifier → E02 at line 42","color":"#f59e0b"}},
  {"type":"LOG_ADDED","eventId":"evt-m24","timestamp":"2026-04-04T10:00:24.000Z","data":{"logId":"log-m10","logType":"OUTER","stage":"BUG_FIX","title":"Context collected (parallel)","summary":"Source window + APIs + Vector Search(3 hits)","color":"#f59e0b"}},
  {"type":"LOG_ADDED","eventId":"evt-m25","timestamp":"2026-04-04T10:00:24.800Z","data":{"logId":"log-m11","logType":"OUTER","stage":"BUG_FIX","title":"FixPrompt ready","summary":"target=finddByUid → should be findById+orElseThrow","color":"#f59e0b"}},
  {"type":"FIX_ATTEMPTED","eventId":"evt-m26","timestamp":"2026-04-04T10:00:26.000Z","data":{
    "round":1,"errorCategory":"E02","targetFile":"UserService.java",
    "fixApplied":true,"llmModel":"gpt-4o","llmTemperature":0.1,
    "patchType":"FULL_REPLACE","backoffBeforeNextMs":1000
  }},
  {"type":"LOG_ADDED","eventId":"evt-m27","timestamp":"2026-04-04T10:00:26.500Z","data":{"logId":"log-m12","logType":"FIX","stage":"BUG_FIX","title":"✓ Patch applied: line 42 fixed","summary":"finddByUid → findById + orElseThrow","color":"#8b5cf6"}},
  {"type":"PROGRESS_UPDATED","eventId":"evt-m28","timestamp":"2026-04-04T10:00:27.000Z","data":{"percentage":78,"stageName":"Rebuilding (Round 1)...","detail":null}},

  {"type":"ERROR_DETECTED","eventId":"evt-m29","timestamp":"2026-04-04T10:00:31.000Z","data":{
    "category":"E02","severity":"NORMAL",
    "file":"UserController.java","line":28,
    "message":"variable userDto might not have been initialized",
    "suggestion":"Initialize before conditional block",
    "sourceSnippet":"UserDto userDto;\nif (isValid) { return userDto; }"
  }},
  {"type":"LOG_ADDED","eventId":"evt-m30","timestamp":"2026-04-04T10:00:31.500Z","data":{"logId":"log-m13","logType":"OUTER","stage":"BUG_FIX","title":"Still failing after round 1","summary":"New error introduced during fix","color":"#ef4444"}},
  {"type":"LOG_ADDED","eventId":"evt-m31","timestamp":"2026-04-04T10:00:32.200Z","data":{"logId":"log-m14","logType":"OUTER","stage":"BUG_FIX","title":"Backoff: 1s before next attempt","summary":"","color":"#f59e0b"}},

  {"type":"FIX_ATTEMPTED","eventId":"evt-m32","timestamp":"2026-04-04T10:00:34.000Z","data":{
    "round":2,"errorCategory":"E02","targetFile":"UserController.java",
    "fixApplied":true,"llmModel":"gpt-4o","llmTemperature":0.1,
    "patchType":"DIFF_PATCH","backoffBeforeNextMs":2000
  }},
  {"type":"LOG_ADDED","eventId":"evt-m33","timestamp":"2026-04-04T10:00:34.800Z","data":{"logId":"log-m15","logType":"FIX","stage":"BUG_FIX","title":"✓ Patch applied: UserController fixed","summary":"Added proper initialization","color":"#8b5cf6"}},
  {"type":"PROGRESS_UPDATED","eventId":"evt-m34","timestamp":"2026-04-04T10:00:36.000Z","data":{"percentage":85,"stageName":"Rebuilding (Round 2)...","detail":null}},

  {"type":"LOG_ADDED","eventId":"evt-m35","timestamp":"2026-04-04T10:00:39.500Z","data":{"logId":"log-m16","logType":"SUCCESS","stage":"BUILD_TEST","title":"✅ mvn clean test BUILD SUCCESS!","summary":"Tests run: 5, Failures: 0, Errors: 0\nBuild time: 3.891s","color":"#10b981"}},

  {"type":"STAGE_CHANGED","eventId":"evt-m36","timestamp":"2026-04-04T10:00:40.000Z","data":{"from":"BUG_FIX","to":"COMPLETE","transition":"FixSuccess","agentCount":1,"activeAgents":1}},
  {"type":"PROGRESS_UPDATED","eventId":"evt-m37","timestamp":"2026-04-04T10:00:40.100Z","data":{"percentage":100,"stageName":"Healed!","detail":null}},
  {"type":"PIPELINE_COMPLETED","eventId":"evt-m38","timestamp":"2026-04-04T10:00:40.333Z","data":{
    "status":"COMPLETE","outcome":"HEALED",
    "totalDurationMs":40333,"healingRounds":2,
    "filesGenerated":6,"testsPassed":5,"testsFailed":0,
    "buildTimeFinalMs":3891,"summary":"🎉 Self-Healing succeeded in 2 rounds for E02"
  }}
]
```

#### Step 3: 最终状态轮询 (GET /status)

```json
{
  "taskId": "task-mock-e02-heal-001",
  "status": "COMPLETE",
  "currentStage": null,
  "progress": {
    "totalStages": 5,
    "completedStages": 5,
    "currentStageName": null,
    "percentage": 100
  },
  "healing": null,
  "timeline": [
    {"stage":"INTENT_ANALYSIS","status":"COMPLETED","durationMs":2500,"occurredAt":"2026-04-04T10:00:00.000Z"},
    {"stage":"CODE_GENERATION","status":"COMPLETED","durationMs":6500,"occurredAt":"2026-04-04T10:00:02.500Z"},
    {"stage":"BUILD_TEST","status":"FAILED","durationMs":14800,"error":"E02","occurredAt":"2026-04-04T10:00:09.000Z"},
    {"stage":"BUG_FIX","status":"COMPLETED","durationMs":16533,"round":1,"occurredAt":"2026-04-04T10:00:23.000Z"}
  ],
  "generatedFiles": [
    "src/main/java/com/example/entity/User.java",
    "src/main/java/com/example/repository/UserRepository.java",
    "src/main/java/com/example/service/UserService.java",
    "src/main/java/com/example/controller/UserController.java",
    "src/main/java/com/example/dto/UserDto.java",
    "src/main/resources/application.yml"
  ],
  "statistics": {
    "durationSeconds": 40,
    "llmTokenUsage": {"input": 12500, "output": 48000},
    "mavenBuildCount": 3
  },
  "result": {
    "outcome": "HEALED",
    "totalHealingRounds": 2,
    "filesGenerated": 6,
    "testsPassed": 5,
    "testsFailed": 0,
    "buildTimeMs": 3891
  },
  "finalStatistics": {
    "totalDurationMs": 40333,
    "llmTotalTokens": {"input": 12500, "output": 48000},
    "mavenExecutions": 3,
    "validationPasses": 12,
    "securityViolationsBlocked": 0
  }
}
```

### 5.2 Mock Scenario: E05 Security Violation → CRITICAL Block

```json
[
  {"type":"ERROR_DETECTED","eventId":"evt-e05-01","timestamp":"2026-04-04T11:00:15.000Z","data":{
    "category":"E05","severity":"CRITICAL",
    "file":"SecurityTestService.java","line":18,
    "message":"Path traversal detected: ../etc/passwd",
    "suggestion":"Use relative paths within generated-workspace/",
    "sourceSnippet":"File f = new File(\"../etc/passwd\");"
  }},
  {"type":"LOG_ADDED","eventId":"evt-e05-02","timestamp":"2026-04-04T11:00:15.200Z","data":{
    "logId":"log-e05-1","logType":"ERROR","stage":"CODE_GENERATION",
    "title":"🔴 CRITICAL: Path traversal detected!",
    "summary":"PathValidator.toRealPath(NOFOLLOW_LINKS) blocked symlink attack",
    "color":"#ef4444"
  }},
  {"type":"LOG_ADDED","eventId":"evt-e05-03","timestamp":"2026-04-04T11:00:15.500Z","data":{
    "logId":"log-e05-2","logType":"WARN","stage":"CODE_GENERATION",
    "title":"↵ Forcing security fix — not counted against normal retry limit",
    "summary":"CRITICAL errors get separate handling",
    "color":"#f59e0b"
  }},
  {"type":"FIX_ATTEMPTED","eventId":"evt-e05-04","timestamp":"2026-04-04T11:00:16.000Z","data":{
    "round":1,"errorCategory":"E05","targetFile":"SecurityTestService.java",
    "fixApplied":true,"llmModel":"gpt-4o","llmTemperature":0.05,
    "patchType":"FULL_REPLACE","backoffBeforeNextMs":0
  }}
]
```

### 5.3 Mock Scenario: Exhausted → Manual Intervention

```json
{
  "taskId": "task-mock-exhausted-001",
  "status": "MANUAL_INTERVENTION",
  "currentStage": null,
  "progress": { "totalStages": 5, "completedStages": 4, "currentStageName": null, "percentage": 95 },
  "healing": {
    "errorCategory": "E06",
    "currentRetry": 3,
    "maxRetries": 3,
    "lastError": "AssertionError: expected:<test_user> but was:<null>",
    "lastErrorFile": "UserServiceTest.java",
    "lastErrorLine": 42,
    "nextBackoffMs": 0
  },
  "timeline": [
    {"stage":"INTENT_ANALYSIS","status":"COMPLETED","durationMs":2500},
    {"stage":"CODE_GENERATION","status":"COMPLETED","durationMs":6500},
    {"stage":"BUILD_TEST","status":"FAILED","durationMs":12000,"error":"E06"},
    {"stage":"BUG_FIX","status":"IN_PROGRESS","round":1,"durationMs":5000},
    {"stage":"BUG_FIX","status":"IN_PROGRESS","round":2,"durationMs":7500},
    {"stage":"BUG_FIX","status":"IN_PROGRESS","round":3,"durationMs":9333}
  ],
  "generatedFiles": [ "..." ],
  "contextUrl": "/api/v1/tasks/task-mock-exhausted-001/context",
  "humanReadableSummary": "Agent failed to fix E06 NPE after 3 rounds.\nLast error: NullPointerException at UserServiceTest.java:42\nRecommendation: Review UserService.getUser() for null guard."
}
```

---

## 6. 异常码表 (Error Catalog)

| Error Code | HTTP Status | 场景 | 触发条件 |
|-----------|-----------|------|---------|
| `VALIDATION_ERROR` | 400 | 请求参数校验失败 | @Valid 注解失败 |
| `TASK_NOT_FOUND` | 404 | 任务 ID 不存在 | DB 查无记录 |
| `INVALID_STATUS_TRANSITION` | 409 | 非法状态转换 | StateMachine.transition() 拒绝 |
| `CONCURRENCY_LIMIT_REACHED` | 409 | Agent 并发池满 | activeCount >= maxConcurrent |
| `TASK_ALREADY_COMPLETED` | 409 | 对已完成任务重复操作 | status == COMPLETE 时调用 cancel |
| `SANDBOX_VIOLATION` | 403 | 安全沙盒违规 | PathValidator 拦截到越权 |
| `LLM_SERVICE_UNAVAILABLE` | 503 | LLM 服务不可用 | LangChain4j 连接超时/拒绝 |
| `REDIS_UNAVAILABLE` | 503 | Redis 连接失败 | 记忆层不可用 |
| `MAVEN_BUILD_TIMEOUT` | 504 | Maven 构建超时 | > 120s 无响应 |
| `HEALING_EXHAUSTED` | 422 | 自修复重试耗尽 | retryCount >= maxRetries |
| `INTERNAL_ERROR` | 500 | 未预期异常 | 兜底异常处理器 |

---

## 7. 前端 ↔ 后端 数据流总览

```
┌───────────────────── Frontend (3_PROTOTYPE.html) ──────────────────────┐
│                                                                      │
│  ┌─ Left Panel ─┐   ┌─ Center Panel ───────────┐   ┌─ Right Panel ─┐ │
│  │ Requirement    │   │ State Machine SVG         │   │ Workspace     │ │
│  │ Input          │   │   ↓                        │   │ File Tree     │ │
│  │ Tech Stack     │   │ Thought Logs               │   │   ↓           │ │
│  │ Fault Toggle   │   │   ↓                        │   │ Code Preview  │ │
│  │ Launch Btn ──────┼──→│ startAgent()              │←──│ selectFile()   │ │
│  └────────────────┘   └──────────────────────────────┘   └────────────────┘ │
│         │                      │                           │
│         │ POST /api/v1/tasks  │                           │
│         │         ↓             │                           │
│         ▼                     ▼                           │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    Backend (Spring Boot 3.4.x)                   │  │
│  │                                                              │  │
│  │  JapController ──→ AgentOrchestrator ──→ JapAgent (VT)       │  │
│  │       │                    │                │                 │  │
│  │       ▼                    ▼                ▼                 │  │
│  │  TaskStatusController ←── Redis Stream ←── STOMP Broker     │  │
│  │       │                                      ↑               │  │
│  │       ▼                                      │ push events   │  │
│  │  ┌──────────────────────────────────────────────────┐       │  │
│  │  │ WebSocket (STOMP /topic/tasks/{id})              │       │  │
│  │  └──────────────────────────────────────────────────┘       │  │
│  └──────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 8. 第五阶段前置要求

进入【第五阶段：基建代码编写】前需完成：

| 序号 | 前置任务 | 产出物 | 依赖 |
|------|---------|--------|------|
| T4-1 | 创建 `pom.xml` (Spring Boot 3.4.x parent) | Maven 项目骨架 | 本文档 §2 |
| T4-2 | 实现 `SubmitRequest` + `ErrorResponse` Records | dto 包 | 本文档 §2.1 |
| T4-3 | 实现 `JapController` (POST /tasks) | Controller 层 | 本文档 §2.1 |
| T4-4 | 实现 `TaskStatusController` (GET /tasks/{id}/status) | Controller 层 | 本文档 §2.2 |
| T4-5 | 实现 `GlobalExceptionHandler` (@RestControllerAdvice) | 异常处理层 | 本文档 §6 |
| T4-6 | 实现 `WebSocketConfiguration` (STOMP endpoint) | WS 配置层 | 本文档 §3 |
| T4-7 | 实现 `StompMessage` sealed hierarchy + `TaskEventPublisher` | WS 消息层 | 本文档 §3.4 |
| T4-8 | 编写集成测试 (MockMvc + STOMP client) 验证完整 API 契约 | 测试套件 | 本文档 §5 |
