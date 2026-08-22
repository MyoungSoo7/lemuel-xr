-- V20260822160000: 아브라함이 인용하는 절 중 **범위 인용에 묻혀 있던 5행**을 마저 시드한다.
--
-- ## 왜 V20260822120000 이 이 다섯을 놓쳤나
--
-- 그 마이그레이션의 수집기는 저작 yml 의 `text_ko` 에서 **단일 절 키**를 만든다.
-- 아브라함 저작은 두 덩어리를 한 블록에 합쳐 놓았다 —
--   · `nb_s4_sarah_laughter_block` = 창 18:12 + 18:13 + 18:14 + 18:15
--   · `nb_s5_birth_21_2`           = 창 21:1 + 21:2 + 21:5
-- 앞머리 절(18:12 · 21:1)만 단일 키로 잡혔고 뒤따르는 네 절 + 21:5 는 문자열 안에
-- 묻혀 수집되지 않았다. 그래서 시드에 18:12 · 21:1 은 있고 18:13~15 · 21:2 · 21:5 는
-- 없는, **한 블록의 절반만 열리는** 상태였다. 실측으로 확인하고 여기서 갚는다.
--
-- ## 출처·방법 (사람이 옮겨 적지 않았다)
--
-- 대한성서공회 개역개정 장 페이지를 기계 파싱했다 (2026-08-22 수거, 창세기 18·21장).
--   https://www.bskorea.or.kr/bible/korbibReadpage.php?version=GAE&book=gen&chap=<n>
-- 수거기는 V20260822120000 과 같은 함수 —
-- `scripts/scripture_text_check.py` 의 `fetch_chapter_krv` — 를 그대로 썼다.
--
-- **독립 검증**: 수거한 5개 절 문자열이 저작 yml 의 합본 블록 안에 그대로 들어 있는지
-- 부분문자열 대조했다. 5건 전부 일치 — 저작이 성경을 지어내지 않았다는 것까지 확인했다.
--
-- ## 범위 밖
--
-- 창 18:16 이후(소돔 중보)·창 21:8 이후(여종 서사)는 한 행도 넣지 않는다.
-- 후자는 `content/abraham/README.md` 가 미션 전체에서 배제한 서사다 — 수집기는
-- 인용 목록에서 키를 만들지 장 전체를 붓지 않는다.
--
-- ⚠️ 저작권: 개역개정판 (C) 대한성서공회. 비영리 이용 약관 범위 안에서 쓴다.

INSERT INTO scripture_passages
  (reference, translation, book, book_code, chapter, verse_start, verse_end, text, title, tags)
VALUES
('gen-18:13', 'rev', 'genesis', 'gen', 18, 13, 13,
 '여호와께서 아브라함에게 이르시되 사라가 왜 웃으며 이르기를 내가 늙었거늘 어떻게 아들을 낳으리요 하느냐', NULL, ARRAY['abraham']),
('gen-18:14', 'rev', 'genesis', 'gen', 18, 14, 14,
 '여호와께 능하지 못한 일이 있겠느냐 기한이 이를 때에 내가 네게로 돌아오리니 사라에게 아들이 있으리라', NULL, ARRAY['abraham']),
('gen-18:15', 'rev', 'genesis', 'gen', 18, 15, 15,
 '사라가 두려워서 부인하여 이르되 내가 웃지 아니하였나이다 이르시되 아니라 네가 웃었느니라', NULL, ARRAY['abraham']),
('gen-21:2', 'rev', 'genesis', 'gen', 21, 2, 2,
 '사라가 임신하고 하나님이 말씀하신 시기가 되어 노년의 아브라함에게 아들을 낳으니', NULL, ARRAY['abraham']),
('gen-21:5', 'rev', 'genesis', 'gen', 21, 5, 5,
 '아브라함이 그의 아들 이삭이 그에게 태어날 때에 백 세라', NULL, ARRAY['abraham'])
ON CONFLICT (reference, translation) DO NOTHING;
