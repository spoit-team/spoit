# ============================================================
# 1단계: 빌드 (JDK + Gradle로 실행 가능한 jar 생성)
# ============================================================
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 의존성 캐시: 빌드 설정 파일이 안 바뀌면 이 단계는 재사용됨
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

# 소스 복사 후 실행용 jar만 생성 (테스트는 CI에서 이미 수행)
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon \
 && mv build/libs/*.jar app.jar

# ============================================================
# 2단계: 실행 (JRE만 포함해 이미지 크기 축소)
# ============================================================
FROM eclipse-temurin:17-jre
WORKDIR /app

ENV TZ=Asia/Seoul \
    JAVA_OPTS="-Xms256m -Xmx512m"

# root가 아닌 전용 사용자로 실행
RUN groupadd -r spring && useradd -r -g spring spring
COPY --from=build /app/app.jar app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 8080

# 프로필과 DB 접속 정보는 실행할 때 환경 변수로 주입
# 예: SPRING_PROFILES_ACTIVE=prod, SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
