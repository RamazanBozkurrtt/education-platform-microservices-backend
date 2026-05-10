FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY commonCore/pom.xml commonCore/
COPY commonJpa/pom.xml commonJpa/
COPY commonGrpcContracts/pom.xml commonGrpcContracts/
COPY auth-service/pom.xml auth-service/
COPY user-service/pom.xml user-service/
COPY search-service/pom.xml search-service/
COPY api-gateway/pom.xml api-gateway/
COPY course-service/pom.xml course-service/
COPY enrollment-service/pom.xml enrollment-service/
COPY review-service/pom.xml review-service/

RUN mvn -pl api-gateway -am dependency:go-offline

COPY . .
RUN mvn -pl api-gateway -am clean package -DskipTests

FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY --from=build /app/api-gateway/target/*.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java","-jar","app.jar"]
