-- scripture_embeddings 의 모델 메타데이터를 실제로 쓰는 모델에 맞춘다.
--
-- 무엇이 어긋나 있었나 — V6 는 이 테이블을 OpenAI `text-embedding-3-small`(1536차원) 전제로 만들었다.
-- 그런데 이 프로젝트가 실제로 호출하는 임베딩은 `gemini-embedding-001` 이다
-- (GroundingEvalProperties.embeddingModel 기본값, 2026-07-19 결정). 그래서 열 기본값이
-- 거짓 라벨을 달고 있었다. 지금 이 테이블은 0행이라 backfill 대상이 없고, 첫 적재부터
-- 틀린 모델명이 붙는 것만 막으면 된다.
--
-- V6 를 직접 고치지 않는 이유는 이미 운영에 적용돼 체크섬이 고정됐기 때문이다.

ALTER TABLE scripture_embeddings
    ALTER COLUMN embed_model SET DEFAULT 'gemini-embedding-001';

-- 차원(1536)은 그대로 둔다. gemini-embedding-001 의 기본 출력은 3072 차원이지만,
-- pgvector 의 HNSW 인덱스는 대상 열의 차원을 2000 으로 제한한다 — 운영 DB(pgvector 0.8.2)에서
-- 직접 확인: "column cannot have more than 2000 dimensions for hnsw index".
-- 3072 로 넓히면 아래 idx_scripture_embeddings_hnsw 를 유지할 수 없으므로,
-- 애플리케이션이 outputDimensionality=1536 으로 잘라서 넣는다(Matryoshka 절단 후 L2 정규화).
-- 근거: https://ai.google.dev/gemini-api/docs/embeddings — 권장 차원 768/1536/3072,
-- gemini-embedding-001 은 3072 이외 차원의 수동 정규화 필요.
COMMENT ON COLUMN scripture_embeddings.embedding IS
    'gemini-embedding-001 을 outputDimensionality=1536 으로 절단 후 L2 정규화한 벡터. HNSW 2000 차원 상한 때문에 3072 를 쓰지 않는다.';

COMMENT ON COLUMN scripture_embeddings.embed_model IS
    '이 행을 만든 임베딩 모델. 모델을 바꾸면 차원도 함께 검토해야 한다(열은 vector(1536) 고정).';
