package com.ecgcare.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinIOConfigTest {

    @Mock
    private S3Client s3Client;

    private MinIOProperties properties;
    private MinIOConfig config;

    @BeforeEach
    void setUp() {
        properties = new MinIOProperties();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("access");
        properties.setSecretKey("secret");
        properties.setBucket("test-bucket");
        properties.setRegion("us-east-1");
        config = new MinIOConfig(properties);
    }

    @Test
    void s3ClientBeanBuildsFromProperties() {
        try (S3Client client = config.s3Client()) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void initializerSkipsCreationWhenBucketExists() throws Exception {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

        config.minioInitializer(s3Client).run();

        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void initializerCreatesMissingBucket() throws Exception {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("missing").build());

        config.minioInitializer(s3Client).run();

        verify(s3Client).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void initializerSwallowsUnexpectedStorageErrors() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("unreachable").build());

        assertThatCode(() -> config.minioInitializer(s3Client).run())
                .doesNotThrowAnyException();
    }
}
