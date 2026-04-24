# 1. 用Java 8作为基础镜像（和你的项目版本匹配）
FROM openjdk:8-jdk-alpine

# 2. 把打包好的jar包复制到容器里（路径和你的pom.xml一致）
COPY target/mint-health-backend-0.0.1-SNAPSHOT.jar app.jar

# 3. 启动命令（这里是关键！必须连在一起，不能有空格）
ENTRYPOINT ["java", "-jar", "/app.jar"]