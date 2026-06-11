# 基于 RAG 的智能文档问答系统

这是一个可直接用 IntelliJ IDEA 打开的 Spring Boot Web 项目，对应课程设计“题目三：基于 RAG 的智能文档问答系统”。

## 功能清单

- 文档上传与解析：支持 `.pdf`、`.txt`，前端显示上传进度，后端使用 PDFBox 提取 PDF 文本。
- 文本分块与向量化：默认按 500 字符切块，80 字符重叠；支持 OpenAI Embedding，未配置密钥时使用本地 Hash Embedding。
- 向量存储与检索：向量块持久化到 `data/rag-store`，使用余弦相似度 Top-K 检索，便于课堂演示免安装外部服务。
- LLM 生成答案：支持 OpenAI 兼容接口或本地 Ollama；未配置时仅使用本地 RAG 兜底，不算真正 LLM。
- 引用片段展示：回答后在右侧显示引用原文块、相似度，并按问题关键词高亮。
- 对话历史管理：每个文档可新建多个会话，会话上下文只绑定当前文档。
- 登录注册：访问系统前需要登录，支持本地注册、登录、退出，用户信息保存在 `data/rag-store/users.json`。

## 技术栈

- JDK 21
- Spring Boot 4.0.6
- Spring WebMVC
- Thymeleaf
- Apache PDFBox
- Maven

## 在 IDEA 中运行

1. 打开 IntelliJ IDEA，选择 `Open`，打开目录 `D:\system\system`。
2. 等待 Maven 依赖加载完成。
3. 确认 Project SDK 使用 JDK 21。
4. 运行启动类 `com.example.system.SystemApplication`。
5. 浏览器访问 `http://localhost:8080`。
6. 首次使用先在登录页注册账号，注册成功后自动进入系统。

也可以在项目根目录执行：

```bash
mvn spring-boot:run
```

## 自动部署到云服务器

本项目是 Spring Boot 动态网站，不能直接部署到 GitHub Pages。项目已内置 GitHub Actions 自动部署配置：

- `.github/workflows/deploy.yml`
- `deploy/install-server.sh`
- `deploy/rag-doc-qa.env.example`

完整流程见 [docs/自动部署到云服务器.md](docs/自动部署到云服务器.md)。

## 真实 LLM 配置

不配置任何环境变量也能运行完整流程，但只是本地兜底回答。若要使用真实 LLM，推荐二选一。

### DeepSeek / OpenAI 兼容接口

在项目根目录执行：

```powershell
.\scripts\run-with-openai-compatible.ps1
```

默认按 DeepSeek 兼容接口启动。脚本会提示输入 API Key。

如果使用 OpenAI：

```powershell
.\scripts\run-with-openai-compatible.ps1 -BaseUrl "https://api.openai.com/v1" -ChatModel "gpt-4o-mini"
```

### Ollama 本地模型

先安装 Ollama 并拉取模型：

```powershell
ollama pull qwen2.5:7b
```

再启动：

```powershell
.\scripts\run-with-ollama.ps1
```

页面左上角如果显示 `真实 LLM: ...`，说明已经真正启用大模型。

可在 `src/main/resources/application.properties` 中调整：

- `rag.chunk-size`
- `rag.chunk-overlap`
- `rag.top-k`
- `rag.openai.base-url`
- `rag.openai.embedding-model`
- `rag.openai.chat-model`
- `rag.ollama.chat-model`

更详细说明见 [docs/真实LLM配置.md](docs/真实LLM配置.md)。

## 项目结构

```text
src/main/java/com/example/system
  config/        RAG 配置项
  controller/    页面入口与 REST API
  dto/           前后端交互对象
  model/         文档、分块、会话、消息、引用
  repository/    本地 JSON 持久化
  service/       解析、分块、向量化、检索、回答生成

src/main/resources
  templates/     前端页面
  static/        CSS 与 JavaScript
```

## API 概览

- `POST /api/auth/register`：注册并登录。
- `POST /api/auth/login`：登录。
- `POST /api/auth/logout`：退出登录。
- `GET /api/auth/me`：查看当前登录用户。
- `GET /api/status`：查看 embedding 与回答模式。
- `GET /api/documents`：文档列表。
- `POST /api/documents`：上传 PDF/TXT。
- `DELETE /api/documents/{documentId}`：删除文档及其会话。
- `GET /api/documents/{documentId}/conversations`：查看文档会话。
- `POST /api/documents/{documentId}/conversations`：新建会话。
- `GET /api/conversations/{conversationId}/messages`：查看会话消息。
- `POST /api/conversations/{conversationId}/messages`：提问并返回答案和引用。

## 测试

```bash
mvn test
```

当前包含 Spring Boot 上下文测试、文本切块测试和本地向量相似度测试。
