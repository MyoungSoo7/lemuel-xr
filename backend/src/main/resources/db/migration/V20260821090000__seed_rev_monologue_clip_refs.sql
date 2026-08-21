-- V20260821090000: 모놀로그가 인용하는 절을 rev 로 마저 채우고, 각주 번호 오염 3행을 씻는다.
--
-- ## 왜
--
-- 이 커밋부터 프론트 모놀로그는 성경 자구를 **자기 소스에 갖고 있지 않다.** 화면에
-- 뜨는 본문은 전부 `/api/scripture` 응답에서 온다 (`frontend/src/lib/content/
-- scripture-quote.ts` 의 클립 — 절 참조 + 낱말 인덱스). 그래서 두 가지가 새로 필요하다.
--
-- 1) **인용된 절이 DB 에 다 있어야 한다.** `GetScripturePassageUseCase` 에는 폴백이
--    없다(`E_SCRIPTURE_NOT_FOUND`). 시드에 없는 절을 인용하면 그 모놀로그는 화면에서
--    통째로 실패한다. 클립이 닿는 절은 30개이고, 그중 17개가 `rev` 에 없었다 —
--    `V20260821030000` 은 `modern` 92행과 *같은 참조 집합* 만 덮었기 때문이다.
--    모놀로그는 그 92개 밖의 절도 인용한다(출 4:2~5 · 요 11:35 · 시 62:5 …).
--
-- 2) **시드 본문에 본문 아닌 것이 섞여 있으면 안 된다.** 지금까지 이 오염은
--    `ScenePassage` 블록에만 보였지만, 이제는 모놀로그 문장 한가운데에도 뜬다.
--
-- ## 각주 번호 오염 (UPDATE 3행)
--
-- `V20260821030000` 의 수거기(`scripture_text_check.py` 의 `fetch_chapter_krv`)는
-- 각주 표시를 `[ㄱ-ㅎ]\)` 로만 지웠다. 대한성서공회 GAE 는 관주를 `ㄱ)`, **난하주를
-- 숫자** 로 단다(`4)`). 그래서 숫자 표시가 본문인 척 시드에 들어앉았다:
--
--     jn-1:14    "… 아버지의 독생자의 영광이요 은혜와 4)진리가 충만하더라"
--     ruth-1:19  "… 이르기를 이이가 1)나오미냐 하는지라"
--     ruth-1:20  "… 나를 2)마라라 부르라 …"
--
-- 세 행 다 사용자에게 그대로 보인다. 수거기는 이 커밋에서 고쳤고(숫자 표시도 제거),
-- `scripts/krv_reference_hashes.json` 을 다시 떠서 세 행의 기준 해시도 함께 옮겼다.
-- 반대편 수거기(`check_monologue_quotes.py`)는 각주 *번호* 는 지우면서 각주 *본문*
-- 팝업(`div.D2`)을 안 지워 `요 1:14` 이 "…충만하더라 헬, 참이" 로 끝났었다 — 그쪽도
-- 같이 고쳐서 두 수거기가 이제 같은 자구를 낸다. 그 일치는
-- `scripts/check_monologue_quotes.py` 의 시드 대조 축이 매 CI 마다 다시 잰다.
--
-- ## 출처·방법 (사람이 옮겨 적지 않았다)
--
-- 17행 전부 `docs/verses-monologues-gae.txt` 에서 **기계로 옮겼다.** 그 표는
-- `python3 scripts/check_monologue_quotes.py --refresh` 가 대한성서공회 GAE HTML 을
-- 파싱해 만든다(2026-08-21 재수거).
--   https://www.bskorea.or.kr/bible/korbibReadpage.php?version=GAE&book=<code>&chap=<n>
--
-- ⚠️ 저작권: 개역개정판 © 대한성서공회. 비영리 이용 약관 범위 안에서 쓴다.
--
-- Rollback:
--   DELETE FROM scripture_passages WHERE translation = 'rev' AND reference IN (...위 17개...);
--   UPDATE 3행은 각주 번호를 도로 넣는 것이므로 되돌릴 이유가 없다.
--   되돌리면 모놀로그가 그 절을 못 받아 화면에 오류 자리표시자가 뜬다(조용히 비지 않는다).

-- ── 각주 번호 제거 (본문 아닌 것이 본문 행에 앉아 있었다) ─────────────────────
UPDATE scripture_passages SET text = '말씀이 육신이 되어 우리 가운데 거하시매 우리가 그의 영광을 보니 아버지의 독생자의 영광이요 은혜와 진리가 충만하더라'
 WHERE reference = 'jn-1:14' AND translation = 'rev';

UPDATE scripture_passages SET text = '이에 그 두 사람이 베들레헴까지 갔더라 베들레헴에 이를 때에 온 성읍이 그들로 말미암아 떠들며 이르기를 이이가 나오미냐 하는지라'
 WHERE reference = 'ruth-1:19' AND translation = 'rev';

UPDATE scripture_passages SET text = '나오미가 그들에게 이르되 나를 나오미라 부르지 말고 나를 마라라 부르라 이는 전능자가 나를 심히 괴롭게 하셨음이니라'
 WHERE reference = 'ruth-1:20' AND translation = 'rev';

-- ── 모놀로그 클립이 닿는데 rev 에 없던 절 (17행) ────────────────────────────
INSERT INTO scripture_passages (reference, translation, book, book_code, chapter, verse_start, verse_end, text, theme_tags, character_tags) VALUES
-- genesis (2행)
('gen-41:26', 'rev', 'genesis', 'gen', 41, 26, 26,
 '일곱 좋은 암소는 일곱 해요 일곱 좋은 이삭도 일곱 해니 그 꿈은 하나라', NULL, ARRAY['joseph']),
('gen-41:27', 'rev', 'genesis', 'gen', 41, 27, 27,
 '그 후에 올라온 파리하고 흉한 일곱 소는 칠 년이요 동풍에 말라 속이 빈 일곱 이삭도 일곱 해 흉년이니', NULL, ARRAY['joseph']),

-- exodus (9행)
('ex-3:11', 'rev', 'exodus', 'ex', 3, 11, 11,
 '모세가 하나님께 아뢰되 내가 누구이기에 바로에게 가며 이스라엘 자손을 애굽에서 인도하여 내리이까', NULL, ARRAY['moses']),
('ex-3:14', 'rev', 'exodus', 'ex', 3, 14, 14,
 '하나님이 모세에게 이르시되 나는 스스로 있는 자이니라 또 이르시되 너는 이스라엘 자손에게 이같이 이르기를 스스로 있는 자가 나를 너희에게 보내셨다 하라', NULL, ARRAY['moses']),
('ex-4:2', 'rev', 'exodus', 'ex', 4, 2, 2,
 '여호와께서 그에게 이르시되 네 손에 있는 것이 무엇이냐 그가 이르되 지팡이니이다', NULL, ARRAY['moses']),
('ex-4:3', 'rev', 'exodus', 'ex', 4, 3, 3,
 '여호와께서 이르시되 그것을 땅에 던지라 하시매 곧 땅에 던지니 그것이 뱀이 된지라 모세가 뱀 앞에서 피하매', NULL, ARRAY['moses']),
('ex-4:10', 'rev', 'exodus', 'ex', 4, 10, 10,
 '모세가 여호와께 아뢰되 오 주여 나는 본래 말을 잘 하지 못하는 자니이다 주께서 주의 종에게 명령하신 후에도 역시 그러하니 나는 입이 뻣뻣하고 혀가 둔한 자니이다', NULL, ARRAY['moses']),
('ex-4:11', 'rev', 'exodus', 'ex', 4, 11, 11,
 '여호와께서 그에게 이르시되 누가 사람의 입을 지었느냐 누가 말 못 하는 자나 못 듣는 자나 눈 밝은 자나 맹인이 되게 하였느냐 나 여호와가 아니냐', NULL, ARRAY['moses']),
('ex-4:12', 'rev', 'exodus', 'ex', 4, 12, 12,
 '이제 가라 내가 네 입과 함께 있어서 할 말을 가르치리라', NULL, ARRAY['moses']),
('ex-4:14', 'rev', 'exodus', 'ex', 4, 14, 14,
 '여호와께서 모세를 향하여 노하여 이르시되 레위 사람 네 형 아론이 있지 아니하냐 그가 말 잘 하는 것을 내가 아노라 그가 너를 만나러 나오나니 그가 너를 볼 때에 그의 마음에 기쁨이 있을 것이라', NULL, ARRAY['moses']),
('ex-4:15', 'rev', 'exodus', 'ex', 4, 15, 15,
 '너는 그에게 말하고 그의 입에 할 말을 주라 내가 네 입과 그의 입에 함께 있어서 너희들이 행할 일을 가르치리라', NULL, ARRAY['moses']),

-- 1_samuel (2행)
('1sam-17:29', 'rev', '1_samuel', '1sam', 17, 29, 29,
 '다윗이 이르되 내가 무엇을 하였나이까 어찌 이유가 없으리이까 하고', NULL, ARRAY['david']),
('1sam-17:37', 'rev', '1_samuel', '1sam', 17, 37, 37,
 '또 다윗이 이르되 여호와께서 나를 사자의 발톱과 곰의 발톱에서 건져내셨은즉 나를 이 블레셋 사람의 손에서도 건져내시리이다 사울이 다윗에게 이르되 가라 여호와께서 너와 함께 계시기를 원하노라', NULL, ARRAY['david']),

-- psalms (1행)
('ps-62:5', 'rev', 'psalms', 'ps', 62, 5, 5,
 '나의 영혼아 잠잠히 하나님만 바라라 무릇 나의 소망이 그로부터 나오는도다', NULL, ARRAY['david']),

-- john (3행)
('jn-11:35', 'rev', 'john', 'jn', 11, 35, 35,
 '예수께서 눈물을 흘리시더라', NULL, ARRAY['jesus']),
('jn-14:18', 'rev', 'john', 'jn', 14, 18, 18,
 '내가 너희를 고아와 같이 버려두지 아니하고 너희에게로 오리라', NULL, ARRAY['jesus']),
('jn-19:30', 'rev', 'john', 'jn', 19, 30, 30,
 '예수께서 신 포도주를 받으신 후에 이르시되 다 이루었다 하시고 머리를 숙이니 영혼이 떠나가시니라', NULL, ARRAY['jesus'])

ON CONFLICT (reference, translation) DO NOTHING;

-- 확인 — 모놀로그 클립이 닿는 절 30개가 전부 rev 로 있는가.
-- (이 목록은 `scripts/check_monologue_quotes.py` 의 시드 대조 축이 프론트 소스에서
--  다시 뽑아 매 CI 마다 재확인한다. 여기 것은 배포 시점의 잠금이다.)
DO $$
DECLARE
    missing TEXT[];
BEGIN
    SELECT array_agg(r) INTO missing FROM unnest(ARRAY[
        '1sam-17:29','1sam-17:37','1sam-17:45','1sam-17:47',
        'ex-3:11','ex-3:12','ex-3:14','ex-4:2','ex-4:3','ex-4:10','ex-4:11',
        'ex-4:12','ex-4:14','ex-4:15','ex-14:13',
        'gen-41:26','gen-41:27','gen-45:5','gen-50:20',
        'jn-1:14','jn-11:35','jn-14:6','jn-14:18','jn-19:30','jn-20:15',
        'lk-22:42','matt-5:3','mk-1:41','ps-23:1','ps-62:5'
    ]) AS r
     WHERE NOT EXISTS (SELECT 1 FROM scripture_passages p
                        WHERE p.translation = 'rev' AND p.reference = r);
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION '모놀로그가 인용하는 절 %건이 rev 에 없다 — 그 화면은 본문을 못 받는다: %',
            array_length(missing, 1), missing;
    END IF;

    -- 각주 번호가 남아 있으면 그 숫자가 성경 본문인 척 화면에 뜬다.
    IF EXISTS (SELECT 1 FROM scripture_passages
                WHERE translation = 'rev' AND text ~ '[0-9]\)') THEN
        RAISE EXCEPTION '개역개정 시드에 각주 번호가 남아 있다 (예: "4)")';
    END IF;
END $$;
