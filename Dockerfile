# -------- BUILD STAGE --------
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

# önce sadece pomları kopyala (cache için)
COPY pom.xml .
COPY commonCore/pom.xml commonCore/
COPY commonJpa/pom.xml commonJpa/
COPY commonGrpcContracts/pom.xml commonGrpcContracts/
COPY auth-service/pom.xml auth-service/
COPY user-service/pom.xml user-service/
COPY api-gateway/pom.xml api-gateway/
COPY course-service/pom.xml course-service/
COPY enrollment-service/pom.xml enrollment-service/
COPY review-service/pom.xml review-service/
COPY search-service/pom.xml search-service/

RUN mvn dependency:go-offline

# sonra source kodu kopyala
COPY . .

RUN mvn clean package -DskipTests

# -------- RUNTIME STAGE --------
FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY --from=build /app/auth-service/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]
