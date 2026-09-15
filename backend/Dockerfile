# syntax=docker/dockerfile:1

# --- Build --------------------------------------------------------------------
# Offizielles Gradle-Image statt Wrapper-Download: reproduzierbar und unabhaengig
# von services.gradle.org (Version hier synchron zu gradle/wrapper halten).
FROM gradle:9.7.1-jdk25 AS build
WORKDIR /workspace

# Build-Skripte zuerst: Dependency-Layer bleibt stabil, solange sich Deps nicht aendern.
COPY settings.gradle build.gradle ./
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true

COPY src src
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon clean bootJar

# --- Runtime ------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# curl fuer den Compose-Healthcheck
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
