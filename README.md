# J-AP (J-Architect-Pilot) 2026

> Industrial-grade Java AI Agent Engine with Self-Healing Closed-Loop

## 🚀 项目简介

J-AP 是一个工业级的 Java AI Agent 引擎，具有自修复闭环能力。它能够根据用户需求自动生成完整的 Java Web 应用程序，并在遇到错误时自动尝试修复，实现了真正的端到端开发流程自动化。

## ✨ 核心特性

- 🤖 **智能需求分析**：自动理解用户需求并生成技术规范
- 🏗️ **架构设计**：自动设计系统架构和模块划分
- 📝 **代码生成**：基于设计自动生成完整的 Java 代码
- 🔧 **自动修复**：遇到编译错误时自动尝试修复
- 🧪 **自动测试**：生成代码后自动运行测试验证
- 🌐 **实时监控**：通过 WebSocket 实时监控生成进度
- 📱 **流式原型**：实时生成前端原型，支持超时保护
- 🔒 **安全沙箱**：所有操作在安全的沙箱环境中执行

## 🚀 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- Redis (可选，用于缓存)
- LLM API Key (DeepSeek API)

### 运行步骤

1. **克隆项目**

```bash
git clone https://github.com/your-username/j-architect-pilot.git
cd j-architect-pilot
```

2. **配置环境变量**

在运行前设置 DeepSeek API Key：

```bash
# Windows (PowerShell)
$env:JAP_LLM_API_KEY="your-deepseek-api-key"

# Linux/Mac
export JAP_LLM_API_KEY=your-deepseek-api-key
```

3. **编译项目**

```bash
mvn clean package
```

4. **启动应用**

```bash
java -jar target/j-architect-pilot-2026.1.0-SNAPSHOT.jar
```

5. **访问界面**

打开浏览器访问：`http://localhost:8080`

## 🛠️ 技术栈

- **后端框架**: Spring Boot 3.4.4
- **前端**: HTML5 + Tailwind CSS (CDN)
- **LLM 集成**: LangChain4j 1.0.0-beta1
- **数据库**: H2 (内存数据库)
- **缓存**: Redis (可选)
- **构建工具**: Maven
- **Java 版本**: 17+

## 📋 使用说明

### 基本流程

1. 在网页界面输入开发需求
2. 点击"开始生成"按钮
3. 实时查看生成进度和日志
4. 生成完成后查看项目文件
5. 可以下载生成的代码

### 示例需求

```
开发一个简单的图书管理系统，包含以下功能：
1. 用户登录和注册
2. 图书的增删改查
3. 借阅和归还功能
4. 简单的统计报表
使用 Spring Boot + MySQL 开发
```

### 环境变量配置

| 变量名 | 描述 | 默认值 |
|--------|------|--------|
| JAP_LLM_API_KEY | DeepSeek API Key | (必填) |
| JAP_LLM_BASE_URL | LLM API 基础地址 | https://api.deepseek.com |
| JAP_LLM_MODEL | LLM 模型名称 | deepseek-chat |

## 🔒 安全说明

- 所有文件操作都在沙箱环境中执行，防止路径遍历攻击
- API Key 通过环境变量配置，不会硬编码在代码中
- 支持配置允许的网络访问主机

### 敏感信息处理

项目中包含以下可能包含敏感信息的文件，上传到GitHub前请确保：

1. `config/settings.json` - 包含API密钥，请在上传前移除或替换为占位符
2. `.env` 文件（如果存在）- 包含环境变量配置

**建议**：在上传到GitHub前，创建一个 `.gitignore` 文件忽略这些敏感文件。

## 📁 项目结构

```
j-architect-pilot/
├── src/
│   ├── main/
│   │   ├── java/com/jap/
│   │   │   ├── api/          # API 控制器和 WebSocket
│   │   │   ├── config/       # 配置类
│   │   │   ├── core/         # 核心业务逻辑
│   │   │   ├── event/        # 事件发布
│   │   │   ├── healing/      # 错误修复
│   │   │   ├── llm/          # LLM 服务和工具
│   │   │   ├── sandbox/      # 安全沙箱
│   │   │   └── JapApplication.java
│   │   └── resources/
│   │       ├── static/       # 前端静态资源
│   │       └── application.yml
│   └── test/                 # 测试代码
├── pom.xml                   # Maven 配置
└── README.md                 # 项目说明
```

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License

## 🆘 故障排除

### 常见问题

1. **编译错误**
   - 确保 JDK 版本为 17+
   - 检查 Maven 配置

2. **LLM 连接失败**
   - 检查 API Key 是否正确
   - 确认网络连接正常

3. **内存不足**
   - 增加 JVM 内存：`java -Xmx2g -jar target/j-architect-pilot-2026.1.0-SNAPSHOT.jar`

## 📞 联系方式

如有问题，请通过以下方式联系：
- GitHub Issues
- Email: your-email@example.com

---

**注意**: 本项目需要有效的 DeepSeek API Key 才能正常运行。请确保在运行前正确配置环境变量。