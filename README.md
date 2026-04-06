# J-AP (J-Architect-Pilot) 2026

工业级 Java AI Agent 引擎，支持从需求到设计、代码生成、构建测试与自愈修复的闭环流程。

## 核心能力
- 需求分析与设计文档生成
- Java 后端代码自动生成
- 构建/测试自动执行
- 失败后自动修复重试
- WebSocket 实时进度与日志
- 配置面板支持动态设置 LLM 与工作目录

## 技术栈
- Java 17
- Spring Boot 3.4.4
- LangChain4j 1.0.0-beta1
- H2 / MySQL / Redis（按场景）
- 前端：静态 HTML + 本地 Tailwind 运行时

## 本地运行
1. 构建：
```bash
mvn package -DskipTests
```
2. 启动：
```bash
java -jar target/j-architect-pilot-2026.1.0-SNAPSHOT-exec.jar
```
3. 打开：`http://localhost:8080`

## 面试版打包（Windows）
已提供一键脚本：
```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-release.ps1
```
产物目录示例：`dist/build-YYYYMMDD-HHMMSS/J-AP/`

可执行文件：`J-AP.exe`

## 面试官体验步骤
1. 双击 `J-AP.exe`
2. 浏览器访问 `http://localhost:8080`
3. 打开右上角“系统设置”
4. 填写 API Key 并保存
5. 输入需求，开始体验设计/实现流程
6. 默认无需安装 Redis（已关闭 Redis 健康检查）

## 配置文件说明
- 本地敏感配置：`config/settings.json`（已在 `.gitignore` 忽略）
- 模板文件：`config/settings.template.json`

## 安全与提交规范
- 不要提交任何真实 API Key
- 提交前确认 `config/settings.json` 未被纳入版本控制
- 构建产物目录（`target/`, `dist/`, `release/` 等）已忽略

## 常见问题
- 页面样式异常：确认使用最新构建包，或刷新浏览器缓存（`Ctrl + F5`）
- LLM 连接失败：检查 Base URL、API Key、网络连通性
- 打包后无法启动：重新执行 `scripts/build-release.ps1` 生成新包
