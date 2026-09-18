# ── Build Stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# git is needed to fetch the `core` submodule inside the build container in case
# the platform's checkout didn't already populate it (see README for details).
RUN apk add --no-cache git

COPY mvnw mvnw.cmd ./
COPY .mvn .mvn
RUN chmod +x mvnw

# Copy the whole repo (small) so `.git`/`.gitmodules` are available for the
# submodule check below, then make sure `core/` actually has sources in it.
COPY . .
RUN if [ ! -f core/pom.xml ]; then \
        git submodule update --init --recursive; \
    fi

RUN ./mvnw dependency:go-offline -B -q

# Build
RUN ./mvnw package -pl planning-service -am -B -DskipTests -q

# ── Runtime Stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/planning-service/target/planning-service-*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
