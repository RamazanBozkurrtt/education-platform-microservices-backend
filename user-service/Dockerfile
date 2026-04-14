FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY commonCore/pom.xml commonCore/
COPY commonJpa/pom.xml commonJpa/
COPY commonGrpcContracts/pom.xml commonGrpcContracts/
COPY user-service/pom.xml user-service/

RUN mvn -pl user-service -am dependency:go-offline

COPY . .
RUN mvn -pl user-service -am clean package -DskipTests

FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY --from=build /app/user-service/target/*.jar app.jar

EXPOSE 8081 9091

ENTRYPOINT ["java","-jar","app.jar"]
