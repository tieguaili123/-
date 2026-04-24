FROM maven:3.8.5-jdk-8 AS builder
WORKDIR /project
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM openjdk:8
COPY --from=builder /project/target/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]