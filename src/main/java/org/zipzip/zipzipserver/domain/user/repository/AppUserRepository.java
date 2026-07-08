package org.zipzip.zipzipserver.domain.user.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByAppleSubject(String appleSubject);
}
