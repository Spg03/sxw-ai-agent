# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 先复制依赖描述，利用缓存
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -e -DskipTests dependency:go-offline || true

# 再复制源码构建
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# 非 root 用户
RUN useradd -r -u 1001 -g root appuser
USER 1001

COPY --from=build /workspace/target/sxw-ai-agent-*.jar /app/app.jar

ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0" \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8123 \
    NOTE_BASE_DIR=/data/notes

EXPOSE 8123

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://127.0.0.1:${SERVER_PORT}/api/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
