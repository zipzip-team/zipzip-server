package org.zipzip.zipzipserver.global.config;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * OCI Object Storage는 S3 호환 API만 제공하므로, AWS SDK를 커스텀 엔드포인트로 연결해 사용한다. forcePathStyle은 OCI가
 * virtual-hosted-style 버킷 주소를 지원하지 않아 필요하다. chunkedEncodingEnabled(false)는 OCI가 AWS 전용 확장인
 * aws-chunked 전송 인코딩을 지원하지 않아("501 Not Implemented") 꺼야 한다.
 */
@Configuration
@RequiredArgsConstructor
public class StorageClientConfig {

    private final StorageProperties storageProperties;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(storageProperties.getEndpoint()))
                .region(Region.of(storageProperties.getRegion()))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(serviceConfiguration())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(storageProperties.getEndpoint()))
                .region(Region.of(storageProperties.getRegion()))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(serviceConfiguration())
                .build();
    }

    private S3Configuration serviceConfiguration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build();
    }

    private StaticCredentialsProvider credentialsProvider() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                        storageProperties.getAccessKey(), storageProperties.getSecretKey()));
    }
}
