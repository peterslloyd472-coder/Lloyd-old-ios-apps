# Stage 1: Download packages and build the Java application
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn -f ObscuraServer/pom.xml clean package -DskipTests

# Stage 2: Use a modern, active open-source Java 17 image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/ObscuraServer/target/ObscuraServer-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar
COPY --from=build /app/config.json config.json
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
