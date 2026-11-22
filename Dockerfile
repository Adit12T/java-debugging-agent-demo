# Build: compile with the JDK alone (the agent has no runtime dependencies, so no Maven download is needed).
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY src/main/java ./src/main/java
RUN find src/main/java -name '*.java' > sources.txt \
 && javac --release 17 -encoding UTF-8 -d classes @sources.txt \
 && jar --create --file app.jar --main-class io.github.adit12t.debugagent.DebugAgent -C classes .

# Run: small JRE image, non-root user.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S agent && adduser -S -G agent -u 10001 agent \
 && mkdir /out && chown agent:agent /out
WORKDIR /app
COPY --from=build /src/app.jar app.jar
# The agent's default output folder ("out") points at the /out volume, so reports land there
# even when you pass your own options without --out.
RUN ln -s /out /app/out
COPY sample-data sample-data
COPY sample-app sample-app
USER agent
VOLUME ["/out"]
# Set ANTHROPIC_API_KEY (and optionally ANTHROPIC_MODEL) at run time to use Claude; without it the offline rule-based analyst runs.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
# Defaults run on the bundled sample data. Override by passing your own options after the image name.
CMD ["--logs", "sample-data/logs-before.ndjson", "--after-logs", "sample-data/logs-after.ndjson", "--source-root", "sample-app/src/main/java", "--app-package", "demo.orders", "--out", "/out"]
