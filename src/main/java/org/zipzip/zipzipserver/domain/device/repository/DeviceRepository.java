package org.zipzip.zipzipserver.domain.device.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.zipzip.zipzipserver.domain.device.entity.Device;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    @Query(
            """
            select new org.zipzip.zipzipserver.domain.device.repository.DeviceRow(
                d.appUser.id,
                d.id,
                d.name
            )
            from Device d
            where d.appUser.id in :appUserIds
              and d.deletedAt is null
            order by d.createdAt asc, d.id asc
            """)
    List<DeviceRow> findActiveDevicesByAppUserIds(@Param("appUserIds") Collection<UUID> appUserIds);
}
