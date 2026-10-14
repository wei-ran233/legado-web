# ===== 构建阶段：编译后端 + 前端 =====
FROM gradle:8.14-jdk24 AS backend-build
WORKDIR /build
COPY gradle/ gradle/
COPY settings.gradle build.gradle gradle.properties ./
COPY modules/engine/ modules/engine/
COPY server/ server/
# 仅构建纯 JVM 服务端（无需 Android SDK）
RUN gradle :server:installDist --no-daemon

FROM node:20-slim AS frontend-build
WORKDIR /build
RUN corepack enable
COPY modules/web/package.json modules/web/pnpm-lock.yaml* ./
RUN pnpm install --no-frozen-lockfile
COPY modules/web/ .
RUN pnpm build-only

# ===== 运行阶段 =====
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd -r legado && useradd -r -g legado legado && mkdir -p /data && chown legado:legado /data

COPY --from=backend-build /build/server/build/install/server/ /app/
COPY --from=frontend-build /build/dist/ /data/webapp/

USER legado

ENV PORT=8080 \
    DATA_DIR=/data \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
VOLUME /data

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s \
  CMD curl -sf http://127.0.0.1:${PORT}/api/health || exit 1

CMD ["/app/bin/server"]
