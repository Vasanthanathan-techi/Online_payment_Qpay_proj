FROM maven:3.9.12-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
ARG MODULE
RUN mvn -B -pl "${MODULE}" -am clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 qpay
ARG MODULE
COPY --from=build /workspace/${MODULE}/target/*.jar app.jar
USER qpay
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
