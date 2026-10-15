$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:8090'
$H = 'Content-Type: application/json'

"health : " + (curl.exe -s "$base/health")

$r1 = curl.exe -s -X POST "$base/auth/request-otp" -H $H -d '{"phone":"+992921234567"}'
"otpA   : $r1"
$c1 = ($r1 | ConvertFrom-Json).dev
$v1 = curl.exe -s -X POST "$base/auth/verify" -H $H -d ('{"phone":"+992921234567","code":"' + $c1 + '","name":"Mustafo"}')
"verifyA: $v1"
$tA = ($v1 | ConvertFrom-Json).token

$r2 = curl.exe -s -X POST "$base/auth/request-otp" -H $H -d '{"phone":"+992555001122"}'
$c2 = ($r2 | ConvertFrom-Json).dev
$v2 = curl.exe -s -X POST "$base/auth/verify" -H $H -d ('{"phone":"+992555001122","code":"' + $c2 + '","name":"Daler"}')
"verifyB: $v2"
$tB = ($v2 | ConvertFrom-Json).token

"search : " + (curl.exe -s "$base/users/search?q=5550" -H "Authorization: Bearer $tA")
"me     : " + (curl.exe -s "$base/users/me" -H "Authorization: Bearer $tA")

"send   : " + (curl.exe -s -X POST "$base/messages/send" -H "Authorization: Bearer $tA" -H $H -d '{"to":"+992555001122","body":"Salom az Oson Aloqa!","enc":false,"kind":"text"}')
"pull B : " + (curl.exe -s "$base/messages/pull" -H "Authorization: Bearer $tB")
"pull B2: " + (curl.exe -s "$base/messages/pull" -H "Authorization: Bearer $tB")
"sendBad: " + (curl.exe -s -X POST "$base/messages/send" -H "Authorization: Bearer $tA" -H $H -d '{"to":"+79990000000","body":"x"}')

"block  : " + (curl.exe -s -X POST "$base/blocks" -H "Authorization: Bearer $tB" -H $H -d '{"address":"+992921234567"}')
"blocks : " + (curl.exe -s "$base/blocks" -H "Authorization: Bearer $tB")
"unblock: " + (curl.exe -s -X DELETE "$base/blocks" -H "Authorization: Bearer $tB" -H $H -d '{"address":"+992921234567"}')
"report : " + (curl.exe -s -X POST "$base/reports" -H "Authorization: Bearer $tB" -H $H -d '{"address":"+992921234567","reason":"spam"}')

"keyPut : " + (curl.exe -s -X POST "$base/keys" -H "Authorization: Bearer $tA" -H $H -d '{"pub":"PUB_A_123"}')
"keyGet : " + (curl.exe -s "$base/keys?phone=%2B992921234567" -H "Authorization: Bearer $tB")

'test file content' | Out-File -Encoding ascii "$env:TEMP\up.txt"
"upload : " + (curl.exe -s -X POST "$base/media/upload" -H "Authorization: Bearer $tA" -H 'Content-Type: application/octet-stream' --data-binary "@$env:TEMP\up.txt")
"unauth : " + (curl.exe -s "$base/users/me")
