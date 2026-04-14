FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY commonCore/pom.xml commonCore/
COPY api-gateway/pom.xml api-gateway/

RUN mvn -pl api-gateway -am dependency:go-offline

COPY . .
RUN mvn -pl api-gateway -am clean package -DskipTests

FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY --from=build /app/api-gateway/target/*.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java","-jar","app.jar"]
