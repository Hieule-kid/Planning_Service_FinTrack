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
ENTRYPOINT ["java", "-jar", "app.jar"]
