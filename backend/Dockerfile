FROM gradle:8.13-jdk21 AS build
WORKDIR /workspace
COPY build.gradle settings.gradle ./
COPY src src
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache su-exec
COPY --from=build /workspace/build/libs/*.jar app.jar
COPY docker-entrypoint.sh /usr/local/bin/signature-validator-entrypoint
RUN chmod 0755 /usr/local/bin/signature-validator-entrypoint \
    && chown 1001:0 /app/app.jar \
    && mkdir -p /app/cache/trusted-lists \
    && chown 1001:0 /app/cache/trusted-lists \
    && chmod 0775 /app/cache/trusted-lists
EXPOSE 8080
USER root
ENTRYPOINT ["/usr/local/bin/signature-validator-entrypoint"]
CMD ["java", "-jar", "/app/app.jar"]
