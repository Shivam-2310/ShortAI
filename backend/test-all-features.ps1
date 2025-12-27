# ============================================================
# URL Shortener v2.0 - Complete Feature Test Script
# ============================================================
# This script tests all features of the AI-powered URL Shortener
# Run with: powershell -ExecutionPolicy Bypass -File test-all-features.ps1
# ============================================================

$baseUrl = "http://localhost:8080"
$passed = 0
$failed = 0
$total = 0

function Write-TestHeader {
    param([string]$title)
    Write-Host "`n" -NoNewline
    Write-Host "=" * 60 -ForegroundColor Cyan
    Write-Host "  $title" -ForegroundColor Cyan
    Write-Host "=" * 60 -ForegroundColor Cyan
}

function Write-TestResult {
    param([string]$testName, [bool]$success, [string]$details = "")
    $script:total++
    if ($success) {
        $script:passed++
        Write-Host "[PASS] " -ForegroundColor Green -NoNewline
        Write-Host "$testName" -ForegroundColor White
        if ($details) { Write-Host "       $details" -ForegroundColor Gray }
    } else {
        $script:failed++
        Write-Host "[FAIL] " -ForegroundColor Red -NoNewline
        Write-Host "$testName" -ForegroundColor White
        if ($details) { Write-Host "       $details" -ForegroundColor Yellow }
    }
}

function Test-Endpoint {
    param(
        [string]$method,
        [string]$url,
        [object]$body = $null,
        [int]$expectedStatus = 200
    )
    try {
        $params = @{
            Uri = $url
            Method = $method
            ContentType = "application/json"
            UseBasicParsing = $true
            MaximumRedirection = 0  # Don't follow redirects
        }
        if ($body) {
            $params.Body = ($body | ConvertTo-Json -Depth 10)
        }
        $response = Invoke-WebRequest @params -ErrorAction Stop
        return @{
            Success = ($response.StatusCode -eq $expectedStatus)
            StatusCode = $response.StatusCode
            Content = $response.Content | ConvertFrom-Json -ErrorAction SilentlyContinue
            Raw = $response.Content
        }
    } catch {
        $statusCode = 0
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }
        # Handle redirect as success if expected
        if ($expectedStatus -eq 302 -and $statusCode -eq 302) {
            return @{
                Success = $true
                StatusCode = 302
                Content = $null
            }
        }
        return @{
            Success = ($statusCode -eq $expectedStatus)
            StatusCode = $statusCode
            Content = $null
            Error = $_.Exception.Message
        }
    }
}

# ============================================================
Write-Host @"

  _   _ ____  _       ____  _                _                       
 | | | |  _ \| |     / ___|| |__   ___  _ __| |_ ___ _ __   ___ _ __ 
 | | | | |_) | |     \___ \| '_ \ / _ \| '__| __/ _ \ '_ \ / _ \ '__|
 | |_| |  _ <| |___   ___) | | | | (_) | |  | ||  __/ | | |  __/ |   
  \___/|_| \_\_____| |____/|_| |_|\___/|_|   \__\___|_| |_|\___|_|   
                                                                      
         AI-Powered URL Shortener v2.0 - Feature Test Suite
                                                                      
"@ -ForegroundColor Magenta

# ============================================================
Write-TestHeader "1. HEALTH CHECK & SERVICE STATUS"
# ============================================================

# Test 1.1: Health endpoint
$result = Test-Endpoint -method "GET" -url "$baseUrl/actuator/health"
Write-TestResult "Health Check Endpoint" $result.Success "Status: $($result.StatusCode)"

# Test 1.2: AI Service Status
$result = Test-Endpoint -method "GET" -url "$baseUrl/api/ai/status"
$aiAvailable = $result.Content.available -eq $true
Write-TestResult "Ollama AI Service Available" $aiAvailable "Model: $($result.Content.model)"

# Test 1.3: Swagger UI (follows redirect)
try {
    $swaggerResponse = Invoke-WebRequest -Uri "$baseUrl/swagger-ui.html" -UseBasicParsing
    Write-TestResult "Swagger UI Accessible" ($swaggerResponse.StatusCode -eq 200)
} catch {
    Write-TestResult "Swagger UI Accessible" $false
}

# Test 1.4: OpenAPI Docs
$result = Test-Endpoint -method "GET" -url "$baseUrl/api-docs"
Write-TestResult "OpenAPI Docs Accessible" $result.Success

# ============================================================
Write-TestHeader "2. BASIC URL SHORTENING"
# ============================================================

# Test 2.1: Create simple short URL
$result = Test-Endpoint -method "POST" -url "$baseUrl/api/urls" -body @{
    originalUrl = "https://www.google.com"
    fetchMetadata = $false
    enableAiAnalysis = $false
} -expectedStatus 201
$simpleShortKey = $result.Content.shortKey
Write-TestResult "Create Simple Short URL" $result.Success "Short Key: $simpleShortKey"

# Test 2.2: Test redirect (should return 302)
try {
    $redirectResponse = Invoke-WebRequest -Uri "$baseUrl/$simpleShortKey" -UseBasicParsing -MaximumRedirection 0 -ErrorAction SilentlyContinue
    Write-TestResult "Redirect Works (302)" ($redirectResponse.StatusCode -eq 302)
} catch {
    # PowerShell treats 302 as an error when MaximumRedirection=0
    $is302 = $_.Exception.Message -match "302" -or $_.Exception.Response.StatusCode -eq 302
    Write-TestResult "Redirect Works (302)" $is302
}

# Test 2.3: Get basic stats
$result = Test-Endpoint -method "GET" -url "$baseUrl/api/urls/$simpleShortKey/stats"
Write-TestResult "Get Basic Stats" $result.Success "Clicks: $($result.Content.clickCount)"

# ============================================================
Write-TestHeader "3. METADATA EXTRACTION"
# ============================================================

# Test 3.1: Create URL with metadata extraction
$result = Test-Endpoint -method "POST" -url "$baseUrl/api/urls" -body @{
    originalUrl = "https://github.com"
    fetchMetadata = $true
    enableAiAnalysis = $false
} -expectedStatus 201
$metadataKey = $result.Content.shortKey
$hasTitle = $null -ne $result.Content.metadata.title
$hasImage = $null -ne $result.Content.metadata.imageUrl
Write-TestResult "Create URL with Metadata" $result.Success "Key: $metadataKey"
Write-TestResult "Metadata: Title Extracted" $hasTitle "Title: $($result.Content.metadata.title)"
Write-TestResult "Metadata: Image Extracted" $hasImage

# ============================================================
Write-TestHeader "4. AI-POWERED FEATURES"
# ============================================================

# Test 4.1: Create URL with AI analysis
$result = Test-Endpoint -method "POST" -url "$baseUrl/api/urls" -body @{
    originalUrl = "https://stackoverflow.com"
    fetchMetadata = $true
    enableAiAnalysis = $true
} -expectedStatus 201
$aiKey = $result.Content.shortKey
$hasSummary = $null -ne $result.Content.aiAnalysis.summary
$hasCategory = $null -ne $result.Content.aiAnalysis.category
Write-TestResult "Create URL with AI Analysis" $result.Success "Key: $aiKey"
Write-TestResult "AI: Summary Generated" $hasSummary "Summary: $($result.Content.aiAnalysis.summary)"
Write-TestResult "AI: Category Detected" $hasCategory "Category: $($result.Content.aiAnalysis.category)"

# Test 4.2: Direct AI analysis endpoint
$result = Test-Endpoint -method "POST" -url "$baseUrl/api/ai/analyze?url=https://microsoft.com"
$aiWorks = $null -ne $result.Content.summary
Write-TestResult "Direct AI Analysis Endpoint" $aiWorks "Category: $($result.Content.category)"

# Test 4.3: AI Safety Check
$result = Test-Endpoint -method "POST" -url "$baseUrl/api/ai/safety-check?url=https://github.com"
$safetyWorks = $null -ne $result.Content.safetyScore
Write-TestResult "AI Safety Check" $safetyWorks "Safety Score: $($result.Content.safetyScore)"

# Test 4.4: AI Alias Suggestions
$result = Test-Endpoint -method "POST" -url "$baseUrl/api/ai/suggest-alias?url=https://github.com"
Write-TestResult "AI Alias Suggestions" $result.Success "Suggestions: $($result.Content -join ', ')"

# ============================================================
Write-TestHeader "5. CUSTOM ALIASES"
# ============================================================

# Test 5.1: Create URL with custom alias
$customAlias = "test-alias-$(Get-Random -Maximum 9999)"
$result = Test-Endpoint -method "POST" -url "$baseUrl/api/urls" -body @{
    originalUrl = "https://example.com"
    customAlias = $customAlias
} -expectedStatus 201
$aliasCreated = $result.Content.customAlias -eq $customAlias
Write-TestResult "Create URL with Custom Alias" $aliasCreated "Alias: $customAlias"

# Test 5.2: Redirect using custom alias
try {
    $redirectResponse = Invoke-WebRequest -Uri "$baseUrl/$customAlias" -UseBasicParsing -MaximumRedirection 0 -ErrorAction SilentlyContinue
    Write-TestResult "Redirect via Custom Alias" ($redirectResponse.StatusCode -eq 302)
} catch {
    $is302 = $_.Exception.Message -match "302" -or $_.Exception.Response.StatusCode -eq 302
    Write-TestResult "Redirect via Custom Alias" $is302
}

# Test 5.3: Duplicate alias should fail
$result = Test-Endpoint -method "POST" -url "$baseUrl/api/urls" -body @{
    originalUrl = "https://duplicate.com"
    customAlias = $customAlias
} -expectedStatus 400
Write-TestResult "Duplicate Alias Rejected" $result.Success

# ============================================================
Write-TestHeader "6. PASSWORD PROTECTION"
# ============================================================

# Test 6.1: Create password-protected URL
$result = Test-Endpoint -method "POST" -url "$baseUrl/api/urls" -body @{
    originalUrl = "https://secret.example.com"
    password = "testpass123"
} -expectedStatus 201
$protectedKey = $result.Content.shortKey
$isProtected = $result.Content.isPasswordProtected -eq $true
Write-TestResult "Create Password-Protected URL" $isProtected "Key: $protectedKey"

# Test 6.2: Check protection status
$result = Test-Endpoint -method "GET" -url "$baseUrl/api/urls/$protectedKey/protected"
Write-TestResult "Check Protection Status" ($result.Content.passwordRequired -eq $true)

# Test 6.3: Access without password should fail (401)
$result = Test-Endpoint -method "GET" -url "$baseUrl/$protectedKey" -expectedStatus 401
Write-TestResult "Access Without Password Blocked" $result.Success

# Test 6.4: Access with correct password
try {
    $unlockBody = @{ password = "testpass123" } | ConvertTo-Json
    $unlockResponse = Invoke-WebRequest -Uri "$baseUrl/$protectedKey/unlock" -Method POST -Body $unlockBody -ContentType "application/json" -UseBasicParsing -MaximumRedirection 0 -ErrorAction SilentlyContinue
    Write-TestResult "Access With Correct Password" ($unlockResponse.StatusCode -eq 302)
} catch {
    $is302 = $_.Exception.Response.StatusCode -eq 302
    Write-TestResult "Access With Correct Password" $is302
}

# Test 6.5: Access with wrong password (401)
$result = Test-Endpoint -method "POST" -url "$baseUrl/$protectedKey/unlock" -body @{
    password = "wrongpassword"
} -expectedStatus 401
Write-TestResult "Wrong Password Rejected" $result.Success

# ============================================================
Write-TestHeader "7. QR CODE GENERATION"
# ============================================================

# Test 7.1: Create URL with QR code
$result = Test-Endpoint -method "POST" -url "$baseUrl/api/urls" -body @{
    originalUrl = "https://qrtest.example.com"
    generateQrCode = $true
} -expectedStatus 201
$qrKey = $result.Content.shortKey
$hasQrCode = $result.Content.qrCode.Length -gt 100  # Base64 should be long
Write-TestResult "Create URL with QR Code" $hasQrCode "QR Length: $($result.Content.qrCode.Length) chars"

# Test 7.2: Get QR code via endpoint
try {
    $qrResponse = Invoke-WebRequest -Uri "$baseUrl/api/urls/$qrKey/qrcode?size=200" -Method GET -UseBasicParsing
    $isPng = $qrResponse.Headers["Content-Type"] -like "*image/png*"
    Write-TestResult "QR Code Endpoint (PNG)" $isPng "Content-Type: $($qrResponse.Headers["Content-Type"])"
} catch {
    Write-TestResult "QR Code Endpoint (PNG)" $false $_.Exception.Message
}

# Test 7.3: QR code with custom colors
try {
    $qrResponse = Invoke-WebRequest -Uri "$baseUrl/api/urls/$qrKey/qrcode?size=300&fgColor=%23FF0000&bgColor=%23FFFFFF" -Method GET -UseBasicParsing
    Write-TestResult "QR Code with Custom Colors" ($qrResponse.StatusCode -eq 200)
} catch {
    Write-TestResult "QR Code with Custom Colors" $false
}

# ============================================================
Write-TestHeader "8. BULK URL CREATION"
# ============================================================

# Test 8.1: Bulk create URLs
$result = Test-Endpoint -method "POST" -url "$baseUrl/api/urls/bulk" -body @{
    urls = @(
        @{ originalUrl = "https://bulk1.example.com" },
        @{ originalUrl = "https://bulk2.example.com" },
        @{ originalUrl = "https://bulk3.example.com" }
    )
    fetchMetadata = $false
    enableAiAnalysis = $false
} -expectedStatus 201
$bulkSuccess = $result.Content.successCount -eq 3
Write-TestResult "Bulk Create (3 URLs)" $bulkSuccess "Success: $($result.Content.successCount), Failed: $($result.Content.failedCount)"

# ============================================================
Write-TestHeader "9. URL EXPIRATION"
# ============================================================

# Test 9.1: Create URL with expiration (in the past - use UTC and go back 1 day to avoid timezone issues)
# Use UTC time to ensure consistency with server (Docker often runs in UTC)
$pastDateUtc = (Get-Date).ToUniversalTime().AddDays(-1).ToString("yyyy-MM-ddTHH:mm:ss")
$result = Test-Endpoint -method "POST" -url "$baseUrl/api/urls" -body @{
    originalUrl = "https://expired.example.com"
    expiresAt = $pastDateUtc
    fetchMetadata = $false
    enableAiAnalysis = $false
} -expectedStatus 201
$expiredKey = $result.Content.shortKey
Write-TestResult "Create URL with Past Expiry" $result.Success "Key: $expiredKey, ExpiresAt (UTC): $pastDateUtc"

# Test 9.2: Access expired URL should return 410 (use curl for reliable status code)
$expiredStatus = curl.exe -s -o NUL -w "%{http_code}" "$baseUrl/$expiredKey"
$is410 = $expiredStatus -eq "410"
Write-TestResult "Expired URL Returns 410" $is410 "Status: $expiredStatus"

# Test 9.3: Create URL with future expiration
$futureDate = (Get-Date).AddDays(7).ToString("yyyy-MM-ddTHH:mm:ss")
$result = Test-Endpoint -method "POST" -url "$baseUrl/api/urls" -body @{
    originalUrl = "https://future.example.com"
    expiresAt = $futureDate
} -expectedStatus 201
$futureKey = $result.Content.shortKey
Write-TestResult "Create URL with Future Expiry" $result.Success "Expires: $futureDate"

# Test 9.4: Future URL should still work
try {
    $futureResponse = Invoke-WebRequest -Uri "$baseUrl/$futureKey" -UseBasicParsing -MaximumRedirection 0 -ErrorAction SilentlyContinue
    Write-TestResult "Future-Expiry URL Works" ($futureResponse.StatusCode -eq 302)
} catch {
    $is302 = $_.Exception.Response.StatusCode -eq 302
    Write-TestResult "Future-Expiry URL Works" $is302
}

# ============================================================
Write-TestHeader "10. DETAILED ANALYTICS"
# ============================================================

# Test 10.1: Get detailed analytics
$result = Test-Endpoint -method "GET" -url "$baseUrl/api/urls/$simpleShortKey/analytics"
$hasAnalytics = $null -ne $result.Content.totalClicks
Write-TestResult "Get Detailed Analytics" $hasAnalytics "Total Clicks: $($result.Content.totalClicks)"

# Test 10.2: Analytics includes geo breakdown
$hasGeo = $null -ne $result.Content.clicksByCountry
Write-TestResult "Analytics: Geographic Data" $hasGeo

# Test 10.3: Analytics includes device breakdown
$hasDevice = $null -ne $result.Content.clicksByDevice
Write-TestResult "Analytics: Device Data" $hasDevice

# Test 10.4: Analytics structure complete
$hasClicksOverTime = $null -ne $result.Content.clicksOverTime
Write-TestResult "Analytics: Click Trends Data" $hasClicksOverTime

# ============================================================
Write-TestHeader "11. URL PREVIEW"
# ============================================================

# Test 11.1: Get URL preview
$result = Test-Endpoint -method "GET" -url "$baseUrl/api/urls/$metadataKey/preview"
Write-TestResult "Get URL Preview" $result.Success "Title: $($result.Content.title)"

# ============================================================
Write-TestHeader "12. ERROR HANDLING"
# ============================================================

# Test 12.1: Invalid URL format
$result = Test-Endpoint -method "POST" -url "$baseUrl/api/urls" -body @{
    originalUrl = "not-a-valid-url"
} -expectedStatus 400
Write-TestResult "Invalid URL Rejected" $result.Success

# Test 12.2: Missing URL
$result = Test-Endpoint -method "POST" -url "$baseUrl/api/urls" -body @{
    originalUrl = ""
} -expectedStatus 400
Write-TestResult "Empty URL Rejected" $result.Success

# Test 12.3: Non-existent short key
$result = Test-Endpoint -method "GET" -url "$baseUrl/nonexistent123" -expectedStatus 404
Write-TestResult "Non-existent URL Returns 404" $result.Success

# Test 12.4: Invalid custom alias format
$result = Test-Endpoint -method "POST" -url "$baseUrl/api/urls" -body @{
    originalUrl = "https://valid.com"
    customAlias = "ab"  # Too short
} -expectedStatus 400
Write-TestResult "Short Alias Rejected" $result.Success

# ============================================================
# FINAL SUMMARY
# ============================================================
Write-Host "`n"
Write-Host "=" * 60 -ForegroundColor Cyan
Write-Host "                    TEST SUMMARY" -ForegroundColor Cyan
Write-Host "=" * 60 -ForegroundColor Cyan
Write-Host ""
Write-Host "  Total Tests:  $total" -ForegroundColor White
Write-Host "  Passed:       " -NoNewline; Write-Host "$passed" -ForegroundColor Green
Write-Host "  Failed:       " -NoNewline; Write-Host "$failed" -ForegroundColor $(if ($failed -gt 0) { "Red" } else { "Green" })
Write-Host ""

$percentage = [math]::Round(($passed / $total) * 100, 1)
if ($percentage -eq 100) {
    Write-Host "  ✅ ALL TESTS PASSED! ($percentage%)" -ForegroundColor Green
} elseif ($percentage -ge 80) {
    Write-Host "  ⚠️  MOSTLY PASSED ($percentage%)" -ForegroundColor Yellow
} else {
    Write-Host "  ❌ TESTS FAILED ($percentage%)" -ForegroundColor Red
}

Write-Host ""
Write-Host "=" * 60 -ForegroundColor Cyan
Write-Host ""

