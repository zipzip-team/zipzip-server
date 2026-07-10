package org.zipzip.zipzipserver.domain.photo.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.zipzip.zipzipserver.domain.photo.entity.PhotoUploadReservation;

public interface PhotoUploadReservationRepository
        extends JpaRepository<PhotoUploadReservation, String> {

    List<PhotoUploadReservation> findByExpiresAtLessThanEqual(Instant expiresAt);

    long deleteByExpiresAtLessThanEqual(Instant expiresAt);
}
