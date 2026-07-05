# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src

# private 서브모듈(src/main/resources/config)이 체크아웃되지 않은 환경(CI/CD)에서도
# non-optional classpath import가 깨지지 않도록 placeholder 값만 채운다.
# 실제 프로덕션 DB 접속정보는 컨테이너 실행 시 SPRING_DATASOURCE_* 환경변수로 주입되어
# 이 값을 덮어쓰므로(Spring 프로퍼티 우선순위상 env var가 우선), public 이미지에 포함되어도 안전하다.
RUN mkdir -p src/main/resources/config && cat > src/main/resources/config/application-secret.yml <<'YAML'
spring:
  application:
    name: zipzip-server
  datasource:
    url: jdbc:postgresql://localhost:5432/placeholder
    username: placeholder
    password: placeholder
    driver-class-name: org.postgresql.Driver
YAML

RUN chmod +x gradlew && ./gradlew --no-daemon clean bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build /workspace/build/libs/*.jar app.jar
RUN chown spring:spring app.jar

USER spring
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75.0 -jar app.jar"]
