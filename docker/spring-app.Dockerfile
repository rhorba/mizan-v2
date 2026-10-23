# syntax=docker/dockerfile:1
# Builds one Spring Boot module of the Maven reactor. Build context: repository root.
#   docker build -f docker/spring-app.Dockerfile --build-arg MODULE=mizan-api -t mizan-api .

ARG JDK_IMAGE=eclipse-temurin:25.0.4.1_1-jdk-alpine@sha256:1e20ead9eec7234db293f1913438090d533ed47c0b9e12dc8c7627a802a44ed6
ARG JRE_IMAGE=eclipse-temurin:25.0.4.1_1-jre-alpine@sha256:b7528962e65d43064d8a8b67ba175367896bc948363ea2d3fcd7f10d34e1f048

# === Build ===
FROM ${JDK_IMAGE} AS build
ARG MODULE
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY config config
COPY mizan-api/pom.xml mizan-api/pom.xml
COPY mizan-analyzer/pom.xml mizan-analyzer/pom.xml
COPY coverage-report/pom.xml coverage-report/pom.xml
COPY ${MODULE}/src ${MODULE}/src
# Tests, formatting and coverage run in CI (./mvnw verify); the image build only packages.
# sharing=locked: compose/bake build api and analyzer in parallel; they must not write the same
# Maven repository at once (seen locally as an intermittent build failure).
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -q -pl ${MODULE} -am package \
      -DskipTests -Djacoco.skip=true -Dspotless.check.skip=true -Dcheckstyle.skip=true \
 && java -Djarmode=tools -jar ${MODULE}/target/${MODULE}-*.jar extract --layers --destination /layers \
 && mv /layers/application/${MODULE}-*.jar /layers/application/app.jar

# === Runtime ===
FROM ${JRE_IMAGE}
LABEL org.opencontainers.image.source="https://github.com/rhorba/mizan-v2"
RUN addgroup -S -g 1001 app && adduser -S -D -H -u 1001 -G app app
WORKDIR /app
# Least-changing layers first so dependency layers stay cached between builds.
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER 1001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "app.jar"]
