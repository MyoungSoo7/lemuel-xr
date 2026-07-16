package github.lms.lemuel.xr.ai.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface LlmCacheJpaRepository : JpaRepository<LlmCacheJpaEntity, String>
