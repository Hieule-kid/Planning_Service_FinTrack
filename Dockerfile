# ── Build Stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# `com.fintrack:core` is resolved from GitHub Packages (no submodule, no
# vendored copy). GitHub Packages requires auth even for public repos, so
# a token needs to flow into settings.xml at build time — see README.
ARG GITHUB_ACTOR
ARG GITHUB_TOKEN

COPY mvnw mvnw.cmd settings.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw

COPY . .

RUN ./mvnw -s settings.xml dependency:go-offline -B -q

# Build
RUN ./mvnw -s settings.xml package -pl planning-service -am -B -DskipTests -q

# ── Runtime Stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/planning-service/target/planning-service-*.jar app.jar
EXPOSE 8090

# JVM flags tuned for Render's free tier (~0.15 vCPU, 512 MB):
#   -XX:+UseSerialGC            single-threaded GC: less CPU and memory overhead than G1 on a tiny box
#   -XX:TieredStopAtLevel=1     C1 JIT only: far less compile CPU during the (CPU-bound) startup
#   -Xss512k                    smaller thread stacks (Tomcat/Hikari/Eureka threads add up)
#   -XX:+ExitOnOutOfMemoryError die on OOM so Render restarts the instance instead of leaving a zombie
# Override without rebuilding by setting JAVA_OPTS in the Render dashboard.
# `exec` makes the JVM PID 1 so SIGTERM triggers Spring's graceful shutdown.
ENV JAVA_OPTS="-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
