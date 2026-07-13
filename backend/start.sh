#!/bin/bash

set -e

# Download MinIO (one-time per container start; keeps this image simple)
wget -q https://dl.min.io/server/minio/release/linux-amd64/minio
chmod +x minio
mkdir -p /tmp/minio-data

# Start MinIO in background
MINIO_ROOT_USER="${MINIO_ROOT_USER:-minioadmin}" \
MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD:-minioadmin}" \
./minio server /tmp/minio-data --address ":9000" &

# Wait for MinIO to start
sleep 5

# Start Spring Boot
java -jar app.jar

