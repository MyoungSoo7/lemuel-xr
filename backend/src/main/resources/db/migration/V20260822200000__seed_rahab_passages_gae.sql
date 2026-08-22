-- V20260822200000: 라합 미션이 가리키는 참조 23개를 시드한다.
--
-- ## 왜 지금인가
--
-- `scripts/scripture_ref_check.py` 가 라합을 이렇게 판정하고 있었다 —
-- `[FAIL] rahab — 열리지 않는 참조 — 행없음 24`. 시나리오의 `scripture_ref` ·
-- `additional_refs` 가 전부 `scripture_passages` 에 행이 없는 포인터였다는 뜻이다.
-- (그 24 중 하나가 `jos-6:17` 이었고, 그것은 시드가 아니라 **참조 목록에서** 빠졌다 —
--  아래 「수 6:17 은 왜 없나」. 그래서 남은 참조 23개에 시드 23행이 1:1 로 대응한다.)
-- 라합은 아직 `Character` enum 에 없어서 화면이 열리지 않지만, 끊어진 포인터를
-- 안고 여는 것과 갚고 여는 것은 다르다. 여기서 갚는다.
--
-- ## 출처·방법 (사람이 옮겨 적지 않았다)
--
-- 대한성서공회 개역개정 장 페이지를 기계 파싱했다 (2026-08-22 수거).
--   https://www.bskorea.or.kr/bible/korbibReadpage.php?version=GAE&book=<code>&chap=<n>
-- 수거기는 `scripts/scripture_text_check.py` 의 `fetch_chapter_krv` 를 그대로 썼다 —
-- V20260822120000 · V20260822160000 과 같은 함수다. 수거 장은 수 2(24절) · 수 6(27절) ·
-- 약 2(26절) · 마 1(25절).
--
-- **독립 검증**: 수거한 23행을 `docs/verses-rahab.txt` 의 `## USED` 자구와 전수
-- 대조했다 (23/23 일치).
--
-- ## 수 6:17 은 왜 없나 — 반절만 채택된 절이라서다
--
-- `docs/VERSES-RAHAB-GAE.md` 의 수 6 판정표는 이 절을 **구 단위로 쪼갠다**:
--   · 6:17 전반절 "이 성과 그 가운데에 있는 모든 것은 여호와께 온전히 바치되" — **배제**(헤렘 명령)
--   · 6:17 후반절 "기생 라합과 …그가 숨겨 주었음이니라"                        — **채택**('기생' 호칭 두 번째 출현)
--
-- 이 표에 맞는 시드 행은 **없다.** 전절을 넣으면 배제된 헤렘 구가 딸려 들어오고,
-- 후반절만 넣으면 `translation='rev'` 라벨이 거짓이 되어 `scripture_text_check.py`
-- 가 FAIL 한다(그리고 화면에 뜨는 자구가 축자가 아니게 된다). 반절 키
-- (`jos-6:17b`)는 `docs/SCRIPTURE-REF-CONVENTION.md` 가 형식불가로 막는다 —
-- `scripture_ref_check.py` 가 `ruth-1:21a` 를 잡던 그 규칙이다.
--
-- 처음엔 룻의 전례(`ruth.yml:423` 의 `1:21a` → `additional_refs: ["ruth-1:21"]`)를
-- 따라 전절을 넣었다. 그건 **틀린 선택이었다.** 화면은 씬의 `scripture_ref` 뿐 아니라
-- `additional_refs` 도 전부 연다(`ScenePassage` — `frontend/src/app/mission-passage.test.tsx`
-- 의 셋째 케이스가 그 배선을 강제한다). 즉 Scene 5 에 `jos-6:17` 을 두면
-- **헤렘 명령이 화면에 자구 그대로 뜬다.** 룻의 1:21 은 배제 절이 아니라서 같은 값을
-- 치르지 않았을 뿐이다.
--
-- 그래서 참조로는 열지 않는다. 6:17b 는 **자막 한 줄로만** 남는다(`rahab.yml` Scene 5
-- 의 `verse_ref: 수 6:17b`). 그 자구가 정본과 같은지는 저작 게이트 g0b 와
-- `RahabWithheldNarrativeTest` 가 `docs/verses-rahab.txt` 에 대고 잰다 — 이 표가
-- 아니라 그쪽이 그 한 줄의 근거다.
--
-- (수거 단계에서는 전절도 함께 받아 `## EXCLUDED` 의 6:17a + `## USED` 의 6:17b 를
--  공백 하나로 이은 것과 축자 일치하는지 대조했다. 일치했고, 그 확인을 마친 뒤 뺐다.)
--
-- ## 범위 밖
--
-- 수 2:10 · 2:16 · 2:17 · 2:20 · 2:22-24 와 수 6 의 나머지 절은 한 행도 넣지 않는다.
-- 인용 목록에서 키를 만들지 장 전체를 붓지 않는다 — 특히 수 6:1-16 · 6:18-21 · 6:24 ·
-- 6:26 은 `docs/SERIES-GRACE.md` §4 가 미션 범위 밖으로 배제한 절이다.
-- 히 11:31 도 넣지 않는다 — `docs/VERSES-RAHAB-GAE.md` 가 "순종하지 아니한 자와 함께
-- 멸망하지 아니하였도다"(전멸) 때문에 미션에서 뺀 절이고, 시나리오도 인용하지 않는다.
--
-- ⚠️ 저작권: 개역개정판 (C) 대한성서공회. 비영리 이용 약관 범위 안에서 쓴다.
--
-- 되돌리기:
--   DELETE FROM scripture_passages
--    WHERE translation = 'rev' AND character_tags && ARRAY['rahab'];

-- 컬럼명은 V1__init_schema.sql + V6__scripture_embeddings.sql 의 실제 스키마다
-- (theme_tags · character_tags). 앞선 시드 V20260822160000 과 같은 목록을 쓴다.
INSERT INTO scripture_passages
  (reference, translation, book, book_code, chapter, verse_start, verse_end, text, theme_tags, character_tags)
VALUES
('jos-2:1', 'rev', 'joshua', 'jos', 2, 1, 1,
 '눈의 아들 여호수아가 싯딤에서 두 사람을 정탐꾼으로 보내며 이르되 가서 그 땅과 여리고를 엿보라 하매 그들이 가서 라합이라 하는 기생의 집에 들어가 거기서 유숙하더니', NULL, ARRAY['rahab']),
('jos-2:2', 'rev', 'joshua', 'jos', 2, 2, 2,
 '어떤 사람이 여리고 왕에게 말하여 이르되 보소서 이 밤에 이스라엘 자손 중의 몇 사람이 이 땅을 정탐하러 이리로 들어왔나이다', NULL, ARRAY['rahab']),
('jos-2:3', 'rev', 'joshua', 'jos', 2, 3, 3,
 '여리고 왕이 라합에게 사람을 보내어 이르되 네게로 와서 네 집에 들어간 그 사람들을 끌어내라 그들은 이 온 땅을 정탐하러 왔느니라', NULL, ARRAY['rahab']),
('jos-2:4', 'rev', 'joshua', 'jos', 2, 4, 4,
 '그 여인이 그 두 사람을 이미 숨긴지라 이르되 과연 그 사람들이 내게 왔었으나 그들이 어디에서 왔는지 나는 알지 못하였고', NULL, ARRAY['rahab']),
('jos-2:5', 'rev', 'joshua', 'jos', 2, 5, 5,
 '그 사람들이 어두워 성문을 닫을 때쯤 되어 나갔으니 어디로 갔는지 내가 알지 못하나 급히 따라가라 그리하면 그들을 따라잡으리라 하였으나', NULL, ARRAY['rahab']),
('jos-2:6', 'rev', 'joshua', 'jos', 2, 6, 6,
 '그가 이미 그들을 이끌고 지붕에 올라가서 그 지붕에 벌여 놓은 삼대에 숨겼더라', NULL, ARRAY['rahab']),
('jos-2:7', 'rev', 'joshua', 'jos', 2, 7, 7,
 '그 사람들은 요단 나루터까지 그들을 쫓아갔고 그들을 뒤쫓는 자들이 나가자 곧 성문을 닫았더라', NULL, ARRAY['rahab']),
('jos-2:8', 'rev', 'joshua', 'jos', 2, 8, 8,
 '또 그들이 눕기 전에 라합이 지붕에 올라가서 그들에게 이르러', NULL, ARRAY['rahab']),
('jos-2:9', 'rev', 'joshua', 'jos', 2, 9, 9,
 '말하되 여호와께서 이 땅을 너희에게 주신 줄을 내가 아노라 우리가 너희를 심히 두려워하고 이 땅 주민들이 다 너희 앞에서 간담이 녹나니', NULL, ARRAY['rahab']),
('jos-2:11', 'rev', 'joshua', 'jos', 2, 11, 11,
 '우리가 듣자 곧 마음이 녹았고 너희로 말미암아 사람이 정신을 잃었나니 너희의 하나님 여호와는 위로는 하늘에서도 아래로는 땅에서도 하나님이시니라', NULL, ARRAY['rahab']),
('jos-2:12', 'rev', 'joshua', 'jos', 2, 12, 12,
 '그러므로 이제 청하노니 내가 너희를 선대하였은즉 너희도 내 아버지의 집을 선대하도록 여호와로 내게 맹세하고 내게 증표를 내라', NULL, ARRAY['rahab']),
('jos-2:13', 'rev', 'joshua', 'jos', 2, 13, 13,
 '그리고 나의 부모와 나의 남녀 형제와 그들에게 속한 모든 사람을 살려 주어 우리 목숨을 죽음에서 건져내라', NULL, ARRAY['rahab']),
('jos-2:14', 'rev', 'joshua', 'jos', 2, 14, 14,
 '그 사람들이 그에게 이르되 네가 우리의 이 일을 누설하지 아니하면 우리의 목숨으로 너희를 대신할 것이요 여호와께서 우리에게 이 땅을 주실 때에는 인자하고 진실하게 너를 대우하리라', NULL, ARRAY['rahab']),
('jos-2:15', 'rev', 'joshua', 'jos', 2, 15, 15,
 '라합이 그들을 창문에서 줄로 달아 내리니 그의 집이 성벽 위에 있으므로 그가 성벽 위에 거주하였음이라', NULL, ARRAY['rahab']),
('jos-2:18', 'rev', 'joshua', 'jos', 2, 18, 18,
 '우리가 이 땅에 들어올 때에 우리를 달아 내린 창문에 이 붉은 줄을 매고 네 부모와 형제와 네 아버지의 가족을 다 네 집에 모으라', NULL, ARRAY['rahab']),
('jos-2:19', 'rev', 'joshua', 'jos', 2, 19, 19,
 '누구든지 네 집 문을 나가서 거리로 가면 그의 피가 그의 머리로 돌아갈 것이요 우리는 허물이 없으리라 그러나 누구든지 너와 함께 집에 있는 자에게 손을 대면 그의 피는 우리의 머리로 돌아오려니와', NULL, ARRAY['rahab']),
('jos-2:21', 'rev', 'joshua', 'jos', 2, 21, 21,
 '라합이 이르되 너희의 말대로 할 것이라 하고 그들을 보내어 가게 하고 붉은 줄을 창문에 매니라', NULL, ARRAY['rahab']),
-- 수 6:17 은 여기에 없다 — 머리 주석의 「수 6:17」 항 참조.
('jos-6:22', 'rev', 'joshua', 'jos', 6, 22, 22,
 '여호수아가 그 땅을 정탐한 두 사람에게 이르되 그 기생의 집에 들어가서 너희가 그 여인에게 맹세한 대로 그와 그에게 속한 모든 것을 이끌어 내라 하매', NULL, ARRAY['rahab']),
('jos-6:23', 'rev', 'joshua', 'jos', 6, 23, 23,
 '정탐한 젊은이들이 들어가서 라합과 그의 부모와 그의 형제와 그에게 속한 모든 것을 이끌어 내고 또 그의 친족도 다 이끌어 내어 그들을 이스라엘의 진영 밖에 두고', NULL, ARRAY['rahab']),
('jos-6:25', 'rev', 'joshua', 'jos', 6, 25, 25,
 '여호수아가 기생 라합과 그의 아버지의 가족과 그에게 속한 모든 것을 살렸으므로 그가 오늘까지 이스라엘 중에 거주하였으니 이는 여호수아가 여리고를 정탐하려고 보낸 사자들을 숨겼음이었더라', NULL, ARRAY['rahab']),
('jos-6:27', 'rev', 'joshua', 'jos', 6, 27, 27,
 '여호와께서 여호수아와 함께 하시니 여호수아의 소문이 그 온 땅에 퍼지니라', NULL, ARRAY['rahab']),
('jas-2:25', 'rev', 'james', 'jas', 2, 25, 25,
 '또 이와 같이 기생 라합이 사자들을 접대하여 다른 길로 나가게 할 때에 행함으로 의롭다 하심을 받은 것이 아니냐', NULL, ARRAY['rahab']),
('matt-1:5', 'rev', 'matthew', 'matt', 1, 5, 5,
 '살몬은 라합에게서 보아스를 낳고 보아스는 룻에게서 오벳을 낳고 오벳은 이새를 낳고', NULL, ARRAY['rahab'])
ON CONFLICT (reference, translation) DO NOTHING;
