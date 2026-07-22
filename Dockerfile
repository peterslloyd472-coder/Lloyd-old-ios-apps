# Stage 1: Download packages and build the Java application
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn -f ObscuraServer/pom.xml clean package -DskipTests

# Stage 2: Use a modern, active open-source Java 17 image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the compiled Java engine
COPY --from=build /app/ObscuraServer/target/*.jar app.jar

# Copy the configuration and required asset files
COPY --from=build /app/config.json config.json
COPY --from=build /app/*.jpg ./
COPY --from=build /app/*.png ./
COPY --from=build /app/*.ico ./
COPY --from=build /app/*.svg ./

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
