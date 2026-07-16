package github.lms.lemuel.xr.outbox.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface ProcessedEventJpaRepository :
    JpaRepository<ProcessedEventJpaEntity, ProcessedEventJpaEntity.PK>
