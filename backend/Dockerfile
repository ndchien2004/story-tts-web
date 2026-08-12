# Bản dựng hai tầng: tầng đầu cần cả Maven lẫn JDK, ảnh chạy thì chỉ cần JRE.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# Container trên Railway bị giới hạn bộ nhớ; để mặc định thì JVM đọc RAM của cả
# máy chủ vật lý và đặt heap quá tay, dẫn tới bị hạ tiến trình giữa chừng.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
