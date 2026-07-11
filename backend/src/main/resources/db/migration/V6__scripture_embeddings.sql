-- V6: SCRIPTURE 도메인 확장 — 임베딩 (pgvector) + 태그
-- DB-SCHEMA.md §6 매핑

-- pgvector 확장
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- scripture_passages 컬럼 보강 (V1 의 기본 스키마에서)
-- ============================================================
ALTER TABLE scripture_passages
    ADD COLUMN IF NOT EXISTS book_code        VARCHAR(10),     -- 'gen' | 'ex' | '1sam' | 'ps' | 'matt'
    ADD COLUMN IF NOT EXISTS theme_tags       TEXT[],          -- ['fear','identity','covenant']
    ADD COLUMN IF NOT EXISTS character_tags   TEXT[];          -- ['joseph','moses','david','jesus']

-- V1 에서 book 컬럼 있던 거 → book_code 로 백필
UPDATE scripture_passages
SET book_code = CASE book
    WHEN 'genesis'    THEN 'gen'
    WHEN 'exodus'     THEN 'ex'
    WHEN '1samuel'    THEN '1sam'
    WHEN '2samuel'    THEN '2sam'
    WHEN 'psalms'     THEN 'ps'
    WHEN 'proverbs'   THEN 'prov'
    WHEN 'ecclesiastes' THEN 'eccl'
    WHEN 'matthew'    THEN 'matt'
    WHEN 'mark'       THEN 'mark'
    WHEN 'luke'       THEN 'luke'
    WHEN 'john'       THEN 'john'
    ELSE LEFT(book, 4)
END
WHERE book_code IS NULL;

CREATE INDEX IF NOT EXISTS idx_scripture_theme_tags
    ON scripture_passages USING GIN(theme_tags);
CREATE INDEX IF NOT EXISTS idx_scripture_character_tags
    ON scripture_passages USING GIN(character_tags);

-- ============================================================
-- scripture_embeddings (pgvector HNSW)
-- ============================================================
CREATE TABLE IF NOT EXISTS scripture_embeddings (
    passage_id      BIGINT PRIMARY KEY REFERENCES scripture_passages(id) ON DELETE CASCADE,
    embedding       vector(1536) NOT NULL,             -- text-embedding-3-small
    embed_model     VARCHAR(50) NOT NULL DEFAULT 'text-embedding-3-small',
    embed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_scripture_embeddings_hnsw
    ON scripture_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
