$taskKeyBytes = New-Object byte[] 32
$taskRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $taskRandom.GetBytes($taskKeyBytes)
    $env:PASSWORD_RESET_CODE_SECRET = [Convert]::ToBase64String($taskKeyBytes)
} finally {
    $taskRandom.Dispose()
}
