FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY src src
RUN ./gradlew --no-daemon installDist

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache git
COPY --from=build /src/build/install/puzzler /opt/puzzler
WORKDIR /repo
ENTRYPOINT ["/opt/puzzler/bin/puzzler"]
