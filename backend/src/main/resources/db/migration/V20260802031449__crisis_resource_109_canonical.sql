-- V20260802031449: SAFETY — 자살예방 상담전화 정번호를 109 로 교정 (2024-01-01 통합 시행)
--
-- 근거 (1차·공식 출처만 인용)
--   [1] 보건복지부 보도자료 — "분산된 자살예방 상담전화 1월 1일부터 '109'로 통합 운영"
--       https://www.mohw.go.kr/board.es?act=view&bid=0027&cg_code=&list_no=1479607&mid=a10503000000&tag=
--   [2] 보건복지상담센터 FAQ — https://www.129.go.kr/109
--       "2024년 1월 1일부터 자살예방상담은 109로 모두 통합되었으며, 정신건강상담전화(1577-0199),
--        청소년전화(1388), 여성긴급전화(1366) 등은 종전과 같이 본연의 역할에 따라
--        담당 분야 상담을 수행하고 있습니다."
--
-- 무엇이 잘못돼 있었나
--   V7__safety_domain.sql 의 seed 는 1393 을 ('KR','ko-KR') 의 'suicide' priority 1 정본으로 두었다.
--   1393 은 2024-01-01 자로 109 에 통합되면서 *자살예방 상담 정번호의 지위를 잃은* 번호다.
--   위기 상태의 사용자가 직접 읽는 카피에 구 번호가 정본으로 박혀 있던 상태였다.
--
-- 무엇을 바꾸나
--   · 109 를 ('KR','ko-KR') 의 priority 1 'suicide' 자원으로 세운다 (= 앱의 default 위기 연락처).
--   · 1393 은 *삭제하지 않고* '구 번호·보조' 로 라벨을 바꿔 뒤로 내린다.
--
-- 무엇을 바꾸지 않나 (중요 — 오해 금지)
--   · 1577-0199(정신건강상담) · 1388(청소년) · 1366(여성긴급) · 1588-9191(생명의전화) 은 *폐지되지 않았다*.
--     각자 담당 분야 상담을 종전대로 수행한다 [2] → 행을 그대로 유지한다.
--   · 1393 이 *불통* 이라는 근거는 어느 출처에도 없다. 통합으로 정번호 지위를 잃었을 뿐이므로
--     제거·비활성화가 아니라 강등(보조 표기)으로 처리한다.
--
-- 재실행 안전성: 아래 문장은 모두 멱등이다. V7 seed 가 이미 적용된 DB 와
-- 그렇지 않은 DB 어느 쪽에서도 같은 최종 상태로 수렴한다.

-- ── 1) 109 — 자살예방 상담 정번호 (없으면 추가) ─────────────────────────────
INSERT INTO crisis_resources (
    region, locale, name, contact_type, contact_value, description, hours, category, priority, active
)
SELECT 'KR', 'ko-KR', '자살예방 상담전화', 'phone', '109',
       '24시간 자살예방 상담 — 2024-01-01 통합 정번호', '24/7', 'suicide', 1, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM crisis_resources
    WHERE region = 'KR' AND locale = 'ko-KR' AND contact_value = '109'
);

-- 이미 수동 시드된 DB 도 같은 상태로 맞춘다 (priority 1 · active).
UPDATE crisis_resources
SET name          = '자살예방 상담전화',
    contact_type  = 'phone',
    description   = '24시간 자살예방 상담 — 2024-01-01 통합 정번호',
    hours         = '24/7',
    category      = 'suicide',
    priority      = 1,
    active        = TRUE
WHERE region = 'KR' AND locale = 'ko-KR' AND contact_value = '109';

-- ── 2) 1393 — 구 번호로 강등 (삭제 X · 비활성 X) ────────────────────────────
UPDATE crisis_resources
SET name        = '자살예방상담(구 번호·보조)',
    description = '2024-01-01 109 로 통합된 종전 번호 — 정번호는 109',
    priority    = 6
WHERE region = 'KR' AND locale = 'ko-KR' AND contact_value = '1393';

-- ROLLBACK NOTES (운영 사고 시 reference; 자동 적용 금지)
--   UPDATE crisis_resources
--      SET name = '자살예방상담전화',
--          description = '24시간 자살·정신건강 위기 상담',
--          priority = 1
--    WHERE region = 'KR' AND locale = 'ko-KR' AND contact_value = '1393';
--   DELETE FROM crisis_resources
--    WHERE region = 'KR' AND locale = 'ko-KR' AND contact_value = '109';
--   ※ 되돌리면 앱이 다시 구 번호를 자살예방 정번호로 안내하게 된다. 근거 [1][2] 확인 후에만.
