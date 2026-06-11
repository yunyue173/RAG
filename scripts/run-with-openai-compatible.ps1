param(
    [string]$ApiKey = "",
    [string]$BaseUrl = "https://api.deepseek.com",
    [string]$ChatModel = "deepseek-chat"
)

if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    $ApiKey = Read-Host "请输入 API Key"
}

$env:LLM_API_KEY = $ApiKey
$env:LLM_BASE_URL = $BaseUrl
$env:LLM_CHAT_MODEL = $ChatModel
$env:LLM_EMBEDDING_ENABLED = "false"

Write-Host "真实 LLM 已启用：" $ChatModel "@" $BaseUrl
mvn spring-boot:run
