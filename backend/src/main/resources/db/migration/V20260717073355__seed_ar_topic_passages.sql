-- V20260717073355: AR 1~7 토픽 카드가 참조하는 본문 시드 (404 수정).
--
-- 버그: AR 토픽 카드 시드(V20260522014800)는 scripture_ref('eccl-3:1' 등)를 *포인터* 로만
--       저장했고, 대응하는 본문 행은 scripture_passages 에 없었다.
--       → 프론트가 GET /api/scripture/{ref} 호출 시 E_SCRIPTURE_NOT_FOUND → HTTP 404.
--       토픽이 가리키는 참조 20개 중 18개가 미시드 상태(job-3:11 · ps-42:11 만 기존 시드에 존재).
--
-- 이 마이그레이션은 미시드 18개를 translation='modern' 으로 채운다.
-- 본문은 각 토픽 카드 body 에 이미 인라인으로 들어있던 *현대인의 성경* 인용(fair use, ≤10명)과 동일.
--
-- ⚠️ 저작권: 현대인의 성경 (생명의말씀사). MVP 비공개 테스터 ≤10명 fair use.
--   공개 출시 전 개역개정(대한성서공회 비영리 약관)으로 swap — translation='rev' 로 분리 예정.
--
-- ⚠️ 검토 필요(3건): gen-37:11 · gen-39:20 · num-20:11 은 토픽 body 가 *의역* 만 담고 있어
--   축자 인용이 없었다. 아래 본문은 현대인의 성경 기준 재구성 — 게시 전 원문 대조 요망.

INSERT INTO scripture_passages (reference, translation, book, book_code, chapter, verse_start, verse_end, text, character_tags) VALUES

-- Topic 1 — 일기와 묵상
('gen-37:11', 'modern', 'genesis', 'gen', 37, 11, 11,
 '''그의 형들은 그를 시기하였으나 그의 아버지는 그 말을 마음에 두었다.''', ARRAY['joseph']),  -- ⚠ 검토: 원문 대조
('ps-13:1',   'modern', 'psalms', 'ps', 13, 1, 1,
 '''여호와여 어느 때까지니이까 나를 영원히 잊으시나이까''', ARRAY['david']),
('gen-39:20', 'modern', 'genesis', 'gen', 39, 20, 20,
 '''요셉의 주인이 그를 잡아 왕의 죄수들을 가두는 감옥에 가두었다.''', ARRAY['joseph']),  -- ⚠ 검토: 원문 대조

-- Topic 2 — 잠언과 지혜
('prov-3:5',  'modern', 'proverbs', 'prov', 3, 5, 5,
 '''너는 마음을 다하여 여호와를 의뢰하고 네 명철을 의지하지 말라''', ARRAY['joseph']),
('prov-10:19','modern', 'proverbs', 'prov', 10, 19, 19,
 '''말이 많으면 허물을 면하기 어려우나 그 입술을 제어하는 자는 지혜가 있느니라''', ARRAY['david']),
('prov-4:23', 'modern', 'proverbs', 'prov', 4, 23, 23,
 '''모든 지킬 만한 것 중에 더욱 네 마음을 지키라 생명의 근원이 이에서 남이니라''', NULL),

-- Topic 3 — 전도서와 인생
('eccl-3:1',  'modern', 'ecclesiastes', 'eccl', 3, 1, 1,
 '''범사에 기한이 있고 천하만사가 다 때가 있나니''', NULL),
('matt-6:26', 'modern', 'matthew', 'matt', 6, 26, 26,
 '''공중의 새를 보라 심지도 않고 거두지도 않고 창고에 모아들이지도 아니하되 너희 하늘 아버지께서 기르시나니''', ARRAY['jesus']),
('eccl-12:13','modern', 'ecclesiastes', 'eccl', 12, 13, 13,
 '''일의 결국을 다 들었으니 하나님을 경외하고 그의 명령들을 지킬지어다 이것이 모든 사람의 본분이니라''', NULL),

-- Topic 4 — 시편과 감정
('ps-23:1',   'modern', 'psalms', 'ps', 23, 1, 1,
 '''여호와는 나의 목자시니 내게 부족함이 없으리로다''', ARRAY['david']),
('ps-90:1',   'modern', 'psalms', 'ps', 90, 1, 1,
 '''주여 주는 대대에 우리의 거처가 되셨나이다''', ARRAY['moses']),

-- Topic 5 — 욥과 진리
('job-38:4',  'modern', 'job', 'job', 38, 4, 4,
 '''내가 땅의 기초를 놓을 때 너는 어디 있었느냐''', ARRAY['job']),
('matt-27:46','modern', 'matthew', 'matt', 27, 46, 46,
 '''엘리 엘리 라마 사박다니''', ARRAY['jesus']),

-- Topic 6 — 마음 지키기
('matt-26:39','modern', 'matthew', 'matt', 26, 39, 39,
 '''내 아버지여 만일 할 만하시거든 이 잔을 내게서 지나가게 하옵소서 그러나 나의 원대로 마시옵고 아버지의 원대로 하옵소서''', ARRAY['jesus']),
('num-20:11', 'modern', 'numbers', 'num', 20, 11, 11,
 '''모세가 손을 들어 지팡이로 반석을 두 번 치자 물이 많이 솟아나와 백성과 그들의 짐승이 다 마셨다.''', ARRAY['moses']),  -- ⚠ 검토: 원문 대조

-- Topic 7 — 사람 두려워하지 않기
('prov-29:25','modern', 'proverbs', 'prov', 29, 25, 25,
 '''사람을 두려워하면 올무에 걸리게 되거니와 여호와를 의지하는 자는 안전하리라''', NULL),
('ps-57:1',   'modern', 'psalms', 'ps', 57, 1, 1,
 '''하나님이여 내게 은혜를 베푸소서''', ARRAY['david']),
('isa-51:7',  'modern', 'isaiah', 'isa', 51, 7, 7,
 '''너희 마음에 내 율법이 있는 자들아 사람의 비방을 두려워하지 말며 그들의 능욕에 놀라지 말라''', NULL)

ON CONFLICT (reference, translation) DO NOTHING;

-- 시드 검증 — 토픽 참조 20개가 모두 조회되는지 확인
DO $$
DECLARE missing INT;
BEGIN
    SELECT COUNT(*) INTO missing
    FROM (
        SELECT DISTINCT scripture_ref FROM topic_contents WHERE scripture_ref IS NOT NULL
    ) t
    LEFT JOIN scripture_passages s
        ON s.reference = t.scripture_ref AND s.translation = 'modern'
    WHERE s.id IS NULL;
    RAISE NOTICE 'AR 토픽 참조 중 미시드 본문: % 건', missing;
END $$;

-- ROLLBACK NOTES (운영 사고 시 reference; 자동 적용 금지)
-- DELETE FROM scripture_passages WHERE translation = 'modern' AND reference IN (
--   'gen-37:11','ps-13:1','gen-39:20','prov-3:5','prov-10:19','prov-4:23',
--   'eccl-3:1','matt-6:26','eccl-12:13','ps-23:1','ps-90:1','job-38:4',
--   'matt-27:46','matt-26:39','num-20:11','prov-29:25','ps-57:1','isa-51:7'
-- );
