package com.ecgcare.backend.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class MinIOConfig {
    private final MinIOProperties minIOProperties;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minIOProperties.getEndpoint())
                .credentials(minIOProperties.getAccessKey(), minIOProperties.getSecretKey())
                .build();
    }

    @Bean
    public CommandLineRunner minioInitializer(MinioClient minioClient) {
        return args -> {
            try {
                String bucketName = minIOProperties.getBucket();
                boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder()
                        .bucket(bucketName)
                        .build());

                if (!bucketExists) {
                    log.info("Creating MinIO bucket: {}", bucketName);
                    minioClient.makeBucket(MakeBucketArgs.builder()
                            .bucket(bucketName)
                            .build());
                    log.info("MinIO bucket '{}' created successfully", bucketName);
                } else {
                    log.info("MinIO bucket '{}' already exists", bucketName);
                }
            } catch (Exception e) {
                log.warn("Failed to initialize MinIO bucket: {}. MinIO may not be running.", e.getMessage());
                log.debug("MinIO initialization error", e);
            }
        };
    }
}
