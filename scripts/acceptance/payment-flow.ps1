param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "local.merchant@qpay.test",
    [string]$Password = "QPayLocal123!",
    [string]$WebhookSecret = "local-webhook-secret-change-me",
    [string]$MySqlContainer = "onlinepaymentproject-mysql-1",
    [string]$MongoContainer = "onlinepaymentproject-mongodb-1",
    [string]$MySqlPassword = "qpay_local",
    [string]$MongoPassword = "qpay_local",
    [int]$AsyncTimeoutSeconds = 30
)

$ErrorActionPreference = "Stop"

function Assert-Equal($Expected, $Actual, [string]$Message) {
    if ($Expected -ne $Actual) { throw "$Message. Expected '$Expected', received '$Actual'." }
}

function Invoke-QPayJson([string]$Method, [string]$Path, $Body, $Headers = @{}) {
    $arguments = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $Headers; TimeoutSec = 20 }
    if ($null -ne $Body) {
        $arguments.ContentType = "application/json"
        $arguments.Body = $Body | ConvertTo-Json -Depth 8 -Compress
    }
    Invoke-RestMethod @arguments
}

$health = Invoke-QPayJson GET "/actuator/health" $null
Assert-Equal "UP" $health.status "API Gateway is not healthy"

$tokens = Invoke-QPayJson POST "/api/v1/auth/login" @{ username = $Username; password = $Password }
if (-not $tokens.accessToken -or -not $tokens.refreshToken) { throw "Login did not return both tokens." }
$auth = @{ Authorization = "Bearer $($tokens.accessToken)" }
$runId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

$merchant = Invoke-QPayJson POST "/api/v1/merchants" @{
    legalName = "QPay Acceptance Pvt Ltd"
    displayName = "QPay Acceptance"
    registrationNumber = "ACC-$runId"
    countryCode = "IN"
    defaultCurrency = "INR"
} $auth

$wallet = Invoke-QPayJson POST "/api/v1/wallets" @{
    ownerType = "MERCHANT"
    ownerId = $merchant.id
    currency = "INR"
} $auth

$paymentHeaders = @{
    Authorization = "Bearer $($tokens.accessToken)"
    "X-Merchant-Id" = $merchant.id
    "Idempotency-Key" = "acceptance-$runId"
}
$paymentRequest = @{
    walletId = $wallet.id
    merchantReference = "ORDER-$runId"
    amount = @{ amountMinor = 10000; currency = "INR" }
    paymentMethod = @{ type = "UPI" }
    returnUrl = "https://merchant.test/payment-return"
}
$payment = Invoke-QPayJson POST "/api/v1/payments" $paymentRequest $paymentHeaders
Assert-Equal "REQUIRES_ACTION" $payment.status "Unexpected initial payment status"

$replayed = Invoke-QPayJson POST "/api/v1/payments" $paymentRequest $paymentHeaders
Assert-Equal $payment.id $replayed.id "Idempotency replay created another payment"

$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$rawBody = "{}"
$hmac = New-Object Security.Cryptography.HMACSHA256
$hmac.Key = [Text.Encoding]::UTF8.GetBytes($WebhookSecret)
$hash = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes("$timestamp.$rawBody"))
$signature = ([BitConverter]::ToString($hash)).Replace("-", "").ToLowerInvariant()
$webhookHeaders = @{
    "X-Provider-Event-Id" = "evt-$runId"
    "X-Provider-Reference" = $payment.providerReference
    "X-Provider-Status" = "SUCCEEDED"
    "X-Provider-Timestamp" = "$timestamp"
    "X-Provider-Signature" = $signature
}
$completed = Invoke-QPayJson POST "/api/v1/webhooks/providers/payments/STUB" @{} $webhookHeaders
Assert-Equal "SUCCEEDED" $completed.status "Webhook did not complete payment"

$deadline = (Get-Date).AddSeconds($AsyncTimeoutSeconds)
$ledger = $null
$notification = $null
$reconciliation = $null
do {
    Start-Sleep -Seconds 1
    $ledger = docker exec -e "MYSQL_PWD=$MySqlPassword" $MySqlContainer mysql -uqpay -N qpay_wallet -e "SELECT CONCAT(status,':',COUNT(e.id),':',SUM(CASE WHEN e.entry_side='DEBIT' THEN e.amount_minor ELSE 0 END),':',SUM(CASE WHEN e.entry_side='CREDIT' THEN e.amount_minor ELSE 0 END)) FROM journal j JOIN ledger_entry e ON e.journal_id=j.id WHERE j.business_reference='$($payment.id)' GROUP BY j.id,j.status;" 2>$null
    $notification = docker exec $MongoContainer mongosh --quiet -u qpay -p $MongoPassword --authenticationDatabase admin qpay_notification --eval "const n=db.notification.findOne({'safeVariables.paymentId':'$($payment.id)'}); n ? n.status : ''" 2>$null
    $reconciliation = docker exec -e "MYSQL_PWD=$MySqlPassword" $MySqlContainer mysql -uqpay -N qpay_reconciliation -e "SELECT CONCAT(provider_code,':',amount_minor,':',currency,':',status) FROM internal_transaction WHERE reference='$($payment.id)';" 2>$null
} until (($ledger -and $notification -and $reconciliation) -or (Get-Date) -ge $deadline)

Assert-Equal "POSTED:3:10000:10000" "$ledger" "Balanced ledger result is missing"
Assert-Equal "DELIVERED" "$notification" "Notification was not delivered"
Assert-Equal "STUB:10000:INR:SUCCEEDED" "$reconciliation" "Reconciliation record is missing"

[pscustomobject]@{
    Result = "PASS"
    MerchantId = $merchant.id
    WalletId = $wallet.id
    PaymentId = $payment.id
    PaymentStatus = $completed.status
    Ledger = $ledger
    Notification = $notification
    Reconciliation = $reconciliation
}
