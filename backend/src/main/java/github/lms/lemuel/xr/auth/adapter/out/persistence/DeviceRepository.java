package github.lms.lemuel.xr.auth.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<DeviceJpaEntity, UUID> {
    Optional<DeviceJpaEntity> findByDeviceFingerprint(String fingerprint);
}
