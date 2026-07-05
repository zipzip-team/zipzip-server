# JAR은 CD 워크플로우에서 네이티브(x86 러너)로 미리 빌드해서 build/libs에 둔다.
# 여기서는 arm64 JRE 런타임 이미지에 그 결과물만 얹는다 — QEMU 에뮬레이션 위에서
# Gradle/JDK 컴파일이 돌아가는 걸 피하기 위함 (에뮬레이션은 느림, 단순 COPY/chown은 빠름).
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring

COPY app/app.jar app.jar
RUN chown spring:spring app.jar

USER spring
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75.0 -jar app.jar"]
