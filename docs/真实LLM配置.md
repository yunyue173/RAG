# 真实 LLM 配置

项目默认可以离线运行，但离线模式只是“本地检索 + 规则兜底”，不是真正的大模型回答。

## 方案一：DeepSeek / OpenAI 兼容接口

在项目根目录执行：

```powershell
.\scripts\run-with-openai-compatible.ps1
```

默认使用：

- `LLM_BASE_URL=https://api.deepseek.com`
- `LLM_CHAT_MODEL=deepseek-chat`
- `LLM_EMBEDDING_ENABLED=false`

如果使用 OpenAI，可这样启动：

```powershell
.\scripts\run-with-openai-compatible.ps1 -BaseUrl "https://api.openai.com/v1" -ChatModel "gpt-4o-mini"
```

也可以手动设置环境变量：

```powershell
$env:LLM_API_KEY="你的API密钥"
$env:LLM_BASE_URL="https://api.deepseek.com"
$env:LLM_CHAT_MODEL="deepseek-chat"
$env:LLM_EMBEDDING_ENABLED="false"
mvn spring-boot:run
```

## 方案二：本地 Ollama

先安装 Ollama，然后拉取模型：

```powershell
ollama pull qwen2.5:7b
```

再启动项目：

```powershell
.\scripts\run-with-ollama.ps1
```

## 如何判断是否真的启用了 LLM

打开网页后，左上角状态应显示类似：

- `真实 LLM: deepseek-chat（OpenAI兼容）`
- `真实 LLM: qwen2.5:7b（Ollama本地）`

如果显示：

- `未接入真实 LLM：本地 RAG 兜底`

说明还没有真正接入大模型。

## 说明

真实 LLM 负责“基于检索片段生成答案”。文档检索默认使用本地中文关键词向量，稳定且不依赖外部 embedding 接口。

如果你确认使用的是 OpenAI 且想启用远程 Embedding，可以设置：

```powershell
$env:LLM_EMBEDDING_ENABLED="true"
```
