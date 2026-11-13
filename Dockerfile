FROM eclipse-temurin:17-jre

WORKDIR /app
ENV TZ=Asia/Shanghai
ENV MAMMOTH_WEB_DIST=/app/web
ENV MAMMOTH_DATA_ROOT=/data

COPY services/domain/target/mammoth-domain-service.jar /app/app.jar
COPY apps/web/dist /app/web

VOLUME ["/data"]
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
