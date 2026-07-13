FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

CMD bash -c "wget -q https://dl.min.io/server/minio/release/linux-amd64/minio && \
    chmod +x minio && \
    mkdir -p /tmp/minio-data && \
    MINIO_ROOT_USER=minioadmin MINIO_ROOT_PASSWORD=minioadmin ./minio server /tmp/minio-data --address ':9000' & \
    sleep 5 && \
    java -jar app.jar"

