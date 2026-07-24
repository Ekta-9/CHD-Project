package com.ecgcare.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class MinIOConfig {
    private final MinIOProperties minIOProperties;

    // Uses AWS SDK v2's generic S3 client rather than the MinIO SDK, since the
    // MinIO client rejects any endpoint URL containing a path (e.g. Supabase
    // Storage's .../storage/v1/s3) - AWS SDK v2 supports arbitrary endpoint
    // overrides, path included.
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(minIOProperties.getEndpoint()))
                .region(Region.of(minIOProperties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minIOProperties.getAccessKey(), minIOProperties.getSecretKey())))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    public CommandLineRunner minioInitializer(S3Client s3Client) {
        return args -> {
            try {
                String bucketName = minIOProperties.getBucket();
                try {
                    s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
                    log.info("Storage bucket '{}' already exists", bucketName);
                } catch (NoSuchBucketException e) {
                    log.info("Creating storage bucket: {}", bucketName);
                    s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                    log.info("Storage bucket '{}' created successfully", bucketName);
                }
            } catch (Exception e) {
                log.warn("Failed to initialize storage bucket: {}. Storage may not be accessible.", e.getMessage());
                log.debug("Storage initialization error", e);
            }
        };
    }
}
