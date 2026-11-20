# secbrief Spring Boot 서버 이미지 (빌드: ./gradlew :SB-Web:bootJar)
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

ENV TZ=Asia/Seoul
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

COPY SB-Web/build/libs/app.jar app.jar

HEALTHCHECK --interval=60s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
