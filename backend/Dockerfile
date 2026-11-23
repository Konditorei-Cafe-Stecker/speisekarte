# Café Stecker backend: Spring Boot on Java 21.
# Build:  docker build -t stecker-backend backend
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Docker VMs often have no IPv6 route; Java would still try it first.
ENV GRADLE_OPTS="-Djava.net.preferIPv4Stack=true"
# Dependencies first, so they are cached while only the code changes.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon -q dependencies > /dev/null
COPY src src
# The version from git (deploy/local-up.sh passes it); shown at /actuator/info.
ARG APP_VERSION=dev
# Tests run in CI and with ./gradlew build, not inside the image build.
RUN ./gradlew --no-daemon -q bootJar -x test -PappVersion="$APP_VERSION" \
    && cp "build/libs/backend-$APP_VERSION.jar" /app.jar

FROM eclipse-temurin:21-jre
ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="stecker-backend" \
      org.opencontainers.image.version="$APP_VERSION"
RUN useradd --system --uid 10001 stecker
COPY --from=build /app.jar /app/app.jar
USER 10001
EXPOSE 8080
# Measured under load in the local cluster (deploy/README.md):
# - Parallel collector with a fixed heap of 50 % of the memory limit. The
#   Serial collector the JVM picks for small containers stopped a copy for
#   up to 0.8 s while its heap grew; G1 costs more CPU, and CPU is what a
#   rush runs out of. Fixed size: no growing, no surprises near the limit.
# - The other half of the limit is for threads (Tomcat is capped at 50 in
#   deploy/k8s/backend.yaml), classes and compiled code; with 200 threads
#   and a larger heap the copies hit the limit and were OOM-killed.
# - Compiler/GC threads sized for 2 CPUs: without a CPU limit the JVM sees
#   every CPU of the node and several copies would fight over them.
ENV JAVA_TOOL_OPTIONS="-XX:+UseParallelGC -XX:InitialRAMPercentage=50 -XX:MaxRAMPercentage=50 -XX:ActiveProcessorCount=2 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
