# ---------- build ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

# ---------- runtime ----------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home /app app

COPY --from=build /build/target/campus-kb-qa-0.2.0.jar /app/app.jar
RUN mkdir -p /app/uploads && chown -R app:app /app
USER app

EXPOSE 8080
ENV JAVA_OPTS="-Xms256m -Xmx768m -Dfile.encoding=UTF-8" \
    TZ=Asia/Shanghai

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/api/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]