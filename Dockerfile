# Multi-stage build so the shipped image only has the JRE + the built jar,
# not the whole Maven/Node toolchain used to produce it.
#
# Stage 1: build the production jar (Vaadin production-mode frontend bundle
# included, via the `production` Maven profile in pom.xml). The Vaadin Maven
# plugin downloads its own pinned Node/npm automatically — no need to
# pre-install Node in this image.
#
# Deliberately NOT using a third-party "maven:..." base image here. An
# earlier version of this file used maven:3.9-eclipse-temurin-21 and it
# pulled fine but didn't have `mvn` on PATH in at least one real build
# (exit 127, "mvn: not found") — never diagnosed further since this sandbox
# has no Docker daemon to test image tags against. Installing Maven via apt
# on a plain, well-known JDK image is slightly slower on a cold cache but
# doesn't depend on a specific third-party image's internal layout.
FROM eclipse-temurin:21-jdk-jammy AS build
ENV HOME=/app
RUN mkdir -p $HOME

# RUN apt-get update \
#     && apt-get install -y --no-install-recommends maven \
#     && rm -rf /var/lib/apt/lists/*

# WORKDIR /app
WORKDIR $HOME
# COPY pom.xml .
COPY pom.xml $HOME
# Warm the Maven dependency cache in its own layer so a source-only change
# doesn't force a full re-download on the next build.
RUN mvn -B dependency:go-offline || true
COPY src $HOME/src
RUN mvn -B clean package -DskipTests

# Stage 2: run it on a slim JRE.
FROM eclipse-temurin:21-jre-jammy
# WORKDIR /app
WORKDIR $HOME
COPY --from=build /app/target/*.jar app.jar

# Render/Railway/Fly all pass PORT at runtime; application.yml already reads
# ${PORT:8080}, so nothing else to configure here.
EXPOSE 8080

# Explicit heap sizing (added 2026-10-01, see DEPLOY.md "Known fix" section).
# Without this, the JVM's container-aware default (a percentage of detected
# cgroup memory, historically ~25%) left too little heap on Render's free
# 512MB containers, and a real Demand.xlsx upload triggered
# OutOfMemoryError: Java heap space inside Apache POI's DOM-based XSSF
# parsing (ExcelSheetReader -> RefreshService.refreshDemand). Raising
# MaxRAMPercentage gives the heap a much bigger share of the container's
# RAM. This is a mitigation, not a guaranteed fix — POI's DOM model loads
# the whole workbook as an in-memory XML tree, which can be several times
# the raw .xlsx file size, and a big enough file can still exhaust even a
# generous heap on a 512MB box. If OOM recurs after this, the real fix is
# either more RAM (e.g. an Oracle Cloud Always Free VM) or rewriting
# ExcelSheetReader to use POI's streaming/SAX reader instead of XSSFWorkbook.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar", "--spring.profiles.active=prod"]

