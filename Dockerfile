FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/dependency

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/target/classes ./classes
COPY --from=build /workspace/target/dependency ./dependency

EXPOSE 8081

ENV AUCTION_API_PORT=8081

ENTRYPOINT ["java", "-cp", "/app/classes:/app/dependency/*", "org.example.server.AuctionApiServerMain"]
