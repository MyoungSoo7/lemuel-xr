package github.lms.lemuel.xr.values.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface UserValueProfileJpaRepository : JpaRepository<UserValueProfileJpaEntity, UUID> {
    fun findByUserId(userId: UUID): Optional<UserValueProfileJpaEntity>
}
