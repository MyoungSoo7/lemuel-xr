-- V11: 이벤트 outbox + idempotency (인터아삿 Lemuel 패턴 차용)
-- DB-SCHEMA.md §10 + 헥사고날 아웃박스 패턴

-- ============================================================
-- outbox_events — 트랜잭션 일관성 보장된 이벤트 큐
--   서비스 트랜잭션에서 INSERT → 워커가 Kafka/Webhook 으로 발행 → status='sent'
-- ============================================================
CREATE TABLE IF NOT EXISTS outbox_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type      VARCHAR(50) NOT NULL,          -- 'EmotionLog'|'GameSession'|'SafetyAlert'|...
    aggregate_id        VARCHAR(80) NOT NULL,          -- 도메인 PK 의 문자열 표현
    event_type          VARCHAR(80) NOT NULL,          -- 'emotion.classified'|'game.completed'|...
    payload             JSONB NOT NULL,
    headers             JSONB,                         -- {"correlation_id":"...","trace_id":"..."}
    status              VARCHAR(20) NOT NULL DEFAULT 'pending',  -- 'pending'|'sent'|'failed'|'dead'
    attempt_count       SMALLINT NOT NULL DEFAULT 0,
    last_error          TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at             TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_outbox_pending
    ON outbox_events(created_at)
    WHERE status = 'pending';
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON outbox_events(aggregate_type, aggregate_id);

-- ============================================================
-- processed_events — 컨슈머 idempotency 보장 (Triple Idempotency 의 PK 레이어)
-- ============================================================
CREATE TABLE IF NOT EXISTS processed_events (
    event_id            UUID PRIMARY KEY,              -- outbox_events.id 와 동일한 UUID
    consumer_name       VARCHAR(80) NOT NULL,          -- 'emotion-recommender' | 'metrics-rollup' ...
    processed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (event_id, consumer_name)
);
CREATE INDEX IF NOT EXISTS idx_processed_events_consumer_time
    ON processed_events(consumer_name, processed_at DESC);
