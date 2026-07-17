-- V20260718004903: AR 토픽 본문 3건 원문 대조 교정.
--
-- 배경: V20260717073355 에서 gen-37:11 · gen-39:20 · num-20:11 은 토픽 카드 body 가
--   축자 인용이 아니라 *의역* 만 담고 있어, 당시 현대인의 성경 기준으로 재구성해 시드하며
--   '⚠ 검토: 원문 대조' 표시를 남겼다.
--
-- 이 마이그레이션은 그 3건을 현대인의 성경(Korean Living Bible, 생명의말씀사) 원문과 대조해 교정한다.
-- 출처: BibleGateway KLB (Genesis 37:11, Genesis 39:20, Numbers 20:11).
--
-- ⚠️ 이전 마이그레이션(V20260717073355)은 이미 배포/적용되었을 수 있으므로 in-place 수정 금지
--   (Flyway 체크섬 무결성). 별도 UPDATE 마이그레이션으로 교정한다.
--
-- ⚠️ 저작권: 현대인의 성경. MVP 비공개 테스터 ≤10명 fair use. 공개 출시 전 개역개정 swap 대상.

-- 창 37:11 — "새겨" (기존: "그 말을 마음에 두었다" → 원문: "마음에 새겨 두었다")
UPDATE scripture_passages
SET text = '''요셉의 형들은 그를 시기하였으나 그의 아버지는 그 말을 마음에 새겨 두었다.'''
WHERE reference = 'gen-37:11' AND translation = 'modern';

-- 창 39:20 — 원문 문장으로 교정 (궁중 죄수들을 가두는 곳)
UPDATE scripture_passages
SET text = '''요셉을 잡아 감옥에 처넣었는데 그 곳은 궁중 죄수들을 가두는 곳이었다.'''
WHERE reference = 'gen-39:20' AND translation = 'modern';

-- 민 20:11 — 원문 문장으로 교정 (분수처럼 솟구쳐나와)
UPDATE scripture_passages
SET text = '''지팡이를 들어 바위를 두 번 쳤다. 그러자 물이 분수처럼 솟구쳐나와 백성과 그들의 짐승이 다 그 물을 마셨다.'''
WHERE reference = 'num-20:11' AND translation = 'modern';

-- 검증 — 교정 3건이 모두 반영됐는지 확인
DO $$
DECLARE stale INT;
BEGIN
    SELECT COUNT(*) INTO stale
    FROM scripture_passages
    WHERE translation = 'modern'
      AND reference IN ('gen-37:11', 'gen-39:20', 'num-20:11')
      AND text LIKE '%마음에 두었다%';  -- 교정 전 문구 잔존 여부
    RAISE NOTICE 'AR 본문 교정 후 잔존 구문: % 건', stale;
END $$;

-- ROLLBACK NOTES (운영 사고 시 reference; 자동 적용 금지)
-- 교정 이전 값(V20260717073355 재구성본)으로 되돌리려면:
-- UPDATE scripture_passages SET text = '''그의 형들은 그를 시기하였으나 그의 아버지는 그 말을 마음에 두었다.''' WHERE reference='gen-37:11' AND translation='modern';
-- UPDATE scripture_passages SET text = '''요셉의 주인이 그를 잡아 왕의 죄수들을 가두는 감옥에 가두었다.''' WHERE reference='gen-39:20' AND translation='modern';
-- UPDATE scripture_passages SET text = '''모세가 손을 들어 지팡이로 반석을 두 번 치자 물이 많이 솟아나와 백성과 그들의 짐승이 다 마셨다.''' WHERE reference='num-20:11' AND translation='modern';
