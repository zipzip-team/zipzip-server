package org.zipzip.zipzipserver.global.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zipzip.zipzipserver.global.jpa.BaseTimeEntity;

@Getter
@Entity
@Table(
        name = "api_idempotency_record",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_api_idempotency_record__scope_key_method_path",
                        columnNames = {"scope", "idempotency_key", "http_method", "api_path"}))
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiIdempotencyRecord extends BaseTimeEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String scope;

    @Column(nullable = false)
    private UUID idempotencyKey;

    @Column(nullable = false, length = 10)
    private String httpMethod;

    @Column(nullable = false, length = 255)
    private String apiPath;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApiIdempotencyStatus status;

    private Integer responseHttpStatus;

    @Column(columnDefinition = "text")
    private String encryptedResponseBody;

    @Column(nullable = false)
    private Instant expiresAt;

    public static ApiIdempotencyRecord processing(
            String scope,
            UUID idempotencyKey,
            String httpMethod,
            String apiPath,
            String requestHash,
            Instant expiresAt) {
        return ApiIdempotencyRecord.builder()
                .id(UUID.randomUUID())
                .scope(scope)
                .idempotencyKey(idempotencyKey)
                .httpMethod(httpMethod)
                .apiPath(apiPath)
                .requestHash(requestHash)
                .status(ApiIdempotencyStatus.PROCESSING)
                .expiresAt(expiresAt)
                .build();
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean hasSameRequestHash(String requestHash) {
        return this.requestHash.equals(requestHash);
    }

    public void complete(int responseHttpStatus, String encryptedResponseBody) {
        this.responseHttpStatus = responseHttpStatus;
        this.encryptedResponseBody = encryptedResponseBody;
        this.status = ApiIdempotencyStatus.COMPLETED;
    }
}
