package org.zipzip.zipzipserver.domain.storage;

import java.time.Duration;
import java.util.Collection;

public interface ObjectStorageService {

    PresignedUpload issueUploadUrl(
            String objectKey, String contentType, long sizeBytes, Duration ttl);

    PresignedDownload issueDownloadUrl(String objectKey, Duration ttl);

    boolean exists(String objectKey);

    byte[] download(String objectKey);

    void upload(String objectKey, byte[] content, String contentType);

    void delete(String objectKey);

    void deleteAll(Collection<String> objectKeys);
}
