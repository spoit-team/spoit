# VS Code Java 진단 오류 해결

이 안내는 VS Code의 Problems 패널에 Lombok의 필드 초기화 오류나 Thymeleaf 패키지 누락 오류가 표시되지만, Gradle 빌드는 정상인 경우를 다룹니다.

## 증상과 원인

다음과 같은 메시지는 Java 소스 오류가 아니라 VS Code Java 언어 서버가 Lombok을 처리하지 못하거나 Gradle 의존성을 가져오지 못할 때 나타날 수 있습니다.

- `variable ... not initialized in the default constructor`
- `Can't initialize javac processor`
- `ClassNotFoundException: com.sun.tools.javac.tree.EndPosTable`
- `package org.thymeleaf... does not exist`

예를 들어 Lombok의 `@Data`/`@Builder` 등이 생성하는 생성자나 접근자를 언어 서버가 인식하지 못하면 필드 미초기화·생성자 누락 진단이 연쇄적으로 표시됩니다. `org.thymeleaf` 패키지 오류도 IDE가 Gradle classpath를 가져오지 못한 경우일 수 있습니다.

이 프로젝트는 Java 17 Gradle toolchain을 사용하며, Thymeleaf는 `spring-boot-starter-thymeleaf`로 선언되어 있습니다. 먼저 저장소 루트에서 Gradle 빌드가 정상인지 확인하세요.

```powershell
.\gradlew.bat test
.\gradlew.bat dependencyInsight --dependency thymeleaf-spring6 --configuration runtimeClasspath
```

macOS/Linux에서는 `./gradlew test`를 사용합니다. Gradle 빌드도 실패한다면 IDE 설정만의 문제가 아니므로 빌드 오류부터 확인하세요.

## VS Code 설정

### 1. Java 확장 하나로 진단하기

이 프로젝트에서는 Microsoft의 **Language Support for Java by Red Hat** (`redhat.java`)와 **Gradle for Java** (`vscjava.vscode-gradle`) 조합을 권장합니다.

진단 메시지에 `org.netbeans...`가 포함되어 있으면 Oracle Java Platform Extension (`oracle.oracle-java`)의 NetBeans 언어 서버가 진단을 내고 있는지 확인하세요. Microsoft Java 확장과 동시에 활성화되어 있다면 Oracle 확장을 이 워크스페이스에서 비활성화합니다.

1. VS Code에서 `Ctrl+Shift+X`를 눌러 Extensions를 엽니다.
2. **Java Platform Extension for Visual Studio Code** (Oracle)를 찾습니다.
3. 톱니바퀴 메뉴에서 **Disable (Workspace)**를 선택합니다.

권장 확장이 설치되어 있지 않다면 `redhat.java`와 `vscjava.vscode-gradle`을 설치합니다. 확장 권장 목록은 자동으로 이미 설치된 확장을 비활성화하지 않으므로, Oracle 확장은 직접 꺼야 합니다.

### 2. JDK 역할을 분리하기

언어 서버가 실행되는 JDK와 프로젝트를 빌드하는 JDK는 다를 수 있습니다. 아래 경로는 예시입니다. 각자 설치된 JDK 경로로 바꾸세요.

- `java.jdt.ls.java.home`: VS Code Java 언어 서버용 JDK (사용 중인 `redhat.java`가 요구하는 버전, 예: JDK 21)
- `java.import.gradle.java.home`: Gradle 가져오기/데몬용 JDK (이 프로젝트는 JDK 17)
- `java.configuration.runtimes`: 프로젝트 Java 런타임 (이 프로젝트는 JDK 17)

워크스페이스의 `.vscode/settings.json`에 다음처럼 설정할 수 있습니다.

```json
{
    "java.jdt.ls.java.home": "C:\\path\\to\\jdk-21",
    "java.import.gradle.java.home": "C:\\path\\to\\jdk-17",
    "java.configuration.runtimes": [
        {
            "name": "JavaSE-17",
            "path": "C:\\path\\to\\jdk-17",
            "default": true
        }
    ],
    "java.configuration.updateBuildConfiguration": "automatic"
}
```

JDK 경로는 OS와 PC마다 다릅니다. `.vscode/`는 이 저장소의 `.gitignore` 대상이므로 이 설정 파일은 기본적으로 로컬 전용입니다. 다른 개발자도 각자의 설치 경로로 설정해야 합니다.

## 설정 적용 및 재검사

1. `Ctrl+Shift+P` → **Developer: Reload Window**
2. `Ctrl+Shift+P` → **Java: Clean Java Language Server Workspace**를 실행하고 재시작을 승인
3. Gradle 창에서 **Refresh Gradle Project**를 실행하거나 프로젝트를 다시 가져오기
4. `Ctrl+Shift+P` → **Java: Configure Java Runtime**에서 프로젝트 런타임이 Java 17인지 확인
5. Problems 패널에서 Lombok/Thymeleaf 오류가 사라졌는지 확인

그래도 남으면 저장소 루트(`build.gradle`이 있는 폴더)를 VS Code에서 열었는지 확인하고, Gradle 테스트 결과와 남은 오류의 전체 메시지를 함께 확인하세요.
