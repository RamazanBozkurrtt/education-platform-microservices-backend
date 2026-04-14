FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY commonCore/pom.xml commonCore/
COPY commonJpa/pom.xml commonJpa/
COPY auth-service/pom.xml auth-service/

RUN mvn -pl auth-service -am dependency:go-offline

COPY . .
RUN mvn -pl auth-service -am clean package -DskipTests

FROM eclipse-temurin:21-jdk

WORKDIR /app
COPY --from=build /app/auth-service/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]
