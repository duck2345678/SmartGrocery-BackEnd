$login = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/auth/login' -ContentType 'application/json' -Body (ConvertTo-Json @{ email='customer.p0@smartgrocery.com'; password='password123' })
$token = $login.token
Write-Output "Logged in. Token length=$($token.Length)"
$prompts = @(
"Tạo danh sách mua sắm cho bữa tối giảm cân",
"Tạo cho tôi list ăn tối nhẹ nhẹ, no lâu, đừng quá ngấy",
"Tôi không ăn hải sản, tạo danh sách mua sắm giàu protein giúp tôi",
"Ok tạo danh sách mua sắm đi",
"Tạo danh sách mua sắm dùm tôi"
)
$i=1
foreach ($p in $prompts) {
    Write-Output "---"
    Write-Output ("Case {0}: {1}" -f $i, $p)
    $body = @{ message = $p } | ConvertTo-Json
    $resp = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/ai/chat' -Headers @{ Authorization = "Bearer $token" } -ContentType 'application/json' -Body $body
    $intent = if ($resp.intentPrediction) { $resp.intentPrediction.detectedIntent } else { $resp.intentDetected }
    Write-Output ("message: {0}" -f $p)
    Write-Output ("intentDetected: {0}" -f $intent)
    Write-Output ("replyStatus: {0}" -f $($resp.replyStatus))
    $propCount = if ($resp.proposedItems) { $resp.proposedItems.Count } else { 0 }
    $recCount = if ($resp.recommendedProductIds) { $resp.recommendedProductIds.Count } else { 0 }
    Write-Output "proposedItems count: $propCount"
    Write-Output "recommendedProductIds count: $recCount"
    Write-Output "Final product names:"
    if ($propCount -gt 0) {
        foreach ($it in $resp.proposedItems) {
            $id = $it.productId
            if ($id) {
                $prod = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/v1/products/$id"
                Write-Output " - $($prod.name) (ID:$id)"
            } else {
                Write-Output " - (no id) $it"
            }
        }
    } else {
        Write-Output " - (none)"
    }
    $i++
    Start-Sleep -Milliseconds 300
}
