-- V20260711105818: Theme 6·7 실천/성찰 기록 (TRACK-A-5-7-ACTION-GUIDANCE.md §3, §4).
--
-- 배경 (Track A · Theme 6/7 — "밖으로 한 걸음 디디기"):
--   Theme 6 (마음을 지키는 것, 잠 4:23) — 마음 지킴 실천 기록 (체크인/성찰) + 성경 카드.
--   Theme 7 (사람을 두려워하지 않는 것, 잠 29:25/사 51:7) — 거절/용기 실천 기록 + 성찰.
--   §7 측정: "행동 자유도" 신호 (경계 문장 작성·작은 행동 실행 누적).
--   §1 안전: R1 자해 키워드는 서버가 스캔 (기존 CrisisKeywordScanner 재사용).
--            평문 저장 금지 대상 아님 — 실천 노트는 사용자 자기 기록 (일기와 동일 취급).
--
-- 설계: proverbs_interactions 패턴을 따름 (BIGSERIAL PK, JSONB 성찰, 익명 user_id FK).
--       topic_id 로 Theme 6/7 을 한 테이블에 구분 (별 테이블 안 만듦 — 스키마 최소화).
--
-- Rollback:
--   DROP TABLE practice_reflections;

CREATE TABLE IF NOT EXISTS practice_reflections (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- 6 = 마음 지킴, 7 = 사람 두려움. Track A Topic id 와 일치.
    topic_id            SMALLINT NOT NULL CHECK (topic_id IN (6, 7)),
    -- 실천 종류.
    --   Theme 6: 'heart_checkin' | 'boundary_sentence'
    --   Theme 7: 'courage_act'   | 'thought_record'
    practice_kind       VARCHAR(30) NOT NULL,
    -- 사용자 자유 기록 (마음 흔든 사건 / 거절·용기 상황 서술). 일기와 동일 민감도.
    situation           TEXT,
    -- 성찰 응답 JSONB — kind 별 구조 유연.
    --   heart_checkin:     {"events":[{"text":..,"intensity":3,"my_responsibility":true}], ...}
    --   boundary_sentence: {"sentence":"...","sent":false}
    --   courage_act:       {"feared_person":"...","body_signal":"가슴 두근거림","did_it":true}
    --   thought_record:    {"thought":"...","distortion":"catastrophizing","balanced":"..."}
    reflection          JSONB,
    -- §7 측정 — "작은 행동" 실행/경계 문장 전송 체크 (누적 신호).
    action_taken        BOOLEAN NOT NULL DEFAULT FALSE,
    -- 선택된 성경 카드 (scripture_ref). 성경만 근거 — 외부 자료 필드 없음.
    scripture_ref       VARCHAR(50),
    -- 3차원 (영성/감성/이성). 'spiritual'|'emotional'|'rational'.
    dimension           VARCHAR(20),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_practice_reflections_user_time
    ON practice_reflections(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_practice_reflections_user_topic
    ON practice_reflections(user_id, topic_id, created_at DESC);

COMMENT ON TABLE practice_reflections IS
    'Theme 6(마음 지킴)·7(사람 두려움) 실천/성찰 기록. TRACK-A-5-7-ACTION-GUIDANCE §3·§4·§7.';
