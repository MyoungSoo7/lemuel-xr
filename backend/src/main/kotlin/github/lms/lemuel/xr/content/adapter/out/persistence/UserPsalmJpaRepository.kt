package github.lms.lemuel.xr.content.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserPsalmJpaRepository : JpaRepository<UserPsalmJpaEntity, UUID>
