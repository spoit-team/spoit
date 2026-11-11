#Requires -Version 5.1
<#
.SYNOPSIS
Starts an isolated Docker app + MySQL 8.4 server for manual withdrawal testing.

.DESCRIPTION
Creates a run-scoped network and brand-new MySQL 8.4 container, applies db/schema.sql and
scripts/fixtures/withdrawal-dummy.sql to a fresh database inside it, then starts the app
built from the repository Dockerfile and publishes it on 127.0.0.1 only (default port 18080).

Isolation guarantees
- Existing containers, networks and the development database are never modified.
  Cleanup (Stop/Reset) verifies both the resource-name shape and this run's labels first.
- MySQL joins only the dedicated network, publishes no host port, and keeps data on tmpfs
  so nothing persistent is written to the host (no bind mounts).
- DB credentials are generated in process memory with a cryptographic RNG and handed to
  Docker by environment-variable NAME, never as a value on a command line.
- The login password is read from the user-scoped (or process) environment variable
  SPOIT_DUMMY_LOGIN_PASSWORD. The value is never printed, logged, or written to disk.
- The fixture references the session variable @withdrawal_password_hash. This script sets it
  in the same SQL session from a BCrypt hash generated at runtime (compatible with Spring
  BCryptPasswordEncoder) before applying the fixture.

Compatibility
The source is deliberately pure ASCII because Windows PowerShell 5.1 decodes script files
without a UTF-8 BOM using the system ANSI code page (for example, cp949).

.PARAMETER Mode
Start (default): create and start the isolated server.
Stop: remove only the dedicated containers/network of the given RunId.
Reset: remove that RunId's resources, then recreate them fresh (data is new).
Status: show only that RunId's dedicated container state.

.PARAMETER RunId
Suffix for dedicated resource names (lowercase letters and digits, 4-24 chars).
Generated randomly for Start when omitted. Required for Stop/Reset/Status. Wildcards are
not supported.

.PARAMETER Port
Loopback port for the app. Default 18080.

.EXAMPLE
powershell -ExecutionPolicy Bypass -File scripts/withdrawal-dummy.ps1

.EXAMPLE
powershell -ExecutionPolicy Bypass -File scripts/withdrawal-dummy.ps1 -Mode Stop -RunId 1a2b3c4d

.EXAMPLE
powershell -ExecutionPolicy Bypass -File scripts/withdrawal-dummy.ps1 -Mode Reset -RunId 1a2b3c4d
#>
[CmdletBinding()]
param(
    [ValidateSet('Start', 'Stop', 'Reset', 'Status')]
    [string]$Mode = 'Start',
    [string]$RunId,
    [ValidateRange(1, 65535)]
    [int]$Port = 18080,
    [string]$MySqlImage = 'mysql:8.4',
    [string]$HasherImage = 'httpd:2.4-alpine',
    [string]$AppImage = 'spoit-wd-app:local',
    [switch]$RebuildImage
)

$ErrorActionPreference = 'Stop'

$taskRepo = Split-Path -Parent $PSScriptRoot
$taskSchemaFile = Join-Path $taskRepo 'db/schema.sql'
$taskFixtureFile = Join-Path $taskRepo 'scripts/fixtures/withdrawal-dummy.sql'
$taskDockerFile = Join-Path $taskRepo 'Dockerfile'

# Dedicated resources are identified only by this prefix plus these labels.
# Resources without them are never inspected further, let alone removed.
$taskNamePrefix = 'spoit-wd'
$taskLabelMarker = 'spoit.withdrawal-dummy'
$taskLabelRun = 'spoit.withdrawal-dummy.run'
$taskLabelRole = 'spoit.withdrawal-dummy.role'
$taskDatabase = 'spoit_withdrawal_dummy'
$taskPasswordVariable = 'SPOIT_DUMMY_LOGIN_PASSWORD'

# Every secret value registered here is masked out of anything printed.
$script:SecretValues = New-Object 'System.Collections.Generic.List[string]'
$script:SavedEnvironment = @{}
# Resource set currently being handled. Set only by Start/Stop/Status.
$script:WdTarget = $null

function Add-SecretValue {
    param([string]$Value)
    if (-not [string]::IsNullOrEmpty($Value)) { [void]$script:SecretValues.Add($Value) }
}

function Protect-Text {
    param([string]$Text)
    if ([string]::IsNullOrEmpty($Text)) { return '' }
    foreach ($taskSecret in $script:SecretValues) { $Text = $Text.Replace($taskSecret, '***') }
    return $Text
}

function Write-Step {
    param([string]$Message)
    Write-Host $Message
}

function Set-WorkingEnvironment {
    param([string]$Name, [string]$Value)
    if (-not $script:SavedEnvironment.ContainsKey($Name)) {
        $script:SavedEnvironment[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process')
    }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}

function Restore-WorkingEnvironment {
    foreach ($taskName in $script:SavedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($taskName, $script:SavedEnvironment[$taskName], 'Process')
    }
    $script:SavedEnvironment.Clear()
}

# Windows command-line quoting (the inverse of CommandLineToArgvW). PowerShell 5.1 has no
# ProcessStartInfo.ArgumentList, so arguments are joined into a single escaped string.
# Round-tripping exactly matters: a mis-quoted argument silently changes a docker flag.
function ConvertTo-CommandLineArgument {
    param([string]$Value)
    if ($null -eq $Value) { $Value = '' }
    if ($Value.Length -eq 0) { return '""' }
    if ($Value -notmatch '[\s"]') { return $Value }
    $taskBuilder = New-Object System.Text.StringBuilder
    [void]$taskBuilder.Append('"')
    $taskBackslashes = 0
    foreach ($taskChar in $Value.ToCharArray()) {
        if ($taskChar -eq [char]92) {
            $taskBackslashes++
            continue
        }
        if ($taskChar -eq [char]34) {
            # Double the preceding backslashes, then escape the quote itself.
            if ($taskBackslashes -gt 0) { [void]$taskBuilder.Append([string]::new([char]92, $taskBackslashes * 2)) }
            [void]$taskBuilder.Append([string]::new([char]92, 1))
            [void]$taskBuilder.Append('"')
            $taskBackslashes = 0
            continue
        }
        if ($taskBackslashes -gt 0) {
            [void]$taskBuilder.Append([string]::new([char]92, $taskBackslashes))
            $taskBackslashes = 0
        }
        [void]$taskBuilder.Append($taskChar)
    }
    if ($taskBackslashes -gt 0) { [void]$taskBuilder.Append([string]::new([char]92, $taskBackslashes * 2)) }
    [void]$taskBuilder.Append('"')
    return $taskBuilder.ToString()
}

# Runs the docker CLI and keeps stdout/stderr in memory only.
# Standard input is written as raw UTF-8 bytes: PowerShell 5.1 cannot set
# ProcessStartInfo.StandardInputEncoding and would otherwise encode with the ANSI
# code page, corrupting a non-ASCII password before BCrypt ever sees it.
function Invoke-Docker {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [string]$StandardInput,
        [int]$TimeoutSeconds = 120
    )
    $taskEscaped = @()
    foreach ($taskArgument in $Arguments) { $taskEscaped += (ConvertTo-CommandLineArgument -Value $taskArgument) }
    $taskStart = New-Object System.Diagnostics.ProcessStartInfo
    $taskStart.FileName = 'docker'
    $taskStart.Arguments = ($taskEscaped -join ' ')
    $taskStart.UseShellExecute = $false
    $taskStart.CreateNoWindow = $true
    $taskStart.RedirectStandardInput = $true
    $taskStart.RedirectStandardOutput = $true
    $taskStart.RedirectStandardError = $true
    $taskStart.StandardOutputEncoding = New-Object System.Text.UTF8Encoding($false)
    $taskStart.StandardErrorEncoding = New-Object System.Text.UTF8Encoding($false)
    $taskProcess = New-Object System.Diagnostics.Process
    $taskProcess.StartInfo = $taskStart
    try {
        [void]$taskProcess.Start()
        $taskStdOut = $taskProcess.StandardOutput.ReadToEndAsync()
        $taskStdErr = $taskProcess.StandardError.ReadToEndAsync()
        if ($null -ne $StandardInput) {
            $taskBytes = [Text.Encoding]::UTF8.GetBytes($StandardInput)
            $taskProcess.StandardInput.BaseStream.Write($taskBytes, 0, $taskBytes.Length)
            $taskProcess.StandardInput.BaseStream.Flush()
        }
        $taskProcess.StandardInput.Close()
        if (-not $taskProcess.WaitForExit($TimeoutSeconds * 1000)) {
            try { $taskProcess.Kill() } catch { }
            [void]$taskProcess.WaitForExit()
            return [pscustomobject]@{ ExitCode = 124; Output = ''; ErrorText = 'docker command timed out' }
        }
        return [pscustomobject]@{
            ExitCode  = $taskProcess.ExitCode
            Output    = $taskStdOut.GetAwaiter().GetResult()
            ErrorText = $taskStdErr.GetAwaiter().GetResult()
        }
    } finally {
        $taskProcess.Dispose()
    }
}

function Get-RunResources {
    param([string]$TaskRunId)
    return [pscustomobject]@{
        RunId   = $TaskRunId
        Network = "$taskNamePrefix-net-$TaskRunId"
        MySql   = "$taskNamePrefix-mysql-$TaskRunId"
        App     = "$taskNamePrefix-app-$TaskRunId"
    }
}

function Test-DedicatedContainer {
    param([string]$Name)
    $taskResult = Invoke-Docker -Arguments @('container', 'inspect', '--format', '{{.Id}}', $Name) -TimeoutSeconds 30
    return ($taskResult.ExitCode -eq 0)
}

function Test-DedicatedNetwork {
    param([string]$Name)
    $taskResult = Invoke-Docker -Arguments @('network', 'inspect', '--format', '{{.Id}}', $Name) -TimeoutSeconds 30
    return ($taskResult.ExitCode -eq 0)
}

function Get-ContainerLabels {
    param([string]$Name)
    $taskResult = Invoke-Docker -Arguments @('container', 'inspect', '--format', '{{json .Config.Labels}}', $Name) -TimeoutSeconds 30
    if ($taskResult.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($taskResult.Output)) { return $null }
    try { return $taskResult.Output.Trim() | ConvertFrom-Json } catch { return $null }
}

function Get-NetworkLabels {
    param([string]$Name)
    $taskResult = Invoke-Docker -Arguments @('network', 'inspect', '--format', '{{json .Labels}}', $Name) -TimeoutSeconds 30
    if ($taskResult.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($taskResult.Output)) { return $null }
    try { return $taskResult.Output.Trim() | ConvertFrom-Json } catch { return $null }
}

# Label names contain dots, so $object.$variable would be parsed as a nested path and
# silently yield nothing. The PSObject property indexer addresses the exact key.
function Get-LabelValue {
    param($Labels, [string]$Key)
    if ($null -eq $Labels) { return '' }
    $taskProperty = $Labels.PSObject.Properties[$Key]
    if ($null -eq $taskProperty -or $null -eq $taskProperty.Value) { return '' }
    return [string]$taskProperty.Value
}

# A resource is ours only if both the name shape and all labels match this run.
# Anything else is left untouched and reported.
function Assert-DedicatedContainer {
    param([string]$Name, [string]$TaskRunId, [string]$Role)
    if ($Name -cnotmatch "^$taskNamePrefix-(app|mysql)-[a-z0-9]{4,24}$") {
        throw "Refusing to touch a resource whose name is not a dedicated-resource name: $Name"
    }
    $taskLabels = Get-ContainerLabels -Name $Name
    if ((Get-LabelValue -Labels $taskLabels -Key $taskLabelMarker) -cne '1' -or
        (Get-LabelValue -Labels $taskLabels -Key $taskLabelRun) -cne $TaskRunId -or
        (Get-LabelValue -Labels $taskLabels -Key $taskLabelRole) -cne $Role) {
        throw "Refusing to touch a container this script did not create (verify manually): $Name"
    }
}

function Assert-DedicatedNetwork {
    param([string]$Name, [string]$TaskRunId)
    if ($Name -cnotmatch "^$taskNamePrefix-net-[a-z0-9]{4,24}$") {
        throw "Refusing to touch a resource whose name is not a dedicated-network name: $Name"
    }
    $taskLabels = Get-NetworkLabels -Name $Name
    if ((Get-LabelValue -Labels $taskLabels -Key $taskLabelMarker) -cne '1' -or
        (Get-LabelValue -Labels $taskLabels -Key $taskLabelRun) -cne $TaskRunId) {
        throw "Refusing to touch a network this script did not create (verify manually): $Name"
    }
}

# Token generation from a cryptographic RNG. GetInt32/Fill are unavailable in the
# Windows PowerShell 5.1/.NET Framework runtime, so bytes
# are drawn with GetBytes and reduced by rejection sampling: a byte is kept only when it is
# below the largest multiple of the alphabet size, which avoids modulo bias.
function New-RandomToken {
    param([int]$Length = 24)
    $taskAlphabet = 'abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'
    $taskLimit = [int][Math]::Floor(256 / $taskAlphabet.Length) * $taskAlphabet.Length
    $taskBuilder = New-Object System.Text.StringBuilder
    $taskRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        while ($taskBuilder.Length -lt $Length) {
            $taskChunk = New-Object 'byte[]' 64
            $taskRandom.GetBytes($taskChunk)
            foreach ($taskByte in $taskChunk) {
                if ($taskBuilder.Length -ge $Length) { break }
                if ($taskByte -lt $taskLimit) {
                    [void]$taskBuilder.Append($taskAlphabet[$taskByte % $taskAlphabet.Length])
                }
            }
        }
    } finally {
        $taskRandom.Dispose()
    }
    return $taskBuilder.ToString()
}

function New-RandomRunId {
    $taskBytes = New-Object 'byte[]' 4
    $taskRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $taskRandom.GetBytes($taskBytes) } finally { $taskRandom.Dispose() }
    return ([BitConverter]::ToString($taskBytes) -replace '-', '').ToLowerInvariant()
}

# Prefers the user-scoped variable, then the current process scope.
# The value is not trimmed (surrounding characters are significant for a password).
function Resolve-DummyPassword {
    $taskPassword = [Environment]::GetEnvironmentVariable($taskPasswordVariable, 'User')
    if ([string]::IsNullOrEmpty($taskPassword)) {
        $taskPassword = [Environment]::GetEnvironmentVariable($taskPasswordVariable, 'Process')
    }
    if ([string]::IsNullOrEmpty($taskPassword)) {
        throw "$taskPasswordVariable is not set. Set it as a user (or process) environment variable and retry."
    }
    if ($taskPassword -match '[\x00-\x1f\x7f]') {
        throw "$taskPasswordVariable contains control characters (newline etc.), so it cannot be BCrypt-hashed."
    }
    $taskByteCount = [Text.Encoding]::UTF8.GetByteCount($taskPassword)
    if ($taskByteCount -gt 72) {
        throw "$taskPasswordVariable exceeds the BCrypt limit of 72 bytes (currently $taskByteCount bytes)."
    }
    Add-SecretValue $taskPassword
    return $taskPassword
}

# htpasswd emits $2y$ BCrypt, which Spring's BCryptPasswordEncoder verifies as-is.
# The password travels over standard input only, never as an argument or environment value.
function New-BcryptHash {
    param([string]$Password)
    $taskResult = Invoke-Docker -Arguments @('run', '--rm', '--interactive', '--network', 'none',
        $HasherImage, 'htpasswd', '-inBC', '10', 'spoit-dummy') -StandardInput ($Password + "`n") -TimeoutSeconds 300
    if ($taskResult.ExitCode -ne 0) {
        throw "Failed to generate the BCrypt hash. Check that Docker can pull $HasherImage."
    }
    $taskLine = ($taskResult.Output -split "`n" | Where-Object { $_.Trim() } | Select-Object -First 1)
    if ($null -ne $taskLine) { $taskLine = $taskLine.Trim() }
    if ($taskLine -notmatch '^spoit-dummy:(\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53})$') {
        throw 'Unexpected BCrypt hash format; refusing to seed.'
    }
    return $Matches[1]
}

function Assert-LoopbackPortFree {
    param([int]$TaskPort)
    $taskListener = $null
    try {
        $taskListener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $TaskPort)
        $taskListener.Start()
    } catch {
        throw "127.0.0.1:$TaskPort is not available. Free that port and retry."
    } finally {
        if ($taskListener) { $taskListener.Stop() }
    }
}

# Executes SQL inside the dedicated MySQL container only. The statement travels as base64
# over standard input so it never appears in a command line or process list, and raw client
# errors are not printed. The container-side script uses no double quotes, keeping the
# Windows argument escaping unambiguous.
function Invoke-DedicatedSql {
    param(
        [string]$Database,
        [string]$Sql,
        [int]$TimeoutSeconds = 120,
        [switch]$Scalar
    )
    if ($Database -cnotmatch '^[a-z0-9_]+$') { throw 'Dedicated database name is not in a safe format.' }
    $taskEncoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Sql))
    # No CR filter: the payload comes from Convert.ToBase64String and contains no line breaks.
    # (sh 'tr -d "\r"' deletes literal r characters and corrupts base64.)
    $taskShell = 'base64 -d | mysql --protocol=TCP --host=127.0.0.1 --user=$MYSQL_USER --database=' +
        $Database + ' --default-character-set=utf8mb4 --batch --raw'
    if ($Scalar) { $taskShell += ' --skip-column-names' }
    return Invoke-Docker -Arguments @('exec', '--interactive', '--env', 'MYSQL_PWD', $script:WdTarget.MySql, 'sh', '-c', $taskShell) `
        -StandardInput $taskEncoded -TimeoutSeconds $TimeoutSeconds
}

function Wait-MySqlReady {
    $taskDeadline = [DateTime]::UtcNow.AddSeconds(180)
    while ([DateTime]::UtcNow -lt $taskDeadline) {
        if (-not (Test-DedicatedContainer -Name $script:WdTarget.MySql)) { throw 'The dedicated MySQL container disappeared.' }
        $taskState = (Invoke-Docker -Arguments @('container', 'inspect', '--format', '{{.State.Running}}', $script:WdTarget.MySql)).Output.Trim()
        if ($taskState -ne 'true') {
            Show-ContainerLogTail -Name $script:WdTarget.MySql
            throw 'The dedicated MySQL container exited before becoming ready.'
        }
        $taskProbe = Invoke-DedicatedSql -Database $taskDatabase -Sql 'SELECT 1;' -TimeoutSeconds 20 -Scalar
        if ($taskProbe.ExitCode -eq 0) { return }
        Start-Sleep -Seconds 2
    }
    throw 'Timed out waiting for the dedicated MySQL server.'
}

function Show-ContainerLogTail {
    param([string]$Name)
    $taskResult = Invoke-Docker -Arguments @('logs', '--tail', '60', $Name) -TimeoutSeconds 30
    $taskText = Protect-Text (($taskResult.Output + $taskResult.ErrorText).Trim())
    if ([string]::IsNullOrWhiteSpace($taskText)) { return }
    # Initialization logs can contain generated secrets, so matching lines are dropped whole.
    $taskLines = @($taskText -split "`n" | Where-Object { $_ -notmatch '(?i)password|secret|generated root|MYSQL_PWD' })
    if ($taskLines.Count -eq 0) { return }
    Write-Host '--- container log tail (secret lines removed) ---'
    Write-Host (($taskLines | Select-Object -Last 30) -join [Environment]::NewLine)
    Write-Host '--- end of log ---'
}

function Test-OptionalImage {
    param([string]$Image)
    $taskResult = Invoke-Docker -Arguments @('image', 'inspect', '--format', '{{.Id}}', $Image) -TimeoutSeconds 30
    return ($taskResult.ExitCode -eq 0)
}

# Readiness probe that works on PowerShell 5.1, which lacks -SkipHttpErrorCheck.
# Any HTTP status means the container is answering; connection errors mean it is not yet up.
function Test-AppResponding {
    param([int]$TaskPort)
    $taskUri = "http://127.0.0.1:$TaskPort/login"
    try {
        $taskResponse = Invoke-WebRequest -Uri $taskUri -TimeoutSec 5 -UseBasicParsing
        return ($null -ne $taskResponse)
    } catch {
        $taskWebResponse = $_.Exception.Response
        if ($null -ne $taskWebResponse) {
            try {
                $taskCode = [int]$taskWebResponse.StatusCode
                return ($taskCode -ge 100 -and $taskCode -lt 600)
            } catch {
                return $true
            }
        }
        return $false
    }
}

function Get-FixtureEmails {
    param([string]$Database)
    # Only accounts actually seeded are listed, so example addresses in file comments are ignored.
    $taskResult = Invoke-DedicatedSql -Database $Database `
        -Sql 'SELECT email FROM app_user ORDER BY user_id;' -Scalar -TimeoutSeconds 30
    if ($taskResult.ExitCode -ne 0) { return @() }
    return @($taskResult.Output -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

function Start-DummyServer {
    foreach ($taskFile in @($taskSchemaFile, $taskDockerFile)) {
        if (-not (Test-Path -LiteralPath $taskFile -PathType Leaf)) { throw "Required file is missing: $taskFile" }
    }
    if (-not (Test-Path -LiteralPath $taskFixtureFile -PathType Leaf)) {
        throw 'Fixture file is missing (provided separately): scripts/fixtures/withdrawal-dummy.sql'
    }

    $taskDockerVersion = Invoke-Docker -Arguments @('version', '--format', '{{.Server.Version}}') -TimeoutSeconds 60
    if ($taskDockerVersion.ExitCode -ne 0) {
        throw 'Cannot reach the Docker daemon. Check that Docker Desktop is running.'
    }

    # Validate the password before any network or container work happens.
    $taskLoginPassword = Resolve-DummyPassword

    $taskResources = Get-RunResources -TaskRunId $RunId
    $script:WdTarget = $taskResources
    if ((Test-DedicatedContainer -Name $taskResources.MySql) -or (Test-DedicatedContainer -Name $taskResources.App)) {
        throw "Dedicated containers for this RunId already exist. Use -Mode Reset -RunId $($taskResources.RunId) to recreate, or -Mode Stop to clean up."
    }
    if (Test-DedicatedNetwork -Name $taskResources.Network) {
        throw "A dedicated network for this RunId already exists. Clean it with -Mode Reset -RunId $($taskResources.RunId)."
    }

    Assert-LoopbackPortFree -TaskPort $Port

    if (-not (Test-OptionalImage -Image $MySqlImage)) {
        Write-Step "Pulling MySQL image: $MySqlImage"
        $taskPull = Invoke-Docker -Arguments @('pull', $MySqlImage) -TimeoutSeconds 900
        if ($taskPull.ExitCode -ne 0) { throw "Failed to pull the MySQL image: $MySqlImage" }
    }
    if (-not (Test-OptionalImage -Image $HasherImage)) {
        Write-Step "Pulling hash-tool image: $HasherImage"
        $taskPull = Invoke-Docker -Arguments @('pull', $HasherImage) -TimeoutSeconds 900
        if ($taskPull.ExitCode -ne 0) { throw "Failed to pull the hash-tool image: $HasherImage" }
    }
    if ($RebuildImage -or -not (Test-OptionalImage -Image $AppImage)) {
        Write-Step "Building the app image from the Dockerfile: $AppImage"
        Push-Location $taskRepo
        try {
            & docker build --file $taskDockerFile --tag $AppImage $taskRepo
            if ($LASTEXITCODE -ne 0) { throw "App image build failed (exit code $LASTEXITCODE)." }
        } finally {
            Pop-Location
        }
    }

    # Credentials exist only in this process's memory, and Docker receives variable names.
    $taskDbUser = 'spoit_dummy_' + (New-RandomToken -Length 8).ToLowerInvariant()
    $taskDbPassword = New-RandomToken -Length 32
    $taskRememberMeKey = New-RandomToken -Length 40
    Add-SecretValue $taskDbPassword
    Add-SecretValue $taskRememberMeKey
    $taskMySqlHost = $taskResources.MySql

    Write-Step "Creating the dedicated network: $($taskResources.Network)"
    $taskNetwork = Invoke-Docker -Arguments @('network', 'create', '--driver', 'bridge',
        '--label', "$taskLabelMarker=1", '--label', "$taskLabelRun=$($taskResources.RunId)",
        $taskResources.Network) -TimeoutSeconds 60
    if ($taskNetwork.ExitCode -ne 0) {
        throw ('Failed to create the dedicated network. ' + (Protect-Text $taskNetwork.ErrorText.Trim()))
    }

    try {
        Set-WorkingEnvironment 'MYSQL_USER' $taskDbUser
        Set-WorkingEnvironment 'MYSQL_PASSWORD' $taskDbPassword
        Set-WorkingEnvironment 'MYSQL_PWD' $taskDbPassword

        Write-Step 'Creating a brand-new dedicated MySQL 8.4 container (no host port, tmpfs data).'
        $taskMySqlRun = Invoke-Docker -Arguments @('run', '--detach', '--name', $taskResources.MySql,
            '--label', "$taskLabelMarker=1", '--label', "$taskLabelRun=$($taskResources.RunId)",
            '--label', "$taskLabelRole=mysql",
            '--network', $taskResources.Network, '--network-alias', $taskMySqlHost,
            '--tmpfs', '/var/lib/mysql:rw,size=1g',
            '--env', 'MYSQL_USER', '--env', 'MYSQL_PASSWORD',
            '--env', "MYSQL_DATABASE=$taskDatabase", '--env', 'MYSQL_RANDOM_ROOT_PASSWORD=yes',
            $MySqlImage,
            '--character-set-server=utf8mb4', '--collation-server=utf8mb4_0900_ai_ci') -TimeoutSeconds 120
        if ($taskMySqlRun.ExitCode -ne 0) {
            throw ('Failed to create the dedicated MySQL container. ' + (Protect-Text $taskMySqlRun.ErrorText.Trim()))
        }

        Write-Step 'Waiting for the dedicated MySQL server.'
        Wait-MySqlReady

        $taskVersion = Invoke-DedicatedSql -Database $taskDatabase -Sql 'SELECT VERSION();' -Scalar -TimeoutSeconds 30
        $taskVersionText = $taskVersion.Output.Trim()
        if ($taskVersion.ExitCode -ne 0 -or $taskVersionText -notmatch '^8\.4\.') {
            throw "Could not confirm the dedicated MySQL version (8.4 required). Actual: $taskVersionText"
        }

        $taskSchemaCheck = Invoke-DedicatedSql -Database $taskDatabase `
            -Sql "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = '$taskDatabase';" -Scalar -TimeoutSeconds 30
        if ($taskSchemaCheck.ExitCode -ne 0 -or $taskSchemaCheck.Output.Trim() -ne '1') {
            throw 'The dedicated database was not created; refusing to apply the schema.'
        }

        Write-Step 'Applying db/schema.sql to the dedicated database only.'
        $taskSchemaSql = Get-Content -LiteralPath $taskSchemaFile -Raw -Encoding utf8
        $taskSchemaApply = Invoke-DedicatedSql -Database $taskDatabase -Sql $taskSchemaSql -TimeoutSeconds 180
        if ($taskSchemaApply.ExitCode -ne 0) {
            throw 'Applying db/schema.sql failed (raw SQL errors are not printed). Check MySQL 8.4 compatibility.'
        }

        $taskTableCheck = Invoke-DedicatedSql -Database $taskDatabase `
            -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$taskDatabase' AND table_name = 'app_user';" -Scalar -TimeoutSeconds 30
        if ($taskTableCheck.ExitCode -ne 0 -or $taskTableCheck.Output.Trim() -ne '1') {
            throw 'The app_user table was not found after applying the schema.'
        }

        Write-Step 'Generating a runtime BCrypt hash and applying the fixture.'
        $taskPasswordHash = New-BcryptHash -Password $taskLoginPassword
        Add-SecretValue $taskPasswordHash
        # The hash uses only [./A-Za-z0-9$], so single quotes delimit it safely (enforced above).
        $taskFixtureSql = Get-Content -LiteralPath $taskFixtureFile -Raw -Encoding utf8
        $taskSeedSql = "SET @withdrawal_password_hash = '$taskPasswordHash';" + [Environment]::NewLine + $taskFixtureSql
        $taskSeed = Invoke-DedicatedSql -Database $taskDatabase -Sql $taskSeedSql -TimeoutSeconds 180
        if ($taskSeed.ExitCode -ne 0) {
            throw 'Applying the fixture failed (raw SQL errors are not printed). Check scripts/fixtures/withdrawal-dummy.sql.'
        }

        $taskCount = Invoke-DedicatedSql -Database $taskDatabase -Sql 'SELECT COUNT(*) FROM app_user;' -Scalar -TimeoutSeconds 30
        if ($taskCount.ExitCode -ne 0) { throw 'Could not verify the fixture result.' }

        Set-WorkingEnvironment 'SPRING_DATASOURCE_URL' ("jdbc:mysql://$taskMySqlHost`:3306/$taskDatabase" +
            '?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false')
        Set-WorkingEnvironment 'SPRING_DATASOURCE_USERNAME' $taskDbUser
        Set-WorkingEnvironment 'SPRING_DATASOURCE_PASSWORD' $taskDbPassword
        # application.properties references ${DB_USERNAME}/${DB_PASSWORD}; fill those too so the
        # placeholders resolve even where relaxed binding is not applied.
        Set-WorkingEnvironment 'DB_USERNAME' $taskDbUser
        Set-WorkingEnvironment 'DB_PASSWORD' $taskDbPassword
        Set-WorkingEnvironment 'SPRING_SQL_INIT_MODE' 'never'
        # Hyphenated property names bind ambiguously through environment variables, so they are
        # passed as JVM system properties. The Dockerfile ENTRYPOINT is
        # "java $JAVA_OPTS -jar app.jar", so this value takes effect.
        Set-WorkingEnvironment 'JAVA_OPTS' ('-Xms256m -Xmx512m' +
            ' -Dspoit.match-start-scheduler.enabled=false' +
            ' -Dspoit.security.remember-me-key=' + $taskRememberMeKey)

        Write-Step "Starting the app container, published only on 127.0.0.1:$Port."
        $taskAppRun = Invoke-Docker -Arguments @('run', '--detach', '--name', $taskResources.App,
            '--label', "$taskLabelMarker=1", '--label', "$taskLabelRun=$($taskResources.RunId)",
            '--label', "$taskLabelRole=app",
            '--network', $taskResources.Network,
            '--publish', "127.0.0.1:$Port`:8080",
            '--env', 'SPRING_DATASOURCE_URL', '--env', 'SPRING_DATASOURCE_USERNAME',
            '--env', 'SPRING_DATASOURCE_PASSWORD', '--env', 'DB_USERNAME', '--env', 'DB_PASSWORD',
            '--env', 'SPRING_SQL_INIT_MODE',
            '--env', 'JAVA_OPTS', '--env', 'TZ=Asia/Seoul',
            $AppImage) -TimeoutSeconds 120
        if ($taskAppRun.ExitCode -ne 0) {
            throw ('Failed to create the app container. Check whether 127.0.0.1:' + $Port + ' is in use. ' +
                (Protect-Text $taskAppRun.ErrorText.Trim()))
        }

        $taskAppDeadline = [DateTime]::UtcNow.AddSeconds(240)
        $taskAppReady = $false
        while ([DateTime]::UtcNow -lt $taskAppDeadline) {
            $taskAppState = (Invoke-Docker -Arguments @('container', 'inspect', '--format', '{{.State.Running}}', $taskResources.App)).Output.Trim()
            if ($taskAppState -ne 'true') {
                Show-ContainerLogTail -Name $taskResources.App
                throw 'The app container exited before becoming ready.'
            }
            if (Test-AppResponding -TaskPort $Port) {
                $taskAppReady = $true
                break
            }
            Start-Sleep -Seconds 2
        }
        if (-not $taskAppReady) {
            Show-ContainerLogTail -Name $taskResources.App
            throw "The app did not respond within the time limit. Check 127.0.0.1:$Port."
        }

        Write-Step ''
        Write-Step 'The dedicated server is ready. Existing containers and the development database were not touched.'
        Write-Step "  RunId       : $($taskResources.RunId)"
        Write-Step "  App URL     : http://127.0.0.1:$Port"
        Write-Step "  Login       : http://127.0.0.1:$Port/login"
        Write-Step "  Profile     : http://127.0.0.1:$Port/mypage/profile"
        Write-Step "  Withdrawal  : http://127.0.0.1:$Port/mypage/profile (POST /mypage/withdrawal-requests)"
        Write-Step "  MySQL       : $($taskResources.MySql) (reachable only on network $($taskResources.Network))"
        Write-Step "  Database    : $taskDatabase ($($taskCount.Output.Trim()) app_user rows)"
        $taskEmails = Get-FixtureEmails -Database $taskDatabase
        if ($taskEmails.Count -gt 0) {
            Write-Step "  Fixture accounts: $($taskEmails -join ', ')"
        } else {
            Write-Step '  Fixture accounts: no email found in the fixture.'
        }
        Write-Step '  Login password  : the injected environment variable value (not printed)'
        Write-Step ''
        Write-Step 'Clean up or recreate with this RunId only:'
        Write-Step "  powershell -ExecutionPolicy Bypass -File scripts/withdrawal-dummy.ps1 -Mode Stop  -RunId $($taskResources.RunId)"
        Write-Step "  powershell -ExecutionPolicy Bypass -File scripts/withdrawal-dummy.ps1 -Mode Reset -RunId $($taskResources.RunId)"
        Write-Step ''
        Write-Step 'Note: MySQL data lives on tmpfs, so Stop discards it.'
    } catch {
        # Roll back only what this run created, using the same label checks as Stop.
        Write-Warning 'Start failed; cleaning up only the dedicated resources this run created.'
        try { Stop-DummyServer -TaskRunId $taskResources.RunId -Quiet } catch {
            Write-Warning "Cleanup encountered a problem: $($_.Exception.Message)"
        }
        throw
    }
}

function Stop-DummyServer {
    param([string]$TaskRunId, [switch]$Quiet)
    $taskTarget = Get-RunResources -TaskRunId $TaskRunId
    $taskRemoved = 0

    foreach ($taskTargetContainer in @(
            [pscustomobject]@{ Name = $taskTarget.App; Role = 'app' },
            [pscustomobject]@{ Name = $taskTarget.MySql; Role = 'mysql' })) {
        if (-not (Test-DedicatedContainer -Name $taskTargetContainer.Name)) { continue }
        Assert-DedicatedContainer -Name $taskTargetContainer.Name -TaskRunId $TaskRunId -Role $taskTargetContainer.Role
        $taskRemove = Invoke-Docker -Arguments @('rm', '--force', $taskTargetContainer.Name) -TimeoutSeconds 120
        if ($taskRemove.ExitCode -ne 0) {
            throw ('Failed to remove the dedicated container: ' + $taskTargetContainer.Name + ' ' +
                (Protect-Text $taskRemove.ErrorText.Trim()))
        }
        $taskRemoved++
        if (-not $Quiet) { Write-Step "Removed dedicated container: $($taskTargetContainer.Name)" }
    }

    if (Test-DedicatedNetwork -Name $taskTarget.Network) {
        Assert-DedicatedNetwork -Name $taskTarget.Network -TaskRunId $TaskRunId
        $taskNetworkRemove = Invoke-Docker -Arguments @('network', 'rm', $taskTarget.Network) -TimeoutSeconds 60
        if ($taskNetworkRemove.ExitCode -ne 0) {
            throw ('Failed to remove the dedicated network. Check for leftover containers: ' +
                (Protect-Text $taskNetworkRemove.ErrorText.Trim()))
        }
        $taskRemoved++
        if (-not $Quiet) { Write-Step "Removed dedicated network: $($taskTarget.Network)" }
    }

    if ($taskRemoved -eq 0 -and -not $Quiet) {
        Write-Step "No dedicated resources to remove (RunId: $TaskRunId)."
    }
}

function Show-DummyStatus {
    param([string]$TaskRunId)
    $taskTarget = Get-RunResources -TaskRunId $TaskRunId
    Write-Step "RunId: $TaskRunId"
    foreach ($taskTargetContainer in @(
            [pscustomobject]@{ Name = $taskTarget.App; Role = 'app' },
            [pscustomobject]@{ Name = $taskTarget.MySql; Role = 'mysql' })) {
        if (-not (Test-DedicatedContainer -Name $taskTargetContainer.Name)) {
            Write-Step "  $($taskTargetContainer.Name): absent"
            continue
        }
        Assert-DedicatedContainer -Name $taskTargetContainer.Name -TaskRunId $TaskRunId -Role $taskTargetContainer.Role
        $taskState = (Invoke-Docker -Arguments @('container', 'inspect', '--format', '{{.State.Status}}', $taskTargetContainer.Name)).Output.Trim()
        Write-Step "  $($taskTargetContainer.Name): $taskState"
    }
    if (Test-DedicatedNetwork -Name $taskTarget.Network) {
        Assert-DedicatedNetwork -Name $taskTarget.Network -TaskRunId $TaskRunId
        Write-Step "  $($taskTarget.Network): present"
    } else {
        Write-Step "  $($taskTarget.Network): absent"
    }
    Write-Step "  App URL: http://127.0.0.1:$Port (answers only while running)"
}

if ($Mode -ne 'Start' -and [string]::IsNullOrWhiteSpace($RunId)) {
    throw "-Mode $Mode requires an explicit -RunId. Reuse the RunId printed by Start."
}
if (-not [string]::IsNullOrWhiteSpace($RunId) -and $RunId -cnotmatch '^[a-z0-9]{4,24}$') {
    throw '-RunId must be 4-24 characters of lowercase letters and digits.'
}
if ([string]::IsNullOrWhiteSpace($RunId)) { $RunId = New-RandomRunId }

try {
    switch ($Mode) {
        'Start' {
            Start-DummyServer
        }
        'Stop' {
            Stop-DummyServer -TaskRunId $RunId
        }
        'Reset' {
            Stop-DummyServer -TaskRunId $RunId
            Start-DummyServer
        }
        'Status' {
            Show-DummyStatus -TaskRunId $RunId
        }
    }
} finally {
    Restore-WorkingEnvironment
}
