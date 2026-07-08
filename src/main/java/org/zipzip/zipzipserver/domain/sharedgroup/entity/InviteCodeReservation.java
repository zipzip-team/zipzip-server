package org.zipzip.zipzipserver.domain.sharedgroup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "invite_code_reservation")
@EntityListeners(AuditingEntityListener.class)
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteCodeReservation {

    @Id
    @Column(nullable = false, length = 64)
    private String inviteCode;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static InviteCodeReservation create(String inviteCode) {
        return InviteCodeReservation.builder().inviteCode(inviteCode).build();
    }
}
