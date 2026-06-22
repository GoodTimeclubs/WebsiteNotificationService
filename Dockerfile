# ---- Build-Stage: kompiliert die Sourcen mit jsoup im Classpath ----
FROM eclipse-temurin:26-jdk AS build
WORKDIR /app
COPY src/ ./src/
COPY lib/ ./lib/
RUN javac -cp lib/jsoup-1.22.2.jar -d out src/*.java

# ---- Run-Stage: schlankes Runtime-Image ----
FROM eclipse-temurin:26-jre
WORKDIR /app
COPY --from=build /app/out ./out
COPY --from=build /app/lib ./lib
# Linux nutzt ":" als Classpath-Trenner (nicht ";" wie Windows)
ENTRYPOINT ["java", "-cp", "out:lib/jsoup-1.22.2.jar", "Main"]