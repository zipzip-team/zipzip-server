package org.zipzip.zipzipserver.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "app_user")
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends BaseTimeEntity {

    public static final String WITHDRAWN_DISPLAY_NAME = "탈퇴한 사용자";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false, length = 255)
    private String appleSubject;

    @Column(nullable = false, length = 50)
    private String displayName;

    private Instant deletedAt;

    public static AppUser create(String appleSubject, String displayName) {
        return AppUser.builder()
                .id(UUID.randomUUID())
                .appleSubject(appleSubject)
                .displayName(displayName)
                .build();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void withdraw(Instant deletedAt) {
        this.displayName = WITHDRAWN_DISPLAY_NAME;
        this.deletedAt = deletedAt;
    }

    public void updateDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void restore(String displayName) {
        this.displayName = displayName;
        this.deletedAt = null;
    }
}
