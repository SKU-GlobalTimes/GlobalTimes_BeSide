# JDK 17을 베이스 이미지로 사용
FROM openjdk:17-jdk-slim

# 애플리케이션 JAR 파일을 복사
COPY build/libs/*SNAPSHOT.jar /app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]