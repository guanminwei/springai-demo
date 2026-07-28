Add-Type -AssemblyName System.Net.Http

$apiKey = "sk-leljrroukidntcnuoeeympknwtxkfwfxnxfdewzzrdhobozl"
$filePath = "d:\hzzh\project\AI\spring-ai-demo-master\spring-ai-demo-master\S19-audio-transaction\src\main\resources\test.mp3"
$url = "https://api.siliconflow.cn/v1/audio/transcriptions"

$client = New-Object System.Net.Http.HttpClient
$client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $apiKey)

$content = New-Object System.Net.Http.MultipartFormDataContent
$fileBytes = [System.IO.File]::ReadAllBytes($filePath)
$fileContent = [System.Net.Http.ByteArrayContent]::new($fileBytes)
$fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new("audio/mpeg")
$content.Add($fileContent, "file", "test.mp3")
$content.Add([System.Net.Http.StringContent]::new("FunAudioLLM/SenseVoiceSmall"), "model")

try {
    $response = $client.PostAsync($url, $content).Result
    $body = $response.Content.ReadAsStringAsync().Result
    Write-Output "Status: $($response.StatusCode) ($([int]$response.StatusCode))"
    Write-Output "Body: $body"
} catch {
    Write-Output "ERROR: $($_.Exception.Message)"
} finally {
    $client.Dispose()
    $content.Dispose()
}
