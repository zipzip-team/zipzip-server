package org.zipzip.zipzipserver.domain.storage;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zipzip.zipzipserver.global.config.StorageProperties;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class S3ObjectStorageService implements ObjectStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;

    @Override
    public PresignedUpload issueUploadUrl(
            String objectKey, String contentType, long sizeBytes, Duration ttl) {
        PutObjectRequest putObjectRequest =
                PutObjectRequest.builder()
                        .bucket(storageProperties.getBucket())
                        .key(objectKey)
                        .contentType(contentType)
                        .contentLength(sizeBytes)
                        .build();

        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .putObjectRequest(putObjectRequest)
                        .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        return new PresignedUpload(presigned.url().toString(), presigned.expiration());
    }

    @Override
    public PresignedDownload issueDownloadUrl(String objectKey, Duration ttl) {
        GetObjectRequest getObjectRequest =
                GetObjectRequest.builder()
                        .bucket(storageProperties.getBucket())
                        .key(objectKey)
                        .build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(getObjectRequest)
                        .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return new PresignedDownload(presigned.url().toString(), presigned.expiration());
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(storageProperties.getBucket())
                            .key(objectKey)
                            .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public byte[] download(String objectKey) {
        ResponseBytes<GetObjectResponse> response =
                s3Client.getObjectAsBytes(
                        GetObjectRequest.builder()
                                .bucket(storageProperties.getBucket())
                                .key(objectKey)
                                .build());
        return response.asByteArray();
    }

    @Override
    public void upload(String objectKey, byte[] content, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(storageProperties.getBucket())
                        .key(objectKey)
                        .contentType(contentType)
                        .contentLength((long) content.length)
                        .build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public void delete(String objectKey) {
        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(storageProperties.getBucket())
                        .key(objectKey)
                        .build());
    }

    @Override
    public void deleteAll(Collection<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }
        List<ObjectIdentifier> identifiers =
                objectKeys.stream()
                        .map(key -> ObjectIdentifier.builder().key(key).build())
                        .toList();
        s3Client.deleteObjects(
                DeleteObjectsRequest.builder()
                        .bucket(storageProperties.getBucket())
                        .delete(Delete.builder().objects(identifiers).build())
                        .build());
    }
}
