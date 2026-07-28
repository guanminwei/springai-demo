$root = 'd:\hzzh\project\AI\spring-ai-demo-master\spring-ai-demo-master'
# 从 .env 读取 SILICON_API_KEY
$apiKey = $null
Get-Content "$root\.env" | ForEach-Object {
    if ($_ -match '^\s*SILICON_API_KEY\s*=\s*(.+)\s*$') { $apiKey = $Matches[1].Trim().Trim('"').Trim("'") }
}
if (-not $apiKey) { Write-Host 'SILICON_API_KEY not found in .env'; exit 1 }
Write-Host ('Key loaded, length=' + $apiKey.Length)

$mp3 = "$root\S19-audio-transaction\src\main\resources\test.mp3"
$url = 'https://api.siliconflow.cn/v1/audio/transcriptions'

# 验证文件名后缀对 SiliconFlow 的影响(Spring AI 会硬编码文件名)
$model = 'TeleAI/TeleSpeechASR'

Write-Host "`n===== A. filename=audio.webm (内容仍为mp3) ====="
curl.exe -s -w "`nHTTP_STATUS:%{http_code}" -X POST $url -H "Authorization: Bearer $apiKey" -F "file=@$mp3;filename=audio.webm" -F "model=$model"

Write-Host "`n===== B. filename=audio.wav (内容仍为mp3) ====="
curl.exe -s -w "`nHTTP_STATUS:%{http_code}" -X POST $url -H "Authorization: Bearer $apiKey" -F "file=@$mp3;filename=audio.wav" -F "model=$model"

Write-Host "`n===== C. 无扩展名 filename=audio ====="
curl.exe -s -w "`nHTTP_STATUS:%{http_code}" -X POST $url -H "Authorization: Bearer $apiKey" -F "file=@$mp3;filename=audio" -F "model=$model"

