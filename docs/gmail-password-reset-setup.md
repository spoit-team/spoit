# Gmail API 비밀번호 재설정 실행 준비

이 문서는 이메일 인증번호를 이용한 비밀번호 재설정 기능을 실행하기 위한 설정을 설명합니다.

## 1. 기존 데이터베이스에 마이그레이션 적용

1. MySQL 관리 도구에서 애플리케이션이 사용할 데이터베이스를 선택합니다.
2. [비밀번호 재설정 마이그레이션 SQL](../../db/migrations/20261112-password-reset.sql)을 UTF-8로 열어 실행합니다.
3. `password_reset_challenge`와 `password_reset_rate_limit` 테이블이 생성됐는지 확인합니다.

마이그레이션은 `CREATE TABLE`을 사용하므로 두 테이블이 모두 없는 기존 데이터베이스에 한 번만 적용합니다. 이미 두 테이블이 생성된 신규 스키마에는 다시 적용하지 않습니다. 기존 데이터베이스에는 초기화용 `db/schema.sql`을 실행하지 않습니다.

## 2. Gmail 환경변수 설정

애플리케이션을 실행하는 OS, IDE 실행 설정 또는 CI의 비밀값 관리 기능에 다음 값을 주입합니다. 설정 항목은 [application.properties](../../src/main/resources/application.properties)에서도 확인할 수 있습니다.

| 환경변수 | 설정할 값 |
| --- | --- |
| `GMAIL_CLIENT_ID` | Gmail API를 사용하는 Google OAuth 클라이언트 ID |
| `GMAIL_CLIENT_SECRET` | 해당 OAuth 클라이언트의 비밀키 |
| `GMAIL_REFRESH_TOKEN` | Gmail 발송 권한을 승인받은 계정의 리프레시 토큰 |
| `GMAIL_SENDER` | 위 리프레시 토큰을 승인한 Gmail 계정의 이메일 주소 |

`GMAIL_SENDER`에는 메일을 발송할 전용 계정 주소를 지정하고, 리프레시 토큰도 같은 계정으로 승인받아야 합니다. 클라이언트 비밀키와 리프레시 토큰은 안전한 비밀값 저장소에 보관하고 소스 코드나 문서에 기록하지 않습니다.

## 3. 인증번호 보호용 비밀키 설정

`PASSWORD_RESET_CODE_SECRET`은 인증번호와 재설정 권한을 보호하는 데 사용합니다. 암호학적으로 안전한 난수로 생성한 32바이트 이상의 비밀키를 환경변수로 주입합니다. 운영 환경에서는 안전한 비밀값 저장소에 보관한 동일한 값을 서버 재시작 후에도 사용합니다. 값을 변경하면 진행 중인 인증번호와 재설정 권한을 사용할 수 없게 됩니다.

프로젝트 루트에서 Windows PowerShell 5.1로 `.\scripts\generate-password-reset-code-secret.ps1`를 실행하면 암호학적으로 안전한 32바이트 키를 생성해 현재 프로세스에 설정합니다. 비밀값은 화면에 출력되지 않습니다. 직접 입력할 때는 아래 명령을 실행합니다.

```powershell
$taskKeyBytes = New-Object byte[] 32
$taskRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $taskRandom.GetBytes($taskKeyBytes)
    $env:PASSWORD_RESET_CODE_SECRET = [Convert]::ToBase64String($taskKeyBytes)
} finally {
    $taskRandom.Dispose()
}
```

이 예시로 설정한 값은 현재 PowerShell과 그 프로세스에서 시작한 애플리케이션에만 적용되며, 터미널을 닫으면 사라집니다. 이미 실행 중인 IDE에는 자동으로 적용되지 않으므로 IDE에서 서버를 실행한다면 실행 설정에 비밀키를 따로 주입합니다. 다른 환경변수도 서버가 시작되기 전에 설정하고, 설정을 변경한 뒤에는 서버를 다시 시작합니다.

## 4. 동작 확인

1. 로그인 화면에서 **비밀번호 찾기**를 선택합니다.
2. 가입된 이메일로 인증번호를 요청하고 수신한 6자리 번호를 입력합니다. 인증번호는 10분 동안 유효합니다.
3. 새 비밀번호를 설정하고 새 비밀번호로 로그인합니다.

실제 Gmail 메일 수신은 설정을 적용한 환경에서 직접 확인합니다.

서버는 리프레시 토큰으로 Gmail 액세스 토큰을 자동 갱신합니다. 다만 외부 OAuth 앱의 게시 상태가 Testing이면 Gmail 권한이 포함된 리프레시 토큰은 7일 후 만료됩니다. 자세한 내용은 [Google OAuth 토큰 만료 안내](https://developers.google.com/identity/protocols/oauth2#expiration)를 참고합니다.
