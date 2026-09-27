FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/logicscope-domain/pom.xml backend/logicscope-domain/pom.xml
COPY backend/logicscope-discovery/pom.xml backend/logicscope-discovery/pom.xml
COPY backend/logicscope-trace/pom.xml backend/logicscope-trace/pom.xml
COPY backend/logicscope-source/pom.xml backend/logicscope-source/pom.xml
COPY backend/logicscope-semantic/pom.xml backend/logicscope-semantic/pom.xml
COPY backend/logicscope-evidence/pom.xml backend/logicscope-evidence/pom.xml
COPY backend/logicscope-replay/pom.xml backend/logicscope-replay/pom.xml
COPY backend/logicscope-persistence/pom.xml backend/logicscope-persistence/pom.xml
COPY backend/logicscope-app/pom.xml backend/logicscope-app/pom.xml
RUN mvn -f backend/pom.xml -B dependency:go-offline
COPY backend backend
RUN mvn -f backend/pom.xml -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/backend/logicscope-app/target/logicscope-app-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 4377
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
