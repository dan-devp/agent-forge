# syntax=docker/dockerfile:1

# --- Build --------------------------------------------------------------------
# Official Gradle image instead of the wrapper download: reproducible and independent
# of services.gradle.org (keep the version in sync with gradle/wrapper).
FROM gradle:9.7.1-jdk25 AS build
WORKDIR /workspace

# Build scripts first: the dependency layer stays cached as long as deps do not change.
COPY settings.gradle build.gradle ./
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true

COPY src src
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon clean bootJar

# --- Runtime ------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# curl for the compose healthcheck
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
