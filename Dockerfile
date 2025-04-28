# Use a multi-stage build to reduce the final image size
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Create the runtime image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Set environment variables for the application
# ENV SPRING_PROFILES_ACTIVE=prod
ENV OPENAI_API_KEY=${OPENAI_API_KEY}
# ENV GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
# ENV GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET}

# Expose the port the app runs on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"] 