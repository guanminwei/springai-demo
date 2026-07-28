$jar = "$env:USERPROFILE\.m2\repository\org\springframework\ai\spring-ai-openai\1.1.2\spring-ai-openai-1.1.2.jar"
Write-Host "Jar exists: $(Test-Path $jar)"
$out = javap -p -c -classpath $jar org.springframework.ai.openai.api.OpenAiAudioApi 2>&1 | Out-String
# 找出所有字符串常量(ldc 指令)中与文件名相关的内容
($out -split "`n") | Where-Object { $_ -match 'ldc.*//\s*String' } | ForEach-Object { $_.Trim() } | Sort-Object -Unique
