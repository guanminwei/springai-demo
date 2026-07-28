$root = 'd:\hzzh\project\AI\spring-ai-demo-master\spring-ai-demo-master'
# 加载 .env 中的环境变量
Get-Content "$root\.env" | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.+)\s*$') {
        [System.Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim().Trim('"').Trim("'"), 'Process')
    }
}
Write-Host ('SILICON_API_KEY loaded: ' + ($null -ne $env:SILICON_API_KEY))
Set-Location $root
& .\mvnw.cmd -q -pl S19-audio-transaction spring-boot:run
