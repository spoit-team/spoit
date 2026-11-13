#Requires -Version 5.1
<#
.SYNOPSIS
개발 MySQL과 분리된 임시 MySQL 서버에서 Gradle 빌드·테스트를 실행한다.
.DESCRIPTION
로컬 MySQL 8.4 바이너리가 있으면 기존과 같이 임시 데이터 디렉터리의 서버를 사용한다.
바이너리가 없으면 Docker CLI·엔진·이미지를 확인하고 일회용 컨테이너로 같은 검증을 수행한다.
두 경로 모두 개발 DB와 개발 자격증명에 접근하지 않는다.
.PARAMETER MySqlImage
Docker 경로에서만 사용하는 이미지. CI(.github/workflows/ci.yml)와 맞춰 mysql:8.4.11이 기본값이다.
이미지는 자동으로 내려받지 않는다.
.EXAMPLE
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/test-mat-mysql.ps1
.EXAMPLE
& .\scripts\test-mat-mysql.ps1 -GradleArgs @('test', '--tests', 'co.kr.spoit.mat.mapper.*')
.EXAMPLE
& .\scripts\test-mat-mysql.ps1 -UseDocker -MySqlImage 'mysql:8.4.11'
#>
param(
    [string]$MySqlBin = 'C:\Program Files\MySQL\MySQL Server 8.4\bin',
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$MySqlImage = 'mysql:8.4.11',
    [switch]$UseDocker,
    [string[]]$GradleArgs = @('clean', 'build', '--no-daemon', '--console=plain')
)

$ErrorActionPreference = 'Stop'
$taskRepo = Split-Path -Parent $PSScriptRoot
$taskDatabase = 'spoit_mat_test'
# 이번 실행이 만든 컨테이너만 식별하기 위한 라벨이다.
$taskLabelMarker = 'spoit.mat-test'
$taskLabelRun = 'spoit.mat-test.run'

$taskMySql = Join-Path $MySqlBin 'mysql.exe'
$taskServerExe = Join-Path $MySqlBin 'mysqld.exe'
$taskAdmin = Join-Path $MySqlBin 'mysqladmin.exe'
$taskLocalMySql = -not $UseDocker.IsPresent
foreach ($taskFile in @($taskMySql, $taskServerExe, $taskAdmin)) {
    if (-not (Test-Path -LiteralPath $taskFile -PathType Leaf)) { $taskLocalMySql = $false }
}
foreach ($taskFile in @((Join-Path $taskRepo 'gradlew.bat'), (Join-Path $taskRepo 'db/schema.sql'))) {
    if (-not (Test-Path -LiteralPath $taskFile -PathType Leaf)) {
        throw "필요한 파일이 없습니다: $taskFile"
    }
}
if ([string]::IsNullOrWhiteSpace($JavaHome) -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'JDK 17 경로를 -JavaHome 또는 JAVA_HOME으로 지정하세요.'
}

function ConvertTo-WindowsProcessArgument {
    param([string]$Value)
    if ($null -eq $Value) { $Value = '' }
    if ($Value.Length -eq 0) { return '""' }
    if ($Value -notmatch '[\s"]') { return $Value }

    # ProcessStartInfo.ArgumentList is unavailable in Windows PowerShell 5.1/.NET Framework.
    # Quote each argument using the Windows command-line parsing rules before joining them.
    $taskBuilder = New-Object System.Text.StringBuilder
    [void]$taskBuilder.Append('"')
    $taskBackslashes = 0
    foreach ($taskChar in $Value.ToCharArray()) {
        if ($taskChar -eq [char]92) {
            $taskBackslashes++
            continue
        }
        if ($taskChar -eq [char]34) {
            if ($taskBackslashes -gt 0) {
                [void]$taskBuilder.Append([string]::new([char]92, $taskBackslashes * 2))
            }
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
    if ($taskBackslashes -gt 0) {
        [void]$taskBuilder.Append([string]::new([char]92, $taskBackslashes * 2))
    }
    [void]$taskBuilder.Append('"')
    return $taskBuilder.ToString()
}

function ConvertTo-WindowsProcessArguments {
    param([string[]]$Arguments)
    $taskEscapedArguments = New-Object 'System.Collections.Generic.List[string]'
    foreach ($taskArgument in $Arguments) {
        $taskEscapedArguments.Add((ConvertTo-WindowsProcessArgument -Value $taskArgument))
    }
    return [string]::Join(' ', $taskEscapedArguments.ToArray())
}

# MySQL 클라이언트나 docker CLI를 실행하고 출력은 메모리에만 남긴다.
# 인증값은 명령줄이 아니라 자식 프로세스 환경변수로만 넘긴다.
function Invoke-TestProcess {
    param(
        [string]$File,
        [string[]]$Arguments,
        [string]$InputSql = '',
        [hashtable]$Environment = @{},
        [int]$TimeoutSeconds = 120
    )
    $taskStart = New-Object System.Diagnostics.ProcessStartInfo
    $taskStart.FileName = $File
    $taskStart.UseShellExecute = $false
    $taskStart.CreateNoWindow = $true
    $taskStart.RedirectStandardInput = $true
    $taskStart.RedirectStandardOutput = $true
    $taskStart.RedirectStandardError = $true
    $taskStart.StandardOutputEncoding = New-Object System.Text.UTF8Encoding($false)
    $taskStart.StandardErrorEncoding = New-Object System.Text.UTF8Encoding($false)
    $taskStart.Arguments = ConvertTo-WindowsProcessArguments -Arguments $Arguments
    # 호출자의 MySQL 인증 설정을 상속하지 않는다.
    $taskEnvironment = $taskStart.EnvironmentVariables
    $taskEnvironment.Remove('MYSQL_PWD')
    foreach ($taskKey in $Environment.Keys) { $taskEnvironment[$taskKey] = [string]$Environment[$taskKey] }
    $taskProcess = New-Object System.Diagnostics.Process
    $taskProcess.StartInfo = $taskStart
    $taskProcess.Start() | Out-Null
    $taskOutput = $taskProcess.StandardOutput.ReadToEndAsync()
    $taskError = $taskProcess.StandardError.ReadToEndAsync()
    if ($InputSql) {
        # Windows PowerShell 5.1 uses the active code page for StandardInput.Write.
        # Write explicit UTF-8 bytes so Korean schema/fixture text is not re-encoded.
        $taskInputBytes = [Text.Encoding]::UTF8.GetBytes($InputSql)
        $taskProcess.StandardInput.BaseStream.Write($taskInputBytes, 0, $taskInputBytes.Length)
        $taskProcess.StandardInput.BaseStream.Flush()
    }
    $taskProcess.StandardInput.Close()
    if (-not $taskProcess.WaitForExit($TimeoutSeconds * 1000)) {
        $taskProcess.Kill()
        $taskProcess.WaitForExit()
        throw '임시 MySQL 명령이 제한 시간 내에 종료되지 않았습니다.'
    }
    $taskResult = [PSCustomObject]@{
        ExitCode = $taskProcess.ExitCode
        Output = $taskOutput.GetAwaiter().GetResult()
        ErrorOutput = $taskError.GetAwaiter().GetResult()
    }
    $taskProcess.Dispose()
    return $taskResult
}

function Get-FreeLoopbackPort {
    $taskListener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    try {
        $taskListener.Start()
        return $taskListener.LocalEndpoint.Port
    } finally {
        $taskListener.Stop()
    }
}

# docker inspect --format '{{json .Config.Labels}}' 결과에서 점이 포함된 라벨을 읽는다.
function Get-LabelValue {
    param($Labels, [string]$Key)
    if ($null -eq $Labels) { return '' }
    $taskProperty = $Labels.PSObject.Properties[$Key]
    if ($null -eq $taskProperty -or $null -eq $taskProperty.Value) { return '' }
    return [string]$taskProperty.Value
}

function Get-DockerEngineVersion {
    try {
        $taskResult = Invoke-TestProcess -File 'docker' -Arguments @('version', '--format', '{{.Server.Version}}') -TimeoutSeconds 60
    } catch {
        return $null
    }
    if ($taskResult.ExitCode -ne 0) { return $null }
    return $taskResult.Output.Trim()
}

$taskRunId = [Guid]::NewGuid().ToString('N')
$taskContainer = "spoit-mat-mysql-$taskRunId"
$taskTempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$taskRunDir = $null
$taskDataDir = $null
$taskServer = $null
$taskServerStarted = $false
$taskContainerRunAttempted = $false
$taskGradleExit = 1
$taskPort = 0
$taskRootPassword = [Guid]::NewGuid().ToString('N')
$taskPassword = [Guid]::NewGuid().ToString('N')
$taskEnvNames = @('JAVA_HOME', 'MAT_TEST_DB_URL', 'MAT_TEST_DB_USERNAME', 'MAT_TEST_DB_PASSWORD',
    'SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD',
    'SPRING_DATASOURCE_DRIVER_CLASS_NAME', 'SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_IMPORT',
    'SPRING_PROFILES_ACTIVE', 'SPRING_SQL_INIT_MODE')
$taskSavedEnv = @{}
foreach ($taskName in $taskEnvNames) { $taskSavedEnv[$taskName] = [Environment]::GetEnvironmentVariable($taskName, 'Process') }

try {
    if ($taskLocalMySql) {
        $taskRunDir = Join-Path $taskTempRoot ('spoit-mat-mysql-' + $taskRunId)
        $taskDataDir = Join-Path $taskRunDir 'data'
        New-Item -ItemType Directory -Path $taskRunDir | Out-Null

        $taskPort = Get-FreeLoopbackPort

        Write-Host '새 임시 데이터 디렉터리에 MySQL을 초기화합니다.'
        $taskInit = Invoke-TestProcess -File $taskServerExe -Arguments @('--no-defaults', '--initialize-insecure', "--datadir=$taskDataDir")
        if ($taskInit.ExitCode -ne 0) {
            throw ('임시 MySQL 초기화 실패. MySQL 설치 및 임시 디렉터리 쓰기 권한을 확인하세요. ' +
                "로컬 바이너리를 쓰지 않으려면 -UseDocker로 Docker 경로를 사용할 수 있습니다.")
        }

        $taskStart = New-Object System.Diagnostics.ProcessStartInfo
        $taskStart.FileName = $taskServerExe
        $taskStart.UseShellExecute = $false
        $taskStart.CreateNoWindow = $true
        $taskStart.Arguments = ConvertTo-WindowsProcessArguments -Arguments @(
            '--no-defaults', "--datadir=$taskDataDir", '--bind-address=127.0.0.1',
            "--port=$taskPort", '--mysqlx=OFF', "--log-error=$(Join-Path $taskRunDir 'mysql.log')")
        $taskServer = New-Object System.Diagnostics.Process
        $taskServer.StartInfo = $taskStart
        $taskServer.Start() | Out-Null
        $taskServerStarted = $true

        $taskClientArgs = @('--no-defaults', '--no-login-paths', '--protocol=TCP', '--host=127.0.0.1',
            "--port=$taskPort", '--user=root', '--connect-timeout=2', '--default-character-set=utf8mb4', '--batch', '--skip-column-names')
        $taskDeadline = [DateTime]::UtcNow.AddSeconds(60)
        $taskReady = $false
        do {
            if ($taskServer.HasExited) { throw '임시 MySQL 서버가 준비 전에 종료되었습니다.' }
            $taskProbe = Invoke-TestProcess -File $taskMySql -Arguments $taskClientArgs -InputSql 'SELECT @@datadir;'
            if ($taskProbe.ExitCode -eq 0) {
                # 포트가 다른 프로세스에 선점되었어도 기존 DB에는 DDL을 실행하지 않는다.
                $taskActualDir = [IO.Path]::GetFullPath($taskProbe.Output.Trim()).TrimEnd([char[]]@('\', '/'))
                if ($taskActualDir -ne [IO.Path]::GetFullPath($taskDataDir).TrimEnd([char[]]@('\', '/'))) {
                    throw '임시 서버의 데이터 디렉터리가 일치하지 않아 테스트 DB 초기화를 거부합니다.'
                }
                $taskReady = $true
                break
            }
            Start-Sleep -Milliseconds 500
        } while ([DateTime]::UtcNow -lt $taskDeadline)
        if (-not $taskReady) { throw '임시 MySQL 서버 시작 시간이 초과되었습니다.' }

        $taskSchema = Get-Content -LiteralPath (Join-Path $taskRepo 'db/schema.sql') -Raw -Encoding utf8
        $taskBootstrap = "CREATE DATABASE $taskDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; USE $taskDatabase;" +
            [Environment]::NewLine + $taskSchema + [Environment]::NewLine +
            "CREATE USER '$taskDatabase'@'localhost' IDENTIFIED BY '$taskPassword';" +
            "GRANT SELECT, INSERT, UPDATE, DELETE ON $taskDatabase.* TO '$taskDatabase'@'localhost';" +
            "ALTER USER 'root'@'localhost' IDENTIFIED BY '$taskRootPassword';"
        $taskBootstrapResult = Invoke-TestProcess -File $taskMySql -Arguments $taskClientArgs -InputSql $taskBootstrap
        if ($taskBootstrapResult.ExitCode -ne 0) {
            # SQL에 임시 인증값이 있으므로 클라이언트 원문 오류를 출력하지 않는다.
            throw '전용 테스트 스키마 초기화 실패. db/schema.sql과 MySQL 8.4 호환성을 확인하세요.'
        }
        $taskVersion = Invoke-TestProcess -File $taskMySql -Arguments $taskClientArgs `
            -Environment @{ MYSQL_PWD = $taskRootPassword } -InputSql 'SELECT VERSION();'
        if ($taskVersion.ExitCode -ne 0) { throw '임시 MySQL 인증 확인에 실패했습니다.' }
        Write-Host ("전용 MySQL {0}, DB {1}, 포트 {2}" -f $taskVersion.Output.Trim(), $taskDatabase, $taskPort)
    } else {
        $taskDockerCli = Get-Command -Name 'docker' -CommandType Application -ErrorAction SilentlyContinue
        if ($null -eq $taskDockerCli) {
            throw ("로컬 MySQL 바이너리(-MySqlBin: $MySqlBin)와 Docker CLI를 모두 사용할 수 없습니다. " +
                'MySQL Server 8.4를 설치하거나 Docker Desktop 설치 후 ' +
                "'docker pull $MySqlImage'로 이미지를 준비하세요.")
        }
        $taskEngineVersion = Get-DockerEngineVersion
        if ($null -eq $taskEngineVersion) {
            throw ('Docker 엔진에 연결할 수 없습니다. Docker Desktop을 실행하거나 ' +
                "MySQL Server 8.4 바이너리를 -MySqlBin으로 지정하세요. 예: -MySqlBin '$MySqlBin'")
        }
        $taskImageCheck = Invoke-TestProcess -File 'docker' -Arguments @('image', 'inspect', '--format', '{{.Id}}', $MySqlImage) -TimeoutSeconds 60
        if ($taskImageCheck.ExitCode -ne 0) {
            # 예상치 못한 네트워크 사용을 막기 위해 이미지를 자동으로 받지 않는다.
            throw "Docker 이미지 '$MySqlImage'가 없습니다. 'docker pull $MySqlImage'로 준비한 뒤 다시 실행하세요."
        }

        $taskPort = Get-FreeLoopbackPort
        if ($UseDocker.IsPresent) {
            Write-Host ("-UseDocker 지정에 따라 Docker {0}의 일회용 MySQL {1} 컨테이너로 검증합니다." -f $taskEngineVersion, $MySqlImage)
        } else {
            Write-Host ("로컬 MySQL 바이너리가 없어 Docker {0}의 일회용 MySQL {1} 컨테이너로 검증합니다." -f $taskEngineVersion, $MySqlImage)
        }
        $taskContainerRunAttempted = $true
        $taskRun = Invoke-TestProcess -File 'docker' -Arguments @(
            'run', '--detach', '--name', $taskContainer,
            '--label', "$taskLabelMarker=1", '--label', "$taskLabelRun=$taskRunId",
            '--publish', "127.0.0.1:${taskPort}:3306",
            # 데이터는 컨테이너 안 tmpfs에만 두고 호스트 볼륨을 쓰지 않는다.
            '--tmpfs', '/var/lib/mysql:rw,size=1g',
            '--env', 'MYSQL_ROOT_PASSWORD',
            # root@'%' 계정을 만들지 않는다(기본값은 %). 호스트에는 테스트 계정만 노출한다.
            '--env', 'MYSQL_ROOT_HOST=localhost',
            $MySqlImage,
            '--character-set-server=utf8mb4', '--collation-server=utf8mb4_0900_ai_ci') `
            -Environment @{ MYSQL_ROOT_PASSWORD = $taskRootPassword } -TimeoutSeconds 180
        if ($taskRun.ExitCode -ne 0) {
            throw ('일회용 Docker MySQL 컨테이너 생성에 실패했습니다. ' +
                "127.0.0.1:$taskPort 포트 사용 여부와 Docker 엔진 상태를 확인하세요.")
        }

        # tmpfs만 쓰고 호스트 마운트가 없는지 확인한다. Mounts가 []가 아니면 데이터가 남을 수 있다.
        $taskInspect = Invoke-TestProcess -File 'docker' -Arguments @('container', 'inspect', '--format', '{{json .Mounts}}', $taskContainer) -TimeoutSeconds 60
        if ($taskInspect.ExitCode -ne 0 -or $taskInspect.Output.Trim() -ne '[]') {
            throw '일회용 컨테이너에 데이터 마운트가 있어 안전을 위해 중단합니다.'
        }
        $taskPublish = Invoke-TestProcess -File 'docker' -Arguments @('port', $taskContainer, '3306/tcp') -TimeoutSeconds 60
        $taskPublishLines = @($taskPublish.Output -split "`r?`n" | Where-Object { $_.Trim() } | ForEach-Object { $_.Trim() })
        if ($taskPublish.ExitCode -ne 0 -or -not ($taskPublishLines -contains "127.0.0.1:$taskPort")) {
            throw '임시 컨테이너가 127.0.0.1에만 게시되지 않아 테스트를 중단합니다.'
        }

        Write-Host '일회용 컨테이너의 MySQL이 준비되기를 기다립니다.'
        $taskDeadline = [DateTime]::UtcNow.AddSeconds(180)
        $taskReady = $false
        do {
            $taskState = Invoke-TestProcess -File 'docker' -Arguments @('container', 'inspect', '--format', '{{.State.Running}}', $taskContainer) -TimeoutSeconds 60
            if ($taskState.ExitCode -ne 0 -or $taskState.Output.Trim() -ne 'true') {
                throw '일회용 MySQL 컨테이너가 준비 전에 종료되었습니다.'
            }
            # 이미지 엔트리포인트는 초기화 중 소켓만 여는 임시 서버(--skip-networking)를 띄운다.
            # 그때 스키마를 적용하면 곧 재시작되며 사라지므로, 네트워킹이 켜진 최종 서버만 준비로 본다.
            # root@'%'를 만들지 않았으므로 root는 컨테이너 소켓으로만 접속한다.
            $taskProbe = Invoke-TestProcess -File 'docker' -Arguments @('exec', '--interactive', '--env', 'MYSQL_PWD', $taskContainer,
                'mysql', '--user=root', '--connect-timeout=2', '--default-character-set=utf8mb4', '--batch', '--skip-column-names') `
                -InputSql 'SELECT @@skip_networking, @@datadir;' -Environment @{ MYSQL_PWD = $taskRootPassword } -TimeoutSeconds 60
            if ($taskProbe.ExitCode -eq 0) {
                $taskProbeFields = @($taskProbe.Output.Trim() -split "\t")
                # 이번 컨테이너의 최종 데이터 디렉터리인지 확인한 뒤에만 DDL을 실행한다.
                if ($taskProbeFields.Count -lt 2 -or
                    $taskProbeFields[1].Trim().TrimEnd([char[]]@('/')) -ne '/var/lib/mysql') {
                    throw '임시 컨테이너의 데이터 디렉터리가 일치하지 않아 테스트 DB 초기화를 거부합니다.'
                }
                if ($taskProbeFields[0].Trim() -eq '0') {
                    $taskReady = $true
                    break
                }
            }
            Start-Sleep -Milliseconds 1000
        } while ([DateTime]::UtcNow -lt $taskDeadline)
        if (-not $taskReady) { throw '일회용 Docker MySQL 서버 시작 시간이 초과되었습니다.' }

        # 스키마와 계정 초기화는 컨테이너 내부(root 소켓 접속)에서만 수행한다.
        $taskSchema = Get-Content -LiteralPath (Join-Path $taskRepo 'db/schema.sql') -Raw -Encoding utf8
        $taskBootstrap = "CREATE DATABASE IF NOT EXISTS $taskDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" +
            "USE $taskDatabase;" + [Environment]::NewLine + $taskSchema + [Environment]::NewLine +
            "CREATE USER '$taskDatabase'@'%' IDENTIFIED BY '$taskPassword';" +
            "GRANT SELECT, INSERT, UPDATE, DELETE ON $taskDatabase.* TO '$taskDatabase'@'%';"
        $taskBootstrapResult = Invoke-TestProcess -File 'docker' -Arguments @('exec', '--interactive', '--env', 'MYSQL_PWD', $taskContainer,
            'mysql', '--user=root', '--default-character-set=utf8mb4', '--batch') `
            -InputSql $taskBootstrap -Environment @{ MYSQL_PWD = $taskRootPassword } -TimeoutSeconds 180
        if ($taskBootstrapResult.ExitCode -ne 0) {
            # SQL에 임시 인증값이 있으므로 클라이언트 원문 오류를 출력하지 않는다.
            throw '전용 테스트 스키마 초기화 실패. db/schema.sql과 MySQL 8.4 호환성을 확인하세요.'
        }
        $taskVersion = Invoke-TestProcess -File 'docker' -Arguments @('exec', '--interactive', '--env', 'MYSQL_PWD', $taskContainer,
            'mysql', '--protocol=TCP', '--host=127.0.0.1', '--port=3306', '--user', $taskDatabase, "--database=$taskDatabase",
            '--default-character-set=utf8mb4', '--batch', '--skip-column-names') `
            -InputSql 'SELECT VERSION();' -Environment @{ MYSQL_PWD = $taskPassword } -TimeoutSeconds 60
        if ($taskVersion.ExitCode -ne 0) { throw '임시 MySQL 인증 확인에 실패했습니다.' }
        # 호스트에서 게시 포트로 접속하면 서버는 이를 127.0.0.1로 보고 localhost 계정과 매칭한다.
        # root 계정을 모두 잠가 호스트에서 root로 들어올 수 없게 한다(로컬 root는 먼저 잠근다).
        $taskRootHosts = Invoke-TestProcess -File 'docker' -Arguments @('exec', '--interactive', '--env', 'MYSQL_PWD', $taskContainer,
            'mysql', '--user=root', '--batch', '--skip-column-names') `
            -InputSql "SELECT host FROM mysql.user WHERE user = 'root' AND account_locked = 'N';" -Environment @{ MYSQL_PWD = $taskRootPassword } -TimeoutSeconds 60
        if ($taskRootHosts.ExitCode -ne 0) { throw '임시 root 계정 조회에 실패했습니다.' }
        $taskRootHostList = @($taskRootHosts.Output -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        # localhost를 먼저 잠그면 세션이 끊기므로 원격 계정을 먼저 잠근다.
        $taskLockTargets = @($taskRootHostList | Where-Object { $_ -ne 'localhost' }) + @($taskRootHostList | Where-Object { $_ -eq 'localhost' })
        if ($taskLockTargets.Count -gt 0) {
            # 호스트 값은 서버가 준 식별자다. 작은따옴표만 이스케이프해 SQL 리터럴로 안전하게 넣는다.
            $taskLockSql = @($taskLockTargets | ForEach-Object { "ALTER USER 'root'@'$($_.Replace("'", "''"))' ACCOUNT LOCK;" }) -join [Environment]::NewLine
            $taskLockRoot = Invoke-TestProcess -File 'docker' -Arguments @('exec', '--interactive', '--env', 'MYSQL_PWD', $taskContainer,
                'mysql', '--user=root', '--batch') `
                -InputSql $taskLockSql -Environment @{ MYSQL_PWD = $taskRootPassword } -TimeoutSeconds 60
            if ($taskLockRoot.ExitCode -ne 0) { throw '임시 root 계정 잠금에 실패했습니다.' }
        }
        Write-Host ("전용 MySQL {0}, DB {1}, 127.0.0.1:{2} (컨테이너 {3})" -f $taskVersion.Output.Trim(), $taskDatabase, $taskPort, $taskContainer)
    }

    $env:JAVA_HOME = $JavaHome
    $env:MAT_TEST_DB_URL = "jdbc:mysql://127.0.0.1:$taskPort/$taskDatabase"
    $env:MAT_TEST_DB_USERNAME = $taskDatabase
    $env:MAT_TEST_DB_PASSWORD = $taskPassword
    # 기존 SpringBootTest도 같은 임시 DB를 사용하도록 실행 프로세스에만 설정한다.
    $env:SPRING_DATASOURCE_URL = $env:MAT_TEST_DB_URL + '?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false'
    $env:SPRING_DATASOURCE_USERNAME = $env:MAT_TEST_DB_USERNAME
    $env:SPRING_DATASOURCE_PASSWORD = $taskPassword
    $env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'com.mysql.cj.jdbc.Driver'
    $env:SPRING_CONFIG_LOCATION = 'classpath:/application.properties'
    $env:SPRING_CONFIG_IMPORT = 'optional:classpath:/mat/no-local-config.properties'
    $env:SPRING_PROFILES_ACTIVE = 'mat-test'
    $env:SPRING_SQL_INIT_MODE = 'never'
    Push-Location $taskRepo
    try {
        & (Join-Path $taskRepo 'gradlew.bat') @GradleArgs
        $taskGradleExit = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($taskGradleExit -ne 0) { throw "Gradle 검증 실패 (종료 코드 $taskGradleExit). build/reports를 확인하세요." }
} finally {
    foreach ($taskName in $taskEnvNames) {
        if ($null -eq $taskSavedEnv[$taskName]) {
            Remove-Item -LiteralPath "Env:\$taskName" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($taskName, $taskSavedEnv[$taskName], 'Process')
        }
    }
    if ($taskServerStarted -and $taskServer -and -not $taskServer.HasExited) {
        try {
            $taskShutdown = Invoke-TestProcess -File $taskAdmin -Arguments @('--no-defaults', '--no-login-paths',
                '--protocol=TCP', '--host=127.0.0.1', "--port=$taskPort", '--user=root', '--connect-timeout=2', 'shutdown') `
                -Environment @{ MYSQL_PWD = $taskRootPassword }
        } catch {
            Write-Warning 'MySQL 종료 요청에 실패하여 이번 실행의 임시 프로세스를 직접 정리합니다.'
        } finally {
            if (-not $taskServer.WaitForExit(10000)) {
                # 이 스크립트가 시작한 프로세스만 종료한다.
                $taskServer.Kill()
                $taskServer.WaitForExit()
            }
        }
    }
    if ($taskServer) { $taskServer.Dispose() }
    if ($taskContainerRunAttempted) {
        $taskContainerRemoved = $false
        try {
            # 삭제 대상은 이번 실행이 만든 이름·라벨을 모두 만족하는 컨테이너로 한정한다.
            if ($taskContainer -cnotmatch '^spoit-mat-mysql-[0-9a-f]{32}$') {
                throw '컨테이너 이름 검증 실패로 자동 정리를 중단했습니다.'
            }
            # 먼저 컨테이너 존재 여부만 확인한다(안전한 Id 포맷). 없으면 정리할 대상도 없다.
            $taskExists = Invoke-TestProcess -File 'docker' -Arguments @('container', 'inspect', '--format', '{{.Id}}', $taskContainer) -TimeoutSeconds 60
            if ($taskExists.ExitCode -eq 0) {
                $taskInspect = Invoke-TestProcess -File 'docker' -Arguments @('container', 'inspect', '--format', '{{json .Config.Labels}}', $taskContainer) -TimeoutSeconds 60
                $taskLabels = $null
                if ($taskInspect.ExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($taskInspect.Output)) {
                    try { $taskLabels = $taskInspect.Output.Trim() | ConvertFrom-Json } catch { $taskLabels = $null }
                }
                if ((Get-LabelValue -Labels $taskLabels -Key $taskLabelMarker) -cne '1' -or
                    (Get-LabelValue -Labels $taskLabels -Key $taskLabelRun) -cne $taskRunId) {
                    throw "이번 실행이 만든 컨테이너가 아니므로 자동 정리를 중단했습니다: $taskContainer"
                }
                # 데이터는 tmpfs에만 있으므로 컨테이너와 함께 사라진다. 남은 볼륨도 함께 제거한다.
                $taskRemove = Invoke-TestProcess -File 'docker' -Arguments @('rm', '--force', '--volumes', $taskContainer) -TimeoutSeconds 120
                if ($taskRemove.ExitCode -ne 0) { throw 'Docker 컨테이너 제거 명령이 실패했습니다.' }
                $taskContainerRemoved = $true
            }
        } catch {
            Write-Warning ("임시 Docker 컨테이너 정리에 실패했습니다: $taskContainer - " + $_.Exception.Message)
        }
        if ($taskContainerRemoved) { Write-Host '임시 Docker MySQL 컨테이너를 정리했습니다.' }
    }
    if ($taskRunDir) {
        # 삭제 대상은 이번 실행에서 생성한 임시 폴더인지 절대 경로로 확인한다.
        $taskResolved = [IO.Path]::GetFullPath($taskRunDir)
        $taskAllowedPrefix = $taskTempRoot.TrimEnd([char[]]@('\', '/')) + [IO.Path]::DirectorySeparatorChar
        if (-not $taskResolved.StartsWith($taskAllowedPrefix, [StringComparison]::OrdinalIgnoreCase) -or
            [IO.Path]::GetFileName($taskResolved) -notmatch '^spoit-mat-mysql-[0-9a-f]{32}$') {
            throw '임시 디렉터리 경로 검증 실패로 자동 정리를 중단했습니다.'
        }
        if (Test-Path -LiteralPath $taskResolved) { Remove-Item -LiteralPath $taskResolved -Recurse -Force }
        Write-Host '임시 MySQL 서버와 데이터 디렉터리를 정리했습니다.'
    }
}
