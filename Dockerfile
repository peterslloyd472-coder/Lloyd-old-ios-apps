# Stage 1: Download packages and build the Java application
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Create a lightweight runtime image to host on Render
FROM openjdk:17-slim
WORKDIR /app
COPY --from=build /app/ObscuraServer/target/ObscuraServer-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar
COPY --from=build /app/config.json config.json
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
