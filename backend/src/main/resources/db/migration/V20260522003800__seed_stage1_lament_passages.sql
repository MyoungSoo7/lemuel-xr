-- V20260522003800: Stage 1 (욥·엘리야·시편 비탄) 본문 시드.
--
-- 배경 (2026-05-22 mission 재정의):
--   타겟 = B (Recovery) — 우울증 안정기 사용자
--   Stage 1 = 절망의 *언어 허락* (욥기·엘리야·시편 88 등)
--   콘텐츠 = 큐레이션만 (LLM 생성 OFF until 임상 자문 영입)
--
-- ⚠️ 저작권: 현대인의 성경 (생명의말씀사). MVP 비공개 테스터 ≤10명 fair use.
-- 공개 출시 전 라이선스 협의 OR 개역개정 (대한성서공회 비영리 약관) 으로 swap.
-- translation='modern' 으로 분리해 추후 'rev' 로 swap 가능하게.
--
-- Rollback: DELETE FROM scripture_passages WHERE reference IN (...) — 본문만 제거,
-- 인덱스 영향 없음.

-- ============================================================
-- 욥기 비탄 (욥 3:1-26, 6:8-9, 7:11-21, 23:2-9, 38:1-7, 42:1-6)
-- ============================================================
INSERT INTO scripture_passages (reference, translation, book_code, book, chapter, verse_start, verse_end, text, theme_tags, character_tags) VALUES
('job-3:3',  'modern', 'job', 'JOB', 3,  3,  3,  '''내가 태어난 날이여 차라리 없었더라면 좋았을 것을, 내가 임신되던 그 밤이여 사라져 버려라.''', ARRAY['despair','birth_lament'], ARRAY['job']),
('job-3:11', 'modern', 'job', 'JOB', 3, 11, 13, '''어째서 내가 모태에서 죽지 않았으며 어머니의 배에서 나오자마자 숨을 거두지 않았는가? ... 그랬더라면 지금쯤 평안히 누워 잠들었을 것을.''', ARRAY['despair','death_wish'], ARRAY['job']),
('job-6:8',  'modern', 'job', 'JOB', 6,  8,  9,  '''내 소원이 이뤄졌으면! 내가 바라는 것을 하나님이 들어 주셨으면! 그것은 곧 하나님이 나를 박살내시고, 손을 들어 나를 끊어 버리시는 것이다.''', ARRAY['despair','death_wish','prayer'], ARRAY['job']),
('job-7:11', 'modern', 'job', 'JOB', 7, 11, 11, '''그러므로 내가 내 입을 다물지 않고 내 영혼의 괴로움 가운데서 말하며 내 마음의 쓰라림 속에서 부르짖겠다.''', ARRAY['lament_language_permitted'], ARRAY['job']),
('job-23:3', 'modern', 'job', 'JOB', 23, 3, 5,  '''내가 하나님을 만날 수만 있다면 그분의 자리에 나아가 내 사정을 그 앞에 펴 놓고 변론하고 싶다. ... 내가 그분의 말씀을 듣겠고 그분의 응답을 알게 될 것이다.''', ARRAY['silence_of_god','seeking'], ARRAY['job']),
('job-38:1', 'modern', 'job', 'JOB', 38, 1, 7,  '''여호와께서 폭풍 가운데서 욥에게 대답하셨다. ... 내가 땅의 기초를 놓을 때에 너는 어디 있었느냐?''', ARRAY['god_speaks','cosmic_perspective'], ARRAY['job']),
('job-42:5', 'modern', 'job', 'JOB', 42, 5, 6,  '''내가 주께 대하여 귀로 듣기만 하였더니, 이제는 눈으로 주를 뵙습니다. 그러므로 내 자신을 부끄럽게 여기며 티끌과 재 가운데서 회개합니다.''', ARRAY['encounter','restoration'], ARRAY['job'])
ON CONFLICT (reference, translation) DO NOTHING;

-- ============================================================
-- 엘리야 (왕상 19:1-18 핵심 발췌 — 죽음 갈구 → 천사의 보살핌 → 세미한 음성)
-- ============================================================
INSERT INTO scripture_passages (reference, translation, book_code, book, chapter, verse_start, verse_end, text, theme_tags, character_tags) VALUES
('1kgs-19:4', 'modern', '1kgs', '1_KINGS', 19, 4, 4,
 '''엘리야는 광야로 하룻길을 들어가 한 로뎀나무 아래 앉아 죽기를 간청하며 말하였다. ''여호와여 이제 됐습니다. 내 생명을 거두소서. 나는 내 조상들보다 나은 것이 없습니다.''''',
 ARRAY['despair','death_wish','burnout'], ARRAY['elijah']),
('1kgs-19:5', 'modern', '1kgs', '1_KINGS', 19, 5, 8,
 '''그가 로뎀나무 아래에서 잠들었을 때 한 천사가 그를 어루만지며 말하였다. ''일어나서 먹어라.'' ... 그가 일어나서 먹고 마신 후 그 음식의 힘을 의지하여 사십 주 사십 야를 걸어 하나님의 산 호렙에 이르렀다.''',
 ARRAY['recovery','basic_care_first','food_rest'], ARRAY['elijah']),
('1kgs-19:11','modern', '1kgs', '1_KINGS', 19, 11, 13,
 '''크고 강한 바람이 산을 가르고 바위를 부수었으나 여호와는 바람 가운데 계시지 않았으며 지진 후에 불이 있었으나 여호와는 불 가운데도 계시지 않았다. 불 후에는 세미한 소리가 있었다. ... ''엘리야야 네가 어찌하여 여기 있느냐?''''',
 ARRAY['still_small_voice','encounter'], ARRAY['elijah']),
('1kgs-19:18','modern', '1kgs', '1_KINGS', 19, 18, 18,
 '''내가 이스라엘 가운데 칠천 명을 남기리니 다 바알에게 무릎을 꿇지 아니하고 그에게 입맞추지 아니한 자라.''',
 ARRAY['not_alone','community'], ARRAY['elijah'])
ON CONFLICT (reference, translation) DO NOTHING;

-- ============================================================
-- 시편 비탄 — 시 22 (다윗 / 십자가에서 예수 인용), 시 42, 시 88 (답 없는 시편), 시 130, 시 137
-- ============================================================
INSERT INTO scripture_passages (reference, translation, book_code, book, chapter, verse_start, verse_end, text, theme_tags, character_tags) VALUES
('ps-22:1',  'modern', 'ps',  'PSALMS', 22,  1,  2,
 '''내 하나님이여, 내 하나님이여, 어찌하여 나를 버리셨습니까? 어찌하여 나를 멀리하셔서 돕지 아니하시며 내 신음 소리를 듣지 아니하십니까? 내 하나님이여, 내가 낮에도 부르짖고 밤에도 잠잠하지 아니하나 응답하지 아니하십니다.''',
 ARRAY['lament','god_absent','jesus_quoted'], ARRAY['david','jesus']),
('ps-22:14','modern', 'ps',  'PSALMS', 22, 14, 15,
 '''나는 물같이 쏟아졌으며 내 모든 뼈는 어그러졌고 내 마음은 밀랍 같아서 내 속에서 녹았습니다.''',
 ARRAY['bodily_lament','psychosomatic'], ARRAY['david']),
('ps-42:1',  'modern', 'ps',  'PSALMS', 42,  1,  3,
 '''사슴이 시냇물을 갈망함같이 내 영혼이 주를 찾기에 갈급합니다. 내 영혼이 하나님 곧 살아 계시는 하나님을 갈망하나니, 내가 언제 가서 하나님 앞에 나아가 뵈올 수 있을까요? 사람들이 종일 ''네 하나님이 어디 있느냐?'' 하며 조롱할 때 내 눈물이 주야로 내 음식이 되었습니다.''',
 ARRAY['longing','dark_night'], ARRAY['korah']),
('ps-42:11','modern', 'ps',  'PSALMS', 42, 11, 11,
 '''내 영혼아 네가 어찌하여 낙망하며 어찌하여 내 속에서 불안해하는가? 너는 하나님께 소망을 두라. 나는 여전히 그분을 찬양하리니 그는 내 얼굴의 도움이시요 나의 하나님이시다.''',
 ARRAY['self_dialogue','hope_amid_lament'], ARRAY['korah']),
('ps-88:1',  'modern', 'ps',  'PSALMS', 88,  1,  3,
 '''여호와, 나를 구원하시는 하나님이시여, 내가 주야로 주께 부르짖나이다. 내 기도가 주 앞에 이르게 하시고 내 부르짖음에 귀를 기울여 주십시오. 내 영혼이 고난으로 가득 차고 내 생명이 죽음에 가까이 이르렀습니다.''',
 ARRAY['darkest_psalm','no_resolution'], ARRAY['heman']),
('ps-88:13','modern', 'ps',  'PSALMS', 88, 13, 14,
 '''여호와여, 그러나 내가 주께 부르짖었으며 아침에 내 기도가 주 앞에 이르렀습니다. 여호와여, 어찌하여 나를 버리시며 어찌하여 주의 얼굴을 내게서 숨기시나이까?''',
 ARRAY['unanswered','persistent_cry'], ARRAY['heman']),
('ps-88:18','modern', 'ps',  'PSALMS', 88, 18, 18,
 '''내 사랑하는 자와 친구들을 주께서 내게서 멀리하시며 내가 아는 자를 흑암에 두셨습니다.''',
 ARRAY['ends_in_darkness','no_resolution_validated'], ARRAY['heman']),
('ps-130:1', 'modern', 'ps',  'PSALMS', 130, 1,  4,
 '''여호와여, 내가 깊은 데서 주께 부르짖었습니다. 주여, 내 소리를 들으시고 내 간구하는 소리에 귀를 기울이소서. ... 그러나 사유하심이 주께 있으므로 사람들이 주를 경외하게 됩니다.''',
 ARRAY['depths','forgiveness'], ARRAY['unknown']),
('ps-137:1', 'modern', 'ps',  'PSALMS', 137, 1,  4,
 '''우리가 바벨론의 강가에 앉아서 시온을 기억하며 울었도다. ... 어찌 우리가 이방 땅에서 여호와의 노래를 부를까?''',
 ARRAY['exile','collective_lament'], ARRAY['exile_community'])
ON CONFLICT (reference, translation) DO NOTHING;

-- ============================================================
-- 시드 검증
-- ============================================================
DO $$
DECLARE
    job_count INT;
    elijah_count INT;
    psalm_count INT;
BEGIN
    SELECT COUNT(*) INTO job_count FROM scripture_passages WHERE book_code = 'job' AND translation = 'modern';
    SELECT COUNT(*) INTO elijah_count FROM scripture_passages WHERE reference LIKE '1kgs-19:%' AND translation = 'modern';
    SELECT COUNT(*) INTO psalm_count FROM scripture_passages
        WHERE reference IN ('ps-22:1','ps-22:14','ps-42:1','ps-42:11','ps-88:1','ps-88:13','ps-88:18','ps-130:1','ps-137:1')
          AND translation = 'modern';

    RAISE NOTICE 'Stage 1 시드 — 욥: % verses, 엘리야: %, 시편 비탄: %', job_count, elijah_count, psalm_count;

    IF job_count < 7 OR elijah_count < 4 OR psalm_count < 9 THEN
        RAISE WARNING '시드 count 미달 — 본문 추가 작업 필요할 수 있음';
    END IF;
END $$;
