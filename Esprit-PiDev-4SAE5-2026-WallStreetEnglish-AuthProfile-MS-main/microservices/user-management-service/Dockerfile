FROM eclipse-temurin:22-jre-alpine
WORKDIR /app
# RUN addgroup -S spring && adduser -S spring -G spring
# USER spring
COPY target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]