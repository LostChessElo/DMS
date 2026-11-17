FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src ./src
RUN ./mvnw -q clean package


FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/dms-api*.jar ./dms-api.jar

EXPOSE 5050
ENTRYPOINT ["java", "-jar", "dms-api.jar"]
