package github.lms.lemuel.xr.auth.adapter.out.persistence

import github.lms.lemuel.xr.auth.application.port.out.DevicePort
import github.lms.lemuel.xr.auth.domain.Device
import org.springframework.stereotype.Component
import java.util.Optional

/**
 * [DevicePort] 구현 — Spring Data [DeviceJpaRepository] 위임.
 *
 * 이 클래스가 [DeviceJpaEntity] 를 import 하는 유일한 application/adapter 지점.
 */
@Component
class DevicePersistenceAdapter(
    private val repository: DeviceJpaRepository,
) : DevicePort {

    override fun findByDeviceFingerprint(fingerprint: String): Optional<Device> =
        repository.findByDeviceFingerprint(fingerprint).map(::toDomain)

    override fun save(device: Device): Device =
        toDomain(repository.save(toEntity(device)))

    private fun toDomain(e: DeviceJpaEntity): Device =
        Device(
            e.id,
            e.userId,
            e.deviceType,
            e.deviceFingerprint,
            e.lastSeenAt,
            e.createdAt,
        )

    private fun toEntity(d: Device): DeviceJpaEntity =
        DeviceJpaEntity().apply {
            id = d.id
            userId = d.userId
            deviceType = d.deviceType
            deviceFingerprint = d.deviceFingerprint
            lastSeenAt = d.lastSeenAt
            createdAt = d.createdAt
        }
}
