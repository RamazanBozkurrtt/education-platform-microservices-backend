# -------- BUILD STAGE --------
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY commonCore/pom.xml commonCore/
COPY commonJpa/pom.xml commonJpa/
COPY commonGrpcContracts/pom.xml commonGrpcContracts/
COPY auth-service/pom.xml auth-service/
COPY user-service/pom.xml user-service/
COPY api-gateway/pom.xml api-gateway/
COPY enrollment-service/pom.xml enrollment-service/

RUN mvn -pl enrollment-service -am dependency:go-offline

COPY . .

RUN mvn -pl enrollment-service -am clean package -DskipTests

# -------- RUNTIME STAGE --------
FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY --from=build /app/enrollment-service/target/*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java","-jar","app.jar"]
