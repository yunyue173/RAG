param(
    [string]$Model = "qwen2.5:7b",
    [string]$BaseUrl = "http://localhost:11434"
)

$env:OLLAMA_ENABLED = "true"
$env:OLLAMA_BASE_URL = $BaseUrl
$env:OLLAMA_CHAT_MODEL = $Model
$env:LLM_API_KEY = ""

Write-Host "真实 LLM 已启用：Ollama" $Model "@" $BaseUrl
Write-Host "请确认已安装 Ollama，并已执行：ollama pull $Model"
mvn spring-boot:run
