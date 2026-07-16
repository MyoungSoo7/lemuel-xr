package github.lms.lemuel.xr.tts.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface TtsCacheJpaRepository : JpaRepository<TtsCacheJpaEntity, String>
