# 第一阶段：构建jar包（用有效的Maven镜像）
FROM maven:3.8.6-openjdk-8 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 第二阶段：运行jar包（用现在还能用的Java 8镜像）
FROM eclipse-temurin:8-jre-alpine
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]