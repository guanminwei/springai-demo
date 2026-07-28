$path = 'd:\hzzh\project\AI\spring-ai-demo-master\spring-ai-demo-master\S19-audio-transaction\src\main\resources\test.mp3'
$b = [System.IO.File]::ReadAllBytes($path)
Write-Host ('Size: ' + $b.Length + ' bytes')
Write-Host ('Header hex: ' + (($b[0..15] | ForEach-Object { $_.ToString('X2') }) -join ' '))
Write-Host ('Header text: ' + -join ($b[0..40] | ForEach-Object { if ($_ -ge 32 -and $_ -le 126) { [char]$_ } else { '.' } }))
