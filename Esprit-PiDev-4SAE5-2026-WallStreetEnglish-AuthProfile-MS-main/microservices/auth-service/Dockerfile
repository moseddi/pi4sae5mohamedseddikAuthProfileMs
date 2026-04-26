FROM maven:3.9.6-eclipse-temurin-22 AS builder

# Use Maven with custom DNS via JVM properties
ENV MAVEN_OPTS="-Djava.net.preferIPv4Stack=true -DsocksNonProxyHosts=localhost"

WORKDIR /app

# Copy parent POM
COPY pom.xml .

# Install parent POM with retry and alternative repository
RUN mvn install -N -B -Dmaven.wagon.http.retryHandler.count=3 \
    -Dmaven.wagon.httpconnectionManager.ttlSeconds=25

# Copy auth-service
COPY microservices/auth-service ./microservices/auth-service

# Build auth-service with retry
WORKDIR /app/microservices/auth-service
RUN mvn clean package -DskipTests -B \
    -Dmaven.wagon.http.retryHandler.count=3 \
    -Dmaven.wagon.httpconnectionManager.ttlSeconds=25

FROM eclipse-temurin:22-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=builder /app/microservices/auth-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]