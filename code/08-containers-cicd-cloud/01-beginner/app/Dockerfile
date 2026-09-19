# Multi-stage build: the build toolchain (Maven + full JDK) never ships in the final image -
# smaller image, smaller attack surface.
FROM docker.io/library/maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the POM first and resolve dependencies BEFORE copying source - a source-only change
# (the overwhelming majority of commits) then reuses this cached layer instead of re-resolving
# every dependency on every build.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package

# Runtime stage: JRE only, not the full JDK/Maven toolchain used to build the artifact.
FROM docker.io/library/eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/container-demo-app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
