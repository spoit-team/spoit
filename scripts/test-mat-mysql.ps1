#Requires -Version 7.0
<#
.SYNOPSIS
개발 MySQL과 분리된 임시 MySQL 서버에서 Gradle 빌드·테스트를 실행한다.
.EXAMPLE
pwsh -File scripts/test-mat-mysql.ps1
.EXAMPLE
& ./scripts/test-mat-mysql.ps1 -GradleArgs @('test', '--tests', 'co.kr.spoit.mat.mapper.*')
#>
param(
    [string]$MySqlBin = 'C:\Program Files\MySQL\MySQL Server 8.4\bin',
    [string]$JavaHome = $env:JAVA_HOME,
    [string[]]$GradleArgs = @('clean', 'build', '--no-daemon', '--console=plain')
)

$ErrorActionPreference = 'Stop'
$taskRepo = Split-Path -Parent $PSScriptRoot
$taskMySql = Join-Path $MySqlBin 'mysql.exe'
$taskServerExe = Join-Path $MySqlBin 'mysqld.exe'
$taskAdmin = Join-Path $MySqlBin 'mysqladmin.exe'
foreach ($taskFile in @($taskMySql, $taskServerExe, $taskAdmin, (Join-Path $taskRepo 'gradlew.bat'), (Join-Path $taskRepo 'db/schema.sql'))) {
    if (-not (Test-Path -LiteralPath $taskFile -PathType Leaf)) {
        throw "필요한 파일이 없습니다: $taskFile"
    }
}
if ([string]::IsNullOrWhiteSpace($JavaHome) -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'JDK 17 경로를 -JavaHome 또는 JAVA_HOME으로 지정하세요.'
}

function Invoke-TestMySqlTool {
    param([string]$File, [string[]]$Arguments, [string]$InputSql = '', [string]$Password = '')
    $taskStart = [Diagnostics.ProcessStartInfo]::new()
    $taskStart.FileName = $File
    $taskStart.UseShellExecute = $false
    $taskStart.CreateNoWindow = $true
    $taskStart.RedirectStandardInput = $true
    $taskStart.RedirectStandardOutput = $true
    $taskStart.RedirectStandardError = $true
    $taskStart.StandardInputEncoding = [Text.UTF8Encoding]::new($false)
    $taskStart.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)
    $taskStart.StandardErrorEncoding = [Text.UTF8Encoding]::new($false)
    foreach ($taskArgument in $Arguments) { $taskStart.ArgumentList.Add($taskArgument) }
    # 호출자의 MySQL 인증 설정을 상속하지 않는다.
    $taskStart.Environment.Remove('MYSQL_PWD') | Out-Null
    if ($Password) { $taskStart.Environment['MYSQL_PWD'] = $Password }
    $taskProcess = [Diagnostics.Process]::new()
    $taskProcess.StartInfo = $taskStart
    $taskProcess.Start() | Out-Null
    $taskOutput = $taskProcess.StandardOutput.ReadToEndAsync()
    $taskError = $taskProcess.StandardError.ReadToEndAsync()
    if ($InputSql) { $taskProcess.StandardInput.Write($InputSql) }
    $taskProcess.StandardInput.Close()
    if (-not $taskProcess.WaitForExit(120000)) {
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

$taskTempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$taskRunDir = Join-Path $taskTempRoot ('spoit-mat-mysql-' + [Guid]::NewGuid().ToString('N'))
$taskDataDir = Join-Path $taskRunDir 'data'
$taskServer = $null
$taskServerStarted = $false
$taskGradleExit = 1
$taskRootPassword = [Guid]::NewGuid().ToString('N')
$taskPassword = [Guid]::NewGuid().ToString('N')
$taskEnvNames = @('JAVA_HOME', 'MAT_TEST_DB_URL', 'MAT_TEST_DB_USERNAME', 'MAT_TEST_DB_PASSWORD',
    'SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD',
    'SPRING_DATASOURCE_DRIVER_CLASS_NAME', 'SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_IMPORT',
    'SPRING_PROFILES_ACTIVE', 'SPRING_SQL_INIT_MODE')
$taskSavedEnv = @{}
foreach ($taskName in $taskEnvNames) { $taskSavedEnv[$taskName] = [Environment]::GetEnvironmentVariable($taskName, 'Process') }
New-Item -ItemType Directory -Path $taskRunDir | Out-Null

try {
    $taskListener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $taskListener.Start()
    $taskPort = $taskListener.LocalEndpoint.Port
    $taskListener.Stop()

    Write-Host '새 임시 데이터 디렉터리에 MySQL을 초기화합니다.'
    $taskInit = Invoke-TestMySqlTool -File $taskServerExe -Arguments @('--no-defaults', '--initialize-insecure', "--datadir=$taskDataDir")
    if ($taskInit.ExitCode -ne 0) {
        throw '임시 MySQL 초기화 실패. MySQL 설치 및 임시 디렉터리 쓰기 권한을 확인하세요.'
    }

    $taskStart = [Diagnostics.ProcessStartInfo]::new()
    $taskStart.FileName = $taskServerExe
    $taskStart.UseShellExecute = $false
    $taskStart.CreateNoWindow = $true
    foreach ($taskArgument in @('--no-defaults', "--datadir=$taskDataDir", '--bind-address=127.0.0.1',
            "--port=$taskPort", '--mysqlx=OFF', "--log-error=$(Join-Path $taskRunDir 'mysql.log')")) {
        $taskStart.ArgumentList.Add($taskArgument)
    }
    $taskServer = [Diagnostics.Process]::new()
    $taskServer.StartInfo = $taskStart
    $taskServer.Start() | Out-Null
    $taskServerStarted = $true

    $taskClientArgs = @('--no-defaults', '--no-login-paths', '--protocol=TCP', '--host=127.0.0.1',
        "--port=$taskPort", '--user=root', '--connect-timeout=2', '--default-character-set=utf8mb4', '--batch', '--skip-column-names')
    $taskDeadline = [DateTime]::UtcNow.AddSeconds(60)
    $taskReady = $false
    do {
        if ($taskServer.HasExited) { throw '임시 MySQL 서버가 준비 전에 종료되었습니다.' }
        $taskProbe = Invoke-TestMySqlTool -File $taskMySql -Arguments $taskClientArgs -InputSql 'SELECT @@datadir;'
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
    $taskBootstrap = "CREATE DATABASE spoit_mat_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; USE spoit_mat_test;" +
        [Environment]::NewLine + $taskSchema + [Environment]::NewLine +
        "CREATE USER 'spoit_mat_test'@'localhost' IDENTIFIED BY '$taskPassword';" +
        "GRANT SELECT, INSERT, UPDATE, DELETE ON spoit_mat_test.* TO 'spoit_mat_test'@'localhost';" +
        "ALTER USER 'root'@'localhost' IDENTIFIED BY '$taskRootPassword';"
    $taskBootstrapResult = Invoke-TestMySqlTool -File $taskMySql -Arguments $taskClientArgs -InputSql $taskBootstrap
    if ($taskBootstrapResult.ExitCode -ne 0) {
        # SQL에 임시 인증값이 있으므로 클라이언트 원문 오류를 출력하지 않는다.
        throw '전용 테스트 스키마 초기화 실패. db/schema.sql과 MySQL 8.4 호환성을 확인하세요.'
    }
    $taskVersion = Invoke-TestMySqlTool -File $taskMySql -Arguments $taskClientArgs -Password $taskRootPassword -InputSql 'SELECT VERSION();'
    if ($taskVersion.ExitCode -ne 0) { throw '임시 MySQL 인증 확인에 실패했습니다.' }
    Write-Host ("전용 MySQL {0}, DB spoit_mat_test, 포트 {1}" -f $taskVersion.Output.Trim(), $taskPort)

    $env:JAVA_HOME = $JavaHome
    $env:MAT_TEST_DB_URL = "jdbc:mysql://127.0.0.1:$taskPort/spoit_mat_test"
    $env:MAT_TEST_DB_USERNAME = 'spoit_mat_test'
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
    if ($taskServerStarted -and -not $taskServer.HasExited) {
        try {
            $taskShutdown = Invoke-TestMySqlTool -File $taskAdmin -Arguments @('--no-defaults', '--no-login-paths',
                '--protocol=TCP', '--host=127.0.0.1', "--port=$taskPort", '--user=root', '--connect-timeout=2', 'shutdown') -Password $taskRootPassword
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
